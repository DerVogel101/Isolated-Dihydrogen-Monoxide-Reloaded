package io.github.SirWashington.mixin;

import io.github.SirWashington.features.FiniteWaterloggedPlants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StateDefinition.Builder.class)
public abstract class MixinStateDefinitionBuilder<O, S extends StateHolder<O, S>> {
    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void immersivefluids$addFiniteWaterLevel(O owner, CallbackInfo callbackInfo) {
        if (owner instanceof Block block && FiniteWaterloggedPlants.supports(block)) {
            ((StateDefinition.Builder<O, S>) (Object) this).add(FiniteWaterloggedPlants.LEVEL,
                    io.github.SirWashington.features.FrozenWaterloggedBlocks.FROZEN);
        }
    }
}
