package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.DihydrogenMonoxideAssemblerBlock;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterValveBlock;
import io.github.SirWashington.block.WaterValveBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Real snapshots and controlled worker completion, without timing-dependent sleeps. */
final class PumpArchitectureChecks {
    private static final int Y = 270;
    private static final BlockPos FIRST = new BlockPos(4, Y, 8);
    static void run(ServerLevel level) {
 try { continuity(level, false); continuity(level, true); partialVertical(level); scheduling(level); generatorClockDoesNotRebuild(level); valveMovementDoesNotRebuild(level); rapidReplenishment(level); connectivity(level); }
        catch (Exception error) { throw new AssertionError("Architecture regression failed", error); }
        finally { clear(level); }
        System.out.println("PUMP_ARCHITECTURE_REGRESSION_PASS");
    }
    private static List<PumpAssembly> fixture(ServerLevel level, boolean bent) {
        clear(level);
        for (BlockPos pos : BlockPos.betweenClosed(2, Y - 1, 3, 14, Y + 1, 10))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        var machines = new ArrayList<PumpAssembly>();
        if (bent) {
            for (int x = 3; x <= 8; x++) level.setBlock(new BlockPos(x, Y, 4), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int z = 5; z <= 9; z++) level.setBlock(new BlockPos(8, Y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            machines.add(place(level, new BlockPos(4, Y, 4), Direction.EAST));
            machines.add(place(level, new BlockPos(8, Y, 8), Direction.SOUTH));
        } else {
            for (int x = 3; x <= 13; x++) level.setBlock(new BlockPos(x, Y, 8), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int x : new int[]{4, 8, 12}) machines.add(place(level, new BlockPos(x, Y, 8), Direction.EAST));
        }
        return machines;
    }
    private static PumpAssembly place(ServerLevel level, BlockPos pos, Direction facing) {
        var stage = new PumpStructure(pos, 1, facing);
        level.setBlock(pos, ModBlocks.MUTED_WATER_PUMP.defaultBlockState()
                .setValue(WaterPumpBlock.FACING, facing).setValue(WaterPumpBlock.POWERED, true), Block.UPDATE_CLIENTS);
        level.setBlock(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        return new PumpAssembly(List.of(stage), true, 16, 128, 8);
    }
    private static void continuity(ServerLevel level, boolean bent) {
        List<PumpAssembly> machines = fixture(level, bent);
        BlockPos source = bent ? new BlockPos(3, Y, 4) : FIRST.west();
        BlockPos outlet = bent ? new BlockPos(8, Y, 9) : new BlockPos(13, Y, 8);
        for (BlockPos pos : BlockPos.betweenClosed(2, Y, 3, 14, Y, 10))
            if (!pos.equals(outlet) && FiniteWaterPhysics.getWaterLevel(level, pos) == 0) FiniteWaterPhysics.setWaterLevel(level, pos, 8);
        PumpSnapshot snapshot = new PumpSnapshot(); PumpScratch scratch = new PumpScratch();
        for (int cycle = 0; cycle < 30; cycle++) {
            FiniteWaterPhysics.setWaterLevel(level, source, 8); FiniteWaterPhysics.setWaterLevel(level, outlet, 0);
            int before = total(level);
                snapshot.capture(level, machines, cycle, 0); scratch.simulate(snapshot);
            expect(total(level) == before, "Planning cannot mutate live world");
            expect(snapshot.valid(level), "Unchanged snapshot valid"); snapshot.commit(level);
            if (cycle == 0 && !bent) {
                var rabbit = EntityTypes.RABBIT.create(level, EntitySpawnReason.COMMAND);
                rabbit.setPos(6.5, Y + 0.1, 8.5);
                level.addFreshEntity(rabbit);
                try {
                    var currents = new HashMap<Entity, Vec3>();
                    PumpCurrentField.collect(level, currents, new HashSet<>());
                    expect(currents.containsKey(rabbit) && currents.get(rabbit).lengthSqr() > 0,
                            "Published pump field moves an entity");
                } finally {
                    rabbit.discard();
                    PumpCurrentField.clearAll();
                }
            }
            expect(total(level) == before, "Group conservation");
            for (int i = 1; i < machines.size(); i++) {
                PumpAssembly machine = machines.get(i);
                expect(FiniteWaterPhysics.getWaterLevel(level, machine.intake.origin().relative(machine.facing.getOpposite())) == 8,
                        "No downstream intake gap: bent=" + bent + " cycle=" + cycle);
            }
            expect(FiniteWaterPhysics.getWaterLevel(level, outlet) == 8, "One budget reaches outlet");
        }
            snapshot.capture(level, machines, 0);
        FiniteWaterPhysics.setWaterLevel(level, source, 1); expect(!snapshot.valid(level), "Water mutation invalidates snapshot");
            snapshot.capture(level, machines, 0);
        BlockPos pump = machines.getFirst().intake.origin();
        level.setBlock(pump, level.getBlockState(pump).setValue(WaterPumpBlock.FACING, Direction.WEST), Block.UPDATE_CLIENTS);
        expect(!snapshot.valid(level), "Rotation invalidates snapshot");
        System.out.println("PUMP_GROUP_CONTINUITY_PASS bent=" + bent + " cycles=30");
    }
    private static void partialVertical(ServerLevel level) {
        BlockPos source = new BlockPos(20, Y - 1, 8), lower = source.above(), middle = lower.above();
        BlockPos upper = middle.above(), outlet = upper.above();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(19, Y - 2, 7, 21, Y + 4, 9))
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (BlockPos pos : List.of(source, middle, outlet))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (BlockPos pos : List.of(lower, upper)) {
                level.setBlock(pos, (pos.equals(upper) ? ModBlocks.MUTED_WATER_PUMP : ModBlocks.WATER_PUMP).defaultBlockState()
                        .setValue(WaterPumpBlock.FACING, Direction.UP).setValue(WaterPumpBlock.POWERED, true), Block.UPDATE_CLIENTS);
                level.setBlock(pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            FiniteWaterPhysics.setWaterLevel(level, source, 3);
            FiniteWaterPhysics.setWaterLevel(level, middle, 2);
            var machines = List.of(
                    new PumpAssembly(List.of(new PumpStructure(lower, 1, Direction.UP)), false, 8, 64, 8),
                    new PumpAssembly(List.of(new PumpStructure(upper, 1, Direction.UP)), true, 8, 64, 8));
            PumpSnapshot snapshot = new PumpSnapshot();
            snapshot.capture(level, machines, 0, 0);
            new PumpScratch().simulate(snapshot);
            expect(snapshot.valid(level), "Partial vertical snapshot valid");
            snapshot.commit(level);
            expect(FiniteWaterPhysics.getWaterLevel(level, source) == 0
                    && FiniteWaterPhysics.getWaterLevel(level, middle) == 3
                    && FiniteWaterPhysics.getWaterLevel(level, outlet) == 2,
                    "Separated vertical pumps move partial water downstream without loss");
            System.out.println("PUMP_PARTIAL_VERTICAL_PASS");
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(19, Y - 2, 7, 21, Y + 4, 9))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    private static void connectivity(ServerLevel level) throws ReflectiveOperationException {
        long savedTime = level.getGameTime();
        try {
            clear(level);
            for (int chunk = 0; chunk <= 11; chunk++) level.getChunk(chunk, 0);
            for (BlockPos pos : BlockPos.betweenClosed(2, Y - 1, 7, 82, Y + 1, 9))
                level.setBlock(pos, pos.getY() == Y && pos.getZ() == 8 && pos.getX() > 2 && pos.getX() < 82
                        ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            place(level, FIRST, Direction.EAST); place(level, new BlockPos(60, Y, 8), Direction.EAST);
            place(level, new BlockPos(80, Y, 8), Direction.EAST);
            place(level, new BlockPos(180, Y, 8), Direction.EAST);
            for (int x = 3; x <= 80; x++) if (x != 40) FiniteWaterPhysics.setWaterLevel(level, new BlockPos(x, Y, 8), 8);
            PumpManager manager = PumpManager.get(level);
            tick(level, manager, savedTime + 500);
            expect(manager.groups.size() == 3 && manager.groups.stream().anyMatch(g -> g.machines.size() == 2),
                    "The broken conduit separates one pump from two connected downstream pumps");
            var dirty = PumpManager.class.getDeclaredField("dirty");
            dirty.setAccessible(true);
            expect(!dirty.getBoolean(manager), "Pump fixture begins with stable connectivity");
            expect(Blocks.REDSTONE_BLOCK.defaultBlockState().isSignalSource(), "Regression uses a signal source");
            PumpManager.changed(level, new BlockPos(4, Y, 15), Blocks.AIR.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            expect(!dirty.getBoolean(manager), "Remote redstone changes must not rebuild pump connectivity");
            System.out.println("PUMP_REMOTE_REDSTONE_NO_REBUILD_PASS");
            FiniteWaterPhysics.setWaterLevel(level, new BlockPos(40, Y, 8), 8);
            tick(level, manager, savedTime + 501);
            expect(manager.groups.size() == 2 && manager.groups.stream().anyMatch(g -> g.machines.size() == 3),
                    "Water joining a 76-block conduit merges hydraulic groups beyond pressure overlap");
            FiniteWaterPhysics.setWaterLevel(level, new BlockPos(40, Y, 8), 0);
            tick(level, manager, savedTime + 502);
            expect(manager.groups.size() == 2, "Temporary water gaps do not split dependent schedules");
            System.out.println("PUMP_LONG_CONDUIT_AND_INDEPENDENT_GROUPS_PASS");
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(2, Y - 1, 7, 82, Y + 1, 9)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(180, Y, 8), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(180, Y - 1, 8), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime);
        }
    }

    static void scheduling(ServerLevel level) throws Exception {
        long savedTime = level.getGameTime(); int savedInterval = WaterPhysicsConfig.pumpTickInterval();
        var workers = PumpManager.class.getDeclaredField("workers"); workers.setAccessible(true);
        ExecutorService originalWorkers = (ExecutorService) workers.get(null);
        ControlledExecutor controlled = new ControlledExecutor();
        try {
            workers.set(null, controlled); fixture(level, false);
            PumpManager manager = PumpManager.get(level); long time = savedTime + 1000;
            for (int i = 0; i < 40 && controlled.pending.isEmpty(); i++) tick(level, manager, time++);
            expect(controlled.pending.size() == 1, "One job per connected group");
            PumpManager.Group group = manager.groups.stream().filter(g -> g.machines.stream().anyMatch(m -> m.controller() == FIRST.asLong())).findFirst().orElseThrow();
            expect(group.machines.size() == 3 && !group.job.isDone(), "Server returns without waiting");
            long start = group.buffers[group.buffer].started;
            for (int i = 0; i < 45; i++) tick(level, manager, time++);
            expect(controlled.pending.size() == 1, "No queued catch-up cycles");
            controlled.complete(); tick(level, manager, time++);
            expect(group.sleeping, "Dry group sleeps");
            for (int i = 0; i < 60; i++) tick(level, manager, time++);
            expect(controlled.pending.isEmpty(), "Sleeping groups submit no work");
            FiniteWaterPhysics.setWaterLevel(level, FIRST.west(), 8);
            for (int i = 0; i < 40 && controlled.pending.isEmpty(); i++) tick(level, manager, time++);
            expect(controlled.pending.size() == 1, "Water wakes group");
            expect(Math.floorMod(group.buffers[group.buffer].started - start, savedInterval) == 0, "Cadence remains anchored");
            FiniteWaterPhysics.setWaterLevel(level, FIRST.west(), 3); int before = total(level);
            controlled.complete(); tick(level, manager, time++);
            expect(total(level) == before && group.completedCycles == 1, "Stale water result rejected");
            for (int i = 0; i < 40 && controlled.pending.isEmpty(); i++) tick(level, manager, time++);
            expect(controlled.pending.size() == 1, "Next logical cycle retries");
            level.setBlock(FIRST, level.getBlockState(FIRST).setValue(WaterPumpBlock.FACING, Direction.WEST), Block.UPDATE_CLIENTS);
            before = total(level); controlled.complete(); tick(level, manager, time++);
            expect(total(level) == before, "Topology mutation rejects in-flight plan");
            for (int i = 0; i < 40 && controlled.pending.isEmpty(); i++) tick(level, manager, time++);
            expect(!controlled.pending.isEmpty(), "Work resumes after rebuild");
            var chunk = level.getChunkAt(FIRST);
            ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
            before = total(level); controlled.complete(); tick(level, manager, time++);
            expect(total(level) == before, "Chunk-unload notification rejects result");
            ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk, false);
            for (int interval : new int[]{7, 3, 20}) {
                WaterPhysicsConfig.SERVER.pump.tickInterval.set(interval);
                long previousStart = Long.MIN_VALUE;
                for (int i = 0; i < 45; i++) {
                    tick(level, manager, time++);
                    int submitted = controlled.pending.size(); manager.tick();
                    expect(controlled.pending.size() == submitted, "No duplicate start in same tick");
                    if (submitted > 0) {
                        long latest = manager.groups.stream().flatMap(g -> g.machines.stream()).mapToLong(m -> m.lastStart).max().orElseThrow();
                        expect(previousStart == Long.MIN_VALUE || latest - previousStart >= interval, "No catch-up burst after interval change");
                        previousStart = latest;
                    }
                    controlled.complete();
                }
            }
            System.out.println("PUMP_ASYNC_SLEEP_WAKE_STALE_TOPOLOGY_CHUNK_CADENCE_PASS");
        } finally {
            controlled.complete(); workers.set(null, originalWorkers);
            WaterPhysicsConfig.SERVER.pump.tickInterval.set(savedInterval);
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime);
            clear(level); PumpStructure.invalidate(level);
        }
    }

    private static void generatorClockDoesNotRebuild(ServerLevel level) throws Exception {
        long savedTime = level.getGameTime();
        try {
            fixture(level, false);
            BlockPos watched = FIRST.west();
            FiniteWaterPhysics.setWaterLevel(level, watched, 8);
            PumpManager manager = PumpManager.get(level);
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1500);
            manager.tick();
            var hydraulics = PumpManager.class.getDeclaredField("hydraulics"); hydraulics.setAccessible(true);
            expect(((PumpHydraulics) hydraulics.get(manager)).watches(watched.asLong()), "Generator regression position must be watched");
            var group = manager.groups.get(0);
            var off = ModBlocks.DIHYDROGEN_MONOXIDE_ASSEMBLER.defaultBlockState();
            PumpManager.changed(level, watched, off, off.setValue(DihydrogenMonoxideAssemblerBlock.POWERED, true));
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1501);
            manager.tick();
            expect(manager.groups.get(0) == group, "Generator power edge must not rebuild a pumping group");
            System.out.println("PUMP_GENERATOR_CLOCK_NO_REBUILD_PASS");
        } finally {
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime);
            clear(level); PumpStructure.invalidate(level);
        }
    }

