package io.github.starfabricated.nanoforge.api;

import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.List;
import java.util.Map;


/**
 * IEarlyMixinLoader, this time extend "IFMLLoadingPlugin"
 * if we can use Mixin, ASMTransformer is not that useful Right?
 * just saving time
 */
public interface IMixinLoader extends INanoCorePlugin {
    //Very like Mixinbooter, but now only EarlyMixin, because i‘m not found Vanilla Mod Class loading Point
    List<String> getMixinConfigs();

    //in most time, we always using Mixin, not ASM
    @Override
    default String[] getASMTransformerClass(){
        return new String[0];
    }
    @Override
    default void injectData(Map<String, Object> data){
        // DO Nothing
    };

}

