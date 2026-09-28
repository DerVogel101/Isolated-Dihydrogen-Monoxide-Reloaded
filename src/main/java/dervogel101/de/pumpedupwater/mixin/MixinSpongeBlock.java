package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpongeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpongeBlock.class)
public abstract class MixinSpongeBlock {

    @Inject(method = "lambda$removeWaterBreadthFirstSearch$1", at = @At("HEAD"), cancellable = true)
    private static void pumpedupwater$absorbFiniteWater(
            BlockPos spongePos,
            Level level,
            BlockPos currentPos,
            CallbackInfoReturnable<BlockPos.TraversalNodeStatus> cir
    ) {
        if (level instanceof ServerLevel serverLevel
                && FiniteWaterPhysics.getWaterLevel(level, currentPos) > 0) {
            FiniteWaterPhysics.setWaterLevel(serverLevel, currentPos, 0);
            cir.setReturnValue(BlockPos.TraversalNodeStatus.ACCEPT);
        }
    }
}
