package io.github.SirWashington.features;

import com.sun.management.ThreadMXBean;
import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterPumpBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
            ServerLevel level = server.overworld();
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) level.setChunkForced(x, z, true);
            int[] ticks = {0};
            boolean[] done = {false};
            ServerTickEvents.END_SERVER_TICK.register(ticked -> {
                if (ticked != server || done[0]) return;
                boolean loaded = true;
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                    loaded &= level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(x, z));
                if (!loaded && ++ticks[0] < 200) return;
                done[0] = true;
                try {
                    if (!loaded) throw new AssertionError("Pump fixture chunks did not become entity-ticking");
                    run(level);
                    System.out.println("PUMP_PERFORMANCE_SERVER_TEST_PASS");
                } catch (Throwable failure) {
                    failure.printStackTrace();
                } finally {
                    server.halt(false);
                }
            });
        });
    }

    private static void run(ServerLevel level) throws InterruptedException {
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
        PumpManager manager = PumpManager.get(level);
        for (int run = -1; run < 3; run++) {
            long bytes = 0, units = 0;
            int rebuilds = 0;
            PumpManager.Group previousGroup = manager.groups.isEmpty() ? null : manager.groups.getFirst();
            for (int tick = 0; tick < times.length; tick++, clock++) {
                ((ServerLevelData) level.getLevelData()).setGameTime(clock);
                if (run == -1 && tick == 0) {
                    for (BlockPos p : cells) {
                        FiniteWaterPhysics.setWaterLevel(level, p.below(), 8);
                        FiniteWaterPhysics.setWaterLevel(level, p, 8);
                    }
                }
                long before = threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(thread) : 0;
                long start = System.nanoTime();
                manager.finish();
                long finishNanos = System.nanoTime() - start;
                long finishBytes = threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(thread) - before : 0;
                for (BlockPos p : cells) {
                    int output = FiniteWaterPhysics.getWaterLevel(level, p.above());
                    units += output;
                    if (output > 0) FiniteWaterPhysics.setWaterLevel(level, p.above(), 0);
                    if (FiniteWaterPhysics.getWaterLevel(level, p.below()) < 8) FiniteWaterPhysics.setWaterLevel(level, p.below(), 8);
                    if (FiniteWaterPhysics.getWaterLevel(level, p) < 8) FiniteWaterPhysics.setWaterLevel(level, p, 8);
                }
                before = threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(thread) : 0;
                start = System.nanoTime();
                manager.tick();
                if (!manager.groups.isEmpty() && manager.groups.getFirst() != previousGroup) { rebuilds++; previousGroup = manager.groups.getFirst(); }
                times[tick] = finishNanos + System.nanoTime() - start;
                if (threads.isThreadAllocatedMemorySupported()) bytes += finishBytes + threads.getThreadAllocatedBytes(thread) - before;
                Thread.sleep(50); // Pace synthetic ticks at 20 TPS.
            }
            if (units == 0) throw new AssertionError("Pump fixture transferred no water");
            if (manager.groups.size() != pumps.size() / 9) throw new AssertionError("Stone-isolated pumps must remain independent");
            if (run >= 0 && rebuilds != 0) throw new AssertionError("Water transfer rebuilt stable pump topology");
            if (run >= 0) {
                Arrays.sort(times);
                System.out.println("PUMP_PERFORMANCE run=" + run + " blocks=" + pumps.size()
                        + " p95_ns=" + times[569] + " p99_ns=" + times[593] + " peak_ns=" + times[599]
                        + " allocated_bytes_per_tick=" + bytes / times.length + " transferred_units=" + units
                        + " expected_units=" + (long) cells.size() * 8 * times.length / interval
                        + " groups=" + manager.groups.size() + " rebuilds=" + rebuilds
                        + " snapshot_cells=" + manager.groups.stream().mapToInt(g -> g.buffers[0].size + g.buffers[1].size).sum());
            }
        }
    }
}
