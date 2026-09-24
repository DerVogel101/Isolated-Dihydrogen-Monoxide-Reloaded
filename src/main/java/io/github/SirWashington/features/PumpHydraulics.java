package io.github.SirWashington.features;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/** Retained wet-conduit connectivity, extended only when water arrives at a recorded frontier. */
final class PumpHydraulics {
    // ponytail: bound ocean discovery; a level-wide group is conservative when this index fills.
    private static final int MAX_CELLS = 65_536;
    private final Long2ObjectOpenHashMap<PumpManager.Group> wet = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<PumpManager.Group>> frontier = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet changed = new LongOpenHashSet();
    private final LongArrayList queue = new LongArrayList();
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final LongOpenHashSet chunks = new LongOpenHashSet();
    boolean broad;

    void rebuild(ServerLevel level, List<PumpManager.Group> groups) {
        wet.clear(); frontier.clear(); changed.clear(); chunks.clear(); broad = false;
        var original = new ArrayList<>(groups);
        for (PumpManager.Group group : original) for (PumpAssembly machine : group.machines)
            for (long[] lane : machine.lanes) for (long pos : lane) {
                if (broad) return;
                PumpManager.Group root = group.root();
                if (water(level, pos) > 0) extend(level, groups, root, pos);
                else watch(pos, root);
            }
    }

    boolean relevantChunk(long chunk) { return chunks.contains(chunk); }
    boolean watches(long pos) { return wet.containsKey(pos) || frontier.containsKey(pos); }
    void changed(BlockPos pos, BlockState next) {
        if (!broad && watches(pos.asLong()) && (next.isAir() || !next.getFluidState().isEmpty())) changed.add(pos.asLong());
    }

    boolean update(ServerLevel level, List<PumpManager.Group> groups) {
        int before = groups.size();
        for (long pos : changed) {
            if (water(level, pos) <= 0) continue;
            PumpManager.Group owner = wet.get(pos);
            if (owner != null) extend(level, groups, owner.root(), pos);
            else {
                List<PumpManager.Group> interested = frontier.get(pos);
                if (interested != null) for (PumpManager.Group group : new ArrayList<>(interested)) {
                    PumpManager.Group root = group.root();
                    boolean connected = false;
                    for (Direction direction : PumpSnapshot.DIRECTIONS) {
                        long adjacent = BlockPos.offset(pos, direction);
                        PumpManager.Group neighbor = wet.get(adjacent);
                        if (neighbor != null && neighbor.root() == root && water(level, adjacent) > 0 && open(level, adjacent, pos)) { connected = true; break; }
                    }
                    if (!connected) for (PumpAssembly machine : root.machines) for (long[] lane : machine.lanes)
                        for (long cell : lane) if (cell == pos) connected = true;
                    if (connected) extend(level, groups, root, pos);
                }
            }
            if (broad) break;
        }
        changed.clear();
        return groups.size() != before;
    }

    private void extend(ServerLevel level, List<PumpManager.Group> groups, PumpManager.Group group, long seed) {
        queue.clear(); visited.clear(); queue.add(seed); visited.add(seed);
        for (int head = 0; head < queue.size(); head++) {
            if (wet.size() + frontier.size() >= MAX_CELLS) { collapse(groups); return; }
            long pos = queue.getLong(head);
            group = group.root();
            PumpManager.Group existing = wet.get(pos);
            if (existing != null && existing.root() != group) group = merge(groups, group, existing.root());
            wet.put(pos, group); frontier.remove(pos); registerChunk(pos);
            for (Direction direction : PumpSnapshot.DIRECTIONS) {
                long next = BlockPos.offset(pos, direction);
                if (water(level, next) > 0 && open(level, pos, next)) {
                    PumpManager.Group neighbor = wet.get(next);
                    if (neighbor != null && neighbor.root() != group) group = merge(groups, group, neighbor.root());
                    if ((neighbor == null || next == seed) && visited.add(next)) queue.add(next);
                } else watch(next, group);
            }
        }
    }

    private void watch(long pos, PumpManager.Group group) {
        registerChunk(pos);
        List<PumpManager.Group> owners = frontier.computeIfAbsent(pos, ignored -> new ArrayList<>());
        for (PumpManager.Group owner : owners) if (owner.root() == group.root()) return;
        owners.add(group);
    }
    private void registerChunk(long pos) { chunks.add(net.minecraft.world.level.ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4)); }
    private static int water(ServerLevel level, long pos) {
        BlockPos block = BlockPos.of(pos);
        return !level.isOutsideBuildHeight(block) && level.hasChunkAt(block) ? FiniteWaterPhysics.getWaterLevel(level, block) : -1;
    }
    private static boolean open(ServerLevel level, long first, long second) {
        BlockPos a = BlockPos.of(first), b = BlockPos.of(second);
        return FiniteWaterPhysics.canFlowBetween(level, a, b) || FiniteWaterPhysics.canFlowBetween(level, b, a);
    }
    private static PumpManager.Group merge(List<PumpManager.Group> groups, PumpManager.Group a, PumpManager.Group b) {
        PumpManager.Group combined = new PumpManager.Group(a.machines.getFirst(), a.generation);
        combined.machines.clear(); combined.merge(a); combined.merge(b);
        a.parent = b.parent = combined; a.revision++; b.revision++;
        groups.remove(a); groups.remove(b); groups.add(combined);
        return combined;
    }
    private void collapse(List<PumpManager.Group> groups) {
        while (groups.size() > 1) merge(groups, groups.getFirst(), groups.getLast());
        broad = true; wet.clear(); frontier.clear(); chunks.clear();
    }
}
