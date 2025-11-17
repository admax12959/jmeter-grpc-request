package vn.zalopay.benchmark.core.config;

import lombok.Builder;
import lombok.Data;

/**
 * Encapsulates TLS/mTLS configuration using PEM files only.
 *
 * <p>Design goals:
 * - Immutable DTO with clear intent (Builder pattern)
 * - No references to UI; reusable across CLI/GUI/tests
 */
@Data
@Builder
public class GrpcSecurityConfig {
    /** Enable TLS. When false, plaintext will be used. */
    private final boolean tls;

    /** Trusted server certificate (PEM) or CA bundle (PEM). Optional when using system trust. */
    private final String caPemPath;

    /** Client certificate chain (PEM) for mTLS. Optional. */
    private final String clientCertPemPath;

    /** Client private key (PKCS#8 PEM) for mTLS. Optional. */
    private final String clientKeyPemPath;

    /** Client private key password for encrypted PKCS#8. Optional. */
    private final String clientKeyPassword;
}
