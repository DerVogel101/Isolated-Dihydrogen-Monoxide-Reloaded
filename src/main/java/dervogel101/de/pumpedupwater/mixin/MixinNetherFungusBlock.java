package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterGrowthDisplacement;
import dervogel101.de.pumpedupwater.features.FiniteWaterloggedPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherFungusBlock.class)
public abstract class MixinNetherFungusBlock {
    @Inject(method = "performBonemeal", at = @At("HEAD"))
    private void pumpedupwater$beginFiniteWaterGrowth(
 ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source, CallbackInfo callbackInfo
    ) {
        FiniteWaterGrowthDisplacement.begin(level);
    }

    @Inject(method = "performBonemeal", at = @At("RETURN"))
    private void pumpedupwater$finishFiniteWaterGrowth(
 ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source, CallbackInfo callbackInfo
    ) {
        BlockState result = level.getBlockState(pos);
        boolean grew = result.getBlock() != state.getBlock()
                || !FiniteWaterloggedPlants.canHoldFiniteWater(result);
        FiniteWaterGrowthDisplacement.finish(level, grew);
    }
}
