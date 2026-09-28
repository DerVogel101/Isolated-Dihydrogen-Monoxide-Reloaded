# Pumped Up Isolated Water — Current Features

This document describes the current checkout of Pumped Up Isolated Water (`pumpedupwater`).
It targets Minecraft 26.2 on Fabric and Java 25; the 26.3 port is still pending.
The Java package is `dervogel101.de.pumpedupwater`.

Saved `immersivefluids` block, item, fluid, and block-entity IDs resolve to the
new `pumpedupwater` entries when loaded. Back up an existing world before opening
it. Existing config files can be copied from `config/immersivefluids` to
`config/pumpedupwater`.

## Finite water

The mod adds an isolated finite-water fluid:

- `pumpedupwater:finite_water` is the full source fluid.
- Water is represented by discrete levels from 0 to 8. A full source contains
  8 units; flowing water contains 1–7 units.
- Finite water has no infinite-source conversion. A full source is not created
  by the normal water-source rules.
- Finite water flows downward first and equalizes horizontally. A one-unit
  puddle can search for a nearby lower outlet and move toward it.
- Flow follows collision shapes and therefore respects walls, slabs, stairs,
  trapdoors, doors, and other partial barriers. Copper grates retain their
  collision while allowing finite water through their openings.
- Extended drain paths can be configured with block IDs or block tags. The
  default extended path includes slabs and stairs. Searches are bounded and
  never cross unloaded chunks.
- Finite water disappears on direct contact with any other non-empty fluid.
  The neighboring fluid remains unchanged. Fluids in `#minecraft:lava` also
  produce an extinguishing sound and smoke effect.
- Finite water is removed at the bottom build limit rather than creating an
  endless reservoir.
- The finite-water fluid uses vanilla water visuals, particles, pickup sounds,
  and water-like entity interaction while retaining its own volume and flow
  rules.
- Successful movement produces flow sounds. Heavier movement produces denser,
  lower-pitched ambient water sounds.

Vanilla water, vanilla lava, and unrelated fluids retain their normal behavior.
The mod does not automatically convert existing vanilla water or lava into
finite water.

## Buckets and measuring water

The mod adds two items:

- **Finite Water Bucket** — a normal full bucket containing 8 finite-water
  units. It can place and pick up finite water through the normal bucket
  interaction.
- **Precision Bucket** — a reusable 0–8-unit container. Crouch-use picks up
  finite water; normal use places it. Its tooltip shows the stored amount and
  its item bar shows the fill level.

Additional bucket behavior:

- A vanilla water bucket can fill a block that supports finite waterlogging.
- A full finite-water bucket requires enough free capacity at the target. If
  necessary, the extra water is distributed to adjacent flow-compatible cells;
  the operation fails without changing anything if the volume cannot fit.
- Precision buckets keep any amount that does not fit, including when filling
  layered finite ice.

## Finite-water storage and waterlogging

Supported block states receive a `finite_water_level` property from 0 to 8.
Finite water can be stored in:

- Normal vanilla waterloggable blocks.
- Vegetation, growing plants, vines, hanging plants, bamboo, sugar cane,
  cactus, chorus plants, glow lichen, spore blossom, cocoa, dripleaf, coral,
  turtle eggs, webs, carpets, banners, candles, and similar plant/decorative
  blocks.
- Slabs, stairs, doors, trapdoors, flower pots, beds, chests, hoppers,
  brewing stands, cauldrons, and many other utility, redstone, transport, and
  container blocks.

Storage is limited by the block's available space. Double slabs cannot hold
finite water. By default, walls are excluded through the mod tag, and blocks
such as leaves, glass panes, beacons, barriers, and shulker boxes are not
supported. Additional supported blocks can be excluded in the server config.

Finite-water state is preserved when supported blocks change state, are moved
through normal growth transformations, or are replaced by a compatible block.
When a waterlogged block is broken, its stored finite water remains in the
world instead of being silently lost. Block entities and their contents are
preserved by the finite-ice freeze/thaw path.

## Flow interactions

### Extinguishing

Finite water extinguishes entities inside it. It also extinguishes lit blocks
selected by the `pumpedupwater:finite_water_extinguishable` tag. The default
selection is campfires, candles, and candle cakes. The default minimum level is
3, and the threshold is configurable.

### Sponge absorption

