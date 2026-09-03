# Dihydrogen-Monoxide-Reloaded

https://www.curseforge.com/minecraft/mc-mods/dihydrogen-monoxide-reloaded

This branch targets Minecraft/Fabric 26.2 and Java 25. Finite-water physics use the mod-owned
`immersivefluids:finite_water`; vanilla water and lava retain their normal behavior.

```powershell
.\gradlew.bat -g .gradle\codex-gradle-9.5.1 clean build --no-daemon
```

## Configuration

Global physics values are stored in `config/immersivefluids.server.toml` and can also be edited
through Configured. They cover flow timing, normal and extended drain-search limits, extinguishing,
door pressure, entity-current strength and speed limits, waterlogging exclusions, and piston pressure.

`waterlogging.excluded_blocks` accepts block IDs that already support finite water, for example
`["minecraft:oak_slab"]`. It cannot add the `finite_water_level` property to otherwise unsupported
blocks because block states are created before the server config is loaded.

Datapacks can replace or extend these block tags:

| Tag | Default membership | Purpose |
| --- | --- | --- |
| `immersivefluids:extended_drain_path` | Empty; additive to the Configured list | Adds blocks that may extend a drain search beyond `flow.puddle_search_radius`. `flow.extended_drain_path_blocks` defaults to `#minecraft:slabs` and `#minecraft:stairs`. |
| `immersivefluids:ignores_own_shape_for_outflow` | Empty | Completely ignores a tagged block's own horizontal outflow shape. Extended-path slabs and stairs instead retain fully closed faces. |
| `immersivefluids:water_pressure_openable_doors` | `#minecraft:wooden_doors` | Selects doors water may push open from outside to inside. |
| `immersivefluids:finite_waterlogging_excluded` | `#minecraft:walls` | Disables finite-water storage on otherwise supported blocks. |
| `immersivefluids:finite_water_extinguishable` | campfires, candles, candle cakes | Selects lit, supported blocks extinguished at the configured level. |

Adjacent extended-path blocks ignore the destination entry barrier when it is equal to or lower than
the source-side exit height, including slab-to-stair transitions, while fully closed faces still block flow. Bottom slabs and
bottom-half stairs display stored levels `1-8` as
`4, 4, 5, 5, 6, 6, 7, 8`; this affects appearance only, not finite-water volume.

The maximum finite-water level remains fixed at eight because it is part of the saved block and
fluid-state formats. Searches never cross unloaded chunks.
