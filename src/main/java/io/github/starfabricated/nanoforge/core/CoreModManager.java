package io.github.starfabricated.nanoforge.core;


import com.google.common.collect.Lists;
import io.github.starfabricated.nanoforge.NanoForgeBootstrap;
import io.github.starfabricated.nanoforge.NanoForgeLaunchHelper;
import io.github.starfabricated.nanoforge.api.INanoCorePlugin;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.lwjgl.Sys;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class CoreModManager {
    private static NanoForgeBootstrap tweaker;
    private static File gameDir;

    public static void handleLaunch(LaunchClassLoader classLoader, NanoForgeBootstrap bootstrapTweaker){
        //WIP
        tweaker = bootstrapTweaker;

        discoverCoreMods(classLoader);
    }


    /**
    * SPI is better than Manifest, i think
    */
    private static void discoverCoreMods(LaunchClassLoader classLoader) {
        File modDirs = setupCoreModDir();
        Arrays.stream(modDirs.listFiles())
                .filter(file -> file.isFile() && file.getName().endsWith(".jar"))
                .forEach(s -> {
                    try {
                        classLoader.addURL(s.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                });


        ServiceLoader<INanoCorePlugin> loader = ServiceLoader.load(INanoCorePlugin.class,classLoader);
        for (INanoCorePlugin corePlugin : loader) {
            //TO BE DONE
        }
        //WIP`
    }

    /**
     * Skid form FML , but add more check
     */
    private static File setupCoreModDir() {
        File coreModDir = new File(NanoForgeLaunchHelper.getModsDir(), "coremods");

        try {
            coreModDir = coreModDir.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to resolve coremod path: %s", coreModDir), e);
        }


        if (!coreModDir.exists()) {
            if (!coreModDir.mkdirs()) {
                throw new RuntimeException(String.format("Failed to create coremod directory: %s", coreModDir));
            }

        } else if (!coreModDir.isDirectory()) {
            throw new RuntimeException(String.format("Path exists but is not a directory: %s", coreModDir));
        }
        if (!coreModDir.canWrite() || !coreModDir.canRead()) {
            throw new RuntimeException(String.format("Coremod directory is not writable or readable: %s", coreModDir));
        }
        return coreModDir;
    }


    /**
     *  Also skid form FML , WIP
     */
    private static class NanoPluginWrapper implements ITweaker {
        public final String name;
        public final INanoCorePlugin coreModInstance;
        public final List<String> predepends;
        public final int sortIndex;

        public NanoPluginWrapper(String name, INanoCorePlugin coreModInstance,  int sortIndex, String... predepends) {
            this.name = name;
            this.coreModInstance = coreModInstance;
            this.sortIndex = sortIndex;
            this.predepends = Lists.newArrayList(predepends);
        }

        @Override
        public String toString()
        {
            return String.format("%s {%s}", this.name, this.predepends);
        }

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile){
            //skip
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
            if (coreModInstance == null) {
                throw new IllegalStateException("Core mod instance is null");
            }

            String[] asmTransformerClass = coreModInstance.getASMTransformerClass();

            if (asmTransformerClass != null) {
                for (String transformer : asmTransformerClass) {
                    if (transformer != null && !transformer.trim().isEmpty()) {
                        classLoader.addTransformerExclusion(transformer); //idk
                        classLoader.registerTransformer(transformer);
                    }
                }
            }

            try {
                coreModInstance.setupPlugin(classLoader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getLaunchTarget()
        {
            return "";
        }

        @Override
        public String[] getLaunchArguments()
        {
            return new String[0];
        }

    }


}
