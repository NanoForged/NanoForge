# NanoForge
<div  align="middle" >
<img src="assets/nanoforge.png" width="192">

<b>Next-Generation Modding Framework for Starsector
</div>

## Introduction

NanoForge 是面向 Starsector 的 CoreMod 加载框架：基于 RFB/LaunchWrapper 注入，
为 coremod 提供统一的 ASM transformer / Mixin 装配管线与依赖管理。

## Usage
### Framework
copy game jar to `lib/gameJar`\
copy `graphics` `data` `sounds`  dir to `assets`

run  `runVanilla` task\
or `runLanchWrapper` task

### CoreMod

CoreMod 是一个内含 `coremod.toml` 元数据的 jar，放置于 `mods/coremods/` 目录
（该目录只放 coremod；不含 coremod.toml 的 jar 会被跳过并记录 WARN）。

`coremod.toml` 是 coremod 的唯一元数据来源：

```toml
id = "mymod"                            # 必填，全局唯一
name = "My Mod"                         # 必填
version = "1.0.0"                       # 必填
authors = ["someone"]                   # 可选
description = "..."                     # 可选
priority = 0                            # 可选，默认 0，升序先加载
depends = ["othermod"]                  # 可选，硬依赖：缺失即启动失败，并约束加载顺序
pluginClass = "com.example.MyPlugin"    # 必填，INanoCorePlugin 实现类（公开无参构造）

[asm]
transformers = ["com.example.MyTransformer"]      # 可选，IClassTransformer 类名
transformerExclusions = ["com.example.internal"]  # 可选

[mixin]
configs = ["mymod.mixins.json"]                   # 可选

[patch]
entries = ["patches/demo_MyClass.binpatch"]       # 可选，jar 内 bin patch 文件清单
```

加载顺序由依赖拓扑排序决定：depends 指向的 coremod 先加载；同层按 priority 升序，
再按 id 字典序，保证确定性。依赖缺失、依赖环、重复 id 均会在启动时给出明确错误。

coremod 的外部文件（native 库、缓存、配置等）按约定放在伴生目录
`mods/<coremod-id>/`，经 `CoreModContext.modsPath().resolve(meta.id())` 推导；
`CoreModContext` 同时提供 `gameHome`（游戏安装目录）等路径。

入口插件只保留一个生命周期钩子：

```java
public class MyPlugin implements INanoCorePlugin {
    @Override
    public void onLoad(CoreModContext context) {
        // 装配（transformer 注册、Mixin config 登记）完成后按依赖序回调
        // 此时禁止触碰任何游戏类；context 提供 meta/各游戏路径/按 id 命名的 logger
    }
}
```

ASM transformer 与 Mixin config 在 toml 数据表中声明即可，无需在插件代码里返回。

> **RFB 契约警告**：RFB 的 `runTransformers` 无条件采纳 transformer 返回值
> （`basicClass = newKlass`），返回 `null` 会把类字节**直接丢弃**，类加载以
> "Class bytes are null" 失败——这与原版 LaunchWrapper「null = 无变更」的
> 契约不同。自定义 `IClassTransformer` 在未命中/不修改时必须返回原
> `basicClass`，不得返回 `null`。

### Patch（bin patch）

Patch 是比 ASM/Mixin 更底层的修改方式：直接修改 named 游戏源码、编译后与
原 named jar 逐类对比生成类级二进制 diff（`.binpatch`），运行时在游戏类
加载前整类替换。适合大范围、结构性、用 ASM 难以表达的修改。

生成（开发侧，本仓库 Gradle 任务）：

```bash
./gradlew generatePatches \
  -Ppatch.original=<原named.jar> \
  -Ppatch.patched=<修改后class目录> \
  -Ppatch.output=<输出目录>
```

- 对比以 `ClassReader` 读取的类内部名为准；原 jar 中不存在的类视为新增类
  （随 coremod 常规类分发，不生成 patch）；字节一致的类跳过。
- 每个 patch 写出前用 badiff 回验（原类 + diff == 修改后类），回验失败即生成失败。
- 输出为 `<输出目录>/<类内部名>.binpatch`，按类名排序，输出确定。

运行时（coremod 侧）：把 `.binpatch` 打进 coremod jar，并在 `[patch] entries`
中逐项声明。patch 在 transformer 链最前应用（先于 ASM/Mixin），目标命名空间为
named，仅 deobf 运行时生效。应用前校验原类字节的 SHA-256 基线，游戏更新导致
基线不符时**显式抛错**（指明类名、期望/实际哈希与来源 coremod），不静默跳过；
同一个类被两个 coremod patch 也会在装配期直接报错。

### 部署与启动（R3）

部署到游戏目录（产出 NanoForge jar、运行时依赖、全量 remap 表、
unsealed lwjgl 与 `runtime/NanoForge-mixins.jar` 到 `<game>/mods/nanoforge/`）：

```bash
./gradlew deployToGame -Pgame.dir=<游戏目录>
```

辅助任务（均不挂进 `build`，CI 无本地资产）：

- `packFullMapping`：gzip SourceSector 全量表 → `build/nanoforge/game-full.tiny.gz`
- `extractGameNatives`：从游戏 vendor natives jar 提取到 `lib/native/{linux,macos,windows}`
- `unsealLwjgl`：去 lwjgl.jar 密封属性 → `build/nanoforge/lwjgl-unsealed.jar`
- `generatePatches`：见上文 Patch 节

启动（临时路径，专用启动器见 R4）：游戏目录下 `launch_nanoforge_ss.sh`
（linux，实机验证）/ `launch_nanoforge_ss.command`（macOS，未验证）。
关键 JVM 参数：

- `--tweakClass io.github.nanoforged.NanoForgeBootstrap`
  + `-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader`
- `-Dnanoforge.remap.obf2named=false` 关闭 obf→named 全量 remap（**默认开启**，
  仅显式 `false` 用于 obf 运行时对比调试；表路径可用 `-Dnanoforge.remap.mapping=` 覆盖）
- linux 需 `-Dcom.fs.starfarer.settings.linux=true`（OS 分支审计见
  `design/os-branch-audit.md`）
- classpath 中 `mods/nanoforge/*.jar` 置前；排除游戏自带 log4j-1.2.9.jar
  与原 lwjgl.jar（由 lwjgl-unsealed.jar 顶替）

> 旧机制（Java SPI 发现、`@NanoCorePluginInfo` 注解、`IMixinLoader` 接口、
> `injectData(Map)`）已全部移除，coremod.toml 为唯一入口。
