package io.github.nanoforged.launchspec;

/**
 * JVM 参数模板的可覆盖项。
 *
 * <p>所有字段都有与启动脚本 launch_nanoforge_ss.sh 一致的默认值；未显式覆盖的
 * 项保持脚本基线。堆大小等关键项按启动器需要覆写，其余（add-opens 等固定参数）
 * 不可配置，保持脚本基线不变。
 *
 * <p>各路径类字段（compilerDirectives/libraryPath/各 starfarer 路径属性）均为
 * 相对路径，语义与脚本一致：解析于启动时工作目录（游戏根目录）。
 */
public final class JvmArgsOptions {

    /** 默认 -Xms 值，脚本基线 16g。 */
    public static final String DEFAULT_HEAP_MIN = "16g";
    /** 默认 -Xmx 值，脚本基线 16g。 */
    public static final String DEFAULT_HEAP_MAX = "16g";
    /** 默认 -Xss 值，脚本基线 4m。 */
    public static final String DEFAULT_STACK_SIZE = "4m";
    /** 默认 -XX:CompilerDirectivesFile 路径，脚本基线。 */
    public static final String DEFAULT_COMPILER_DIRECTIVES = "./compiler_directives.txt";
    /** 默认 -Djava.library.path 值（linux 基线；macos/windows 由启动器按 OS 覆写）。 */
    public static final String DEFAULT_LIBRARY_PATH = "./native/linux";
    /** 默认存档路径属性，脚本基线。 */
    public static final String DEFAULT_SAVES_PATH = "./saves";
    /** 默认截图路径属性，脚本基线。 */
    public static final String DEFAULT_SCREENSHOTS_PATH = "./screenshots";
    /** 默认 mods 路径属性，脚本基线。 */
    public static final String DEFAULT_MODS_PATH = "./mods";
    /** 默认日志路径属性，脚本基线为游戏根目录本身。 */
    public static final String DEFAULT_LOGS_PATH = ".";
    /** 默认 deobf（全量反混淆运行时重映射）开关，脚本基线为开启。 */
    public static final boolean DEFAULT_DEOBF = true;

    private final String heapMin;
    private final String heapMax;
    private final String stackSize;
    private final String compilerDirectives;
    private final String libraryPath;
    private final String savesPath;
    private final String screenshotsPath;
    private final String modsPath;
    private final String logsPath;
    private final boolean deobf;

