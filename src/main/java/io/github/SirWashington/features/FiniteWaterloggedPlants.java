package io.github.SirWashington.features;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;

public final class FiniteWaterloggedPlants {
    public static final IntegerProperty LEVEL = IntegerProperty.create("finite_water_level", 0, 8);

    private FiniteWaterloggedPlants() {
    }

    public static boolean supports(Block block) {
        return block instanceof VegetationBlock
                || block instanceof GrowingPlantBlock
                || block instanceof GlowLichenBlock
                || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof CactusBlock
                || block instanceof ChorusPlantBlock
                || block instanceof VineBlock
                || block instanceof HangingMossBlock
                || block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock
                || block instanceof CocoaBlock
                || block instanceof FlowerPotBlock
                || block instanceof BigDripleafBlock
                || block instanceof BigDripleafStemBlock
                || block instanceof BaseCoralPlantTypeBlock
                || block instanceof CarpetBlock
                || block instanceof MossyCarpetBlock;
    }

    public static boolean canHoldFiniteWater(BlockState state) {
        return state.hasProperty(LEVEL);
    }

    public static int getLevel(BlockState state) {
        return canHoldFiniteWater(state) ? state.getValue(LEVEL) : -1;
    }

    public static BlockState withLevel(BlockState state, int amount) {
        BlockState result = state.setValue(LEVEL, amount);
        if (result.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result = result.setValue(BlockStateProperties.WATERLOGGED, amount > 0);
        }
        return result;
    }

    public static FluidState fluidState(int amount) {
        return amount == 8
                ? ModFluids.FINITE_WATER.getSource(false)
                : ModFluids.FLOWING_FINITE_WATER.getFlowing(amount, false);
    }
}
