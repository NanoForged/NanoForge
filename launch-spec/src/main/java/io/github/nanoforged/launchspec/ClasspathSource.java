package io.github.nanoforged.launchspec;

/**
 * classpath 条目的来源类别。
 */
public enum ClasspathSource {
    /** mods/nanoforge/*.jar 扫描产物（NanoForge 主 jar 与运行时依赖）。 */
    CORE,
    /** 游戏根目录固定清单中的库（janino/xstream/lwjgl_util 等，脚本顺序）。 */
    GAME,
    /** 顶替游戏根目录同名 jar 的专用条目（lwjgl-unsealed.jar，RFB 密封校验兼容副本）。 */
    OVERRIDE
}
