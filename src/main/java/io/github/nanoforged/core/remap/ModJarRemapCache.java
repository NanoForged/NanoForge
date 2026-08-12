package io.github.nanoforged.core.remap;

import io.github.nanoforged.utils.PathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 挂载模组 jar 的整 jar remap 缓存：为「取 CodeSource 当 classpath 根自建
 * URLClassLoader」的模组模式（如 Ship Mastery 的 ReflectionEnabledClassLoader）
 * 提供一份已 obf→named 改写的 jar 副本。
 *
 * <p>背景：{@link ModJarMounter} 把模组 jar 挂进 LaunchClassLoader 后，经 LCL
 * 加载的模组类会走 {@code NanoRemapTransformer} 改写；但模组自建子类加载器
 * 直接 findClass 读 jar 原始字节码，完全绕过 transformer 链，类常量池里的
 * obf 引用（如 {@code com.fs.starfarer.campaign.ui.UITable}）在 named 运行时
 * 不存在，触发 ClassNotFoundException。{@link CodeSourceSupport} 把 CodeSource
 * URL 还原为 jar 根后，经本类重定向到 remap 副本，使子类加载器读到 named 字节码。
 *
 * <p>副本按源 jar 路径 + mtime + 尺寸命名，缓存在
 * {@code <mods>/nanoforge/remapped-mods/}，首次请求时懒惰生成。
 */
public final class ModJarRemapCache {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Remap");

    /** 已挂载的模组 jar 规范路径，由 {@link ModJarMounter} 登记 */
    private static final Set<Path> MOUNTED_MOD_JARS = ConcurrentHashMap.newKeySet();
    /** 源 jar → remap 副本路径，生成完成后登记 */
    private static final Map<Path, Path> REMAPPED_JARS = new ConcurrentHashMap<>();

    private ModJarRemapCache() {
    }

    /**
     * 登记一个已挂载的模组 jar，使其 CodeSource 重定向到 remap 副本。
     *
     * @param jarPath 模组 jar 文件路径（{@code ScriptStore.jarFiles} 原始值）
     */
    public static void registerMountedJar(String jarPath) {
        MOUNTED_MOD_JARS.add(Path.of(jarPath).toAbsolutePath().normalize());
    }

    /**
     * 把指向已挂载模组 jar 的文件 URL 重定向到其 remap 副本；其余 URL 原样返回。
     *
     * <p>remap 上下文未激活（obf 运行模式）、非 file 协议、非已登记模组 jar
     * 时均不干预。
     *
     * @param jarFileUrl jar 文件 URL（{@link CodeSourceSupport#toJarFileUrl} 还原后的根形态）
     * @return remap 副本的 jar 文件 URL，或原 URL
     */
    public static URL remappedUrlOrOriginal(URL jarFileUrl) {
        NanoRemapContext context = NanoRemapContext.activeContext();
        if (context == null || !"file".equals(jarFileUrl.getProtocol())) {
            return jarFileUrl;
        }

        Path jarPath;
        try {
            jarPath = Path.of(jarFileUrl.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return jarFileUrl;
        }
        if (!MOUNTED_MOD_JARS.contains(jarPath)) {
            return jarFileUrl;
        }

        Path remapped = REMAPPED_JARS.computeIfAbsent(jarPath, path -> remapJar(path, context));
        try {
            return remapped.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("remap 副本路径无法转换为 URL: " + remapped, e);
        }
    }

    /**
     * 整 jar 改写：.class 条目经 {@link BytecodeRemapper} 翻译为 named，
     * 其余资源原样拷贝。单个类改写失败沿用运行时语义 WARN 后放行原字节码。
     */
    private static Path remapJar(Path sourceJar, NanoRemapContext context) {
        try {
            Path cacheDir = PathUtils.getModsPath().resolve("nanoforge").resolve("remapped-mods");
            Files.createDirectories(cacheDir);
            String cacheName = sourceJar.getFileName()
                    + "." + Files.getLastModifiedTime(sourceJar).toMillis()
                    + "." + Files.size(sourceJar)
                    + ".remapped.jar";
            Path target = cacheDir.resolve(cacheName);
            if (Files.isRegularFile(target)) {
                return target;
            }

            long startNanos = System.nanoTime();
            Path tempTarget = target.resolveSibling(cacheName + ".tmp");
            int remappedClasses = 0;
            Set<String> writtenEntries = new HashSet<>();
            try (ZipFile zip = new ZipFile(sourceJar.toFile());
                 OutputStream fileOut = Files.newOutputStream(tempTarget);
                 ZipOutputStream out = new ZipOutputStream(fileOut)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    byte[] content;
                    try (InputStream in = zip.getInputStream(entry)) {
                        content = in.readAllBytes();
                    }

                    String outputName = entry.getName();
                    if (entry.getName().endsWith(".class")) {
                        try {
                            BytecodeRemapper.RemappedClass remapped = context.remapArchiveEntry(content);
                            outputName = remapped.outputInternalName() + ".class";
                            content = remapped.bytecode();
                            if (remapped.modified()) {
                                remappedClasses++;
                            }
                        } catch (Throwable throwable) {
                            LOGGER.warn("整 jar remap 单个类失败，按原样写入: {} !/ {}",
                                    sourceJar.getFileName(), entry.getName(), throwable);
                        }
                    }
                    if (!writtenEntries.add(outputName)) {
                        throw new IllegalStateException(
                                "remap 后条目名冲突: " + sourceJar + " !/ " + outputName);
                    }
                    out.putNextEntry(new ZipEntry(outputName));
                    out.write(content);
                    out.closeEntry();
                }
            }
            Files.move(tempTarget, target, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("模组 jar remap 副本生成: {} ({} 个类改写, {} ms)",
                    target.getFileName(), remappedClasses, (System.nanoTime() - startNanos) / 1_000_000);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("模组 jar remap 副本生成失败: " + sourceJar, e);
        }
    }
}
