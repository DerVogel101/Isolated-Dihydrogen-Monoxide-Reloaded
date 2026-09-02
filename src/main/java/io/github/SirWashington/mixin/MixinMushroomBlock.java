package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FiniteWaterGrowthDisplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomBlock.class)
public abstract class MixinMushroomBlock {
    @Inject(method = "growMushroom", at = @At("HEAD"))
    private void immersivefluids$beginFiniteWaterGrowth(
            ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        FiniteWaterGrowthDisplacement.begin(level);
    }

    @Inject(method = "growMushroom", at = @At("RETURN"))
    private void immersivefluids$finishFiniteWaterGrowth(
            ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        FiniteWaterGrowthDisplacement.finish(level, callbackInfo.getReturnValue());
    }
}
