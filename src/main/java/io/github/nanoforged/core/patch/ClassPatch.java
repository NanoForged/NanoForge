package io.github.nanoforged.core.patch;

import java.util.Objects;

/**
 * 单个类的 bin patch（纯数据）。
 *
 * <p>patch 目标命名空间为 named：{@code baselineSha256} 是原 named 类字节的基线哈希，
 * {@code diff} 是 badiff 序列化字节（原 named 类 → 修改后类）。
 * 运行时按类名命中后先校验基线再应用，见 {@link PatcherManager#apply}。
 *
 * @param className      目标类内部名（如 {@code com/fs/starfarer/settings/StarfarerSettings}）
 * @param baselineSha256 原 named 类字节的 SHA-256（32 字节）
 * @param diff           badiff 序列化字节
 * @param source         诊断用来源（生成侧为 patched 类文件，运行侧为 coremod jar 内条目）
 */
public record ClassPatch(String className, byte[] baselineSha256, byte[] diff, String source) {

    public ClassPatch {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(baselineSha256, "baselineSha256");
        if (baselineSha256.length != 32) {
            throw new IllegalArgumentException("baselineSha256 必须是 32 字节: " + source);
        }
        Objects.requireNonNull(diff, "diff");
        Objects.requireNonNull(source, "source");
    }
}
