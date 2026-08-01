# NanoForge 实现计划与进度跟踪

> 版本：v1（2026-08-01） 上游设计：docs/design/architecture.md

## 轮次总览

| 轮次 | 目标 | 状态 |
|---|---|---|
| R1 | CoreMod 骨架完整落地 | ✅ 已完成（分支 feat/coremod-skeleton，commit 03b56ce） |
| R2 | Patch 开发工作流（bin patch） | ✅ 已完成（游戏内冒烟已随 R3 关闭，见 R2 节说明） |
| R3 | 跨平台运行时承载 + deobf 全量模式接管 | ✅ 已完成（分支 feat/coremod-skeleton） |
| R4 | SSOptimizer coremod 化 | ✅ 已完成（分支 feat/coremod-skeleton / SSOptimizer feat/deobf-game） |
| R5 | 专用启动器对接 | ⬜ 未开始（前置：R4） |

> 设计修正（2026-08-02）：全平台统一部署 windows 版 named jar，**不存在**
> linux/macos obf jar 的 remap 对位链路。跨平台任务 = 运行时环境层
> （natives / 启动 / OS 分支），与 mapping 体系正交；intermediary 命名空间
> 定位为「未命名成员稳定引用名 + 跨游戏版本 mapping 迁移锚点」。
> 详见 architecture.md §1.1。

---

## R1 CoreMod 骨架 ✅

**范围**：coremod.toml 元数据、依赖排序、统一变换管线（ASM/Mixin 数据表）、测试与 CI。

交付物：

- [x] `core/meta/CoreModMeta` + `CoreModMetaParser` + `CoreModMetaException`
- [x] `core/meta/CoreModSorter`（Kahn 拓扑 + 环/缺失/重复诊断）
- [x] `core/CoreModDiscovery` + `core/CoreModAssembly`（纯逻辑装配管线）
- [x] `core/CoreModManager` 重写（直线装配应用；修复旧 wrapper tweaker 死代码导致 transformer 从未注册的缺陷）
- [x] `api/INanoCorePlugin` 收敛为 `onLoad(CoreModContext)`；新增 `api/CoreModContext`
- [x] 移除 SPI/`@NanoCorePluginInfo`/`IMixinLoader`/`GameData`/`NanoForgeTweaker`
- [x] 包名统一 `io.github.nanoforged`；Gradle 9.4.1；JUnit 5
- [x] 测试 15 用例全绿（解析 6 / 排序 7 / 发现集成 2）；GitHub Actions CI
- [x] docs/README.md coremod.toml 规范

验收（已达成）：`./gradlew build` 全绿；toml 为唯一元数据来源；非法 toml/环/缺依赖明确报错。

## R2 Patch 开发工作流 ✅（游戏内冒烟已随 R3 关闭）

**前置**：SourceSector 产出 named jar + sources（开发基线）✅ 已达成
（`:mapping:publishNamedGameJars` → `build/named-game-repo/windows`）。

- [x] 开发侧：`core/patch/PatchGenerator` + `PatchGenCli` + Gradle `generatePatches`
      任务——修改 named 源码编译后与原 named jar 逐类 diff，生成 NFBP 类级
      bin patch（badiff），patch 内含原类 SHA-256 基线，写出前 badiff 回验
- [x] 运行时：`core/patch/PatcherManager` + `core/asm/tweakers/NanoPatcherTransformer`
      落地，patch 在 transformer 链最前应用（先于 coremod ASM/Mixin）；
      生效索引经 `PatcherManager.activePatches()` 供 LaunchWrapper 无参实例化读取
- [x] 基线校验失败显式报错（含类名/期望与实际哈希/来源 coremod），不做静默跳过；
      同类被两个 coremod patch 装配期报错
- [x] coremod.toml 扩展 `[patch] entries` 数据表
- [x] 端到端集成测试（`CoreModPatchIntegrationTest`）：真实 coremod jar → 发现 →
      装配 → patch 加载 → transformer 命中 → patched 字节能被真实类加载器加载
- [x] 生成冒烟：真实 windows named jar（fs.sound_obf.jar）改类 → CLI 生成
      .binpatch → 独立回验 apply 结果与修改后类字节一致
- [x] 游戏内冒烟：**已随 R3 关闭**。R3 搭建的可启动运行环境上实证：
      修改 named 源码 `StarfarerLauncher` 日志串 → 编译 → `generatePatches`
      产出 binpatch → 测试 coremod 声明 `[patch] entries` 放入 `mods/coremods/`
      → 游戏日志确认 patch 注册、coremod onLoad、patched 日志串生效
      （R3 冒烟时游戏目录为 linux named jar，与最终「统一 windows 产物」
      模型不一致，但该冒烟以目录内现字节为基线，结论不受影响）

