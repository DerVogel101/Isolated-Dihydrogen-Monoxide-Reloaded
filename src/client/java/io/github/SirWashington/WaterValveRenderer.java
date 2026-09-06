package io.github.SirWashington;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.SirWashington.block.*;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class WaterValveRenderer implements BlockEntityRenderer<WaterValveBlockEntity, WaterValveRenderer.State> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/block/iron_block.png");
    public WaterValveRenderer(BlockEntityRendererProvider.Context context) { }
    public static final class State extends BlockEntityRenderState {
        int size;
        boolean controller;
        Direction facing;
        double progress;
    }
    @Override
    public State createRenderState() { return new State(); }
    @Override
    public void extractRenderState(WaterValveBlockEntity entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, camera, breaking);
        state.size = entity.size(); state.controller = entity.isController();
        state.facing = entity.getBlockState().getValue(WaterValveBlock.FACING);
        state.progress = entity.progress(partialTick);
    }
    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.controller) return;
        Direction u = SquareStructure.right(state.facing), v = SquareStructure.up(state.facing), w = state.facing;
        poses.pushPose();
        poses.translate(.5, .5, .5);
        poses.mulPose(new Matrix4f().m00(u.getStepX()).m01(u.getStepY()).m02(u.getStepZ())
                .m10(v.getStepX()).m11(v.getStepY()).m12(v.getStepZ())
                .m20(w.getStepX()).m21(w.getStepY()).m22(w.getStepZ()));
        poses.translate((state.size - 1) / 2F, (state.size - 1) / 2F, 0);
        for (ValveGeometry.Part p : ValveGeometry.parts(state.size, state.progress)) {
            poses.pushPose();
            poses.translate(p.x(), p.y(), p.z());
            poses.mulPose(Axis.YP.rotationDegrees((float)p.yaw()));
            int light = state.lightCoords;
            collector.submitCustomGeometry(poses, RenderTypes.entitySolid(TEXTURE), (pose, vertices) -> {
                face(pose, vertices, p, light, new Vec3(0, 0, p.depth() / 2), new Vec3(p.width(), 0, 0), new Vec3(0, p.height(), 0));
                face(pose, vertices, p, light, new Vec3(0, 0, -p.depth() / 2), new Vec3(-p.width(), 0, 0), new Vec3(0, p.height(), 0));
                face(pose, vertices, p, light, new Vec3(p.width() / 2, 0, 0), new Vec3(0, 0, -p.depth()), new Vec3(0, p.height(), 0));
                face(pose, vertices, p, light, new Vec3(-p.width() / 2, 0, 0), new Vec3(0, 0, p.depth()), new Vec3(0, p.height(), 0));
                face(pose, vertices, p, light, new Vec3(0, p.height() / 2, 0), new Vec3(p.width(), 0, 0), new Vec3(0, 0, -p.depth()));
                face(pose, vertices, p, light, new Vec3(0, -p.height() / 2, 0), new Vec3(p.width(), 0, 0), new Vec3(0, 0, p.depth()));
            });
            poses.popPose();
        }
        poses.popPose();
    }
    /** Tile in physical units: a texture covers half a block, regardless of multiblock size. */
    private static void face(PoseStack.Pose pose, VertexConsumer vertices, ValveGeometry.Part part, int light,
                             Vec3 center, Vec3 u, Vec3 v) {
        double width = u.length(), height = v.length();
        Vec3 normal = u.cross(v).normalize(), du = u.normalize(), dv = v.normalize();
        Vec3 origin = center.subtract(u.scale(.5)).subtract(v.scale(.5));
        for (double x = 0; x < width - 1E-8; x += .5) for (double y = 0; y < height - 1E-8; y += .5) {
            double w = Math.min(.5, width - x), h = Math.min(.5, height - y);
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 1 || corner == 2 ? w : 0, b = corner >= 2 ? h : 0;
                Vec3 point = origin.add(du.scale(x + a)).add(dv.scale(y + b));
                vertices.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(part.color())
                        .setUv((float)(a * 2), (float)(b * 2)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
            }
        }
    }
    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
