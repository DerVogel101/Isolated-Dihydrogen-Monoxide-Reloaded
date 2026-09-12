package io.github.SirWashington.compat.iris.mixin;

import io.github.SirWashington.compat.iris.IrisMaterials;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockMaterialMapping.class, remap = false)
public abstract class BlockMaterialMappingMixin {
    @Inject(method = "createBlockStateIdMap", at = @At("RETURN"))
    private static void immersivefluids$materials(CallbackInfoReturnable<Object2IntMap<BlockState>> cir) {
        IrisMaterials.mapBlocks(cir.getReturnValue());
    }
}
