package io.github.SirWashington;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.SirWashington.block.PumpGeometry;
import io.github.SirWashington.block.PumpStructure;
import io.github.SirWashington.block.WaterPumpBlock;
import io.github.SirWashington.block.WaterPumpBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class WaterPumpRenderer implements BlockEntityRenderer<WaterPumpBlockEntity, WaterPumpRenderer.State> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private final ModelPart cube;

    public WaterPumpRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8, -8, -8, 16, 16, 16), PartPose.ZERO);
        cube = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("cube");
    }

    public static final class State extends BlockEntityRenderState {
        int size, connections;
        boolean controller;
        Direction facing;
        float angle, pitch;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(WaterPumpBlockEntity entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, camera, breaking);
        state.size = entity.size(); state.connections = entity.connections(); state.controller = entity.isController();
        state.facing = entity.getBlockState().getValue(WaterPumpBlock.FACING);
        state.angle = Mth.lerp(partialTick, entity.animation.previousAngle, entity.animation.angle);
        state.pitch = Mth.lerp(partialTick, entity.animation.previousPitch, entity.animation.pitch);
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.controller) return;
        Direction u = PumpStructure.right(state.facing), v = PumpStructure.up(state.facing), w = state.facing;
        poses.pushPose();
        poses.translate(.5, .5, .5);
        poses.mulPose(new Matrix4f().m00(u.getStepX()).m01(u.getStepY()).m02(u.getStepZ())
                .m10(v.getStepX()).m11(v.getStepY()).m12(v.getStepZ())
                .m20(w.getStepX()).m21(w.getStepY()).m22(w.getStepZ()));
        poses.translate((state.size - 1) / 2F, (state.size - 1) / 2F, 0);
        var housing = PumpGeometry.housing(state.size, state.connections);
        int light = state.lightCoords;
        float size = state.size;
        float opening = PumpGeometry.openingRadius(state.size);
        collector.submitCustomGeometry(poses, RenderTypes.entitySolid(TEXTURE), (pose, vertices) -> {
            for (PumpGeometry.Quad quad : housing) {
                Vec3 normal = quad.b().subtract(quad.a()).cross(quad.c().subtract(quad.a())).normalize();
                Vec3[] corners = {quad.a(), quad.b(), quad.c(), quad.d()};
                boolean barrel = Math.abs(normal.z) < .001 && Math.abs(Math.hypot(quad.a().x, quad.a().y) - opening) < 1E-6;
                double[] us = new double[4];
                double[] vs = new double[4];
                for (int i = 0; i < 4; i++) {
                    Vec3 point = corners[i];
                    if (barrel) {
                        us[i] = (Math.atan2(point.y, point.x) / (Math.PI * 2) + 1) % 1;
                        vs[i] = point.z + .5;
                    } else if (Math.abs(normal.z) > .5) {
                        us[i] = point.x / size + .5; vs[i] = point.y / size + .5;
                    } else if (Math.abs(normal.x) > .5) {
                        us[i] = point.z + .5; vs[i] = point.y / size + .5;
                    } else {
                        us[i] = point.x / size + .5; vs[i] = point.z + .5;
                    }
                }
                if (barrel && Math.abs(us[0] - us[2]) > .5) {
                    for (int i = 0; i < 4; i++) if (us[i] < .5) us[i] += 1;
                }
                for (int i = 0; i < 4; i++) {
                    Vec3 point = corners[i];
                    vertices.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(quad.color())
                            .setUv((float)us[i], (float)vs[i])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
                }
            }
        });
        for (PumpGeometry.Part part : PumpGeometry.parts(state.size, state.connections, state.angle, state.pitch)) {
            poses.pushPose();
            poses.translate(part.x(), part.y(), part.z());
            poses.mulPose(Axis.ZP.rotationDegrees(part.roll()));
            poses.mulPose(Axis.XP.rotationDegrees(part.pitch()));
            poses.scale(part.width(), part.height(), part.depth());
            collector.submitModelPart(cube, poses, RenderTypes.entitySolid(TEXTURE), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, null, part.color(), state.breakProgress);
            poses.popPose();
        }
        poses.popPose();
    }

    // A controller may be outside the frustum while its three-block-wide rotor is visible.
    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
