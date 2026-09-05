package com.pessoal.agenda.infra.pairing;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.DesktopSyncRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalSyncTlsIdentityStoreTest {
    @TempDir Path tempDir;

    @Test
    void identitySurvivesStoreRecreationAndFilesArePrivate() throws Exception {
        Path keyStore = tempDir.resolve("sync.p12");
        Path secret = tempDir.resolve("sync.secret");
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

        var first = new LocalSyncTlsIdentityStore(keyStore, secret, clock)
                .loadOrCreate(InetAddress.getByName("192.168.1.10"));
        var second = new LocalSyncTlsIdentityStore(keyStore, secret, clock)
                .loadOrCreate(InetAddress.getByName("192.168.1.99"));

        assertArrayEquals(first.certificate().getEncoded(), second.certificate().getEncoded());
        assertArrayEquals(first.keys().getPrivate().getEncoded(), second.keys().getPrivate().getEncoded());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyStore)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(secret)));
    }

    @Test
    void fingerprintSurvivesCompleteServerRecreation() throws Exception {
        Path keyStore = tempDir.resolve("server-sync.p12");
        Path secret = tempDir.resolve("server-sync.secret");
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);
        InetAddress address = InetAddress.getByName("127.0.0.1");
        var store = new LocalSyncTlsIdentityStore(keyStore, secret, clock);
        Database database = new Database(tempDir.resolve("server-sync.db"));
        database.runMigrations();
        var repository = new DesktopSyncRepository(database);
        String firstFingerprint;

        try (var first = new LocalPairingServer(
                repository, address, 0, store.loadOrCreate(address))) {
            firstFingerprint = invitationValue(first.start().invitation(), "fingerprint");
        }
        try (var second = new LocalPairingServer(
                repository, address, 0, store.loadOrCreate(address))) {
            assertEquals(firstFingerprint,
                    invitationValue(second.start().invitation(), "fingerprint"));
        }
    }

    private static String invitationValue(String invitation, String key) {
        return java.util.Arrays.stream(invitation.substring(invitation.indexOf('?') + 1).split("&"))
                .map(value -> value.split("=", 2))
                .filter(value -> value[0].equals(key))
                .map(value -> java.net.URLDecoder.decode(value[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }
}
