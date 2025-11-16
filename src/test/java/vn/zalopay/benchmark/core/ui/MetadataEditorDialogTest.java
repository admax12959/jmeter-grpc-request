package vn.zalopay.benchmark.core.ui;

import org.testng.Assert;
import org.testng.annotations.Test;

public class MetadataEditorDialogTest {
    @Test
    public void binKeyGetsBase64Encoded() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            throw new org.testng.SkipException("Headless");
        }
        MetadataEditorDialog dialog = new MetadataEditorDialog(null);
        dialog.loadFromString("{\"token-bin\":\"plain\"}");
        String out = dialog.toJsonString();
        // "plain" -> base64 cGxhaW4=
        Assert.assertTrue(out.contains("token-bin"));
        Assert.assertTrue(out.contains("cGxhaW4="));
    }
}
