package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class FiniteWaterMathSelfTest {
    public static void main(String[] args) throws Exception {
        verifyAllHorizontalCombinations();
        verifySettledWaterStaysSettled();
        verifyDistribution();
        verifyPistonTransaction();
        verifyPistonDirectionPriority();
        verifyPistonSideFallback();
        verifyPistonDoesNotCrossBarrier();
        verifyBlockedPiston();
        verifyPistonPressureAroundCorner();
        verifyPistonPressureSplitsCapacity();
        verifyPistonPressureUsesStraightOutletForMultipleSources();
        verifyPistonPressureDoesNotTurnThroughAir();
        verifyPistonPressureDoesNotTravelThroughAir();
        verifyPistonPressureRejectsInsufficientCapacity();
        verifyPistonPressureKeepsComponentsSeparate();
        verifyPistonPressureLimits();
        verifyPistonPressureDoesNotLoadChunks();
        verifyPistonPressureAvoidsOccupiedCells();
        verifyCurrentStrength();
        verifyPistonCurrentDirections();
        verifyFiniteWaterEntityCompatibilityMixin();
        verifyPistonPressureConfig();
    }

    private static void verifyAllHorizontalCombinations() {
        for (int center = 0; center <= 8; center++) {
            for (int north = -1; north <= 8; north++) {
                for (int east = -1; east <= 8; east++) {
                    for (int south = -1; south <= 8; south++) {
                        for (int west = -1; west <= 8; west++) {
                            verifyConservation(center, new int[]{north, east, south, west});
                        }
                    }
                }
            }
        }
    }

    private static void verifyConservation(int center, int[] neighbors) {
        int before = center + sum(neighbors);
        int remaining = FiniteWaterMath.equalizeFromCenter(center, neighbors);
        int after = remaining + sum(neighbors);
        if (before != after) {
            throw new AssertionError("Finite-water volume changed from " + before + " to " + after);
        }
        for (int amount : neighbors) {
            if (amount < -1 || amount > 8) {
                throw new AssertionError("Invalid finite-water level: " + amount);
            }
        }
    }

    private static void verifySettledWaterStaysSettled() {
        int[] neighbors = {0, 1, -1, 0};
        int remaining = FiniteWaterMath.equalizeFromCenter(1, neighbors);
        if (remaining != 1 || neighbors[0] != 0 || neighbors[1] != 1 || neighbors[3] != 0) {
            throw new AssertionError("Settled finite water moved again");
        }
    }

    private static void verifyDistribution() {
        int[] targets = {-1, 6, 8, 0};
        int remaining = FiniteWaterMath.distribute(8, targets);
        if (remaining != 0 || targets[0] != -1 || targets[1] != 8 || targets[2] != 8 || targets[3] != 6) {
            throw new AssertionError("Finite-water distribution lost or crossed capacity");
        }
    }

    private static int sum(int[] amounts) {
        int result = 0;
        for (int amount : amounts) {
            result += Math.max(amount, 0);
        }
        return result;
    }

    private static void verifyPistonTransaction() {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.east();
        Map<BlockPos, Integer> world = Map.of(first, 8, second, 8);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(first, second), Direction.EAST, pos -> world.getOrDefault(pos, 0)
        );
        if (plan == null
                || plan.values().stream().mapToInt(Integer::intValue).sum() != 16
                || plan.getOrDefault(second, -1) != 8
                || plan.getOrDefault(second.east(), -1) != 8) {
            throw new AssertionError("Multi-block piston plan did not conserve 16 units");
        }
    }

    private static void verifyPistonDirectionPriority() {
        BlockPos water = BlockPos.ZERO;
        BlockPos first = water.east();
        BlockPos second = first.east();
        Map<BlockPos, Integer> world = Map.of(water, 8, first, 4, first.above(), -1);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST, pos -> world.getOrDefault(pos, 0)
        );
        if (plan == null || plan.getOrDefault(first, -1) != 8 || plan.getOrDefault(second, -1) != 4) {
            throw new AssertionError("Piston plan did not continue in the preferred direction");
        }
    }

    private static void verifyPistonSideFallback() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST,
                pos -> pos.equals(water) ? 8 : pos.getX() > 0 ? -1 : 0
        );
        if (plan == null || plan.getOrDefault(water.south(), -1) != 8) {
            throw new AssertionError("Piston plan did not use the clockwise side fallback");
        }
    }

    private static void verifyPistonDoesNotCrossBarrier() {
        BlockPos water = BlockPos.ZERO;
        BlockPos airBehindBarrier = water.east().east();
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST,
                pos -> pos.equals(water) ? 8 : pos.equals(airBehindBarrier) ? 0 : -1
        );
        if (plan != null) {
            throw new AssertionError("Piston plan crossed a solid barrier");
        }
    }

    private static void verifyBlockedPiston() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST, pos -> pos.equals(water) ? 8 : -1
        );
        if (plan != null) {
            throw new AssertionError("Fully blocked piston plan should fail");
        }
    }

    private static void verifyPistonPressureAroundCorner() {
        BlockPos start = BlockPos.ZERO;
        BlockPos conduit = start.south();
        BlockPos outlet = conduit.east();
        Map<BlockPos, Integer> world = Map.of(start, 8, conduit, 8, outlet, 0);
        Map<BlockPos, Integer> plan = plan(List.of(start), world, 8, 64);
        assertSuccessfulPlan("Corner pressure", world, plan);
        if (plan.getOrDefault(outlet, -1) != 8) {
            throw new AssertionError("Piston pressure did not turn through finite water");
        }
    }

    private static void verifyPistonPressureSplitsCapacity() {
        BlockPos start = BlockPos.ZERO;
        BlockPos firstTarget = start.east();
        BlockPos secondTarget = start.south();
        Map<BlockPos, Integer> world = Map.of(start, 8, firstTarget, 4, secondTarget, 4);
        Map<BlockPos, Integer> plan = plan(List.of(start), world, 8, 64);
        assertSuccessfulPlan("Split pressure", world, plan);
        if (plan.getOrDefault(firstTarget, -1) != 8 || plan.getOrDefault(secondTarget, -1) != 8) {
            throw new AssertionError("Piston pressure did not combine partial capacities");
        }
    }

    private static void verifyPistonPressureUsesStraightOutletForMultipleSources() {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.south();
        BlockPos outlet = first.east();
        BlockPos overflow = outlet.east();
        Map<BlockPos, Integer> world = Map.of(first, 8, second, 8, outlet, 0, overflow, 0);
        Set<BlockPos> occupied = Set.of(first, second);

        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(first, second), Direction.EAST, levelAt(world), 8, 64, pos -> true, occupied
        );
        assertSuccessfulPlan("Straight pressure outlet", world, plan);
        if (plan.getOrDefault(outlet, -1) != 8 || plan.getOrDefault(overflow, -1) != 8) {
            throw new AssertionError("One outlet did not accept both displaced water sources");
        }

        if (SpecialFlow.planPush(
                List.of(first, second), Direction.EAST, levelAt(world), 1, 64, pos -> true, occupied
        ) != null) {
            throw new AssertionError("Straight pressure outlet exceeded its depth limit");
        }
    }

    private static void verifyPistonPressureDoesNotTurnThroughAir() {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.south();
        BlockPos outlet = first.east();
        BlockPos aroundCorner = outlet.north();
        Map<BlockPos, Integer> world = Map.of(first, 8, second, 8, outlet, 0, aroundCorner, 0);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(first, second), Direction.EAST, levelAt(world), 8, 64,
                pos -> true, Set.of(first, second)
        );
        if (plan != null) {
            throw new AssertionError("Piston pressure turned through air around a corner");
        }
    }

    private static void verifyPistonPressureDoesNotTravelThroughAir() {
        BlockPos start = BlockPos.ZERO;
        Map<BlockPos, Integer> world = Map.of(start, 8, start.east().east(), 0);
        if (plan(List.of(start), world, 8, 64) != null) {
            throw new AssertionError("Piston pressure travelled through an air corridor");
        }
    }

    private static void verifyPistonPressureRejectsInsufficientCapacity() {
        BlockPos start = BlockPos.ZERO;
        Map<BlockPos, Integer> world = Map.of(start, 8, start.east(), 7);
        Map<BlockPos, Integer> snapshot = new HashMap<>(world);
        if (plan(List.of(start), world, 8, 64) != null || !world.equals(snapshot)) {
            throw new AssertionError("Insufficient pressure capacity was not rejected atomically");
        }
    }

    private static void verifyPistonPressureKeepsComponentsSeparate() {
        BlockPos openStart = BlockPos.ZERO;
        BlockPos blockedStart = new BlockPos(10, 0, 0);
        Map<BlockPos, Integer> world = Map.of(openStart, 8, openStart.east(), 0, blockedStart, 8);
        if (plan(List.of(openStart, blockedStart), world, 8, 64) != null) {
            throw new AssertionError("Disconnected pressure component borrowed capacity");
        }
    }

    private static void verifyPistonPressureLimits() {
        BlockPos start = BlockPos.ZERO;
        Map<BlockPos, Integer> world = Map.of(
                start, 8,
                start.east(), 8,
                start.east(2), 8,
                start.east(3), 0
        );
        if (plan(List.of(start), world, 2, 64) != null) {
            throw new AssertionError("Piston pressure exceeded its depth limit");
        }
        if (plan(List.of(start), world, 3, 2) != null) {
            throw new AssertionError("Piston pressure exceeded its visited-water limit");
        }
        assertSuccessfulPlan("Configured pressure limits", world, plan(List.of(start), world, 3, 3));
    }

    private static void verifyPistonPressureDoesNotLoadChunks() {
        BlockPos start = BlockPos.ZERO;
        BlockPos unloadedTarget = start.east();
        Map<BlockPos, Integer> world = Map.of(start, 8, unloadedTarget, 0);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(start), Direction.EAST, levelAt(world), 8, 64, pos -> !pos.equals(unloadedTarget)
        );
        if (plan != null) {
            throw new AssertionError("Piston pressure used capacity in an unloaded chunk");
        }
    }

    private static void verifyPistonPressureAvoidsOccupiedCells() {
        BlockPos start = BlockPos.ZERO;
        BlockPos occupiedTarget = start.east();
        Map<BlockPos, Integer> world = Map.of(start, 8, occupiedTarget, 0);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(start), Direction.EAST, levelAt(world), 8, 64,
                pos -> true, Set.of(occupiedTarget)
        );
        if (plan != null) {
            throw new AssertionError("Piston pressure filled a cell that the piston subsequently occupies");
        }
    }

    private static void verifyCurrentStrength() {
        if (Math.abs(FiniteWaterPhysics.verticalCurrentStrength(8) - 0.06D) > 1.0E-9D
                || Math.abs(FiniteWaterPhysics.verticalCurrentStrength(-8) + 0.039D) > 1.0E-9D
                || Math.abs(FiniteWaterPhysics.verticalCurrentStrength(4) - 0.03D) > 1.0E-9D
                || FiniteWaterPhysics.verticalCurrentStrength(0) != 0.0D) {
            throw new AssertionError("Vertical current strength does not scale with transferred units");
        }
        if (FiniteWaterPhysics.combineVerticalCurrents(4, 8) != 8
                || FiniteWaterPhysics.combineVerticalCurrents(-4, -8) != -8
                || FiniteWaterPhysics.combineVerticalCurrents(8, -4) != 4
                || FiniteWaterPhysics.combineVerticalCurrents(4, -8) != -4) {
            throw new AssertionError("Overlapping vertical current cells combine incorrectly");
        }
        Vec3 horizontalStrength = FiniteWaterPhysics.currentStrength(new Vec3(8.0D, 0.0D, -4.0D));
        if (Math.abs(horizontalStrength.x() - 0.06D) > 1.0E-9D
                || horizontalStrength.y() != 0.0D
                || Math.abs(horizontalStrength.z() + 0.03D) > 1.0E-9D) {
            throw new AssertionError("Horizontal current strength does not scale with transferred units");
        }
        Vec3 combined = FiniteWaterPhysics.combineCurrentUnits(
                new Vec3(8.0D, 0.0D, 0.0D), new Vec3(-4.0D, 8.0D, 0.0D)
        );
        if (!combined.equals(new Vec3(4.0D, 8.0D, 0.0D))) {
            throw new AssertionError("Overlapping three-dimensional currents combine incorrectly");
        }
    }

    private static void verifyPistonCurrentDirections() {
        BlockPos start = BlockPos.ZERO;
        Map<BlockPos, Integer> upwardWorld = Map.of(start, 8, start.above(), 0);
        Map<BlockPos, Vec3> upwardTransfers = new HashMap<>();
        Map<BlockPos, Integer> upwardPlan = SpecialFlow.planPush(
                List.of(start), Direction.UP, levelAt(upwardWorld), 8, 64,
                pos -> true, Set.of(start), upwardTransfers
        );
        if (upwardPlan == null
                || upwardTransfers.getOrDefault(start.above(), Vec3.ZERO).y() != 8.0D) {
            throw new AssertionError("Upward piston transfer did not retain its current direction");
        }

        Map<BlockPos, Integer> downwardWorld = Map.of(start, 8, start.below(), 0);
        Map<BlockPos, Vec3> downwardTransfers = new HashMap<>();
        Map<BlockPos, Integer> downwardPlan = SpecialFlow.planPush(
                List.of(start), Direction.DOWN, levelAt(downwardWorld), 8, 64,
                pos -> true, Set.of(start), downwardTransfers
        );
        if (downwardPlan == null
                || downwardTransfers.getOrDefault(start.below(), Vec3.ZERO).y() != -8.0D) {
            throw new AssertionError("Downward piston transfer did not retain its current direction");
        }

        BlockPos columnMiddle = start.above();
        BlockPos columnOutlet = start.above(2);
        Map<BlockPos, Vec3> columnTransfers = new HashMap<>();
        Map<BlockPos, Integer> columnPlan = SpecialFlow.planPush(
                List.of(start), Direction.UP,
                levelAt(Map.of(start, 8, columnMiddle, 8, columnOutlet, 0)), 8, 64,
                pos -> true, Set.of(start), columnTransfers
        );
        if (columnPlan == null
                || columnTransfers.getOrDefault(start, Vec3.ZERO).y() != 8.0D
                || columnTransfers.getOrDefault(columnMiddle, Vec3.ZERO).y() != 8.0D
                || columnTransfers.getOrDefault(columnOutlet, Vec3.ZERO).y() != 8.0D) {
            throw new AssertionError("Vertical current did not cover the full pressure path");
        }

        BlockPos corner = start.east();
        BlockPos cornerOutlet = corner.above();
        Map<BlockPos, Vec3> cornerTransfers = new HashMap<>();
        Map<BlockPos, Integer> cornerPlan = SpecialFlow.planPush(
                List.of(start), Direction.EAST,
                levelAt(Map.of(start, 8, corner, 8, cornerOutlet, 0)), 8, 64,
                pos -> true, Set.of(start), cornerTransfers
        );
        if (cornerPlan == null
                || !cornerTransfers.getOrDefault(corner, Vec3.ZERO).equals(new Vec3(8.0D, 8.0D, 0.0D))
                || cornerTransfers.getOrDefault(cornerOutlet, Vec3.ZERO).y() != 8.0D) {
            throw new AssertionError("Current did not follow a turning pressure path");
        }

        Map<BlockPos, Vec3> horizontalTransfers = new HashMap<>();
        Map<BlockPos, Integer> horizontalPlan = SpecialFlow.planPush(
                List.of(start), Direction.EAST, levelAt(Map.of(start, 8, start.east(), 0)), 8, 64,
                pos -> true, Set.of(start), horizontalTransfers
        );
        if (horizontalPlan == null
                || !horizontalTransfers.getOrDefault(start.east(), Vec3.ZERO)
                .equals(new Vec3(8.0D, 0.0D, 0.0D))) {
            throw new AssertionError("Horizontal piston transfer did not retain its current direction");
        }

        Map<BlockPos, Vec3> failedTransfers = new HashMap<>();
        Map<BlockPos, Integer> failedPlan = SpecialFlow.planPush(
                List.of(start), Direction.UP, levelAt(Map.of(start, 8, start.above(), 7)), 8, 64,
                pos -> true, Set.of(start), failedTransfers
        );
        if (failedPlan != null || !failedTransfers.isEmpty()) {
            throw new AssertionError("Failed piston plan leaked a vertical current");
        }
    }

    private static void verifyPistonPressureConfig() throws Exception {
        Path directory = Files.createTempDirectory("immersivefluids-config-test");
        Path config = directory.resolve("immersivefluids.properties");
        try {
            WaterPhysicsConfig.load(directory);
            if (!Files.isRegularFile(config)
                    || WaterPhysicsConfig.pistonPressureMaxDepth() != 8
                    || WaterPhysicsConfig.pistonPressureMaxVisitedWaterCells() != 64) {
                throw new AssertionError("Default piston-pressure config was not created");
            }

            Files.writeString(config, "piston_pressure.max_depth=3\n"
                    + "piston_pressure.max_visited_water_cells=5\n");
            WaterPhysicsConfig.load(directory);
            if (WaterPhysicsConfig.pistonPressureMaxDepth() != 3
                    || WaterPhysicsConfig.pistonPressureMaxVisitedWaterCells() != 5) {
                throw new AssertionError("Custom piston-pressure limits were not loaded");
            }
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(directory);
        }
    }

    private static void verifyFiniteWaterEntityCompatibilityMixin() throws Exception {
        String resource = "waterphysics.mixins.json";
        try (var stream = FiniteWaterMathSelfTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("Missing Mixin configuration: " + resource);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!json.contains("\"MixinEntity\"")) {
                throw new AssertionError("Finite-water entity compatibility Mixin is not configured");
            }
        }
        Class.forName("io.github.SirWashington.mixin.MixinEntity");
    }

    private static Map<BlockPos, Integer> plan(List<BlockPos> starts, Map<BlockPos, Integer> world,
                                                int maxDepth, int maxVisitedWaterCells) {
        return SpecialFlow.planPush(
                starts, Direction.EAST, levelAt(world), maxDepth, maxVisitedWaterCells, pos -> true
        );
    }

    private static ToIntFunction<BlockPos> levelAt(Map<BlockPos, Integer> world) {
        return pos -> world.getOrDefault(pos, -1);
    }

    private static void assertSuccessfulPlan(String name, Map<BlockPos, Integer> world,
                                             Map<BlockPos, Integer> plan) {
        if (plan == null) {
            throw new AssertionError(name + " unexpectedly failed");
        }
        Map<BlockPos, Integer> after = new HashMap<>(world);
        after.putAll(plan);
        int beforeVolume = world.values().stream().mapToInt(value -> Math.max(value, 0)).sum();
        int afterVolume = after.values().stream().mapToInt(value -> Math.max(value, 0)).sum();
        if (beforeVolume != afterVolume || after.values().stream().anyMatch(value -> value < 0 || value > 8)) {
            throw new AssertionError(name + " did not conserve valid finite-water levels");
        }
    }
}
