package io.github.nanoforged.core.patch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@code .binpatch} 文件格式（纯逻辑读写）。
 *
 * <pre>
 * magic    4B   "NFBP"
 * version  2B   1
 * classLen 2B   内部名 UTF 长度
 * class    NB   类内部名
 * sha256   32B  原 named 类字节基线
 * diff     余量 badiff 序列化字节
 * </pre>
 *
 * 读取时做魔数/版本/长度校验，非法文件抛 {@link PatchException} 并指明来源。
 */
public final class PatchFormat {

    private static final byte[] MAGIC = {'N', 'F', 'B', 'P'};
    private static final int VERSION = 1;

    private PatchFormat() {}

    /**
     * 序列化一个类 patch 为 {@code .binpatch} 文件字节。
     */
    public static byte[] write(String className, byte[] baselineSha256, byte[] diff) {
        byte[] classBytes = className.getBytes(StandardCharsets.UTF_8);
        if (classBytes.length > 0xFFFF) {
            throw new PatchException("类名过长，无法写入 binpatch: " + className);
        }
        if (baselineSha256.length != 32) {
            throw new PatchException("基线 SHA-256 必须是 32 字节: " + className);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(classBytes.length);
            out.write(classBytes);
            out.write(baselineSha256);
            out.write(diff);
        } catch (IOException exception) {
            // ByteArrayOutputStream 不会抛 IOException，出现即 JDK 行为变更，显式暴露
            throw new IllegalStateException("写入内存缓冲区失败: " + className, exception);
        }
        return buffer.toByteArray();
    }

    /**
     * 从文件字节解析类 patch。
     *
     * @param fileBytes {@code .binpatch} 文件字节
     * @param source    诊断用来源（jar 路径 + 条目名或文件路径）
     * @throws PatchException 魔数/版本不符或文件截断
     */
    public static ClassPatch read(byte[] fileBytes, String source) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(fileBytes))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (magic.length != MAGIC.length || !java.util.Arrays.equals(magic, MAGIC)) {
                throw new PatchException("binpatch 魔数不符: " + source);
            }
            int version = in.readShort();
            if (version != VERSION) {
                throw new PatchException("binpatch 版本不支持（" + version + "，支持 " + VERSION + "）: " + source);
            }
            int classLength = in.readShort() & 0xFFFF;
            byte[] classBytes = in.readNBytes(classLength);
            if (classBytes.length != classLength) {
                throw new PatchException("binpatch 文件截断（类名不完整）: " + source);
            }
            String className = new String(classBytes, StandardCharsets.UTF_8);
            byte[] sha256 = in.readNBytes(32);
            if (sha256.length != 32) {
                throw new PatchException("binpatch 文件截断（基线哈希不完整）: " + source);
            }
            byte[] diff = in.readAllBytes();
            if (diff.length == 0) {
                throw new PatchException("binpatch 文件截断（diff 为空）: " + source);
            }
            return new ClassPatch(className, sha256, diff, source);
        } catch (IOException exception) {
            throw new PatchException("binpatch 读取失败（文件截断）: " + source, exception);
        }
    }
}
