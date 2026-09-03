package io.github.SirWashington.features;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;

public final class FiniteWaterloggedPlants {
    public static final IntegerProperty LEVEL = IntegerProperty.create("finite_water_level", 0, 8);
    public static final int EXTINGUISH_LEVEL = 3;

    private FiniteWaterloggedPlants() {
    }

    public static boolean supports(Block block) {
        if (isExcluded(block)) {
            return false;
        }
        return block instanceof SimpleWaterloggedBlock
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

    private static boolean isExcluded(Block block) {
        String id = block.getDescriptionId();
        return block instanceof BarrierBlock
                || block instanceof BeaconBlock
                || block instanceof LeavesBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof WallBlock
                || id.endsWith("glass_pane");
    }

    public static boolean canHoldFiniteWater(BlockState state) {
        return state.hasProperty(LEVEL)
                && (!state.hasProperty(BlockStateProperties.SLAB_TYPE)
                || state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE);
    }

    public static int getLevel(BlockState state) {
        return canHoldFiniteWater(state) ? state.getValue(LEVEL) : -1;
    }

    public static BlockState withLevel(BlockState state, int amount) {
        BlockState result = state.setValue(LEVEL, amount);
        boolean extinguishable = isExtinguishable(result);
        if (result.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result = result.setValue(
                    BlockStateProperties.WATERLOGGED,
                    amount > 0 && (!extinguishable || amount >= EXTINGUISH_LEVEL)
            );
        }
        if (shouldExtinguish(result, amount)) {
            result = result.setValue(BlockStateProperties.LIT, false);
        }
        return result;
    }

    static boolean shouldExtinguish(BlockState state, int amount) {
        return isExtinguishable(state)
                && amount >= EXTINGUISH_LEVEL
                && state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT);
    }

    public static boolean isExtinguishable(BlockState state) {
        return state.getBlock() instanceof AbstractCandleBlock
                || state.getBlock() instanceof CampfireBlock;
    }

    public static FluidState fluidState(int amount) {
        return amount == 8
                ? ModFluids.FINITE_WATER.getSource(false)
                : ModFluids.FLOWING_FINITE_WATER.getFlowing(amount, false);
    }
}
