package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.AboveIceVertexConsumer;
import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public abstract class MixinFiniteIceFluidRenderer {
    @ModifyVariable(method = "tesselate", at = @At("HEAD"), argsOnly = true)
    private FluidRenderer.Output immersivefluids$waterAboveIce(FluidRenderer.Output output,
            @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) FluidState fluid) {
        if (!ModFluids.isFiniteWater(fluid.getType()) || FiniteWaterloggedPlants.occupiedLayers(state) == 0) return output;
        float floor = (FrozenWaterloggedBlocks.isFrozen(state) ? FrozenWaterloggedBlocks.iceHeight(state)
                : FiniteWaterloggedPlants.occupiedLayers(state)) / 8.0F;
        // FluidRenderer emits block-local vertices. Keep its water out of the frozen volume.
        return layer -> new AboveIceVertexConsumer(output.getBuilder(layer), floor);
    }

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void immersivefluids$noWaterBottomInIce(FluidState fluid, BlockState state, Direction face,
            FluidState neighbor, CallbackInfoReturnable<Boolean> ci) {
        if (face == Direction.DOWN && ModFluids.isFiniteWater(fluid.getType()) && FiniteWaterloggedPlants.occupiedLayers(state) > 0)
            ci.setReturnValue(false);
    }

}
