package io.github.SirWashington.compat.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import io.github.SirWashington.fluid.ModFluids;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium bypasses the vanilla FluidRenderer where the existing clipping hook lives. */
@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumFluidRendererMixin {
    @Unique private float immersivefluids$floor;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean immersivefluids$smoothWaterLighting(FluidState fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(fluid, tag) || tag == FluidTags.WATER && ModFluids.isFiniteWater(fluid.getType());
    }

    @WrapMethod(method = "render")
    private void immersivefluids$floor(LevelSlice level, BlockState block, FluidState fluid, BlockPos pos, BlockPos offset,
            TranslucentGeometryCollector collector, ChunkModelBuilder builder, Material material,
            ColorProvider<FluidState> color, FluidModel sprites, Operation<Void> original) {
        float previous = immersivefluids$floor;
        try {
            immersivefluids$floor = ModFluids.isFiniteWater(fluid.getType())
                    ? (FrozenWaterloggedBlocks.isFrozen(block) ? FrozenWaterloggedBlocks.iceHeight(block)
                    : FiniteWaterloggedPlants.occupiedLayers(block)) / 8F : 0;
            original.call(level, block, fluid, pos, offset, collector, builder, material, color, sprites);
        } finally { immersivefluids$floor = previous; }
    }
    @Inject(method = "writeQuad", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$clip(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material,
            BlockPos offset, ModelQuadView quad, ModelQuadFacing facing, boolean flip, CallbackInfo ci) {
        if (immersivefluids$floor == 0) return;
        boolean visible = false;
        for (int i = 0; i < 4; i++) visible |= quad.getY(i) > immersivefluids$floor;
        if (!visible) { ci.cancel(); return; }
        for (int i = 0; i < 4; i++) ((ModelQuadViewMutable)quad).setY(i, Math.max(quad.getY(i), immersivefluids$floor));
    }
}
