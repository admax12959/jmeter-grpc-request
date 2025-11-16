package vn.zalopay.benchmark.core.ui;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ConnectionTesterTest {
    @Test
    public void canParseCertDetails() {
        String pem = java.nio.file.Paths.get(
                        System.getProperty("user.dir"), "dist", "cert", "localhost.crt")
                .toString();
        String details = ConnectionTester.parseCertDetails(pem);
        Assert.assertTrue(details.contains("Subject:") || details.contains("CN="));
        Assert.assertTrue(details.contains("Issuer:"));
    }
}

