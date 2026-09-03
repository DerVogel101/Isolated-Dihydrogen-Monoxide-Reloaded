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

Hanging pointed dripstone supplied by exactly eight finite-water units above its support block
produces one finite-water unit per successful drip, without consuming the source. Drips create
puddles in air above the first obstruction or add to an existing partial puddle (up to eight).
The vanilla drip-path limits apply; cauldrons and other fluids are not converted.
`dripstone.enabled` disables this feature. `dripstone.fill_chance` controls the chance per random
tick, defaulting to vanilla water-cauldron rarity (`0.17578125`); `0` stops accumulation.

Farmland crops holding finite-water level **1 or 2** can gain one growth stage by consuming exactly
one water level. This includes wheat, carrots, potatoes, beetroot, torchflowers, pitcher crops,
and immature melon/pumpkin stems; mature stems do not produce bonus fruit. Normal growth is free.
Normal light and growth-space requirements still apply, and blocked growth consumes no water.
`crop_fertilization.enabled` toggles the feature. `crop_fertilization.growth_speed_increase`
ranges from `0.0` to `1.0` and defaults to `0.5`: **50% more growth stages per unit time**, or
about 33% less time to maturity, while levels 1-2 are continually maintained. Consumed or drained
water must be replenished to maintain that average. Setting the increase to `0.0` disables bonuses.
The chance follows each crop's normal growth probability, including soil conditions and slower
beetroot/torchflower ticks. Dry crops, levels 3-8, and vanilla fluids receive no bonus.

The isolated dedicated-server regression is available with
`./gradlew.bat -g .gradle/codex-gradle-9.5.1 -PcropTest runServer --args="--nogui" --no-daemon`
(server directory: `build/crop-test-server`, with its own EULA/server settings).

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
