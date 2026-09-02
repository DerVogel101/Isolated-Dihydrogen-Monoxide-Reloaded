package io.github.SirWashington.features;

import io.github.SirWashington.fluid.ModFluids;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

public final class FiniteWaterPhysics {
    private static final int MAX_LEVEL = 8;
    private static final double HORIZONTAL_CURRENT_SCALE = 0.06D;
    private static final double UPWARD_CURRENT_SCALE = 0.06D;
    private static final double DOWNWARD_CURRENT_SCALE = 0.039D;
    private static final double MAX_HORIZONTAL_SPEED = 0.7D;
    private static final double MAX_UPWARD_SPEED = 0.7D;
    private static final double MAX_DOWNWARD_SPEED = -0.3D;
    private static final long CURRENT_DURATION_TICKS = 10L;
    private static final int PUDDLE_RADIUS = 4;
    private static final Map<ServerLevel, Map<BlockPos, FlowCurrent>> ACTIVE_CURRENTS = new WeakHashMap<>();
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] BUCKET_OVERFLOW = {
            Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
    };

    private FiniteWaterPhysics() {
    }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(FiniteWaterPhysics::tickCurrents);
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
                applyCurrent(level, pos, positions[i], amounts[i] - previous[i]);
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

        if (disappearsOnVanillaFluidContact(level, pos)) {
            return;
        }

        BlockPos below = pos.below();
        int belowLevel = getWaterLevel(level, below);
        if (belowLevel >= 0 && belowLevel < MAX_LEVEL) {
            int moved = Math.min(center, MAX_LEVEL - belowLevel);
            setWaterLevel(level, pos, center - moved);
            setWaterLevel(level, below, belowLevel + moved);
            applyCurrent(level, pos, below, moved);
            return;
        }

        if (center == 1 && movePuddleTowardDrop(level, pos)) {
            return;
        }
        equalizeHorizontally(level, pos, center);
    }

    private static boolean disappearsOnVanillaFluidContact(ServerLevel level, BlockPos pos) {
        boolean touchesWater = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
                continue;
            }
            Fluid fluid = level.getFluidState(neighbor).getType();
            if (isVanillaLava(fluid)) {
                playVanishingEffect(level, pos);
                setWaterLevel(level, pos, 0);
                return true;
            }
            touchesWater |= isVanillaWater(fluid);
        }
        if (touchesWater) {
            setWaterLevel(level, pos, 0);
            return true;
        }
        return false;
    }

    private static void playVanishingEffect(ServerLevel level, BlockPos pos) {
        var random = level.getRandom();
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
        for (int i = 0; i < 8; i++) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + random.nextFloat(),
                    pos.getY() + random.nextFloat(),
                    pos.getZ() + random.nextFloat(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    static boolean isVanillaWater(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    static boolean isVanillaLava(Fluid fluid) {
        return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    static void applyCurrent(ServerLevel level, BlockPos pos, Vec3 units) {
        recordCurrent(level, pos, units);
    }

    private static void applyCurrent(ServerLevel level, BlockPos from, BlockPos to, int movedUnits) {
        if (movedUnits > 0) {
            Vec3 units = new Vec3(
                    Integer.signum(to.getX() - from.getX()) * movedUnits,
                    Integer.signum(to.getY() - from.getY()) * movedUnits,
                    Integer.signum(to.getZ() - from.getZ()) * movedUnits
            );
            recordCurrent(level, from, units);
            recordCurrent(level, to, units);
        }
    }

    private static void recordCurrent(ServerLevel level, BlockPos pos, Vec3 units) {
        Vec3 limitedUnits = clampCurrentUnits(units);
        if (limitedUnits.lengthSqr() == 0.0D) {
            return;
        }

        long gameTime = level.getGameTime();
        Map<BlockPos, FlowCurrent> currents = ACTIVE_CURRENTS.computeIfAbsent(
                level, ignored -> new HashMap<>()
        );
        currents.compute(pos.immutable(), (ignored, existing) -> {
            Vec3 combined = existing == null || existing.expiresAt() <= gameTime
                    ? limitedUnits
                    : combineCurrentUnits(existing.units(), limitedUnits);
            return combined.lengthSqr() == 0.0D
                    ? null
                    : new FlowCurrent(combined, gameTime + CURRENT_DURATION_TICKS);
        });
    }

    private static void tickCurrents(ServerLevel level) {
        Map<BlockPos, FlowCurrent> currents = ACTIVE_CURRENTS.get(level);
        if (currents == null) {
            return;
        }

        long gameTime = level.getGameTime();
        Map<Entity, Vec3> entityCurrents = new HashMap<>();
        var iterator = currents.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAt() <= gameTime || getWaterLevel(level, entry.getKey()) <= 0) {
                iterator.remove();
                continue;
            }
            for (Entity entity : level.getEntities(
                    (Entity) null, new AABB(entry.getKey()), Entity::isPushedByFluid
            )) {
                entityCurrents.merge(
                        entity, entry.getValue().units(), FiniteWaterPhysics::combineCurrentUnits
                );
            }
        }
        if (currents.isEmpty()) {
            ACTIVE_CURRENTS.remove(level);
        }
        entityCurrents.forEach(FiniteWaterPhysics::pushWithCurrent);
    }

    private static void pushWithCurrent(Entity entity, Vec3 units) {
        Vec3 strength = currentStrength(units);
        Vec3 movement = entity.getDeltaMovement();
        double nextX = cappedHorizontalSpeed(movement.x(), strength.x());
        double nextY = cappedVerticalSpeed(movement.y(), strength.y());
        double nextZ = cappedHorizontalSpeed(movement.z(), strength.z());
        entity.setDeltaMovement(nextX, nextY, nextZ);
        entity.needsSync = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    static double verticalCurrentStrength(int signedUnits) {
        int limitedUnits = Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, signedUnits));
        double scale = limitedUnits > 0 ? UPWARD_CURRENT_SCALE : DOWNWARD_CURRENT_SCALE;
        return scale * limitedUnits / MAX_LEVEL;
    }

    static Vec3 currentStrength(Vec3 units) {
        Vec3 limited = clampCurrentUnits(units);
        return new Vec3(
                HORIZONTAL_CURRENT_SCALE * limited.x() / MAX_LEVEL,
                verticalCurrentStrength((int) limited.y()),
                HORIZONTAL_CURRENT_SCALE * limited.z() / MAX_LEVEL
        );
    }

    static int combineVerticalCurrents(int first, int second) {
        return (int) combineCurrentComponent(first, second);
    }

    static Vec3 combineCurrentUnits(Vec3 first, Vec3 second) {
        return new Vec3(
                combineCurrentComponent(first.x(), second.x()),
                combineCurrentComponent(first.y(), second.y()),
                combineCurrentComponent(first.z(), second.z())
        );
    }

    private static Vec3 clampCurrentUnits(Vec3 units) {
        return new Vec3(
                Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, units.x())),
                Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, units.y())),
                Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, units.z()))
        );
    }

    private static double combineCurrentComponent(double first, double second) {
        if (Math.signum(first) == Math.signum(second)) {
            return Math.abs(first) >= Math.abs(second) ? first : second;
        }
        return Math.max(-MAX_LEVEL, Math.min(MAX_LEVEL, first + second));
    }

    private static double cappedHorizontalSpeed(double movement, double strength) {
        return strength == 0.0D
                ? movement
                : Math.max(-MAX_HORIZONTAL_SPEED, Math.min(MAX_HORIZONTAL_SPEED, movement + strength));
    }

    private static double cappedVerticalSpeed(double movement, double strength) {
        return strength > 0.0D
                ? Math.min(MAX_UPWARD_SPEED, movement + strength)
                : strength < 0.0D
                ? Math.max(MAX_DOWNWARD_SPEED, movement + strength)
                : movement;
    }

    private static void equalizeHorizontally(ServerLevel level, BlockPos centerPos, int center) {
        int rotation = Math.floorMod((int) (level.getGameTime() + centerPos.asLong()), HORIZONTAL.length);
        BlockPos[] positions = new BlockPos[HORIZONTAL.length];
        int[] amounts = new int[HORIZONTAL.length];
        for (int i = 0; i < HORIZONTAL.length; i++) {
            positions[i] = centerPos.relative(HORIZONTAL[(rotation + i) % HORIZONTAL.length]);
            amounts[i] = getWaterLevel(level, positions[i]);
        }

        int[] previous = amounts.clone();
        center = FiniteWaterMath.equalizeFromCenter(center, amounts);

        for (int i = 0; i < HORIZONTAL.length; i++) {
            if (amounts[i] >= 0 && amounts[i] != getWaterLevel(level, positions[i])) {
                setWaterLevel(level, positions[i], amounts[i]);
                int moved = amounts[i] - previous[i];
                if (moved > 0) {
                    applyCurrent(level, centerPos, positions[i], moved);
                } else if (moved < 0) {
                    applyCurrent(level, positions[i], centerPos, -moved);
                }
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
                    applyCurrent(level, start, destination, 1);
                    return true;
                }
                queue.add(next);
            }
        }
        return false;
    }

    private record FlowCurrent(Vec3 units, long expiresAt) {
    }
}
