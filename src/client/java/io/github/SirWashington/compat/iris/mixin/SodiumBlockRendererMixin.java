package io.github.SirWashington.compat.iris.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.SirWashington.compat.iris.IrisMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Override only this quad's encoded data after Iris populated it, before Sodium copies it. */
@Mixin(value = BlockRenderer.class, priority = 900, remap = false)
public abstract class SodiumBlockRendererMixin extends AbstractBlockRenderContext {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Inject(method = "bufferQuad", at = @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;bits()I"))
    private void immersivefluids$surface(MutableQuadViewImpl quad, float[] brightness, Material material,
            CallbackInfo ci, @Local TextureAtlasSprite sprite) {
        var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids == null) return;
        int previous = ids.getOrDefault(state, -1);
        int surface = IrisMaterials.blockSurface(state, sprite, previous);
        if (surface == previous) return;
        for (var vertex : vertices) ((ChunkVertexExtension)vertex).iris$setData(
                (byte)state.getLightEmission(), (byte)0, surface, pos.getX(), pos.getY(), pos.getZ());
    }
}
