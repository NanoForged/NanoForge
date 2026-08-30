package io.github.nanoforged.common;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JOptionPane;
import java.awt.HeadlessException;
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
            LOGGER.fatal("Failed to Inject NanoForge!", e);
            // 崩溃兜底弹窗用 Swing 而非 org.lwjgl.Sys.alert：System 域不得引用 org.lwjgl，
            // 否则 lwjgl 必须经 classLoaderExclusion 固定在系统类加载器，
            // Launch 域 transformer 链（ASM/Mixin）便摸不到 LinuxDisplay 等类（IME 注入前提）
            try {
                JOptionPane.showMessageDialog(null, "Failed to Inject NanoForge!", "NanoForge",
                        JOptionPane.ERROR_MESSAGE);
            } catch (HeadlessException he) {
                LOGGER.error("Headless 环境无法弹出错误窗口", he);
            }
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
