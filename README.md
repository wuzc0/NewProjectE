# ProjectE — Fabric 26.3 移植版

**中文** | [English](README.en.md)

**当前版本：`1.0.2-release`**

## 运行环境

| 依赖 | 要求 |
| --- | --- |
| Minecraft Java Edition | 26.3 |
| 模组加载器 | Fabric Loader 0.19.5 或更新的兼容版本 |
| Fabric API | 0.163.0+26.3 或更新的兼容版本 |
| Java | 25 或更高版本 |

不适用于 Forge / NeoForge。联机时，客户端与服务端均需安装相同版本。


## 操作与指令

默认键位沿用基础附件，可在游戏设置中修改：

| 操作 | 默认按键 |
| --- | --- |
| 充能 / 降低充能 | `V` / `Shift + V` |
| 切换模式 | `G` |
| 额外功能（例如拳剑范围攻击） | `C` |
| 发射投射物 | `R` |

拳剑需先充能并等待攻击冷却，切换模式不会自动杀怪。基座放入功能物品后，空手右键切换激活状态。

有相应权限的玩家可使用：

```text
/projecte setemc 12345 minecraft:iron_sword
/projecte reloademc
/projecte resetemc minecraft:iron_sword
/projecte removeemc minecraft:iron_sword
/projecte densitydebug
```

- `setemc` 设置自定义值；省略物品参数时使用手持物品。随后执行 `reloademc` 生效。
- `resetemc` 删除自定义覆盖、恢复默认；`removeemc` 是移除该物品的 EMC，而不是恢复默认。两者均需随后重载 EMC。
- 原版 `/reload` 不替代 `/projecte reloademc`。
- `densitydebug` 只读检查玩家背包；对准炼金术箱时检查箱内宝石/指环。显示启用状态、目标价格、缓存和过滤原因，同时写入日志，不修改物品或余额。