    private static void valveMovementDoesNotRebuild(ServerLevel level) throws Exception {
        long savedTime = level.getGameTime();
        try {
            fixture(level, false);
            BlockPos watched = FIRST.west();
            level.setBlock(watched, ModBlocks.WATER_VALVE.defaultBlockState()
                    .setValue(WaterValveBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
            FiniteWaterPhysics.setWaterLevel(level, watched, 8);
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1600);
            level.setBlock(watched.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            var valve = (WaterValveBlockEntity) level.getBlockEntity(watched);
            valve.refresh();
            PumpManager manager = PumpManager.get(level);
            tick(level, manager, savedTime + 1600);
            var hydraulics = PumpManager.class.getDeclaredField("hydraulics"); hydraulics.setAccessible(true);
            expect(((PumpHydraulics) hydraulics.get(manager)).watches(watched.asLong()), "Valve regression position must be watched");
            var state = level.getBlockState(watched);
            expect(state.getValue(WaterValveBlock.POWERED), "Valve must be opening for regression");
            expect(PumpSnapshot.sameGeometry(state, state.setValue(WaterValveBlock.POWERED, false)),
                    "Valve power edge does not change its current geometry");
            var group = manager.groups.getFirst();
            var changed = PumpHydraulics.class.getDeclaredField("changed"); changed.setAccessible(true);
            PumpManager.changed(level, watched, state.setValue(WaterValveBlock.POWERED, false), state);
            expect(((it.unimi.dsi.fastutil.longs.LongOpenHashSet) changed.get(hydraulics.get(manager))).isEmpty(),
                    "Valve power edge must not rescan wet connectivity");
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1601);
            WaterValveBlockEntity.tick(level, watched, level.getBlockState(watched), valve);
            tick(level, manager, savedTime + 1601);
            expect(manager.groups.getFirst() == group, "Valve movement with unchanged passage must not rebuild");
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1620);
            WaterValveBlockEntity.tick(level, watched, level.getBlockState(watched), valve);
            tick(level, manager, savedTime + 1620);
            expect(manager.groups.getFirst() == group, "Valve remains sealed at progress 120");
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 1621);
            WaterValveBlockEntity.tick(level, watched, level.getBlockState(watched), valve);
            tick(level, manager, savedTime + 1621);
            expect(manager.groups.getFirst() != group, "Valve seal transition must rebuild connectivity");
            System.out.println("PUMP_VALVE_PASSAGE_ONLY_REBUILD_PASS");
        } finally {
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime);
            clear(level); PumpStructure.invalidate(level);
        }
    }

    private static void rapidReplenishment(ServerLevel level) throws Exception {
        long savedTime = level.getGameTime();
        var workers = PumpManager.class.getDeclaredField("workers"); workers.setAccessible(true);
        ExecutorService originalWorkers = (ExecutorService) workers.get(null);
        ControlledExecutor controlled = new ControlledExecutor();
        try {
            workers.set(null, controlled);
            fixture(level, false);
            for (int x = 3; x <= 13; x++) FiniteWaterPhysics.setWaterLevel(level, new BlockPos(x, Y, 8), 8);
            PumpManager manager = PumpManager.get(level);
            BlockPos source = FIRST.west(), replenished = FIRST.east(), outlet = new BlockPos(13, Y, 8);
            int transferred = 0;
            for (int tick = 0; tick < 85; tick++) {
                ((ServerLevelData) level.getLevelData()).setGameTime(savedTime + 2000 + tick);
                manager.finish(); // Next tick starts before the redstone/fluid clock changes water.
                if (tick == 25) FiniteWaterPhysics.setWaterLevel(level, outlet, 0);
                else if (tick > 25) {
                    transferred += FiniteWaterPhysics.getWaterLevel(level, outlet);
                    FiniteWaterPhysics.setWaterLevel(level, outlet, 0);
                }
                FiniteWaterPhysics.setWaterLevel(level, source, 8);
                FiniteWaterPhysics.setWaterLevel(level, replenished, tick % 2 == 0 ? 7 : 8);
                manager.tick(); // The pump snapshots the world after that tick's changes.
                controlled.complete();
            }
            expect(transferred >= 16, "Pump must recover when a full outlet is freed during one-tick replenishment: " + transferred);
            System.out.println("PUMP_ONE_TICK_REPLENISHMENT_RECOVERY_PASS units=" + transferred);
        } finally {
            controlled.complete(); workers.set(null, originalWorkers);
            ((ServerLevelData) level.getLevelData()).setGameTime(savedTime);
            clear(level); PumpStructure.invalidate(level);
        }
    }
    private static void tick(ServerLevel level, PumpManager manager, long time) {
        ((ServerLevelData) level.getLevelData()).setGameTime(time); manager.finish(); manager.tick();
    }
    private static int total(ServerLevel level) {
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(2, Y, 3, 14, Y, 10)) total += Math.max(0, FiniteWaterPhysics.getWaterLevel(level, pos));
        return total;
    }
    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(2, Y - 1, 3, 14, Y + 1, 10)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class ControlledExecutor extends AbstractExecutorService {
        final List<Runnable> pending = new ArrayList<>();
        @Override public void execute(Runnable task) { pending.add(task); }
        void complete() throws InterruptedException {
            for (Runnable task : pending) { Thread worker = new Thread(task, "pump-regression-worker"); worker.start(); worker.join(); }
            pending.clear();
        }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
