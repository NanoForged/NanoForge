package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CodeSourceSupport#toJarFileUrl(URL)} 的真实逻辑验证：
 * jar!/entry 形态还原为 jar 根后可作为 URLClassLoader classpath 根加载类，
 * 其余形态原样放行。
 */
class CodeSourceSupportTest {

    @Test
    void jarEntryUrlIsReducedToJarRootAndUsableAsClasspathRoot() throws Exception {
        URL jarRoot = new URL("file:/tmp/demo.jar");
        URL entryUrl = new URL("jar:" + jarRoot + "!/demo/Mod.class");

        URL fixed = CodeSourceSupport.toJarFileUrl(entryUrl);

        assertEquals(jarRoot, fixed);
    }

    @Test
    void jarEntryUrlFixMatchesRfbCodeSourceShape() throws Exception {
        // 以真实 jar（本测试类所在 surefire 依赖之一）验证修复后可当 classpath 根：
        // 这里用 JDK 模块外的一个已知 jar——直接用测试类自身的 code source 更真实
        URL ownJar = CodeSourceSupportTest.class.getProtectionDomain().getCodeSource().getLocation();
        URL entryShape = new URL("jar:" + ownJar + "!/io/github/nanoforged/core/remap/CodeSourceSupportTest.class");

        URL fixed = CodeSourceSupport.toJarFileUrl(entryShape);

        // 修复后的 URL 作为 URLClassLoader 根必须能加载本测试类
        try (URLClassLoader loader = new URLClassLoader(new URL[]{fixed}, null)) {
            assertDoesNotThrow(() -> loader.loadClass(CodeSourceSupportTest.class.getName()));
        }
        // 未修复的 entry URL 无法作为 classpath 根（RFB 原始形态的失败实证）
        try (URLClassLoader broken = new URLClassLoader(new URL[]{entryShape}, null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> broken.loadClass(CodeSourceSupportTest.class.getName()));
        }
    }

    @Test
    void nonJarAndPlainUrlsPassThrough() throws Exception {
        URL fileUrl = new URL("file:/tmp/classes/");
        assertEquals(fileUrl, CodeSourceSupport.toJarFileUrl(fileUrl));
        URL jarRootForm = new URL("jar:file:/tmp/demo.jar!/");
        assertEquals(jarRootForm, CodeSourceSupport.toJarFileUrl(jarRootForm));
        assertNull(CodeSourceSupport.toJarFileUrl(null));
    }
}
