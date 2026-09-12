package io.github.SirWashington.compat.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.SirWashington.compat.iris.IrisMaterials;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {
    @WrapOperation(method = "prepareMainSubmit", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void immersivefluids$surface(VertexConsumer vertices, PoseStack.Pose pose, BakedQuad quad,
                                         QuadInstance instance, Operation<Void> original) {
        var state = CapturedRenderingState.INSTANCE;
        int previous = state.getCurrentRenderedItem();
        if (previous == IrisMaterials.ITEM && IrisMaterials.active()) {
            IrisMaterials.withItemMaterial(IrisMaterials.itemSurface(quad.materialInfo().sprite()),
                    () -> original.call(vertices, pose, quad, instance));
        } else {
            original.call(vertices, pose, quad, instance);
        }
    }
}
