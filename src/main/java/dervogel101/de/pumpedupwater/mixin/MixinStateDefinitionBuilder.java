package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FiniteWaterloggedPlants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StateDefinition.Builder.class)
public abstract class MixinStateDefinitionBuilder<O, S extends StateHolder<O, S>> {
    @Inject(method = "create", at = @At("RETURN"))
    private void pumpedupwater$logStateCount(CallbackInfoReturnable<StateDefinition<O, S>> callbackInfo) {
        var definition = callbackInfo.getReturnValue();
        if (definition.getOwner() instanceof Block block) {
            dervogel101.de.pumpedupwater.EarlyWaterloggingRules.logStateCount(block, definition.getPossibleStates().size());
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void pumpedupwater$addFiniteWaterLevel(O owner, CallbackInfo callbackInfo) {
        if (owner instanceof Block block && FiniteWaterloggedPlants.supports(block)
                && !dervogel101.de.pumpedupwater.EarlyWaterloggingRules.excludes(block)) {
            ((StateDefinition.Builder<O, S>) (Object) this).add(FiniteWaterloggedPlants.LEVEL,
                    dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.FROZEN);
        }
    }
}
