# ProjectE — Fabric 26.3 Port

[中文](README.md) | **English**

**Current version: `1.0.2-release`**

An unofficial port of the supplied **Minecraft 26.1.2 Fabric** ProjectE binary to **Minecraft 26.3 Fabric**, with fixes for EMC accounting, automation, item interactions, and compatibility.

This is an **unofficial port and repair project**, not an official upstream ProjectE release. This release retains the previous fixes and changes the version label to `1.0.2-release`.


## Requirements

| Dependency | Requirement |
| --- | --- |
| Minecraft Java Edition | 26.3 |
| Mod loader | Fabric Loader 0.19.5 or a newer compatible version |
| Fabric API | 0.163.0+26.3 or a newer compatible version |
| Java | 25 or newer |

Not for Forge or NeoForge. Multiplayer clients and servers must install the same version.

## Installation and Upgrading

1. Back up your world and `config/projecte/`.
2. Install the required Fabric environment and Fabric API.
3. Remove the older ProjectE JAR from `mods`, then add `projecte-1.0.2-release-fabric-26.3.jar`. Do not install multiple versions together.
4. Fully restart the game and server; `/reload` alone is insufficient.

The `projecte` mod ID and existing item/block IDs are retained. There is no need to erase worlds, learned items, EMC configuration, or player balances. Previously lost EMC or consumed items are not reimbursed retroactively.

## Main Fixes and Features

- **Transmutation accounting:** Shift-click charges only for items actually inserted into the inventory. A full inventory is not charged; partial space incurs a proportional charge.
- **Automation:** Energy Collectors can supply adjacent Energy Condensers. Both Condenser tiers support hopper input, output extraction, transactional rollback, and target-icon synchronization.
- **Block drops:** Added loot tables for 22 mod blocks and appropriate mining tags. Breaking a Condenser drops its inventory; tool and game-mode requirements still apply.
- **Dark Matter Pedestal:** Empty-hand right-click toggles activation. Watch of Flowing Time uses loaded block-entity tickers while respecting configuration and blacklists.
- **Gem Helmet:** Grants 20 seconds of night vision and refreshes every tick—0.05 seconds at the normal 20 TPS. Longer or infinite existing effects are not shortened.
- **Red Matter tools:** Added the missing armor-bypassing damage type used by Katar attacks and repaired the Morning Star's tall/wide/deep three-block mining modes. These modes are not changed into 3×3 mining.
- **Crafting catalysts:** The Philosopher's Stone and Zero Ring are returned after crafting, retaining charge, stored EMC, custom names, and other components.
- **Gem of Eternal Density and Void Ring:** Convert permitted materials inside player inventories and Alchemical Chests, including materials with equal or higher unit EMC than the selected target. Remainders stay in the EMC buffer. Target outputs and the converters themselves are protected against recycling loops.
- **Inventory and components:** Fixed several causes of charged items refusing movement or dropping, snapping back after transfer, and component-bearing materials incorrectly reporting zero EMC.
- **EMC commands:** Fixed persistence, reloads, rounding overrides, and stale caches. Added a read-only conversion diagnostic command.

## EMC Rules

### Items Without EMC

The following items **have no EMC** and are not consumed as EMC input by Condensers, Gems of Eternal Density, or Void Rings. Whitelists and custom EMC assignments cannot override these rules:

- Items with actual enchantments, including curses and enchanted books with stored enchantments.
- Items with durability damage: any damage value greater than zero, even a single point.

Items are not deleted, enchantments and durability are not altered, and existing player balances are not deducted. Normal pricing returns after full repair and removal of all actual enchantments. Empty enchantment components and purely visual glint do not count as enchantments.

### Default Values

Except for the following scoped changes, the base port's default mapping and rounding behavior is preserved; recipes are not globally repriced:

| Item / rule | Default EMC |
| --- | --- |
| Iron Ingot / Iron Block | 256 / 2304 |
| Gold Ingot / Gold Block | 2048 / 18432 |
| Ancient Debris / Netherite Scrap | 8192 / 8192 |
| Netherite Ingot / Netherite Block | 40960 / 368640 |

- Single-material compressed blocks are corrected only when an exact reverse recipe exists, such as 9 Iron Ingots ↔ 1 Iron Block. **Nugget ↔ ingot ratios are not adjusted.**
- Vanilla Netherite smithing equipment is priced from its base equipment, Netherite Ingot, and consumed template.
- Data packs, custom EMC, and mapping configuration can change actual results. The no-EMC rules take precedence.

At the listed defaults, one Iron Block can produce nine Iron Ingots, and one Diamond can produce four Gold Ingots. Targets must have EMC, input must satisfy the blacklist/whitelist, and unstackable input still requires explicit whitelisting.

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

Ordinary chests store items but do not execute ring effects. Alchemical Chests support hoppers. Enabled functional items are protected from automatic extraction; disabling them permits extraction, while manual removal remains available.

## Source and Building

The slim GitHub source package contains only **production Java overrides, required build scripts, bilingual documentation, and the specified input JAR**. It is not the original author's complete Gradle project. The scripts target Windows and do not require Gradle.

It excludes isolated regression tests, client startup tests, test launchers, link-audit tools, development-history documentation, logs, test worlds, and downloaded dependencies. These validation tools remain in the local development environment but are not distributed in this package.

```text
src/                 Production fixes and compatibility code
PortBinary.java      Bytecode compatibility processing
original.jar         Required 26.1.2 Fabric build input
prepare.py           Download build dependencies
build.py             Compile production code
package.py           Package runtime code and generated resources
rebuild.py           Build the release JAR without running tests
README.md            Chinese documentation
README.en.md         English documentation
.gitignore           Exclude dependencies and generated files
```

Install Python 3.10+ and JDK 25. The first build downloads dependencies from Mojang, Fabric, and Maven. Run from the extracted source root:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
python rebuild.py
```

The release output is written to the current directory:

```text
projecte-1.0.2-release-fabric-26.3.jar
```

With a complete existing `deps/` cache, use `python rebuild.py --offline` to skip dependency preparation. Running `python package.py` alone only packages the mod; compatibility processing and compilation must already be complete.

`original.jar` is required to preserve inherited implementation and resources that are not overridden. It is not a test dependency. Its required SHA-256 is:

```text
855e48329f788fd7039d506620c54eec88ad8e4c3d71f209ede41b1aac4b8257
```

For GitHub, upload the files inside the extracted source ZIP root. `.gitignore` excludes local dependencies, compiled artifacts, and logs while retaining the required `original.jar`. Upload the generated release JAR separately as a GitHub Release asset.

## Validation and Bug Reports

The current code has passed **1,506 isolated regression checks** and an isolated client resource-loading/title-screen check. The test environment uses Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, and Java 25.

This is not exhaustive testing of modpacks, complete worlds, or multiplayer environments. Historical runs have encountered native client-process failures; stability across all GPUs, drivers, and third-party combinations is not guaranteed. `release` is this build's version label, not upstream certification.

When reporting a problem, include game/loader/API/Java versions, the mod list, reproduction steps, and the latest `latest.log` or crash report. For conversion problems, also include `/projecte densitydebug` output. Remove sensitive information before sharing logs.

## Credits and Provenance

- [Upstream ProjectE](https://github.com/sinkillerj/ProjectE): sinkillerj and ProjectE contributors.
- Based on the supplied 26.1.2 Fabric port, with thanks to its porting contributors.
- The original JAR's metadata declares MIT. This repair project does not add a blanket license declaration covering all inherited material. Inherited code, assets, and bundled third-party components retain their respective provenance and credits; they are not original work of this port.
