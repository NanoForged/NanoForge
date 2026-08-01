package io.github.nanoforged.core;

import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModMetaParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CoreMod 目录扫描：遍历 coremods 目录下的 jar，解析 coremod.toml。
 *
 * <p>目录语义为「只放 coremod」：不含 coremod.toml 的 jar 不加载，
 * 记 WARN 日志后跳过（它可能是误放的普通模组或库）。
 */
public final class CoreModDiscovery {

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreModDiscovery");

    private CoreModDiscovery() {}

    /**
     * 扫描目录下所有 jar 并解析出 coremod 元数据列表（未排序）。
     *
     * @throws io.github.nanoforged.core.meta.CoreModMetaException 任一 coremod.toml 非法或 jar 不可读
     */
    public static List<CoreModMeta> scan(File coreModDir) {
        File[] files = coreModDir.listFiles();
        if (files == null) {
            throw new IllegalStateException("无法列出 coremod 目录: " + coreModDir);
        }

        List<CoreModMeta> discovered = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            Optional<CoreModMeta> meta = CoreModMetaParser.parse(file.toPath());
            if (meta.isPresent()) {
                LOGGER.info("发现 CoreMod: {} ({}) @ {}", meta.get().id(), meta.get().version(), file.getName());
                discovered.add(meta.get());
            } else {
                LOGGER.warn("{} 不含 coremod.toml，非 coremod，已跳过加载", file.getName());
            }
        }
        return discovered;
    }
}