Vanilla sponges absorb finite water during their normal breadth-first search.
Each finite-water cell they absorb is emptied without changing vanilla sponge
behavior.

### Door pressure

Finite water can open configured doors from the outside when the required water
column is present beside both door halves. Wooden doors are selected by
default. The feature is enabled by default and requires level 8 beside each
half by default. It can be disabled or reconfigured on the server.

### Entity currents

Moving finite water records a short-lived current at the source and receiving
cells. These currents push entities that can be pushed by fluids, including
horizontal, upward, and downward movement. Current strength scales with the
amount transferred and is capped by configurable speed limits.

Defaults are:

- 10 ticks of current duration;
- horizontal strength `0.06`;
- upward strength `0.06`;
- downward strength `0.039`;
- maximum horizontal and upward speed `0.7`;
- maximum downward speed `0.3`.

Depth Strider reduces the current effect for living entities, up to the normal
enchantment level limit.

## Plants, crops, and growth

- Seagrass, tall seagrass, and kelp can be placed and survive in full finite
  water. They can also use finite water for their normal bonemeal growth
  checks.
- Mangrove roots can be placed in finite water.
- Tree growth, large mushroom growth, and Nether fungus growth preserve and
  displace finite water from affected supported blocks. Water is moved into
  nearby compatible cells where possible instead of being discarded.
- Farmland crops with finite-water level 1 or 2 can receive a bonus growth
  stage by consuming exactly one water unit. The supported crop families are
  wheat, carrots, potatoes, beetroot, torchflowers, pitcher crops, and
  immature melon and pumpkin stems.
- Normal crop growth does not consume water. Mature stems do not receive a
  bonus fruit-producing growth step.
- The bonus follows the crop's normal growth probability and still respects
  light, soil, and available-growth-space requirements. A blocked growth
  attempt does not consume water.
- Crop fertilization is enabled by default with a relative growth-speed
  increase of `0.5` (50% more growth stages per unit time). It can be disabled
  or configured from `0.0` to `1.0`.

Dry crops, finite-water levels 3–8, and vanilla water do not receive the crop
fertilization bonus.

## Finite ice and freezing

Finite water freezes during vanilla precipitation checks when it is exposed at
the motion-blocking surface, the biome is cold enough to snow, and block light
is below 10.

### Water in open space

- 1–7 water units become layered finite ice with the matching number of ice
  layers.
- 8 water units become a full Finite Ice block.
- Layered ice can hold additional liquid finite water in its remaining space;
  frozen ice plus liquid water never exceeds 8 units.
- Further freezing converts stored liquid into more ice.

### Waterlogged blocks

Finite-waterlogged blocks freeze in place and retain their original block state,
including block entities and inventories. Partial freezing can leave liquid
finite water above the frozen portion. Water can still be topped up into the
remaining capacity, and freezing/thawing conserves both the ice and liquid
amounts.

Snow shares the same eight-level space. For example, three snow layers leave
room for five water units. Snow stacking is rejected when it would overfill the
block, and wet snow retains its finite water when removed or melted.

### Frozen behavior

- Frozen waterlogged blocks gain an ice overlay and solid ice collision up to
  the frozen height. Thin ice around plants is aligned to the block grid.
- Frozen surfaces use vanilla ice slipperiness.
- Frozen blocks are immovable by pistons and block normal use, inventory access,
  hopper access, replacement, and normal block ticking. Paired chests, doors,
  beds, and similar two-part blocks are locked together.
- Breaking frozen waterlogged ice thaws the original host instead of breaking
  or harvesting it, including with Silk Touch. Its contents are retained.
- Strong block light above 11 thaws frozen waterlogged blocks and restores the
  stored finite-water amount.
- Full Finite Ice drops itself with Silk Touch; without Silk Touch it releases
  eight finite-water units. Layered Finite Ice drops no item and releases its
  frozen units plus any stored liquid water.
- Full and layered finite ice also melt back into their conserved water volume
  under strong block light.

- Legacy or debug-stick-created states that exceed the shared eight-unit
  capacity are repaired before further flow or freezing, rather than causing
  an overfill failure.
- Ice overlay faces are culled using the neighboring frozen height, so
  adjacent layers do not hide exposed borders while shorter neighboring snow
  or ice remains correctly visible.

Vanilla water and vanilla-waterlogged blocks are not affected by the finite-ice
logic.

## Pointed dripstone

Hanging pointed dripstone can produce finite water when:

