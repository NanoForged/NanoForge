package io.github.nanoforged.core.asm.system;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import io.github.nanoforged.core.asm.AsmHelper;
import io.github.nanoforged.api.SystemAsmBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.jar.Manifest;

/**
 * NanoForge 的 RFB 插件入口：把 {@link SystemAsmBridge} 注册表挂进 RFB 系统层
 * transformer 链，使 {@code org.lwjgl.} 等 classLoaderException 包（由 RFB 系统
 * 类加载器加载、Launch 域 transformer 链不可触及）的类可被改写。
 *
 * <p>由 {@code META-INF/rfb-plugin/nanoforge-system-asm.properties} 声明，
 * RFB 在 system classpath 扫描发现并实例化。</p>
 */
public final class NanoForgeSystemRfbPlugin implements RfbPlugin {

    @Override
    public RfbClassTransformer[] makeTransformers() {
        return new RfbClassTransformer[]{new SystemAsmRfbTransformer()};
    }

    /**
     * {@link SystemAsmBridge} 的 RFB transformer 适配：按注册表命中类名，
     * 命中即执行处理器并把产出字节写回 {@link ClassNodeHandle}。
     */
    static final class SystemAsmRfbTransformer implements RfbClassTransformer {
        private static final Logger LOGGER = LogManager.getLogger("NanoForge/SystemAsm");

        @Override
        public String id() {
            return "nanoforge-system-asm";
        }

        @Override
        public boolean shouldTransformClass(final ExtensibleClassLoader classLoader,
                                            final Context context,
                                            final Manifest manifest,
                                            final String className,
                                            final ClassNodeHandle classNode) {
            return SystemAsmBridge.hasProcessor(className);
        }

        @Override
        public void transformClass(final ExtensibleClassLoader classLoader,
                                   final Context context,
                                   final Manifest manifest,
                                   final String className,
                                   final ClassNodeHandle classNode) {
            final byte[] processed = SystemAsmBridge.process(className, classNode.getOriginalBytes());
            if (processed == null) {
                return;
            }
            classNode.setNode(AsmHelper.bytesToClassNode(processed));
            LOGGER.info("[NanoForge] System ASM transformed {} (context={})", className, context);
        }
    }
}
