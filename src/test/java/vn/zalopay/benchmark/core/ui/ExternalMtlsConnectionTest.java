package vn.zalopay.benchmark.core.ui;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * External mTLS connectivity test against a running echo server on localhost:9090
 * using provided PEMs under grpc-echo-server project.
 *
 * This test is conditional and will be skipped if files or port are not available.
 */
public class ExternalMtlsConnectionTest {
    private static boolean exists(String p) { return Files.exists(Path.of(p)); }

    private static boolean portOpen(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    public void testConnectEchoServerMtls() {
        if (!"true".equalsIgnoreCase(System.getProperty("external.mtls.test", "false"))) {
            throw new SkipException("external.mtls.test not enabled; skipping external mTLS test");
        }
        String ca = "/Users/max/workspace/Codes/jmeter/jmeter-test-server/grpc-echo-server/src/main/resources/certs/ca.crt";
        String cert = "/Users/max/workspace/Codes/jmeter/jmeter-test-server/grpc-echo-server/src/main/resources/certs/client.crt";
        String key = "/Users/max/workspace/Codes/jmeter/jmeter-test-server/grpc-echo-server/src/main/resources/certs/client.key";
        String host = "localhost";
        int port = 9090;

        if (!(exists(ca) && exists(cert) && exists(key))) {
            throw new SkipException("External cert files not found; skipping");
        }
        if (!portOpen(host, port, 500)) {
            throw new SkipException("localhost:" + port + " not reachable; skipping");
        }

        ConnectionTester tester = new ConnectionTester();

        // Try with localhost first
        GrpcRequestConfig cfgLocal = GrpcRequestConfig.builder()
                .hostPort(host + ":" + port)
                .tls(true)
                .caPemPath(ca)
                .clientCertPemPath(cert)
                .clientKeyPemPath(key)
                .maxInboundMessageSize(4 * 1024 * 1024)
                .maxInboundMetadataSize(8192)
                .build();
        boolean ok = tester.test(cfgLocal, 5000);

        // If hostname validation mismatch (CN/SAN 127.0.0.1), retry with 127.0.0.1
        if (!ok && portOpen("127.0.0.1", port, 200)) {
            GrpcRequestConfig cfgIp = GrpcRequestConfig.builder()
                    .hostPort("127.0.0.1:" + port)
                    .tls(true)
                    .caPemPath(ca)
                    .clientCertPemPath(cert)
                    .clientKeyPemPath(key)
                    .maxInboundMessageSize(4 * 1024 * 1024)
                    .maxInboundMetadataSize(8192)
                    .build();
            ok = tester.test(cfgIp, 5000);
        }

        Assert.assertTrue(ok, "Expected READY state mTLS connection to echo server");
    }
}
