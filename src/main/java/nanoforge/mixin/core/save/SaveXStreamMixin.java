// 注意：该包刻意不在 io.github.nanoforged 下。RFB 的 Launch 会将 tweaker 所在包
// （io.github.nanoforged）整体注册为 LaunchClassLoader 排除项，mixin 类必须经
// LaunchClassLoader 加载，置于该前缀下会触发 PACKAGE_CLASSLOADER_EXCLUSION 被拒。
package nanoforge.mixin.core.save;

import com.thoughtworks.xstream.mapper.MapperWrapper;
import io.github.nanoforged.core.save.SaveCompatMapping;
import nanoforge.save.SaveCompatMapperWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在游戏的存档 XStream 装配点挂上 {@link SaveCompatMapperWrapper}，
 * 使 named 运行时能读取 linux obf 游戏写出的存档（并保持写盘格式互通）。
 *
 * <p>注入点是 {@code SaveXStream.wrapMapper} 的 RETURN：游戏在此处装上自己的
 * {@code SaveMapperWrapper}，本 mixin 在其外再包一层兼容翻译，
 * 对游戏原有别名链零侵入。
 */
@Mixin(targets = "com.fs.starfarer.campaign.save.SaveXStream")
public abstract class SaveXStreamMixin {

    @Inject(method = "wrapMapper", at = @At("RETURN"), cancellable = true)
    private void nanoforge$wrapSaveCompatMapper(MapperWrapper next, CallbackInfoReturnable<MapperWrapper> cir) {
        if (!SaveCompatMapping.isEnabled()) {
            return;
        }
        cir.setReturnValue(new SaveCompatMapperWrapper(cir.getReturnValue(), SaveCompatMapping.active()));
    }
}
