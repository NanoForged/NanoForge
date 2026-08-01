# NanoForge 实现计划与进度跟踪

> 版本：v1（2026-08-01） 上游设计：docs/design/architecture.md

## 轮次总览

| 轮次 | 目标 | 状态 |
|---|---|---|
| R1 | CoreMod 骨架完整落地 | ✅ 已完成（分支 feat/coremod-skeleton，commit 03b56ce） |
| R2 | Patch 开发工作流（bin patch） | ⬜ 未开始（前置：SourceSector named 基线） |
| R3 | runtime remap + 跨平台指纹对位 | ⬜ 未开始（前置：SourceSector 单 mapping） |
| R4 | 专用启动器对接 / SSOptimizer coremod 化 | ⬜ 未开始（前置：R2/R3 + SSOptimizer 侧拆分） |

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

## R2 Patch 开发工作流 ⬜

**前置**：SourceSector 产出 named jar + sources（开发基线）。

- [ ] 开发侧：基于 named 源码直接修改游戏类 → 编译 → 与原 named 类 diff → 生成类级 bin patch（Badiff），patch 内含原类 SHA-256 基线
- [ ] 运行时：`PatcherManager`/`NanoPatcherTransformer` 落地，patch 在 transformer 链最前应用（先于 coremod ASM/Mixin）
- [ ] 基线校验失败显式报错（游戏更新后 patch 失效可追），不做静默跳过
- [ ] coremod.toml 扩展 `[patch]` 数据表
- [ ] 端到端：一个真实 patch 在游戏内生效的冒烟验证

## R3 runtime remap + 跨平台对位 ⬜

**前置**：SourceSector 单 mapping（windows 基准，obf/intermediary/named 三列）。

- [ ] 移植 SSOptimizer 结构指纹实现对任意平台游戏 jar 做 obf→intermediary 对位
- [ ] 非 windows 平台运行时解析 obf→named，按 `jar SHA-256 + mapping 版本` 落盘缓存
- [ ] 对位失败（汉化新增类/结构漂移）保持 obf 名 + 显式 WARN
- [ ] deobf 全量模式（named jar 直接入 classpath）接管自 SSOptimizer

## R4 启动器与 SSOptimizer 收编 ⬜

- [ ] 专用启动器（modified game 启动）取代 RFB tweaker 临时路径
- [ ] SSOptimizer 退化为 NanoForge coremod（优化处理器 + deobf 运行时迁入）
- [ ] 游戏内全量冒烟（复用 SSOptimizer smoke test 三模式）

---

## 变更记录

- 2026-08-01：文档建立；R1 完成并提交（feat/coremod-skeleton）。
