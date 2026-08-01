package io.github.nanoforged.core.patch;

import io.github.nanoforged.core.meta.CoreModMeta;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类级 bin patch 的运行时加载与应用。
 *
 * <p>按 coremod 依赖序从各 jar 读取 {@code [patch] entries} 声明的 patch 文件，
 * 冲突检测（同一类被两个 coremod patch 直接抛错并指明双方来源）后产出类名索引。
 * patch 在 transformer 链最前应用（先于 coremod ASM/Mixin），目标命名空间为 named；
 * 应用前先校验原类字节的 SHA-256 基线，游戏更新导致基线不符时显式抛错，不静默跳过。
 */
public final class PatcherManager {

    /** 运行时生效的 patch 索引，由 {@link #load} 写入，供 LaunchWrapper 无参实例化的 transformer 读取 */
    private static volatile Map<String, ClassPatch> activePatches = Map.of();

    private PatcherManager() {}

    /**
     * 加载全部 coremod 的 patch 文件，产出类名 → patch 索引。
     *
     * @param sortedMods 依赖排序后的 coremod 元数据
     * @return 类内部名到 patch 的映射（按加载顺序）
     * @throws PatchException patch 文件缺失/非法，或同类被多个 coremod patch
     */
    public static Map<String, ClassPatch> load(List<CoreModMeta> sortedMods) {
        Map<String, ClassPatch> patches = new LinkedHashMap<>();
        for (CoreModMeta meta : sortedMods) {
            if (meta.patchEntries().isEmpty()) {
                continue;
            }
            Path jar = Path.of(meta.source());
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                for (String entryName : meta.patchEntries()) {
                    JarEntry entry = jarFile.getJarEntry(entryName);
                    if (entry == null) {
                        throw new PatchException("coremod '" + meta.id() + "' 声明的 patch 文件不存在: "
                                + entryName + " (" + jar + ")");
                    }
                    byte[] bytes;
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    ClassPatch patch = PatchFormat.read(bytes, meta.id() + "!" + entryName);
                    ClassPatch existing = patches.putIfAbsent(patch.className(), patch);
                    if (existing != null) {
                        throw new PatchException("类 " + patch.className() + " 被两个 coremod patch: "
                                + existing.source() + " 与 " + patch.source());
                    }
                }
            } catch (IOException exception) {
                throw new PatchException("读取 coremod patch 失败: " + jar, exception);
            }
        }
        activePatches = Map.copyOf(patches);
        return patches;
    }

    /**
     * 当前运行时生效的 patch 索引（{@link #load} 最近一次产出的不可变副本）。
     * 未调用 load 或没有任何 patch 时为空表。
     */
    public static Map<String, ClassPatch> activePatches() {
        return activePatches;
    }

    /**
     * 校验基线并应用单个 patch。
     *
     * @param patch      类 patch
     * @param basicClass 运行时读到的原类字节
     * @return patched 类字节
     * @throws PatchException 基线 SHA-256 不符（游戏更新导致 patch 失效）
     */
    public static byte[] apply(ClassPatch patch, byte[] basicClass) {
        byte[] actual = sha256(basicClass);
        if (!Arrays.equals(actual, patch.baselineSha256())) {
            throw new PatchException("patch 基线校验失败: " + patch.className()
                    + "（期望 " + hex(patch.baselineSha256()) + "，实际 " + hex(actual)
                    + "，来源 " + patch.source() + "）");
        }
        MemoryDiff diff = new MemoryDiff();
        try {
            diff.deserialize(DefaultSerialization.newInstance(), new ByteArrayInputStream(patch.diff()));
        } catch (IOException exception) {
            throw new PatchException("badiff diff 反序列化失败: " + patch.className()
                    + "（来源 " + patch.source() + "）", exception);
        }
        return MemoryDiffs.apply(basicClass, diff);
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
