package com.pessoal.agenda.infra.pairing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Identidade TLS privada e estável usada somente pelo sync na rede local. */
public final class LocalSyncTlsIdentityStore {
    private static final String ALIAS = "agenda-local-sync";
    private static final Duration VALIDITY = Duration.ofDays(3650);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path keyStorePath;
    private final Path passwordPath;
    private final Clock clock;

    public LocalSyncTlsIdentityStore() {
        this(Path.of(System.getProperty("user.home"), ".agenda", "mobile-sync-identity.p12"),
                Path.of(System.getProperty("user.home"), ".agenda", "mobile-sync-identity.secret"),
                Clock.systemUTC());
    }

    LocalSyncTlsIdentityStore(Path keyStorePath, Path passwordPath, Clock clock) {
        this.keyStorePath = keyStorePath;
        this.passwordPath = passwordPath;
        this.clock = clock;
    }

    public synchronized TlsIdentity loadOrCreate(InetAddress address) {
        try {
            if (Files.isRegularFile(keyStorePath) && Files.isRegularFile(passwordPath)) {
                return load();
            }
            return create(address);
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível preparar a identidade TLS local.", error);
        }
    }

    private TlsIdentity load() throws Exception {
        restrict(keyStorePath);
        restrict(passwordPath);
        char[] password = Files.readString(passwordPath, StandardCharsets.US_ASCII).trim().toCharArray();
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(Files.readAllBytes(keyStorePath)), password);
            PrivateKey privateKey = (PrivateKey) store.getKey(ALIAS, password);
            X509Certificate certificate = (X509Certificate) store.getCertificate(ALIAS);
            if (privateKey == null || certificate == null) throw new IOException("Identidade incompleta.");
            certificate.checkValidity(java.util.Date.from(clock.instant()));
            return new TlsIdentity(new KeyPair(certificate.getPublicKey(), privateKey), certificate);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private TlsIdentity create(InetAddress address) throws Exception {
        Files.createDirectories(keyStorePath.getParent());
        byte[] passwordBytes = new byte[32];
        RANDOM.nextBytes(passwordBytes);
        char[] password = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(passwordBytes).toCharArray();
        java.util.Arrays.fill(passwordBytes, (byte) 0);
        KeyPair keys = keyPair();
        X509Certificate certificate = certificate(keys, address, clock.instant());
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry(ALIAS, keys.getPrivate(), password,
                    new java.security.cert.Certificate[]{certificate});
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            store.store(output, password);
            writePrivate(keyStorePath, output.toByteArray());
            writePrivate(passwordPath,
                    new String(password).getBytes(StandardCharsets.US_ASCII));
            return new TlsIdentity(keys, certificate);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(KeyPair keys, InetAddress address, Instant now)
            throws Exception {
        X500Name name = new X500Name("CN=Agenda Local Sync");
        var builder = new JcaX509v3CertificateBuilder(
                name, new BigInteger(128, RANDOM), java.util.Date.from(now.minusSeconds(60)),
                java.util.Date.from(now.plus(VALIDITY)), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.iPAddress, address.getHostAddress())));
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static void writePrivate(Path path, byte[] content) throws IOException {
        Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        restrict(path);
    }

    private static void restrict(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Sistemas sem permissões POSIX dependem das ACLs da conta local.
        }
    }

    public record TlsIdentity(KeyPair keys, X509Certificate certificate) {}
}
