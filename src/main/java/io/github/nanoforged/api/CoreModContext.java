package io.github.nanoforged.api;

import io.github.nanoforged.core.meta.CoreModMeta;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * coremod 装配完成后的运行上下文，替代旧的 injectData(Map) 自由传参。
 *
 * @param meta        本 coremod 的元数据（coremod.toml 解析结果）
 * @param gameHome    游戏安装目录
 * @param savesPath   存档目录
 * @param modsPath    模组目录
 * @param screenshotsPath 截图目录
 * @param logger      按 coremod id 命名的日志器
 */
public record CoreModContext(
        CoreModMeta meta,
        Path gameHome,
        Path savesPath,
        Path modsPath,
        Path screenshotsPath,
        Logger logger) {
}
