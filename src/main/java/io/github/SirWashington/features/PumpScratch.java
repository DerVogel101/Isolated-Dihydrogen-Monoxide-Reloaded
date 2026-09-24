package io.github.SirWashington.features;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Arrays;

/** Worker-owned primitive storage. Search paths are parent indices, never copied lists. */
final class PumpScratch {
    private int[] volumes = new int[0], retained = new int[0], plan = new int[0], targets = new int[0], best = new int[0];
    private boolean[] reserved = new boolean[0], duct = new boolean[0], visited = new boolean[0], assigned = new boolean[0];
    private int[] nodeCell = new int[256], parent = new int[256], depth = new int[256], priority = new int[256], pathLength = new int[256];
    private final IntArrayList sources = new IntArrayList(), queue = new IntArrayList(), outlets = new IntArrayList();
    private final IntArrayList intake = new IntArrayList();
    private final Int2IntOpenHashMap strengths = new Int2IntOpenHashMap();
    private final PumpCurrentField pressureCurrent = new PumpCurrentField();
    private int nodes, visitedCount;
    private PumpSnapshot world;
    private PumpAssembly machine;
    private PumpCurrentField current;

    void simulate(PumpSnapshot snapshot) {
        world = snapshot; ensureCells(world.size);
        for (int i : world.simulationOrder) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            machine = world.assemblies.get(i); current = world.fields[i];
            prepare();
            world.wet[i] = !sources.isEmpty();
            if (!world.wet[i]) continue;
            for (int cell : intake) current.add(world.positions[cell], machine.facing, Math.min(8, machine.budget / machine.lanes.length));
            for (long pos : machine.rotors) current.add(pos, machine.facing, Math.min(8, machine.budget / machine.lanes.length));
            int primed = prime();
            world.moved[i] = primed > 0;
            if (primed < machine.budget && pressure(machine.budget - primed)) world.moved[i] = true;
        }
    }

    private void prepare() {
        Arrays.fill(reserved, 0, world.size, false); Arrays.fill(duct, 0, world.size, false);
        intake.clear(); sources.clear();
        for (long[] lane : machine.lanes) {
            long previous = lane[3];
            for (int d = 0; d < 3; d++) {
                long pos = lane[d];
                if (world.water(pos) < 0 || !world.open(pos, previous)) break;
                int cell = world.index(pos);
                if (world.poweredPump[cell]) break;
                intake.add(cell); reserved[cell] = true;
                if (world.levels[cell] > 0) sources.add(cell);
                previous = pos;
            }
        }
        for (long pos : machine.rotors) {
            int cell = world.index(pos); reserved[cell] = duct[cell] = true;
            if (world.levels[cell] > 0) sources.add(cell);
        }
    }

    private int prime() {
        if (machine.stages.size() == 1) return 0;
        int remaining = machine.budget;
        for (int stage = machine.stages.size() - 1; stage >= 0; stage--) for (long[] lane : machine.lanes) {
            int target = world.index(lane[3 + stage]);
            int amount = world.levels[target];
            if (amount < 0 || amount >= 8 || remaining == 0) continue;
            long previous = lane[3];
            for (int d = 0; d < 3; d++) {
                long source = lane[d]; int cell = world.index(source);
                if (cell < 0 || world.levels[cell] < 0 || world.poweredPump[cell] || !world.open(source, previous)) break;
                previous = source;
                if (world.levels[cell] == 0) continue;
                boolean open = true;
                for (long pos = source; pos != lane[3 + stage]; pos = BlockPos.offset(pos, machine.facing))
                    if (!world.open(pos, BlockPos.offset(pos, machine.facing))) { open = false; break; }
                if (!open) continue;
                int moved = Math.min(remaining, Math.min(8 - amount, world.levels[cell]));
                if (moved == 0) continue;
                world.levels[cell] -= (byte) moved; amount += moved; remaining -= moved;
                world.levels[target] = (byte) amount;
                for (long pos = source;; pos = BlockPos.offset(pos, machine.facing)) {
                    current.add(pos, machine.facing, moved);
                    if (pos == lane[3 + stage]) break;
                }
                if (amount == 8 || remaining == 0) break;
            }
        }
        return machine.budget - remaining;
    }

    private boolean pressure(int budget) {
        sources.clear();
        Arrays.fill(volumes, 0, world.size, 0); Arrays.fill(retained, 0, world.size, 0);
        Arrays.fill(plan, 0, world.size, -1); Arrays.fill(visited, 0, world.size, false);
        Arrays.fill(assigned, 0, world.size, false); pressureCurrent.clear(); visitedCount = 0;
        for (int cell : intake) budget = source(cell, budget);
        for (long pos : machine.rotors) budget = source(world.index(pos), budget);
        if (sources.isEmpty()) return false;
        // Stable insertion sort preserves lane ordering for sources on the same plane.
        for (int i = 1; i < sources.size(); i++) {
            int cell = sources.getInt(i), j = i;
            while (j > 0 && machine.plane(world.positions[sources.getInt(j - 1)]) > machine.plane(world.positions[cell])) {
                sources.set(j, sources.getInt(j - 1)); j--;
            }
            sources.set(j, cell);
        }
        if (!search()) return false;
        for (int cell : sources) plan[cell] += retained[cell];
        for (int i = 0; i < world.size; i++) if (plan[i] >= 0) world.levels[i] = (byte) plan[i];
        for (int cell : sources) for (long pos = world.positions[cell]; machine.plane(pos) <= machine.outletPlane;
                                      pos = BlockPos.offset(pos, machine.facing)) pressureCurrent.add(pos, machine.facing, volumes[cell]);
        spread(); current.merge(pressureCurrent);
        return true;
    }

    private int source(int cell, int budget) {
        int amount = world.levels[cell], moved = Math.min(Math.max(0, amount), budget);
        if (moved > 0) {
            sources.add(cell); volumes[cell] = moved; retained[cell] = amount - moved; plan[cell] = 0;
        }
        return budget - moved;
    }

    private boolean passage(int from, int to) {
        if (to < 0 || world.levels[to] < 0 || (world.poweredPump[to] && !duct[to])) return false;
        long a = world.positions[from], b = world.positions[to];
        if (machine.plane(a) <= machine.outletPlane) {
            if (BlockPos.offset(a, machine.facing) != b) return false;
        } else if (machine.plane(b) <= machine.outletPlane) return false;
        return world.open(a, b);
    }

    private boolean visit(int cell) {
        if (!visited[cell]) {
            if (visitedCount >= machine.visits) return false;
            visited[cell] = true;
            visitedCount++;
        }
        return true;
    }

    private boolean search() {
        Direction facing = machine.facing;
        Direction[] directions = facing.getAxis() == Direction.Axis.Y
                ? new Direction[]{facing, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, facing.getOpposite()}
                : new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise(), Direction.UP, Direction.DOWN, facing.getOpposite()};
        for (int source : sources) {
            if (assigned[source]) continue;
            nodes = 0; queue.clear(); outlets.clear();
            Arrays.fill(best, 0, world.size * 7, Integer.MAX_VALUE);
            Arrays.fill(targets, 0, world.size, -1);
            if (!visit(source)) return false;
            assigned[source] = true; int volume = volumes[source];
            enqueue(source, -1, 0, 6);
            for (int head = 0; head < queue.size(); head++) {
                int state = queue.getInt(head), cell = nodeCell[state];
                if (best[cell * 7 + priority[state]] != depth[state] || depth[state] >= machine.depth) continue;
                for (int d = 0; d < directions.length; d++) {
                    Direction direction = directions[d];
                    int next = world.index(BlockPos.offset(world.positions[cell], direction));
                    if (!passage(cell, next)) continue;
                    int amount = volumes[next] > 0 ? volumes[next] : world.levels[next];
                    int p = priority[state] == 6 ? d : priority[state];
                    if (amount == 0 && !reserved[next]) {
                        int previous = state, cursor = next;
                        for (int dep = depth[state] + 1; dep <= machine.depth; dep++) {
                            if (cursor < 0 || reserved[cursor] || world.levels[cursor] != 0 || !passage(nodeCell[previous], cursor)) break;
                            previous = node(cursor, previous, dep, p); target(previous);
                            cursor = world.index(BlockPos.offset(world.positions[cursor], direction));
                        }
                        continue;
                    }
                if (amount > 0 && !visit(next)) continue;
                    int nextNode = node(next, state, depth[state] + 1, p);
                    if (!reserved[next] && volumes[next] == 0 && amount < 8) target(nextNode);
                    if (volumes[next] > 0) {
                        if (!assigned[next]) { assigned[next] = true; volume += volumes[next]; enqueue(next, -1, 0, 6); }
                    } else enqueueNode(nextNode);
                }
            }
            for (int p = 0; p < 6 && volume > 0; p++) for (int cell : outlets) {
                int target = targets[cell];
                if (priority[target] != p) continue;
                int moved = add(cell, volume);
                if (moved > 0) { volume -= moved; record(target, moved); }
                if (volume == 0) break;
            }
            if (volume != 0) return false;
        }
        return true;
    }

    private int add(int cell, int volume) {
        int amount = plan[cell] >= 0 ? plan[cell] : world.levels[cell];
        int moved = Math.min(volume, world.capacities[cell] - amount);
        if (moved > 0) plan[cell] = amount + moved;
        return Math.max(0, moved);
    }

    private void target(int node) {
        int cell = nodeCell[node], existing = targets[cell];
        if (existing < 0) { targets[cell] = node; outlets.add(cell); }
        else if (priority[node] < priority[existing] || priority[node] == priority[existing] && pathLength[node] < pathLength[existing]) targets[cell] = node;
    }
    private void enqueue(int cell, int previous, int dep, int p) { enqueueNode(node(cell, previous, dep, p)); }
    private void enqueueNode(int node) {
        int key = nodeCell[node] * 7 + priority[node];
        if (depth[node] < best[key]) { best[key] = depth[node]; queue.add(node); }
    }
    private int node(int cell, int previous, int dep, int p) {
        if (nodes == nodeCell.length) {
            int capacity = nodes * 2;
            nodeCell = Arrays.copyOf(nodeCell, capacity); parent = Arrays.copyOf(parent, capacity);
            depth = Arrays.copyOf(depth, capacity); priority = Arrays.copyOf(priority, capacity); pathLength = Arrays.copyOf(pathLength, capacity);
        }
        int node = nodes++; nodeCell[node] = cell; parent[node] = previous; depth[node] = dep; priority[node] = p;
        pathLength[node] = previous < 0 ? 1 : pathLength[previous] + 1;
        return node;
    }
    private void record(int node, int amount) {
        for (int previous = parent[node]; previous >= 0; node = previous, previous = parent[node]) {
            long from = world.positions[nodeCell[previous]], to = world.positions[nodeCell[node]];
            int packed = PumpCurrentField.pack(Integer.signum(BlockPos.getX(to) - BlockPos.getX(from)) * Math.min(8, amount),
                    Integer.signum(BlockPos.getY(to) - BlockPos.getY(from)) * Math.min(8, amount),
                    Integer.signum(BlockPos.getZ(to) - BlockPos.getZ(from)) * Math.min(8, amount));
            pressureCurrent.add(from, packed); pressureCurrent.add(to, packed);
        }
    }

    private void spread() {
        strengths.clear();
        var entries = pressureCurrent.cells.long2IntEntrySet().fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next(); int v = entry.getIntValue();
            int strength = PumpCurrentField.x(v) * machine.facing.getStepX() + PumpCurrentField.y(v) * machine.facing.getStepY() + PumpCurrentField.z(v) * machine.facing.getStepZ();
            if (strength > 0) strengths.merge(machine.plane(entry.getLongKey()), strength, Math::max);
        }
        for (var entry : strengths.int2IntEntrySet()) for (long[] lane : machine.lanes) {
            int offset = entry.getIntKey() - machine.plane(lane[3]);
            Direction direction = offset < 0 ? machine.facing.getOpposite() : machine.facing;
            long pos = lane[3]; boolean open = true;
            for (int d = 0; d < Math.abs(offset); d++) {
                long next = BlockPos.offset(pos, direction);
                if (world.water(next) < 0 || !(offset < 0 ? world.open(next, pos) : world.open(pos, next))) { open = false; break; }
                pos = next;
            }
            if (open) {
                int old = pressureCurrent.cells.get(pos);
                int x = PumpCurrentField.x(old), y = PumpCurrentField.y(old), z = PumpCurrentField.z(old);
                int axial = x * machine.facing.getStepX() + y * machine.facing.getStepY() + z * machine.facing.getStepZ();
                int increase = Math.max(0, entry.getIntValue() - axial);
                pressureCurrent.cells.put(pos, PumpCurrentField.pack(x + increase * machine.facing.getStepX(),
                        y + increase * machine.facing.getStepY(), z + increase * machine.facing.getStepZ()));
            }
        }
    }

    private void ensureCells(int required) {
        if (required <= volumes.length) return;
        int capacity = Math.max(required, Math.max(256, volumes.length * 2));
        volumes = new int[capacity]; retained = new int[capacity]; plan = new int[capacity]; targets = new int[capacity]; best = new int[capacity * 7];
        reserved = new boolean[capacity]; duct = new boolean[capacity]; visited = new boolean[capacity]; assigned = new boolean[capacity];
    }
}