- the source exactly two blocks above the support contains a full 8-unit
  finite-water source;
- the dripstone is a downward, free-hanging stalactite; and
- the random-tick roll succeeds.

Each successful drip adds one unit without consuming the source. It creates a
finite-water puddle in air above the first obstruction or adds to an existing
partial puddle up to level 8. The normal vanilla drip path limit applies.
Vanilla cauldrons, vanilla fluids, upward dripstone, and invalid obstructions
are not converted or overwritten.

The feature is enabled by default. `dripstone.fill_chance` defaults to
`0.17578125`; setting it to `0` prevents accumulation.

## Rainfall and anti-rain generator

- Rain can add finite water to eligible exposed surfaces, up to level 3. It does
  not convert vanilla water or change other fluids.
- Shallow finite water can be absorbed by configured surfaces or evaporate in
  bright, clear weather. The default absorption set includes dirt, farmland,
  gravel, sand, and moss. Included and excluded block selectors are configurable.
- Rainfall is enabled by default. The default change chance is `0.075` and the
  clear-day evaporation chance is `0.5`. Changes are normally limited to a
  six-chunk square radius around players; outside it, collection stops while
  removal may continue (`DISAPPEAR_ONLY`).
- A powered `pumpedupwater:anti_rain_generator` prevents new finite-water rain
  collection in its chunk and the eight surrounding chunks. It leaves vanilla
  weather, other fluids, and existing finite-water drying unchanged.

## Rain / Water Sensor

The **Rain / Water Sensor** is a redstone block that:

- detects exposed rain above it;
- detects submersion in vanilla or finite water when the fluid amount is above
  level 2;
- emits redstone power level 15 while active;
- can be toggled into inverted mode by using it without an item; and
- rechecks its state every 20 ticks.

It can be waterlogged and can hold finite water like other supported blocks.
Snowfall is not currently treated as rain by the sensor.

## Water pump

The **Water Pump** is a configurable redstone multiblock that moves finite
water. It supports all six orientations and forms a 1x1, 2x2, or 3x3 square
when adjacent pump blocks face the same direction. Touching blocks are grouped
into non-overlapping squares, preferring larger squares; formation is bounded
and does not load missing chunks.

- Up to three equal-sized squares can connect end to end along the outlet axis.
  Connected stages have independent motors and rotors. Only consecutively
  powered stages contribute to a series transfer; series increase search reach,
  not throughput. Different sizes, offsets, opposite directions, and chains
  longer than three do not connect.
- Redstone at any constituent block powers that stage. The blades first pitch
  and then the rotor accelerates when powered; removing power brakes the rotor
  and feathers the blades. The transitions are synchronized across each square
  and reversing power preserves the current position and ordering.
- The casing has collision while the motors, supports, and blades leave the
  central opening passable. An open 1x1 admits crawling or swimming entities;
  open 2x2 and 3x3 pumps admit crouching or swimming entities.
- Powered openings deal ordinary melee damage without knockback: 10 health
  points for a 1x1 and 5 health points for a 2x2 or 3x3, starting on contact and
  repeating every 8 ticks. Armor, invulnerability, and other normal melee
  protections still apply. The current implementation attributes this damage
  to a non-connected `[Water Pump]` fake player, so mob loot and experience use
  player-kill handling.
- Powered pumps draw finite water from the rotor and up to three cells behind
  each intake lane, then discharge it forward. Dry gaps are allowed, but
  unloaded cells, solid obstructions, closed faces, and non-finite fluids stop
  a lane. Vanilla water and lava are never pumped or converted.
- The default transfer rate is one full water block (8 levels) per constituent
  block every 20 ticks: 1, 4, or 9 full blocks for 1x1, 2x2, or 3x3. Partial
  source levels are preserved. A transfer without enough discharge capacity
  changes no water and creates no current.
- A powered series fills its internal gaps from upstream before discharging.
  Priming uses the normal cycle budget and any remaining budget can discharge
  in that same cycle. Independent powered pumps downstream block the pressure
  search; members of the same powered series are allowed. Powered pumps also
  block backward finite-water flow on both axial faces, while powering off
  restores passive passage.
- Transfers create the existing finite-water entity currents through the
  intake, rotor, and discharge path. Pump currents steer entities toward the
  opening, fill the complete cross-section, compensate gravity across dry
  upward cells, and last for the configured pump interval. A powered pump with
  water in its rotor or three-cell intake reach refreshes the passage current
  even during priming or blocked-discharge cycles.
