package vn.zalopay.benchmark.core.mtls;

import com.google.common.net.HostAndPort;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.testng.Assert;
import org.testng.annotations.Test;
import vn.zalopay.benchmark.core.ClientCaller;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;
import vn.zalopay.benchmark.core.grpc.DynamicMessageMarshaller;
import vn.zalopay.benchmark.core.protobuf.ProtoMethodName;
import vn.zalopay.benchmark.core.protobuf.ProtocInvoker;
import vn.zalopay.benchmark.core.protobuf.ServiceResolver;

/** End-to-end mTLS test using PEM files. */
public class MtlsIntegrationTest {
    @Test
    public void echoOverMtls() throws Exception {
        int port = 18089;
        String hostPort = HostAndPort.fromParts("localhost", port).toString();
        String protoRoot = Paths.get(System.getProperty("user.dir"),
                "src", "test", "resources", "mtls").toString();

        // Build dynamic service from proto
        ServiceResolver resolver =
                ServiceResolver.fromFileDescriptorSet(
                        ProtocInvoker.forConfig(protoRoot, "").invoke());
        ProtoMethodName method = ProtoMethodName.parseFullGrpcMethodName("echo.EchoService/Echo");
        Descriptors.MethodDescriptor m = resolver.resolveServiceMethod(method);
        io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> md =
                io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                        .setFullMethodName("echo.EchoService/Echo")
                        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                        .setRequestMarshaller(new DynamicMessageMarshaller(m.getInputType()))
                        .setResponseMarshaller(new DynamicMessageMarshaller(m.getOutputType()))
                        .build();

        // Build server requiring client auth (mTLS)
        File serverCert = Paths.get(System.getProperty("user.dir"), "dist", "cert", "localhost.crt").toFile();
        File serverKey = Paths.get(System.getProperty("user.dir"), "dist", "cert", "localhost.key").toFile();
        File clientCert = serverCert; // for test purpose, reuse same cert/key as client identity

        io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.ensureAlpnAndH2Enabled(
                io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder.forServer(serverCert, serverKey));
        io.grpc.netty.shaded.io.netty.handler.ssl.SslContext sslContext =
                io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.forServer(serverCert, serverKey)
                        .trustManager(clientCert)
                        .clientAuth(io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth.REQUIRE)
                        .build();
        Server server =
                io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder.forPort(port)
                        .sslContext(sslContext)
                        .addService(
                                ServerServiceDefinition.builder("echo.EchoService")
                                        .addMethod(
                                                md,
                                                ServerCalls.asyncUnaryCall(
                                                        (request, responseObserver) -> {
                                                            // Echo back the request.message field
                                                            DynamicMessage resp =
                                                                    DynamicMessage.newBuilder(m.getOutputType())
                                                                            .mergeFrom(request)
                                                                            .build();
                                                            responseObserver.onNext(resp);
                                                            responseObserver.onCompleted();
                                                        }))
                                        .build())
                        .build()
                        .start();
        try {
            // Build client config with mTLS
            String pem = serverCert.getAbsolutePath();
            GrpcRequestConfig cfg =
                    new GrpcRequestConfig(
                            hostPort,
                            protoRoot,
                            "",
                            "echo.EchoService/Echo",
                            true,
                            5000,
                            pem,
                            pem,
                            serverKey.getAbsolutePath());
            ClientCaller caller = new ClientCaller(cfg);
            caller.buildRequestAndMetadata("{\"message\":\"hello\"}", "");
            String resp = caller.call("2000").getGrpcMessageString();
            Assert.assertTrue(resp.contains("hello"));
        } finally {
            server.shutdown();
            server.awaitTermination(3, TimeUnit.SECONDS);
        }
    }
}
