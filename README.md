# Dihydrogen-Monoxide-Reloaded

https://www.curseforge.com/minecraft/mc-mods/dihydrogen-monoxide-reloaded

This branch targets Minecraft/Fabric 26.2 and Java 25. Finite-water physics use the mod-owned
`immersivefluids:finite_water`; vanilla water and lava retain their normal behavior.

```powershell
.\gradlew.bat -g .gradle\codex-gradle-9.5.1 clean build --no-daemon
```

## Water valve

Place `immersivefluids:water_valve` blocks facing the same direction in a 1x1, 2x2, or 3x3 square.
All six directions are supported. Redstone at any member opens the whole valve; removing power closes it.
A full transition takes 140 ticks: 60 to swing the leaves, 60 to extend the telescoping plates,
and 20 to extend the short top/bottom bolts from their drive bases. Opening reverses this order.
The water seal closes at the outlet face when the plates meet, before the final locking phase.
Both faces have visible actuators and locking hardware; 3x3 leaves have three actuator rows. Open 2x2/3x3 valves admit a walking player;
the 1x1 admits a crawling or swimming player.

The shaped recipe yields two valves: iron blocks in the corners, iron doors at the top/bottom center,
sticky pistons at the left/right center, and a waxed lightning rod in the center.
Run `./gradlew.bat -PvalveTest runServer --args="--nogui" --no-daemon` for the isolated server regression.
`valveGeometrySelfTest` also produces a headless preview in `build/valve-geometry-preview.png`.

## Finite ice

Finite water freezes outdoors in cold biomes during Minecraft's regular ice/snow checks,
at block light below 10. Levels 1-7 become layered finite ice; level 8 becomes full finite ice.
Layered ice can hold liquid finite water in its remaining space (ice + water <= 8).
Further freezing converts the liquid into more ice, producing a full block at eight layers.
Precision buckets retain any water that does not fit. Full buckets require room for overflow.

In survival, full finite ice drops itself with Silk Touch; otherwise it releases eight finite-water
units. Layered ice drops nothing, even with Silk Touch, and releases its frozen plus liquid units.
Both variants melt back into their conserved water volume under block light above 11.
Finite-waterlogged blocks freeze in place, keeping their block state and block entity (including inventories).
Partially frozen hosts can hold more liquid finite water up to eight total units (ice + liquid).
Snow layers share that space: three snow layers leave room for five water levels, and eight leave none.
Water freezes above the snow, preserving its layer count; snow, ice, and liquid together never exceed eight levels.
Snow stacking is rejected when it would overfill the block. Removing or melting wet snow retains its finite water.
Buckets and natural flow can fill that remaining space; refreezing and thawing conserve both portions.
The ice prevents use, hopper access, and normal ticking. Paired chests/doors/beds are locked together.
Frozen waterlogged blocks have solid ice collision and vanilla ice slipperiness, including thin layers around plants.
Breaking the ice thaws the host without harvesting it or dropping its contents, including with Silk Touch.
Strong block light (above 11) also thaws it; the stored finite-water amount is restored.
Vanilla water and vanilla-waterlogged blocks retain their existing behavior.
The isolated regression is `./gradlew.bat -g .gradle/codex-gradle-9.5.1 -PiceTest runServer --args="--nogui" --no-daemon`.

Manual client check: freeze a chest containing items, a potted plant, and a slab in an enclosed,
outdoor finite-water basin in a cold biome. Check the ice overlay/collision, blocked chest access
(including the other half of a double chest), then break or melt the ice with nearby glowstone.
The original blocks and chest contents must remain, with no duplicated drops.
Also top up partially frozen plants and layered ice: the ice should stay visible beneath the water,
and the ice around offset plants should align with the block grid, not the plant's random offset.
For snow, try three layers with five water levels, then freeze and break/thaw the ice: all three snow layers
and five water levels should remain. Check that water and ice render above the snow, not through it.

## Configuration

### Shaders

The optional client integration targets Iris 1.11.2 + Sodium 0.9.1 on Minecraft 26.2,
with Complementary Unbound r5.9 and Euphoria Patches 1.10.0. Select **RP Support → Integrated PBR+**.
No extra resource pack or shader-file edits are needed. Other Iris/Sodium versions or changed
shader material sources disable this version-sensitive integration and log the reason.
See [shader compatibility and validation](docs/shader-compatibility.md) for coverage and test commands.

### Physics

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
