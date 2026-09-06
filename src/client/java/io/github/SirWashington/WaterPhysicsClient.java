package io.github.SirWashington;

import io.github.SirWashington.fluid.ModFluids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

public class WaterPhysicsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                io.github.SirWashington.block.WaterPumpBlockEntity.TYPE, WaterPumpRenderer::new);
        FrozenWaterloggedModel.initialize();
        FluidRenderingRegistry.register(
                ModFluids.FINITE_WATER,
                ModFluids.FLOWING_FINITE_WATER,
                new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        BlockTintSources.water()
                )
        );
    }
}
