package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.PumpGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One transactional transfer per contiguous powered segment of a connected series. */
public final class PumpFlow {
    private static final int INTAKE_REACH = 3;

    private PumpFlow() { }

    public static void tick(ServerLevel level, BlockPos controller) {
        PumpStructure stage = PumpStructure.find(level, controller);
        if (!stage.origin().equals(controller) || !stage.powered(level)) return;
        List<PumpStructure> series = stage.series(level);
        int first = series.indexOf(stage);
        if (first > 0 && series.get(first - 1).powered(level)) return;
        int end = first + 1;
        while (end < series.size() && series.get(end).powered(level)) end++;
        List<PumpStructure> poweredStages = series.subList(first, end);
        transfer(level, poweredStages,
                scaledLimit(WaterPhysicsConfig.pumpMaxDepth(), end - first),
                scaledLimit(WaterPhysicsConfig.pumpMaxVisitedWaterCells(), end - first));
    }

    static int scaledLimit(int perStage, int stages) {
        return (int) Math.min(Integer.MAX_VALUE, (long) perStage * stages);
    }

    public static boolean hasWater(ServerLevel level, PumpStructure stage) {
        if (intakePassage(level, stage).stream()
                .anyMatch(pos -> FiniteWaterPhysics.getWaterLevel(level, pos) > 0)) return true;
        for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
            if (FiniteWaterPhysics.getWaterLevel(level, stage.cell(x, y)) > 0) return true;
        }
        return false;
    }

    static boolean transfer(ServerLevel level, List<PumpStructure> stages, int depth, int visits) {
        PumpAssembly machine = new PumpAssembly(stages, stages.getFirst().muted(level), depth, visits,
                WaterPhysicsConfig.pumpWaterUnitsPerCycle());
        PumpSnapshot snapshot = new PumpSnapshot();
        snapshot.capture(level, List.of(machine), 0);
        new PumpScratch().simulate(snapshot);
        snapshot.commit(level);
        return snapshot.moved[0];
    }

    /** A formed pump produces one full-width current field, even when only one lane supplied water. */
    static void spreadAxialCurrent(ServerLevel level, PumpStructure pump, Map<BlockPos, Vec3> currents) {
        Vec3 axis = pump.facing().getUnitVec3();
        var strengthByPlane = new HashMap<Integer, Double>();
        currents.forEach((pos, units) -> {
            double strength = units.dot(axis);
            if (strength > 0) {
                strengthByPlane.merge(PumpStructure.coordinate(pos, pump.facing()), strength, Math::max);
            }
        });
        int originPlane = PumpStructure.coordinate(pump.origin(), pump.facing());
        strengthByPlane.forEach((plane, strength) -> {
            int offset = plane - originPlane;
            for (int x = 0; x < pump.size(); x++) for (int y = 0; y < pump.size(); y++) {
                BlockPos pos = pump.cell(x, y).relative(pump.facing(), offset);
                if (!openAxialLane(level, pump, x, y, offset)) continue;
                Vec3 existing = currents.getOrDefault(pos, Vec3.ZERO);
                double existingAxial = existing.dot(axis);
                if (existingAxial < strength) {
                    currents.put(pos, existing.add(axis.scale(strength - existingAxial)));
                }
            }
        });
    }

    private static boolean openAxialLane(ServerLevel level, PumpStructure pump, int x, int y, int offset) {
        BlockPos previous = pump.cell(x, y);
        Direction direction = offset < 0 ? pump.facing().getOpposite() : pump.facing();
        for (int distance = 1; distance <= Math.abs(offset); distance++) {
            BlockPos pos = pump.cell(x, y).relative(direction, distance);
            if (!level.hasChunkAt(pos) || FiniteWaterPhysics.getWaterLevel(level, pos) < 0) return false;
            boolean open = offset < 0
                    ? FiniteWaterPhysics.canFlowBetween(level, pos, previous)
                    : FiniteWaterPhysics.canFlowBetween(level, previous, pos);
            if (!open) return false;
            previous = pos;
        }
        return true;
    }

    private static List<BlockPos> intakePassage(ServerLevel level, PumpStructure intake) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < intake.size(); x++) for (int y = 0; y < intake.size(); y++) {
            cells.addAll(intakeLane(level, intake, x, y));
        }
        return cells;
    }

    private static List<BlockPos> intakeLane(ServerLevel level, PumpStructure intake, int x, int y) {
        var cells = new ArrayList<BlockPos>(INTAKE_REACH);
        BlockPos previous = intake.cell(x, y);
        for (int distance = 1; distance <= INTAKE_REACH; distance++) {
            BlockPos pos = intake.cell(x, y).relative(intake.facing().getOpposite(), distance);
            if (!level.hasChunkAt(pos) || FiniteWaterPhysics.getWaterLevel(level, pos) < 0
                    || !FiniteWaterPhysics.canFlowBetween(level, pos, previous)) break;
            cells.add(pos);
            previous = pos;
        }
        return cells;
    }

    static Vec3 guidedCurrent(Entity entity, PumpStructure pump, Vec3 units) {
        Vec3 axis = pump.facing().getUnitVec3();
        Vec3 center = Vec3.atCenterOf(pump.origin())
                .add(PumpStructure.right(pump.facing()).getUnitVec3().scale((pump.size() - 1) / 2.0))
                .add(PumpStructure.up(pump.facing()).getUnitVec3().scale((pump.size() - 1) / 2.0));
        return guidedCurrent(entity, axis, center, PumpGeometry.openingRadius(pump.size()), units);
    }

    static Vec3 guidedCurrent(Entity entity, PumpAssembly machine, Vec3 units) {
        return guidedCurrent(entity, machine.axis, machine.center, machine.openingRadius, units);
    }

    private static Vec3 guidedCurrent(Entity entity, Vec3 axis, Vec3 center, double openingRadius, Vec3 units) {
        var bounds = entity.getBoundingBox();
        Vec3 offset = bounds.getCenter().subtract(center);
        Vec3 radial = offset.subtract(axis.scale(offset.dot(axis)));
        Vec3 half = new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()).scale(.5);
        double axialHalf = Math.abs(half.dot(axis));
        double clearance = Math.max(.02, openingRadius
                - Math.sqrt(half.lengthSqr() - axialHalf * axialHalf) - .04);
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 radialVelocity = velocity.subtract(axis.scale(velocity.dot(axis)));
        Vec3 correction = radial.scale(-8).subtract(radialVelocity.scale(20));
        double axialUnits = units.dot(axis);
        if (radial.lengthSqr() > clearance * clearance) axialUnits *= .15;
        return axis.scale(axialUnits).add(correction);
    }

    /** The housing seals its sides; a powered rotor also prevents reverse axial flow. */
    static boolean blocksFlow(BlockState state, Direction movement) {
        if (!(state.getBlock() instanceof WaterPumpBlock)) return false;
        Direction facing = state.getValue(WaterPumpBlock.FACING);
        return movement.getAxis() != facing.getAxis()
                || state.getValue(WaterPumpBlock.POWERED) && movement != facing;
    }
}
