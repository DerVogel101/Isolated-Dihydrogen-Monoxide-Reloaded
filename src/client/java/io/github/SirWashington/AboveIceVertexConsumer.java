package io.github.SirWashington;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Clip the fluid mesh to the unoccupied space, leaving the ice mesh visible. */
public record AboveIceVertexConsumer(VertexConsumer target, float floor) implements VertexConsumer {
    @Override public VertexConsumer addVertex(float x, float y, float z) {
        target.addVertex(x, Math.max(floor, y), z); return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { target.setColor(r, g, b, a); return this; }
    @Override public VertexConsumer setColor(int color) { target.setColor(color); return this; }
    @Override public VertexConsumer setUv(float u, float v) { target.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { target.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { target.setUv2(u, v); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { target.setNormal(x, y, z); return this; }
    @Override public VertexConsumer setLineWidth(float width) { target.setLineWidth(width); return this; }
}
