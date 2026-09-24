package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.DihydrogenMonoxideAssemblerBlock;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterPumpBlockEntity;
import io.github.SirWashington.block.WaterValveBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Level-owned machines, dependency groups, logical timing wheel, and nonblocking worker commits. */
public final class PumpManager {
    private static final Map<ServerLevel, PumpManager> MANAGERS = new WeakHashMap<>();
    private static ExecutorService workers;
    private static final ThreadLocal<PumpScratch> SCRATCH = ThreadLocal.withInitial(PumpScratch::new);
    private final ServerLevel level;
    private final LongOpenHashSet blocks = new LongOpenHashSet();
    private final Long2LongOpenHashMap lastStarts = new Long2LongOpenHashMap();
    private final Long2ObjectOpenHashMap<List<Group>> dependencies = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<Group>> wheel = new Int2ObjectOpenHashMap<>();
    final List<Group> groups = new ArrayList<>();
    private final List<Group> inFlight = new ArrayList<>();
    private final Long2ObjectOpenHashMap<SoundState> sounds = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<SoundState>> soundWheel = new Int2ObjectOpenHashMap<>();
    private final PumpHydraulics hydraulics = new PumpHydraulics();
    private boolean dirty = true, refreshing, broadDependencies;
    private int interval, depth, visits, units;
    private long revision, lastTick = Long.MIN_VALUE;

    private PumpManager(ServerLevel level) { this.level = level; lastStarts.defaultReturnValue(Long.MIN_VALUE); }
    static PumpManager get(ServerLevel level) { return MANAGERS.computeIfAbsent(level, PumpManager::new); }

