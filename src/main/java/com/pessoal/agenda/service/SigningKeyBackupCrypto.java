package com.pessoal.agenda.service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/** Envelope local autenticado antes de qualquer envio ao Drive. */
public final class SigningKeyBackupCrypto {
    private static final byte[] MAGIC = "AGKEYBK1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int HASH_BYTES = 32;

    private SigningKeyBackupCrypto() {}

    public static byte[] encrypt(byte[] pkcs12, char[] recoveryPassphrase)
            throws GeneralSecurityException {
        requirePassphrase(recoveryPassphrase);
        byte[] salt = random(SALT_BYTES);
        byte[] nonce = random(NONCE_BYTES);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(pkcs12);
        byte[] header = ByteBuffer.allocate(MAGIC.length + Integer.BYTES * 2
                        + SALT_BYTES + NONCE_BYTES + HASH_BYTES)
                .put(MAGIC).putInt(VERSION).putInt(ITERATIONS)
                .put(salt).put(nonce).put(hash).array();
        byte[] derived = derive(recoveryPassphrase, salt, ITERATIONS);
        try {
            SecretKeySpec key = new SecretKeySpec(derived, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(header);
            byte[] ciphertext = cipher.doFinal(pkcs12);
            return ByteBuffer.allocate(header.length + ciphertext.length)
                    .put(header).put(ciphertext).array();
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    public static byte[] decrypt(byte[] envelope, char[] recoveryPassphrase)
            throws GeneralSecurityException, IOException {
        requirePassphrase(recoveryPassphrase);
        int headerLength = MAGIC.length + Integer.BYTES * 2 + SALT_BYTES + NONCE_BYTES + HASH_BYTES;
        if (envelope == null || envelope.length <= headerLength + 16) {
            throw new IOException("Backup cifrado incompleto.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        int version = buffer.getInt();
        int iterations = buffer.getInt();
        if (!Arrays.equals(magic, MAGIC) || version != VERSION || iterations != ITERATIONS) {
            throw new IOException("Formato de backup não reconhecido.");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] expectedHash = new byte[HASH_BYTES];
        buffer.get(salt).get(nonce).get(expectedHash);
        byte[] header = Arrays.copyOf(envelope, headerLength);
        byte[] ciphertext = Arrays.copyOfRange(envelope, headerLength, envelope.length);
        byte[] derived = derive(recoveryPassphrase, salt, iterations);
        try {
            SecretKeySpec key = new SecretKeySpec(derived, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(header);
            byte[] plaintext = cipher.doFinal(ciphertext);
            byte[] actualHash = MessageDigest.getInstance("SHA-256").digest(plaintext);
            if (!MessageDigest.isEqual(expectedHash, actualHash)) {
                Arrays.fill(plaintext, (byte) 0);
                throw new IOException("A integridade do backup não pôde ser confirmada.");
            }
            return plaintext;
        } catch (AEADBadTagException error) {
            throw new IOException("Senha de recuperação incorreta ou backup alterado.", error);
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    private static byte[] derive(char[] passphrase, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void requirePassphrase(char[] value) {
        if (value == null || value.length < 16) {
            throw new IllegalArgumentException("A senha de recuperação deve ter ao menos 16 caracteres.");
        }
    }

    private static byte[] random(int length) {
        byte[] value = new byte[length];
        new SecureRandom().nextBytes(value);
        return value;
    }
}
