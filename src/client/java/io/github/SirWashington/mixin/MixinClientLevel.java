package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
    @ModifyVariable(method = "syncBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockState immersivefluids$keepPredictedPotWater(
            BlockState acknowledged, @Local(argsOnly = true) BlockPos pos
    ) {
        BlockState predicted = ((ClientLevel) (Object) this).getBlockState(pos);
        int predictedLevel = FiniteWaterloggedPlants.getLevel(predicted);
        if (predicted.getBlock() == acknowledged.getBlock()
                && predicted.getBlock() instanceof FlowerPotBlock
                && predictedLevel > 0
                && FiniteWaterloggedPlants.getLevel(acknowledged) == 0) {
            return FiniteWaterloggedPlants.withLevel(acknowledged, predictedLevel);
        }
        return acknowledged;
    }
}
