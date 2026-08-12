package io.github.nanoforged.core.remap;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * CodeSource 位置修复：RFB LaunchClassLoader 对被 transformer 改写的类赋予
 * {@code jar:file:...jar!/entry} 形态的 CodeSource（{@code JarURLConnection.getURL()}），
 * 而模组惯用的「取本类 CodeSource 当 classpath 根自建 URLClassLoader」模式
 * （如 Ship Mastery 的 ReflectionEnabledClassLoader）只接受 jar 文件 URL，
 * 直接喂 entry URL 会导致 findClass 全线 ClassNotFoundException。
 *
 * <p>{@link BytecodeRemapper} 在 remap 通道里把所有
 * {@code CodeSource.getLocation()} 调用点包一层 {@link #toJarFileUrl(URL)}，
 * 使该模式取到与原版一致的 jar 根 URL。仅改写 {@code jar:} 协议且含 {@code !/}
 * 的 URL，其余形态（file: 目录/jar、null）原样放行。
 */
public final class CodeSourceSupport {

    private CodeSourceSupport() {
    }

    /**
     * 把 {@code jar:file:...jar!/entry} 形态的 code source URL 还原为 jar 文件 URL。
     *
     * <p>还原结果若是已挂载的模组 jar，进一步重定向到 {@link ModJarRemapCache}
     * 的 obf→named remap 副本，保证模组自建子类加载器读到 named 字节码。
     *
     * @param location {@code CodeSource.getLocation()} 的原始返回值，可为 {@code null}
     * @return jar 文件 URL；非 jar 协议或无 {@code !/} 分隔时原样返回
     */
    public static URL toJarFileUrl(URL location) {
        if (location == null || !"jar".equals(location.getProtocol())) {
            return location;
        }
        String file = location.getFile();
        int separator = file.indexOf("!/");
        // 仅还原带 entry 路径的 jar!/entry 形态；jar!/ 根形态与原样 URL 均可用，直接放行
        if (separator < 0 || separator + 2 >= file.length()) {
            return location;
        }
        try {
            URL jarRootUrl = new URL(file.substring(0, separator));
            // 已挂载的模组 jar 重定向到 obf→named remap 副本：模组自建子类加载器
            // 直读 jar 字节码、绕过 LCL transformer 链，必须喂 named 字节码
            return ModJarRemapCache.remappedUrlOrOriginal(jarRootUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("无法从 code source URL 提取 jar 文件 URL: " + location, e);
        }
    }
}
