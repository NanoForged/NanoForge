// 注意：该包刻意不在 io.github.nanoforged 下。RFB 的 Launch 会将 tweaker 所在包
// （io.github.nanoforged）整体注册为 LaunchClassLoader 排除项，mixin 类必须经
// LaunchClassLoader 加载，置于该前缀下会触发 PACKAGE_CLASSLOADER_EXCLUSION 被拒。
package nanoforge.mixin.core.bootstrap;


import io.github.nanoforged.common.NanoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.fs.starfarer.StarfarerLauncher")
public class StarfarerLauncherMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void InjectInit(String[] par1, CallbackInfo ci){
        NanoForge.init();
    }
}
