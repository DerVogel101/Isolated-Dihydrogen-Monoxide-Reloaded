package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockStateBase {
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$getFiniteWaterState(CallbackInfoReturnable<FluidState> callbackInfo) {
        BlockState state = (BlockState) (Object) this;
        if (io.github.SirWashington.features.FrozenWaterloggedBlocks.isFrozen(state)
                && FiniteWaterloggedPlants.getLevel(state) == 0) {
            callbackInfo.setReturnValue(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
            return;
        }
        int amount = FiniteWaterloggedPlants.getLevel(state);
        if (amount > 0) {
            callbackInfo.setReturnValue(FiniteWaterloggedPlants.visualFluidState(state, amount));
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$scheduleFiniteWaterShapeTick(
            LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random,
            CallbackInfoReturnable<BlockState> callbackInfo
    ) {
        if (io.github.SirWashington.features.FrozenWaterloggedBlocks.isLocked(level, pos)) {
            immersivefluids$scheduleFiniteWaterTick(level, ticks, pos);
            callbackInfo.setReturnValue((BlockState) (Object) this);
            return;
        }
        immersivefluids$scheduleFiniteWaterTick(level, ticks, pos);
    }

    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void immersivefluids$keepFiniteWaterWhenPlantBreaks(
            LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random,
            CallbackInfoReturnable<BlockState> callbackInfo
    ) {
        int amount = FiniteWaterloggedPlants.getLevel((BlockState) (Object) this);
        if (amount > 0 && callbackInfo.getReturnValue().isAir()) {
            callbackInfo.setReturnValue(FiniteWaterloggedPlants.fluidState(amount).createLegacyBlock());
        }
    }

    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$scheduleFiniteWaterNeighborTick(
            Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston,
            CallbackInfo callbackInfo
    ) {
        if (io.github.SirWashington.features.FrozenWaterloggedBlocks.isLocked(level, pos)) {
            immersivefluids$scheduleFiniteWaterTick(level, level, pos);
            callbackInfo.cancel();
            return;
        }
        immersivefluids$scheduleFiniteWaterTick(level, level, pos);
    }

    private void immersivefluids$scheduleFiniteWaterTick(
            LevelReader level, ScheduledTickAccess ticks, BlockPos pos
    ) {
        FluidState fluidState = ((BlockState) (Object) this).getFluidState();
        if (ModFluids.isFiniteWater(fluidState.getType())) {
            ticks.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        }
    }
}
