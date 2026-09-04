package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class MixinFrozenHopperAccess {
    @Inject(method = "getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;",
            at = @At("HEAD"), cancellable = true)
    private static void immersivefluids$noFrozenContainer(Level level, BlockPos pos, BlockState state,
            double x, double y, double z, CallbackInfoReturnable<Container> ci) {
        if (FrozenWaterloggedBlocks.isLocked(level, pos)) ci.setReturnValue(null);
    }
}
