# Pumped Up Isolated Water

Pumped Up Isolated Water adds the mod-owned `pumpedupwater:finite_water` fluid to Minecraft. It flows in measurable units, interacts with blocks and machinery, and stays independent of vanilla water and lava.

## Requirements

| Minecraft | Mod loader                    | Java        | Required library              |
|-----------|-------------------------------|-------------|-------------------------------|
| 26.3      | Fabric Loader 0.19.5 or newer | 25 or newer | Fabric API for Minecraft 26.3 |

The current build uses Fabric API 0.161.0+26.3. Install the mod on the client and server for multiplayer.

## Compatibility

- Vanilla water and lava keep their normal behavior and are never converted into finite water.
- When finite water directly touches any other non-empty fluid, including fluids from other mods, the finite water disappears and the neighboring fluid remains.
- The optional shader integration targets Iris 1.11.6 with Sodium 0.9.2 or 0.9.3-alpha.1 using Complementary Unbound. See the [shader compatibility guide](docs/shader-compatibility.md) for setup and details.

## What the mod adds

- Isolated finite water with eight volume levels, precision buckets, waterlogging, flow interactions, and finite ice.
- Water pumps and animated valves, plus water movement caused by piston pressure.
- Rain collection and evaporation, a rain/water sensor, and an anti-rain generator.
- Dripstone water collection and water-powered crop growth.

## Documentation

- [Full feature list](docs/FEATURES.md)
- [Configuration guide](docs/configuration.md)
- [Water pump guide](docs/water-pump.md)
- [Shader compatibility and setup](docs/shader-compatibility.md)
- [Build and regression checks](docs/development.md)
- [Planned changes](docs/future_changes.md)

This mod builds on [Dihydrogen Monoxide Reloaded](https://github.com/CoolMineman/Dihydrogen-Monoxide-Reloaded) and later work by SirWashington, Ewoudje, and Ruby. Contributors are credited in `fabric.mod.json`. The code is distributed under [LGPL-3.0](LICENSE).
