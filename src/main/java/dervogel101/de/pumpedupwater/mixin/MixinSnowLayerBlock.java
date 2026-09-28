package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterloggedPlants;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowLayerBlock {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$requireRoomForSnow(BlockPlaceContext context, CallbackInfoReturnable<BlockState> ci) {
        BlockState proposed = ci.getReturnValue();
        if (proposed == null) return;
        int water = dervogel101.de.pumpedupwater.features.FiniteWaterPhysics.getWaterLevel(context.getLevel(), context.getClickedPos());
        if (water + FiniteWaterloggedPlants.snowLayers(proposed) > 8) ci.setReturnValue(null);
    }
}
