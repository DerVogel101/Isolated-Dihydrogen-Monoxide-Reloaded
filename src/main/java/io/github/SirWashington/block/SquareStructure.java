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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Complete squares in the outlet plane, tiled largest first when assemblies touch. */
public record SquareStructure(BlockPos origin, int size, Direction facing) {
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

    public static SquareStructure find(BlockGetter level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        Direction facing = level.getBlockState(pos).getValue(BlockStateProperties.FACING);
        SquareStructure isolated = new SquareStructure(pos, 1, facing);
        Direction right = right(facing), up = up(facing);
        Set<BlockPos> remaining = new HashSet<>();
        var queue = new ArrayList<BlockPos>();
        remaining.add(pos); queue.add(pos);
        for (int i = 0; i < queue.size(); i++) {
            for (Direction direction : new Direction[]{right, right.getOpposite(), up, up.getOpposite()}) {
                BlockPos next = queue.get(i).relative(direction);
                if (matches(level, next, facing, block) && !remaining.contains(next)) {
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
                SquareStructure stage = new SquareStructure(origin, size, facing);
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

    private static boolean matches(BlockGetter level, BlockPos pos, Direction facing, Block block) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(block) && state.getValue(BlockStateProperties.FACING) == facing;
    }

    public boolean powered(Level level) {
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            if (level.hasNeighborSignal(cell(x, y))) return true;
        }
        return false;
    }

}