# NanoForge 实现计划与进度跟踪

> 版本：v1（2026-08-01） 上游设计：docs/design/architecture.md

## 轮次总览

| 轮次 | 目标 | 状态 |
|---|---|---|
| R1 | CoreMod 骨架完整落地 | ✅ 已完成（分支 feat/coremod-skeleton，commit 03b56ce） |
| R2 | Patch 开发工作流（bin patch） | ⬜ 下一轮（前置已达成：SourceSector named 基线已产出） |
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

## R2 Patch 开发工作流 ⬜（下一轮）

**前置**：SourceSector 产出 named jar + sources（开发基线）✅ 已达成
（`:mapping:publishNamedGameJars` → `build/named-game-repo/windows`）。

- [ ] 开发侧：基于 named 源码直接修改游戏类 → 编译 → 与原 named 类 diff → 生成类级 bin patch（Badiff），patch 内含原类 SHA-256 基线
- [ ] 运行时：`PatcherManager`/`NanoPatcherTransformer` 落地，patch 在 transformer 链最前应用（先于 coremod ASM/Mixin）
- [ ] 基线校验失败显式报错（游戏更新后 patch 失效可追），不做静默跳过
- [ ] coremod.toml 扩展 `[patch]` 数据表
- [ ] 端到端：一个真实 patch 在游戏内生效的冒烟验证

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
