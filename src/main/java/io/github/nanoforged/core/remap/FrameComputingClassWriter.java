package io.github.nanoforged.core.remap;

import org.objectweb.asm.ClassWriter;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 重算 StackMapTable 的 {@link ClassWriter}（{@code COMPUTE_FRAMES}）。
 *
 * <p>动机：旧实现 {@code new ClassWriter(0)} 透传原帧表，remap 改写类名后帧条目
 * 重编码可能产生非法偏移（实机：IDEA debug 强制校验时
 * {@code ClassFormatError: StackMapTable format error: bad offset for Uninitialized}）。
 * 重算帧以帧内容自身为准，与 remap 的名字改写彻底解耦。
 *
 * <p>共同父类解析不定义类（默认实现会 {@code Class.forName}），改为沿
 * {@link RemapClassHierarchy} 的父类链走查；两端链路的第一个交集即最近公共父类。
 * 层级覆盖必须包含 JDK 平台类（实现侧已兜底）：合流类型若被降级成
 * {@code java/lang/Object}，流入带精确类型形参的调用点会炸 VerifyError
 * （实机：URLClassLoader 与自定义加载器合流后作为 ClassLoader 实参）。
 * 仅在层级彻底不可达时才落到 {@code java/lang/Object}。
 */
final class FrameComputingClassWriter extends ClassWriter {

    private static final String OBJECT_INTERNAL_NAME = "java/lang/Object";

    private final RemapClassHierarchy hierarchy;

    FrameComputingClassWriter(RemapClassHierarchy hierarchy) {
        super(ClassWriter.COMPUTE_FRAMES);
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        // 数组/原始类型的公共父类解析按 Object 处理（ASM 只就引用类型触发本方法，
        // 数组按元素类型的精确公共类型并非验证器必需）。
        if (type1.charAt(0) == '[' || type2.charAt(0) == '[') {
            return OBJECT_INTERNAL_NAME;
        }
        Set<String> chain1 = superChainOf(type1);
        String current = type2;
        while (current != null) {
            if (chain1.contains(current)) {
                return current;
            }
            current = hierarchy.findSuperName(current).orElse(null);
        }
        return OBJECT_INTERNAL_NAME;
    }

    /** 沿父类链收集 type 的全部祖先（含自身）；链断裂或成环即止。 */
    private Set<String> superChainOf(String type) {
        Set<String> chain = new LinkedHashSet<>();
        String current = type;
        while (current != null && chain.add(current)) {
            current = hierarchy.findSuperName(current).orElse(null);
        }
        return chain;
    }
}
