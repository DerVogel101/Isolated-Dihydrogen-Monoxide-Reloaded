package dervogel101.de.pumpedupwater.mixin;

import dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Block.class)
public abstract class MixinFrozenBlockDefaults {
    @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
    private BlockState pumpedupwater$defaultUnfrozen(BlockState state) {
        return state.hasProperty(FrozenWaterloggedBlocks.FROZEN)
                ? state.setValue(FrozenWaterloggedBlocks.FROZEN, FrozenWaterloggedBlocks.Phase.NONE) : state;
    }
}
