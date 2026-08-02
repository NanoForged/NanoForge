package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.GameJarKind;
import io.github.nanoforged.launchspec.NamedJarProbe;
import io.github.nanoforged.launchspec.NamedVerdict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

/**
 * {@link NamedJarProbe} 的抽样实现：以 jar 条目存在性做静态判定。
 *
 * <p>判定顺序：先查混淆特征类（命中任一 → 原版混淆产物，非 named），再查 named
 * 采样类（全部命中 → named）。采样类名与判定依据见 {@link GameJarKind} 注释。
 */
public final class SampleNamedJarProbe implements NamedJarProbe {

    @Override
    public NamedVerdict probe(Path jarFile, GameJarKind kind) {
        Objects.requireNonNull(jarFile, "jarFile 不能为 null");
        Objects.requireNonNull(kind, "kind 不能为 null");
        if (!Files.isRegularFile(jarFile)) {
            throw new IllegalArgumentException("待判定的 jar 不存在或不是文件: " + jarFile);
        }
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            List<String> obfHits = kind.obfuscatedSamples().stream()
                    .filter(sample -> zip.getEntry(sample + ".class") != null)
                    .toList();
            if (!obfHits.isEmpty()) {
                return new NamedVerdict(kind, false,
                        "命中混淆特征类（原版混淆产物）: " + obfHits);
            }
            List<String> missingSamples = kind.namedSamples().stream()
                    .filter(sample -> zip.getEntry(sample + ".class") == null)
                    .toList();
            if (!missingSamples.isEmpty()) {
                return new NamedVerdict(kind, false,
                        "未命中 named 采样类: " + missingSamples);
            }
            return new NamedVerdict(kind, true, "全部 named 采样类命中且无混淆特征");
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 jar: " + jarFile, e);
        }
    }
}
