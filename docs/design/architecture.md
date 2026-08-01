# NanoForge 设计文档

> 版本：v1（2026-08-01） 状态：R1/R2 已落地

## 1. 定位

NanoForge 是面向 Starsector（远行星号）的 CoreMod 加载框架，目标是建立
「统一化 ASM/Mixin」的类 Minecraft NeoForge/Fabric 社区模组生态基座。

在三项目拆分中的生态位：

```
SourceSector   ── mapping 工作流（obf → intermediary → named，windows 单基准）
     │ 产出 named jar / sources jar / 单套全量 mapping（全平台统一产物）
     ▼
NanoForge      ── 运行时：CoreMod 加载器（本项目）
     │ 承载 Patch/ASM/Mixin 与跨平台运行时环境（natives / 启动 / OS 分支）
     ▼
SectorDevGradle ── 构建侧：mod 作者 Gradle 工具链，把前两者接给下游
SSOptimizer    ── 最终退化为 NanoForge 上的一个 coremod（性能优化 + deobf 运行时）
```

### 1.1 跨平台模型（2026-08-02 修正）

**全平台统一部署 windows 版 named jar**，字节码只有一份产物。不存在
「拿 windows mapping 去 remap linux/macos obf jar」的对位链路——linux/macos
的 obf jar 不进入任何处理流程。

NanoForge 的跨平台任务因此是**运行时环境层**而非字节码层：

- natives 装配：lwjgl / jinput 平台库按 OS 提供（游戏字节码与 natives 正交）；
- 启动入口：linux/macos 启动脚本（包装或取代官方 launcher）；
- OS 条件分支：游戏内 `System.getProperty("com.fs.starfarer.settings.{linux,osx}")`
  等平台分支在统一 windows jar 下的行为审计与必要时的 Patch 修正。

SourceSector 侧的跨平台指纹对位报告仅作为**前提验证门禁**（确认双平台 jar
无结构差异），不是 remap 的输入。intermediary 命名空间的价值相应定位为：
① named jar 中未命名类/成员的稳定引用名（CoreMod ASM/Mixin 数据表直接引用）；
② 跨游戏版本 mapping 迁移锚点（obf 名随版本重洗，结构指纹不变，
旧 named 名经 intermediary 自动挂到新版本——Fabric intermediary 的本职用法）。

## 2. 架构

### 2.1 启动链（临时路径，后续有专用启动器）

```
RFB Main (com.gtnewhorizons.retrofuturabootstrap.Main)
  └─ --tweakClass io.github.nanoforged.NanoForgeBootstrap   ← LaunchWrapper tweaker
       ├─ NanoForgeLaunchHelper.configureLaunch()
       │    ├─ MixinBootstrap.init() + MixinExtrasBootstrap.init()
       │    ├─ Mixins.addConfiguration("nanoforge.init.mixins.json")
       │    └─ transformer/classloader exclusion（自身包、sponge、lwjgl、slf4j…）
       └─ CoreModManager.handleLaunch()                     ← coremod 装配（见 2.2）
游戏 main: com.fs.starfarer.StarfarerLauncher
  └─ StarfarerLauncherMixin @Inject HEAD → NanoForge.init()  ← EventBus 启动
```

决策记录：启动路线（RFB tweaker vs javaagent）**均为临时路径**，后续由专用启动器
（modified game 启动）取代，现阶段不为启动链做额外投入。

### 2.2 CoreMod 装配管线（第一轮核心）

```
mods/coremods/*.jar
  │  CoreModDiscovery.scan()        纯逻辑：无 coremod.toml 的 jar 跳过（WARN）
  ▼
List<CoreModMeta>                   纯数据：id/name/version/depends/priority/pluginClass
  │  CoreModMetaParser              night-config 解析 + 必填/类型校验 + 未知键 WARN
  ▼
CoreModAssembly.assemble()
  │  CoreModSorter.sort()           Kahn 拓扑：depends → priority 升序 → id 字典序（确定性）
  ▼
CoreModAssembly                     纯数据装配计划（aggregated exclusions/transformers/mixins）
  │  CoreModManager.apply()         唯一接触运行时设施的地方
  ▼
1. coremod jar addURL → LaunchClassLoader
2. PatcherManager.load() 读取各 coremod [patch] entries → NanoPatcherTransformer 注册（链最前）
3. transformerExclusions / asmTransformers 按序注册
4. mixinConfigs 统一登记（Early Mixin）
5. pluginClass 实例化（公开无参构造）→ 按依赖序 onLoad(CoreModContext)
```

