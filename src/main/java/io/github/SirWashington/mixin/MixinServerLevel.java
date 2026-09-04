package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FiniteWaterFreezing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {
    @Inject(method = "tickPrecipitation", at = @At("HEAD"))
    private void immersivefluids$freezeFiniteWater(BlockPos pos, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        FiniteWaterFreezing.freeze(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).below());
    }
}
