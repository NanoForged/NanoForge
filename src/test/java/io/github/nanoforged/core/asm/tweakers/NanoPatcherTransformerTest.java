package io.github.nanoforged.core.asm.tweakers;

import io.github.nanoforged.core.patch.ClassPatch;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * NanoPatcherTransformer 的真实逻辑验证：命中类名（点分 → 内部名转换）应用 patch，
 * 未命中与 null 输入原样透传。
 */
class NanoPatcherTransformerTest {

    private static ClassPatch patchOf(String className, byte[] original, byte[] patched) {
        MemoryDiff diff = MemoryDiffs.diff(original, patched);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            diff.serialize(DefaultSerialization.newInstance(), buffer);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        byte[] sha;
        try {
            sha = MessageDigest.getInstance("SHA-256").digest(original);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        return new ClassPatch(className, sha, buffer.toByteArray(), "test");
    }

    @Test
    void hitClassGetsPatched() {
        byte[] original = "原始类字节".getBytes(StandardCharsets.UTF_8);
        byte[] patched = "修改后类字节!!".getBytes(StandardCharsets.UTF_8);
        ClassPatch patch = patchOf("demo/Target", original, patched);
        NanoPatcherTransformer transformer = new NanoPatcherTransformer(Map.of("demo/Target", patch));

        // LaunchWrapper 传入点分类名，transformer 内部转换为内部名命中索引
        byte[] result = transformer.transform("demo.Target", "demo.Target", original);

        assertArrayEquals(patched, result);
    }

    @Test
    void unhitClassPassesThrough() {
        byte[] original = {1, 2, 3};
        byte[] other = {4, 5, 6};
        ClassPatch patch = patchOf("demo/Target", original, new byte[]{1, 2, 4});
        NanoPatcherTransformer transformer = new NanoPatcherTransformer(Map.of("demo/Target", patch));

        byte[] result = transformer.transform("demo.Other", "demo.Other", other);

        assertSame(other, result);
    }

    @Test
    void nullClassPassesThrough() {
        NanoPatcherTransformer transformer = new NanoPatcherTransformer(Map.of());

        assertNull(transformer.transform("demo.Any", "demo.Any", null));
    }
}