交付物：`core/patch/`（PatchException/ClassPatch/PatchFormat/PatchGenerator/PatchGenCli）、
`PatcherManager`、`NanoPatcherTransformer`、CoreModMeta(Parser) `[patch]` 扩展、
`generatePatches` 任务、badiff 1.2.1 依赖；测试 34 用例全绿
（patch 格式 6 / 生成器 2 / 运行时加载 5 / transformer 3 / 元数据 10 / 排序 7 / 发现集成 2 / patch 端到端 1）。

## R3 跨平台运行时承载 + deobf 全量模式接管 ✅

**前置**：SourceSector 单 mapping（windows 基准，obf/intermediary/named 三列）✅ 已达成。

按修正后的跨平台模型（architecture.md §1.1）：全平台统一部署 windows 版
named jar，NanoForge 不做任何 obf jar 对位/remap。

- [x] natives 装配：`extractGameNatives` 从游戏 vendor 的 lwjgl/jinput
      platform natives jar 按 OS 提取到 `lib/native/{linux,macos,windows}`
      （游戏字节码与 natives 正交）
- [x] linux/macos 启动脚本：`launch_nanoforge_ss.sh`（linux 实机验证）+
      `launch_nanoforge_ss.command`（macOS 已写未验证）；RFB Main +
      `--tweakClass io.github.nanoforged.NanoForgeBootstrap`，classpath 置前
      `mods/nanoforge/*.jar`，排除 log4j-1.2.9.jar 与原 lwjgl.jar
- [x] OS 条件分支审计：实查 windows named 源码，行为分支仅 3 处，
      `-Dcom.fs.starfarer.settings.linux=true` 即全覆盖，无需 Patch
      （docs/design/os-branch-audit.md）
- [x] deobf 全量模式接管：`core/remap`（7 类，移植自 SSOptimizer mapping
      模块，适配 SourceSector 三命名空间表）+ `NanoRemapTransformer`；
      开关 `-Dnanoforge.remap.obf2named`，默认表
      `mods/nanoforge/game-full.tiny.gz`（`packFullMapping` 产出）；
      transformer 链顺序 patch → remap → ASM/Mixin
- [x] 运行时适配（实机验证）：`unsealLwjgl` 去 lwjgl 密封；slf4j2 绑定换
      `log4j-slf4j2-impl:2.25.2`；mixin 类拆至独立根包 `nanoforge.mixin` +
      独立 `NanoForge-mixins.jar`（RFB 将 tweaker 包整体注册为
      LaunchClassLoader 排除项所致，architecture.md §2.5）
- [x] 游戏内冒烟全过：游戏进主菜单渲染循环，无 deadlock；remap 表
      221585 条加载无失败 WARN；bin patch 游戏内生效（R2 移交项关闭）；
      mixin 生效（NanoForge Injected）；无 Classloader restrictions

交付物：`core/remap/`（MappingDirection/MappingLookupException/MappingEntry/
MappingRepository/TinyV2MappingRepository/BytecodeRemapper/NanoRemapContext）、
`NanoRemapTransformer`、`packFullMapping`/`extractGameNatives`/`unsealLwjgl`/
`deployToGame` 任务、启动脚本 ×2；测试 50 用例全绿（新增 remap 16）。

**不做**（原 R3 错误前提下的条目，已删除）：对任意平台游戏 jar 做
obf→intermediary 指纹对位；非 windows 平台运行时 obf→named 解析与缓存；
汉化新增类的对位失败兜底。

**遗留**：游戏目录当前部署的 4 个游戏 jar 为 SSOptimizer 时代的 linux named
产物，后续应切换为 windows named（SourceSector `build/named-game-repo/windows`）；
游戏目录改动（启动脚本、mods/ 部署）不入任何 Git 仓库。

## R4 SSOptimizer coremod 化 ✅

**前置**：R3 deobf 全量模式接管 ✅；NanoForge 发布 mavenLocal SNAPSHOT
（`io.github.nanoforged:NanoForge:0.1.0-SNAPSHOT`，maven-publish 插件）✅。

NanoForge 侧：

- [x] maven-publish 接入，`publishToMavenLocal` 供 SSOptimizer 编译期依赖
- [x] sanitize 必要性核查（docs/design/sanitize-audit.md）：非法标识符仅
      SourceSector windows named jar 中 `EngineSlot` 5 个字段（identity 保持
      原名所致），字符串/反射引用为零 → Sanitizing/ReflectionSanitizing
      不移植，留待 SourceSector 侧修正映射
- [x] docs/README.md 补充 coremod 伴生目录约定（`mods/<coremod-id>/`）与
      **RFB transformer 契约警告**：RFB `runTransformers` 无条件采用返回值，
      与原版 LaunchWrapper「返回 null 表示未修改」契约不同，transformer
      必须透传原始字节
- [x] `CoreModManager.instantiate` 永久性 ERROR 诊断日志（pluginClass CNFE
      时输出 jarInSources/resourceFound）

SSOptimizer 侧（分支 feat/deobf-game）：

