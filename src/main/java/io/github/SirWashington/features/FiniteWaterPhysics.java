package io.github.SirWashington.features;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class FiniteWaterPhysics {
    private static final int MAX_LEVEL = 8;
    private static final int PUDDLE_RADIUS = 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] BUCKET_OVERFLOW = {
            Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
    };

    private FiniteWaterPhysics() {
    }

    public static int getWaterLevel(LevelReader level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return -1;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 0;
        }
        FluidState fluidState = state.getFluidState();
        return ModFluids.isFiniteWater(fluidState.getType()) ? fluidState.getAmount() : -1;
    }

    public static void setWaterLevel(ServerLevel level, BlockPos pos, int amount) {
        if (amount < 0 || amount > MAX_LEVEL) {
            throw new IllegalArgumentException("Finite-water level must be between 0 and 8: " + amount);
        }
        if (level.isOutsideBuildHeight(pos)) {
            if (amount == 0) {
                return;
            }
            throw new IllegalStateException("Cannot place finite water outside build height at " + pos);
        }

        BlockState previous = level.getBlockState(pos);
        boolean wasFiniteWater = ModFluids.isFiniteWater(previous.getFluidState().getType());
        if (!previous.isAir() && !wasFiniteWater) {
            throw new IllegalStateException("Cannot place finite water into " + previous + " at " + pos);
        }

        if (amount == 0) {
            if (wasFiniteWater) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }

        FluidState fluidState = amount == MAX_LEVEL
                ? ModFluids.FINITE_WATER.getSource(false)
                : ModFluids.FLOWING_FINITE_WATER.getFlowing(amount, false);
        level.setBlock(pos, fluidState.createLegacyBlock(), Block.UPDATE_ALL);
        level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
    }

    public static boolean placeFullBucket(ServerLevel level, BlockPos pos) {
        int current = getWaterLevel(level, pos);
        if (current < 0) {
            return false;
        }
        if (current == 0) {
            setWaterLevel(level, pos, MAX_LEVEL);
            return true;
        }

        BlockPos[] positions = new BlockPos[BUCKET_OVERFLOW.length];
        int[] amounts = new int[BUCKET_OVERFLOW.length];
        for (int i = 0; i < BUCKET_OVERFLOW.length; i++) {
            positions[i] = pos.relative(BUCKET_OVERFLOW[i]);
            amounts[i] = getWaterLevel(level, positions[i]);
        }

        int[] previous = amounts.clone();
        if (FiniteWaterMath.distribute(current, amounts) != 0) {
            return false;
        }

        setWaterLevel(level, pos, MAX_LEVEL);
        for (int i = 0; i < positions.length; i++) {
            if (amounts[i] >= 0 && amounts[i] != previous[i]) {
                setWaterLevel(level, positions[i], amounts[i]);
            }
        }
        return true;
    }

    public static void tick(ServerLevel level, BlockPos pos) {
        int center = getWaterLevel(level, pos);
        if (center <= 0) {
            return;
        }
        if (pos.getY() <= level.getMinY()) {
            setWaterLevel(level, pos, 0);
            return;
        }

        BlockPos below = pos.below();
        if (level.getBlockState(below).is(Blocks.LAVA)) {
            level.setBlock(below, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }

        int belowLevel = getWaterLevel(level, below);
        if (belowLevel >= 0 && belowLevel < MAX_LEVEL) {
            int moved = Math.min(center, MAX_LEVEL - belowLevel);
            setWaterLevel(level, pos, center - moved);
            setWaterLevel(level, below, belowLevel + moved);
            return;
        }

        if (center == 1 && movePuddleTowardDrop(level, pos)) {
            return;
        }
        equalizeHorizontally(level, pos, center);
    }

    private static void equalizeHorizontally(ServerLevel level, BlockPos centerPos, int center) {
        int rotation = Math.floorMod((int) (level.getGameTime() + centerPos.asLong()), HORIZONTAL.length);
        BlockPos[] positions = new BlockPos[HORIZONTAL.length];
        int[] amounts = new int[HORIZONTAL.length];
        for (int i = 0; i < HORIZONTAL.length; i++) {
            positions[i] = centerPos.relative(HORIZONTAL[(rotation + i) % HORIZONTAL.length]);
            amounts[i] = getWaterLevel(level, positions[i]);
        }

        center = FiniteWaterMath.equalizeFromCenter(center, amounts);

        for (int i = 0; i < HORIZONTAL.length; i++) {
            if (amounts[i] >= 0 && amounts[i] != getWaterLevel(level, positions[i])) {
                setWaterLevel(level, positions[i], amounts[i]);
            }
        }
        if (center != getWaterLevel(level, centerPos)) {
            setWaterLevel(level, centerPos, center);
        }
    }

    private static boolean movePuddleTowardDrop(ServerLevel level, BlockPos start) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, Direction> firstStep = new HashMap<>();
        queue.add(start);
        visited.add(start);

        int rotation = Math.floorMod((int) (level.getGameTime() + start.asLong()), HORIZONTAL.length);
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction direction = HORIZONTAL[(rotation + i) % HORIZONTAL.length];
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - start.getX()) > PUDDLE_RADIUS
                        || Math.abs(next.getZ() - start.getZ()) > PUDDLE_RADIUS
                        || !visited.add(next)) {
                    continue;
                }

                int nextLevel = getWaterLevel(level, next);
                if (nextLevel < 0 || nextLevel > 1) {
                    continue;
                }

                Direction step = current.equals(start) ? direction : firstStep.get(current);
                firstStep.put(next, step);
                int dropLevel = getWaterLevel(level, next.below());
                if (dropLevel >= 0 && dropLevel < MAX_LEVEL) {
                    BlockPos destination = start.relative(step);
                    int destinationLevel = getWaterLevel(level, destination);
                    setWaterLevel(level, start, 0);
                    setWaterLevel(level, destination, destinationLevel + 1);
                    return true;
                }
                queue.add(next);
            }
        }
        return false;
    }
}
