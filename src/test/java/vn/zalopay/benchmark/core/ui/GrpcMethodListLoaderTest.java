package vn.zalopay.benchmark.core.ui;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GrpcMethodListLoaderTest {
    @Test
    public void returnsWorkerAndSupportsCancel() {
        javax.swing.SwingWorker<?, ?> w =
                GrpcMethodListLoader.loadAsync(".", "", false, new GrpcMethodListLoader.Callback() {
                    public void onSuccess(java.util.List<String> m) {}

                    public void onError(Throwable t) {}
                });
        Assert.assertNotNull(w);
        boolean canceled = w.cancel(true);
        Assert.assertTrue(canceled || w.isDone());
    }
}

