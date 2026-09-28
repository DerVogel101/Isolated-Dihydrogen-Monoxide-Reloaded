package dervogel101.de.pumpedupwater.compat.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dervogel101.de.pumpedupwater.compat.iris.IrisMaterials;
import dervogel101.de.pumpedupwater.fluid.ModFluids;
import net.minecraft.client.Camera;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Camera-only water recognition; never add finite water to the vanilla gameplay tag. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @WrapOperation(method = "getFluidInCamera", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean pumpedupwater$underwater(FluidState fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(fluid, tag) || tag == FluidTags.WATER && IrisMaterials.active() && ModFluids.isFiniteWater(fluid.getType());
    }
}
