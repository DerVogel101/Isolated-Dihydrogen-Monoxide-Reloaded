# Development

Build the mod with JDK 25:

```powershell
.\gradlew.bat build --no-daemon
```

## Focused server checks

```powershell
.\gradlew.bat -PvalveTest runServer --args="--nogui" --no-daemon
.\gradlew.bat -PiceTest runServer --args="--nogui" --no-daemon
.\gradlew.bat -PcropTest runServer --args="--nogui" --no-daemon
.\gradlew.bat -PearlyRulesTest --args="--nogui" --offline
```

The valve geometry self-check writes `build/valve-geometry-preview.png`.

## Finite-ice client check

In a cold biome, freeze finite water stored in a chest, potted plant, and slab. Confirm the ice overlay and collision, blocked chest access (including the other half of a double chest), and that breaking or melting the ice preserves the original blocks and chest contents.

Also check partially frozen plants and layered ice: ice should stay visible under liquid water and align to the block grid around offset plants. Freeze and thaw finite water above snow; the snow layer count and total water volume should remain unchanged, and water should render above rather than through the snow.
