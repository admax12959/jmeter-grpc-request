package vn.zalopay.benchmark.core.protobuf;

import com.google.protobuf.DescriptorProtos;
import org.testng.Assert;
import org.testng.annotations.Test;

public class InlineMultiFileTest {
    @Test
    public void canCompileInlineWithPlainMultiFiles() {
        String inline = String.join("\n",
                "syntax = \"proto3\";",
                "package test;",
                "import \"foo/bar/imported.proto\";",
                "message A { foo.bar.B b = 1; }");

        String libs = String.join("\n",
                "=== file: foo/bar/imported.proto",
                "syntax = \"proto3\";",
                "package foo.bar;",
                "message B { int32 x = 1; }");

        ProtocInvoker inv = ProtocInvoker.forInline(inline, libs);
        DescriptorProtos.FileDescriptorSet fds = inv.invoke();
        Assert.assertTrue(fds.getFileCount() >= 2, "should contain inline and imported files");
    }
}

