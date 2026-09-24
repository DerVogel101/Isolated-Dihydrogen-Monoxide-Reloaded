package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;

final class PumpOptimizationChecks {
    private static final BlockPos POS = new BlockPos(8, 270, 8);

    static void run(ServerLevel level) throws Exception {
        long time = level.getGameTime();
        try {
            for (Direction facing : Direction.values()) {
                clear(level);
                var shape = new PumpStructure(POS, 3, facing);
                place(level, shape);
                PumpStructure cached = PumpStructure.find(level, POS);
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                    expect(PumpStructure.find(level, shape.cell(x, y)) == cached, "Constituents share structure");
                }
                var oracle = SquareStructure.find(level, POS);
                expect(cached.origin().equals(oracle.origin()) && cached.size() == oracle.size(), "Original tiling");
                level.setBlock(shape.cell(1, 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                expect(!PumpStructure.find(level, POS).equals(cached), "Same-tick removal invalidates topology");
                place(level, shape);
                cached = PumpStructure.find(level, POS);
                level.setBlock(POS, level.getBlockState(POS).setValue(WaterPumpBlock.FACING, facing.getOpposite()), Block.UPDATE_CLIENTS);
                expect(PumpStructure.find(level, POS).facing() == facing.getOpposite(), "Same-tick rotation invalidates topology");
                place(level, shape);
                cached = PumpStructure.find(level, POS);
                ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
                expect(PumpStructure.find(level, POS) == cached, "Topology survives unchanged ticks");
            }
            clear(level);
            for (int i = 0; i < 3; i++) place(level, new PumpStructure(POS.above(i), 1, Direction.UP));
            PumpStructure first = PumpStructure.find(level, POS);
            expect(first.series(level).size() == 3, "Series assembled");
            expect(first.series(level) == PumpStructure.find(level, POS.above()).series(level), "Series shared across stages");
            level.setBlock(POS.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            expect(PumpStructure.find(level, POS).series(level).size() == 2, "Series removal invalidates immediately");
            benchmark(level);
        } finally {
            clear(level);
            ((ServerLevelData) level.getLevelData()).setGameTime(time);
            PumpStructure.invalidate(level);
        }
        System.out.println("PUMP_OPTIMIZATION_REGRESSION_PASS");
    }

    private static void benchmark(ServerLevel level) {
        clear(level);
        var shape = new PumpStructure(POS, 3, Direction.NORTH);
        place(level, shape);
        long[] original = new long[3], cached = new long[3];
        for (int run = -2; run < 3; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) SquareStructure.find(level, shape.cell(i % 3, i / 3 % 3));
            long originalNanos = System.nanoTime() - start;
            start = System.nanoTime();
            for (int i = 0; i < 1000; i++) PumpStructure.find(level, shape.cell(i % 3, i / 3 % 3));
            long cachedNanos = System.nanoTime() - start;
            if (run >= 0) { original[run] = originalNanos; cached[run] = cachedNanos; }
        }
        java.util.Arrays.sort(original);
        java.util.Arrays.sort(cached);
        System.out.println("PUMP_TOPOLOGY_BENCH original_ns=" + original[1] / 1000 + " cached_ns=" + cached[1] / 1000);
    }

    private static void place(ServerLevel level, PumpStructure shape) {
        for (int x = 0; x < shape.size(); x++) for (int y = 0; y < shape.size(); y++) {
            level.setBlock(shape.cell(x, y), ModBlocks.WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, shape.facing()), Block.UPDATE_CLIENTS);
        }
    }

    private static void clear(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(POS.offset(-4, -4, -4), POS.offset(4, 4, 4))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
