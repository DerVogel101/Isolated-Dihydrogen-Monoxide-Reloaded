package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluidTags;
import net.fabricmc.fabric.api.registry.fluid.EntityFluidExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isInWater", at = @At("RETURN"), cancellable = true)
    private void immersivefluids$finiteWaterCountsAsWater(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && ((EntityFluidExtension) this).isInFluid(ModFluidTags.FINITE_WATER)) {
            cir.setReturnValue(true);
        }
    }
}
