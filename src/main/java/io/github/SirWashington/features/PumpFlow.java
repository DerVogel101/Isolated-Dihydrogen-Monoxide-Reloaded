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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.BiPredicate;

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
        maintainPassageCurrent(level, poweredStages);
        transfer(level, poweredStages,
                scaledLimit(WaterPhysicsConfig.pumpMaxDepth(), end - first),
                scaledLimit(WaterPhysicsConfig.pumpMaxVisitedWaterCells(), end - first));
    }

    /** Keep suction through a wet, running segment steady even when a cycle only primes or cannot discharge. */
    private static void maintainPassageCurrent(ServerLevel level, List<PumpStructure> stages) {
        PumpStructure intake = stages.getFirst();
        Direction facing = intake.facing();
        List<BlockPos> intakePassage = intakePassage(level, intake);
        boolean wet = intakePassage.stream().anyMatch(pos -> FiniteWaterPhysics.getWaterLevel(level, pos) > 0);
        for (int x = 0; x < intake.size() && !wet; x++) for (int y = 0; y < intake.size() && !wet; y++) {
            for (PumpStructure stage : stages) {
                wet |= FiniteWaterPhysics.getWaterLevel(level, stage.cell(x, y)) > 0;
            }
        }
        if (!wet) return;
        Vec3 units = facing.getUnitVec3().scale(Math.min(8, WaterPhysicsConfig.pumpWaterUnitsPerCycle()));
        intakePassage.forEach(pos -> FiniteWaterPhysics.applyPumpCurrent(level, pos, units, intake));
        for (int x = 0; x < intake.size(); x++) for (int y = 0; y < intake.size(); y++) {
            for (PumpStructure stage : stages) {
                FiniteWaterPhysics.applyPumpCurrent(level, stage.cell(x, y), units, intake);
            }
        }
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
        PumpStructure intake = stages.getFirst();
        Direction facing = intake.facing();
        int outletPlane = PumpStructure.coordinate(stages.getLast().origin(), facing);
        int budget = scaledLimit(WaterPhysicsConfig.pumpWaterUnitsPerCycle(), intake.size() * intake.size());
        int primed = stages.size() > 1 ? primeSeries(level, stages, budget) : 0;
        if (primed == budget) return true;
        Set<BlockPos> duct = new HashSet<>();
        List<BlockPos> sources = new ArrayList<>();
        List<BlockPos> intakePassage = intakePassage(level, intake);
        // Draw from the intake first so a supplied series retains the water already in its duct.
        intakePassage.stream().filter(pos -> FiniteWaterPhysics.getWaterLevel(level, pos) > 0).forEach(sources::add);
        for (PumpStructure stage : stages) {
            for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
                BlockPos pos = stage.cell(x, y);
                duct.add(pos);
                if (FiniteWaterPhysics.getWaterLevel(level, pos) > 0) sources.add(pos);
            }
        }
        if (sources.isEmpty()) return primed > 0;
        Set<BlockPos> reserved = new HashSet<>(duct);
        reserved.addAll(intakePassage);
        reserved.addAll(sources);
        var retained = new HashMap<BlockPos, Integer>();
        var volumes = new HashMap<BlockPos, Integer>();
        int remaining = budget - primed;
        for (BlockPos source : sources) {
            int amount = FiniteWaterPhysics.getWaterLevel(level, source);
            int moved = Math.min(amount, remaining);
            if (moved > 0) {
                volumes.put(source, moved);
                retained.put(source, amount - moved);
                remaining -= moved;
            }
        }
        sources.removeIf(pos -> !volumes.containsKey(pos));
        // The intake-to-outlet graph is directed: visit upstream sources before downstream ones.
        sources.sort(java.util.Comparator.comparingInt(pos -> PumpStructure.coordinate(pos, facing)));
        var currents = new HashMap<BlockPos, Vec3>();
        BiPredicate<BlockPos, BlockPos> passage = (from, to) -> {
            // An independent powered pump is a pressure boundary, including pumps facing forward.
            BlockState target = level.getBlockState(to);
            if (!duct.contains(to) && target.getBlock() instanceof WaterPumpBlock
                    && target.getValue(WaterPumpBlock.POWERED)) return false;
            if (PumpStructure.coordinate(from, facing) <= outletPlane) {
                if (!to.equals(from.relative(facing))) return false;
            } else if (PumpStructure.coordinate(to, facing) <= outletPlane) return false;
            return FiniteWaterPhysics.canFlowBetween(level, from, to);
        };
        var plan = WaterPhysicsConfig.pumpStraightOnly()
                ? straightPlan(level, sources, volumes, reserved, facing, depth, visits, passage, currents)
                : SpecialFlow.planPush(sources, facing,
                pos -> volumes.containsKey(pos) ? volumes.get(pos) : FiniteWaterPhysics.getWaterLevel(level, pos),
                depth, visits, level::hasChunkAt,
                reserved, currents,
                passage, pos -> FiniteWaterPhysics.getWaterCapacity(level, pos), reserved);
        if (plan == null) return primed > 0;
        retained.forEach((pos, amount) -> plan.merge(pos, amount, Integer::sum));
        SpecialFlow.applyPlan(level, plan);
        // A merged pressure component may choose a downstream source's shorter discharge path.
        // Retain suction along every intake path that actually supplied water as well.
        volumes.forEach((source, amount) -> {
            Vec3 units = Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(amount);
            for (BlockPos pos = source; PumpStructure.coordinate(pos, facing) <= outletPlane; pos = pos.relative(facing)) {
                currents.merge(pos, units, FiniteWaterPhysics::combineCurrentUnits);
            }
        });
        spreadAxialCurrent(level, intake, currents);
        boolean muted = intake.muted(level);
        currents.forEach((pos, units) -> FiniteWaterPhysics.applyPumpCurrent(level, pos, units,
                PumpStructure.coordinate(pos, facing) <= outletPlane ? intake : null, muted));
        return true;
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

    /** Fill internal gaps from upstream before discharging; never invent water to keep a duct full. */
    private static int primeSeries(ServerLevel level, List<PumpStructure> stages, int budget) {
        PumpStructure intake = stages.getFirst();
        Direction facing = intake.facing();
        var plan = new HashMap<BlockPos, Integer>();
        var currents = new HashMap<BlockPos, Vec3>();
        int remaining = budget;
        for (int end = stages.size() - 1; end >= 0; end--) {
            for (int x = 0; x < intake.size(); x++) for (int y = 0; y < intake.size(); y++) {
                BlockPos target = stages.get(end).cell(x, y);
                int current = plan.getOrDefault(target, FiniteWaterPhysics.getWaterLevel(level, target));
                if (current < 0 || current >= 8 || remaining == 0) continue;
                // Only fresh intake water primes a gap. Moving water between rotor cells
                // simply relocates the gap and can spend every cycle on internal circulation.
                for (BlockPos source : intakeLane(level, intake, x, y)) {
                    int available = plan.getOrDefault(source, FiniteWaterPhysics.getWaterLevel(level, source));
                    if (available <= 0) continue;
                    var path = new ArrayList<BlockPos>();
                    path.add(source);
                    boolean open = true;
                    while (!path.getLast().equals(target)) {
                        BlockPos next = path.getLast().relative(facing);
                        if (!FiniteWaterPhysics.canFlowBetween(level, path.getLast(), next)) { open = false; break; }
                        path.add(next);
                    }
                    if (!open) continue;
                    int moved = Math.min(remaining, Math.min(8 - current, available));
                    if (moved <= 0) continue;
                    plan.put(source, available - moved);
                    current += moved; remaining -= moved;
                    plan.put(target, current);
                    SpecialFlow.recordFlowTransfers(currents, path, moved);
                    if (current == 8 || remaining == 0) break;
                }
            }
        }
        if (currents.isEmpty()) return 0;
        SpecialFlow.applyPlan(level, plan);
        currents.forEach((pos, units) -> FiniteWaterPhysics.applyPumpCurrent(level, pos, units, intake));
        return budget - remaining;
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

    /** Linear scans only: no queue, branching or BFS allocation when straight_only is enabled. */
    private static Map<BlockPos, Integer> straightPlan(ServerLevel level, List<BlockPos> sources,
            Map<BlockPos, Integer> volumes, Set<BlockPos> reserved, Direction facing, int depth, int visits,
            BiPredicate<BlockPos, BlockPos> passage, Map<BlockPos, Vec3> currents) {
        var plan = new HashMap<BlockPos, Integer>();
        sources.forEach(pos -> plan.put(pos, 0));
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos source : sources) {
            if (visited.add(source) && visited.size() > visits) return null;
            int remaining = volumes.get(source);
            var path = new ArrayList<BlockPos>();
            path.add(source);
            for (int step = 1; step <= depth && remaining > 0; step++) {
                BlockPos next = path.getLast().relative(facing);
                if (!level.hasChunkAt(next) || !passage.test(path.getLast(), next)) break;
                int water = FiniteWaterPhysics.getWaterLevel(level, next);
                if (water < 0) break;
                if (water > 0 && visited.add(next) && visited.size() > visits) return null;
                path.add(next);
                if (reserved.contains(next)) continue;
                int current = plan.getOrDefault(next, water);
                int moved = Math.min(remaining, FiniteWaterPhysics.getWaterCapacity(level, next) - current);
                if (moved <= 0) continue;
                plan.put(next, current + moved); remaining -= moved;
                SpecialFlow.recordFlowTransfers(currents, path, moved);
            }
            if (remaining > 0) return null;
        }
        return plan;
    }

    static Vec3 guidedCurrent(Entity entity, PumpStructure pump, Vec3 units) {
        Vec3 axis = pump.facing().getUnitVec3();
        Vec3 center = Vec3.atCenterOf(pump.origin())
                .add(PumpStructure.right(pump.facing()).getUnitVec3().scale((pump.size() - 1) / 2.0))
                .add(PumpStructure.up(pump.facing()).getUnitVec3().scale((pump.size() - 1) / 2.0));
        var bounds = entity.getBoundingBox();
        Vec3 offset = bounds.getCenter().subtract(center);
        Vec3 radial = offset.subtract(axis.scale(offset.dot(axis)));
        Vec3 half = new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()).scale(.5);
        double axialHalf = Math.abs(half.dot(axis));
        double clearance = Math.max(.02, PumpGeometry.openingRadius(pump.size())
                - Math.sqrt(half.lengthSqr() - axialHalf * axialHalf) - .04);
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 radialVelocity = velocity.subtract(axis.scale(velocity.dot(axis)));
        Vec3 correction = radial.scale(-8).subtract(radialVelocity.scale(20));
        double axialUnits = units.dot(axis);
        if (radial.lengthSqr() > clearance * clearance) axialUnits *= .15;
        return axis.scale(axialUnits).add(correction);
    }

    /** The powered rotor acts as a check valve for all finite-water transfer paths. */
    static boolean blocksBackflow(BlockState state, Direction movement) {
        return state.getBlock() instanceof WaterPumpBlock && state.getValue(WaterPumpBlock.POWERED)
                && movement == state.getValue(WaterPumpBlock.FACING).getOpposite();
    }
}
