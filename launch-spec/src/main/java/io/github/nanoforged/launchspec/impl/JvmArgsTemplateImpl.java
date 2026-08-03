package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.JvmArgsOptions;
import io.github.nanoforged.launchspec.JvmArgsTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link JvmArgsTemplate} 的实现：按启动脚本 launch_nanoforge_ss.sh 的 JVM 参数
 * 基线逐项产出，顺序与脚本一致；可覆盖项（堆/栈/路径类）取自 {@link JvmArgsOptions}，
 * 固定项保持脚本基线。
 *
 * <p>脚本中每个 JVM 参数在此都有着落：可覆盖项见 {@link JvmArgsOptions} 各字段，
 * 固定项为下方逐行参数；脚本中非 JVM 参数（mesa_glthread 环境变量、RFB 主类与
 * --tweakClass）不在本模板范围，见接口注释。
 */
public final class JvmArgsTemplateImpl implements JvmArgsTemplate {

    @Override
    public List<String> resolve(JvmArgsOptions options) {
        Objects.requireNonNull(options, "options 不能为 null");
        List<String> args = new ArrayList<>();

        // 编码、字节码校验与诊断（脚本 85-91 行基线）
        args.add("-Dfile.encoding=UTF-8");
        args.add("-noverify");
        args.add("-XX:+UnlockDiagnosticVMOptions");
        args.add("-XX:+ShowCodeDetailsInExceptionMessages");
        args.add("-XX:+PrintCommandLineFlags");
        args.add("-XX:+TieredCompilation");
        args.add("-XX:+DisableExplicitGC");
        args.add("-XX:+AlwaysPreTouch");
        args.add("-XX:+ParallelRefProcEnabled");
        args.add("-XX:+UseZGC");
        args.add("-XX:ReservedCodeCacheSize=256m");

        // CompilerDirectives 文件（可覆盖，默认脚本基线 ./compiler_directives.txt）
        args.add("-XX:CompilerDirectivesFile=" + options.compilerDirectives());

        // XML 深度上限与字节码/排序兼容（脚本 97-100 行基线）
        args.add("-Djdk.xml.maxElementDepth=10000");
        args.add("-XX:-BytecodeVerificationLocal");
        args.add("-XX:-BytecodeVerificationRemote");
        args.add("-Djava.util.Arrays.useLegacyMergeSort=true");

        // Java 25 预览特性与 native access（脚本 101-102 行基线）
        args.add("--enable-preview");
        args.add("--enable-native-access=ALL-UNNAMED");

        // --add-opens（13 项，脚本 103-115 行基线）
        args.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.nio.Buffer.UNSAFE=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED");
        args.add("--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.lang.ref=ALL-UNNAMED");
        args.add("--add-opens=java.base/java.text=ALL-UNNAMED");
        args.add("--add-opens=java.desktop/java.awt.font=ALL-UNNAMED");
        args.add("--add-opens=java.desktop/java.awt.Rectangle=ALL-UNNAMED");
        args.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");

        // --add-exports（3 项，脚本 116-118 行基线）
        args.add("--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED");
        args.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        args.add("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED");

        // 堆与栈（脚本 119-121 行基线，可覆盖）
        args.add("-Xms" + options.heapMin());
        args.add("-Xmx" + options.heapMax());
        args.add("-Xss" + options.stackSize());

        // 游戏路径属性（脚本 122-125 行基线，可覆盖）
        args.add("-Dcom.fs.starfarer.settings.paths.saves=" + options.savesPath());
        args.add("-Dcom.fs.starfarer.settings.paths.screenshots=" + options.screenshotsPath());
        args.add("-Dcom.fs.starfarer.settings.paths.mods=" + options.modsPath());
        args.add("-Dcom.fs.starfarer.settings.paths.logs=" + options.logsPath());

        // 原生库路径（脚本 126 行基线，可覆盖，按 OS 分支由启动器替换）
        args.add("-Djava.library.path=" + options.libraryPath());

        // RFB 系统类加载器与游戏平台分支属性（脚本 127-130 行基线，固定）
        args.add("-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader");
        // deobf 全量反混淆开关（NanoForge 缺省开启；关闭时必须显式产出 false，
        // 否则缺省 true 会让关闭选项静默失效）
        args.add("-Dnanoforge.remap.obf2named=" + options.deobf());
        args.add("-Dssoptimizer.font.ttf.enable=true");
        args.add("-Dcom.fs.starfarer.settings.linux=true");

        return List.copyOf(args);
    }
}
