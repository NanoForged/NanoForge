package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.Classpath;
import io.github.nanoforged.launchspec.ClasspathAssembler;
import io.github.nanoforged.launchspec.ClasspathEntry;
import io.github.nanoforged.launchspec.ClasspathSource;
import io.github.nanoforged.launchspec.GameInstallProbe;
import io.github.nanoforged.launchspec.InstallCheck;
import io.github.nanoforged.launchspec.InstallReport;
import io.github.nanoforged.launchspec.JvmArgsOptions;
import io.github.nanoforged.launchspec.JvmArgsTemplate;
import io.github.nanoforged.launchspec.LaunchPrecheck;
import io.github.nanoforged.launchspec.PrecheckReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * {@link LaunchPrecheck} 的实现：组合安装探测、classpath 组装与 JVM 参数模板，
 * 对一次启动做完整前置校验并产出报告。
 *
 * <p>校验项：安装布局与 named 判定（委托 {@link GameInstallProbe}）、classpath
 * 逐条目存在性、{@code mods/nanoforge} 目录存在、core 段非空、log4j-1.2.9.jar
 * 排除并由 log4j-over-slf4j 顶替、lwjgl.jar 由 lwjgl-unsealed.jar 顶替。
 * 游戏根目录非法时 classpath 为空并给出失败校验，不抛异常。
 */
public final class LaunchPrecheckImpl implements LaunchPrecheck {

    private final GameInstallProbe installProbe;
    private final ClasspathAssembler classpathAssembler;
    private final JvmArgsTemplate jvmArgsTemplate;

    public LaunchPrecheckImpl(GameInstallProbe installProbe,
                              ClasspathAssembler classpathAssembler,
                              JvmArgsTemplate jvmArgsTemplate) {
        this.installProbe = Objects.requireNonNull(installProbe, "installProbe 不能为 null");
        this.classpathAssembler = Objects.requireNonNull(classpathAssembler, "classpathAssembler 不能为 null");
        this.jvmArgsTemplate = Objects.requireNonNull(jvmArgsTemplate, "jvmArgsTemplate 不能为 null");
    }

    @Override
    public PrecheckReport check(Path gameRoot, JvmArgsOptions jvmOptions) {
        Objects.requireNonNull(gameRoot, "gameRoot 不能为 null");
        Objects.requireNonNull(jvmOptions, "jvmOptions 不能为 null");

        InstallReport install = installProbe.probe(gameRoot);
        boolean rootValid = Files.isDirectory(gameRoot);

        Classpath classpath;
        List<InstallCheck> classpathChecks;
        List<InstallCheck> invariantChecks;
        if (rootValid) {
            classpath = classpathAssembler.assemble(gameRoot);
            classpathChecks = classpath.entries().stream()
                    .map(entry -> {
                        boolean exists = Files.isRegularFile(entry.file());
                        return new InstallCheck(
                                "classpath 条目存在: " + entry.file() + " (" + entry.source() + ")",
                                exists,
                                exists ? null : "文件不存在");
                    })
                    .toList();
            invariantChecks = List.of(
                    coreDirCheck(gameRoot),
                    coreNonEmptyCheck(classpath),
                    log4jOverrideCheck(gameRoot, classpath),
                    lwjglOverrideCheck(classpath));
        } else {
            classpath = new Classpath(List.of());
            classpathChecks = List.of(new InstallCheck(
                    "游戏根目录", false, "不是目录或不存在: " + gameRoot));
            invariantChecks = List.of();
        }

        boolean ready = install.ready()
                && classpathChecks.stream().allMatch(InstallCheck::passed)
                && invariantChecks.stream().allMatch(InstallCheck::passed);
        return new PrecheckReport(gameRoot, install, List.copyOf(classpathChecks),
                List.copyOf(invariantChecks), classpath, jvmArgsTemplate.resolve(jvmOptions), ready);
    }

    /** mods/nanoforge 目录存在性校验。 */
    private static InstallCheck coreDirCheck(Path gameRoot) {
        Path coreDir = gameRoot.resolve("mods").resolve("nanoforge");
        boolean ok = Files.isDirectory(coreDir);
        return new InstallCheck("mods/nanoforge 目录存在", ok, ok ? null : "目录缺失: " + coreDir);
    }

    /** core 段非空校验（无任何 *.jar 说明未执行 deployToGame 部署）。 */
    private static InstallCheck coreNonEmptyCheck(Classpath classpath) {
        boolean ok = classpath.entries().stream()
                .anyMatch(entry -> entry.source() == ClasspathSource.CORE
                        || entry.source() == ClasspathSource.OVERRIDE);
        return new InstallCheck("core 段非空（已部署 NanoForge 运行时）", ok,
                ok ? null : "mods/nanoforge/ 下无任何 *.jar，请先执行 deployToGame 部署");
    }

    /**
     * log4j 顶替不变量：游戏根目录存在 log4j-1.2.9.jar 时，classpath 不得含同名
     * 条目且必须含 log4j-over-slf4j 顶替条目（统一汇入 log4j2）。前提不成立时
     * 视为通过并说明。
     */
    private static InstallCheck log4jOverrideCheck(Path gameRoot, Classpath classpath) {
        String name = "log4j-1.2.9.jar 排除并由 log4j-over-slf4j 顶替";
        if (!Files.isRegularFile(gameRoot.resolve("log4j-1.2.9.jar"))) {
            return new InstallCheck(name, true, "游戏根目录无 log4j-1.2.9.jar，无需排除");
        }
        boolean excluded = classpath.entries().stream()
                .noneMatch(entry -> entry.file().getFileName().toString().equals("log4j-1.2.9.jar"));
        boolean overridden = classpath.entries().stream()
                .anyMatch(entry -> entry.file().getFileName().toString().startsWith("log4j-over-slf4j"));
        boolean ok = excluded && overridden;
        String reason = ok ? null : (excluded ? "缺 log4j-over-slf4j 顶替条目" : "classpath 仍含 log4j-1.2.9.jar");
        return new InstallCheck(name, ok, reason);
    }

    /**
     * lwjgl 顶替不变量：classpath 必须含 OVERRIDE 条目（mods/nanoforge/
     * lwjgl-unsealed.jar，RFB 密封校验兼容副本），且不得含游戏根 lwjgl.jar。
     */
    private static InstallCheck lwjglOverrideCheck(Classpath classpath) {
        boolean unsealedPresent = classpath.entries().stream()
                .anyMatch(entry -> entry.source() == ClasspathSource.OVERRIDE);
        boolean gameLwjglAbsent = classpath.entries().stream()
                .noneMatch(entry -> entry.file().getFileName().toString().equals("lwjgl.jar"));
        boolean ok = unsealedPresent && gameLwjglAbsent;
        String reason = ok ? null : (unsealedPresent
                ? "classpath 含 lwjgl.jar（应被 lwjgl-unsealed.jar 顶替）"
                : "缺 lwjgl-unsealed.jar（OVERRIDE）条目");
        return new InstallCheck("lwjgl.jar 由 lwjgl-unsealed.jar 顶替", ok, reason);
    }
}
