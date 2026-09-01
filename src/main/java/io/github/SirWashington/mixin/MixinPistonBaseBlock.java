package io.github.SirWashington.mixin;

import io.github.SirWashington.fluid.ModFluids;
import io.github.SirWashington.features.SpecialFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonBaseBlock.class)
public class MixinPistonBaseBlock {

    @Inject(at = @At("HEAD"), method = "moveBlocks", cancellable = true)
    private void moveBlocks(Level level, BlockPos blockPos, Direction direction, boolean extending, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() || !extending) {
            return;
        }

        PistonStructureResolver resolver = new PistonStructureResolver(level, blockPos, direction, true);
        if (!resolver.resolve()) {
            return;
        }

        List<BlockPos> waterBlocks = resolver.getToDestroy().stream()
                .filter(pos -> ModFluids.isFiniteWater(level.getFluidState(pos).getType()))
                .toList();

        if (!SpecialFlow.pushWater((ServerLevel) level, waterBlocks, direction)) {
            cir.setReturnValue(false);
        }
    }
}
