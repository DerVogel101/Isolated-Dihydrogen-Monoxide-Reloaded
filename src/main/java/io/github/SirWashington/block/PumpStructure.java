package io.github.SirWashington.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;

/** Complete squares in the outlet plane, tiled largest first when assemblies touch. */
public record PumpStructure(BlockPos origin, int size, Direction facing) {
    private static final Map<ServerLevel, TickCache> CACHES = new WeakHashMap<>();

    /** Server-thread only; live water and redstone are deliberately not cached. */
    private static TickCache cache(ServerLevel level) {
        TickCache cache = CACHES.computeIfAbsent(level, ignored -> new TickCache());
        if (cache.tick != level.getGameTime()) {
            cache.structures.clear();
            cache.series.clear();
            cache.tick = level.getGameTime();
        }
        return cache;
    }

    public static void invalidate(Level level) {
        if (level instanceof ServerLevel server) CACHES.remove(server);
    }

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> invalidate(level));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> invalidate(level));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register(
                (level, chunk, before, after) -> invalidate(level));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHES.clear());
    }

    private static final class TickCache {
        private long tick = Long.MIN_VALUE;
        private final Long2ObjectOpenHashMap<PumpStructure> structures = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<List<PumpStructure>> series = new Long2ObjectOpenHashMap<>();
    }
    public static Direction right(Direction facing) {
        return SquareStructure.right(facing);
    }

    public static Direction up(Direction facing) {
        return SquareStructure.up(facing);
    }

    public static int coordinate(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getStepX() + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }

    public BlockPos cell(int x, int y) {
        return origin.relative(right(facing), x).relative(up(facing), y);
    }

    public static PumpStructure find(BlockGetter level, BlockPos pos) {
        TickCache cache = level instanceof ServerLevel server ? cache(server) : null;
        if (cache != null) {
            PumpStructure found = cache.structures.get(pos.asLong());
            if (found != null) return found;
        }
        SquareStructure square = SquareStructure.find(level, pos);
        PumpStructure found = new PumpStructure(square.origin().immutable(), square.size(), square.facing());
        if (cache != null) {
            if (cache.structures.size() + found.size * found.size > 4096) {
                cache.structures.clear();
                cache.series.clear();
            }
            for (int x = 0; x < found.size; x++) for (int y = 0; y < found.size; y++) {
                cache.structures.put(found.cell(x, y).asLong(), found);
            }
        }
        return found;
    }

    private boolean matches(BlockGetter level, BlockPos pos, Direction facing) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(level.getBlockState(origin).getBlock()) && state.getValue(WaterPumpBlock.FACING) == facing;
    }

    private boolean alignedStage(BlockGetter level, int distance) {
        BlockPos next = origin.relative(facing, distance);
        if (!matches(level, next, facing)) return false;
        PumpStructure stage = find(level, next);
        return stage.size == size && stage.origin.equals(next);
    }

    /** Bit 0: intake connection, bit 1: outlet connection. Overlong chains stay separate. */
    public int connections(BlockGetter level) {
        List<PumpStructure> stages = series(level);
        int index = stages.indexOf(this);
        return (index > 0 ? 1 : 0) | (index < stages.size() - 1 ? 2 : 0);
    }

    public boolean powered(Level level) {
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            if (level.hasNeighborSignal(cell(x, y))) return true;
        }
        return false;
    }

    public List<PumpStructure> series(BlockGetter level) {
        TickCache cache = level instanceof ServerLevel server ? cache(server) : null;
        if (cache != null) {
            List<PumpStructure> found = cache.series.get(origin.asLong());
            if (found != null && found.contains(this)) return found;
        }
        int before = 0, after = 0;
        while (before < 3 && alignedStage(level, -before - 1)) before++;
        while (after < 3 && alignedStage(level, after + 1)) after++;
        if (before + after >= 3) return List.of(this);
        var stages = new ArrayList<PumpStructure>();
        for (int offset = -before; offset <= after; offset++) {
            stages.add(new PumpStructure(origin.relative(facing, offset), size, facing));
        }
        List<PumpStructure> result = List.copyOf(stages);
        if (cache != null) {
            if (cache.series.size() + result.size() > 4096) cache.series.clear();
            for (PumpStructure stage : result) cache.series.put(stage.origin.asLong(), result);
        }
        return result;
    }

    public boolean muted(BlockGetter level) {
        return level.getBlockState(origin).getBlock() instanceof WaterPumpBlock pump && pump.muted();
    }
}
