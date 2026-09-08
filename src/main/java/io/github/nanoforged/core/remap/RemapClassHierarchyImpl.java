package io.github.nanoforged.core.remap;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RemapClassHierarchy} 实现：字节定位 + 命名空间换算 + 结果缓存。
 *
 * <p>字节来源由构造时注入的定位函数承载（jar 索引 / 类加载器资源），
 * 本类只负责「输出名直查 → 输入名换算直查 → 父类/接口名换算回输出命名空间」的查询流程。
 */
final class RemapClassHierarchyImpl implements RemapClassHierarchy {

    static final RemapClassHierarchy EMPTY = new RemapClassHierarchy() {
        @Override
        public Optional<String> findSuperName(String internalName) {
            return Optional.empty();
        }

        @Override
        public List<String> findInterfaces(String internalName) {
            return List.of();
        }
    };

    /** 字节定位函数：按 JVM 内部名取 class 字节，未命中返回 empty */
    @FunctionalInterface
    interface ClassBytesLocator {
        Optional<byte[]> locate(String internalName);
    }

    /** 单个类的层级节点（父类/接口名均为输出命名空间） */
    private record Node(Optional<String> superName, List<String> interfaces) {
        static final Node UNREACHABLE = new Node(Optional.empty(), List.of());
    }

    private final MappingRepository repository;
    private final MappingDirection direction;
    private final ClassBytesLocator locator;
    /** 查询缓存（含未命中的 UNREACHABLE 结果，避免重复读 jar/资源） */
    private final Map<String, Node> cache = new ConcurrentHashMap<>();

    private RemapClassHierarchyImpl(MappingRepository repository,
                                    MappingDirection direction,
                                    ClassBytesLocator locator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    static RemapClassHierarchy ofJars(MappingRepository repository,
                                      MappingDirection direction,
                                      java.util.Collection<java.nio.file.Path> jars) {
        Map<String, byte[]> classBytes = new java.util.HashMap<>();
        for (java.nio.file.Path jar : jars) {
            try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.endsWith(".class") || "module-info.class".equals(name)) {
                        continue;
                    }
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        classBytes.putIfAbsent(name.substring(0, name.length() - ".class".length()),
                                in.readAllBytes());
                    }
                }
            } catch (IOException e) {
                throw new MappingLookupException("层级索引读取 jar 失败: " + jar, e);
            }
        }
        return new RemapClassHierarchyImpl(repository, direction,
                internalName -> Optional.ofNullable(classBytes.get(internalName)));
    }

    static RemapClassHierarchy ofClassLoader(MappingRepository repository,
                                             MappingDirection direction,
                                             ClassLoader loader) {
        Map<String, Optional<byte[]>> resourceCache = new ConcurrentHashMap<>();
        ClassBytesLocator locator = internalName -> resourceCache.computeIfAbsent(internalName, name -> {
            String resourcePath = name + ".class";
            try (InputStream in = loader.getResourceAsStream(resourcePath)) {
                return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
            } catch (IOException e) {
                return Optional.empty();
            }
        });
        return new RemapClassHierarchyImpl(repository, direction, locator);
    }

    @Override
    public Optional<String> findSuperName(String internalName) {
        return node(internalName).superName();
    }

    @Override
    public List<String> findInterfaces(String internalName) {
        return node(internalName).interfaces();
    }

    private Node node(String outputName) {
        return cache.computeIfAbsent(outputName, this::resolveNode);
    }

    private Node resolveNode(String outputName) {
        Optional<byte[]> bytes = locator.locate(outputName);
        if (bytes.isEmpty()) {
            bytes = locator.locate(toInputName(outputName));
        }
        if (bytes.isEmpty()) {
            // JDK 平台类兜底：java.* 不在游戏/模组 jar 内，但帧合流的公共父类走查
            // 会沿其继承链上行（如 URLClassLoader → SecureClassLoader → ClassLoader），
            // 缺失会把本应精确的合流类型降级成 java/lang/Object，运行期 VerifyError。
            // JDK 类名不参与映射，两命名空间同形，直接用输出名查。
            bytes = locateJdkClass(outputName);
        }
        if (bytes.isEmpty()) {
            return Node.UNREACHABLE;
        }
        ClassReader reader = new ClassReader(bytes.get());
        Optional<String> superName = Optional.ofNullable(reader.getSuperName()).map(this::toOutputName);
        List<String> interfaces = Arrays.stream(reader.getInterfaces()).map(this::toOutputName).toList();
        return new Node(superName, interfaces);
    }

    /** JDK 平台类字节读取（jrt 资源，不触发类定义）；未命中返回 empty。 */
    private static Optional<byte[]> locateJdkClass(String internalName) {
        try (InputStream in = ClassLoader.getPlatformClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 输出命名空间名换算为输入命名空间名；未命中映射时原样返回（两命名空间名集不相交，恒等安全）。 */
    private String toInputName(String outputName) {
        return switch (direction) {
            case OBFUSCATED_TO_NAMED -> repository.findClassByNamedName(outputName)
                    .map(MappingEntry::obfuscatedName).orElse(outputName);
            case NAMED_TO_OBFUSCATED -> repository.findClassByObfuscatedName(outputName)
                    .map(MappingEntry::namedName).orElse(outputName);
        };
    }

    /** 输入命名空间名换算为输出命名空间名；未命中映射时原样返回。 */
    private String toOutputName(String inputName) {
        return switch (direction) {
            case OBFUSCATED_TO_NAMED -> repository.findClassByObfuscatedName(inputName)
                    .map(MappingEntry::namedName).orElse(inputName);
            case NAMED_TO_OBFUSCATED -> repository.findClassByNamedName(inputName)
                    .map(MappingEntry::obfuscatedName).orElse(inputName);
        };
    }
}
