# OS 条件分支审计（R3）

> 审计对象：windows 版 named jar 反编译源码（SourceSector 产出，0.98a-RC8）。
> 背景：跨平台模型修正后全平台统一部署 windows 版 named jar
> （architecture.md §1.1），需确认游戏内平台分支在该模型下的行为。

## 结论

平台分支面极小，linux 部署设 `-Dcom.fs.starfarer.settings.linux=true`
即全覆盖，**无需任何 Patch**。

## 分支开关

`com.fs.starfarer.settings.StarfarerSettings` 的两个静态开关：

- `osx`：由 `-Dcom.fs.starfarer.settings.osx` 系统属性驱动
- `linux`：由 `-Dcom.fs.starfarer.settings.linux` 系统属性驱动

默认（无属性）= windows 行为。

## 行为分支点（实查，仅 3 处）

| 位置 | 行为 | linux 部署影响 |
|---|---|---|
| `MemoryDiagnostics:58` | 内存诊断信息获取方式的 OS 适配 | 设 linux 属性后走 linux 路径，正常 |
| `GLSettings:233`、`GLSettings:544` | GL 相关设置的 OS 适配 | 同上 |
| `StarfarerLauncher:96` | 仅日志输出内容差异 | 无功能影响 |

另有 `isWindows32Bit()`（按 `os.name` 检测）2 处，与上述开关无关，
在 64 位 linux 下自然为 false，行为正确。

## 部署约束

- linux：`launch_nanoforge_ss.sh` 已带 `-Dcom.fs.starfarer.settings.linux=true`。
- macOS：`launch_nanoforge_ss.command` 对应 `-Dcom.fs.starfarer.settings.osx=true`
  （脚本已写，未实机验证）。
- windows：不设属性即可。
