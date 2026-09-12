package io.github.SirWashington.compat.iris.mixin;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Used only by our emitter; does not change other mods' bulk submissions. */
@Mixin(BufferBuilder.class)
public interface MachineryBufferAccess {
    @Accessor("vertices") int immersivefluids$vertexCount();
    @Accessor("primitiveTopology") PrimitiveTopology immersivefluids$topology();
    @Invoker("endLastVertex") void immersivefluids$finishVertex();
}
