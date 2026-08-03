package io.github.nanoforged.launchspec;

import io.github.nanoforged.launchspec.impl.JvmArgsTemplateImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JVM 参数基线模板的真实逻辑验证：默认值对齐启动脚本，覆盖项正确替换。
 */
class JvmArgsTemplateTest {

    private final JvmArgsTemplate template = new JvmArgsTemplateImpl();

    @Test
    void defaultsMatchScriptBaseline() {
        List<String> args = template.resolve(JvmArgsOptions.builder().build());

        // 脚本基线共 46 个 JVM 参数（13 add-opens + 3 add-exports + 其余固定项）
        assertEquals(46, args.size(), "默认参数总数应对齐脚本基线");

        // 关键固定项与顺序
        assertEquals("-Dfile.encoding=UTF-8", args.get(0));
        assertEquals("-noverify", args.get(1));
        assertEquals("-XX:CompilerDirectivesFile=./compiler_directives.txt", args.get(11));
        assertTrue(args.contains("-Xms16g"));
        assertTrue(args.contains("-Xmx16g"));
        assertTrue(args.contains("-Xss4m"));
        assertEquals(13, args.stream().filter(arg -> arg.startsWith("--add-opens=")).count());
        assertEquals(3, args.stream().filter(arg -> arg.startsWith("--add-exports=")).count());

        // 脚本基线其余参数逐一有着落
        assertTrue(args.contains("--enable-preview"));
        assertTrue(args.contains("--enable-native-access=ALL-UNNAMED"));
        assertTrue(args.contains("-XX:+UseZGC"));
        assertTrue(args.contains("-XX:+AlwaysPreTouch"));
        assertTrue(args.contains("-XX:ReservedCodeCacheSize=256m"));
        assertTrue(args.contains("-Djdk.xml.maxElementDepth=10000"));
        assertTrue(args.contains("-Djava.util.Arrays.useLegacyMergeSort=true"));
        assertTrue(args.contains("-Djava.library.path=./native/linux"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.saves=./saves"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.screenshots=./screenshots"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.mods=./mods"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.logs=."));
        assertTrue(args.contains("-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader"));
        assertTrue(args.contains("-Dnanoforge.remap.obf2named=true"));
        assertTrue(args.contains("-Dssoptimizer.font.ttf.enable=true"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.linux=true"));
    }

    @Test
    void overridesSubstituteKeyArgs() {
        List<String> args = template.resolve(JvmArgsOptions.builder()
                .heapMin("12g")
                .heapMax("24g")
                .stackSize("8m")
                .libraryPath("./native/macos")
                .compilerDirectives("./jvm/cd.txt")
                .savesPath("/data/saves")
                .build());

        assertTrue(args.contains("-Xms12g"));
        assertFalse(args.contains("-Xms16g"));
        assertTrue(args.contains("-Xmx24g"));
        assertTrue(args.contains("-Xss8m"));
        assertTrue(args.contains("-Djava.library.path=./native/macos"));
        assertTrue(args.contains("-XX:CompilerDirectivesFile=./jvm/cd.txt"));
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.saves=/data/saves"));
        // 未覆盖项保持基线
        assertTrue(args.contains("-Dcom.fs.starfarer.settings.paths.logs=."));
    }

    @Test
    void heapArgsKeepScriptRelativeOrder() {
        List<String> args = template.resolve(JvmArgsOptions.builder().heapMin("1g").build());
        int xms = args.indexOf("-Xms1g");
        int xmx = args.indexOf("-Xmx16g");
        int xss = args.indexOf("-Xss4m");
        assertTrue(xms >= 0 && xmx > xms && xss > xmx,
                "-Xms/-Xmx/-Xss 应按脚本顺序排列，实际索引: " + xms + "/" + xmx + "/" + xss);
    }

    @Test
    void deobfDisabledEmitsExplicitFalse() {
        List<String> args = template.resolve(JvmArgsOptions.builder().deobf(false).build());

        // NanoForge 缺省开启 remap，关闭必须显式产出 false，否则关闭选项静默失效
        assertTrue(args.contains("-Dnanoforge.remap.obf2named=false"),
                "deobf=false 时应显式产出 obf2named=false，实际: " + args);
        assertFalse(args.contains("-Dnanoforge.remap.obf2named=true"));
        // 其余基线项不受影响
        assertTrue(args.contains("-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader"));
        assertTrue(args.contains("-Dssoptimizer.font.ttf.enable=true"));
    }

    @Test
    void nullOptionsThrows() {
        assertThrows(NullPointerException.class, () -> template.resolve(null));
    }

    @Test
    void blankOverrideThrowsWithMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> JvmArgsOptions.builder().heapMin(" ").build());
        assertTrue(e.getMessage().contains("heapMin"), e.getMessage());
    }
}
