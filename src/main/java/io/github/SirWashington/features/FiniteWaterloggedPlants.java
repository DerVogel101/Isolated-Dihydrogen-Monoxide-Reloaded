package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.FiniteIceBlock;
import io.github.SirWashington.block.LayeredFiniteIceBlock;
import io.github.SirWashington.block.ModBlockTags;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;

public final class FiniteWaterloggedPlants {
    public static final IntegerProperty LEVEL = IntegerProperty.create("finite_water_level", 0, 8);
    public static final int DEFAULT_EXTINGUISH_LEVEL = 3;

    private FiniteWaterloggedPlants() {
    }

    public static boolean supports(Block block) {
        return block instanceof SimpleWaterloggedBlock
                || block instanceof BeaconBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof SnowLayerBlock
                || block instanceof VegetationBlock
                || block instanceof GrowingPlantBlock
                || block instanceof GlowLichenBlock
                || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof CactusBlock
                || block instanceof ChorusPlantBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof VineBlock
                || block instanceof HangingMossBlock
                || block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock
                || block instanceof SlabBlock
                || block instanceof StairBlock
                || block instanceof CocoaBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FlowerPotBlock
                || block instanceof BigDripleafBlock
                || block instanceof BigDripleafStemBlock
                || block instanceof BaseCoralPlantTypeBlock
                || block instanceof CarpetBlock
                || block instanceof MossyCarpetBlock
                || block instanceof AbstractBannerBlock
                || block instanceof AbstractCandleBlock
                || block instanceof AbstractCauldronBlock
                || block instanceof AbstractChestBlock
                || block instanceof AbstractSkullBlock
                || block instanceof AnvilBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof BaseRailBlock
                || block instanceof BaseTorchBlock
                || block instanceof BedBlock
                || block instanceof BellBlock
                || block instanceof BrewingStandBlock
                || block instanceof ButtonBlock
                || block instanceof CakeBlock
                || block instanceof CampfireBlock
                || block instanceof ComposterBlock
                || block instanceof DaylightDetectorBlock
                || block instanceof DiodeBlock
                || block instanceof DragonEggBlock
                || block instanceof EnchantingTableBlock
                || block instanceof EndPortalFrameBlock
                || block instanceof EndRodBlock
                || block instanceof FenceGateBlock
                || block instanceof GrindstoneBlock
                || block instanceof HopperBlock
                || block instanceof LadderBlock
                || block instanceof LecternBlock
                || block instanceof LeverBlock
                || block instanceof PistonBaseBlock
                || block instanceof PistonHeadBlock
                || block instanceof RedStoneWireBlock
                || block instanceof SpawnerBlock
                || block instanceof StonecutterBlock
                || block instanceof TripWireBlock
                || block instanceof TripWireHookBlock
                || block instanceof TurtleEggBlock
                || block instanceof WebBlock;
    }

    public static boolean canHoldFiniteWater(BlockState state) {
        return state.hasProperty(LEVEL)
                && !ModBlockTags.contains(ModBlockTags.FINITE_WATERLOGGING_EXCLUDED, state)
                && (!state.hasProperty(BlockStateProperties.SLAB_TYPE)
                || state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE);
    }

    public static int getLevel(BlockState state) {
        return canHoldFiniteWater(state) ? state.getValue(LEVEL) - FrozenWaterloggedBlocks.frozenUnits(state) : -1;
    }

    public static int snowLayers(BlockState state) {
        return state.getBlock() instanceof SnowLayerBlock ? state.getValue(SnowLayerBlock.LAYERS) : 0;
    }

    public static int occupiedLayers(BlockState state) {
        return snowLayers(state) + FiniteIceBlock.frozenLayers(state);
    }

    public static int visualFluidLevel(BlockState state, int amount) {
        if (amount > 0 && snowLayers(state) > 0) {
            return Math.min(8, occupiedLayers(state) + amount + 1);
        }
        if (amount > 0 && FrozenWaterloggedBlocks.isFrozen(state)) {
            return Math.min(8, 1 + visualFluidLevel(state.setValue(FrozenWaterloggedBlocks.FROZEN,
                    FrozenWaterloggedBlocks.Phase.NONE), amount + FrozenWaterloggedBlocks.frozenUnits(state)));
        }
        if (amount > 0 && state.getBlock() instanceof LayeredFiniteIceBlock) {
            return Math.min(8, FiniteIceBlock.frozenLayers(state) + amount + 1);
        }
        if (amount <= 0 || amount == 8 || !isUpwardHalfBlock(state)) {
            return amount;
        }
        // Fluid height is amount / 9, so add one to the requested eighth-based visual level.
        return 4 + (amount + 1) / 2;
    }

    private static boolean isUpwardHalfBlock(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM
                || state.getBlock() instanceof StairBlock
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    public static BlockState withLevel(BlockState state, int amount) {
        if (FrozenWaterloggedBlocks.isFrozen(state)) return FrozenWaterloggedBlocks.withLiquid(state, amount);
        BlockState result = state.setValue(LEVEL, amount);
        boolean extinguishable = isExtinguishable(result);
        if (result.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result = result.setValue(
                    BlockStateProperties.WATERLOGGED,
                    amount > 0 && (!extinguishable || amount >= WaterPhysicsConfig.extinguishingMinimumLevel())
            );
        }
        if (shouldExtinguish(result, amount)) {
            result = result.setValue(BlockStateProperties.LIT, false);
        }
        return result;
    }

    static boolean shouldExtinguish(BlockState state, int amount) {
        return shouldExtinguish(state, amount, isExtinguishable(state));
    }

    static boolean shouldExtinguish(BlockState state, int amount, boolean taggedExtinguishable) {
        return taggedExtinguishable
                && amount >= WaterPhysicsConfig.extinguishingMinimumLevel()
                && state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT);
    }

    public static boolean isExtinguishable(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT)
                && ModBlockTags.contains(ModBlockTags.FINITE_WATER_EXTINGUISHABLE, state);
    }

    public static FluidState fluidState(int amount) {
        return amount == 8
                ? ModFluids.FINITE_WATER.getSource(false)
                : ModFluids.FLOWING_FINITE_WATER.getFlowing(amount, false);
    }

    public static FluidState visualFluidState(BlockState state, int amount) {
        return amount == 8
                ? ModFluids.FINITE_WATER.getSource(false)
                : ModFluids.FLOWING_FINITE_WATER.getFlowing(visualFluidLevel(state, amount), false);
    }
}
