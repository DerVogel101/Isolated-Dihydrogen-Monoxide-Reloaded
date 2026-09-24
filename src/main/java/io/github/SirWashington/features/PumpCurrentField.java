package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.PumpStructure;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** One spatial entity query per machine, with compact exceptions for bent pressure paths. */
final class PumpCurrentField {
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<PumpCurrentField>> ACTIVE = new WeakHashMap<>();
    final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
    private PumpAssembly machine;
    private AABB bounds;
    private long expires;
    private long[] starts = new long[16];
    private int[] lengths = new int[16], strengths = new int[16];
    private int runCount;

    void clear() { cells.clear(); }
    static int pack(int x, int y, int z) { return (x & 255) | ((y & 255) << 8) | ((z & 255) << 16); }
    static int x(int packed) { return (byte) packed; }
    static int y(int packed) { return (byte) (packed >> 8); }
    static int z(int packed) { return (byte) (packed >> 16); }
    static int combine(int first, int second) {
        return pack(combineAxis(x(first), x(second)), combineAxis(y(first), y(second)), combineAxis(z(first), z(second)));
    }
    private static int combineAxis(int a, int b) {
        return Integer.signum(a) == Integer.signum(b) ? (Math.abs(a) >= Math.abs(b) ? a : b) : Math.clamp(a + b, -8, 8);
    }
    void add(long pos, Direction direction, int amount) {
        amount = Math.min(8, amount);
        add(pos, pack(direction.getStepX() * amount, direction.getStepY() * amount, direction.getStepZ() * amount));
    }
    void add(long pos, int units) { cells.put(pos, combine(cells.get(pos), units)); }
    void merge(PumpCurrentField other) {
        var entries = other.cells.long2IntEntrySet().fastIterator();
        while (entries.hasNext()) { var entry = entries.next(); add(entry.getLongKey(), entry.getIntValue()); }
    }

    void publish(ServerLevel level, PumpAssembly machine) {
        if (cells.isEmpty()) return;
        if (!machine.muted) {
            var entries = cells.long2IntEntrySet().fastIterator();
            while (entries.hasNext()) {
                var entry = entries.next(); int units = entry.getIntValue();
                FiniteWaterSounds.record(level, BlockPos.of(entry.getLongKey()), Math.abs(x(units)) + Math.abs(y(units)) + Math.abs(z(units)));
            }
        }
        if (!WaterPhysicsConfig.currentsEnabled()) return;
        var fields = ACTIVE.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        PumpCurrentField target = fields.computeIfAbsent(machine.controller(), ignored -> new PumpCurrentField());
        target.clear(); target.merge(this); target.machine = machine;
        // A worker that finishes just after START_LEVEL_TICK publishes on the following start.
        target.expires = level.getGameTime() + WaterPhysicsConfig.pumpTickInterval() + 1;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long pos : target.cells.keySet()) {
            minX = Math.min(minX, BlockPos.getX(pos)); maxX = Math.max(maxX, BlockPos.getX(pos));
            minY = Math.min(minY, BlockPos.getY(pos)); maxY = Math.max(maxY, BlockPos.getY(pos));
            minZ = Math.min(minZ, BlockPos.getZ(pos)); maxZ = Math.max(maxZ, BlockPos.getZ(pos));
        }
        target.bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        target.compress();
        target.cells.clear();
    }

    private void compress() {
        runCount = 0;
        var entries = cells.long2IntEntrySet().fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next(); long start = entry.getLongKey(); int strength = entry.getIntValue();
            if (strength == 0) continue;
            long previous = BlockPos.offset(start, machine.facing.getOpposite());
            boolean guided = machine.plane(start) <= machine.outletPlane;
            if (cells.get(previous) == strength && (machine.plane(previous) <= machine.outletPlane) == guided) continue;
            int length = 1;
            for (long next = BlockPos.offset(start, machine.facing); cells.get(next) == strength
                    && (machine.plane(next) <= machine.outletPlane) == guided; next = BlockPos.offset(next, machine.facing)) length++;
            if (runCount == starts.length) {
                starts = java.util.Arrays.copyOf(starts, runCount * 2);
                lengths = java.util.Arrays.copyOf(lengths, runCount * 2);
                strengths = java.util.Arrays.copyOf(strengths, runCount * 2);
            }
            starts[runCount] = start; lengths[runCount] = length; strengths[runCount++] = strength;
        }
    }

    static void collect(ServerLevel level, Map<Entity, Vec3> currents, Set<Entity> guided) {
        var fields = ACTIVE.get(level);
        if (fields == null) return;
        if (!WaterPhysicsConfig.currentsEnabled()) { ACTIVE.remove(level); return; }
        var iterator = fields.values().iterator();
        while (iterator.hasNext()) {
            PumpCurrentField field = iterator.next();
            if (field.expires <= level.getGameTime()) { iterator.remove(); continue; }
            for (Entity entity : level.getEntities((Entity) null, field.bounds, Entity::isPushedByFluid)) {
                AABB box = entity.getBoundingBox();
                for (int run = 0; run < field.runCount; run++) {
                            long pos = field.starts[run];
                            int distance = field.lengths[run] - 1;
                            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
                            int endX = x + field.machine.facing.getStepX() * distance;
                            int endY = y + field.machine.facing.getStepY() * distance;
                            int endZ = z + field.machine.facing.getStepZ() * distance;
                            if (!box.intersects(Math.min(x, endX), Math.min(y, endY), Math.min(z, endZ),
                                    Math.max(x, endX) + 1, Math.max(y, endY) + 1, Math.max(z, endZ) + 1)) continue;
                            int units = field.strengths[run];
                            Vec3 vector = new Vec3(x(units), y(units), z(units));
                            if (field.machine.plane(pos) <= field.machine.outletPlane) {
                                guided.add(entity); vector = PumpFlow.guidedCurrent(entity, field.machine, vector);
                            }
                            currents.merge(entity, vector, FiniteWaterPhysics::combineCurrentUnits);
                }
            }
        }
        if (fields.isEmpty()) ACTIVE.remove(level);
    }

    static void clearAll() { ACTIVE.clear(); }
}
