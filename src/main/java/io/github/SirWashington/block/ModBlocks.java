package io.github.SirWashington.block;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

public final class ModBlocks {
    public static final Block WATER_PUMP = Registry.register(BuiltInRegistries.BLOCK, ModBlockIds.WATER_PUMP,
            new WaterPumpBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
                    .strength(3F).noOcclusion().dynamicShape().setId(ModBlockIds.WATER_PUMP)));
    public static final Block FINITE_ICE = Registry.register(BuiltInRegistries.BLOCK, ModBlockIds.FINITE_ICE,
            new FiniteIceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ICE).noLootTable().setId(ModBlockIds.FINITE_ICE)));
    public static final Block LAYERED_FINITE_ICE = Registry.register(BuiltInRegistries.BLOCK, ModBlockIds.LAYERED_FINITE_ICE,
            new LayeredFiniteIceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ICE).noOcclusion().noLootTable()
                    .setId(ModBlockIds.LAYERED_FINITE_ICE)));
    public static final Block RAIN_SENSOR = Registry.register(
            BuiltInRegistries.BLOCK, ModBlockIds.RAIN_SENSOR,
            new RainSensorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
                    .strength(1.5F).noOcclusion().setId(ModBlockIds.RAIN_SENSOR))
    );
    public static final Block FINITE_WATER = Registry.register(
            BuiltInRegistries.BLOCK,
            ModBlockIds.FINITE_WATER,
            new FiniteWaterBlock(ModFluids.FINITE_WATER,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).setId(ModBlockIds.FINITE_WATER))
    );

    private ModBlocks() {
    }

    public static void initialize() {
    }

    private static final class FiniteWaterBlock extends LiquidBlock {
        private FiniteWaterBlock(FlowingFluid fluid, Properties properties) {
            super(fluid, properties);
        }
    }
}
