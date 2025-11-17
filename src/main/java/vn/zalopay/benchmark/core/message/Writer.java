package vn.zalopay.benchmark.core.message;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.zalopay.benchmark.core.specification.GrpcResponse;

public class Writer<T extends Message> implements StreamObserver<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Writer.class);

    private final JsonFormat.Printer jsonPrinter;
    private final GrpcResponse grpcResponse;

    Writer(JsonFormat.Printer jsonPrinter, GrpcResponse grpcResponse) {
        this.jsonPrinter = jsonPrinter.preservingProtoFieldNames().includingDefaultValueFields();
        this.grpcResponse = grpcResponse;
    }

    /** Creates a new Writer which writes the messages it sees to the supplied Output. */
    public static <T extends Message> Writer<T> create(
            GrpcResponse grpcResponse, JsonFormat.TypeRegistry registry) {
        return new Writer<>(JsonFormat.printer().usingTypeRegistry(registry), grpcResponse);
    }

    @Override
    public void onCompleted() {
        try {
            String msg = grpcResponse.getGrpcMessageString();
            int size = msg == null ? 0 : msg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            LOGGER.info("[GRPC] Stream completed. success={} sizeBytes={}", grpcResponse.isSuccess(), size);
        } catch (Exception e) {
            LOGGER.debug("[GRPC] Stream completed (size calc failed): {}", e.getMessage());
        }
    }

    @Override
    public void onError(Throwable throwable) {
        grpcResponse.setSuccess(false);
        grpcResponse.setThrowable(throwable);
        LOGGER.error("[GRPC] Stream error: {}", throwable.toString());
    }

    @Override
    public void onNext(T message) {
        try {
            grpcResponse.setSuccess(true);
            grpcResponse.storeGrpcMessage(jsonPrinter.print(message));
            String s = grpcResponse.getGrpcMessageString();
            int size = s == null ? 0 : s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            LOGGER.info("[GRPC] Received message sizeBytes={}", size);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warn(e.getMessage());
            grpcResponse.storeGrpcMessage(message.toString());
        }
    }
}
