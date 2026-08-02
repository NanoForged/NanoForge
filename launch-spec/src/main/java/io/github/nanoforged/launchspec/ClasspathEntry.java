package io.github.nanoforged.launchspec;

import java.nio.file.Path;

/**
 * 单条 classpath 条目：jar 路径 + 来源类别。
 *
 * @param file   条目指向的 jar 文件路径（组装时以游戏根目录解析）
 * @param source 条目来源类别（coremod/game/override）
 */
public record ClasspathEntry(Path file, ClasspathSource source) {
}
