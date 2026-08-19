# NanoForge 项目规范

## 类变换通道：禁止 javaagent 模式

1. **禁止以 javaagent（`java.lang.instrument` / `-javaagent:`）作为类变换通道**，
   包括 Premain-Class manifest、Instrumentation.addTransformer、以及任何形式的
   「全类加载器兜底改写」设施。
2. 一切字节码改写必须走 RFB/LaunchWrapper transformer 链 + Mixin：
   - System 域 / Launch 域类：Launch transformer 链（`IClassTransformer` 注册）；
   - 游戏行为改写：Mixin 优先（见 SSOptimizer AGENTS.md 的 Mixin/ASM 规范）。
3. 模组自建类加载器（如 aitweaks 的 Kotlin 引导 loader、shipmastery 的
   ReflectionEnabledClassLoader）漏 transform 的问题，**用针对性适配解决**：
   对该模组的 loader 类写专用 ASM processor，把其 defineClass 挂进 Launch
   transformer 链；不得用 agent 做通用兜底。
4. 设计动机：agent 通道会让 System 域类（如 system classpath 上的 lwjgl）被改写出
   对 Launch 域类的引用，定义方加载器不可见即 NoClassDefFoundError；且「全覆盖兜底」
   与 NanoForge 的显式域模型（RFB 模块系统 + Launch 链）冲突，问题在启动期不可见、
   运行期才爆。针对性适配的改写范围显式可控、失败在启动期可见。
