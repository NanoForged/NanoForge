package io.github.starfabricated.nanoforge.mixin.gamehooks.bootstrap;


import io.github.starfabricated.nanoforge.NanoForge;
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
