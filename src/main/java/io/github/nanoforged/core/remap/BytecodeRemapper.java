package io.github.nanoforged.core.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 基于映射仓库的字节码重映射器：在 class 级别统一改写类名、字段名、方法名和描述符。
 *
 * <p>覆盖范围：常量池中的直接引用（字段/方法/类常量）、MethodHandle/MethodType
 * 常量与 invokedynamic（ASM ClassRemapper 默认经 map 系列方法处理）；
 * 以及字符串常量中精确匹配 obf 类名的内容（{@code Class.forName} 等字符串
 * 反射路径，slash/dot 两种形态均识别并保持原形态输出）。
 * 不覆盖：成员名的独立字符串（{@code getMethod("名")}），无法静态判定语境。
 *
 * <p>输出侧兜底：映射表 named 侧偶尔残留非法 JVM 标识符（如 yGuard 字典名
 * {@code String.new} 的自映射）。这类类只有在字节码校验关闭（-noverify）时才能 define，
 * 而 HotSpot 对经过 JVMTI ClassFileLoadHook 改写的字节会强制格式检查（IDEA 调试
 *  attach 后即触发），因此 remap 输出时把非法成员名确定性改写为合成名
 * （{@code 清洗名$nf<hash>}），声明与引用走同一函数保证一致。
 *
 * <p>移植自 SSOptimizer mapping 模块（github.kasuminova.ssoptimizer.mapping），
 * 供运行时 {@code NanoRemapTransformer} 使用。
 */
public final class BytecodeRemapper {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Remap");
    /** 已报告过的非法名清洗项（按 owner#name 去重，避免逐类刷日志）。 */
    private static final Set<String> SANITIZE_REPORTED = ConcurrentHashMap.newKeySet();

    private final MappingRepository repository;
    private final MappingDirection direction;
    private final RemapClassHierarchy hierarchy;

    /**
     * 创建字节码重映射器（无层级来源，帧合流一律落到 {@code java/lang/Object}，
     * 供无层级可用的测试/合成场景）。
     *
     * @param repository 映射仓库
     * @param direction  重映射方向
     */
    public BytecodeRemapper(MappingRepository repository, MappingDirection direction) {
        this(repository, direction, RemapClassHierarchy.empty());
    }

    /**
     * 创建字节码重映射器。
     *
     * @param repository 映射仓库
     * @param direction  重映射方向
     * @param hierarchy  帧重算的类层级来源（字节读取，不触发类定义）
     */
    public BytecodeRemapper(MappingRepository repository, MappingDirection direction,
                            RemapClassHierarchy hierarchy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
    }

    /**
     * 重映射单个类文件字节码。
     *
     * @param classfileBuffer 原始类文件字节码
     * @return 重映射结果
     */
    public RemappedClass remapClass(byte[] classfileBuffer) {
        Objects.requireNonNull(classfileBuffer, "classfileBuffer");

        ClassReader reader = new ClassReader(classfileBuffer);
        RepositoryBackedRemapper remapper = new RepositoryBackedRemapper();
        // 不复用原常量池（new ClassWriter(reader, 0) 会逐字拷贝原 CP，把混淆器留下的
        // 孤儿 Methodref/Fieldref 项带进产物；全新 ClassWriter 只保留可达常量，
        // 避免 JDK 对变换后类的强制格式检查被孤儿项击杀）。移植自 SSOptimizer。
        // 帧表由 FrameComputingClassWriter 全量重算（COMPUTE_FRAMES）：改写类名后
        // 透传原帧的重编码产出过非法偏移（IDEA debug 强制校验下 ClassFormatError），
        // 故读侧 SKIP_FRAMES，写侧以帧内容自身为准重建。
        ClassWriter writer = new FrameComputingClassWriter(hierarchy);
        reader.accept(new StringAwareClassRemapper(writer, remapper), ClassReader.SKIP_FRAMES);

        String inputInternalName = reader.getClassName();
        String outputInternalName = remapper.map(inputInternalName);
        byte[] outputBytes = remapper.modified() ? writer.toByteArray() : classfileBuffer;
        return new RemappedClass(inputInternalName, outputInternalName, outputBytes, remapper.modified());
    }

