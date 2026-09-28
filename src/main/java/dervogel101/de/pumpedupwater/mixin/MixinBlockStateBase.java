package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterloggedPlants;
import dervogel101.de.pumpedupwater.fluid.ModFluids;
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
    private void pumpedupwater$getFiniteWaterState(CallbackInfoReturnable<FluidState> callbackInfo) {
        BlockState state = (BlockState) (Object) this;
        if (dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.isFrozen(state)
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
    private void pumpedupwater$scheduleFiniteWaterShapeTick(
            LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random,
            CallbackInfoReturnable<BlockState> callbackInfo
    ) {
        if (dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.isLocked(level, pos)) {
            pumpedupwater$scheduleFiniteWaterTick(level, ticks, pos);
            callbackInfo.setReturnValue((BlockState) (Object) this);
            return;
        }
        pumpedupwater$scheduleFiniteWaterTick(level, ticks, pos);
    }

    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$keepFiniteWaterWhenPlantBreaks(
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
    private void pumpedupwater$scheduleFiniteWaterNeighborTick(
            Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston,
            CallbackInfo callbackInfo
    ) {
        if (dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.isLocked(level, pos)) {
            pumpedupwater$scheduleFiniteWaterTick(level, level, pos);
            callbackInfo.cancel();
            return;
        }
        pumpedupwater$scheduleFiniteWaterTick(level, level, pos);
    }

    private void pumpedupwater$scheduleFiniteWaterTick(
            LevelReader level, ScheduledTickAccess ticks, BlockPos pos
    ) {
        FluidState fluidState = ((BlockState) (Object) this).getFluidState();
        if (ModFluids.isFiniteWater(fluidState.getType())) {
            ticks.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        }
    }
}
