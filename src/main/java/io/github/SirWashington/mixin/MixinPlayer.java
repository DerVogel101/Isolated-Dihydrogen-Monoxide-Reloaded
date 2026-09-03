package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluidTags;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {
    @Shadow
    protected boolean wasUnderwater;

    @Inject(method = "updateIsUnderwater", at = @At("RETURN"), cancellable = true)
    private void immersivefluids$finiteWaterUpdatesPlayerUnderwaterState(
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()
                && ((Player) (Object) this).isEyeInFluid(ModFluidTags.FINITE_WATER)) {
            wasUnderwater = true;
            cir.setReturnValue(true);
        }
    }
}
