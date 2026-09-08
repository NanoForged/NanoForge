package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 层级索引的 JDK 平台类兜底验证：合流公共父类走查会沿 JDK 继承链上行
 * （URLClassLoader → SecureClassLoader → ClassLoader），JDK 类不在游戏/模组 jar 内，
 * 缺失兜底会把合流类型降级成 java/lang/Object，运行期 VerifyError
 * （实机：ScriptStore$ScriptLoadingTask.run 的加载器合流流入 ClassLoader 实参）。
 */
class JdkHierarchyFallbackTest {

    @TempDir
    Path tempDir;

    private static TinyV2MappingRepository repository() {
        return TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("demo/MyLoader", "demo/MyLoader", "demo/MyLoader")));
    }

    /** 自定义加载器：直接继承 java/lang/ClassLoader。 */
    private static byte[] myLoaderBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/MyLoader", null,
                "java/lang/ClassLoader", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private RemapClassHierarchy hierarchy() throws IOException {
        Path jar = tempDir.resolve("fixtures.jar");
        try (OutputStream out = java.nio.file.Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("demo/MyLoader.class"));
            jos.write(myLoaderBytes());
            jos.closeEntry();
        }
        TinyV2MappingRepository repository = repository();
        return RemapClassHierarchy.ofJars(repository, MappingDirection.OBFUSCATED_TO_NAMED,
                List.of(jar));
    }

    @Test
    void JDK类的父类链必须可达() throws IOException {
        RemapClassHierarchy hierarchy = hierarchy();
        assertEquals(Optional.of("java/security/SecureClassLoader"),
                hierarchy.findSuperName("java/net/URLClassLoader"));
        assertEquals(Optional.of("java/lang/ClassLoader"),
                hierarchy.findSuperName("java/security/SecureClassLoader"));
        assertEquals(Optional.of("java/lang/Object"),
                hierarchy.findSuperName("java/lang/ClassLoader"));
    }

    @Test
    void JDK类与自定义加载器的公共父类必须是ClassLoader而非Object() throws IOException {
        FrameComputingClassWriter writer = new FrameComputingClassWriter(hierarchy());
        assertEquals("java/lang/ClassLoader",
                writer.getCommonSuperClass("java/net/URLClassLoader", "demo/MyLoader"));
    }
}