设计要点：

- **coremod.toml 是唯一元数据来源**。SPI 发现、`@NanoCorePluginInfo` 注解、
  `IMixinLoader` 接口、`injectData(Map)` 自由传参已全部移除，避免多套机制漂移。
- **纯逻辑与运行时解耦**：发现/解析/排序/装配计划生成不触碰 LaunchClassLoader
  与 Mixin 静态状态，全链路可单元测试；`CoreModManager.apply()` 是薄壳。
- **错误显式化**：依赖缺失、依赖环（打印环路径）、重复 id、非法 toml、
  pluginClass 不存在/未实现接口/无无参构造，均在启动期抛出带来源的诊断，
  无静默兜底。
- **INanoCorePlugin 收敛为生命周期钩子**：`onLoad(CoreModContext)`；
  context 提供 meta / 游戏路径 / 按 id 命名的 logger。EventBus 不进 context
  （`NanoForge.EVENT_BUS` 静态可达，且 init 时序晚于装配）。

### 2.3 依赖语义

- `depends`：硬依赖。缺失即启动失败；同时约束加载顺序（被依赖者先加载）。
- `priority`：同层次序裁决，升序先加载（越小越早），默认 0。
- 同 priority 按 id 字典序兜底，保证同输入必然同输出。
- 软依赖（loadAfter/loadBefore）刻意不设计，出现真实需求再加。

### 2.4 Patch 开发工作流（R2，bin patch）

比 ASM/Mixin 更底层的修改通道：直接修改 named 游戏源码，编译后与原 named
jar 逐类 diff 出类级二进制补丁，运行时整类替换。

```
开发侧（本仓库 generatePatches 任务）
原 named jar（SourceSector 产出） + 修改源码编译的 class 目录
  │  PatchGenerator（core/patch，纯逻辑）
  │    · 类名以 ClassReader 内部名为准
  │    · 原 jar 不存在的类 = 新增类（随 coremod 常规类分发，INFO 跳过）
  │    · 字节一致的类跳过；写出前 badiff 回验（原类+diff==修改后类）
  ▼
<类内部名>.binpatch（NFBP 格式：魔数/版本/类名/原类 SHA-256 基线/badiff diff）
  │  打进 coremod jar，[patch] entries 逐项声明
  ▼
运行时
PatcherManager.load()   按依赖序读全部 entries → 类名索引（同类冲突装配期报错）
  │  PatcherManager.activePatches() 静态生效索引
  ▼
NanoPatcherTransformer  transformer 链最前（先于 coremod ASM/Mixin）
  │  命中 → SHA-256 基线校验 → badiff apply；未命中透传
  ▼
patched 类字节进入后续 transformer 链
```

设计要点：

- **目标命名空间 = named**：patch 只在 deobf 运行时生效；obf 运行时类名
  不匹配自然透传，不做 runtime remap（跨平台模型见 §1.1：全平台统一部署
  windows 版 named jar）。
- **基线显式校验**：原类字节 SHA-256 不符即抛 `PatchException`（含类名、
  期望/实际哈希、来源 coremod），游戏更新导致 patch 失效可追，不静默跳过。
- **冲突显式化**：同一类被两个 coremod patch，装配期报错并指明双方来源。
- **LaunchWrapper 约束**：`registerTransformer` 只接受类名并无参反射实例化，
  故 NanoPatcherTransformer 生产路径经无参构造读取
  `PatcherManager.activePatches()`（装配期 load 已先行写入）。
- **纯逻辑与运行时解耦**沿用 R1：PatchFormat/PatchGenerator/PatcherManager
  均为可单测纯逻辑；diff 算法用 badiff（`org.badiff:badiff:1.2.1`，
  纯 Java 无传递依赖）。

### 2.5 全量 remap 接管与运行时适配（R3）

deobf 全量模式自 SSOptimizer 接管：`core/remap` 解析 SourceSector
三命名空间全量表（`tiny 2 0 obf intermediary named`，成员行 desc 在末列，
未命名条目省略 named 列并回退为 intermediary），运行时把残留的
obf/intermediary 引用重定向到 named。

