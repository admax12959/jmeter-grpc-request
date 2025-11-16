package vn.zalopay.benchmark.core.grpc;

import com.google.common.net.HostAndPort;

import io.grpc.*;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.TlsChannelCredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.zalopay.benchmark.core.config.GrpcSecurityConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

/** Knows how to construct grpc channels using the Credentials API. */
public class ChannelFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelFactory.class);

    public static ChannelFactory create() {
        return new ChannelFactory();
    }

    private ChannelFactory() {}

    public ManagedChannel createChannel(
            HostAndPort endpoint,
            GrpcSecurityConfig security,
            Map<String, String> metadataHash,
            int maxInboundMessageSize,
            int maxInboundMetadataSize) {
        ManagedChannelBuilder<?> builder = createChannelBuilder(endpoint, security);
        builder.maxInboundMessageSize(maxInboundMessageSize);
        builder.maxInboundMetadataSize(maxInboundMetadataSize);
        builder.intercept(metadataInterceptor(metadataHash));
        return builder.build();
    }

    private ManagedChannelBuilder<?> createChannelBuilder(
            HostAndPort endpoint, GrpcSecurityConfig security) {
        if (security == null || !security.isTls()) {
            return Grpc.newChannelBuilderForAddress(
                    endpoint.getHost(), endpoint.getPort(), InsecureChannelCredentials.create());
        }
        ChannelCredentials creds = buildTlsCredentials(security);
        return Grpc.newChannelBuilderForAddress(endpoint.getHost(), endpoint.getPort(), creds);
    }

    private ChannelCredentials buildTlsCredentials(GrpcSecurityConfig security) {
        try {
            TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder();
            if (notBlank(security.getCaPemPath())) {
                builder = builder.trustManager(readAll(security.getCaPemPath()));
            }
            if (notBlank(security.getClientCertPemPath()) && notBlank(security.getClientKeyPemPath())) {
                builder = builder.keyManager(
                        readAll(security.getClientCertPemPath()), readAll(security.getClientKeyPemPath()));
            }
            return builder.build();
        } catch (IOException e) {
            LOGGER.error("Error in create TLS credentials: {}", e.getMessage());
            throw new RuntimeException("Error in create SSL connection!", e);
        }
    }

    private InputStream readAll(String path) throws IOException {
        Path p = Paths.get(path);
        return Files.newInputStream(p);
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private ClientInterceptor metadataInterceptor(Map<String, String> metadataHash) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    final io.grpc.MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    final Channel next) {
                return new ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    protected void checkedStart(
                            Listener<RespT> responseListener, Metadata headers) {
                        for (Map.Entry<String, String> entry : metadataHash.entrySet()) {
                            String k = entry.getKey();
                            String v = entry.getValue();
                            if (k != null && k.endsWith("-bin")) {
                                Metadata.Key<byte[]> key =
                                        Metadata.Key.of(k, Metadata.BINARY_BYTE_MARSHALLER);
                                byte[] bytes;
                                try {
                                    bytes = Base64.getDecoder().decode(v);
                                } catch (IllegalArgumentException ex) {
                                    bytes = v == null ? new byte[0] : v.getBytes(StandardCharsets.UTF_8);
                                }
                                headers.put(key, bytes);
                            } else {
                                Metadata.Key<String> key =
                                        Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER);
                                headers.put(key, v);
                            }
                        }
                        delegate().start(responseListener, headers);
                    }
                };
            }
        };
    }
}
