package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkCache {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void immersivefluids$wakeFiniteWaterAfterBlockChange(BlockPos pos, CallbackInfo callbackInfo) {
        FluidState fluidState = level.getFluidState(pos);
        if (ModFluids.isFiniteWater(fluidState.getType())) {
            scheduleFiniteWater(pos);
        } else {
            for (Direction direction : Direction.values()) {
                scheduleFiniteWater(pos.relative(direction));
            }
        }
    }

    private void scheduleFiniteWater(BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        if (ModFluids.isFiniteWater(fluidState.getType())) {
            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        }
    }
}
