package io.github.starfabricated.nanoforge.impl.core;


import com.google.common.collect.Lists;
import io.github.starfabricated.nanoforge.NanoForgeBootstrap;
import io.github.starfabricated.nanoforge.NanoForgeLaunchHelper;
import io.github.starfabricated.nanoforge.api.IMixinLoader;
import io.github.starfabricated.nanoforge.api.INanoCorePlugin;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

public class CoreModManager {
    private static final Set<String> MIXINS = new HashSet<>();
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreMod");

    private static NanoForgeBootstrap tweaker;
    private static File gameDir;
    private static LaunchClassLoader primeClassloader;


    public static void handleLaunch(LaunchClassLoader classLoader, NanoForgeBootstrap bootstrapTweaker){
        //WIP
        tweaker = bootstrapTweaker;
        primeClassloader = classLoader;

        discoverCoreMods();
    }


    /**
     * SPI is better than Manifest, i think.
     * so now you can load your CoreMod form ClassPath.
     * i love Stream API, Java7 is hell dude.
    */
    private static void discoverCoreMods() {
        File modDirs = setupCoreModDir();
        List<File> fileList = List.of(modDirs.listFiles()); //idk
        LOGGER.info("discovering CoreMods...");

        if (!fileList.isEmpty()){
            LOGGER.info("discovering CoreMod in dir...");

            fileList.stream()
                    .filter(file -> file.isFile() && file.getName().endsWith(".jar"))
                    .forEach(CoreModManager::loadJars);

        } else LOGGER.warn("directory is empty, file discover skipped.");

        ServiceLoader<INanoCorePlugin> loader = ServiceLoader.load(INanoCorePlugin.class,primeClassloader);
        LOGGER.info("SPI Load done.");
        for (INanoCorePlugin corePlugin : loader) {
            LOGGER.debug("Find CorePlugin:{}",corePlugin.getClass().getName());
            //get Mixin configs
            if (corePlugin instanceof IMixinLoader mixinLoader) MIXINS.addAll(mixinLoader.getMixinConfigs());

            //TODO:need mateData coreModList mixinOwnerList and fucking everything
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


    private static void loadJars(File file) {
        try {
            LOGGER.info("Find Jar:{} ,try to load.",file.getName());
            primeClassloader.addURL(file.toURI().toURL());
        } catch (MalformedURLException ignored) {}
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

            //reg plugin transformer
            if (asmTransformerClass != null) {
                for (String transformer : asmTransformerClass) {
                    if (transformer != null && !transformer.trim().isEmpty()) {
                        classLoader.addTransformerExclusion(transformer); //idk
                        classLoader.registerTransformer(transformer);
                    }
                }
            }

            //build data
            Map<String, Object> data = new HashMap<>();

            //maybe this is method called InjectData in FML, i removed most thing about IFMLCallHook
            //idk what happen early class loaded will make...
            //so pls don't touch any game class in this func, using reflection if you needed.
            coreModInstance.setupPlugin(classLoader,data);



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
