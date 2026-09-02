package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.features.FiniteWaterGrowthDisplacement;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Level.class)
public abstract class MixinLevel {
    @ModifyVariable(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private BlockState immersivefluids$keepFiniteWaterWhenPlantChanges(
            BlockState replacement, @Local(argsOnly = true) BlockPos pos
    ) {
        Level level = (Level) (Object) this;
        replacement = FiniteWaterGrowthDisplacement.prepareReplacement(level, pos, replacement);
        if (!level.isInValidBounds(pos) || !FiniteWaterloggedPlants.canHoldFiniteWater(replacement)) {
            return replacement;
        }

        BlockState previous = level.getBlockState(pos);
        if (previous.getBlock() == replacement.getBlock()) {
            return replacement;
        }
        FluidState previousFluid = previous.getFluidState();
        return ModFluids.isFiniteWater(previousFluid.getType())
                ? FiniteWaterloggedPlants.withLevel(replacement, previousFluid.getAmount())
                : replacement;
    }
}
