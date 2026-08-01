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
```

加载顺序由依赖拓扑排序决定：depends 指向的 coremod 先加载；同层按 priority 升序，
再按 id 字典序，保证确定性。依赖缺失、依赖环、重复 id 均会在启动时给出明确错误。

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

> 旧机制（Java SPI 发现、`@NanoCorePluginInfo` 注解、`IMixinLoader` 接口、
> `injectData(Map)`）已全部移除，coremod.toml 为唯一入口。
