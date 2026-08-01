# Sanitize 必要性核查（R4 阶段 0）

> 核查对象：SourceSector 产出的 windows 版 named jar 全量 4 个
> （starfarer_obf / fs.common_obf / fs.sound_obf / starfarer.api，6379 类）。
> 背景：SSOptimizer javaagent 时代有 `SanitizingTransformer`（非法标识符净化）
> 与 `ReflectionSanitizingTransformer`（反射调用名翻译）两个运行时变换器，
> R4 coremod 化前需确认 NanoForge 是否需要移植。

## 结论：两者均不移植

### 非法标识符残留：仅 1 个类的 5 个字段

全量扫描结果，非法 Java 标识符只剩
`com.fs.starfarer.loading.specs.EngineSlot` 的 5 个字段：

| 字段名（字面） | 类型 |
|---|---|
| `this.super` | `Ljava/lang/String;` |
| `Object.super` | `Ljava/awt/Color;` |
| `for.super` | `Z` |
| `return.super` | `F` |
| `int.super` | `F` |

成因：EngineSlot 大部分成员本就是游戏未混淆的真实名，被登记进
`ssoptimizer-identity.tiny` 保持原名（app 编译期直接引用），这 5 个被
混淆器改成「关键字.super」 trick 的字段随之原样保留。

影响面核查（均为零）：

- 4 个游戏 jar 内对这 5 个名字的字符串常量引用：**0**（排除
  reflection-by-name 破坏风险）；
- SSOptimizer app 源码/ASM 引用：**0**（`EngineSlotAccessor` 只 Invoker 方法）；
- JVM 直接字节码访问合法，游戏运行不受影响（R3 冒烟已证）。

唯一受限场景：Java 源码级引用（javac/janino/mixin @Shadow）无法写出这种
名字。当前无此需求。

**修复路径（列入 SourceSector 改进项，不占 R4）**：在人工映射表为这 5 个
字段登记合法 named 名即可从源头消除，无需运行时净化层。

### 反射调用点：游戏 jar 内为 0

`Class.getMethod / getDeclaredMethod / getField / getDeclaredField`
调用点在 4 个游戏 jar 中**一个都没有**。`ReflectionSanitizingTransformer`
的改写对象不存在，不移植。mod 侧理论上可能按名反射游戏内部成员，
但无实际证据，出现时再议。
