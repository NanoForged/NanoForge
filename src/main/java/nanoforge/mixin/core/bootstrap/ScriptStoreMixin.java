// 注意：该包刻意不在 io.github.nanoforged 下。RFB 的 Launch 会将 tweaker 所在包
// （io.github.nanoforged）整体注册为 LaunchClassLoader 排除项，mixin 类必须经
// LaunchClassLoader 加载，置于该前缀下会触发 PACKAGE_CLASSLOADER_EXCLUSION 被拒。
package nanoforge.mixin.core.bootstrap;

import io.github.nanoforged.core.remap.ModJarMounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在模组脚本类加载器装配点把模组 jar 挂载进 LaunchClassLoader，
 * 使模组类经父委托走 LCL 加载，进入 obf→named remap 链（详见
 * {@link ModJarMounter}）。
 *
 * <p>常规启动下模组 jar 已由 {@code CoreModManager.apply} 在 tweaker 期经
 * {@code ModJarScanner} 提前挂载（Mixin prepare 时序要求），此处为兜底入口，
 * 已挂载的路径由 {@link ModJarMounter} 去重跳过。
 *
 * <p>注入点选择 {@code createSourceClassLoader} 而非 {@code loadScripts}：
 * 本版本游戏的实际脚本加载路径是 {@code ScriptStore$ScriptLoadingTask.run()}，
 * 它内联复制了 jar 加载逻辑、不调用 {@code loadScripts()}（遗留方法）；
 * {@code createSourceClassLoader} 是两条路径共用的咽喉点，此时
 * {@code jarFiles} 已填充且任何模组类尚未加载。
 */
@Mixin(targets = "com.fs.starfarer.loading.scripts.ScriptStore")
public abstract class ScriptStoreMixin {

    @Shadow
    private static List<String> jarFiles;

    @Inject(method = "createSourceClassLoader(Ljava/lang/ClassLoader;)V", at = @At("HEAD"))
    private static void nanoforge$mountModJars(ClassLoader parent, CallbackInfo ci) {
        ModJarMounter.mountIntoLaunchClassLoader(jarFiles);
    }
}
