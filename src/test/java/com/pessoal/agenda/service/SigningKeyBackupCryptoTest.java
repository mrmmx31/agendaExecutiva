package com.pessoal.agenda.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SigningKeyBackupCryptoTest {
    private static final char[] PASSWORD = "recuperacao-segura-123".toCharArray();

    @Test
    void roundTripPreservesContent() throws Exception {
        byte[] original = "pkcs12-test-content".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = SigningKeyBackupCrypto.encrypt(original, PASSWORD);

        assertArrayEquals(original, SigningKeyBackupCrypto.decrypt(encrypted, PASSWORD));
    }

    @Test
    void wrongPasswordAndTamperingAreRejected() throws Exception {
        byte[] encrypted = SigningKeyBackupCrypto.encrypt(new byte[]{1, 2, 3}, PASSWORD);

        assertThrows(IOException.class, () -> SigningKeyBackupCrypto.decrypt(
                encrypted, "outra-senha-segura-456".toCharArray()));
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(IOException.class, () -> SigningKeyBackupCrypto.decrypt(encrypted, PASSWORD));
    }

    @Test
    void shortRecoveryPasswordIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SigningKeyBackupCrypto.encrypt(new byte[]{1}, "curta".toCharArray()));
    }

    @Test
    void manipulatedWorkFactorIsRejectedBeforeDerivation() throws Exception {
        byte[] encrypted = SigningKeyBackupCrypto.encrypt(new byte[]{1}, PASSWORD);
        // Magic (8 bytes), versão (4 bytes), iterações (4 bytes).
        encrypted[15] ^= 1;

        assertThrows(IOException.class,
                () -> SigningKeyBackupCrypto.decrypt(encrypted, PASSWORD));
    }
}
