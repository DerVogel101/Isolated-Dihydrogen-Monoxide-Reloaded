package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class MixinBucketItem {
    @Shadow
    protected abstract void playEmptySound(LivingEntity user, LevelAccessor level, BlockPos pos);

    @ModifyExpressionValue(
            method = "use",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos immersivefluids$targetFiniteWaterloggableBlock(
            BlockPos adjacent, @Local BlockHitResult hitResult, @Local(argsOnly = true) Level level
    ) {
        BucketItem bucket = (BucketItem) (Object) this;
        BlockPos clicked = hitResult.getBlockPos();
        return bucket.getContent() == Fluids.WATER
                && FiniteWaterloggedPlants.canHoldFiniteWater(level.getBlockState(clicked))
                ? clicked : adjacent;
    }

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$placeFiniteWaterInSupportedBlock(
            LivingEntity user, Level level, BlockPos pos, BlockHitResult hitResult,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        BucketItem bucket = (BucketItem) (Object) this;
        if (bucket.getContent() != Fluids.WATER
                || !FiniteWaterloggedPlants.canHoldFiniteWater(level.getBlockState(pos))
                || FiniteWaterPhysics.getWaterLevel(level, pos) < 0) {
            return;
        }
        if (level.isClientSide()) {
            callbackInfo.setReturnValue(true);
            return;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && FiniteWaterPhysics.placeFullBucket(serverLevel, pos)) {
            playEmptySound(user, level, pos);
            callbackInfo.setReturnValue(true);
        } else {
            callbackInfo.setReturnValue(false);
        }
    }
}