- [x] javaagent 通道整体删除（agent/bootstrap/remap/sanitize/MixinBridge 及
      `:agent-api`/`:mod-optimizations` 子项目）；api/mapping/modopt.dcr
      源码并入 `:app`
- [x] 新入口 `bootstrap/SSOptimizerCorePlugin`（INanoCorePlugin，onLoad
      注册 25 项处理器含 DCR 直注册）；`HybridWeaverTransformer` 改
      IClassTransformer 静态注册表 + 同类重入透传防护（RFB 契约适配）
- [x] `coremod.toml`（[asm] 1 transformer + [mixin] mixins.ssoptimizer.json）；
      named 依赖切 SourceSector windows 仓（`-Psourcesector.namedRepo=`）
- [x] 构建链清理：jarMapped/jarReobf/reobf 验证/runClientExec/launch-config.json
      /javaagent 启动脚本全部移除；发布物改为 app jar 双轨
      （mods/ssoptimizer/jars + mods/coremods）；CI/release 工作流同步
- [x] 游戏内冒烟（launcher 等价）：0 ERROR、25 项处理器注册、Mixin 生效、
      native lib 加载、字体 override 生效、jstack 确认主菜单渲染循环

验收（已达成）：双仓库 `./gradlew build` 全绿；游戏内冒烟优化全链路生效。

**排坑记录**（详见 SSOptimizer 侧提交）：

1. shade 的 jctools `module-info.class` 使 RFB 把 jar 当命名模块 → jar 任务
   排除 `module-info.class` / `META-INF/versions/**`；
2. RFB transformer 契约差异（见上 docs/README.md 警告）→ weaver 全部改
   透传原始字节；
3. `LauncherDirectStartProcessor` 内部类懒加载 → Mixin 反读同类 →
   ClassCircularityError → weaver 加 IN_FLIGHT 同类重入透传防护。

## R5 专用启动器 ⬜

- [ ] 专用启动器（modified game 启动）取代 RFB tweaker 临时路径
- [ ] 游戏内全量冒烟三模式脚本化（launcher/game/automation，smoke 脚本已
      支持 `SSOPTIMIZER_SMOKE_LAUNCH_SCRIPT` 环境变量指定启动脚本）
- [ ] Windows 端 NanoForge 启动入口（当前仅 linux 脚本实机验证）
- [ ] 游戏目录 4 个游戏 jar 切换为 windows named（SourceSector
      `build/named-game-repo/windows`）

---

## SourceSector 侧跟进项（不占用 NanoForge 轮次）

- [ ] **vendor linux jar 版本核实**：`cross-platform-match.txt` 仅 1312/2932 匹配，
      疑为 linux/windows vendor jar 游戏版本不一致（而非真实平台分支）；
      同版本后重跑，匹配率应接近 100%——这是「统一 windows 产物」前提的门禁验证
- [ ] mapping 版本化存库：按 `0.98a-rc8-windows-full.tiny` 等命名存放已发布版本的
      全量表，为跨版本 mapping 迁移（intermediary 锚点）与多版本并存做准备
- [ ] linux scope 片段 / 小表资源处置：保留冻结还是清理，待 linux vendor
      核实后一并决定

---

## 变更记录

- 2026-08-01：文档建立；R1 完成并提交（feat/coremod-skeleton）。
- 2026-08-02：跨平台模型修正（architecture.md §1.1）——全平台统一 windows 版
  named jar，删除 R3 的 obf jar 对位/remap 条目，R3 改为跨平台运行时承载 +
  deobf 接管；SourceSector 三命名空间 mapping 已落地（`86c4014`），R2 前置达成。
- 2026-08-02：R2 完成——Patch 开发工作流（bin patch）全链路落地，测试 34 用例
  全绿，生成冒烟以真实 named jar 通过；游戏内冒烟移交 R4（NanoForge 运行环境
  尚不存在，搭建即 R4 专用启动器本职工作）。
- 2026-08-02：R3 完成——跨平台运行时承载 + deobf 全量模式接管。core/remap
  移植（三命名空间适配）、启动脚本 + deployToGame、unsealLwjgl/slf4j2/mixin
  jar 拆分三项运行时适配，测试 50 用例全绿；linux 实机冒烟全过（启动进主菜单、
  bin patch 游戏内生效、remap 表 221585 条无失败、mixin 生效），R2 游戏内
  冒烟一并关闭。
- 2026-08-02：R4 完成——SSOptimizer coremod 化。NanoForge 侧 maven-publish /
  sanitize 核查（不移植，结论见 docs/design/sanitize-audit.md）/ RFB transformer
  契约文档化；SSOptimizer 侧 javaagent 通道整体退役、收编为 coremod
  （SSOptimizerCorePlugin + coremod.toml），构建链与发布物改双轨布局。
  游戏内冒烟 0 ERROR、25 项处理器注册生效。原「专用启动器」条目拆为 R5。
