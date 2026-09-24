package io.github.SirWashington.features;

import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterValveBlock;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;
import java.util.List;

/** Main-thread capture; workers see only primitive cells and immutable machine geometry. */
final class PumpSnapshot {
    static final Direction[] DIRECTIONS = Direction.values();
    final Long2IntOpenHashMap indices = new Long2IntOpenHashMap();
    long[] positions = new long[256];
    BlockPos[] blocks = new BlockPos[256];
    BlockState[] states = new BlockState[256];
    byte[] original = new byte[256], levels = new byte[256], capacities = new byte[256], edges = new byte[256];
    boolean[] poweredPump = new boolean[256];
    int size;
    long revision, started;
    List<PumpAssembly> assemblies;
    boolean[] wet = new boolean[0], moved = new boolean[0];
    PumpCurrentField[] fields = new PumpCurrentField[0];
    int[] simulationOrder = new int[0];
    private boolean[][] downstream = new boolean[0][];
    private boolean[] ordered = new boolean[0];
    private int[] remainingDownstream = new int[0];
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<IntArrayList> intakeOwners = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap depths = new Long2IntOpenHashMap();
    private final IntArrayList queue = new IntArrayList();
    private final LongOpenHashSet duct = new LongOpenHashSet();
    private List<PumpAssembly> cachedMachines;
    private long cachedGeometry = Long.MIN_VALUE;

    PumpSnapshot() { indices.defaultReturnValue(-1); depths.defaultReturnValue(Integer.MAX_VALUE); }

    void capture(ServerLevel level, List<PumpAssembly> machines, long revision) {
        capture(level, machines, revision, revision);
    }

    void capture(ServerLevel level, List<PumpAssembly> machines, long revision, long geometryRevision) {
        boolean reuse = cachedMachines == machines && cachedGeometry == geometryRevision;
        if (!reuse) { indices.clear(); size = 0; }
        assemblies = machines; this.revision = revision; started = level.getGameTime();
        if (wet.length != machines.size()) {
            wet = new boolean[machines.size()]; moved = new boolean[machines.size()];
            fields = new PumpCurrentField[machines.size()];
            simulationOrder = new int[machines.size()]; downstream = new boolean[machines.size()][machines.size()]; ordered = new boolean[machines.size()];
            remainingDownstream = new int[machines.size()];
            for (int i = 0; i < fields.length; i++) fields[i] = new PumpCurrentField();
        }
        Arrays.fill(wet, false); Arrays.fill(moved, false);
        for (PumpCurrentField field : fields) field.clear();
        if (reuse) {
            for (int i = 0; i < size; i++) readCell(level, i);
            return;
        }
        intakeOwners.clear();
        for (int i = 0; i < machines.size(); i++) {
            Arrays.fill(downstream[i], false);
            for (long[] lane : machines.get(i).lanes) for (int d = 0; d < 3; d++)
                intakeOwners.computeIfAbsent(lane[d], ignored -> new IntArrayList()).add(i);
        }
        for (int i = 0; i < machines.size(); i++) captureMachine(level, machines.get(i), i);
        orderMachines();
        // Edges are evaluated on the server, including context-sensitive collision shapes.
        for (int i = 0; i < size; i++) {
            int mask = 0;
            if (levels[i] >= 0) for (Direction direction : DIRECTIONS) {
                int next = index(BlockPos.offset(positions[i], direction));
                if (next >= 0 && levels[next] >= 0
                        && FiniteWaterPhysics.canFlowBetween(level, blocks[i], blocks[next])) mask |= 1 << direction.ordinal();
            }
            edges[i] = (byte) mask;
        }
        cachedMachines = machines; cachedGeometry = geometryRevision;
    }

    private void captureMachine(ServerLevel level, PumpAssembly machine, int machineIndex) {
        depths.clear(); queue.clear(); duct.clear();
        for (long pos : machine.rotors) duct.add(pos);
        for (long[] lane : machine.lanes) for (long pos : lane) {
            int cell = captureCell(level, pos);
            if (depths.putIfAbsent(pos, 0) == Integer.MAX_VALUE) queue.add(cell);
        }
        for (int head = 0; head < queue.size(); head++) {
            int cell = queue.getInt(head);
            long pos = positions[cell];
            int depth = depths.get(pos);
            if (depth >= machine.depth || levels[cell] < 0) continue;
            for (Direction direction : DIRECTIONS) {
                if (machine.plane(pos) <= machine.outletPlane && direction != machine.facing) continue;
                long nextPos = BlockPos.offset(pos, direction);
                if (machine.plane(pos) > machine.outletPlane && machine.plane(nextPos) <= machine.outletPlane) continue;
                int next = captureCell(level, nextPos);
                if (levels[next] < 0 || (poweredPump[next] && !duct.contains(nextPos))
                        || !FiniteWaterPhysics.canFlowBetween(level, blocks[cell], blocks[next])) continue;
                IntArrayList owners = intakeOwners.get(nextPos);
                if (owners != null && machine.plane(nextPos) > machine.outletPlane)
                    for (int owner : owners) if (owner != machineIndex) downstream[machineIndex][owner] = true;
                if (depth + 1 < depths.get(nextPos)) {
                    depths.put(nextPos, depth + 1); queue.add(next);
                }
            }
        }
    }

