package io.github.SirWashington;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.SirWashington.block.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;

public final class WaterValveRenderer implements BlockEntityRenderer<WaterValveBlockEntity, WaterValveRenderer.State> {
    private static final Identifier TEXTURE = TextureAtlas.LOCATION_BLOCKS;
    public WaterValveRenderer(BlockEntityRendererProvider.Context context) { }
    public static final class State extends BlockEntityRenderState {
        int size;
        boolean controller;
        Direction facing;
        double progress;
        final MachineryMesh.ValveCache meshCache = new MachineryMesh.ValveCache();
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
        var sprite = Minecraft.getInstance().getAtlasManager().get(
                new SpriteId(TEXTURE, Identifier.withDefaultNamespace("block/iron_block")));
        var mesh = state.meshCache.get(state.size, state.progress);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poses, RenderTypes.entitySolid(TEXTURE),
                (pose, vertices) -> mesh.emit(pose, vertices, sprite, light));
        poses.popPose();
    }
    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
