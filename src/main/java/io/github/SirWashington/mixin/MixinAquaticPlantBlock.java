package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SeagrassBlock.class, TallSeagrassBlock.class, KelpBlock.class})
abstract class MixinAquaticPlantBlock {
    @Redirect(
            method = {
                    "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
                    "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"
            ),
            require = 0
    )
    private boolean immersivefluids$acceptFiniteWater(FluidState fluidState, TagKey<Fluid> tag) {
        return fluidState.is(tag)
                || tag == FluidTags.WATER && ModFluids.isFiniteWater(fluidState.getType());
    }

    @Inject(method = "isValidBonemealTarget", at = @At("RETURN"), cancellable = true, require = 0)
    private void immersivefluids$growSeagrassInFiniteWater(LevelReader level, BlockPos pos, BlockState state,
                                                            CallbackInfoReturnable<Boolean> cir) {
        FluidState fluidState = level.getFluidState(pos.above());
        if (!cir.getReturnValue() && fluidState.isFull() && ModFluids.isFiniteWater(fluidState.getType())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canGrowInto", at = @At("RETURN"), cancellable = true, require = 0)
    private void immersivefluids$growKelpInFiniteWater(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        FluidState fluidState = state.getFluidState();
        if (!cir.getReturnValue() && fluidState.isFull() && ModFluids.isFiniteWater(fluidState.getType())) {
            cir.setReturnValue(true);
        }
    }
}
