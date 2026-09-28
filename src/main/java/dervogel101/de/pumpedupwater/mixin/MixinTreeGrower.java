package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterGrowthDisplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeGrower.class)
public abstract class MixinTreeGrower {
    @Inject(method = "growTree", at = @At("HEAD"))
    private void pumpedupwater$beginFiniteWaterGrowth(
            ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        FiniteWaterGrowthDisplacement.begin(level);
    }

    @Inject(method = "growTree", at = @At("RETURN"))
    private void pumpedupwater$finishFiniteWaterGrowth(
            ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        FiniteWaterGrowthDisplacement.finish(level, callbackInfo.getReturnValue());
    }
}
