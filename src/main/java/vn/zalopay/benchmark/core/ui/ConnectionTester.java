package vn.zalopay.benchmark.core.ui;

import com.google.common.net.HostAndPort;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;
import vn.zalopay.benchmark.core.config.GrpcSecurityConfig;
import vn.zalopay.benchmark.core.grpc.ChannelFactory;

/**
 * Small utility to validate connectivity/TLS handshake against target endpoint.
 *
 * <p>Notes: This does not perform an application RPC, it only forces a connect
 * attempt and observes state transitions within a timeout window.
 */
public class ConnectionTester {
    public boolean test(GrpcRequestConfig cfg, long timeoutMillis) {
        HostAndPort endpoint = HostAndPort.fromString(cfg.getHostPort());
        GrpcSecurityConfig sec =
                GrpcSecurityConfig.builder()
                        .tls(cfg.isTls())
                        .caPemPath(cfg.getCaPemPath())
                        .clientCertPemPath(cfg.getClientCertPemPath())
                        .clientKeyPemPath(cfg.getClientKeyPemPath())
                        .build();
        ManagedChannel ch =
                ChannelFactory.create()
                        .createChannel(endpoint, sec, Map.of(), cfg.getMaxInboundMessageSize(),
                                cfg.getMaxInboundMetadataSize());
        try {
            Instant end = Instant.now().plus(Duration.ofMillis(timeoutMillis));
            ConnectivityState state = ch.getState(true);
            while (Instant.now().isBefore(end)) {
                if (state == ConnectivityState.READY) return true;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                state = ch.getState(false);
            }
            return state == ConnectivityState.READY;
        } finally {
            ch.shutdownNow();
        }
    }

    /**
     * Parse simple Subject/Issuer details from CA PEM file for diagnostics.
     */
    static String parseCertDetails(String caPemPath) {
        if (caPemPath == null || caPemPath.trim().isEmpty()) return "";
        try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(
                java.nio.file.Paths.get(caPemPath))) {
            StringBuilder pem = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) pem.append(line).append('\n');
            String pemStr = pem.toString();
            String[] entries = pemStr.split("-----END CERTIFICATE-----");
            StringBuilder out = new StringBuilder();
            java.security.cert.CertificateFactory cf =
                    java.security.cert.CertificateFactory.getInstance("X.509");
            for (String e : entries) {
                if (!e.contains("BEGIN CERTIFICATE")) continue;
                String certPem = (e + "-----END CERTIFICATE-----").trim();
                byte[] der = java.util.Base64.getMimeDecoder().decode(
                        certPem.replace("-----BEGIN CERTIFICATE-----", "")
                                .replace("-----END CERTIFICATE-----", "")
                                .replaceAll("\\s", ""));
                java.security.cert.X509Certificate x =
                        (java.security.cert.X509Certificate) cf.generateCertificate(
                                new java.io.ByteArrayInputStream(der));
                out.append("Subject: ").append(x.getSubjectX500Principal().getName()).append('\n');
                out.append("Issuer: ").append(x.getIssuerX500Principal().getName()).append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
