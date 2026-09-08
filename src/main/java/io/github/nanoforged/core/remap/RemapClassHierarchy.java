package io.github.nanoforged.core.remap;

import java.util.List;
import java.util.Optional;

/**
 * remap 期类层级查询：为 ASM {@code COMPUTE_FRAMES} 提供共同父类解析、以及成员映射的
 * 声明类定位（成员映射挂在声明类条目下，引用点的 owner 可能是其子类）所需的最小信息
 * （直接父类与直接接口的内部名）。
 *
 * <p>实现按字节读取类文件（jar 条目 / 类加载器资源），并兜底 JDK 平台类
 * （jrt 资源，合流走查会沿 {@code ClassLoader} 等 JDK 继承链上行），绝不触发类定义——
 * 被 remap 的类提前 define 会进入错误的类加载域（启动期不可见、运行期才爆）。
 *
 * <p>查询与返回均使用 remap 输出命名空间的内部名；实现内部负责跨命名空间换算：
 * 定位字节时先按输出名直查、再换算回输入名直查（named 游戏 jar 与 obf 模组 jar
 * 并存的运行时装配下两种形态都要命中），解析出的父类/接口名再统一换算回输出命名空间。
 */
public interface RemapClassHierarchy {

    /**
     * 查询类的直接父类内部名（输出命名空间）。
     *
     * @param internalName 输出命名空间的类内部名（/ 分隔）
     * @return 直接父类内部名；接口、{@code java/lang/Object}、字节不可达的类均返回 empty
     */
    Optional<String> findSuperName(String internalName);

    /**
     * 查询类直接实现/继承的接口内部名（输出命名空间）。
     *
     * @param internalName 输出命名空间的类内部名（/ 分隔）
     * @return 直接接口内部名列表；字节不可达的类返回空表
     */
    List<String> findInterfaces(String internalName);

    /**
     * 空层级：所有查询返回空结果，共同父类解析一律落到 {@code java/lang/Object}。
     * 帧语义仍然合法（精度降级），供无层级来源的测试与合成场景使用。
     */
    static RemapClassHierarchy empty() {
        return RemapClassHierarchyImpl.EMPTY;
    }

    /**
     * 以 jar 集合为字节来源构建层级索引（编译期 jar remap 场景）。
     *
     * @param repository 映射仓库（命名空间换算依据）
     * @param direction  remap 方向（决定输入/输出命名空间）
     * @param jars       字节来源 jar（通常为被 remap 的 jar 全集 + 其依赖 jar）
     * @return 层级索引
     */
    static RemapClassHierarchy ofJars(MappingRepository repository,
                                      MappingDirection direction,
                                      java.util.Collection<java.nio.file.Path> jars) {
        return RemapClassHierarchyImpl.ofJars(repository, direction, jars);
    }

    /**
     * 以类加载器资源为字节来源构建层级索引（运行时 remap 场景）。
     * 资源查找只读字节、不触发类定义；结果带缓存。
     *
     * @param repository 映射仓库（命名空间换算依据）
     * @param direction  remap 方向
     * @param loader     资源可见性以该加载器为准（运行时为 LaunchClassLoader）
     * @return 层级索引
     */
    static RemapClassHierarchy ofClassLoader(MappingRepository repository,
                                             MappingDirection direction,
                                             ClassLoader loader) {
        return RemapClassHierarchyImpl.ofClassLoader(repository, direction, loader);
    }
}
