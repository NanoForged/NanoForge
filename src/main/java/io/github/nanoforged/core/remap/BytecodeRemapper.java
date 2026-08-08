package io.github.nanoforged.core.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Objects;

/**
 * 基于映射仓库的字节码重映射器：在 class 级别统一改写类名、字段名、方法名和描述符。
 *
 * <p>覆盖范围：常量池中的直接引用（字段/方法/类常量）、MethodHandle/MethodType
 * 常量与 invokedynamic（ASM ClassRemapper 默认经 map 系列方法处理）；
 * 以及字符串常量中精确匹配 obf 类名的内容（{@code Class.forName} 等字符串
 * 反射路径，slash/dot 两种形态均识别并保持原形态输出）。
 * 不覆盖：成员名的独立字符串（{@code getMethod("名")}），无法静态判定语境。
 *
 * <p>移植自 SSOptimizer mapping 模块（github.kasuminova.ssoptimizer.mapping），
 * 供运行时 {@code NanoRemapTransformer} 使用。
 */
public final class BytecodeRemapper {
    private final MappingRepository repository;
    private final MappingDirection direction;

    /**
     * 创建字节码重映射器。
     *
     * @param repository 映射仓库
     * @param direction  重映射方向
     */
    public BytecodeRemapper(MappingRepository repository, MappingDirection direction) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.direction = Objects.requireNonNull(direction, "direction");
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
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new StringAwareClassRemapper(writer, remapper), 0);

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
            MappingEntry fieldEntry = switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findFieldByObfuscatedName(owner, name).orElse(null);
                case NAMED_TO_OBFUSCATED -> repository.findFieldByNamedName(owner, name).orElse(null);
            };
            if (fieldEntry == null) {
                return name;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> fieldEntry.namedName();
                case NAMED_TO_OBFUSCATED -> fieldEntry.obfuscatedName();
            };
            if (!name.equals(mappedName)) {
                modified = true;
            }
            return mappedName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            MappingEntry methodEntry = switch (direction) {
                case OBFUSCATED_TO_NAMED -> repository.findMethodByObfuscatedName(owner, name, descriptor).orElse(null);
                case NAMED_TO_OBFUSCATED -> repository.findMethodByNamedName(owner, name, descriptor).orElse(null);
            };
            if (methodEntry == null) {
                return name;
            }

            String mappedName = switch (direction) {
                case OBFUSCATED_TO_NAMED -> methodEntry.namedName();
                case NAMED_TO_OBFUSCATED -> methodEntry.obfuscatedName();
            };
            if (!name.equals(mappedName)) {
                modified = true;
            }
            return mappedName;
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
    }
}
