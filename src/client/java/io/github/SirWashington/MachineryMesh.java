package io.github.SirWashington;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.SirWashington.block.PumpGeometry;
import io.github.SirWashington.block.ValveGeometry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Immutable local geometry; atlas coordinates and world lighting are resolved only at emission. */
final class MachineryMesh {
    record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int color) { }
    private final Vertex[] vertices;
    private MachineryMesh(List<Vertex> vertices) { this.vertices = vertices.toArray(Vertex[]::new); }
    List<Vertex> vertices() { return List.of(vertices); }

    private static final class Pumps {
        static final List<MachineryMesh> MESHES = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> buildPump(i / 4 + 1, i % 4)).toList();
    }
    private static final class Valves {
        static final List<MachineryMesh> MESHES = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> buildMovingValve(i / 2 + 1, i % 2 * ValveGeometry.DURATION)).toList();
    }
    static MachineryMesh pump(int size, int connections) { return Pumps.MESHES.get((size - 1) * 4 + connections); }
    static final class ValveCache {
        private int size;
        private double progress;
        private MachineryMesh mesh;
        MachineryMesh get(int size, double progress) {
            if (mesh == null || this.size != size || this.progress != progress) {
                mesh = progress == 0 || progress == ValveGeometry.DURATION
                        ? Valves.MESHES.get((size - 1) * 2 + (progress == 0 ? 0 : 1))
                        : buildMovingValve(size, progress);
                this.size = size;
                this.progress = progress;
            }
            return mesh;
        }
    }
    private static final class BulkSupport {
        static final boolean AVAILABLE = version("sodium", "0.9.1")
                && version("iris", "1.11.2");
        private static boolean version(String mod, String expected) {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(mod).map(container ->
                    container.getMetadata().getVersion().getFriendlyString().split("[+]")[0].equals(expected)).orElse(false);
        }
    }
    void emit(PoseStack.Pose pose, VertexConsumer target, TextureAtlasSprite sprite, int light) {
        if (BulkSupport.AVAILABLE && SodiumMachineryEmitter.tryEmit(vertices, pose, target, sprite, light)) return;
        emitOrdinary(pose, target, sprite, light);
    }
    void emitOrdinary(PoseStack.Pose pose, VertexConsumer target, TextureAtlasSprite sprite, int light) {
        for (Vertex p : vertices) {
            target.addVertex(pose, p.x, p.y, p.z).setColor(p.color)
                    .setUv(sprite.getU(p.u), sprite.getV(p.v))
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, p.nx, p.ny, p.nz);
        }
    }
    static MachineryMesh buildPumpHousing(int size, int connections) {
        var result = new ArrayList<Vertex>();
        var housing = PumpGeometry.housing(size, connections);
        float opening = PumpGeometry.openingRadius(size);
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
            var polygon = new ArrayList<AtlasTiling.Vertex>(4);
            for (int i = 0; i < 4; i++) {
                Vec3 point = corners[i];
                polygon.add(new AtlasTiling.Vertex((float)point.x, (float)point.y, (float)point.z, (float)us[i], (float)vs[i]));
            }
            AtlasTiling.split(polygon, tile -> {
                for (var point : tile) result.add(new Vertex(point.x(), point.y(), point.z(), point.u(), point.v(), (float)normal.x, (float)normal.y, (float)normal.z, quad.color()));
            });
        }
        return new MachineryMesh(result);
    }
    static MachineryMesh buildPump(int size, int connections) {
        var result = new ArrayList<>(buildPumpHousing(size, connections).vertices());
        for (var part : PumpGeometry.parts(size, connections, 0, 0)) {
            if (!part.solid()) continue;
            var box = new ValveGeometry.Part(0, 0, 0, part.width(), part.height(), part.depth(),
                    0, part.color(), true);
            var mesh = buildValveParts(List.of(box), List.of(box));
            double angle = Math.toRadians(part.roll()), c = Math.cos(angle), s = Math.sin(angle);
            for (var p : mesh.vertices()) result.add(new Vertex(
                    part.x() + (float)(c * p.x() - s * p.y()),
                    part.y() + (float)(s * p.x() + c * p.y()), part.z() + p.z(), p.u(), p.v(),
                    (float)(c * p.nx() - s * p.ny()), (float)(s * p.nx() + c * p.ny()), p.nz(), p.color()));
        }
        return new MachineryMesh(result);
    }
    static MachineryMesh buildValve(int size, double progress) {
        var parts = ValveGeometry.parts(size, progress);
        return buildValveParts(parts, parts);
    }
    static MachineryMesh buildValveFrame(int size) {
        var parts = ValveGeometry.frameParts(size);
        return buildValveParts(parts, parts);
    }
    static MachineryMesh buildMovingValve(int size, double progress) {
        var all = ValveGeometry.parts(size, progress);
        var moving = new ArrayList<>(all);
        moving.removeAll(ValveGeometry.frameParts(size));
        return buildValveParts(moving, all);
    }
    private static MachineryMesh buildValveParts(List<ValveGeometry.Part> parts, List<ValveGeometry.Part> occluders) {
        var result = new ArrayList<Vertex>();
        for (ValveGeometry.Part p : parts) {
            int hidden = hiddenValveFaces(p, occluders);
            if (hidden == 63) continue;
            var poses = new PoseStack();
            poses.translate(p.x(), p.y(), p.z());
            poses.mulPose(Axis.YP.rotationDegrees((float)p.yaw()));
            var transform = poses.last();
            if ((hidden & 1) == 0) face(result, transform, p, new Vec3(0, 0, p.depth() / 2), new Vec3(p.width(), 0, 0), new Vec3(0, p.height(), 0));
            if ((hidden & 2) == 0) face(result, transform, p, new Vec3(0, 0, -p.depth() / 2), new Vec3(-p.width(), 0, 0), new Vec3(0, p.height(), 0));
            if ((hidden & 4) == 0) face(result, transform, p, new Vec3(p.width() / 2, 0, 0), new Vec3(0, 0, -p.depth()), new Vec3(0, p.height(), 0));
            if ((hidden & 8) == 0) face(result, transform, p, new Vec3(-p.width() / 2, 0, 0), new Vec3(0, 0, p.depth()), new Vec3(0, p.height(), 0));
            if ((hidden & 16) == 0) face(result, transform, p, new Vec3(0, p.height() / 2, 0), new Vec3(p.width(), 0, 0), new Vec3(0, 0, -p.depth()));
            if ((hidden & 32) == 0) face(result, transform, p, new Vec3(0, -p.height() / 2, 0), new Vec3(p.width(), 0, 0), new Vec3(0, 0, p.depth()));

        }
        return new MachineryMesh(result);
    }
    /** Whole faces only: a covering box must extend beyond the face, never merely share its exterior. */
    static int hiddenValveFaces(ValveGeometry.Part p, List<ValveGeometry.Part> parts) {
        double angle = Math.toRadians(p.yaw()), c = Math.cos(angle), s = Math.sin(angle);
        double w = p.width() / 2, h = p.height() / 2, d = p.depth() / 2;
        int hidden = 0;
        // ponytail: bounded O(parts squared) preparation; spatial indexing only if part counts grow.
        for (var q : parts) {
            if (p == q || p.yaw() != q.yaw()) continue;
            // Covering a whole face requires at least two sufficiently large dimensions.
            boolean wide = q.width() >= p.width(), tall = q.height() >= p.height(), deep = q.depth() >= p.depth();
            if (!(wide && tall || wide && deep || tall && deep)) continue;
            double y = q.y() - p.y();
            if (Math.abs(y) > h + q.height() / 2) continue;
            double dx = q.x() - p.x(), dz = q.z() - p.z();
            double x = c * dx - s * dz, z = s * dx + c * dz;
            if (Math.abs(x) > w + q.width() / 2 || Math.abs(z) > d + q.depth() / 2) continue;
            double x0 = x - q.width() / 2, x1 = x + q.width() / 2;
            double y0 = y - q.height() / 2, y1 = y + q.height() / 2;
            double z0 = z - q.depth() / 2, z1 = z + q.depth() / 2;
            boolean coversX = x0 <= -w && x1 >= w;
            boolean coversY = y0 <= -h && y1 >= h;
            boolean coversZ = z0 <= -d && z1 >= d;
            // Strict outward margin protects exposed coplanar faces and floating-point boundaries.
            if (coversX && coversY) {
                if (z0 <= d && z1 > d + 1E-6) hidden |= 1;
                if (z0 < -d - 1E-6 && z1 >= -d) hidden |= 2;
            }
            if (coversY && coversZ) {
                if (x0 <= w && x1 > w + 1E-6) hidden |= 4;
                if (x0 < -w - 1E-6 && x1 >= -w) hidden |= 8;
            }
            if (coversX && coversZ) {
                if (y0 <= h && y1 > h + 1E-6) hidden |= 16;
                if (y0 < -h - 1E-6 && y1 >= -h) hidden |= 32;
            }
            if (hidden == 63) break;
        }
        return hidden;
    }

    /** Tile in physical units: a texture covers half a block, regardless of multiblock size. */
    private static void face(ArrayList<Vertex> result, PoseStack.Pose transform, ValveGeometry.Part part,
                             Vec3 center, Vec3 u, Vec3 v) {
        double width = u.length(), height = v.length();
        Vec3 normal = u.cross(v).normalize(), du = u.normalize(), dv = v.normalize();
        double ox = center.x - u.x * .5 - v.x * .5;
        double oy = center.y - u.y * .5 - v.y * .5;
        double oz = center.z - u.z * .5 - v.z * .5;
        var position = new org.joml.Vector3f();
        var n = transform.normal().transform((float)normal.x, (float)normal.y, (float)normal.z, new org.joml.Vector3f());
        for (double x = 0; x < width - 1E-8; x += .5) for (double y = 0; y < height - 1E-8; y += .5) {
            double w = Math.min(.5, width - x), h = Math.min(.5, height - y);
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 1 || corner == 2 ? w : 0, b = corner >= 2 ? h : 0;
                transform.pose().transformPosition(
                        (float)(ox + du.x * (x + a) + dv.x * (y + b)),
                        (float)(oy + du.y * (x + a) + dv.y * (y + b)),
                        (float)(oz + du.z * (x + a) + dv.z * (y + b)), position);
                result.add(new Vertex(position.x, position.y, position.z, (float)(a * 2), (float)(b * 2), n.x, n.y, n.z, part.color()));
            }
        }
    }
}
