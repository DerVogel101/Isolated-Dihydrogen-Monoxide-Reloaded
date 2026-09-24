# Water pump

The pump supplies structure, placement, animation, contact damage and finite-water pumping.

Find **Water Pump** in the Redstone creative tab, or use:

```mcfunction
/give @s immersivefluids:water_pump 27
```

- Place a single block, or complete a 2x2 or 3x3 square with every block facing
  the same direction. Squares are one block deep, perpendicular to the outlet.
  The outlet faces toward the player on placement, including up and down.
- Each complete square becomes one motor and one six-blade rotor. The 1x1 and
  2x2 have four side supports; the 3x3 adds four corner supports.
- Touching pumps regroup into non-overlapping squares, choosing 3x3 first, then
  2x2, then individual blocks. Equal-sized candidates are chosen in a consistent
  order in the pump's local plane. No gap is required for parallel pumps: a 6x3
  wall forms two 3x3 stages and a 4x2 wall forms two 2x2 stages. Adding or removing
  blocks can move existing boundaries. Formation searches stop at 1,024 connected
  blocks and never load missing chunks.
- Align up to three equal-sized stages end to end. Their housings connect, with
  one independent motor and rotor per stage. Opposite directions, different
  sizes, offsets, and chains longer than three do not connect.
- The case fills the corners around a circular opening that nearly reaches the
  square's edges. A thin lip surrounds the opening, leaving more room for blades.
- Redstone touching any constituent block activates that stage. Blades first
  pitch to 45 degrees over ten ticks, then the rotor accelerates over ten ticks.
  Removing power first brakes the rotor over ten ticks, then feathers the blades
  parallel to the water path over ten ticks. Reversing power during a transition
  preserves this ordering. Redstone updates take at most five server ticks.
- Powering a stage plays a metallic startup sound from its rotor. After the
  twenty-tick startup, every powered rotor emits a steady mechanical dry-running
  sound or a heavier whirlpool sound while finite water is present in its rotor
  or unobstructed three-cell intake. The wet sound carries about 24 blocks, 50%
  farther than the normal 16-block range, and includes a quiet mechanical rumble.
  Larger dry rotors are louder.
- Break a member to disassemble the square; replace it to reform. A pickaxe
  returns one pump item per block. Craft two pumps using four iron blocks in the
  corners, four iron bars at the sides, and a waxed copper golem statue in the center.
- Only the casing has physical collision. Motors, supports and blades keep their
  visible models but allow passage. The 1x1 admits crawling/swimming; the 2x2 and
  3x3 admit crouching/swimming. The whole-block selection outline is unchanged.
- Powered openings deal melee damage without knockback: 10 health points (5
  hearts) for 1x1, and 5 health points (2.5 hearts) for 2x2/3x3, before armor and
  other normal melee protection. Damage starts on contact and repeats every 8
  ticks while occupied. A shared cooldown prevents overlapping members/stages
  from multiplying hits. Unpowered pumps do no damage. Redstone state controls
  the hazard; the client animation is visual only. Pump damage is attributed to
  a non-connected `[Water Pump]` fake player, enabling player-kill loot and experience.
- Powered pumps draw finite water from inside the assembly and from up to three
  cells behind each intake lane, then push it through the rotor into the discharge side.
  Dry gaps are allowed. An unloaded cell, solid obstruction, closed face or non-finite
  fluid stops that lane's intake scan.
  The piston pressure planner searches connected water for available capacity,
  including bends and upward outlets. Fully closed faces remain blocked.
- The default rate is **one full water block (8 levels) per constituent block every
  20 ticks**: 1 block for a 1x1, 4 for a 2x2, and 9 for a 3x3. The assembly shares
  this total transfer budget across its cross-section. Partial source levels are preserved.
  A transfer without enough discharge capacity changes no water and creates no current.
- Each stage needs its own redstone power. Consecutive powered stages in a valid
  series share one transfer and add their depth and visited-water limits, up to
  three stages. Series increase reach, not throughput. Unpowered stages add no
  reach; overlong or mismatched assemblies receive no series bonus.
