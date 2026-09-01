package io.github.SirWashington.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public class SpecialFlow {

    static int maxPistonPushingDistance = 8;

    public static boolean tryPushWater(ServerLevel level, BlockPos waterPos, Direction direction) {
        Direction pushDirection = findPushDirection(level, waterPos, direction);
        if (pushDirection == null) {
            return false;
        }

        BlockPos newPos = waterPos;
        int currentDistance = 0;
        int volumeToDisplace = NonCachedWater.getWaterLevel(newPos, level);

        while (volumeToDisplace > 0 && currentDistance < maxPistonPushingDistance) {
            newPos = newPos.relative(pushDirection);
            currentDistance++;
            volumeToDisplace = NonCachedWater.addWaterLevelAndReturnRemaining(newPos, volumeToDisplace, level);
        }

        return volumeToDisplace == 0;
    }

    public static boolean checkIfCanPushWater(ServerLevel level, BlockPos waterPos, Direction direction) {
        return findPushDirection(level, waterPos, direction) != null;
    }

    private static Direction findPushDirection(ServerLevel level, BlockPos waterPos, Direction pistonDirection) {
        for (Direction direction : getPushDirections(pistonDirection)) {
            if (canPushWater(level, waterPos, direction)) {
                return direction;
            }
        }
        return null;
    }

    private static boolean canPushWater(ServerLevel level, BlockPos waterPos, Direction direction) {
        BlockPos newPos = waterPos;
        int currentDistance = 0;
        int volumeToDisplace = NonCachedWater.getWaterLevel(newPos, level);

        while (volumeToDisplace > 0 && currentDistance < maxPistonPushingDistance) {
            newPos = newPos.relative(direction);
            if (NonCachedWater.getWaterLevel(newPos, level) < 0) {
                return false;
            }
            currentDistance++;
            volumeToDisplace = NonCachedWater.addWaterLevelAndReturnRemainingImaginary(newPos, volumeToDisplace, level);
        }

        return volumeToDisplace == 0;
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
