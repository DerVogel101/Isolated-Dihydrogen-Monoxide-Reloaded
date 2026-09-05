package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.block.LayeredFiniteIceBlock;
import io.github.SirWashington.features.FiniteWaterGrowthDisplacement;
import io.github.SirWashington.features.FiniteWaterPhysics;
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
        if (FiniteWaterPhysics.isChangingWaterLevel(pos)) return replacement;
        if (!level.isClientSide() && level.isInValidBounds(pos)) {
            BlockState old = level.getBlockState(pos);
            if ((replacement.isAir() || replacement.is(io.github.SirWashington.block.ModBlocks.FINITE_WATER))
                    && FiniteWaterloggedPlants.snowLayers(old) > 0
                    && FiniteWaterloggedPlants.getLevel(old) > 0) {
                return FiniteWaterloggedPlants.fluidState(FiniteWaterloggedPlants.getLevel(old)).createLegacyBlock();
            }
            if (old.is(replacement.getBlock())
                    && io.github.SirWashington.features.FrozenWaterloggedBlocks.isFrozen(old)) return old;
        }
        // Ice phase transitions explicitly set both frozen and liquid amounts.
        if (replacement.getBlock() instanceof LayeredFiniteIceBlock) {
            int layers = replacement.getValue(LayeredFiniteIceBlock.LAYERS);
            int liquid = replacement.getValue(FiniteWaterloggedPlants.LEVEL);
            return liquid + layers <= 8
                    ? replacement
                    : replacement.setValue(FiniteWaterloggedPlants.LEVEL, 8 - layers);
        }
        replacement = FiniteWaterGrowthDisplacement.prepareReplacement(level, pos, replacement);
        if (!level.isInValidBounds(pos) || !FiniteWaterloggedPlants.canHoldFiniteWater(replacement)) {
            return replacement;
        }
        if (level.isClientSide()) {
            return replacement;
        }

        BlockState previous = level.getBlockState(pos);
        int amount;
        if (previous.getBlock() == replacement.getBlock()) {
            amount = FiniteWaterloggedPlants.getLevel(previous);
            if (amount < 0) {
                return replacement.setValue(FiniteWaterloggedPlants.LEVEL, 0);
            }
            if (amount == 0 && !previous.getFluidState().isEmpty()) {
                return replacement;
            }
        } else {
            amount = FiniteWaterloggedPlants.getLevel(previous);
            if (amount < 0) {
                FluidState previousFluid = previous.getFluidState();
                if (!ModFluids.isFiniteWater(previousFluid.getType())) {
                    return replacement.setValue(FiniteWaterloggedPlants.LEVEL, 0);
                }
                amount = previousFluid.getAmount();
            }
        }

        if (amount + FiniteWaterloggedPlants.snowLayers(replacement)
                + io.github.SirWashington.features.FrozenWaterloggedBlocks.frozenUnits(replacement) > 8) {
            return previous;
        }
        BlockState result = FiniteWaterloggedPlants.withLevel(replacement, amount);
        if (amount > 0) {
            FluidState fluidState = FiniteWaterloggedPlants.fluidState(amount);
            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        }
        return result;
    }
}
