package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.ModBlockTags;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.fluid.ModFluids;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class FiniteWaterPhysics {
    private static final int MAX_LEVEL = 8;
    private static final VoxelShape[] UPPER_LAYER_MASKS = java.util.stream.IntStream.range(1, MAX_LEVEL)
        .mapToObj(layer -> Shapes.box(0.0D, (double) layer / MAX_LEVEL, 0.0D, 1.0D, 1.0D, 1.0D))
        .toArray(VoxelShape[]::new);
    private static final Set<Block> COPPER_GRATES = Set.copyOf(Blocks.COPPER_GRATE.asList());
    private static final ThreadLocal<BlockPos> WATER_LEVEL_WRITE_POSITION = new ThreadLocal<>();
    private static final ThreadLocal<DrainScratch> DRAIN_SCRATCH = ThreadLocal.withInitial(DrainScratch::new);
    private static final ThreadLocal<BarrierCache> BARRIER_CACHE = ThreadLocal.withInitial(BarrierCache::new);
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DRAIN_SCRATCH.remove();
            BARRIER_CACHE.remove();
        });
        ServerTickEvents.END_LEVEL_TICK.register(FiniteWaterPhysics::tickCurrents);
        ServerTickEvents.END_LEVEL_TICK.register(FiniteWaterSounds::tick);
    }

    public static int getWaterLevel(LevelReader level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return -1;
        }
        BlockState state = level.getBlockState(pos);
        return getWaterLevel(state);
    }

    private static int getWaterLevel(BlockState state) {
        if (state.isAir()) {
            return 0;
        }
        int storedLevel = FiniteWaterloggedPlants.getLevel(state);
        if (storedLevel >= 0) {
            return storedLevel;
        }
        FluidState fluidState = state.getFluidState();
        if (ModFluids.isFiniteWater(fluidState.getType())) {
            return fluidState.getAmount();
        }
        return -1;
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
        int capacity = getWaterCapacity(level, pos);
        boolean repaired = FiniteWaterloggedPlants.getLevel(previous) > capacity;
        if (repaired) {
            // Repair overfilled states saved before capacity validation was added.
            previous = FiniteWaterloggedPlants.withLevel(previous, capacity);
            setWaterloggedBlock(level, pos, previous);
        }
        if (amount > capacity) {
            if (repaired) {
                amount = capacity;
            } else {
                throw new IllegalArgumentException("Water exceeds free space at " + pos);
            }
        }
        boolean wasFiniteWater = ModFluids.isFiniteWater(previous.getFluidState().getType());
        boolean plantHost = FiniteWaterloggedPlants.canHoldFiniteWater(previous);
        if (!previous.isAir() && !wasFiniteWater && !plantHost) {
            throw new IllegalStateException("Cannot place finite water into " + previous + " at " + pos);
        }

        if (plantHost) {
            int previousAmount = FiniteWaterloggedPlants.getLevel(previous);
            int extinguishLevel = WaterPhysicsConfig.extinguishingMinimumLevel();
            boolean extinguished = previousAmount < extinguishLevel
                    && amount >= extinguishLevel
                    && previous.hasProperty(BlockStateProperties.LIT)
                    && previous.getValue(BlockStateProperties.LIT)
                    && FiniteWaterloggedPlants.isExtinguishable(previous);
            if (extinguished) {
                if (previous.getBlock() instanceof CampfireBlock) {
                    CampfireBlock.dowse(null, level, pos, previous);
                } else if (previous.getBlock() instanceof AbstractCandleBlock) {
                    AbstractCandleBlock.extinguish(null, previous, level, pos);
                }
            }
            BlockState updated = FiniteWaterloggedPlants.withLevel(previous, amount);
            if (amount == 0 && wasFiniteWater && !updated.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            setWaterloggedBlock(level, pos, updated);
            if (amount > 0) {
                FluidState fluidState = FiniteWaterloggedPlants.fluidState(amount);
                level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            }
            return;
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

    public static boolean isChangingWaterLevel(BlockPos pos) {
        return pos.equals(WATER_LEVEL_WRITE_POSITION.get());
    }

    public static void setWaterloggedBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos previousWritePosition = WATER_LEVEL_WRITE_POSITION.get();
        WATER_LEVEL_WRITE_POSITION.set(pos.immutable());
        try {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        } finally {
            if (previousWritePosition == null) {
                WATER_LEVEL_WRITE_POSITION.remove();
            } else {
                WATER_LEVEL_WRITE_POSITION.set(previousWritePosition);
            }
        }
    }

    public static boolean placeFullBucket(ServerLevel level, BlockPos pos) {
        int current = getWaterLevel(level, pos);
        if (current < 0) {
            return false;
        }
        int capacity = getWaterCapacity(level, pos);
        if (current == 0 && capacity == MAX_LEVEL) {
            setWaterLevel(level, pos, MAX_LEVEL);
            return true;
        }

        BlockPos[] positions = new BlockPos[BUCKET_OVERFLOW.length];
        int[] amounts = new int[BUCKET_OVERFLOW.length];
        int[] capacities = new int[BUCKET_OVERFLOW.length];
        for (int i = 0; i < BUCKET_OVERFLOW.length; i++) {
            positions[i] = pos.relative(BUCKET_OVERFLOW[i]);
            amounts[i] = canFlowBetween(level, pos, positions[i])
                    ? getWaterLevel(level, positions[i]) : -1;
            capacities[i] = getWaterCapacity(level, positions[i]);
        }

        int[] previous = amounts.clone();
        if (FiniteWaterMath.distribute(current + MAX_LEVEL - capacity, amounts, capacities) != 0) {
            return false;
        }

        setWaterLevel(level, pos, capacity);
        for (int i = 0; i < positions.length; i++) {
            if (amounts[i] >= 0 && amounts[i] != previous[i]) {
                setWaterLevel(level, positions[i], amounts[i]);
                applyCurrent(level, pos, positions[i], amounts[i] - previous[i]);
            }
        }
        return true;
    }

    public static int displaceWater(ServerLevel level, BlockPos origin, int amount) {
        if (amount < 0 || amount > MAX_LEVEL) {
            throw new IllegalArgumentException("Finite-water level must be between 0 and 8: " + amount);
        }

        BlockPos[] positions = new BlockPos[BUCKET_OVERFLOW.length];
        int[] levels = new int[BUCKET_OVERFLOW.length];
        int[] previous = new int[BUCKET_OVERFLOW.length];
        int[] capacities = new int[BUCKET_OVERFLOW.length];
        for (int i = 0; i < BUCKET_OVERFLOW.length; i++) {
            positions[i] = origin.relative(BUCKET_OVERFLOW[i]);
            levels[i] = isChunkLoaded(level, positions[i]) && canFlowBetween(level, origin, positions[i])
                    ? getWaterLevel(level, positions[i]) : -1;
            previous[i] = levels[i];
            capacities[i] = levels[i] >= 0 ? getWaterCapacity(level, positions[i]) : 0;
        }

        int destroyed = FiniteWaterMath.distribute(amount, levels, capacities);
        for (int i = 0; i < positions.length; i++) {
            if (levels[i] >= 0 && levels[i] != previous[i]) {
                setWaterLevel(level, positions[i], levels[i]);
                applyCurrent(level, origin, positions[i], levels[i] - previous[i]);
            }
        }
        return destroyed;
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

        extinguishAdjacentFires(level, pos, center);

        if (WaterPhysicsConfig.doorPressureEnabled()
                && center >= WaterPhysicsConfig.doorPressureRequiredLevelPerHalf()) {
            openPressurizedDoor(level, pos);
        }

        if (disappearsOnForeignFluidContact(level, pos, center)) {
            return;
        }

        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        int belowLevel = getWaterLevel(belowState);
        int belowCapacity = MAX_LEVEL - FiniteWaterloggedPlants.occupiedLayers(belowState);
        if (belowLevel >= 0 && belowLevel < belowCapacity && canFlowBetween(level, pos, below, center)) {
            int moved = Math.min(center, belowCapacity - belowLevel);
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

    static boolean shouldExtinguishFire(BlockState state, int amount) {
        return amount >= WaterPhysicsConfig.extinguishingMinimumLevel()
                && state.getBlock() instanceof BaseFireBlock;
    }

    private static void extinguishAdjacentFires(ServerLevel level, BlockPos waterPos, int amount) {
        if (amount < WaterPhysicsConfig.extinguishingMinimumLevel()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos firePos = waterPos.relative(direction);
            if (shouldExtinguishFire(level.getBlockState(firePos), amount)) {
                level.removeBlock(firePos, false);
            }
        }
    }

    private static void openPressurizedDoor(ServerLevel level, BlockPos waterPos) {
        for (Direction direction : HORIZONTAL) {
            BlockPos candidatePos = waterPos.relative(direction);
            BlockState candidate = level.getBlockState(candidatePos);
            if (!(candidate.getBlock() instanceof DoorBlock)) {
                continue;
            }

            BlockPos lowerPos = candidate.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? candidatePos : candidatePos.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (!(lower.getBlock() instanceof DoorBlock door)
                    || lower.getValue(BlockStateProperties.OPEN)
                    || !ModBlockTags.contains(ModBlockTags.WATER_PRESSURE_OPENABLE_DOORS, lower)) {
                continue;
            }

            Direction facing = lower.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (doorHasRequiredWaterOutside(
                    lowerPos, facing, WaterPhysicsConfig.doorPressureRequiredLevelPerHalf(),
                    pos -> getWaterLevel(level, pos)
            )) {
                door.setOpen(null, level, lower, lowerPos, true);
            }
        }
    }

    static boolean doorHasRequiredWaterOutside(BlockPos lowerPos, Direction facing, int requiredLevel,
                                               ToIntFunction<BlockPos> levelAt) {
        return hasRequiredWaterColumn(lowerPos.relative(facing.getOpposite()), requiredLevel, levelAt);
    }

    private static boolean hasRequiredWaterColumn(BlockPos lowerOutside, int requiredLevel,
                                                  ToIntFunction<BlockPos> levelAt) {
        return levelAt.applyAsInt(lowerOutside) >= requiredLevel
                && levelAt.applyAsInt(lowerOutside.above()) >= requiredLevel;
    }

    private static boolean disappearsOnForeignFluidContact(ServerLevel level, BlockPos pos, int waterLevel) {
        boolean touchesForeignFluid = false;
        BlockState sourceState = level.getBlockState(pos);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!isChunkLoaded(level, neighbor)) {
                continue;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            FluidState fluidState = neighborState.getFluidState();
            if (!isForeignFluid(fluidState)) {
                continue;
            }
            boolean ordinaryContact = sourceState.is(ModBlocks.FINITE_WATER)
                    && neighborState.is(fluidState.createLegacyBlock().getBlock());
            if (!ordinaryContact && !canFlowBetween(level, pos, neighbor, waterLevel)) {
                continue;
            }
            if (fluidState.is(FluidTags.LAVA)) {
                playVanishingEffect(level, pos);
                setWaterLevel(level, pos, 0);
                return true;
            }
            touchesForeignFluid = true;
        }
        if (touchesForeignFluid) {
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

    static boolean isForeignFluid(FluidState state) {
        return !state.isEmpty() && !ModFluids.isFiniteWater(state.getType());
    }

    static boolean canFlowBetween(LevelReader level, BlockPos from, BlockPos to) {
        return flowBarrierLevel(level, from, to, false) < MAX_LEVEL;
    }

    static boolean canFlowBetween(LevelReader level, BlockPos from, BlockPos to, int waterLevel) {
        int barrier = flowBarrierLevel(level, from, to);
        return waterLevel + FiniteWaterloggedPlants.occupiedLayers(level.getBlockState(from)) > barrier
            || canEnterConnectedDrainPath(level, from, to, getWaterLevel(level, to), barrier);
    }

    private static boolean canFlowBetween(LevelReader level, BlockPos from, BlockPos to, int waterLevel,
                                         int barrier, BlockState fromState, BlockState toState) {
        return waterLevel + FiniteWaterloggedPlants.occupiedLayers(fromState) > barrier
            || canEnterConnectedDrainPath(level, from, to,
                level.isOutsideBuildHeight(to) ? -1 : getWaterLevel(toState), barrier);
    }

    static int flowBarrierLevel(LevelReader level, BlockPos from, BlockPos to) {
        return flowBarrierLevel(level, from, to, true);
    }

    private static int flowBarrierLevel(LevelReader level, BlockPos from, BlockPos to,
                                        boolean naturalFlow) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return MAX_LEVEL;
        }
        Direction direction = Direction.getNearest(dx, dy, dz, Direction.NORTH);
        BlockState fromState = level.getBlockState(from);
        BlockState toState = level.getBlockState(to);
        return flowBarrierLevel(level, from, to, fromState, toState, direction, naturalFlow);
    }

    private static int flowBarrierLevel(LevelReader level, BlockPos from, BlockPos to,
                                       BlockState fromState, BlockState toState, Direction direction, boolean naturalFlow) {
        if (PumpFlow.blocksFlow(fromState, direction) || PumpFlow.blocksFlow(toState, direction)) {
            return MAX_LEVEL;
        }
        VoxelShape fromShape = flowShape(fromState, level, from);
        VoxelShape toShape = flowShape(toState, level, to);
        return flowBarrierLevel(
                naturalFlow
                        ? outgoingFlowShape(fromState, fromShape, direction)
                        : FiniteWaterloggedPlants.isExtinguishable(fromState) ? Shapes.empty() : fromShape,
                !naturalFlow && FiniteWaterloggedPlants.isExtinguishable(toState) ? Shapes.empty() : toShape,
                direction
        );
    }

    private static VoxelShape flowShape(BlockState state, LevelReader level, BlockPos pos) {
        // Snow collision is one layer shorter than its actual occupied volume.
        if (FiniteWaterloggedPlants.snowLayers(state) > 0) return state.getShape(level, pos);
        // Grates have full player collision, but their openings admit finite water on every face.
        return COPPER_GRATES.contains(state.getBlock()) ? Shapes.empty() : state.getCollisionShape(level, pos);
    }

    private static boolean canEnterConnectedDrainPath(LevelReader level, BlockPos from, BlockPos to,
                                                       int targetLevel, int barrier) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1 || barrier >= MAX_LEVEL
                || targetLevel < 0 || targetLevel > barrier) {
            return false;
        }
        Direction direction = Direction.getNearest(dx, dy, dz, Direction.NORTH);
        return direction.getAxis().isHorizontal()
                && hasMatchingExtendedDrainHeight(level, from, to, direction);
    }

    private static boolean hasMatchingExtendedDrainHeight(
            LevelReader level, BlockPos from, BlockPos to, Direction direction
    ) {
        BlockState fromState = level.getBlockState(from);
        BlockState toState = level.getBlockState(to);
        if (!isExtendedDrainPath(fromState) || !isExtendedDrainPath(toState)) {
            return false;
        }
        return hasCompatibleDrainHeight(
                flowShape(fromState, level, from),
                flowShape(toState, level, to),
                direction
        );
    }

    static boolean hasCompatibleDrainHeight(VoxelShape from, VoxelShape to, Direction direction) {
        int outgoingHeight = flowBarrierLevel(from, Shapes.empty(), direction);
        int entryHeight = flowBarrierLevel(Shapes.empty(), to, direction);
        return outgoingHeight < MAX_LEVEL && entryHeight <= outgoingHeight;
    }

    private static boolean isExtendedDrainPath(BlockState state) {
        return ModBlockTags.contains(ModBlockTags.EXTENDED_DRAIN_PATH, state)
                || WaterPhysicsConfig.isConfiguredExtendedDrainPath(state);
    }

    static VoxelShape outgoingFlowShape(BlockState state, VoxelShape collisionShape, Direction direction) {
        return outgoingFlowShape(
                collisionShape,
                direction,
                ModBlockTags.contains(ModBlockTags.IGNORES_OWN_SHAPE_FOR_OUTFLOW, state),
                isExtendedDrainPath(state)
        );
    }

    static VoxelShape outgoingFlowShape(VoxelShape collisionShape, Direction direction,
                                        boolean ignoresOwnShape) {
        return outgoingFlowShape(collisionShape, direction, ignoresOwnShape, false);
    }

    static VoxelShape outgoingFlowShape(VoxelShape collisionShape, Direction direction,
                                        boolean ignoresOwnShape, boolean extendedDrainPath) {
        if (!direction.getAxis().isHorizontal()) {
            return collisionShape;
        }
        if (ignoresOwnShape) {
            return Shapes.empty();
        }
        if (!extendedDrainPath) {
            return collisionShape;
        }
        boolean hasOpenFace = flowBarrierLevel(collisionShape, Shapes.empty(), direction) < MAX_LEVEL;
        return hasOpenFace ? Shapes.empty() : collisionShape;
    }

    static boolean canFlowBetween(VoxelShape from, VoxelShape to, Direction direction) {
        return !Shapes.mergedFaceOccludes(from, to, direction);
    }

    static int flowBarrierLevel(VoxelShape from, VoxelShape to, Direction direction) {
        if (from.isEmpty() && to.isEmpty()) {
            return 0;
        }
        BarrierCache cache = BARRIER_CACHE.get();
        int slot = (System.identityHashCode(from) * 31 + System.identityHashCode(to) * 7
                + direction.ordinal()) & (BarrierCache.SIZE - 1);
        if (cache.from[slot] == from && cache.to[slot] == to && cache.directions[slot] == direction) {
            return cache.barriers[slot];
        }
        int barrier = calculateFlowBarrierLevel(from, to, direction);
        cache.from[slot] = from;
        cache.to[slot] = to;
        cache.directions[slot] = direction;
        cache.barriers[slot] = (byte) barrier;
        return barrier;
    }

    private static int calculateFlowBarrierLevel(VoxelShape from, VoxelShape to, Direction direction) {
        if (Shapes.mergedFaceOccludes(from, to, direction)) {
            return MAX_LEVEL;
        }
        if (direction.getAxis() == Direction.Axis.Y) {
            return 0;
        }

        int barrier = 0;
        for (int level = 1; level < MAX_LEVEL; level++) {
            VoxelShape upperHalfSpace = UPPER_LAYER_MASKS[level - 1];
            if (!Shapes.mergedFaceOccludes(Shapes.or(from, upperHalfSpace), to, direction)) {
                break;
            }
            barrier = level;
        }
        return barrier;
    }

    static void applyCurrent(ServerLevel level, BlockPos pos, Vec3 units) {
        FiniteWaterSounds.record(level, pos, Math.abs(units.x()) + Math.abs(units.y()) + Math.abs(units.z()));
        recordCurrent(level, pos, units);
    }

    static void applyPumpCurrent(ServerLevel level, BlockPos pos, Vec3 units, PumpStructure guide) {
        applyPumpCurrent(level, pos, units, guide, guide != null && guide.muted(level));
    }

    static void applyPumpCurrent(ServerLevel level, BlockPos pos, Vec3 units, PumpStructure guide, boolean muted) {
        if (!muted) FiniteWaterSounds.record(level, pos, Math.abs(units.x()) + Math.abs(units.y()) + Math.abs(units.z()));
        recordCurrent(level, pos, units, true, guide);
    }

    private static void applyCurrent(ServerLevel level, BlockPos from, BlockPos to, int movedUnits) {
        if (movedUnits > 0) {
            FiniteWaterSounds.record(level, from, movedUnits);
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
        recordCurrent(level, pos, units, false, null);
    }

    private static void recordCurrent(ServerLevel level, BlockPos pos, Vec3 units, boolean pumpTransfer, PumpStructure guide) {
        if (!WaterPhysicsConfig.currentsEnabled()) {
            return;
        }
        Vec3 limitedUnits = clampCurrentUnits(units);
        if (limitedUnits.lengthSqr() == 0.0D) {
            return;
        }

        long gameTime = level.getGameTime();
        long expiresAt = gameTime + currentDurationTicks(pumpTransfer);
        Map<BlockPos, FlowCurrent> currents = ACTIVE_CURRENTS.computeIfAbsent(
                level, ignored -> new HashMap<>()
        );
        currents.compute(pos.immutable(), (ignored, existing) -> {
            Vec3 combined = existing == null || existing.expiresAt() <= gameTime
                    ? limitedUnits
                    : combineCurrentUnits(existing.units(), limitedUnits);
            return combined.lengthSqr() == 0.0D
                    ? null
                    : new FlowCurrent(combined,
                            existing == null ? expiresAt : Math.max(existing.expiresAt(), expiresAt),
                            pumpTransfer ? expiresAt
                                    : existing == null ? 0 : existing.dryPassageUntil(),
                            pumpTransfer ? guide : existing == null ? null : existing.guide());
        });
    }

    static int currentDurationTicks(boolean pumpTransfer) {
        return pumpTransfer ? WaterPhysicsConfig.pumpTickInterval() : WaterPhysicsConfig.currentDurationTicks();
    }

    private static void tickCurrents(ServerLevel level) {
        Map<BlockPos, FlowCurrent> currents = ACTIVE_CURRENTS.get(level);
        if (currents == null) currents = java.util.Collections.emptyMap();
        if (!WaterPhysicsConfig.currentsEnabled()) {
            ACTIVE_CURRENTS.remove(level);
            return;
        }

        long gameTime = level.getGameTime();
        Map<Entity, Vec3> entityCurrents = new HashMap<>();
        Set<Entity> guidedEntities = new HashSet<>();
        var iterator = currents.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAt() <= gameTime
                    || entry.getValue().dryPassageUntil() <= gameTime && getWaterLevel(level, entry.getKey()) <= 0) {
                iterator.remove();
                continue;
            }
            for (Entity entity : level.getEntities(
                    (Entity) null, new AABB(entry.getKey()), Entity::isPushedByFluid
            )) {
                if (entry.getValue().guide() != null && entry.getValue().dryPassageUntil() > gameTime) guidedEntities.add(entity);
                entityCurrents.merge(
                        entity, entry.getValue().guide() != null && entry.getValue().dryPassageUntil() > gameTime
                                ? PumpFlow.guidedCurrent(entity, entry.getValue().guide(), entry.getValue().units())
                                : entry.getValue().units(), FiniteWaterPhysics::combineCurrentUnits
                );
            }
        }
        if (currents.isEmpty()) {
            ACTIVE_CURRENTS.remove(level);
        }
        PumpCurrentField.collect(level, entityCurrents, guidedEntities);
        entityCurrents.forEach((entity, units) -> pushWithCurrent(entity, units, guidedEntities.contains(entity)));
    }

    private static void pushWithCurrent(Entity entity, Vec3 units, boolean guided) {
        Vec3 strength = currentStrength(units);
        if (entity instanceof LivingEntity living) {
            var depthStrider = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.DEPTH_STRIDER);
            strength = strength.scale(depthStriderCurrentMultiplier(
                    EnchantmentHelper.getEnchantmentLevel(depthStrider, living)));
        }
        // Lifting onto the opening must overcome gravity while crossing a temporarily dry cell.
        // Preserve configured acceleration/speed limits; ordinary currents keep their usual behavior.
        if (guided && strength.y() > 0 && !entity.isInWater()) strength = strength.add(0, entity.getGravity(), 0);
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
        double scale = limitedUnits > 0
                ? WaterPhysicsConfig.upwardCurrentStrength()
                : WaterPhysicsConfig.downwardCurrentStrength();
        return scale * limitedUnits / MAX_LEVEL;
    }

    static double depthStriderCurrentMultiplier(int level) {
        // Vanilla levels I-III reduce finite-water push linearly, up to 65%.
        return 1.0D - 0.65D * Math.clamp(level, 0, 3) / 3.0D;
    }

    static Vec3 currentStrength(Vec3 units) {
        Vec3 limited = clampCurrentUnits(units);
        return new Vec3(
                WaterPhysicsConfig.horizontalCurrentStrength() * limited.x() / MAX_LEVEL,
                verticalCurrentStrength((int) limited.y()),
                WaterPhysicsConfig.horizontalCurrentStrength() * limited.z() / MAX_LEVEL
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
        double maximum = WaterPhysicsConfig.maxHorizontalCurrentSpeed();
        return strength == 0.0D
                ? movement
                : Math.max(-maximum, Math.min(maximum, movement + strength));
    }

    private static double cappedVerticalSpeed(double movement, double strength) {
        return strength > 0.0D
                ? Math.min(WaterPhysicsConfig.maxUpwardCurrentSpeed(), movement + strength)
                : strength < 0.0D
                ? Math.max(-WaterPhysicsConfig.maxDownwardCurrentSpeed(), movement + strength)
                : movement;
    }

    private static void equalizeHorizontally(ServerLevel level, BlockPos centerPos, int center) {
        int rotation = Math.floorMod((int) (level.getGameTime() + centerPos.asLong()), HORIZONTAL.length);
        BlockPos[] positions = new BlockPos[HORIZONTAL.length];
        int[] amounts = new int[HORIZONTAL.length];
        int[] barriers = new int[HORIZONTAL.length];
        int[] floors = new int[HORIZONTAL.length];
        BlockState centerState = level.getBlockState(centerPos);
        for (int i = 0; i < HORIZONTAL.length; i++) {
            Direction direction = HORIZONTAL[(rotation + i) % HORIZONTAL.length];
            positions[i] = centerPos.relative(direction);
            BlockState neighborState = level.getBlockState(positions[i]);
            int barrier = flowBarrierLevel(level, centerPos, positions[i], centerState, neighborState, direction, true);
            amounts[i] = barrier < MAX_LEVEL
                    ? getWaterLevel(neighborState) : -1;
            barriers[i] = canEnterConnectedDrainPath(level, centerPos, positions[i], amounts[i], barrier)
                    ? 0 : barrier;
            floors[i] = FiniteWaterloggedPlants.occupiedLayers(neighborState);
        }

        int[] previous = amounts.clone();
        center = FiniteWaterMath.equalizeFromCenter(center, amounts, barriers,
                FiniteWaterloggedPlants.occupiedLayers(centerState), floors);

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
        int normalRadius = WaterPhysicsConfig.puddleSearchRadius();
        int maxPathLength = WaterPhysicsConfig.extendedDrainMaxPathLength();
        int maxVisitedCells = WaterPhysicsConfig.extendedDrainMaxVisitedCells();
        DrainScratch scratch = DRAIN_SCRATCH.get();
        // World callbacks may re-enter physics before an outer search returns.
        if (scratch.inUse) scratch = new DrainScratch();
        scratch.prepare(maxVisitedCells);
        try {
            LongOpenHashSet visited = scratch.visited;
            int head = 0, tail = 1;
            scratch.positions[0] = start.asLong();
            scratch.depths[0] = 0;
            scratch.firstSteps[0] = -1;
            visited.add(start.asLong());
            int rotation = Math.floorMod((int) (level.getGameTime() + start.asLong()), HORIZONTAL.length);
            while (head < tail) {
                int index = head++;
                int depth = scratch.depths[index];
                if (depth >= maxPathLength) continue;
                BlockPos current = scratch.current.set(scratch.positions[index]);
                BlockState currentState = level.getBlockState(current);
                for (int i = 0; i < HORIZONTAL.length; i++) {
                    int directionIndex = (rotation + i) % HORIZONTAL.length;
                    Direction direction = HORIZONTAL[directionIndex];
                    BlockPos next = scratch.next.setWithOffset(current, direction);
                    long packedNext = next.asLong();
                    int nextPathLength = depth + 1;
                    if (visited.contains(packedNext) || visited.size() >= maxVisitedCells
                            || !isChunkLoaded(level, next) || nextPathLength > maxPathLength) continue;
                    BlockState nextState = level.getBlockState(next);
                    if ((Math.abs(next.getX() - start.getX()) > normalRadius
                            || Math.abs(next.getZ() - start.getZ()) > normalRadius)
                            && !isExtendedDrainPath(nextState)) continue;
                    int entryBarrier = flowBarrierLevel(level, current, next, currentState, nextState, direction, true);
                    if (!canFlowBetween(level, current, next, 1, entryBarrier, currentState, nextState)) continue;
                    visited.add(packedNext);
                    int nextLevel = getWaterLevel(nextState);
                    if (nextLevel < 0 || nextLevel >= MAX_LEVEL - FiniteWaterloggedPlants.occupiedLayers(nextState)
                            || nextLevel > Math.max(1, entryBarrier)) continue;
                    byte firstStep = scratch.firstSteps[index] < 0 ? (byte) directionIndex : scratch.firstSteps[index];
                    Direction step = HORIZONTAL[firstStep];
                    BlockPos below = scratch.below.setWithOffset(next, Direction.DOWN);
                    int dropLevel = isChunkLoaded(level, below) ? getWaterLevel(level, below) : -1;
                    if (dropLevel >= 0 && dropLevel < getWaterCapacity(level, below)
                            && canFlowBetween(level, next, below, 1)) {
                        BlockPos destination = start.relative(step);
                        int destinationLevel = getWaterLevel(level, destination);
                        setWaterLevel(level, start, 0);
                        setWaterLevel(level, destination, destinationLevel + 1);
                        applyCurrent(level, start, destination, 1);
                        return true;
                    }
                    scratch.positions[tail] = packedNext;
                    scratch.depths[tail] = nextPathLength;
                    scratch.firstSteps[tail++] = firstStep;
                }
            }
            return false;
        } finally {
            scratch.inUse = false;
        }
    }

    static boolean mayTraverseDrainPath(BlockPos start, BlockPos next, int pathLength,
                                        int normalRadius, int maxPathLength,
                                        Predicate<BlockPos> isExtendedDrainPath) {
        if (pathLength > maxPathLength) {
            return false;
        }
        return (Math.abs(next.getX() - start.getX()) <= normalRadius
                && Math.abs(next.getZ() - start.getZ()) <= normalRadius)
                || isExtendedDrainPath.test(next);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int getWaterCapacity(LevelReader level, BlockPos pos) {
        return MAX_LEVEL - FiniteWaterloggedPlants.occupiedLayers(level.getBlockState(pos));
    }

    private static final class DrainScratch {
        private final LongOpenHashSet visited = new LongOpenHashSet();
        private final BlockPos.MutableBlockPos current = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        private long[] positions = new long[0];
        private int[] depths = new int[0];
        private byte[] firstSteps = new byte[0];
        private boolean inUse;

        private void prepare(int capacity) {
            if (positions.length < capacity) {
                positions = new long[capacity];
                depths = new int[capacity];
                firstSteps = new byte[capacity];
                visited.ensureCapacity(capacity);
            }
            visited.clear();
            inUse = true;
        }
    }

    private static final class BarrierCache {
        private static final int SIZE = 4096;
        private final VoxelShape[] from = new VoxelShape[SIZE];
        private final VoxelShape[] to = new VoxelShape[SIZE];
        private final Direction[] directions = new Direction[SIZE];
        private final byte[] barriers = new byte[SIZE];
    }

    private record FlowCurrent(Vec3 units, long expiresAt, long dryPassageUntil, PumpStructure guide) {
    }
}