    private void orderMachines() {
        Arrays.fill(ordered, false);
        Arrays.fill(remainingDownstream, 0);
        for (int from = 0; from < downstream.length; from++) for (int to = 0; to < downstream.length; to++)
            if (downstream[from][to]) remainingDownstream[from]++;
        for (int n = 0; n < simulationOrder.length; n++) {
            int selected = -1;
            for (int candidate = 0; candidate < ordered.length; candidate++) if (!ordered[candidate]) {
                if (remainingDownstream[candidate] == 0) { selected = candidate; break; }
            }
            // Cyclic dependency: stable controller order, one budget per machine, no iterative extra pass.
            if (selected < 0) for (int candidate = 0; candidate < ordered.length; candidate++)
                if (!ordered[candidate] && (selected < 0 || assemblies.get(candidate).controller() < assemblies.get(selected).controller())) selected = candidate;
            simulationOrder[n] = selected; ordered[selected] = true;
            for (int other = 0; other < ordered.length; other++) if (downstream[other][selected]) remainingDownstream[other]--;
        }
    }

    private int captureCell(ServerLevel level, long pos) {
        int found = index(pos);
        if (found >= 0) return found;
        ensure(size + 1);
        int cell = size++;
        indices.put(pos, cell); positions[cell] = pos;
        if (blocks[cell] == null || blocks[cell].asLong() != pos) blocks[cell] = BlockPos.of(pos);
        readCell(level, cell);
        return cell;
    }

    private void readCell(ServerLevel level, int cell) {
        BlockPos block = blocks[cell];
        boolean loaded = !level.isOutsideBuildHeight(block) && level.hasChunkAt(block);
        states[cell] = loaded ? level.getBlockState(block) : null;
        original[cell] = levels[cell] = (byte) (loaded ? FiniteWaterPhysics.getWaterLevel(level, block) : -1);
        capacities[cell] = (byte) (loaded ? FiniteWaterPhysics.getWaterCapacity(level, block) : 0);
        poweredPump[cell] = loaded && states[cell].getBlock() instanceof WaterPumpBlock
                && states[cell].getValue(WaterPumpBlock.POWERED);
    }

 static boolean sameGeometry(BlockState previous, BlockState next) {
        if (previous.getBlock() == next.getBlock() && previous.getBlock() instanceof WaterValveBlock) {
            previous = previous.setValue(WaterValveBlock.POWERED, false);
            next = next.setValue(WaterValveBlock.POWERED, false);
        }
        boolean oldOpen = previous.isAir() || io.github.SirWashington.fluid.ModFluids.isFiniteWater(previous.getFluidState().getType())
                && previous.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
        boolean newOpen = next.isAir() || io.github.SirWashington.fluid.ModFluids.isFiniteWater(next.getFluidState().getType())
                && next.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
        if (oldOpen && newOpen) return true;
        return previous.getBlock() == next.getBlock() && FiniteWaterloggedPlants.getLevel(previous) >= 0
                && FiniteWaterloggedPlants.getLevel(next) >= 0
                && FiniteWaterloggedPlants.withLevel(previous, 0) == FiniteWaterloggedPlants.withLevel(next, 0);
    }

    int index(long pos) { return indices.get(pos); }
    int water(long pos) { int cell = index(pos); return cell < 0 ? -1 : levels[cell]; }
    boolean open(long from, long to) {
        int cell = index(from);
        if (cell < 0) return false;
        for (Direction d : DIRECTIONS) if (BlockPos.offset(from, d) == to) return (edges[cell] & (1 << d.ordinal())) != 0;
        return false;
    }

    boolean valid(ServerLevel level) {
        for (int i = 0; i < size; i++) {
            boolean loaded = !level.isOutsideBuildHeight(blocks[i]) && level.hasChunkAt(blocks[i]);
            if (states[i] == null ? loaded : !loaded || level.getBlockState(blocks[i]) != states[i]) return false;
            if (states[i] != null && states[i].hasBlockEntity() && !(states[i].getBlock() instanceof WaterPumpBlock)) {
                for (Direction direction : DIRECTIONS) {
                    int next = index(BlockPos.offset(positions[i], direction));
                    if (next < 0 || levels[i] < 0 || levels[next] < 0) continue;
                    if (((edges[i] & (1 << direction.ordinal())) != 0) != FiniteWaterPhysics.canFlowBetween(level, blocks[i], blocks[next])) return false;
                    if (((edges[next] & (1 << direction.getOpposite().ordinal())) != 0) != FiniteWaterPhysics.canFlowBetween(level, blocks[next], blocks[i])) return false;
                }
            }
        }
        return true;
    }

    void commit(ServerLevel level) {
        // No worker/world interleaving: one validated group commits in the server callback.
        for (int i = 0; i < size; i++) if (levels[i] == 0 && original[i] != 0)
            FiniteWaterPhysics.setWaterLevel(level, blocks[i], 0);
        for (int i = 0; i < size; i++) if (levels[i] > 0 && levels[i] != original[i])
            FiniteWaterPhysics.setWaterLevel(level, blocks[i], levels[i]);
        for (int i = 0; i < fields.length; i++) fields[i].publish(level, assemblies.get(i));
    }

    private void ensure(int required) {
        if (required <= positions.length) return;
        int capacity = Math.max(required, positions.length * 2);
        positions = Arrays.copyOf(positions, capacity); blocks = Arrays.copyOf(blocks, capacity);
        states = Arrays.copyOf(states, capacity); original = Arrays.copyOf(original, capacity);
        levels = Arrays.copyOf(levels, capacity); capacities = Arrays.copyOf(capacities, capacity);
        edges = Arrays.copyOf(edges, capacity); poweredPump = Arrays.copyOf(poweredPump, capacity);
    }
}