    public static void initialize() {
        ServerTickEvents.START_LEVEL_TICK.register(level -> get(level).finish());
        ServerTickEvents.END_LEVEL_TICK.register(level -> get(level).tick());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            for (var entity : chunk.getBlockEntities().values()) if (entity instanceof WaterPumpBlockEntity) register(level, entity.getBlockPos());
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            PumpManager manager = MANAGERS.get(level);
            if (manager == null) return;
            manager.blocks.removeIf(pos -> (BlockPos.getX(pos) >> 4) == chunk.getPos().x() && (BlockPos.getZ(pos) >> 4) == chunk.getPos().z());
            manager.markDirty();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (PumpManager manager : MANAGERS.values()) for (Group group : manager.inFlight) group.job.cancel(true);
            MANAGERS.clear(); PumpCurrentField.clearAll();
            if (workers != null) { workers.shutdownNow(); workers = null; }
        });
    }

    public static void register(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) { PumpManager manager = get(server); if (manager.blocks.add(pos.asLong())) manager.markDirty(); }
    }
    public static void unregister(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            PumpManager manager = MANAGERS.get(server);
            if (manager != null && manager.blocks.remove(pos.asLong())) { manager.lastStarts.remove(pos.asLong()); manager.markDirty(); }
        }
    }
    public static void invalidate(ServerLevel level) { PumpManager manager = MANAGERS.get(level); if (manager != null) manager.markDirty(); }
    public static boolean chunkRelevant(ServerLevel level, ChunkPos chunk) {
        PumpManager manager = MANAGERS.get(level);
        if (manager == null) return false;
        for (long pos : manager.blocks) if (Math.abs((BlockPos.getX(pos) >> 4) - chunk.x()) <= 1
                && Math.abs((BlockPos.getZ(pos) >> 4) - chunk.z()) <= 1) return true;
        long key = ChunkPos.pack(chunk.x(), chunk.z());
        return manager.broadDependencies || manager.dependencies.containsKey(key) || manager.hydraulics.relevantChunk(key);
    }
    public static void geometryChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        PumpManager manager = MANAGERS.get(server);
        if (manager == null) return;
        if (manager.hydraulics.watches(pos.asLong())) manager.markDirty();
        List<Group> affected = manager.broadDependencies ? manager.groups : manager.dependencies.get(ChunkPos.pack(pos));
        if (affected != null) for (Group group : affected) if (group.contains(pos)) { group.revision++; group.geometryRevision++; group.sleeping = false; }
    }
    public static void powerChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            PumpManager manager = MANAGERS.get(server);
            if (manager != null && !manager.refreshing && level.getBlockState(pos).getBlock() instanceof WaterPumpBlock
                    && PumpStructure.find(level, pos).powered(level) != level.getBlockState(pos).getValue(WaterPumpBlock.POWERED)) manager.markDirty();
        }
    }
    private void markDirty() {
        dirty = true; revision++;
    }

    private void checkConfig() {
        if (!dirty && (interval != WaterPhysicsConfig.pumpTickInterval() || depth != WaterPhysicsConfig.pumpMaxDepth()
                || visits != WaterPhysicsConfig.pumpMaxVisitedWaterCells() || units != WaterPhysicsConfig.pumpWaterUnitsPerCycle())) markDirty();
    }

    public static void changed(Level level, BlockPos pos, BlockState previous, BlockState next) {
        if (!(level instanceof ServerLevel server) || previous == next) return;
        // Generator power only changes its water output; it does not change pump topology.
        if (previous.getBlock() == next.getBlock() && previous.getBlock() instanceof DihydrogenMonoxideAssemblerBlock) return;
        // Valve power reverses motion without changing the passage at that instant.
        if (previous.getBlock() == next.getBlock() && previous.getBlock() instanceof WaterValveBlock
                && previous.setValue(WaterValveBlock.POWERED, false) == next.setValue(WaterValveBlock.POWERED, false)) return;
        PumpManager manager = MANAGERS.get(server);
        if (manager == null) return;
        manager.hydraulics.changed(pos, next);
        if (!PumpSnapshot.sameGeometry(previous, next) && manager.hydraulics.watches(pos.asLong())) manager.markDirty();
        if ((previous.getBlock() instanceof WaterPumpBlock || next.getBlock() instanceof WaterPumpBlock)
                && (previous.getBlock() != next.getBlock() || previous.getValue(WaterPumpBlock.FACING) != next.getValue(WaterPumpBlock.FACING))) {
            manager.markDirty();
        } else if (previous.getBlock() instanceof WaterPumpBlock && next.getBlock() instanceof WaterPumpBlock
            && previous.getValue(WaterPumpBlock.POWERED) != next.getValue(WaterPumpBlock.POWERED) && !manager.refreshing) manager.markDirty();
        List<Group> affected = manager.broadDependencies ? manager.groups : manager.dependencies.get(ChunkPos.pack(pos));
        if (affected != null) for (Group group : affected) if (group.contains(pos)) {
            boolean geometry = !PumpSnapshot.sameGeometry(previous, next);
            if (geometry) group.geometryRevision++;
            if (geometry || group.job == null || group.buffers[group.buffer].index(pos.asLong()) >= 0) group.revision++;
            group.sleeping = false;
        }
        // Signal sources can be changed with UPDATE_CLIENTS (commands/tests/mods), without neighbor callbacks.
        if (previous.isSignalSource() || next.isSignalSource()) {
            for (var direction : PumpSnapshot.DIRECTIONS) if (manager.blocks.contains(BlockPos.offset(pos.asLong(), direction))) {
                manager.markDirty();
                break;
            }
        }
    }

    void tick() {
        long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        checkConfig();
        if (dirty) rebuild();
        else if (hydraulics.update(level, groups)) indexGroups();
        List<Group> due = wheel.get((int) Math.floorMod(now, interval));
        if (due != null) for (Group group : due) {
            if (group.sleeping || group.job != null || !group.loaded(level)) continue;
            boolean overlapInFlight = false;
            for (Group pending : inFlight) if (pending.overlaps(group)) { overlapInFlight = true; break; }
            if (overlapInFlight) continue;
            boolean tooSoon = false;
            for (PumpAssembly machine : group.machines) if (machine.lastStart != Long.MIN_VALUE && now >= machine.lastStart && now - machine.lastStart < interval) tooSoon = true;
            if (tooSoon) continue;
            PumpSnapshot snapshot = group.buffers[group.buffer ^= 1];
            snapshot.capture(level, group.machines, group.revision, group.geometryRevision);
            for (PumpAssembly machine : group.machines) {
                machine.lastStart = now;
                for (long pos : machine.rotors) lastStarts.put(pos, now);
            }
            if (workers == null) workers = Executors.newFixedThreadPool(Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4), task -> {
                Thread thread = new Thread(task, "immersivefluids-pump"); thread.setDaemon(true); return thread;
            });
            group.job = workers.submit(() -> { SCRATCH.get().simulate(snapshot); return snapshot; });
            inFlight.add(group);
        }
        List<SoundState> dueSounds = soundWheel.get((int) Math.floorMod(now, 20));
        if (dueSounds != null) for (SoundState sound : dueSounds) if (now >= sound.next) {
            if (level.getBlockEntity(sound.stage.origin()) instanceof WaterPumpBlockEntity pump)
                pump.playRunningSound(level, sound.stage, sound.wet);
            sound.next = now + 20;
        }
    }

    void finish() {
        if (inFlight.isEmpty()) return;
        checkConfig();
        for (var iterator = inFlight.iterator(); iterator.hasNext();) {
            Group group = iterator.next();
            if (!group.job.isDone()) continue; // Never wait on the server thread.
            PumpSnapshot result;
            try { result = group.job.get(); }
            catch (Exception error) { throw new IllegalStateException("Pump worker failed", error); }
            iterator.remove(); group.job = null;
            if (group.parent != null || group.generation != revision || result.revision != group.revision) continue;
            if (!result.valid(level)) { group.geometryRevision++; continue; }
            result.commit(level);
            group.completedCycles++;
            boolean wet = false;
            for (int i = 0; i < group.machines.size(); i++) {
                PumpAssembly machine = group.machines.get(i); machine.wet = result.wet[i]; wet |= machine.wet;
                SoundState sound = sounds.get(machine.controller()); if (sound != null) sound.wet = machine.wet;
            }
            group.sleeping = !wet;
        }
    }

    private void rebuild() {
        dirty = false;
        interval = WaterPhysicsConfig.pumpTickInterval(); depth = WaterPhysicsConfig.pumpMaxDepth();
        visits = WaterPhysicsConfig.pumpMaxVisitedWaterCells(); units = WaterPhysicsConfig.pumpWaterUnitsPerCycle();
        groups.clear(); wheel.clear(); dependencies.clear(); broadDependencies = false;
        Long2ObjectOpenHashMap<PumpStructure> stages = new Long2ObjectOpenHashMap<>();
        refreshing = true;
        try {
            // Chunk loads and refreshes can change the registry during this pass.
            for (long pos : blocks.toLongArray()) {
                BlockPos block = BlockPos.of(pos);
                if (!level.hasChunkAt(block) || !(level.getBlockState(block).getBlock() instanceof WaterPumpBlock)) continue;
                PumpStructure stage = PumpStructure.find(level, block); stages.put(stage.origin().asLong(), stage);
                if (level.getBlockEntity(block) instanceof WaterPumpBlockEntity pump) pump.refresh();
            }
        } finally { refreshing = false; }
        var machines = new ArrayList<PumpAssembly>();
        var retainedSounds = new LongOpenHashSet();
        for (PumpStructure stage : stages.values()) {
            if (!stage.powered(level)) continue;
            if (!stage.muted(level)) {
                retainedSounds.add(stage.origin().asLong());
                SoundState sound = sounds.get(stage.origin().asLong());
                if (sound == null || !sound.stage.equals(stage)) sounds.put(stage.origin().asLong(), new SoundState(stage, level.getGameTime() + 19, PumpFlow.hasWater(level, stage)));
            }
            List<PumpStructure> series = stage.series(level);
            int first = series.indexOf(stage);
            if (first > 0 && series.get(first - 1).powered(level)) continue;
            int end = first + 1;
            while (end < series.size() && series.get(end).powered(level)) end++;
            var machine = new PumpAssembly(series.subList(first, end), stage.muted(level), PumpFlow.scaledLimit(depth, end - first), PumpFlow.scaledLimit(visits, end - first), units);
            for (long pos : machine.rotors) machine.lastStart = Math.max(machine.lastStart, lastStarts.get(pos));
            machines.add(machine);
        }
        sounds.keySet().removeIf(pos -> !retainedSounds.contains(pos));
        soundWheel.clear();
        for (SoundState sound : sounds.values()) soundWheel.computeIfAbsent((int) Math.floorMod(sound.next, 20), ignored -> new ArrayList<>()).add(sound);
        machines.sort(Comparator.comparingLong(PumpAssembly::controller));
        // A shared reachable cell is a pressure dependency, even before a conduit becomes wet.
        Long2ObjectOpenHashMap<List<Group>> spatial = new Long2ObjectOpenHashMap<>();
        PumpSnapshot geometry = new PumpSnapshot();
        for (PumpAssembly machine : machines) {
            Group group = new Group(machine, revision);
            geometry.capture(level, List.of(machine), revision);
            for (int i = 0; i < geometry.size; i++) if (geometry.capacities[i] > 0) group.reachable.add(geometry.positions[i]);
            if (group.radius > 128) broadDependencies = true;
            var candidates = new java.util.LinkedHashSet<Group>();
            if (broadDependencies) candidates.addAll(groups);
            else group.chunks(key -> { List<Group> found = spatial.get(key); if (found != null) for (Group candidate : found) candidates.add(candidate.root()); });
            if (!broadDependencies) group.chunks(key -> spatial.computeIfAbsent(key, ignored -> new ArrayList<>()).add(group));
            for (Group other : candidates) if (broadDependencies || group.overlaps(other)) {
                group.merge(other); groups.remove(other);
                other.parent = group;
            }
            groups.add(group);
        }
        hydraulics.rebuild(level, groups);
        indexGroups();
    }

    private void indexGroups() {
        wheel.clear(); dependencies.clear();
        broadDependencies |= hydraulics.broad;
        for (Group group : groups) if (((group.maxX >> 4) - (group.minX >> 4) + 1)
                * ((group.maxZ >> 4) - (group.minZ >> 4) + 1) > 4096) broadDependencies = true;
        for (Group group : groups) {
            if (group.job == null) group.order();
            int phase = Math.floorMod(Long.hashCode(it.unimi.dsi.fastutil.HashCommon.mix(group.machines.getFirst().controller())), interval);
            wheel.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(group);
            if (!broadDependencies) group.chunks(key -> dependencies.computeIfAbsent(key, ignored -> new ArrayList<>()).add(group));
        }
    }

    private static final class SoundState {
        final PumpStructure stage;
        long next;
        boolean wet;
        SoundState(PumpStructure stage, long next, boolean wet) { this.stage = stage; this.next = next; this.wet = wet; }
    }

    static final class Group {
        final List<PumpAssembly> machines = new ArrayList<>();
        final LongOpenHashSet reachable = new LongOpenHashSet();
        final PumpSnapshot[] buffers = {new PumpSnapshot(), new PumpSnapshot()};
        long generation, revision, geometryRevision, completedCycles;
        long minX, minY, minZ, maxX, maxY, maxZ, radius;
        int buffer;
        boolean sleeping;
        Future<PumpSnapshot> job;
        Group parent;
        Group root() { Group root = this; while (root.parent != null) root = root.parent; return root; }
        Group(PumpAssembly machine, long generation) {
            machines.add(machine); this.generation = generation;
            radius = (long) machine.depth + machine.stages.size() + 5;
            BlockPos pos = machine.intake.origin();
            minX = pos.getX() - radius; maxX = pos.getX() + radius;
            minY = pos.getY() - radius; maxY = pos.getY() + radius;
            minZ = pos.getZ() - radius; maxZ = pos.getZ() + radius;
        }
        boolean overlaps(Group other) {
            if (minX > other.maxX || maxX < other.minX || minY > other.maxY || maxY < other.minY || minZ > other.maxZ || maxZ < other.minZ) return false;
            LongOpenHashSet small = reachable.size() <= other.reachable.size() ? reachable : other.reachable;
            LongOpenHashSet large = small == reachable ? other.reachable : reachable;
            for (long pos : small) if (large.contains(pos)) return true;
            return false;
        }
        boolean contains(BlockPos pos) { return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ; }
        void merge(Group other) {
            machines.addAll(other.machines); radius = Math.max(radius, other.radius);
            reachable.addAll(other.reachable);
            minX = Math.min(minX, other.minX); maxX = Math.max(maxX, other.maxX);
            minY = Math.min(minY, other.minY); maxY = Math.max(maxY, other.maxY);
            minZ = Math.min(minZ, other.minZ); maxZ = Math.max(maxZ, other.maxZ);
        }
        void chunks(java.util.function.LongConsumer consumer) {
            for (long x = minX >> 4; x <= maxX >> 4; x++) for (long z = minZ >> 4; z <= maxZ >> 4; z++) consumer.accept(ChunkPos.pack((int) x, (int) z));
        }
        boolean loaded(ServerLevel level) {
            for (PumpAssembly machine : machines) for (long pos : machine.rotors)
                if (!level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(BlockPos.of(pos)))) return false;
            return true;
        }
        void order() {
            machines.sort(Comparator.comparingLong(PumpAssembly::controller));
        }
    }
}
