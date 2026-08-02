package io.github.nanoforged.launchspec;

import java.nio.file.Path;

/**
 * 启动前置检查：组合安装探测、classpath 组装与 JVM 参数模板，对一次启动做完整
 * 校验（安装布局、named 判定、classpath 条目存在性、mods/nanoforge 目录、
 * log4j/lwjgl 顶替不变量），输出结构化报告对象。
 *
 * <p>本检查只读不修改游戏目录；任一项校验失败时报告 {@code ready=false}，
 * 由启动器决定中止并展示原因。
 */
public interface LaunchPrecheck {

    /**
     * 对一次启动做完整前置检查。
     *
     * @param gameRoot   游戏根目录，不能为 null
     * @param jvmOptions JVM 参数覆盖项，不能为 null
     * @return 结构化前置检查报告
     */
    PrecheckReport check(Path gameRoot, JvmArgsOptions jvmOptions);
}
