package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.GameInstallProbe;
import io.github.nanoforged.launchspec.GameJarKind;
import io.github.nanoforged.launchspec.InstallCheck;
import io.github.nanoforged.launchspec.InstallReport;
import io.github.nanoforged.launchspec.NamedJarProbe;
import io.github.nanoforged.launchspec.NamedVerdict;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link GameInstallProbe} 的实现：对 4 个游戏 jar 逐项做存在性校验，
 * 再对存在的 jar 做 named 判定（缺失 jar 记为「跳过 named 判定」的失败结果）。
 *
 * <p>游戏根目录非法时不抛异常：布局校验逐项失败并说明原因，named 判定全部为
 * 非 named（跳过），保证探测报告始终完整可展示。
 */
public final class GameInstallProbeImpl implements GameInstallProbe {

    private final NamedJarProbe namedJarProbe;

    public GameInstallProbeImpl(NamedJarProbe namedJarProbe) {
        this.namedJarProbe = Objects.requireNonNull(namedJarProbe, "namedJarProbe 不能为 null");
    }

    @Override
    public InstallReport probe(Path gameRoot) {
        Objects.requireNonNull(gameRoot, "gameRoot 不能为 null");
        boolean rootValid = Files.isDirectory(gameRoot);

        List<InstallCheck> layoutChecks = new ArrayList<>();
        List<NamedVerdict> namedVerdicts = new ArrayList<>();
        for (GameJarKind kind : GameJarKind.values()) {
            Path jar = gameRoot.resolve(kind.fileName());
            boolean exists = rootValid && Files.isRegularFile(jar);
            layoutChecks.add(new InstallCheck(
                    "游戏 jar 存在: " + kind.fileName(),
                    exists,
                    exists ? null : (rootValid
                            ? "文件缺失: " + jar
                            : "游戏根目录不存在或不是目录: " + gameRoot)));
            if (exists) {
                try {
                    namedVerdicts.add(namedJarProbe.probe(jar, kind));
                } catch (RuntimeException e) {
                    namedVerdicts.add(new NamedVerdict(kind, false,
                            "无法完成 named 判定: " + e.getMessage()));
                }
            } else {
                namedVerdicts.add(new NamedVerdict(kind, false, "jar 不存在，跳过 named 判定"));
            }
        }

        boolean ready = layoutChecks.stream().allMatch(InstallCheck::passed)
                && namedVerdicts.stream().allMatch(NamedVerdict::named);
        return new InstallReport(gameRoot, List.copyOf(layoutChecks), List.copyOf(namedVerdicts), ready);
    }
}
