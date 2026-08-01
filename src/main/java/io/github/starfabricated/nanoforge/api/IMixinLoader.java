package io.github.starfabricated.nanoforge.api;

import java.util.List;
import java.util.Map;


/**
 * A rename version of IEarlyMixinLoader
 */
public interface IMixinLoader extends INanoCorePlugin {

    //Very like Mixinbooter, but now only EarlyMixin, because i‘m not found Vanilla Mod Class loading Point
    List<String> getMixinConfigs();

    //in most time, we're always using Mixin, not ASM
    @Override
    default List<String> getASMTransformerClass(){
        return List.of();
    }

    @Override
    default void injectData(Map<String, Object> data){
        // DO Nothing
    };

}

