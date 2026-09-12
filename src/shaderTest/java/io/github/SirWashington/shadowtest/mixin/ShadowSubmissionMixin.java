package io.github.SirWashington.shadowtest.mixin;
import io.github.SirWashington.*;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in test: observe actual controller submissions during Iris's shadow pass. */
final class ShadowSubmissionMixin {
    @Mixin(WaterPumpRenderer.class)
    public static abstract class Pump {
        @Inject(method = "submit(Lio/github/SirWashington/WaterPumpRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
        private void record(WaterPumpRenderer.State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
            if (ShadowRenderer.ACTIVE) ShadowProbe.pump(state);
        }
    }
    @Mixin(WaterValveRenderer.class)
    public static abstract class Valve {
        @Inject(method = "submit(Lio/github/SirWashington/WaterValveRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
        private void record(WaterValveRenderer.State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
            if (ShadowRenderer.ACTIVE) ShadowProbe.valve(state);
        }
    }
}
