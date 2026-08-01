package io.github.starfabricated.nanoforge.core;


import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import io.github.starfabricated.nanoforge.NanoForgeBootstrap;
import io.github.starfabricated.nanoforge.api.IMixinLoader;
import io.github.starfabricated.nanoforge.api.INanoCorePlugin;
import io.github.starfabricated.nanoforge.utils.PathUtils;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * Handle and Manage CoreMods
 */
public class CoreModManager {
    private static final HashSet<String> MIXINS = new HashSet<>();

    public static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreMod");


    private static NanoForgeBootstrap primeTweaker;
    private static LaunchClassLoader primeClassloader;
    private static List<NanoPluginWrapper> loadPlugins;
    private static HashBiMap<String,List<String>> pluginMixins;

    /**
     * launchHandler, same name for FML, and same Effect.
     * @param classLoader  LaunchClassloader form primeTweaker(NanoForgeBootstrap)
     * @param bootstrapTweaker primeTweaker(NanoForgeBootstrap)
     */
    public static void handleLaunch(LaunchClassLoader classLoader, NanoForgeBootstrap bootstrapTweaker){
        primeTweaker = bootstrapTweaker;
        primeClassloader = classLoader;

        // TODO: Patcher System
        //primeTweaker.injectCascadingTweak("io.github.starfabricated.nanoforge.core.NanoForgeTweaker");

        discoverCoreMods();

        MIXINS.forEach(Mixins::addConfiguration);
    }


    /**
     * discover Mod form dirs and ClassPath , but diff fo forge , we are not support load normal TweakerClass
     * SPI is better than Manifest, i think.
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



        loadPlugins = new ArrayList<>();
        pluginMixins = HashBiMap.create();
        ServiceLoader.load(INanoCorePlugin.class,primeClassloader).forEach(CoreModManager::processCorePlugin);
        LOGGER.info("SPI Load done.");
    }

    /**
     * Skid form FML , but add more check
     */
    private static File setupCoreModDir() {
        File coreModDir = new File(PathUtils.getModsPath().toFile(), "coremods");

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
            throw new RuntimeException(String.format("Core mod directory is not writable or readable: %s", coreModDir));
        }
        return coreModDir;
    }

    /**
     * load jar form dir (any Jar)
     * @param file  JarFile
     */
    private static void loadJars(File file) {
        try {
            LOGGER.info("Find Jar:{} ,try to load.",file.getName());
            primeClassloader.addURL(file.toURI().toURL());
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to load jar file: {}", file, e);
        }
    }

    /**
     * read CorePlugins info and building TweakerWrapper, just like FML
     * @param corePlugin plugin
     */
    private static void processCorePlugin(INanoCorePlugin corePlugin){
        LOGGER.info("Find CorePlugin:{}",corePlugin.getClass().getName());

        String name = corePlugin.getName();

        //get Mixin configs
        if (corePlugin instanceof IMixinLoader mixinLoader) {
            List<String> mixinConfigs = mixinLoader.getMixinConfigs();
            pluginMixins.put(name,mixinConfigs);
            MIXINS.addAll(mixinConfigs);
        }

        NanoPluginWrapper wrapper = new NanoPluginWrapper(corePlugin);
        loadPlugins.add(wrapper);
    }

    /**
     *  if code works don't touch it , it works very good in FML
     * @param injectTweaker FMLInjectionAndSortingTweaker
     */
    public static void injectCoreModTweaks(NanoForgeTweaker injectTweaker) {
        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        // Add the sorting tweaker first- it'll appear twice in the list
        //↑ this commit is copy form FML, and it is. maybe for handle Cascading Tweak
        tweakers.addFirst(injectTweaker);
        LOGGER.info("NanoForgeTweaker Injected.");
        tweakers.addAll(loadPlugins);
        LOGGER.info("NanoForgeTweakerWrapper Injected.");
    }

    //TODO handleCascadingTweak
    //Update: No, we shouldn't expose or load tweakers. it will removed in future.
    private static final Map<String,Integer> tweakSorting = new HashMap<>();


    /**
     * Sorting tweakers, we use a some workaround method in RFB
     */
    public static void sortTweakList() {
        if (Launch.blackboard.containsKey("TweaksSorted")) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        List<ITweaker> sortedTweakers = new ArrayList<>(tweakers);

        sortedTweakers.sort(Comparator.comparingInt(tweaker -> {
            if (tweaker instanceof NanoForgeTweaker) {
                return Integer.MIN_VALUE;
            }
            if (tweaker instanceof NanoPluginWrapper wrapper) {
                return wrapper.sortIndex;
            }
            return tweakSorting.getOrDefault(tweaker.getClass().getName(), 0);
        }));

        //In RFB, We must do this shit. because RFB's impl is different of LaunchWrapper
        Launch.blackboard.put("Tweaks", sortedTweakers);

        LOGGER.info("Tweaker Sorted.");
        Launch.blackboard.put("TweaksSorted", Boolean.TRUE);
    }



    private static class NanoPluginWrapper implements ITweaker {
        public final String name;
        public final INanoCorePlugin corePlugin;
        public final int sortIndex;

        public NanoPluginWrapper(INanoCorePlugin corePlugin) {
            this.name = corePlugin.getName();
            this.corePlugin = corePlugin;
            this.sortIndex = corePlugin.getPriority();
        }


        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile){
            //skip
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
            if (corePlugin == null) {
                throw new IllegalStateException("Core mod instance is null");
            }

            var transformerExclusion = corePlugin.getTransformerExclusion();
            var asmTransformerClass = corePlugin.getASMTransformerClass();



            if (!transformerExclusion.isEmpty()){
                transformerExclusion.forEach(classLoader::addTransformerExclusion);
            }

            if (!asmTransformerClass.isEmpty()){
                asmTransformerClass.forEach(classLoader::registerTransformer);
            }



            //building data
            Map<String, Object> data = new HashMap<>();
            data.put("gameData", GameData.getData());
            data.put("pluginList", loadPlugins);
            data.put("mixinOwnerList",pluginMixins);

            //IDK what happen early class loaded will make...
            //so please don't touch any game class in there, using reflection if you needed.
            LOGGER.info("Injecting Data to {}",corePlugin.getClass().getName());
            corePlugin.injectData(data);
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


}
