package io.github.nanoforged.launchspec;

import java.nio.file.Path;
import java.util.List;

/**
 * classpath 组装：把启动脚本 launch_nanoforge_ss.sh 中的手工 classpath 规则
 * 收敛为可测试的代码。
 *
 * <p>规则（与脚本一致）：
 * <ol>
 *   <li>core 段：扫描 {@code gameRoot/mods/nanoforge/*.jar}，按文件名排序置前；
 *       其中 {@code lwjgl-unsealed.jar} 归类 {@link ClasspathSource#OVERRIDE}，
 *       其余归类 {@link ClasspathSource#CORE}。目录不存在时 core 段为空
 *       （缺失由启动前置检查报告）。</li>
 *   <li>game 段：{@link #GAME_JAR_FILES} 固定清单（脚本 classpath 的游戏根部分，
 *       保持脚本顺序）逐项解析为 {@link ClasspathSource#GAME} 条目。</li>
 *   <li>排除规则：{@code log4j-1.2.9.jar}（由 core 段 log4j-over-slf4j 顶替）与
 *       {@code lwjgl.jar}（由 {@code lwjgl-unsealed.jar} 顶替）永不入列。</li>
 * </ol>
 *
 * <p>本组装只产出结构，不校验条目文件是否存在（存在性由启动前置检查负责），
 * 因此 game 段某 jar 缺失时仍产出对应条目；扫描目录不可读时抛出异常（不静默）。
 */
public interface ClasspathAssembler {

    /**
     * game 段固定清单：脚本 classpath 中位于游戏根目录的全部 jar 文件名
     * （已排除 log4j-1.2.9.jar 与 lwjgl.jar），顺序即 classpath 拼接顺序。
     */
    List<String> GAME_JAR_FILES = List.of(
            "janino.jar",
            "commons-compiler.jar",
            "commons-compiler-jdk.jar",
            "starfarer.api.jar",
            "starfarer_obf.jar",
            "jogg-0.0.7.jar",
            "jorbis-0.0.15.jar",
            "json.jar",
            "jinput.jar",
            "lwjgl_util.jar",
            "fs.sound_obf.jar",
            "fs.common_obf.jar",
            "xstream-1.4.21_miko.jar",
            "txw2-3.0.2.jar",
            "jaxb-api-2.4.0-b180830.0359.jar",
            "webp-imageio-0.1.6.jar");

    /**
     * 组装 classpath。
     *
     * @param gameRoot 游戏根目录，必须存在且为目录
     * @return 有序 classpath（core 段 + game 段）
     * @throws IllegalArgumentException gameRoot 不是目录或不存在
     * @throws IllegalStateException    mods/nanoforge 目录不可扫描
     */
    Classpath assemble(Path gameRoot);
}
