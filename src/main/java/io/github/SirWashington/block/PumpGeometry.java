package io.github.SirWashington.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Geometry in block units, centered on a stage, with its outlet along local +Z. */
public final class PumpGeometry {
    public record Part(float x, float y, float z, float width, float height, float depth,
                       float roll, float pitch, int color, boolean solid) { }
    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) { }
    private static final int FRAME = 0xFF39454D, EDGE = 0xFF65747D, FILL = 0xFF899399, SUPPORT = 0xFF39454D;
    private static final int MOTOR = 0xFFB47846, BLADE = 0xFFC6D6DA;
    private static final int SIDES = 64;
    private static final List<List<Quad>> HOUSINGS = java.util.stream.IntStream.range(0, 12)
            .mapToObj(i -> buildHousing(i / 4 + 1, i % 4)).toList();

    private PumpGeometry() { }

    public static List<Part> parts(int size, int connections, float angle, float poweredPitch) {
        var parts = new ArrayList<Part>();
        float half = size / 2F;
        for (int i = 0; i < 4; i++) {
            radial(parts, half / 2, half - .10F, .07F, -.30F, i * 90, SUPPORT);
        }
        if (size == 3) {
            for (int i = 0; i < 4; i++) {
                float length = (half - .10F) * (float)Math.sqrt(2);
                radial(parts, length / 2, length, .07F, -.30F, 45 + i * 90, SUPPORT);
            }
        }
        float hub = .26F + size * .04F;
        parts.add(new Part(0, 0, -.18F, hub, hub, .40F, 0, 0, MOTOR, true));
        parts.add(new Part(0, 0, .08F, hub * .72F, hub * .72F, .18F, angle, 0, SUPPORT, false));
        parts.add(new Part(0, 0, .19F, hub * .5F, hub * .5F, .08F, angle, 0, EDGE, false));
        float root = hub * .35F;
        float sweep = openingRadius(size) - .02F;
        float tip = (float)Math.sqrt(sweep * sweep - size * size * .01F);
        for (int i = 0; i < 6; i++) {
            float roll = angle + i * 60;
            double a = Math.toRadians(roll);
            float center = (root + tip) / 2;
            // Blades hinge around their radial length: feathered along the duct when idle.
            parts.add(new Part((float)Math.cos(a) * center, (float)Math.sin(a) * center, .08F,
                    tip - root, size * .20F, .025F, roll, 90 - poweredPitch * 45, BLADE, false));
        }
        return parts;
    }

    public static float openingRadius(int size) { return size / 2F - .045F; }

    public static List<Quad> housing(int size, int connections) { return HOUSINGS.get((size - 1) * 4 + connections); }

    private static Vec3 circle(int i, double radius, double z) {
        double angle = (i % SIDES) * Math.PI * 2 / SIDES;
        return new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, z);
    }

    private static Vec3 square(int i, double half, double z) {
        Vec3 unit = circle(i, 1, z);
        double scale = half / Math.max(Math.abs(unit.x), Math.abs(unit.y));
        return new Vec3(unit.x * scale, unit.y * scale, z);
    }

    private static List<Quad> buildHousing(int size, int connections) {
        var quads = new ArrayList<Quad>();
        double half = size / 2.0, radius = openingRadius(size);
        double start = (connections & 1) != 0 ? -.5 : -.44;
        double end = (connections & 2) != 0 ? .5 : .44;
        for (int i = 0; i < SIDES; i++) {
            int j = i + 1;
            // One inward-facing barrel wall, with no intersecting ring segments.
            quads.add(new Quad(circle(i, radius, start), circle(i, radius, end),
                    circle(j, radius, end), circle(j, radius, start), FRAME));
            for (int side = 0; side < 2; side++) {
                if ((connections & (1 << side)) != 0) continue; // No coincident caps between connected stages.
                double faceZ = side == 0 ? start : end;
                Vec3[] a = {circle(i, radius, faceZ), circle(i, radius + .008, faceZ),
                        square(i, half - .025, faceZ), square(i, half, faceZ)};
                Vec3[] b = {circle(j, radius, faceZ), circle(j, radius + .008, faceZ),
                        square(j, half - .025, faceZ), square(j, half, faceZ)};
                for (int band = 0; band < 2; band++) {
                    int color = band == 1 ? FILL : FRAME;
                    quads.add(side == 1 ? new Quad(a[band], a[band + 1], b[band + 1], b[band], color)
                            : new Quad(b[band], b[band + 1], a[band + 1], a[band], color));
                }
            }
        }
        // Flat strips need one quad per square side, not one per circular segment.
        double[] z = {start, start + .025, end - .025, end};
        for (int i = SIDES / 8; i < SIDES; i += SIDES / 4) {
            int j = i + SIDES / 4;
            for (int band = 0; band < 3; band++) {
                quads.add(new Quad(square(i, half, z[band]), square(j, half, z[band]),
                        square(j, half, z[band + 1]), square(i, half, z[band + 1]), band == 1 ? FRAME : EDGE));
            }
            for (int side = 0; side < 2; side++) {
                if ((connections & (1 << side)) != 0) continue;
                double faceZ = side == 0 ? start : end;
                Vec3 a = square(i, half - .025, faceZ), b = square(i, half, faceZ);
                Vec3 c = square(j, half, faceZ), d = square(j, half - .025, faceZ);
                quads.add(side == 1 ? new Quad(a, b, c, d, FRAME) : new Quad(d, c, b, a, FRAME));
            }
        }
        return List.copyOf(quads);
    }

    private static void radial(List<Part> parts, float center, float length, float thickness, float z, float roll, int color) {
        double a = Math.toRadians(roll);
        parts.add(new Part((float)Math.cos(a) * center, (float)Math.sin(a) * center,
                z, length, thickness, .09F, roll, 0, color, true));
    }

    public static VoxelShape collision(int size, int column, int row, Direction facing) {
        VoxelShape shape = Shapes.empty();
        Direction right = PumpStructure.right(facing), up = PumpStructure.up(facing);
        // Only the casing collides. The visible motor, supports and blades remain unchanged.
        var solids = new ArrayList<Part>();
        // Pixel-sized collision strips fill the same square corners as the circular housing.
        double radius = openingRadius(size), half = size / 2.0;
        for (int i = 0; i < size * 16; i++) {
            double left = -half + i / 16.0, rightEdge = left + 1 / 16.0;
            double nearestX = Math.max(0, Math.max(left, -rightEdge));
            double openY = Math.sqrt(Math.max(0, radius * radius - nearestX * nearestX));
            for (int sign : new int[]{-1, 1}) {
                solids.add(new Part((float)(left + rightEdge) / 2, (float)(sign * (half + openY) / 2), 0,
                        1 / 16F, (float)(half - openY), 1, 0, 0, FRAME, true));
            }
        }
        for (Part part : solids) {
            if (!part.solid) continue;
            double a = Math.toRadians(part.roll), c = Math.cos(a), s = Math.sin(a);
            // Short pieces follow diagonal supports without filling their whole bounding rectangle.
            int segments = Math.abs(c * s) > .001 ? (int)Math.ceil(part.width * 16) : 1;
            for (int segment = 0; segment < segments; segment++) {
                double width = part.width / segments;
                double offset = -part.width / 2 + (segment + .5) * width;
                double x = part.x + offset * c + (size - 1) / 2.0 - column;
                double y = part.y + offset * s + (size - 1) / 2.0 - row;
                double hx = (Math.abs(c) * width + Math.abs(s) * part.height) / 2;
                double hy = (Math.abs(s) * width + Math.abs(c) * part.height) / 2;
                double hz = part.depth / 2;
                double cx = .5 + x * right.getStepX() + y * up.getStepX() + part.z * facing.getStepX();
                double cy = .5 + x * right.getStepY() + y * up.getStepY() + part.z * facing.getStepY();
                double cz = .5 + x * right.getStepZ() + y * up.getStepZ() + part.z * facing.getStepZ();
                double dx = hx * Math.abs(right.getStepX()) + hy * Math.abs(up.getStepX()) + hz * Math.abs(facing.getStepX());
                double dy = hx * Math.abs(right.getStepY()) + hy * Math.abs(up.getStepY()) + hz * Math.abs(facing.getStepY());
                double dz = hx * Math.abs(right.getStepZ()) + hy * Math.abs(up.getStepZ()) + hz * Math.abs(facing.getStepZ());
                if (cx + dx <= 0 || cx - dx >= 1 || cy + dy <= 0 || cy - dy >= 1 || cz + dz <= 0 || cz - dz >= 1) continue;
                shape = Shapes.joinUnoptimized(shape, Shapes.box(Math.max(0, cx - dx), Math.max(0, cy - dy), Math.max(0, cz - dz),
                        Math.min(1, cx + dx), Math.min(1, cy + dy), Math.min(1, cz + dz)), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }
}
