package vn.zalopay.benchmark.core.ui;

import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.zalopay.benchmark.core.ClientList;
import vn.zalopay.benchmark.core.protobuf.ServiceResolver;

/**
 * Loads full gRPC method names from proto roots in a background worker.
 *
 * <p>Design:
 * - UI-neutral: no Swing components inside, just a simple callback.
 * - Provides both synchronous and asynchronous loading to support tests and UI.
 */
public final class GrpcMethodListLoader {
    private static final Logger log = LoggerFactory.getLogger(GrpcMethodListLoader.class);

    public interface Callback {
        void onSuccess(List<String> methods);

        void onError(Throwable t);
    }

    private GrpcMethodListLoader() {}

    /**
     * Load methods synchronously (blocking). Prefer this in tests for determinism.
     */
    public static List<String> loadSync(String protoFolder, String libFolder, boolean reload)
            throws Exception {
        ServiceResolver resolver = ClientList.getServiceResolver(protoFolder, libFolder, reload);
        return ClientList.listServices(resolver);
    }

    /**
     * Load methods asynchronously using SwingWorker. The callback is executed on EDT.
     */
    public static SwingWorker<List<String>, Void> loadAsync(
            String protoFolder, String libFolder, boolean reload, Callback callback) {
        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return loadSync(protoFolder, libFolder, reload);
            }

            @Override
            protected void done() {
                try {
                    callback.onSuccess(get());
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error loading gRPC methods", e);
                    callback.onError(e.getCause() != null ? e.getCause() : e);
                }
            }
        };
        worker.execute();
        return worker;
    }
}
