package vn.zalopay.benchmark.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class GrpcRequestConfig {
    private int maxInboundMessageSize = 4194304;
    private int maxInboundMetadataSize = 8192;
    private String hostPort;
    private String protoFolder;
    private String libFolder;
    private String fullMethod;
    private boolean tls;
    // Deprecated: disable verification is not supported anymore (kept for compatibility only)
    private boolean tlsDisableVerification;
    // TLS/mTLS (PEM only)
    private String caPemPath; // Trusted CA or server certificate (PEM)
    private String clientCertPemPath; // Client certificate chain (PEM)
    private String clientKeyPemPath; // Client private key (PKCS#8 PEM)
    private int awaitTerminationTimeout = 5000;

    public GrpcRequestConfig() {}

    public GrpcRequestConfig(
            String hostPort,
            String testProtoFile,
            String libFolder,
            String fullMethod,
            boolean tls,
            boolean tlsDisableVerification,
            int awaitTerminationTimeout) {
        this.hostPort = hostPort;
        this.protoFolder = testProtoFile;
        this.libFolder = libFolder;
        this.fullMethod = fullMethod;
        this.tls = tls;
        this.tlsDisableVerification = tlsDisableVerification;
        this.awaitTerminationTimeout = awaitTerminationTimeout;
    }

    public GrpcRequestConfig(
            String hostPort,
            String testProtoFile,
            String libFolder,
            String fullMethod,
            boolean tls,
            int awaitTerminationTimeout,
            String caPemPath,
            String clientCertPemPath,
            String clientKeyPemPath) {
        this.hostPort = hostPort;
        this.protoFolder = testProtoFile;
        this.libFolder = libFolder;
        this.fullMethod = fullMethod;
        this.tls = tls;
        this.awaitTerminationTimeout = awaitTerminationTimeout;
        this.caPemPath = caPemPath;
        this.clientCertPemPath = clientCertPemPath;
        this.clientKeyPemPath = clientKeyPemPath;
    }

    public String getHostPort() {
        return hostPort;
    }

    public String getProtoFolder() {
        return protoFolder;
    }

    public String getLibFolder() {
        return libFolder;
    }

    public String getFullMethod() {
        return fullMethod;
    }

    public boolean isTls() {
        return tls;
    }

    public boolean isTlsDisableVerification() {
        return tlsDisableVerification;
    }

    public int getAwaitTerminationTimeout() {
        return awaitTerminationTimeout;
    }

    public int getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    public int getMaxInboundMetadataSize() {
        return maxInboundMetadataSize;
    }

    @Override
    public String toString() {
        return "GrpcRequestConfig{"
                + "maxInboundMessageSize="
                + maxInboundMessageSize
                + ", maxInboundMetadataSize="
                + maxInboundMetadataSize
                + ", hostPort='"
                + hostPort
                + '\''
                + ", testProtoFile='"
                + protoFolder
                + '\''
                + ", libFolder='"
                + libFolder
                + '\''
                + ", fullMethod='"
                + fullMethod
                + '\''
                + ", tls="
                + tls
                + ", caPemPath='"
                + caPemPath
                + '\''
                + ", clientCertPemPath='"
                + clientCertPemPath
                + '\''
                + ", clientKeyPemPath='"
                + clientKeyPemPath
                + '\''
                + ", awaitTerminationTimeout="
                + awaitTerminationTimeout
                + '}';
    }
}
