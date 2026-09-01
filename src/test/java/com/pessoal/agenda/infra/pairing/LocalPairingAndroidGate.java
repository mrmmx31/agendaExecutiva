package com.pessoal.agenda.infra.pairing;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.DesktopSyncRepository;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Executável manual do gate AVD; não pertence à suíte Surefire. */
public final class LocalPairingAndroidGate {
    private LocalPairingAndroidGate() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Informe o arquivo de controle.");
        Path control = Path.of(args[0]);
        Path directory = Files.createTempDirectory("agenda-pairing-avd-");
        Database database = new Database(directory.resolve("gate.db"));
        database.runMigrations();
        DesktopSyncRepository repository = new DesktopSyncRepository(database);

        try (var server = new LocalPairingServer(
                repository, java.time.Clock.systemUTC(), InetAddress.getByName("127.0.0.1"), 0,
                message -> System.out.println("PAIRING_GATE_DIAGNOSTIC " + message))) {
            PairingSession session = server.start();
            String port = invitationValue(session.invitation(), "endpoint")
                    .replaceFirst("^https://127\\.0\\.0\\.1:(\\d+)/.*$", "$1");
            Files.writeString(control, session.invitation() + "\n" + session.oneTimeCode()
                    + "\n" + port + "\n", StandardCharsets.UTF_8);
            System.out.println("PAIRING_GATE_READY " + port);

            PendingPairingRequest pending = waitForRequest(server, Duration.ofSeconds(90));
            server.approve(pending.requestId(), pending.requestedRoles());
            System.out.println("PAIRING_GATE_APPROVED " + pending.deviceId());

            Instant deadline = Instant.now().plusSeconds(180);
            while (Instant.now().isBefore(deadline)) {
                if (database.queryInt("SELECT COUNT(*) FROM inbox_captures") == 1) {
                    System.out.println("PAIRING_GATE_SYNCED");
                    return;
                }
                Thread.sleep(250);
            }
            throw new IllegalStateException("O Android não entregou a captura dentro do prazo.");
        } finally {
            Files.deleteIfExists(control);
            deleteTree(directory);
        }
    }

    private static PendingPairingRequest waitForRequest(
            LocalPairingServer server, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            PendingPairingRequest pending = server.pendingRequest();
            if (pending != null) return pending;
            Thread.sleep(100);
        }
        throw new IllegalStateException("O Android não enviou a solicitação dentro do prazo.");
    }

    private static String invitationValue(String invitation, String name) {
        String query = URI.create(invitation).getRawQuery();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Convite sem " + name);
    }

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
