package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.block.PumpStructure;
import dervogel101.de.pumpedupwater.block.WaterPumpBlock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class MixinPumpTopology {
    @ModifyExpressionValue(method = "setBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState pumpedupwater$invalidatePumpTopology(BlockState previous,
                                                             @Local(argsOnly = true) BlockState next,
                                                             @Local(argsOnly = true) net.minecraft.core.BlockPos pos) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        dervogel101.de.pumpedupwater.features.PumpManager.changed(chunk.getLevel(), pos, previous, next);
        if ((previous.getBlock() instanceof WaterPumpBlock || next.getBlock() instanceof WaterPumpBlock)
                && (previous.getBlock() != next.getBlock()
                || previous.getValue(WaterPumpBlock.FACING) != next.getValue(WaterPumpBlock.FACING))) {
            PumpStructure.invalidate(chunk.getLevel());
        }
        return previous;
    }
}
