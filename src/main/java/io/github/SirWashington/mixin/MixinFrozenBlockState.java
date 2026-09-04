package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinFrozenBlockState {
    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$keepIceInPlace(CallbackInfoReturnable<net.minecraft.world.level.material.PushReaction> ci) {
        if (FrozenWaterloggedBlocks.isFrozen((BlockState) (Object) this))
            ci.setReturnValue(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    @Inject(method = {"getShape", "getVisualShape", "getBlockSupportShape"},
            at = @At("RETURN"), cancellable = true)
    private void immersivefluids$iceShape(CallbackInfoReturnable<VoxelShape> ci,
            @Local(argsOnly = true) net.minecraft.world.level.BlockGetter level) {
        // The host computes its opaque lighting cache using this empty getter.
        // Transparent ice must not turn that cached occlusion shape into a solid cube.
        if (level == net.minecraft.world.level.EmptyBlockGetter.INSTANCE) return;
        ci.setReturnValue(FrozenWaterloggedBlocks.withIce((BlockState) (Object) this, ci.getReturnValue()));
    }

    // Name-only selectors resolve just one overload; entity movement uses the context overload.
    @Inject(method = {
            "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
    }, at = @At("RETURN"), cancellable = true)
    private void immersivefluids$collideWithIce(CallbackInfoReturnable<VoxelShape> ci) {
        ci.setReturnValue(FrozenWaterloggedBlocks.withIce((BlockState) (Object) this, ci.getReturnValue()));
    }

    @Inject(method = "canBeReplaced", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$keepFrozenHost(CallbackInfoReturnable<Boolean> ci) {
        if (FrozenWaterloggedBlocks.isFrozen((BlockState) (Object) this)) ci.setReturnValue(false);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$noFrozenItemUse(ItemStack stack, Level level, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        if (FrozenWaterloggedBlocks.isLocked(level, hit.getBlockPos()))
            ci.setReturnValue(FrozenWaterloggedBlocks.isWaterTool(stack) ? InteractionResult.PASS : InteractionResult.FAIL);
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$noFrozenUse(Level level, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> ci) {
        if (FrozenWaterloggedBlocks.isLocked(level, hit.getBlockPos())) ci.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = {"tick", "randomTick"}, at = @At("HEAD"), cancellable = true)
    private void immersivefluids$tickIce(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (FrozenWaterloggedBlocks.isFrozen((BlockState) (Object) this)) {
            FrozenWaterloggedBlocks.tick(level, pos);
            ci.cancel();
        } else if (FrozenWaterloggedBlocks.isLocked(level, pos)) ci.cancel();
    }

    @Inject(method = "getTicker", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$pauseFrozenEntity(CallbackInfoReturnable<BlockEntityTicker<?>> ci) {
        if (FrozenWaterloggedBlocks.isFrozen((BlockState) (Object) this)) ci.setReturnValue(null);
    }
}
