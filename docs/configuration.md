# Configuration

Pumped Up Isolated Water stores its settings under `config/pumpedupwater/`.

- `server.toml` controls server gameplay settings such as flow, rainfall, extinguishing, door pressure, and entity currents. Restart the server after changing these values.
- `waterlogging.toml` controls which blocks receive finite-water and frozen states. It is read during startup, before block states are built, so changes require a full game or server restart.

Waterlogging rules must match on the client and server. Removing support for a block that already contains finite water or frozen water can discard that stored state. Older files under `config/immersivefluids` are not migrated automatically; copy settings you want to keep into the new files.

## Dripstone and crops

The `[dripstone]` section has an `enabled` switch and a `fill_chance` from `0.0` to `1.0`. The default chance is `0.17578125`; setting it to `0` stops accumulation. A full eight-unit finite-water source above the dripstone's support can provide one unit per successful drip without being consumed.

The `[crop_fertilization]` section has an `enabled` switch and a `growth_speed_increase` from `0.0` to `1.0`, defaulting to `0.5`. Crops with finite-water level 1 or 2 can consume one water unit for a bonus growth stage. A value of `0` disables the bonus.

## Waterlogging rules

The default `waterlogging.toml` values are:

```toml
excluded_blocks = ["minecraft:barrier", "minecraft:beacon", "#minecraft:leaves", "#minecraft:shulker_boxes", "#minecraft:walls", "#c:glass_panes"]
included_blocks = []
debug = false
debug_state_threshold = 6480
```

Selectors accept exact block IDs, `@modid` namespaces, `#namespace:tag` selectors, and `*` wildcards. Inclusions take precedence over these configured exclusions, but only for blocks the mod supports. The `pumpedupwater:finite_waterlogging_excluded` datapack tag can still exclude a block.

Set `debug = true` to log blocks whose state count exceeds `debug_state_threshold` during startup. Logs include the block ID and state count.

These rules use the base resources of the game and installed mods when the game starts. World datapacks and runtime-generated tags do not change the selection. Keep the same rules on both sides and restart after edits.

## Datapack tags

| Tag                                           | Default                              | Purpose                                                   |
|-----------------------------------------------|--------------------------------------|-----------------------------------------------------------|
| `pumpedupwater:extended_drain_path`           | Empty                                | Adds blocks that may extend puddle drain searches.        |
| `pumpedupwater:ignores_own_shape_for_outflow` | Empty                                | Ignores a tagged block's own horizontal outflow shape.    |
| `pumpedupwater:water_pressure_openable_doors` | `#minecraft:wooden_doors`            | Selects doors finite-water pressure can open.             |
| `pumpedupwater:finite_waterlogging_excluded`  | Empty                                | Excludes blocks from finite-water storage.                |
| `pumpedupwater:finite_water_extinguishable`   | Campfires, candles, and candle cakes | Selects supported lit blocks finite water can extinguish. |

The `flow.extended_drain_path_blocks` server setting defaults to `#minecraft:slabs` and `#minecraft:stairs`. See the [feature list](FEATURES.md) for the behavior of the other physics settings.
