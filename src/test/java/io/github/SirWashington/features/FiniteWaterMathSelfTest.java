package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mrcrayfish.framework.api.config.AbstractProperty;
import com.mrcrayfish.framework.api.config.ConfigType;
import com.mrcrayfish.framework.api.config.FrameworkConfig;
import com.mrcrayfish.framework.config.FrameworkConfigManager.ValueProxy;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import io.github.SirWashington.block.ModBlockTags;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class FiniteWaterMathSelfTest {
    private static void verifyFlowSounds() {
        Map<BlockPos, FiniteWaterSounds.Flow> flows = new HashMap<>();
        BlockPos pos = new BlockPos(-1, 64, -1);
        FiniteWaterSounds.accumulate(flows, pos, 0);
        if (!flows.isEmpty() || FiniteWaterSounds.volume(0) != 0) {
            throw new AssertionError("No movement must stay silent");
        }
        FiniteWaterSounds.accumulate(flows, pos, 1);
        FiniteWaterSounds.accumulate(flows, pos.west(), 7);
        var flow = flows.values().iterator().next();
        if (flows.size() != 1 || flow.units() != 8 || !flow.pos().equals(pos)) {
            throw new AssertionError("Nearby flow must combine at a real transfer position");
        }
        FiniteWaterSounds.accumulate(flows, pos, 100);
        FiniteWaterSounds.accumulate(flows, pos.east(), 1);
        if (flows.size() != 2 || flows.get(new BlockPos(-1, 16, -1)).units() != 64) {
            throw new AssertionError("Flow areas must separate and cap accumulated volume");
        }
        float previous = 0;
        for (int units = 1; units <= 64; units++) {
            float volume = FiniteWaterSounds.volume(units);
            if (volume <= previous || volume > 1F) {
                throw new AssertionError("Flow sound must grow with movement and respect the volume cap");
            }
            previous = volume;
        }
        if (FiniteWaterSounds.volume(1000) != previous) {
            throw new AssertionError("Large flows must respect the volume cap");
        }
        if (FiniteWaterSounds.volume(1) > 0.08F
                || FiniteWaterSounds.volume(64) != 1F
                || FiniteWaterSounds.volume(64) < 3 * FiniteWaterSounds.volume(8)) {
            throw new AssertionError("Large flows must be noticeably louder while trickles stay gentle");
        }
        if (FiniteWaterSounds.soundChance(0) != 0) {
            throw new AssertionError("Idle water must never start a sound");
        }
        for (int units = 1; units <= 64; units++) {
            if (FiniteWaterSounds.soundChance(units) <= FiniteWaterSounds.soundChance(units - 1)
                    || FiniteWaterSounds.pitch(units, 0.5F) >= FiniteWaterSounds.pitch(units - 1, 0.5F)) {
                throw new AssertionError("Heavier flow must sound denser and deeper");
            }
        }
        if (FiniteWaterSounds.soundChance(1000) != 13F / 64
                || FiniteWaterSounds.pitch(1000, 0) < 0.3F
                || FiniteWaterSounds.pitch(1, 1) > 1.3F
                || FiniteWaterSounds.pitch(64, 1) >= FiniteWaterSounds.pitch(1, 0)) {
            throw new AssertionError("Rushing sound must stay bounded and distinct from a trickle");
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (int level = -1; level <= 4; level++) {
            double expected = switch (level) {
                case 1 -> 47.0D / 60.0D;
                case 2 -> 17.0D / 30.0D;
                case 3, 4 -> 0.35D;
                default -> 1.0D;
            };
            if (Math.abs(FiniteWaterPhysics.depthStriderCurrentMultiplier(level) - expected) > 1.0E-12D) {
                throw new AssertionError("Incorrect Depth Strider current multiplier at level " + level);
            }
        }
        verifyAllHorizontalCombinations();
        verifySettledWaterStaysSettled();
        verifyBarrierLimitedEqualization();
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
        verifyFlowSounds();
        verifyPistonCurrentDirections();
        verifyFiniteWaterContactFluids();
        verifyFiniteWaterRespectsBlockFaces();
        verifyRaisedWaterloggedVisualLevels();
        verifyExtendedDrainPath();
        verifyDoorWaterPressure();
        verifyFiniteWaterloggedPlantCoverage();
        verifyBlockBehaviorTags();
        verifyFiniteWaterEntityCompatibilityMixin();
        verifyFireExtinguishing();
        verifyWaterPhysicsConfig();
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

    private static void verifyBarrierLimitedEqualization() {
        int[] dryNeighbor = {0};
        int retained = FiniteWaterMath.equalizeFromCenter(4, dryNeighbor, new int[]{4});
        if (retained != 4 || dryNeighbor[0] != 0) {
            throw new AssertionError("Water crossed a half-block barrier at level 4");
        }

        int[] floodedNeighbor = {0};
        retained = FiniteWaterMath.equalizeFromCenter(8, floodedNeighbor, new int[]{4});
        if (retained != 4 || floodedNeighbor[0] != 4) {
            throw new AssertionError("Water above a half-block barrier did not overflow correctly");
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
                List.of(first, second), Direction.EAST, pos -> world.getOrDefault(pos, 0), 8, 64, pos -> true
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
                List.of(water), Direction.EAST, pos -> world.getOrDefault(pos, 0), 8, 64, pos -> true
        );
        if (plan == null || plan.getOrDefault(first, -1) != 8 || plan.getOrDefault(second, -1) != 4) {
            throw new AssertionError("Piston plan did not continue in the preferred direction");
        }
    }

    private static void verifyPistonSideFallback() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST,
                pos -> pos.equals(water) ? 8 : pos.getX() > 0 ? -1 : 0, 8, 64, pos -> true
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
                pos -> pos.equals(water) ? 8 : pos.equals(airBehindBarrier) ? 0 : -1, 8, 64, pos -> true
        );
        if (plan != null) {
            throw new AssertionError("Piston plan crossed a solid barrier");
        }
    }

    private static void verifyBlockedPiston() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST, pos -> pos.equals(water) ? 8 : -1, 8, 64, pos -> true
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

    private static void verifyWaterPhysicsConfig() throws Exception {
        verifyConfigLinkage();
        var maxDepth = WaterPhysicsConfig.SERVER.pistonPressure.maxDepth;
        var maxVisited = WaterPhysicsConfig.SERVER.pistonPressure.maxVisitedWaterCells;
        var flow = WaterPhysicsConfig.SERVER.flow;
        var extinguishing = WaterPhysicsConfig.SERVER.extinguishing;
        var doorPressure = WaterPhysicsConfig.SERVER.doorPressure;
        var currents = WaterPhysicsConfig.SERVER.currents;
        var excludedBlocks = WaterPhysicsConfig.WATERLOGGING.excludedBlocks;
        FrameworkConfig config = WaterPhysicsConfig.class.getField("SERVER").getAnnotation(FrameworkConfig.class);
        if (config == null || !config.id().equals("immersivefluids") || !config.name().equals("server")
                || config.type() != ConfigType.SERVER
                || maxDepth.getDefaultValue() != 8 || !maxDepth.isValid(1) || maxDepth.isValid(0)
                || maxVisited.getDefaultValue() != 64 || !maxVisited.isValid(1) || maxVisited.isValid(0)
                || flow.tickDelay.getDefaultValue() != 2 || !flow.tickDelay.isValid(1) || flow.tickDelay.isValid(0)
                || flow.puddleSearchRadius.getDefaultValue() != 4 || !flow.puddleSearchRadius.isValid(0)
                || flow.puddleSearchRadius.isValid(17)
                || flow.extendedDrainMaxPathLength.getDefaultValue() != 32
                || !flow.extendedDrainMaxPathLength.isValid(4) || flow.extendedDrainMaxPathLength.isValid(129)
                || flow.extendedDrainMaxVisitedCells.getDefaultValue() != 256
                || !flow.extendedDrainMaxVisitedCells.isValid(64)
                || flow.extendedDrainMaxVisitedCells.isValid(4097)
                || !flow.extendedDrainPathBlocks.getDefaultValue().equals(
                List.of("#minecraft:slabs", "#minecraft:stairs")
        )
                || extinguishing.minimumLevel.getDefaultValue() != 3
                || !extinguishing.minimumLevel.isValid(1) || extinguishing.minimumLevel.isValid(9)
                || !doorPressure.enabled.getDefaultValue()
                || doorPressure.requiredLevelPerHalf.getDefaultValue() != 8
                || !doorPressure.requiredLevelPerHalf.isValid(1)
                || doorPressure.requiredLevelPerHalf.isValid(9)
                || !currents.enabled.getDefaultValue()
                || currents.durationTicks.getDefaultValue() != 10
                || currents.horizontalStrength.getDefaultValue() != 0.06D
                || currents.upwardStrength.getDefaultValue() != 0.06D
                || currents.downwardStrength.getDefaultValue() != 0.039D
                || currents.maxHorizontalSpeed.getDefaultValue() != 0.7D
                || currents.maxUpwardSpeed.getDefaultValue() != 0.7D
                || currents.maxDownwardSpeed.getDefaultValue() != 0.3D
                || !excludedBlocks.getDefaultValue().equals(io.github.SirWashington.EarlyWaterloggingRules.DEFAULT_EXCLUDED)
                || WaterPhysicsConfig.flowTickDelay() != 2
                || WaterPhysicsConfig.puddleSearchRadius() != 4
                || WaterPhysicsConfig.extendedDrainMaxPathLength() != 32
                || WaterPhysicsConfig.extendedDrainMaxVisitedCells() != 256
                || WaterPhysicsConfig.extinguishingMinimumLevel() != 3
                || !WaterPhysicsConfig.doorPressureEnabled()
                || WaterPhysicsConfig.doorPressureRequiredLevelPerHalf() != 8
                || !WaterPhysicsConfig.currentsEnabled()
                || WaterPhysicsConfig.currentDurationTicks() != 10
                || !ModBlockTags.matchesSelector(Blocks.OAK_SLAB.defaultBlockState(), "minecraft:oak_slab")
                || ModBlockTags.matchesSelector(Blocks.OAK_SLAB.defaultBlockState(), "minecraft:oak_stairs")
                || ModBlockTags.matchesSelector(Blocks.OAK_SLAB.defaultBlockState(), "not an identifier")) {
            throw new AssertionError("Framework water-physics config is invalid");
        }

        try (var stream = FiniteWaterMathSelfTest.class.getClassLoader().getResourceAsStream("fabric.mod.json")) {
            if (stream == null) {
                throw new AssertionError("Missing Fabric metadata");
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int derVogel = json.indexOf("\"DerVogel101\"");
            if (!json.contains("io.github.SirWashington.WaterPhysicsConfig")
                    || !json.contains("\"configured\"") || !json.contains("\"modmenu\"")
                    || derVogel < 0 || derVogel > json.indexOf("\"SirWashington\"")) {
                throw new AssertionError("Fabric metadata integration or author order is invalid");
            }
        }
    }

    private static void verifyFiniteWaterContactFluids() {
        if (!FiniteWaterPhysics.isVanillaWater(Fluids.WATER)
                || !FiniteWaterPhysics.isVanillaWater(Fluids.FLOWING_WATER)
                || FiniteWaterPhysics.isVanillaWater(Fluids.LAVA)
                || !FiniteWaterPhysics.isVanillaLava(Fluids.LAVA)
                || !FiniteWaterPhysics.isVanillaLava(Fluids.FLOWING_LAVA)
                || FiniteWaterPhysics.isVanillaLava(Fluids.WATER)
                || FiniteWaterPhysics.isVanillaWater(Fluids.EMPTY)
                || FiniteWaterPhysics.isVanillaLava(Fluids.EMPTY)) {
            throw new AssertionError("Finite-water contact fluid classification is invalid");
        }
    }

    private static void verifyConfigLinkage() throws Exception {
        var delay = WaterPhysicsConfig.SERVER.flow.tickDelay;
        var exclusions = WaterPhysicsConfig.WATERLOGGING.excludedBlocks;
        var proxyField = AbstractProperty.class.getDeclaredField("proxy");
        proxyField.setAccessible(true);
        var emptyField = ValueProxy.class.getDeclaredField("EMPTY");
        emptyField.setAccessible(true);
        var empty = (ValueProxy) emptyField.get(null);
        var constructor = ValueProxy.class.getDeclaredConstructor(UnmodifiableConfig.class, List.class, boolean.class);
        constructor.setAccessible(true);
        var originalDelay = proxyField.get(delay);
        var originalExclusions = proxyField.get(exclusions);
        var recordingPath = Files.createTempFile("finite-water-config-", ".jfr");
        try (var recording = new Recording()) {
            recording.enable("jdk.JavaExceptionThrow").withStackTrace();
            recording.start();
            verifyTagLinkage();
            // Both cold construction and Framework's unloaded sentinel must avoid exception-based defaults.
            for (var proxy : new ValueProxy[]{null, empty}) {
                proxyField.set(delay, proxy);
                proxyField.set(exclusions, proxy);
                for (int i = 0; i < 1000; i++) {
                    if (WaterPhysicsConfig.flowTickDelay() != 2) {
                        throw new AssertionError("Unlinked config must use defaults");
                    }
                }
            }
            var config = Config.inMemory();
            config.set("delay", 7);
            config.set("exclusions", List.of("minecraft:oak_slab"));
            delay.updateProxy(constructor.newInstance(config, List.of("delay"), false));
            exclusions.updateProxy(constructor.newInstance(config, List.of("exclusions"), true));
            if (WaterPhysicsConfig.flowTickDelay() != 7
                    || !exclusions.get().equals(List.of("minecraft:oak_slab"))) {
                throw new AssertionError("Linked config must use configured values, including read-only sync values");
            }
            delay.set(9);
            if (WaterPhysicsConfig.flowTickDelay() != 9) {
                throw new AssertionError("Config edits must remain visible");
            }
            delay.updateProxy(empty);
            exclusions.updateProxy(empty);
            if (WaterPhysicsConfig.flowTickDelay() != 2) {
                throw new AssertionError("Unloading config must restore defaults");
            }
            config.set("delay", 11);
            config.set("exclusions", List.of("minecraft:oak_stairs"));
            delay.updateProxy(constructor.newInstance(config, List.of("delay"), true));
            exclusions.updateProxy(constructor.newInstance(config, List.of("exclusions"), true));
            if (WaterPhysicsConfig.flowTickDelay() != 11
                    || !exclusions.get().equals(List.of("minecraft:oak_stairs"))) {
                throw new AssertionError("Reloaded/synchronized config must replace old values and defaults");
            }
            recording.stop();
            recording.dump(recordingPath);
            for (var event : RecordingFile.readAllEvents(recordingPath)) {
                if ("Config property is not linked yet".equals(event.getString("message"))
                        || "Tags not bound".equals(event.getString("message"))) {
                    throw new AssertionError("Early lookup constructed an exception: " + event.getStackTrace());
                }
            }
        } finally {
            proxyField.set(delay, originalDelay);
            proxyField.set(exclusions, originalExclusions);
            delay.invalidateCache();
            exclusions.invalidateCache();
            Files.deleteIfExists(recordingPath);
        }
        System.out.println("CONFIG_LINKAGE_SELF_TEST_PASS");
    }

    private static void verifyTagLinkage() throws Exception {
        var state = Blocks.OAK_SLAB.defaultBlockState();
        var holder = state.getBlock().builtInRegistryHolder();
        var tagsField = net.minecraft.core.Holder.Reference.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        var original = tagsField.get(holder);
        try {
            tagsField.set(holder, null);
            for (int i = 0; i < 1000; i++) {
                if (ModBlockTags.contains(ModBlockTags.FINITE_WATERLOGGING_EXCLUDED, state)) {
                    throw new AssertionError("Unbound tags must return false");
                }
            }
            tagsField.set(holder, Set.of(ModBlockTags.FINITE_WATERLOGGING_EXCLUDED));
            if (!ModBlockTags.contains(ModBlockTags.FINITE_WATERLOGGING_EXCLUDED, state)
                    || ModBlockTags.contains(ModBlockTags.EXTENDED_DRAIN_PATH, state)) {
                throw new AssertionError("Bound tags must preserve membership");
            }
            tagsField.set(holder, Set.of(ModBlockTags.EXTENDED_DRAIN_PATH));
            if (ModBlockTags.contains(ModBlockTags.FINITE_WATERLOGGING_EXCLUDED, state)
                    || !ModBlockTags.contains(ModBlockTags.EXTENDED_DRAIN_PATH, state)) {
                throw new AssertionError("Tag reload must replace membership");
            }
        } finally {
            tagsField.set(holder, original);
        }
        System.out.println("TAG_LINKAGE_SELF_TEST_PASS");
    }

    private static void verifyFireExtinguishing() {
        int threshold = WaterPhysicsConfig.extinguishingMinimumLevel();
        if (FiniteWaterPhysics.shouldExtinguishFire(Blocks.FIRE.defaultBlockState(), threshold - 1)
                || !FiniteWaterPhysics.shouldExtinguishFire(Blocks.FIRE.defaultBlockState(), threshold)
                || !FiniteWaterPhysics.shouldExtinguishFire(Blocks.SOUL_FIRE.defaultBlockState(), threshold)
                || FiniteWaterPhysics.shouldExtinguishFire(Blocks.TORCH.defaultBlockState(), 8)) {
            throw new AssertionError("Only finite water at the configured threshold must extinguish free fire");
        }
    }

    private static void verifyFiniteWaterloggedPlantCoverage() {
        List<Block> expectedPlants = List.of(
                Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.SUNFLOWER,
                Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
                Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP, Blocks.PITCHER_PLANT,
                Blocks.NETHER_WART, Blocks.SWEET_BERRY_BUSH,
                Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM,
                Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM,
                Blocks.SUGAR_CANE, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.CACTUS,
                Blocks.CACTUS_FLOWER, Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER,
                Blocks.VINE, Blocks.CAVE_VINES, Blocks.WEEPING_VINES, Blocks.TWISTING_VINES,
                Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS,
                Blocks.SEA_PICKLE, Blocks.COCOA, Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF,
                Blocks.SPORE_BLOSSOM, Blocks.BRAIN_CORAL, Blocks.BRAIN_CORAL_FAN,
                Blocks.BRAIN_CORAL_WALL_FAN, Blocks.MANGROVE_PROPAGULE,
                Blocks.HANGING_ROOTS, Blocks.PALE_HANGING_MOSS, Blocks.GLOW_LICHEN,
                Blocks.PINK_PETALS, Blocks.WILDFLOWERS, Blocks.LEAF_LITTER,
                Blocks.FIREFLY_BUSH, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_CARPET
        );
        if (expectedPlants.stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || !FiniteWaterloggedPlants.supports(Blocks.OAK_DOOR)
                || !FiniteWaterloggedPlants.supports(Blocks.OAK_TRAPDOOR)
                || !FiniteWaterloggedPlants.supports(Blocks.OAK_SLAB)
                || !FiniteWaterloggedPlants.supports(Blocks.OAK_STAIRS)
                || !FiniteWaterloggedPlants.supports(Blocks.IRON_BARS)
                || Blocks.COPPER_BARS.asList().stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || Blocks.BANNER.asList().stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || Blocks.BED.asList().stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || !FiniteWaterloggedPlants.supports(Blocks.BELL)
                || !FiniteWaterloggedPlants.supports(Blocks.BREWING_STAND)
                || !FiniteWaterloggedPlants.supports(Blocks.CAKE)
                || !FiniteWaterloggedPlants.supports(Blocks.CANDLE_CAKE)
                || !FiniteWaterloggedPlants.supports(Blocks.CAMPFIRE)
                || !FiniteWaterloggedPlants.supports(Blocks.CHEST)
                || !FiniteWaterloggedPlants.supports(Blocks.COMPOSTER)
                || !FiniteWaterloggedPlants.supports(Blocks.DAYLIGHT_DETECTOR)
                || !FiniteWaterloggedPlants.supports(Blocks.DRAGON_EGG)
                || !FiniteWaterloggedPlants.supports(Blocks.ENCHANTING_TABLE)
                || !FiniteWaterloggedPlants.supports(Blocks.END_PORTAL_FRAME)
                || !FiniteWaterloggedPlants.supports(Blocks.OAK_FENCE_GATE)
                || !FiniteWaterloggedPlants.supports(Blocks.GRINDSTONE)
                || !FiniteWaterloggedPlants.supports(Blocks.HOPPER)
                || !FiniteWaterloggedPlants.supports(Blocks.LADDER)
                || !FiniteWaterloggedPlants.supports(Blocks.LECTERN)
                || !FiniteWaterloggedPlants.supports(Blocks.LEVER)
                || !FiniteWaterloggedPlants.supports(Blocks.PISTON)
                || !FiniteWaterloggedPlants.supports(Blocks.STONE_BUTTON)
                || !FiniteWaterloggedPlants.supports(Blocks.STONE_PRESSURE_PLATE)
                || !FiniteWaterloggedPlants.supports(Blocks.REDSTONE_WIRE)
                || !FiniteWaterloggedPlants.supports(Blocks.REPEATER)
                || !FiniteWaterloggedPlants.supports(Blocks.RAIL)
                || !FiniteWaterloggedPlants.supports(Blocks.SPAWNER)
                || !FiniteWaterloggedPlants.supports(Blocks.STONECUTTER)
                || !FiniteWaterloggedPlants.supports(Blocks.TORCH)
                || !FiniteWaterloggedPlants.supports(Blocks.TRIPWIRE)
                || !FiniteWaterloggedPlants.supports(Blocks.TURTLE_EGG)
                || Blocks.CARPET.asList().stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || BuiltInRegistries.BLOCK.stream().filter(block -> block instanceof FlowerPotBlock)
                .anyMatch(block -> !FiniteWaterloggedPlants.supports(block))) {
            throw new AssertionError("A vanilla plant or crop family is missing finite-water support");
        }
        if (FiniteWaterloggedPlants.supports(Blocks.AIR)
                || FiniteWaterloggedPlants.supports(Blocks.STONE)
                || FiniteWaterloggedPlants.supports(Blocks.MOSS_BLOCK)
                || FiniteWaterloggedPlants.supports(Blocks.PALE_MOSS_BLOCK)
                || FiniteWaterloggedPlants.supports(Blocks.HAY_BLOCK)) {
            throw new AssertionError("Finite-water support includes unsupported full blocks");
        }
        if (!FiniteWaterloggedPlants.supports(Blocks.OAK_LEAVES)
                || !FiniteWaterloggedPlants.supports(Blocks.COBBLESTONE_WALL)
                || !FiniteWaterloggedPlants.supports(Blocks.GLASS_PANE)
                || Blocks.STAINED_GLASS_PANE.asList().stream().anyMatch(block -> !FiniteWaterloggedPlants.supports(block))
                || !FiniteWaterloggedPlants.supports(Blocks.BARRIER)
                || !FiniteWaterloggedPlants.supports(Blocks.BEACON)
                || !FiniteWaterloggedPlants.supports(Blocks.SHULKER_BOX)) {
            throw new AssertionError("Default exclusions must remain eligible for explicit inclusion");
        }
        if (FiniteWaterloggedPlants.LEVEL.getPossibleValues().size() != 9
                || !FiniteWaterloggedPlants.LEVEL.getPossibleValues().contains(0)
                || !FiniteWaterloggedPlants.LEVEL.getPossibleValues().contains(8)
                || FiniteWaterloggedPlants.DEFAULT_EXTINGUISH_LEVEL != 3) {
            throw new AssertionError("Finite-water plant levels must cover 0 through 8");
        }

        var litCampfire = Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, true);
        if (FiniteWaterloggedPlants.shouldExtinguish(litCampfire, 2, true)
                || !FiniteWaterloggedPlants.shouldExtinguish(litCampfire, 3, true)) {
            throw new AssertionError("Campfires must burn at levels 0-2 and extinguish at level 3+");
        }
    }

    private static void verifyDoorWaterPressure() {
        BlockPos lowerDoor = BlockPos.ZERO;
        Map<BlockPos, Integer> outsideColumn = Map.of(
                lowerDoor.south(), 8,
                lowerDoor.south().above(), 8
        );
        if (!FiniteWaterPhysics.doorHasRequiredWaterOutside(
                lowerDoor, Direction.NORTH, 8, levelAt(outsideColumn)
        )) {
            throw new AssertionError("Two full outside water cells did not open a door inward");
        }

        Map<BlockPos, Integer> insideColumn = Map.of(
                lowerDoor.north(), 8,
                lowerDoor.north().above(), 8
        );
        if (FiniteWaterPhysics.doorHasRequiredWaterOutside(
                lowerDoor, Direction.NORTH, 8, levelAt(insideColumn)
        )) {
            throw new AssertionError("Water on the inside pushed a door open outward");
        }

        Map<BlockPos, Integer> incompleteColumn = Map.of(lowerDoor.south(), 8);
        if (FiniteWaterPhysics.doorHasRequiredWaterOutside(
                lowerDoor, Direction.NORTH, 8, levelAt(incompleteColumn)
        )) {
            throw new AssertionError("One full outside water cell opened both door halves");
        }

        Map<BlockPos, Integer> configurableColumn = Map.of(
                lowerDoor.south(), 6,
                lowerDoor.south().above(), 6
        );
        if (!FiniteWaterPhysics.doorHasRequiredWaterOutside(
                lowerDoor, Direction.NORTH, 6, levelAt(configurableColumn)
        ) || FiniteWaterPhysics.doorHasRequiredWaterOutside(
                lowerDoor, Direction.NORTH, 7, levelAt(configurableColumn)
        )) {
            throw new AssertionError("Door water-pressure threshold is not configurable");
        }
    }

    private static void verifyFiniteWaterRespectsBlockFaces() {
        var air = Shapes.empty();
        var fullBlock = Shapes.block();
        var bottomSlabState = Blocks.OAK_SLAB.defaultBlockState();
        var bottomSlab = bottomSlabState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var bottomStairs = Blocks.OAK_STAIRS.defaultBlockState()
                .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var topSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)
                .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (!FiniteWaterPhysics.canFlowBetween(air, air, Direction.NORTH)
                || FiniteWaterPhysics.canFlowBetween(fullBlock, air, Direction.NORTH)
                || FiniteWaterPhysics.canFlowBetween(bottomSlab, air, Direction.DOWN)
                || !FiniteWaterPhysics.canFlowBetween(bottomSlab, air, Direction.UP)
                || FiniteWaterPhysics.canFlowBetween(bottomStairs, air, Direction.DOWN)
                || !FiniteWaterPhysics.canFlowBetween(bottomStairs, air, Direction.UP)) {
            throw new AssertionError("Finite water does not respect full collision faces");
        }
        if (FiniteWaterPhysics.flowBarrierLevel(bottomSlab, air, Direction.NORTH) != 4
                || FiniteWaterPhysics.flowBarrierLevel(topSlab, air, Direction.NORTH) != 0
                || FiniteWaterPhysics.flowBarrierLevel(fullBlock, air, Direction.NORTH) != 8) {
            throw new AssertionError("Finite-water barrier height does not match the visible block shape");
        }
        if (FiniteWaterPhysics.flowBarrierLevel(
                FiniteWaterPhysics.outgoingFlowShape(bottomSlab, Direction.NORTH, true),
                air, Direction.NORTH
        ) != 0
                || FiniteWaterPhysics.flowBarrierLevel(air, bottomSlab, Direction.NORTH) != 4
                || FiniteWaterPhysics.canFlowBetween(
                FiniteWaterPhysics.outgoingFlowShape(bottomSlabState, bottomSlab, Direction.DOWN),
                air, Direction.DOWN
        )) {
            throw new AssertionError("A slab must release water freely but accept it only above its height");
        }

        boolean stairHasHalfBarrier = false;
        boolean stairHasFullBarrier = false;
        boolean extendedStairKeepsBack = false;
        boolean extendedStairOpensPartialFace = false;
        boolean slabMatchesStairEntry = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int barrier = FiniteWaterPhysics.flowBarrierLevel(bottomStairs, air, direction);
            stairHasHalfBarrier |= barrier == 4;
            stairHasFullBarrier |= barrier == 8;
            int extendedBarrier = FiniteWaterPhysics.flowBarrierLevel(
                    FiniteWaterPhysics.outgoingFlowShape(bottomStairs, direction, false, true),
                    air,
                    direction
            );
            extendedStairKeepsBack |= barrier == 8 && extendedBarrier == 8;
            extendedStairOpensPartialFace |= barrier == 4 && extendedBarrier == 0;
            int stairEntry = FiniteWaterPhysics.flowBarrierLevel(air, bottomStairs, direction);
            slabMatchesStairEntry |= stairEntry == 4
                    && FiniteWaterPhysics.hasCompatibleDrainHeight(bottomSlab, bottomStairs, direction);
        }
        if (!stairHasHalfBarrier || !stairHasFullBarrier
                || !extendedStairKeepsBack || !extendedStairOpensPartialFace
                || !slabMatchesStairEntry
                || !FiniteWaterPhysics.hasCompatibleDrainHeight(bottomSlab, Shapes.empty(), Direction.NORTH)
                || FiniteWaterPhysics.hasCompatibleDrainHeight(
                        bottomSlab, Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D), Direction.NORTH)
                || FiniteWaterPhysics.hasCompatibleDrainHeight(topSlab, bottomSlab, Direction.NORTH)) {
            throw new AssertionError("Extended stairs do not preserve their back while opening partial faces");
        }

        int closedDoorMask = horizontalFlowMask(
                Blocks.OAK_DOOR.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
        );
        int openDoorMask = horizontalFlowMask(
                Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true)
                        .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
        );
        if (closedDoorMask == openDoorMask || closedDoorMask == 0 || openDoorMask == 0) {
            throw new AssertionError("Opening a door did not rotate its finite-water flow boundary");
        }

        var closedTrapdoorState = Blocks.OAK_TRAPDOOR.defaultBlockState();
        var closedTrapdoor = closedTrapdoorState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var openTrapdoor = closedTrapdoorState.setValue(BlockStateProperties.OPEN, true)
                .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (FiniteWaterPhysics.canFlowBetween(
                FiniteWaterPhysics.outgoingFlowShape(closedTrapdoorState, closedTrapdoor, Direction.DOWN),
                air, Direction.DOWN
        )
                || horizontalFlowMask(openTrapdoor) == 0) {
            throw new AssertionError("Trapdoor finite-water boundary does not follow its open state");
        }

        BlockPos start = BlockPos.ZERO;
        Map<BlockPos, Integer> world = Map.of(start, 8, start.east(), 0);
        Map<BlockPos, Integer> blockedPlan = SpecialFlow.planPush(
                List.of(start), Direction.EAST, levelAt(world), 8, 64,
                pos -> true, Set.of(start), new HashMap<>(),
                (from, to) -> !from.equals(start) || !to.equals(start.east())
        );
        if (blockedPlan != null) {
            throw new AssertionError("Piston pressure crossed a block face that stops water");
        }
    }

    private static void verifyRaisedWaterloggedVisualLevels() {
        var bottomSlab = Blocks.OAK_SLAB.defaultBlockState();
        var topSlab = bottomSlab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        var bottomStairs = Blocks.OAK_STAIRS.defaultBlockState();
        var topStairs = bottomStairs.setValue(BlockStateProperties.HALF, Half.TOP);
        int[] expected = {0, 5, 5, 6, 6, 7, 7, 8, 8};
        for (int amount = 0; amount <= 8; amount++) {
            if (FiniteWaterloggedPlants.visualFluidLevel(bottomSlab, amount) != expected[amount]
                    || FiniteWaterloggedPlants.visualFluidLevel(bottomStairs, amount) != expected[amount]
                    || FiniteWaterloggedPlants.visualFluidLevel(topSlab, amount) != amount
                    || FiniteWaterloggedPlants.visualFluidLevel(topStairs, amount) != amount) {
                throw new AssertionError("Waterlogged slab/stair visual level mapping is incorrect at " + amount);
            }
        }
    }

    private static void verifyExtendedDrainPath() {
        BlockPos start = BlockPos.ZERO;
        BlockPos normalEdge = start.east(4);
        BlockPos extended = start.east(5);
        if (!FiniteWaterPhysics.mayTraverseDrainPath(start, normalEdge, 4, 4, 32, pos -> false)
                || FiniteWaterPhysics.mayTraverseDrainPath(start, extended, 5, 4, 32, pos -> false)
                || !FiniteWaterPhysics.mayTraverseDrainPath(start, extended, 5, 4, 32, pos -> true)
                || FiniteWaterPhysics.mayTraverseDrainPath(start, extended, 33, 4, 32, pos -> true)) {
            throw new AssertionError("Extended drain paths ignored their radius, tag, or path-length limit");
        }
    }

    private static void verifyBlockBehaviorTags() throws Exception {
        verifyTagResource("extended_drain_path");
        verifyTagResource("ignores_own_shape_for_outflow");
        verifyTagResource("water_pressure_openable_doors", "#minecraft:wooden_doors");
        verifyTagResource("finite_waterlogging_excluded");
        verifyTagResource(
                "finite_water_extinguishable",
                "#minecraft:campfires", "#minecraft:candles", "#minecraft:candle_cakes"
        );
    }

    private static void verifyTagResource(String name, String... requiredValues) throws Exception {
        String resource = "data/immersivefluids/tags/block/" + name + ".json";
        try (var stream = FiniteWaterMathSelfTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("Missing block tag: " + resource);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String requiredValue : requiredValues) {
                if (!json.contains('"' + requiredValue + '"')) {
                    throw new AssertionError(resource + " is missing " + requiredValue);
                }
            }
        }
    }

    private static int horizontalFlowMask(net.minecraft.world.phys.shapes.VoxelShape shape) {
        int mask = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (FiniteWaterPhysics.canFlowBetween(shape, Shapes.empty(), direction)) {
                mask |= 1 << direction.get2DDataValue();
            }
        }
        return mask;
    }

    private static void verifyFiniteWaterEntityCompatibilityMixin() throws Exception {
        String resource = "waterphysics.mixins.json";
        try (var stream = FiniteWaterMathSelfTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("Missing Mixin configuration: " + resource);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!json.contains("\"MixinAquaticPlantBlock\"")
                    || !json.contains("\"MixinBlockStateBase\"")
                    || !json.contains("\"MixinBucketItem\"")
                    || !json.contains("\"MixinClientLevel\"")
                    || !json.contains("\"MixinMangroveRootPlacer\"")
                    || !json.contains("\"MixinMushroomBlock\"")
                    || !json.contains("\"MixinNetherFungusBlock\"")
                    || !json.contains("\"MixinPlayer\"")
                    || !json.contains("\"MixinSpongeBlock\"")
                    || !json.contains("\"MixinTreeGrower\"")
                    || !json.contains("\"MixinEntity\"")
                    || !json.contains("\"MixinLevel\"")
                    || !json.contains("\"MixinStateDefinitionBuilder\"")) {
                throw new AssertionError("Finite-water compatibility Mixins are not configured");
            }
        }
        Class.forName("io.github.SirWashington.mixin.MixinAquaticPlantBlock");
        Class.forName("io.github.SirWashington.mixin.MixinBlockStateBase");
        Class.forName("io.github.SirWashington.mixin.MixinBucketItem");
        Class.forName("io.github.SirWashington.mixin.MixinEntity");
        Class.forName("io.github.SirWashington.mixin.MixinLevel");
        Class.forName("io.github.SirWashington.mixin.MixinMangroveRootPlacer");
        Class.forName("io.github.SirWashington.mixin.MixinPlayer");
        Class.forName("io.github.SirWashington.mixin.MixinSpongeBlock");
        Class.forName("io.github.SirWashington.mixin.MixinStateDefinitionBuilder");
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