```
CoreModManager.apply（装配期）
  │  -Dnanoforge.remap.obf2named=true 时 NanoRemapContext.loadDefault()
  │    · 表路径 -Dnanoforge.remap.mapping= 覆盖，
  │      默认 mods/nanoforge/game-full.tiny.gz（packFullMapping 任务产出）
  │    · 写静态 activeContext() 供 LaunchWrapper 无参实例化读取
  ▼
transformer 链顺序：bin patch → obf→named remap → coremod ASM → Mixin
  ▼
NanoRemapTransformer（core/asm/tweakers）逐类 remap；失败 WARN 透传，不中断加载
```

运行时适配（linux 实机冒烟验证过的三个点）：

- **lwjgl 密封**：lwjgl.jar manifest 带 `Sealed: true`，与 RFB 包密封校验冲突
  （`Sealing violation in already loaded package org.lwjgl.opengles`）。
  `unsealLwjgl` 任务去除 Sealed 主属性产出 `lwjgl-unsealed.jar`，
  启动脚本以其顶替原 lwjgl.jar。
- **日志绑定**：`log4j-slf4j18-impl`（slf4j 1.8 绑定）在 slf4j 2.x 下
  AbstractMethodError，换 `log4j-slf4j2-impl:2.25.2`；游戏自带
  log4j-1.2.9.jar 从启动 classpath 排除（与 log4j2 共存冲突）。
- **mixin jar 拆分**：RFB 的 Launch 会把 tweaker 所在包
  （`io.github.nanoforged`）整体注册为 LaunchClassLoader 排除项，
  mixin 类必须经 LaunchClassLoader 加载（否则 PACKAGE_CLASSLOADER_EXCLUSION
  被拒）。故 mixin 类置于独立根包 `nanoforge.mixin`，与 init 配置单独打成
  `NanoForge-mixins.jar`（部署在主 jar 同级 `runtime/` 子目录，不在启动脚本
  -classpath glob 内），由 NanoForgeBootstrap 显式 `addURL` 进
  LaunchClassLoader；`-Dnanoforge.mixinJar=` 可覆盖路径。

natives：`extractGameNatives` 从游戏 vendor 的
`lwjgl-platform-2.9.3-natives-{linux,osx,windows}.jar` 与
`jinput-platform-2.0.7-natives-*.jar` 按 OS 提取到
`lib/native/{linux,macos,windows}`，游戏字节码与 natives 正交。
OS 条件分支审计结论见 `os-branch-audit.md`（分支面极小，设驱动属性即全覆盖）。

### 2.6 coremod 化配套结论（R4）

- **sanitize 不移植**：SSOptimizer javaagent 时代的 Sanitizing/ReflectionSanitizing
  通道面向「named jar 含非法标识符」场景；对 SourceSector 全部 windows named jar
  实查后非法标识符仅 `EngineSlot` 5 个字段（identity 保持原名所致），且无任何
  字符串/反射引用——不做运行时 sanitize，留待 SourceSector 侧修正映射。
  完整证据见 `sanitize-audit.md`。
- **coremod 伴生目录**：coremod  jar 放 `mods/coremods/`，其随附资源
  （native、数据文件等）放 `mods/<coremod-id>/`，由 coremod 自解析
  （约定见 docs/README.md）。
- **RFB transformer 契约**：RFB `runTransformers` 无条件采用 transformer 返回值
  （与原版 LaunchWrapper「返回 null 表示未修改」不同），coremod transformer
  未命中时必须透传原始字节，否则会丢弃类。docs/README.md 有显式警告。

## 3. 已排除（不做或不在近期）

| 项 | 原因 |
|---|---|
| 晚期 Mixin | 无 vanilla 模组加载点，需要时单独立项 |
| 普通模组（非 coremod）加载 | 游戏原生 mod 加载器已覆盖，NanoForge 不重复造 |
| 启动路线改造 | 临时路径（RFB tweaker + 启动脚本），等专用启动器 |

## 4. 技术栈基线

- Java 17（source/target），Gradle 9.4.1（JDK 25 可跑），JUnit 5（BOM 5.13）
- RFB 1.0.12（LaunchWrapper FOSS）、sponge-mixin 0.16.3+mixin.0.8.7、
  MixinExtras 0.5.0、neoforged bus 8.0.5、night-config 3.8.3（coremod.toml）
- CI：GitHub Actions（temurin 17 + `./gradlew build`）
