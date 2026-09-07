package io.github.SirWashington.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Complete squares in the outlet plane, tiled largest first when assemblies touch. */
public record PumpStructure(BlockPos origin, int size, Direction facing) {
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
        SquareStructure square = SquareStructure.find(level, pos);
        return new PumpStructure(square.origin(), square.size(), square.facing());
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

    public boolean muted(BlockGetter level) {
        return level.getBlockState(origin).getBlock() instanceof WaterPumpBlock pump && pump.muted();
    }
}