- Powering a stage plays a startup sound. After startup, powered dry rotors
  emit a mechanical sound and wet rotors add the whirlpool sound. The wet sound
  currently carries about 24 blocks and includes a quiet mechanical rumble;
  larger dry rotors are louder.

The pump recipe yields two pumps from four iron blocks, four iron bars, and a
waxed copper golem statue. Pumps are available in the Redstone creative tab.

Pump settings are stored in the `[pump]` server configuration section:

- `tick_interval` defaults to `20` ticks.
- `water_units_per_cycle` defaults to `8` levels per constituent block.
- `max_depth` defaults to `8` and `max_visited_water_cells` to `64` per
  powered stage. These limits add across a valid series, up to three stages.

## Water valve

The **Water Valve** is an animated redstone multiblock that opens or seals a
finite-water passage. It supports all six orientations and forms a 1x1, 2x2,
or 3x3 square from same-facing adjacent valve blocks. Power at any member
opens the complete square; removing power closes it. A complete transition
takes 140 ticks: 60 ticks to swing the leaves, 60 to extend or retract the
telescoping plates, and 20 for the final locking bolts. Opening reverses those
stages. The water seal closes at the outlet face before the final locking
phase.

- The visible frame, actuators, panels, reinforcing ribs, rivets, and locking
  hardware animate together across the square. The 3x3 variant has three
  actuator rows per leaf.
- When open, the passage is collision-clear enough for crawling or swimming
  through a 1x1 and walking through 2x2 or 3x3 valves. When closed, the seal
  blocks finite-water flow in both directions, including transfers driven by
  the water pump.
- Animation changes wake neighboring finite water without replacing the
  waterlogged valve state. Stored finite-water levels and saved travel
  progress survive state refresh and reload.
- Each square has synchronized staged mechanical sounds. The block is
  available in the Redstone creative tab and its recipe yields two valves.

## Piston pressure

When an extending piston would move finite-water cells, the mod searches the
connected finite-water network for valid destinations and pushes the water in
the piston direction, with deterministic direction priority. The search is
bounded by:

- `piston_pressure.max_depth` — default `8`;
- `piston_pressure.max_visited_water_cells` — default `64`.

The full transfer plan is calculated before world mutation. If capacity,
loaded-chunk, collision, or piston-occupied-cell checks fail, the piston move
and water state remain unchanged. Successful pressure transfers also generate
the corresponding entity currents.

## Commands

The mod registers:

```text
/waterlevel <pos>
```

It reports the finite-water level at the target position. Air reports level 0;
unsupported blocks and positions outside the build height are not finite-water
targets.

## Shader compatibility

Optional client integration targets Iris 1.11.2 and Sodium 0.9.1 on Minecraft
26.2. It supplies finite-water shader material handling, fog, and smooth
lighting. The integration is version-gated; other Iris/Sodium versions use
ordinary rendering. Manual finite-versus-vanilla visual parity remains to be
checked in-game.

## Server configuration and datapack tags

Framework stores server settings in `config/pumpedupwater/server.toml` and the
startup waterlogging rules in `config/pumpedupwater/waterlogging.toml`.
Configured provides an optional editing UI. Settings cover flow timing and
search limits, rainfall and absorption, extinguishing, door pressure, currents,
piston pressure, pumps, dripstone, and crop fertilization. Waterlogging
exclusions and inclusions require a restart because they affect block states.

| Tag | Default | Purpose |
| --- | --- | --- |
| `pumpedupwater:extended_drain_path` | empty | Adds blocks that may extend puddle drain searches. |
| `pumpedupwater:ignores_own_shape_for_outflow` | empty | Ignores a tagged block's own horizontal outflow shape. |
| `pumpedupwater:water_pressure_openable_doors` | `#minecraft:wooden_doors` | Selects doors outside finite water may open. |
| `pumpedupwater:finite_waterlogging_excluded` | empty | Adds exclusions to the startup waterlogging rules. |
| `pumpedupwater:finite_water_extinguishable` | campfires, candles, candle cakes | Selects lit blocks extinguished by finite water. |

The waterlogging config also defaults to exclusions for barriers, beacons,
leaves, shulker boxes, walls, and glass panes. The maximum finite-water level
remains eight because it is part of saved block-state and fluid-state formats.