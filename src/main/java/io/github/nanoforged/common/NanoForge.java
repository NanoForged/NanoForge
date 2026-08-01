package io.github.nanoforged.common;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.Sys;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NanoForge {
    public static final IEventBus EVENT_BUS = BusBuilder.builder().startShutdown().build();
    public static final Logger LOGGER = LogManager.getLogger(NanoForge.class.getSimpleName());
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile NanoForge instance = null;


    private NanoForge(){
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler::crashHandle);
        EVENT_BUS.start();
        LOGGER.info("NanoForge Injected!");
    }

    public static void init(){
        if (!initialized.compareAndSet(false, true)) {return;}
        try {
            instance = new NanoForge();
        } catch (Exception e){
            e.printStackTrace();
            Sys.alert("NanoForge","Failed to Inject NanoForge!");
            System.exit(1337);
        }
    }

    public static NanoForge getInstance() {
        if (!initialized.get() || instance == null) {
            throw new IllegalStateException("NanoForge has not been initialized. Please wait Init first.");
        }
        return instance;
    }



}
