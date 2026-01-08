package io.github.starfabricated.nanoforge.core;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NanoForgeTweaker implements ITweaker {
    private final AtomicBoolean run =new AtomicBoolean(false);

    public NanoForgeTweaker(){
        CoreModManager.injectCoreModTweaks(this);
    }

    @Override
    public void acceptOptions(List<String> list, File file, File file1, String s) {
        if (!run.get()) {
            CoreModManager.sortTweakList();
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
        if (!run.getAndSet(true)) {
            //do any Mixin cant do ^^
        }
    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
