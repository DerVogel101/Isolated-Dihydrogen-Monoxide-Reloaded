package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CropBlock.class, StemBlock.class, PitcherCropBlock.class})
public abstract class MixinCropFertilization {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0))
    private int immersivefluids$fertilizationRoll(RandomSource random, int bound, Operation<Integer> original,
            @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos, @Share("fertilized") LocalRef<BlockState> fertilized) {
        int roll = original.call(random, bound);
        int water = FiniteWaterloggedPlants.getLevel(state);
        if (roll != 0 && WaterPhysicsConfig.cropFertilizationEnabled()
                && WaterPhysicsConfig.cropGrowthSpeedIncrease() > 0D
                && water >= 1 && water <= 2 && level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                && !immersivefluids$isMature(state)
                // P(normal) = 1/bound; converting failed rolls with bonus/(bound-1)
                // adds exactly bonus/bound, including subclass tick gates (e.g. beetroot).
                && random.nextDouble() < WaterPhysicsConfig.cropGrowthSpeedIncrease() / (bound - 1)) {
            fertilized.set(state);
            return 0;
        }
        return roll;
    }

    @Inject(method = "randomTick", at = @At("RETURN"))
    private void immersivefluids$consumeAfterGrowth(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci, @Share("fertilized") LocalRef<BlockState> fertilized) {
        // StemBlock reassigns its state argument while growing; retain the pre-growth state.
        state = fertilized.get();
        if (state == null) {
            return;
        }
        BlockState grown = level.getBlockState(pos);
        boolean advanced = state.getBlock() instanceof CropBlock crop
                ? grown.is(crop.getStateForAge(crop.getAge(state) + 1).getBlock())
                    && (grown.getBlock() != state.getBlock() || crop.getAge(grown) == crop.getAge(state) + 1)
                : grown.getBlock() == state.getBlock()
                    && immersivefluids$age(grown) == immersivefluids$age(state) + 1;
        int water = FiniteWaterloggedPlants.getLevel(grown);
        // Pitchers can reject growth when obstructed. Failed growth never consumes water.
        if (advanced && water > 0) {
            FiniteWaterPhysics.setWaterLevel(level, pos, water - 1);
        }
    }

    @Unique
    private static int immersivefluids$age(BlockState state) {
        return state.getValue(state.getBlock() instanceof StemBlock ? StemBlock.AGE : PitcherCropBlock.AGE);
    }

    @Unique
    private static boolean immersivefluids$isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return immersivefluids$age(state) >= (state.getBlock() instanceof StemBlock ? 7 : 4);
    }
}
