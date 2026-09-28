package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterFreezing;
import dervogel101.de.pumpedupwater.features.FiniteWaterRainfall;
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
    private void pumpedupwater$tickFiniteWaterWeather(BlockPos pos, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).below();
        FiniteWaterFreezing.freeze(level, surface);
        FiniteWaterRainfall.tick(level, surface);
    }
}
