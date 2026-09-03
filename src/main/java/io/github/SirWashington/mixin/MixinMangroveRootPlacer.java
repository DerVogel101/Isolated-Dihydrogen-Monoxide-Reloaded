package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.rootplacers.MangroveRootPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MangroveRootPlacer.class)
public abstract class MixinMangroveRootPlacer {
    @Inject(method = "canPlaceRoot", at = @At("RETURN"), cancellable = true)
    private void immersivefluids$allowRootsInFiniteWater(
            LevelSimulatedReader level, BlockPos pos, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!callbackInfo.getReturnValue()
                && level.isFluidAtPosition(pos, fluidState -> ModFluids.isFiniteWater(fluidState.getType()))) {
            callbackInfo.setReturnValue(true);
        }
    }
}
