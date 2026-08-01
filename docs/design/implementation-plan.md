# NanoForge 实现计划与进度跟踪

> 版本：v1（2026-08-01） 上游设计：docs/design/architecture.md

## 轮次总览

| 轮次 | 目标 | 状态 |
|---|---|---|
| R1 | CoreMod 骨架完整落地 | ✅ 已完成（分支 feat/coremod-skeleton，commit 03b56ce） |
| R2 | Patch 开发工作流（bin patch） | ✅ 已完成（游戏内冒烟随 R4 启动器一并验证，见 R2 节说明） |
| R3 | 跨平台运行时承载 + deobf 全量模式接管 | ⬜ 未开始（前置：SourceSector 单 mapping ✅） |
| R4 | 专用启动器对接 / SSOptimizer coremod 化 | ⬜ 未开始（前置：R2/R3 + SSOptimizer 侧拆分） |

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

## R2 Patch 开发工作流 ✅（游戏内冒烟移交 R4）

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
- [ ] 游戏内冒烟：**移交 R4**。NanoForge 侧全链路（发现/装配/patch 加载/transformer）
      已有集成测试覆盖，但 NanoForge 自身尚无任何「能启动游戏」的运行环境
      （run 目录无游戏数据，lib/gameJar 为空，log4j 1.2.9 与 log4j2 的 classpath
      共存未验证）——搭建该环境即 R4 专用启动器的本职工作，不在 R2 重复造临时环境

交付物：`core/patch/`（PatchException/ClassPatch/PatchFormat/PatchGenerator/PatchGenCli）、
`PatcherManager`、`NanoPatcherTransformer`、CoreModMeta(Parser) `[patch]` 扩展、
`generatePatches` 任务、badiff 1.2.1 依赖；测试 34 用例全绿
（patch 格式 6 / 生成器 2 / 运行时加载 5 / transformer 3 / 元数据 10 / 排序 7 / 发现集成 2 / patch 端到端 1）。

## R3 跨平台运行时承载 + deobf 全量模式接管 ⬜

**前置**：SourceSector 单 mapping（windows 基准，obf/intermediary/named 三列）✅ 已达成。

按修正后的跨平台模型（architecture.md §1.1）：全平台统一部署 windows 版
named jar，NanoForge 不做任何 obf jar 对位/remap。

- [ ] natives 装配：lwjgl / jinput 平台库按 OS 组织 classpath（游戏字节码与 natives 正交）
- [ ] linux/macos 启动脚本：包装或取代官方 launcher 入口
- [ ] OS 条件分支审计：`System.getProperty("com.fs.starfarer.settings.{linux,osx}")` 等平台分支在统一 windows jar 下的行为确认，必要时以 Patch 修正
- [ ] deobf 全量模式（named jar 直接入 classpath）接管自 SSOptimizer

**不做**（原 R3 错误前提下的条目，已删除）：对任意平台游戏 jar 做
obf→intermediary 指纹对位；非 windows 平台运行时 obf→named 解析与缓存；
汉化新增类的对位失败兜底。

## R4 启动器与 SSOptimizer 收编 ⬜

- [ ] 专用启动器（modified game 启动）取代 RFB tweaker 临时路径
- [ ] SSOptimizer 退化为 NanoForge coremod（优化处理器 + deobf 运行时迁入）
- [ ] 游戏内全量冒烟（复用 SSOptimizer smoke test 三模式）
- [ ] 首个真实 bin patch 游戏内生效验证（R2 移交，随启动器环境一并做）

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
