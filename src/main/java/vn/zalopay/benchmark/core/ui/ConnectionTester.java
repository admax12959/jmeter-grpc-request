package vn.zalopay.benchmark.core.ui;

import com.google.common.net.HostAndPort;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.zalopay.benchmark.core.config.GrpcSecurityConfig;
import vn.zalopay.benchmark.core.grpc.ChannelFactory;

/**
 * Small utility to validate connectivity/TLS handshake against target endpoint.
 *
 * <p>Notes: This does not perform an application RPC, it only forces a connect
 * attempt and observes state transitions within a timeout window.
 */
public class ConnectionTester {
    private static final Logger log = LoggerFactory.getLogger(ConnectionTester.class);
    public boolean test(GrpcRequestConfig cfg, long timeoutMillis) {
        HostAndPort endpoint = HostAndPort.fromString(cfg.getHostPort());
        GrpcSecurityConfig sec =
                GrpcSecurityConfig.builder()
                        .tls(cfg.isTls())
                        .caPemPath(cfg.getCaPemPath())
                        .clientCertPemPath(cfg.getClientCertPemPath())
                        .clientKeyPemPath(cfg.getClientKeyPemPath())
                        .clientKeyPassword(cfg.getClientKeyPassword())
                        .build();
        ManagedChannel ch =
                ChannelFactory.create()
                        .createChannel(endpoint, sec, Map.of(), cfg.getMaxInboundMessageSize(),
                                cfg.getMaxInboundMetadataSize());
        try {
            Instant end = Instant.now().plus(Duration.ofMillis(timeoutMillis));
            ConnectivityState state = ch.getState(true);
            log.info("[TestConnection] initial state: {}", state);
            while (Instant.now().isBefore(end)) {
                if (state == ConnectivityState.READY) return true;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                state = ch.getState(false);
                log.debug("[TestConnection] state: {}", state);
            }
            if (state == ConnectivityState.READY) return true;
            // Fallback: if TLS handshake via JDK succeeds, consider connectivity OK for probing.
            try {
                String diag = diagnose(cfg);
                if (diag.contains("TLS: handshake OK")) {
                    log.info("[TestConnection] TLS handshake OK; treating as connectivity success");
                    return true;
                }
            } catch (Exception ignore) {}
            return false;
        } finally {
            ch.shutdownNow();
        }
    }

