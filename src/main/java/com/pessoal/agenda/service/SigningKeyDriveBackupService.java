package com.pessoal.agenda.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Optional;

/** Orquestra backup e teste de restauração sem expor a chave aberta ao Drive. */
public final class SigningKeyDriveBackupService {
    public static final Path DEFAULT_KEY_PATH = Path.of(System.getProperty("user.home"),
            ".local", "share", "agenda", "signing", "agenda-release.p12");

    private final GoogleDriveAppDataService drive;

    public SigningKeyDriveBackupService() {
        this(new GoogleDriveAppDataService());
    }

    SigningKeyDriveBackupService(GoogleDriveAppDataService drive) {
        this.drive = drive;
    }

    public boolean localKeyExists() {
        return Files.isRegularFile(DEFAULT_KEY_PATH);
    }

    public Optional<GoogleDriveAppDataService.BackupMetadata> findBackup()
            throws IOException, InterruptedException {
        return drive.findBackup();
    }

    public BackupResult createOrUpdate(char[] keyPassword, char[] recoveryPassphrase)
            throws IOException, GeneralSecurityException, InterruptedException {
        byte[] keyBytes = Files.readAllBytes(DEFAULT_KEY_PATH);
        try {
            String fingerprint = validatePkcs12(keyBytes, keyPassword);
            byte[] encrypted = SigningKeyBackupCrypto.encrypt(keyBytes, recoveryPassphrase);
            try {
                GoogleDriveAppDataService.BackupMetadata metadata = drive.upload(encrypted);
                return new BackupResult(metadata.modifiedAt(), fingerprint, encrypted.length);
            } finally {
                Arrays.fill(encrypted, (byte) 0);
            }
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public RestoreTestResult testRestore(char[] keyPassword, char[] recoveryPassphrase)
            throws IOException, GeneralSecurityException, InterruptedException {
        byte[] encrypted = drive.download();
        byte[] restored = null;
        byte[] local = null;
        try {
            restored = SigningKeyBackupCrypto.decrypt(encrypted, recoveryPassphrase);
            String fingerprint = validatePkcs12(restored, keyPassword);
            if (localKeyExists()) {
                local = Files.readAllBytes(DEFAULT_KEY_PATH);
                String localFingerprint = validatePkcs12(local, keyPassword);
                if (!MessageDigest.isEqual(fingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        localFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new GeneralSecurityException(
                            "O certificado do backup não corresponde à chave local.");
                }
            }
            return new RestoreTestResult(fingerprint, restored.length);
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            if (restored != null) Arrays.fill(restored, (byte) 0);
            if (local != null) Arrays.fill(local, (byte) 0);
        }
    }

    static String validatePkcs12(byte[] content, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(new ByteArrayInputStream(content), password);
        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!store.isKeyEntry(alias)) continue;
            Certificate certificate = store.getCertificate(alias);
            if (certificate == null) continue;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return toHex(digest);
        }
        throw new GeneralSecurityException("O PKCS#12 não contém chave privada e certificado.");
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02X", item));
        return result.toString();
    }

    public record BackupResult(Instant modifiedAt, String certificateSha256, long encryptedBytes) {}
    public record RestoreTestResult(String certificateSha256, long restoredBytes) {}
}
