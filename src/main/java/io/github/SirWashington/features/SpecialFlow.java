package io.github.SirWashington.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public class SpecialFlow {

    private static final int MAX_PISTON_PUSHING_DISTANCE = 8;

    public static boolean pushWater(ServerLevel level, List<BlockPos> waterPositions, Direction pistonDirection) {
        Map<BlockPos, Integer> plannedLevels = planPush(
                waterPositions,
                pistonDirection,
                pos -> FiniteWaterPhysics.getWaterLevel(level, pos)
        );
        if (plannedLevels == null) {
            return false;
        }

        applyPlan(level, plannedLevels);
        return true;
    }

    static Map<BlockPos, Integer> planPush(List<BlockPos> waterPositions, Direction pistonDirection,
                                            ToIntFunction<BlockPos> levelAt) {
        Map<BlockPos, Integer> volumes = new LinkedHashMap<>();
        Map<BlockPos, Integer> plannedLevels = new HashMap<>();
        for (BlockPos pos : waterPositions) {
            int volume = levelAt.applyAsInt(pos);
            if (volume > 0) {
                volumes.put(pos, volume);
                plannedLevels.put(pos, 0);
            }
        }

        for (var entry : volumes.entrySet()) {
            Map<BlockPos, Integer> acceptedPlan = findPlan(
                    plannedLevels, entry.getKey(), entry.getValue(), pistonDirection, levelAt
            );
            if (acceptedPlan == null) {
                return null;
            }
            plannedLevels = acceptedPlan;
        }
        return plannedLevels;
    }

    private static void applyPlan(ServerLevel level, Map<BlockPos, Integer> plannedLevels) {
        for (var entry : plannedLevels.entrySet()) {
            if (entry.getValue() == 0) {
                FiniteWaterPhysics.setWaterLevel(level, entry.getKey(), 0);
            }
        }
        for (var entry : plannedLevels.entrySet()) {
            if (entry.getValue() > 0) {
                FiniteWaterPhysics.setWaterLevel(level, entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<BlockPos, Integer> findPlan(Map<BlockPos, Integer> currentPlan, BlockPos waterPos,
                                                    int volume, Direction pistonDirection,
                                                    ToIntFunction<BlockPos> levelAt) {
        for (Direction direction : getPushDirections(pistonDirection)) {
            Map<BlockPos, Integer> candidate = new HashMap<>(currentPlan);
            BlockPos cursor = waterPos;
            int remaining = volume;
            for (int distance = 0; distance < MAX_PISTON_PUSHING_DISTANCE && remaining > 0; distance++) {
                cursor = cursor.relative(direction);
                remaining = simulateAdd(candidate, cursor, remaining, levelAt);
            }
            if (remaining == 0) {
                return candidate;
            }
        }
        return null;
    }

    private static int simulateAdd(Map<BlockPos, Integer> plan, BlockPos pos, int volume,
                                   ToIntFunction<BlockPos> levelAt) {
        int current = plan.containsKey(pos) ? plan.get(pos) : levelAt.applyAsInt(pos);
        if (current < 0) {
            return -1;
        }
        int accepted = Math.min(8 - current, volume);
        if (accepted > 0) {
            plan.put(pos, current + accepted);
        }
        return volume - accepted;
    }

    private static Direction[] getPushDirections(Direction pistonDirection) {
        if (pistonDirection.getAxis() == Direction.Axis.Y) {
            return new Direction[]{pistonDirection, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        }

        return new Direction[]{
                pistonDirection,
                pistonDirection.getClockWise(),
                pistonDirection.getCounterClockWise(),
                Direction.UP,
                Direction.DOWN
        };
    }
}
