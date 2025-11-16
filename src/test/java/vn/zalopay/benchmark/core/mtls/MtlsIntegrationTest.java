package vn.zalopay.benchmark.core.mtls;

import com.google.common.net.HostAndPort;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.Server;
import io.grpc.Metadata;
import io.grpc.Context;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ForwardingServerCallListener;
import io.grpc.ServerInterceptors;
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

        io.grpc.netty.shaded.io.netty.handler.ssl.SslContext sslContext =
                io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.forServer(serverCert, serverKey)
                        .trustManager(clientCert)
                        .clientAuth(io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth.REQUIRE)
                        .build();
        final Metadata.Key<byte[]> TOKEN_BIN =
                Metadata.Key.of("token-bin", Metadata.BINARY_BYTE_MARSHALLER);
        final Context.Key<byte[]> CTX_TOKEN = Context.key("token-bin");

        ServerInterceptor captureBinToken =
                new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call,
                            Metadata headers,
                            ServerCallHandler<ReqT, RespT> next) {
                        byte[] token = headers.get(TOKEN_BIN);
                        Context ctx = Context.current().withValue(CTX_TOKEN, token);
                        return io.grpc.Contexts.interceptCall(ctx, call, headers, next);
                    }
                };

        Server server =
                io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder.forPort(port)
                        .intercept(captureBinToken)
                        .sslContext(sslContext)
                        .addService(
                                ServerServiceDefinition.builder("echo.EchoService")
                                        .addMethod(
                                                md,
                                                ServerCalls.asyncUnaryCall(
                                                        (request, responseObserver) -> {
                                                            // Build EchoReply(message = request.message)
                                                            DynamicMessage resp =
                                                                    DynamicMessage.newBuilder(m.getOutputType())
                                                                            .setField(
                                                                                    m.getOutputType()
                                                                                            .findFieldByName(
                                                                                                    "message"),
                                                                                    appendToken(
                                                                                            request.getField(
                                                                                            request.getDescriptorForType()
                                                                                                    .findFieldByName(
                                                                                                            "message")),
                                                                                            CTX_TOKEN.get()))
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
            try {
            // Send binary metadata token-bin = base64("bin-value")
            caller.buildRequestAndMetadata(
                    "{\"message\":\"hello\"}", "{\"token-bin\":\"YmluLXZhbHVl\"}");
            String resp = caller.call("2000").getGrpcMessageString();
            Assert.assertTrue(resp.contains("hello"));
            Assert.assertTrue(resp.contains("bin-value"));
            } finally {
                caller.shutdownNettyChannel();
            }
        } finally {
            server.shutdown();
            server.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    private static Object appendToken(Object original, byte[] token) {
        String msg = String.valueOf(original);
        if (token != null) {
            try {
                String bin = new String(token, java.nio.charset.StandardCharsets.UTF_8);
                return msg + " token=" + bin;
            } catch (Exception ignore) {
                return msg;
            }
        }
        return msg;
    }
}
