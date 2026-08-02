package io.github.nanoforged.launchspec;

import java.nio.file.Path;

/**
 * 单个游戏 jar 的 named（反混淆）判定。
 *
 * <p>判定方法为「抽样已知类名存在性」：以 {@link GameJarKind} 上声明的采样特征检查
 * jar 条目——命中任一混淆特征类即判定为原版混淆产物，否则需全部 named 采样类命中
 * 才判定为 named。采样类名源自 SourceSector 0.9.8 全量 mapping 与 named 产物，
 * 属确定性静态判定（判定依据见 {@link GameJarKind} 注释）。
 */
public interface NamedJarProbe {

    /**
     * 判定给定 jar 是否为 named 产物。
     *
     * <p>jarFile 必须存在且为合法 zip，否则抛出异常（不返回「假 named」结果），
     * 由调用方决定如何记录失败。
     *
     * @param jarFile 待判定的 jar 文件
     * @param kind    该 jar 对应的游戏类别（提供采样特征）
     * @return named 判定结果及依据
     * @throws IllegalArgumentException jarFile 不存在或不是文件
     * @throws IllegalStateException    jar 无法作为 zip 读取
     */
    NamedVerdict probe(Path jarFile, GameJarKind kind);
}