    /**
     * Attempt to explain a failure with concrete diagnostics:
     * - DNS resolution
     * - TCP connectivity
     * - TLS handshake using JDK SSL (with hostname verification)
     */
    public static String diagnose(GrpcRequestConfig cfg) {
        String host = cfg.getHostPort().split(":")[0];
        int port = Integer.parseInt(cfg.getHostPort().split(":")[1]);
        StringBuilder sb = new StringBuilder();
        try {
            java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
            sb.append("DNS: ").append(host).append(" => ");
            for (int i = 0; i < addrs.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(addrs[i].getHostAddress());
            }
            sb.append('\n');
        } catch (Exception e) {
            sb.append("DNS: failed for ").append(host).append(" -> ").append(e.toString()).append('\n');
        }

        // TCP connect
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 1500);
            sb.append("TCP: connected to ").append(host).append(":").append(port).append('\n');
        } catch (Exception e) {
            sb.append("TCP: connect failed -> ").append(e.toString()).append('\n');
            return sb.toString();
        }

        // TLS handshake via JDK SSL (PEM trust + optional client auth)
        try {
            javax.net.ssl.TrustManager[] tms = buildTrustManagers(cfg.getCaPemPath());
            javax.net.ssl.KeyManager[] kms = buildKeyManagers(cfg.getClientCertPemPath(), cfg.getClientKeyPemPath(), cfg.getClientKeyPassword());
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(kms, tms, null);
            javax.net.ssl.SSLSocketFactory sf = sc.getSocketFactory();
            try (javax.net.ssl.SSLSocket ss = (javax.net.ssl.SSLSocket) sf.createSocket(host, port)) {
                javax.net.ssl.SSLParameters params = ss.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS"); // enable host verification
                ss.setSSLParameters(params);
                ss.startHandshake();
                javax.net.ssl.SSLSession session = ss.getSession();
                java.security.cert.Certificate[] chain = session.getPeerCertificates();
                if (chain != null && chain.length > 0 && chain[0] instanceof java.security.cert.X509Certificate) {
                    java.security.cert.X509Certificate leaf = (java.security.cert.X509Certificate) chain[0];
                    sb.append("TLS: handshake OK. Peer Subject: ").append(leaf.getSubjectX500Principal().getName()).append('\n');
                } else {
                    sb.append("TLS: handshake OK. Peer chain unavailable\n");
                }
            }
        } catch (Exception e) {
            sb.append("TLS: handshake failed -> ").append(e.toString());
            Throwable c = e.getCause();
            int depth = 0;
            while (c != null && depth++ < 5) { sb.append("\n  cause: ").append(c.toString()); c = c.getCause(); }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static javax.net.ssl.TrustManager[] buildTrustManagers(String caPem) throws Exception {
        if (caPem == null || caPem.trim().isEmpty()) return null; // default system trust
        java.security.KeyStore ts = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        ts.load(null, null);
        try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(caPem))) {
            String line; StringBuilder cur = new StringBuilder(); int idx = 0;
            while ((line = br.readLine()) != null) {
                cur.append(line).append('\n');
                if (line.contains("END CERTIFICATE")) {
                    java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                    java.io.ByteArrayInputStream is = new java.io.ByteArrayInputStream(cur.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    java.security.cert.Certificate cert = cf.generateCertificate(is);
                    ts.setCertificateEntry("ca-" + (idx++), cert);
                    cur.setLength(0);
                }
            }
        }
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf.getTrustManagers();
    }

    private static javax.net.ssl.KeyManager[] buildKeyManagers(String certPem, String keyPem, String keyPassword) throws Exception {
        if (certPem == null || certPem.trim().isEmpty() || keyPem == null || keyPem.trim().isEmpty()) return null;
        // Load client certificate chain
        java.util.List<java.security.cert.X509Certificate> chain = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(certPem))) {
            String line; StringBuilder cur = new StringBuilder();
            while ((line = br.readLine()) != null) {
                cur.append(line).append('\n');
                if (line.contains("END CERTIFICATE")) {
                    java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                    java.io.ByteArrayInputStream is = new java.io.ByteArrayInputStream(cur.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    chain.add((java.security.cert.X509Certificate) cf.generateCertificate(is));
                    cur.setLength(0);
                }
            }
        }
        // Load private key (normalized PKCS#8 PEM)
        byte[] pkcs8Pem = vn.zalopay.benchmark.core.tls.PemUtils.normalizePrivateKeyToPkcs8Pem(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(keyPem)),
                keyPassword == null ? null : keyPassword.toCharArray());
        String pkStr = new String(pkcs8Pem, java.nio.charset.StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] pkDer = java.util.Base64.getDecoder().decode(pkStr);
        java.security.spec.PKCS8EncodedKeySpec ks = new java.security.spec.PKCS8EncodedKeySpec(pkDer);
        java.security.PrivateKey pk;
        try { pk = java.security.KeyFactory.getInstance("RSA").generatePrivate(ks); }
        catch (Exception e) { pk = java.security.KeyFactory.getInstance("EC").generatePrivate(ks); }

        java.security.KeyStore ksStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        ksStore.load(null, null);
        ksStore.setKeyEntry("client", pk, new char[0], chain.toArray(new java.security.cert.X509Certificate[0]));
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ksStore, new char[0]);
        return kmf.getKeyManagers();
    }

    /**
     * Parse simple Subject/Issuer details from CA PEM file for diagnostics.
     */
    public static String parseCertDetails(String caPemPath) {
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

    /**
     * Build a hostname validation hint by comparing target host with CN/SAN from the first cert.
     * This is a best-effort diagnostic to guide users when hostname verification might fail.
     */
    // Note: A robust hostname mismatch hint requires inspecting the server leaf certificate from
    // the TLS handshake. Using the CA PEM can be misleading, so this helper is intentionally
    // disabled to avoid false positives. Future versions may implement a handshake peek.
    public static String hostnameHint(String host, String certPemPath) { return ""; }
}
