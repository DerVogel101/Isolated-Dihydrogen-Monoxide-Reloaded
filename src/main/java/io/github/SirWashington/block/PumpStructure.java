package io.github.SirWashington.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/** Complete squares in the outlet plane, tiled largest first when assemblies touch. */
public record PumpStructure(BlockPos origin, int size, Direction facing) {
    public static Direction right(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            default -> Direction.EAST;
        };
    }

    public static Direction up(Direction facing) {
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    public static int coordinate(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getStepX() + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }

    public BlockPos cell(int x, int y) {
        return origin.relative(right(facing), x).relative(up(facing), y);
    }

    public static PumpStructure find(BlockGetter level, BlockPos pos) {
        Direction facing = level.getBlockState(pos).getValue(WaterPumpBlock.FACING);
        PumpStructure isolated = new PumpStructure(pos, 1, facing);
        Direction right = right(facing), up = up(facing);
        Set<BlockPos> remaining = new HashSet<>();
        var queue = new ArrayList<BlockPos>();
        remaining.add(pos); queue.add(pos);
        for (int i = 0; i < queue.size(); i++) {
            for (Direction direction : new Direction[]{right, right.getOpposite(), up, up.getOpposite()}) {
                BlockPos next = queue.get(i).relative(direction);
                if (matches(level, next, facing) && !remaining.contains(next)) {
                    // Bound formation work on oversized walls; do not load chunks to complete a square.
                    if (remaining.size() >= 1024) return isolated;
                    remaining.add(next); queue.add(next);
                }
            }
        }
        var order = java.util.Comparator.<BlockPos>comparingInt(p -> coordinate(p, up))
                .thenComparingInt(p -> coordinate(p, right));
        queue.sort(order);
        for (int size = 3; size >= 1; size--) {
            for (BlockPos origin : queue) {
                if (!remaining.contains(origin)) continue;
                PumpStructure stage = new PumpStructure(origin, size, facing);
                boolean complete = true;
                for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                    complete &= remaining.contains(stage.cell(x, y));
                }
                if (!complete) continue;
                boolean contains = false;
                for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                    BlockPos member = stage.cell(x, y);
                    remaining.remove(member);
                    contains |= member.equals(pos);
                }
                if (contains) return stage;
            }
        }
        return isolated;
    }

    private static boolean matches(BlockGetter level, BlockPos pos, Direction facing) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof WaterPumpBlock && state.getValue(WaterPumpBlock.FACING) == facing;
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
        int before = 0, after = 0;
        while (before < 3 && alignedStage(level, -before - 1)) before++;
        while (after < 3 && alignedStage(level, after + 1)) after++;
        if (before + after >= 3) return List.of(this);
        var stages = new ArrayList<PumpStructure>();
        for (int offset = -before; offset <= after; offset++) {
            stages.add(new PumpStructure(origin.relative(facing, offset), size, facing));
        }
        return List.copyOf(stages);
    }
}
