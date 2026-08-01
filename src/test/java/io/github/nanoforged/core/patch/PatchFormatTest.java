package io.github.nanoforged.core.patch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .binpatch 文件格式读写的真实逻辑验证：roundtrip 保真与各类非法输入的显式报错。
 */
class PatchFormatTest {

    private static final String CLASS_NAME = "com/fs/starfarer/api/SomeClass";

    private static byte[] sha() {
        byte[] sha = new byte[32];
        for (int i = 0; i < sha.length; i++) {
            sha[i] = (byte) i;
        }
        return sha;
    }

    @Test
    void writeReadRoundtrip() {
        byte[] diff = {1, 2, 3, 4, 5};
        byte[] file = PatchFormat.write(CLASS_NAME, sha(), diff);

        ClassPatch patch = PatchFormat.read(file, "test-source");

        assertEquals(CLASS_NAME, patch.className());
        assertArrayEquals(sha(), patch.baselineSha256());
        assertArrayEquals(diff, patch.diff());
        assertEquals("test-source", patch.source());
    }

    @Test
    void badMagicFails() {
        byte[] file = PatchFormat.write(CLASS_NAME, sha(), new byte[]{9});
        file[0] = 'X';

        PatchException e = assertThrows(PatchException.class, () -> PatchFormat.read(file, "bad-magic"));
        assertTrue(e.getMessage().contains("魔数"), e.getMessage());
        assertTrue(e.getMessage().contains("bad-magic"), e.getMessage());
    }

    @Test
    void unsupportedVersionFails() {
        byte[] file = PatchFormat.write(CLASS_NAME, sha(), new byte[]{9});
        // version 是偏移 4 处的 big-endian short，置为 2
        file[4] = 0;
        file[5] = 2;

        PatchException e = assertThrows(PatchException.class, () -> PatchFormat.read(file, "bad-version"));
        assertTrue(e.getMessage().contains("版本"), e.getMessage());
    }

    @Test
    void truncatedFileFails() {
        byte[] file = PatchFormat.write(CLASS_NAME, sha(), new byte[]{1, 2, 3});
        // 截掉尾部 diff 与部分基线哈希
        byte[] truncated = Arrays.copyOf(file, file.length - 20);

        PatchException e = assertThrows(PatchException.class, () -> PatchFormat.read(truncated, "truncated"));
        assertTrue(e.getMessage().contains("截断"), e.getMessage());
        assertTrue(e.getMessage().contains("truncated"), e.getMessage());
    }

    @Test
    void emptyDiffFails() {
        byte[] file = PatchFormat.write(CLASS_NAME, sha(), new byte[0]);

        PatchException e = assertThrows(PatchException.class, () -> PatchFormat.read(file, "empty-diff"));
        assertTrue(e.getMessage().contains("diff 为空"), e.getMessage());
    }

    @Test
    void wrongShaLengthRejectedOnWrite() {
        PatchException e = assertThrows(PatchException.class,
                () -> PatchFormat.write(CLASS_NAME, new byte[16], new byte[]{1}));
        assertTrue(e.getMessage().contains("32 字节"), e.getMessage());
    }
}
