package io.github.SirWashington;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import java.lang.reflect.Proxy;

/** Geometry checks without opening a Minecraft window. */
public final class IceRenderSelfTest {
    public static void main(String[] args) {
        float[] vertex = new float[3];
        VertexConsumer target = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                    if (method.getName().equals("addVertex")) {
                        for (int i = 0; i < 3; i++) vertex[i] = (float) values[i];
                    }
                    return proxy;
                });
        for (int ice = 1; ice < 8; ice++) {
            float floor = ice / 8F;
            VertexConsumer clipped = new AboveIceVertexConsumer(target, floor);
            for (float y : new float[]{0, 0.001F, 0.25F, 0.75F, 1}) {
                clipped.addVertex(0.125F, y, 0.875F).setColor(0xFFFFFFFF).setUv(0.2F, 0.7F);
                expect(vertex[0] == 0.125F && vertex[2] == 0.875F, "Water X/Z preserved");
                expect(vertex[1] == Math.max(floor, y), "Water never overlaps ice below its surface");
            }
        }
        float[] translation = new float[3];
        MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, values) -> {
                    if (method.getName().equals("translate")) {
                        for (int i = 0; i < 3; i++) translation[i] = (float) values[i];
                    }
                    return proxy;
                });
        for (Vec3 offset : new Vec3[]{Vec3.ZERO, new Vec3(0.2, 0, -0.15), new Vec3(-0.1, -0.2, 0.25)}) {
            expect(FrozenWaterloggedModel.cancelPlantOffset(offset).transform(quad), "Ice quad retained");
            expect(Math.abs(translation[0] + offset.x) < 1E-7 && Math.abs(translation[1] + offset.y) < 1E-7
                    && Math.abs(translation[2] + offset.z) < 1E-7, "Ice offset cancels host offset on all axes");
        }
        float[][] vertices = new float[4][3];
        MutableQuadView snowQuad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, values) -> {
                    int i = (int) values[0];
                    return switch (method.getName()) {
                        case "x" -> vertices[i][0];
                        case "y" -> vertices[i][1];
                        case "z" -> vertices[i][2];
                        case "pos" -> {
                            for (int axis = 0; axis < 3; axis++) vertices[i][axis] = (float) values[axis + 1];
                            yield proxy;
                        }
                        default -> proxy;
                    };
                });
        for (int snow = 1; snow < 8; snow++) {
            for (int i = 0; i < 4; i++) vertices[i] = new float[]{0.25F, i < 2 ? 0 : 1, 0.75F};
            FrozenWaterloggedModel.aboveSnow(snow / 8F).transform(snowQuad);
            for (int i = 0; i < 4; i++) {
                expect(vertices[i][0] == 0.25F && vertices[i][2] == 0.75F, "Snow ice stays centered");
                expect(vertices[i][1] == (i < 2 ? snow / 8F : 1), "Ice starts above snow and retains its top");
            }
        }
        Direction[] cullFace = {Direction.EAST};
        MutableQuadView faceQuad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, values) -> {
                    if (method.getName().equals("cullFace")) {
                        if (values == null || values.length == 0) return cullFace[0];
                        cullFace[0] = (Direction) values[0];
                    }
                    return proxy;
                });
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int snow = 1; snow < 8; snow++) {
                for (int height = snow + 1; height <= 8; height++) {
                    var ice = Shapes.box(0, snow / 8.0, 0, 1, height / 8.0, 1);
                    for (int neighborSnow = 1; neighborSnow <= 8; neighborSnow++) {
                        var neighbor = Shapes.box(0, 0, 0, 1, neighborSnow / 8.0, 1);
                        cullFace[0] = direction;
                        boolean visible = FrozenWaterloggedModel.iceFaceCulling(ice,
                                face -> neighbor.getFaceShape(face.getOpposite())).transform(faceQuad);
                        expect(visible == (height > neighborSnow), "Ice side visibility uses ice height, not host snow height");
                        if (visible) expect(cullFace[0] == null, "Exposed ice bypasses subsequent host culling");
                    }
                }
            }
        }
        System.out.println("FINITE_ICE_RENDER_TEST_PASS");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
