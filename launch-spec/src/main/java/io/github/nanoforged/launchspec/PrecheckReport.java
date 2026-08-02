package io.github.nanoforged.launchspec;

import java.nio.file.Path;
import java.util.List;

/**
 * 一次启动的完整前置检查报告：组合安装探测、classpath 组装与 JVM 参数模板，
 * 同时给出校验结论与可直接使用的启动输入（classpath、JVM 参数）。
 *
 * @param gameRoot        检查的游戏根目录
 * @param install         安装探测结果（布局校验 + named 判定）
 * @param classpathChecks classpath 逐条目存在性校验
 * @param invariantChecks mods/nanoforge 目录、log4j/lwjgl 顶替等一致性校验
 * @param classpath       组装出的 classpath（ready 时可直接使用）
 * @param jvmArgs         JVM 参数列表（由覆盖项解析）
 * @param ready           所有校验全部通过；false 时启动器应中止启动并展示失败项
 */
public record PrecheckReport(
        Path gameRoot,
        InstallReport install,
        List<InstallCheck> classpathChecks,
        List<InstallCheck> invariantChecks,
        Classpath classpath,
        List<String> jvmArgs,
        boolean ready) {
}
