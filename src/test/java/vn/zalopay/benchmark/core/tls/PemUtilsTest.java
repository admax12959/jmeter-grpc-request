package vn.zalopay.benchmark.core.tls;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PemUtilsTest {
    @Test
    public void canPassThroughUnencryptedPkcs8() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Build PKCS#8 PEM manually from DER (avoid BC in tests to prevent signer conflicts)
        byte[] der = kp.getPrivate().getEncoded();
        String b64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";

        byte[] out = PemUtils.normalizePrivateKeyToPkcs8Pem(pem.getBytes(StandardCharsets.UTF_8), null);
        String s = new String(out, StandardCharsets.UTF_8);
        Assert.assertTrue(s.contains("BEGIN PRIVATE KEY"));
        Assert.assertFalse(s.contains("ENCRYPTED"));
    }
}
