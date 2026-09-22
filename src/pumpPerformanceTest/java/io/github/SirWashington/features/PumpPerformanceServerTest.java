package io.github.SirWashington.features;

import com.sun.management.ThreadMXBean;
import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterPumpBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;

/** Isolated synchronous fixture: restoration is outside the timed pump callbacks. */
public final class PumpPerformanceServerTest implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                run(server.overworld());
                System.out.println("PUMP_PERFORMANCE_SERVER_TEST_PASS");
            } catch (Throwable failure) {
                failure.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void run(ServerLevel level) {
        var cells = new ArrayList<BlockPos>();
        var pumps = new ArrayList<WaterPumpBlockEntity>();
        BlockPos base = new BlockPos(0, 270, 0);
        int spacing = Integer.getInteger("immersivefluids.pumpSpacing", 5);
        if (spacing < 5 || spacing > 160) throw new AssertionError("Fixture spacing must be 5..160");
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            var shape = new PumpStructure(base.offset(x * spacing, 0, z * spacing), 3, Direction.UP);
            for (BlockPos p : BlockPos.betweenClosed(shape.origin().offset(-1, -2, -3), shape.origin().offset(3, 2, 1))) {
                level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
                BlockPos p = shape.cell(a, b);
                cells.add(p);
                level.setBlock(p.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(p, ModBlocks.WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
                pumps.add((WaterPumpBlockEntity) level.getBlockEntity(p));
            }
            level.setBlock(shape.origin().west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        int interval = WaterPhysicsConfig.pumpTickInterval();
        if (interval != 20) throw new AssertionError("Fixture expects the default 20-tick pump interval");
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threads.isThreadAllocatedMemorySupported()) threads.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long[] times = new long[600];
        long initialTime = level.getGameTime();
        long clock = initialTime - Math.floorMod(initialTime, interval) + interval;
        for (int run = -1; run < 3; run++) {
            long bytes = 0, units = 0;
            for (int tick = 0; tick < times.length; tick++, clock++) {
                ((ServerLevelData) level.getLevelData()).setGameTime(clock);
                if (clock % interval == 0) {
                    for (BlockPos p : cells) {
                        FiniteWaterPhysics.setWaterLevel(level, p.below(), 8);
                        FiniteWaterPhysics.setWaterLevel(level, p, 8);
                        FiniteWaterPhysics.setWaterLevel(level, p.above(), 0);
                    }
                }
                long before = threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(thread) : 0;
                long start = System.nanoTime();
                for (var pump : pumps) {
                    WaterPumpBlockEntity.tick(level, pump.getBlockPos(), pump.getBlockState(), pump);
                }
                times[tick] = System.nanoTime() - start;
                if (threads.isThreadAllocatedMemorySupported()) bytes += threads.getThreadAllocatedBytes(thread) - before;
                if (clock % interval == interval - 1) {
                    for (BlockPos p : cells) units += FiniteWaterPhysics.getWaterLevel(level, p.above());
                }
            }
            if (units != (long) cells.size() * 8 * times.length / interval) {
                throw new AssertionError("Unexpected pump throughput: " + units);
            }
            if (run >= 0) {
                Arrays.sort(times);
                System.out.println("PUMP_PERFORMANCE run=" + run + " blocks=" + pumps.size()
                        + " p95_ns=" + times[569] + " p99_ns=" + times[593] + " peak_ns=" + times[599]
                        + " allocated_bytes_per_tick=" + bytes / times.length + " transferred_units=" + units);
            }
        }
    }
}
