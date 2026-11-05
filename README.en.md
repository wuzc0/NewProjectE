# ProjectE — Fabric 26.3 Port

[中文](README.md) | **English**

**Current version: `1.0.2-release`**

## Requirements

| Dependency | Requirement |
| --- | --- |
| Minecraft Java Edition | 26.3 |
| Mod loader | Fabric Loader 0.19.5 or a newer compatible version |
| Fabric API | 0.163.0+26.3 or a newer compatible version |
| Java | 25 or newer |

Not for Forge or NeoForge. Multiplayer clients and servers must install the same version.

## Controls and Commands

Defaults inherited from the supplied port can be changed in game settings:

| Action | Default key |
| --- | --- |
| Charge / discharge | `V` / `Shift + V` |
| Change mode | `G` |
| Extra function, including Katar area attacks | `C` |
| Fire projectile | `R` |

Charge the Katar and wait for its attack cooldown before using the extra-function key. Selecting a mode does not automatically attack nearby mobs. After inserting a functional item into a Pedestal, empty-hand right-click toggles activation.

Players with the appropriate permissions can use:

```text
/projecte setemc 12345 minecraft:iron_sword
/projecte reloademc
/projecte resetemc minecraft:iron_sword
/projecte removeemc minecraft:iron_sword
/projecte densitydebug
```

- `setemc` assigns a custom value; omit the item argument to use the held item. Run `reloademc` afterward.
- `resetemc` removes the custom override and restores default pricing. `removeemc` removes the item's EMC instead of restoring its default. Reload EMC after either command.
- Vanilla `/reload` does not replace `/projecte reloademc`.
- `densitydebug` reads the player inventory, or the contents of an Alchemical Chest when looking at it. It reports activation, target value, buffer, and filtering reasons in chat and the log, without changing items or balances.


