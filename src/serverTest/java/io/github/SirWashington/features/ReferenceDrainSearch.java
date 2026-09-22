package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.lang.reflect.Method;
import static io.github.SirWashington.features.FiniteWaterPhysics.*;

/** Original FIFO search from 806129f; intentionally allocation-heavy differential oracle. */
final class ReferenceDrainSearch {
    private static final int MAX_LEVEL = 8;
    private static final Method WATER = method("getWaterLevel", 1);
    private static int getWaterLevel(BlockState state) { return (int) invoke(WATER, state); }
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Method BARRIER = method("flowBarrierLevel", 7);
    private static final Method FLOW = method("canFlowBetween", 7);
    private static final Method EXTENDED = method("isExtendedDrainPath", 1);
    private static final Method CURRENT = method("applyCurrent", 4);
    private record DrainSearchNode(BlockPos pos, int pathLength, Direction firstStep) {}

    private static Method method(String name, int parameters) {
        for (var method : FiniteWaterPhysics.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new AssertionError(name);
    }

    private static Object invoke(Method method, Object... args) {
        try { return method.invoke(null, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static int flowBarrierLevel(ServerLevel level, BlockPos from, BlockPos to,
                                       BlockState fromState, BlockState toState, Direction direction, boolean natural) {
        return (int) invoke(BARRIER, level, from, to, fromState, toState, direction, natural);
    }

    private static boolean canFlowBetween(ServerLevel level, BlockPos from, BlockPos to, int amount,
                                          int barrier, BlockState fromState, BlockState toState) {
        return (boolean) invoke(FLOW, level, from, to, amount, barrier, fromState, toState);
    }

    private static boolean isExtendedDrainPath(BlockState state) { return (boolean) invoke(EXTENDED, state); }
    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    private static void applyCurrent(ServerLevel level, BlockPos from, BlockPos to, int amount) {
        invoke(CURRENT, level, from, to, amount);
    }
    static boolean movePuddleTowardDrop(ServerLevel level, BlockPos start) {
        int normalRadius = WaterPhysicsConfig.puddleSearchRadius();
        int maxPathLength = WaterPhysicsConfig.extendedDrainMaxPathLength();
        int maxVisitedCells = WaterPhysicsConfig.extendedDrainMaxVisitedCells();
        Queue<DrainSearchNode> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(new DrainSearchNode(start, 0, null));
        visited.add(start);

        int rotation = Math.floorMod((int) (level.getGameTime() + start.asLong()), HORIZONTAL.length);
        while (!queue.isEmpty()) {
            DrainSearchNode currentNode = queue.remove();
            if (currentNode.pathLength() >= maxPathLength) {
                continue;
            }
            BlockPos current = currentNode.pos();
            BlockState currentState = level.getBlockState(current);
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction direction = HORIZONTAL[(rotation + i) % HORIZONTAL.length];
                BlockPos next = current.relative(direction);
                int nextPathLength = currentNode.pathLength() + 1;
                if (visited.contains(next) || visited.size() >= maxVisitedCells || !isChunkLoaded(level, next)
                        || !mayTraverseDrainPath(
                        start, next, nextPathLength, normalRadius, maxPathLength,
                        pos -> isExtendedDrainPath(level.getBlockState(pos))
                )) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                int entryBarrier = flowBarrierLevel(level, current, next, currentState, nextState, direction, true);
                if (!canFlowBetween(level, current, next, 1, entryBarrier, currentState, nextState)) {
                    continue;
                }
                visited.add(next);

                int nextLevel = getWaterLevel(nextState);
                if (nextLevel < 0 || nextLevel >= MAX_LEVEL - FiniteWaterloggedPlants.occupiedLayers(nextState) || nextLevel > Math.max(1, entryBarrier)) {
                    continue;
                }

                Direction step = currentNode.firstStep() == null ? direction : currentNode.firstStep();
                BlockPos below = next.below();
                int dropLevel = isChunkLoaded(level, below) ? FiniteWaterPhysics.getWaterLevel(level, below) : -1;
                if (dropLevel >= 0 && dropLevel < getWaterCapacity(level, below) && FiniteWaterPhysics.canFlowBetween(level, next, below, 1)) {
                    BlockPos destination = start.relative(step);
                    int destinationLevel = FiniteWaterPhysics.getWaterLevel(level, destination);
                    setWaterLevel(level, start, 0);
                    setWaterLevel(level, destination, destinationLevel + 1);
                    applyCurrent(level, start, destination, 1);
                    return true;
                }
                queue.add(new DrainSearchNode(next, nextPathLength, step));
            }
        }
        return false;
    }

}
