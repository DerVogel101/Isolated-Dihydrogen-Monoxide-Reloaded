package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.SirWashington.block.FiniteIceBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MixinFrozenIceFriction {
    @ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float immersivefluids$slipperyFrozenSurface(float friction) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // The vanilla half-block-down sample misses thin ice; use the actual supporting block.
        return FiniteIceBlock.frozenLayers(entity.level().getBlockState(entity.getOnPos())) > 0
                ? Blocks.ICE.getFriction() : friction;
    }
}
