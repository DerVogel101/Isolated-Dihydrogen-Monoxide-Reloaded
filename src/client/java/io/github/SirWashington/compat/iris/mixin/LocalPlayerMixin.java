package io.github.SirWashington.compat.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.SirWashington.compat.iris.IrisMaterials;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep underwater vision in step with the camera without changing gameplay fluid tags. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @WrapOperation(method = {"tick", "getWaterVision"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean immersivefluids$waterVision(LocalPlayer player, TagKey<Fluid> tag, Operation<Boolean> original) {
        if (original.call(player, tag)) return true;
        if (tag != FluidTags.WATER || !IrisMaterials.active()) return false;
        var eye = player.getEyePosition();
        var pos = BlockPos.containing(eye);
        var fluid = player.level().getFluidState(pos);
        return ModFluids.isFiniteWater(fluid.getType())
                && eye.y < pos.getY() + fluid.getHeight(player.level(), pos);
    }
}