    /**
     * 单个类 remap 的结果。
     *
     * @param inputInternalName  输入类名
     * @param outputInternalName 输出类名
     * @param bytecode           输出字节码
     * @param modified           是否发生改写
     */
    public record RemappedClass(String inputInternalName,
                                String outputInternalName,
                                byte[] bytecode,
                                boolean modified) {
    }

    /**
     * 在 ClassRemapper 基础上额外改写字符串常量中的 obf 类名
     * （{@code Class.forName} / 字符串承载类名的反射路径）。
     */
    private final class StringAwareClassRemapper extends ClassRemapper {
        private final RepositoryBackedRemapper stringRemapper;

        StringAwareClassRemapper(ClassWriter writer, RepositoryBackedRemapper remapper) {
            super(writer, remapper);
            this.stringRemapper = remapper;
        }

        @Override
        protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
            return new MethodRemapper(methodVisitor, stringRemapper) {
                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof String stringValue) {
                        value = stringRemapper.mapClassNameString(stringValue);
                    }
                    super.visitLdcInsn(value);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String descriptor, boolean isInterface) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    // RFB 对被改写的类赋予 jar!/entry 形态 CodeSource；把 getLocation()
                    // 调用点包一层 toJarFileUrl，修复「CodeSource 当 classpath 根」模式。
                    if (opcode == Opcodes.INVOKEVIRTUAL
                            && "java/security/CodeSource".equals(owner)
                            && "getLocation".equals(name)
                            && "()Ljava/net/URL;".equals(descriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "io/github/nanoforged/core/remap/CodeSourceSupport",
                                "toJarFileUrl", "(Ljava/net/URL;)Ljava/net/URL;", false);
                        stringRemapper.markModified();
                    }
                }
            };
        }
    }

    private final class RepositoryBackedRemapper extends Remapper {
        private boolean modified;

        @Override
        public String mapDesc(String descriptor) {
            String mappedDescriptor = super.mapDesc(descriptor);
            if (!descriptor.equals(mappedDescriptor)) {
                modified = true;
            }
            return mappedDescriptor;
        }

        @Override
        public String mapMethodDesc(String descriptor) {
            String mappedDescriptor = super.mapMethodDesc(descriptor);
            if (!descriptor.equals(mappedDescriptor)) {
                modified = true;
            }
            return mappedDescriptor;
        }

        @Override
        public String map(String internalName) {
            MappingEntry classEntry = switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findClassByObfuscatedName(internalName).orElse(null);
                case NAMED_TO_OBFUSCATED -> repository.findClassByNamedName(internalName).orElse(null);
            };
            if (classEntry == null) {
                return internalName;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> classEntry.namedName();
                case NAMED_TO_OBFUSCATED -> classEntry.obfuscatedName();
            };
            if (!internalName.equals(mappedName)) {
                modified = true;
            }
            return mappedName;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            MappingEntry fieldEntry = findFieldEntry(owner, name).orElse(null);
            if (fieldEntry == null) {
                // named 替换 jar 的场景：类已是 named 形态，映射查找整体落空，
                // 非法成员名（String.new 等 yGuard 字典名）也要在透传侧清洗。
                if (direction == MappingDirection.OBFUSCATED_TO_NAMED) {
                    String sanitized = sanitizeIllegalMemberName(name, name, false);
                    if (!name.equals(sanitized)) {
                        modified = true;
                    }
                    return sanitized;
                }
                return name;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> fieldEntry.namedName();
                case NAMED_TO_OBFUSCATED -> fieldEntry.obfuscatedName();
            };
            if (direction == MappingDirection.OBFUSCATED_TO_NAMED) {
                mappedName = sanitizeIllegalMemberName(mappedName, mappedName, false);
            }
            if (!name.equals(mappedName)) {
                modified = true;
            }
            return mappedName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            MappingEntry methodEntry = findMethodEntry(owner, name, descriptor).orElse(null);
            if (methodEntry == null) {
                if (direction == MappingDirection.OBFUSCATED_TO_NAMED) {
                    String sanitized = sanitizeIllegalMemberName(name, name + descriptor, true);
                    if (!name.equals(sanitized)) {
                        modified = true;
                    }
                    return sanitized;
                }
                return name;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> methodEntry.namedName();
                case NAMED_TO_OBFUSCATED -> methodEntry.obfuscatedName();
            };
            if (direction == MappingDirection.OBFUSCATED_TO_NAMED) {
                mappedName = sanitizeIllegalMemberName(mappedName, mappedName + descriptor, true);
            }
            if (!name.equals(mappedName)) {
                modified = true;
            }
            return mappedName;
        }

        /**
         * 层级感知的字段映射查找：成员映射挂在声明类条目下，而引用点的 owner 可能是其子类
         * （如实机暴露的 TitleMusicPlayer 引用 BaseMusicPlayer 声明的 trackSpec）。
         */
        private Optional<MappingEntry> findFieldEntry(String owner, String name) {
            return findMemberHierarchically(owner, current -> switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findFieldByObfuscatedName(current, name);
                case NAMED_TO_OBFUSCATED -> repository.findFieldByNamedName(current, name);
            });
        }

        /** 层级感知的方法映射查找，语义同 {@link #findFieldEntry}。 */
        private Optional<MappingEntry> findMethodEntry(String owner, String name, String descriptor) {
            return findMemberHierarchically(owner, current -> switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findMethodByObfuscatedName(current, name, descriptor);
                case NAMED_TO_OBFUSCATED -> repository.findMethodByNamedName(current, name, descriptor);
            });
        }

        /**
         * 成员映射的层级查找：先 owner 直查，再沿父类链直上，最后对全部可达接口做广搜
         * （与 JVM 成员解析优先级一致）。所有 owner 名均为输入命名空间；
         * 层级字节不可达时退化为原有的 owner 直查。
         */
        private Optional<MappingEntry> findMemberHierarchically(String owner,
                                                                Function<String, Optional<MappingEntry>> directLookup) {
            Optional<MappingEntry> hit = directLookup.apply(owner);
            if (hit.isPresent()) {
                return hit;
            }

            Set<String> visited = new HashSet<>();
            visited.add(owner);
            Deque<String> interfaceQueue = new ArrayDeque<>();
            String current = owner;
            while (true) {
                for (String itf : interfacesInputNames(current)) {
                    if (visited.add(itf)) {
                        interfaceQueue.add(itf);
                    }
                }
                String superName = superInputName(current);
                if (superName == null || !visited.add(superName)) {
                    break;
                }
                hit = directLookup.apply(superName);
                if (hit.isPresent()) {
                    return hit;
                }
                current = superName;
            }
            while (!interfaceQueue.isEmpty()) {
                String itf = interfaceQueue.poll();
                hit = directLookup.apply(itf);
                if (hit.isPresent()) {
                    return hit;
                }
                for (String parent : interfacesInputNames(itf)) {
                    if (visited.add(parent)) {
                        interfaceQueue.add(parent);
                    }
                }
            }
            return Optional.empty();
        }

        /** 输入命名空间类名 → 输入命名空间直接父类名；无父类或层级不可达时 null。无副作用（不置 modified）。 */
        private String superInputName(String inputName) {
            return hierarchy.findSuperName(toOutputName(inputName))
                    .map(this::toInputName)
                    .orElse(null);
        }

        /** 输入命名空间类名 → 输入命名空间直接接口名列表。无副作用（不置 modified）。 */
        private List<String> interfacesInputNames(String inputName) {
            return hierarchy.findInterfaces(toOutputName(inputName)).stream()
                    .map(this::toInputName)
                    .toList();
        }

        /** 输入命名空间 → 输出命名空间类名换算（无副作用版 {@link #map(String)}）。 */
        private String toOutputName(String inputName) {
            return switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findClassByObfuscatedName(inputName)
                        .map(MappingEntry::namedName).orElse(inputName);
                case NAMED_TO_OBFUSCATED -> repository.findClassByNamedName(inputName)
                        .map(MappingEntry::obfuscatedName).orElse(inputName);
            };
        }

        /** 输出命名空间 → 输入命名空间类名换算；未命中映射时原样返回。 */
        private String toInputName(String outputName) {
            return switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findClassByNamedName(outputName)
                        .map(MappingEntry::obfuscatedName).orElse(outputName);
                case NAMED_TO_OBFUSCATED -> repository.findClassByObfuscatedName(outputName)
                        .map(MappingEntry::namedName).orElse(outputName);
            };
        }

        /**
         * 改写字符串常量中的类名：slash（{@code com/fs/...}）与 dot（{@code com.fs...}）
         * 两种形态均识别，精确匹配映射表中的类条目才改写，并保持原形态输出；
         * 未命中原样返回。成员名不在此处理（无法静态判定语境，误伤风险高）。
         */
        String mapClassNameString(String value) {
            boolean dotForm = value.indexOf('/') < 0;
            if (dotForm && value.indexOf('.') < 0) {
                return value;
            }
            String internalForm = dotForm ? value.replace('.', '/') : value;
            MappingEntry classEntry = switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findClassByObfuscatedName(internalForm).orElse(null);
                case NAMED_TO_OBFUSCATED -> repository.findClassByNamedName(internalForm).orElse(null);
            };
            if (classEntry == null) {
                return value;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> classEntry.namedName();
                case NAMED_TO_OBFUSCATED -> classEntry.obfuscatedName();
            };
            if (internalForm.equals(mappedName)) {
                return value;
            }
            modified = true;
            return dotForm ? mappedName.replace('/', '.') : mappedName;
        }

        boolean modified() {
            return modified;
        }

        /** 标记该类已发生改写（供非映射类改写路径使用，如 CodeSource 修复包裹）。 */
        void markModified() {
            modified = true;
        }
    }

    /**
     * 映射表 named 侧的非法 JVM 成员名清洗：字段名不得含 {@code . ; [ /}，方法名额外不得含
     * {@code < >}（{@code <init>}/{@code <clinit>} 除外）。合法名原样返回；非法名确定性
     * 改写为 {@code 清洗名$nf<hash>}（hash 取自完整 lookup key，保证同 key 同结果，
     * 声明与引用一致）。
     */
    static String sanitizeIllegalMemberName(String mappedName, String lookupKey, boolean method) {
        if (mappedName == null || mappedName.isEmpty()) {
            return syntheticMemberName("x", lookupKey);
        }
        if (method && (mappedName.equals("<init>") || mappedName.equals("<clinit>"))) {
            return mappedName;
        }
        boolean illegal = false;
        for (int i = 0; i < mappedName.length(); i++) {
            char ch = mappedName.charAt(i);
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/'
                    || (method && (ch == '<' || ch == '>'))) {
                illegal = true;
                break;
            }
        }
        if (!illegal) {
            return mappedName;
        }
        StringBuilder base = new StringBuilder(mappedName.length());
        for (int i = 0; i < mappedName.length(); i++) {
            char ch = mappedName.charAt(i);
            boolean bad = ch == '.' || ch == ';' || ch == '[' || ch == '/'
                    || (method && (ch == '<' || ch == '>'));
            base.append(bad ? '_' : ch);
        }
        String sanitized = syntheticMemberName(base.toString(), lookupKey);
        if (SANITIZE_REPORTED.add(lookupKey)) {
            LOGGER.warn("映射表 named 侧为非法 JVM 标识符，remap 输出已改写为合成名: {} -> {}", lookupKey, sanitized);
        }
        return sanitized;
    }

    private static String syntheticMemberName(String base, String lookupKey) {
        return base + "$nf" + Integer.toHexString(lookupKey.hashCode());
    }
}
