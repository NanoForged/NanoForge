package io.github.nanoforged.core.remap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JAR 重映射命令行入口。
 *
 * <p>供 Gradle {@code JavaExec} 任务与手动调用使用，承担编译期 named 游戏 jar 生成
 * 与发布期 reobf 产物两条链路（接替已下线的 SourceSector mapping 模块同名能力）。
 *
 * <pre>
 * JarRemapCli --mapping=&lt;full.tiny&gt; [--classpath=&lt;jar&gt;[,&lt;jar&gt;...]]
 *     batch  &lt;obf-to-named|named-to-obf&gt; &lt;outputDir&gt; &lt;inputJar...&gt;
 *     single &lt;obf-to-named|named-to-obf&gt; &lt;inputJar&gt; &lt;outputJar&gt;
 * </pre>
 *
 * <p>{@code --classpath} 声明帧重算（COMPUTE_FRAMES）的层级字节来源；输入 jar 自身
 * 自动纳入来源。缺省时层级不可达的合流落到 {@code java/lang/Object}（帧精度降级，
 * 校验语义不变）。
 */
public final class JarRemapCli {
    private JarRemapCli() {
    }

    /**
     * 命令行入口。
     *
     * @param args 命令行参数（见类注释）
     * @throws Exception 若重映射失败
     */
    public static void main(String[] args) throws Exception {
        int offset = 0;
        Path mappingFile = null;
        List<Path> classpathJars = new ArrayList<>();
        while (args.length > offset && args[offset].startsWith("--")) {
            if (args[offset].startsWith("--mapping=")) {
                mappingFile = Path.of(args[offset].substring("--mapping=".length()));
            } else if (args[offset].startsWith("--classpath=")) {
                for (String element : args[offset].substring("--classpath=".length()).split(",")) {
                    if (!element.isBlank()) {
                        classpathJars.add(Path.of(element));
                    }
                }
            } else {
                throw new IllegalArgumentException("不支持的参数: " + args[offset]);
            }
            offset++;
        }
        if (mappingFile == null) {
            throw new IllegalArgumentException("缺少 --mapping=<full.tiny> 参数");
        }
        if (args.length - offset < 4) {
            throw new IllegalArgumentException(
                    "用法: batch <direction> <outputDir> <inputJar...> | single <direction> <inputJar> <outputJar>");
        }

        String mode = args[offset];
        MappingDirection direction = parseDirection(args[offset + 1]);
        TinyV2MappingRepository repository = TinyV2MappingRepository.loadFromFile(mappingFile);

        List<Path> hierarchyJars = new ArrayList<>(classpathJars);
        // 帧重算层级来源只含输入 jar：batch 为 offset+3 起全部，single 仅 offset+2 一个
        // （offset+3 是尚不存在的输出路径）。
        int inputStart = offset + ("batch".equals(mode) ? 3 : 2);
        int inputEnd = "batch".equals(mode) ? args.length : inputStart + 1;
        for (int i = inputStart; i < inputEnd; i++) {
            hierarchyJars.add(Path.of(args[i]));
        }
        JarRemapper remapper = new JarRemapper(repository, direction,
                RemapClassHierarchy.ofJars(repository, direction, hierarchyJars));

        if ("batch".equals(mode)) {
            Path outputDir = Path.of(args[offset + 2]);
            for (int i = offset + 3; i < args.length; i++) {
                Path inputJar = Path.of(args[i]);
                Path outputJar = outputDir.resolve(inputJar.getFileName().toString());
                remapper.remapJar(inputJar, outputJar);
                System.out.println("[JarRemapCli] Remapped " + inputJar + " -> " + outputJar);
            }
            return;
        }

        if ("single".equals(mode)) {
            Path inputJar = Path.of(args[offset + 2]);
            Path outputJar = Path.of(args[offset + 3]);
            remapper.remapJar(inputJar, outputJar);
            System.out.println("[JarRemapCli] Remapped " + inputJar + " -> " + outputJar);
            return;
        }

        throw new IllegalArgumentException("不支持的模式: " + mode);
    }

    private static MappingDirection parseDirection(String rawDirection) {
        return switch (rawDirection) {
            case "obf-to-named" -> MappingDirection.OBFUSCATED_TO_NAMED;
            case "named-to-obf" -> MappingDirection.NAMED_TO_OBFUSCATED;
            default -> throw new IllegalArgumentException("不支持的 remap 方向: " + rawDirection);
        };
    }
}
