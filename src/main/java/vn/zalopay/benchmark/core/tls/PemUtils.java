package vn.zalopay.benchmark.core.tls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

/**
 * PEM utilities for normalizing private keys and certificates for TLS/mTLS.
 *
 * <p>Goals:
 * - Accept RSA/EC keys in PKCS#1 ("BEGIN RSA PRIVATE KEY") or SEC1 EC ("BEGIN EC PRIVATE KEY")
 *   and convert to PKCS#8.
 * - Accept encrypted PKCS#8 ("BEGIN ENCRYPTED PRIVATE KEY") and decrypt using provided password.
 * - Return unencrypted PKCS#8 bytes in PEM form ("BEGIN PRIVATE KEY").
 */
public final class PemUtils {
    private PemUtils() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static InputStream readAll(String path) throws IOException {
        Path p = Paths.get(path);
        return Files.newInputStream(p);
    }

    public static byte[] readAllBytes(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }

    public static byte[] normalizePrivateKeyToPkcs8Pem(byte[] keyPemBytes, char[] password)
            throws IOException {
        String pem = new String(keyPemBytes, StandardCharsets.UTF_8);
        Object parsed = parseFirstPemObject(pem);
        try {
            PrivateKeyInfo pki;
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (parsed instanceof PKCS8EncryptedPrivateKeyInfo) {
                if (password == null || password.length == 0) {
                    throw new IllegalArgumentException(
                            "Encrypted PKCS#8 private key detected but no password provided");
                }
                PKCS8EncryptedPrivateKeyInfo enc = (PKCS8EncryptedPrivateKeyInfo) parsed;
                InputDecryptorProvider decProv =
                        new JceOpenSSLPKCS8DecryptorProviderBuilder().build(password);
                pki = enc.decryptPrivateKeyInfo(decProv);
            } else if (parsed instanceof PEMEncryptedKeyPair) {
                if (password == null || password.length == 0) {
                    throw new IllegalArgumentException(
                            "Encrypted private key detected but no password provided");
                }
                PEMEncryptedKeyPair ekp = (PEMEncryptedKeyPair) parsed;
                PEMKeyPair kp = ekp.decryptKeyPair(new JcePEMDecryptorProviderBuilder().build(password));
                java.security.PrivateKey pk = converter.getKeyPair(kp).getPrivate();
                byte[] pkcs8Der = pk.getEncoded();
                return toPem("PRIVATE KEY", pkcs8Der);
            } else if (parsed instanceof PEMKeyPair) {
                PEMKeyPair kp = (PEMKeyPair) parsed;
                java.security.PrivateKey pk = converter.getKeyPair(kp).getPrivate();
                byte[] pkcs8Der = pk.getEncoded();
                return toPem("PRIVATE KEY", pkcs8Der);
            } else if (parsed instanceof PrivateKeyInfo) {
                pki = (PrivateKeyInfo) parsed;
            } else {
                // If the parser didn't understand it, assume it's already a raw PKCS#8 PEM
                // and pass through unchanged.
                return keyPemBytes;
            }

            byte[] pkcs8Der = pki.getEncoded();
            return toPem("PRIVATE KEY", pkcs8Der);
        } catch (OperatorCreationException e) {
            throw new IOException("Unable to decrypt private key: " + e.getMessage(), e);
        } catch (org.bouncycastle.pkcs.PKCSException e) {
            throw new IOException("Unable to decrypt PKCS#8 private key: " + e.getMessage(), e);
        }
    }

    public static InputStream normalizePrivateKeyToPkcs8PemStream(String path, String password)
            throws IOException {
        byte[] bytes = readAllBytes(path);
        byte[] normalized = normalizePrivateKeyToPkcs8Pem(bytes, password == null ? null : password.toCharArray());
        return new ByteArrayInputStream(normalized);
    }

    private static Object parseFirstPemObject(String pem) throws IOException {
        try (PEMParser parser =
                new PEMParser(new InputStreamReader(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))))) {
            Object obj;
            while ((obj = parser.readObject()) != null) {
                // Return the first key-like object encountered
                if (obj instanceof PKCS8EncryptedPrivateKeyInfo
                        || obj instanceof PEMEncryptedKeyPair
                        || obj instanceof PEMKeyPair
                        || obj instanceof PrivateKeyInfo) {
                    return obj;
                }
            }
            return null;
        }
    }

    private static byte[] toPem(String type, byte[] der) {
        String b64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        String s = "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