    private JvmArgsOptions(Builder builder) {
        this.heapMin = requireText(builder.heapMin, "heapMin");
        this.heapMax = requireText(builder.heapMax, "heapMax");
        this.stackSize = requireText(builder.stackSize, "stackSize");
        this.compilerDirectives = requireText(builder.compilerDirectives, "compilerDirectives");
        this.libraryPath = requireText(builder.libraryPath, "libraryPath");
        this.savesPath = requireText(builder.savesPath, "savesPath");
        this.screenshotsPath = requireText(builder.screenshotsPath, "screenshotsPath");
        this.modsPath = requireText(builder.modsPath, "modsPath");
        this.logsPath = requireText(builder.logsPath, "logsPath");
        this.deobf = builder.deobf;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    /** -Xms 堆初始大小（如 "16g"）。 */
    public String heapMin() {
        return heapMin;
    }

    /** -Xmx 堆最大大小（如 "16g"）。 */
    public String heapMax() {
        return heapMax;
    }

    /** -Xss 线程栈大小（如 "4m"）。 */
    public String stackSize() {
        return stackSize;
    }

    /** -XX:CompilerDirectivesFile 指向的 directives 文件路径。 */
    public String compilerDirectives() {
        return compilerDirectives;
    }

    /** -Djava.library.path 原生库搜索路径。 */
    public String libraryPath() {
        return libraryPath;
    }

    /** -Dcom.fs.starfarer.settings.paths.saves 存档路径。 */
    public String savesPath() {
        return savesPath;
    }

    /** -Dcom.fs.starfarer.settings.paths.screenshots 截图路径。 */
    public String screenshotsPath() {
        return screenshotsPath;
    }

    /** -Dcom.fs.starfarer.settings.paths.mods mods 目录路径。 */
    public String modsPath() {
        return modsPath;
    }

    /** -Dcom.fs.starfarer.settings.paths.logs 日志路径。 */
    public String logsPath() {
        return logsPath;
    }

    /**
     * 是否启用全量 deobf 运行时重映射（-Dnanoforge.remap.obf2named）。
     *
     * <p>脚本基线为开启；关闭后模板不产出该属性，游戏以混淆名运行
     * （供对照调试与兼容回退）。
     */
    public boolean deobf() {
        return deobf;
    }

    /**
     * 创建构造器；各字段预置脚本基线默认值，仅显式覆盖需要的项。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 可覆盖项构造器。字段默认取脚本基线，见 {@link JvmArgsOptions} 各 DEFAULT_* 常量。
     */
    public static final class Builder {

        private String heapMin = DEFAULT_HEAP_MIN;
        private String heapMax = DEFAULT_HEAP_MAX;
        private String stackSize = DEFAULT_STACK_SIZE;
        private String compilerDirectives = DEFAULT_COMPILER_DIRECTIVES;
        private String libraryPath = DEFAULT_LIBRARY_PATH;
        private String savesPath = DEFAULT_SAVES_PATH;
        private String screenshotsPath = DEFAULT_SCREENSHOTS_PATH;
        private String modsPath = DEFAULT_MODS_PATH;
        private String logsPath = DEFAULT_LOGS_PATH;
        private boolean deobf = DEFAULT_DEOBF;

        private Builder() {
        }

        /** 覆盖 -Xms 堆初始大小。 */
        public Builder heapMin(String heapMin) {
            this.heapMin = heapMin;
            return this;
        }

        /** 覆盖 -Xmx 堆最大大小。 */
        public Builder heapMax(String heapMax) {
            this.heapMax = heapMax;
            return this;
        }

        /** 覆盖 -Xss 线程栈大小。 */
        public Builder stackSize(String stackSize) {
            this.stackSize = stackSize;
            return this;
        }

        /** 覆盖 -XX:CompilerDirectivesFile 路径。 */
        public Builder compilerDirectives(String compilerDirectives) {
            this.compilerDirectives = compilerDirectives;
            return this;
        }

        /** 覆盖 -Djava.library.path 值。 */
        public Builder libraryPath(String libraryPath) {
            this.libraryPath = libraryPath;
            return this;
        }

        /** 覆盖 -Dcom.fs.starfarer.settings.paths.saves 存档路径。 */
        public Builder savesPath(String savesPath) {
            this.savesPath = savesPath;
            return this;
        }

        /** 覆盖 -Dcom.fs.starfarer.settings.paths.screenshots 截图路径。 */
        public Builder screenshotsPath(String screenshotsPath) {
            this.screenshotsPath = screenshotsPath;
            return this;
        }

        /** 覆盖 -Dcom.fs.starfarer.settings.paths.mods mods 目录路径。 */
        public Builder modsPath(String modsPath) {
            this.modsPath = modsPath;
            return this;
        }

        /** 覆盖 -Dcom.fs.starfarer.settings.paths.logs 日志路径。 */
        public Builder logsPath(String logsPath) {
            this.logsPath = logsPath;
            return this;
        }

        /** 覆盖 deobf 全量反混淆开关（默认开启，见 {@link JvmArgsOptions#DEFAULT_DEOBF}）。 */
        public Builder deobf(boolean deobf) {
            this.deobf = deobf;
            return this;
        }

        /** 构造不可变的 {@link JvmArgsOptions}。 */
        public JvmArgsOptions build() {
            return new JvmArgsOptions(this);
        }
    }
}