- A series fills internal gaps from upstream before discharging. Priming uses its
  normal cycle budget and leaves the rest available for discharge in the same cycle.
  It uses fresh intake water instead of moving a gap between rotor cells.
  A dry three-stage series needs three full intake cycles
  to fill. Once supplied, it draws from behind first and retains its internal water.
  An independent powered pump downstream blocks this pump's pressure search;
  members of the same powered series are allowed.
- Powered rotors block backward finite-water flow on both axial faces, including
  natural flow and piston pressure. Turning power off restores passive passage.
  Vanilla water and lava are not pumped or converted.
- Successful transfers create the existing entity currents along the intake,
  rotor passage and discharge path. These brief pump currents also persist in
  cells just drained by the transfer, so entities can cross the dry rotor passage.
  At every forward plane reached by the transfer, the current fills the pump's
  complete 1x1, 2x2 or 3x3 cross-section. Each obstructed lane remains blocked.
  Near the intake they also steer toward the center and reduce forward pull when
  an entity is misaligned with the opening, helping it clear the casing edge.
  Upward guidance compensates for gravity when crossing a dry cell.
  They respect the existing current enable switch, strengths and speed caps. A
  pump current lasts for the configured `pump.tick_interval`, so consecutive
  successful pumping cycles produce steady flow. Ordinary finite-water currents
  continue to use `currents.duration_ticks`.
  A powered pump with finite water within its three-cell intake reach or rotor passage refreshes this
  passage current every interval, including cycles used for priming or where a
  blocked discharge prevents a pressure transfer. Currents beyond the outlet are
  still created only by water that actually moves there.

## Configuration

The server configuration `immersivefluids.server.toml` contains a `[pump]` section:

```toml
max_depth = 8
max_visited_water_cells = 64
tick_interval = 20
water_units_per_cycle = 8
```

`water_units_per_cycle` is the number of water levels per constituent block.
For example, setting it to 3 gives total budgets of 3, 12 and 27 levels for
1x1, 2x2 and 3x3 stages. Eight water levels equal one full water block.

Pressure search can route around bends beyond the pump outlet.
Depth and visited-water limits, conservation and powered-pump boundaries still apply.

Depth counts path steps from intake/source cells, including travel through the duct.
Dry duct cells do not consume the visited-water budget. Two powered stages give
depth 16 / 128 visited cells; three give depth 24 / 192. Ordinary forward passive
flow can still pass through an opening; the rate limits the active pressure transfer.

## Verification

```powershell
.\gradlew.bat check build --no-daemon
.\gradlew.bat -PpumpTest runServer --args="--nogui" --no-daemon
```

The server regression uses `build/pump-test-server`, requires its usual server
EULA/configuration setup, cleans its fixtures and stops itself. Run the normal
build again after testing to package without the test entrypoint.

`pumpGeometrySelfTest` checks the six orientation bases, blade clearance and
animation bounds. It writes `build/pump-geometry-preview.png` from the renderer's
shared geometry. This is a geometry preview, not a Minecraft screenshot.

The final client check remains manual: inspect all sizes, eight supports on the
3x3, two/three separate rotors in series, redstone on/off transitions, horizontal
and vertical placement, and breaking/reforming an assembly. Also check the
inventory model and rendering with the installed Sodium/Iris setup.

For contact behavior, crawl/swim through an unpowered 1x1 and crouch/swim through
unpowered 2x2/3x3 pumps. Then test powered contact without armor: 1x1 removes five
hearts per hit, larger pumps remove two and a half, every eight ticks, with no
knockback. Switch power off while inside and confirm that damage stops.

For sound, power each pump size and confirm one startup sound per rotor stage,
followed by the dry-running loop. Add finite water within the three-cell intake
range and confirm it changes to the wet-running loop, then remove the water and
confirm it returns to dry running without another startup sound.

The pumping server checks cover crafting, parallel regrouping in all six directions,
area-scaled and configured transfer amounts, conservation, partial sources, blocked
discharge, depth/visited limits, independent stage power, backflow, straight-only
searches, downstream pump boundaries, player centering, priming and native 20-tick
throughput. An off-center mob with gravity enabled must pass through three rotors.
For the client check, feed finite water behind a powered pump, try an
upward discharge and a two/three-stage series, then swim near the intake and verify
the pull through the rotors. Repeat with power off to check passive backflow.
