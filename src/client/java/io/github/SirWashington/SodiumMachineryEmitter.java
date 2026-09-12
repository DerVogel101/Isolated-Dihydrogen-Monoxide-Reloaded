package io.github.SirWashington;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.SirWashington.compat.iris.mixin.MachineryBufferAccess;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/** Loaded only for the verified optional Sodium/Iris versions. */
final class SodiumMachineryEmitter {
    static boolean tryEmit(MachineryMesh.Vertex[] vertices, PoseStack.Pose pose, VertexConsumer target,
                           TextureAtlasSprite sprite, int light) {
        if (target.getClass() != BufferBuilder.class
                || !(target instanceof MachineryBufferAccess boundary)
                || boundary.immersivefluids$topology() != PrimitiveTopology.QUADS
                || boundary.immersivefluids$vertexCount() % 4 != 0 || vertices.length % 4 != 0) return false;
        var writer = VertexBufferWriter.tryOf(target);
        if (writer == null || !writer.canUseIntrinsics()) return false;
        // Decide before writing: a partially emitted mesh must never be retried via the fallback.
        try (var stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4, 256 * EntityVertex.STRIDE);
            var position = new Vector3f();
            var normal = new Vector3f();
            for (int first = 0; first < vertices.length; first += 256) {
                int count = Math.min(256, vertices.length - first);
                for (int i = 0; i < count; i++) {
                    var p = vertices[first + i];
                    pose.pose().transformPosition(p.x(), p.y(), p.z(), position);
                    pose.transformNormal(p.nx(), p.ny(), p.nz(), normal);
                    int color = ColorABGR.pack(p.color() >> 16 & 255, p.color() >> 8 & 255, p.color() & 255, p.color() >>> 24);
                    EntityVertex.write(buffer + (long)i * EntityVertex.STRIDE, position.x, position.y, position.z,
                            color, sprite.getU(p.u()), sprite.getV(p.v()), OverlayTexture.NO_OVERLAY,
                            light, NormI8.pack(normal));
                }
                // Iris finalizes ordinary quads on endLastVertex, not on the last attribute write.
                // After a previous bulk batch this instead consumes Iris's skip-finalization flag.
                boundary.immersivefluids$finishVertex();
                writer.push(stack, buffer, count, EntityVertex.FORMAT);
            }
        }
        return true;
    }
}
