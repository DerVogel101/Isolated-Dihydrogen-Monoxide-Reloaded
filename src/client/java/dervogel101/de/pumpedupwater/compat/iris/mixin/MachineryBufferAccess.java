package dervogel101.de.pumpedupwater.compat.iris.mixin;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Used only by our emitter; does not change other mods' bulk submissions. */
@Mixin(BufferBuilder.class)
public interface MachineryBufferAccess {
    @Accessor("vertices") int pumpedupwater$vertexCount();
    @Accessor("primitiveTopology") PrimitiveTopology pumpedupwater$topology();
    @Invoker("endLastVertex") void pumpedupwater$finishVertex();
}
