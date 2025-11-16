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
}

