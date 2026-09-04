package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public interface MixinFrozenContainerValidity {
    @Inject(method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;F)Z",
            at = @At("HEAD"), cancellable = true)
    private static void immersivefluids$closeFrozenInventory(BlockEntity entity, Player player, float distance,
            CallbackInfoReturnable<Boolean> ci) {
        if (entity.getLevel() != null && FrozenWaterloggedBlocks.isLocked(entity.getLevel(), entity.getBlockPos()))
            ci.setReturnValue(false);
    }
}
