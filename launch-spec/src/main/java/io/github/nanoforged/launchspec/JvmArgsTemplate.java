package io.github.nanoforged.launchspec;

import java.util.List;

/**
 * JVM 参数基线模板：把启动脚本 launch_nanoforge_ss.sh 中的完整 JVM 参数收敛为
 * 可配置模板，产出有序参数列表（顺序与脚本一致）。
 *
 * <p>脚本中每个 JVM 参数都有着落：堆大小/栈大小/路径类参数由 {@link JvmArgsOptions}
 * 覆盖，其余（-noverify、GC 标志、add-opens/exports、RFB 系统类加载器、游戏属性等）
 * 保持脚本基线。脚本中非 JVM 参数的部分——{@code mesa_glthread=false} 环境变量、
 * RFB 主类 {@code com.gtnewhorizons.retrofuturabootstrap.Main} 与
 * {@code --tweakClass io.github.nanoforged.NanoForgeBootstrap} 启动参数——
 * 不属于本模板产出范围，由启动器负责（分别在环境变量与进程命令中提供）。
 */
public interface JvmArgsTemplate {

    /**
     * 按给定覆盖项解析完整 JVM 参数列表。
     *
     * @param options 覆盖项（传 {@code JvmArgsOptions.builder().build()} 即纯脚本基线）
     * @return 有序 JVM 参数列表（与脚本顺序一致），可直接拼入 java 命令
     */
    List<String> resolve(JvmArgsOptions options);
}
