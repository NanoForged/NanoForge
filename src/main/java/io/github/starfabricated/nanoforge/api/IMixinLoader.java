package io.github.starfabricated.nanoforge.api;

import java.util.List;

public interface IMixinLoader extends INanoCorePlugin {
    //Very like Mixinbooter, but now only EarlyMixin, because i‘m not found Vanilla Mod Class loading Point
    List<String> getMixinConfigs();

    //in most time, we always using Mixin, not ASM
    @Override
    public default String[] getASMTransformerClass(){
        return new String[0];
    }
}

