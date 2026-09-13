package io.github.SirWashington.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** Telescopic leaves, hydraulic actuators and independently driven frame bolts. Units are blocks. */
public final class ValveGeometry {
    public static final int DURATION = 140;
    public static final int SEALED_AT = 120;
    public record Part(double x, double y, double z, double width, double height, double depth,
                       double yaw, int color, boolean solid) { }
    private static final int FRAME = 0xFF586771, PANEL = 0xFFC2CDD0, COPPER = 0xFFCF9064;

    private ValveGeometry() { }
    /** Direction-aware stages: hinge, telescoping panels, bolts; zero means travel completed. */
    public static int motionPhase(int progress, boolean opening) {
        if (opening) return progress == 0 ? 0 : progress > 120 ? 3 : progress > 60 ? 2 : 1;
        return progress == DURATION ? 0 : progress < 60 ? 1 : progress < 120 ? 2 : 3;
    }

    private static double phase(double ticks, int start, int duration) {
        double t = Math.clamp((ticks - start) / duration, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static List<Part> frameParts(int size) {
        var parts = new ArrayList<Part>();
        double half = size / 2.0, rim = .04, span = half - rim;
        // Partition the frame at the corners to avoid coplanar exterior faces.
        for (int side : new int[]{-1, 1}) {
            parts.add(new Part(0, side * (half - rim / 2), 0, size, rim, 1, 0, FRAME, true));
            parts.add(new Part(side * (half - rim / 2), 0, 0, rim, size - rim * 2, 1, 0, FRAME, true));
        }
        double thickness = size == 1 ? .075 : .14;
        for (int side : new int[]{-1, 1}) for (int face : new int[]{-1, 1}) for (int edge : new int[]{-1, 1}) {
            double z = face * (thickness / 2 + .019);
            parts.add(new Part(side * .10, edge * (span + rim / 2), .38 + z,
                    .075, rim - .002, .032, 0, COPPER, false));
        }
        return List.copyOf(parts);
    }

    public static List<Part> parts(int size, double ticks) {
        var parts = new ArrayList<>(frameParts(size));
        double half = size / 2.0, rim = .04, span = half - rim;
        double swing = phase(ticks, 0, 60), extension = phase(ticks, 60, 60), lock = phase(ticks, 120, 20);
        int panels = size == 1 ? 2 : 3;
        double thickness = size == 1 ? .075 : .14;
        double section = span / panels + .03;
        for (int side : new int[]{-1, 1}) {
            double angle = -side * (1 - swing) * Math.PI / 2;
            double pivot = side * (span - (thickness / 2 + .035) * (1 - swing));
            double end = section + (span - section) * extension;
            for (int panel = 0; panel < panels; panel++) {
                double along = section / 2 + panel * ((span - section) / (panels - 1) + .002) * extension;
                double depth = thickness - panel * .018;
                // Recess nested end caps so retracted plates do not share a visible face.
                leaf(parts, pivot, angle, -side * along, 0, 0, section - panel * .004, size - rim * 2, depth,
                        PANEL, true);
                // Raised reinforcing ribs and rivets on both faces; the thinner panels nest inside the outer one.
                for (int face : new int[]{-1, 1}) {
                    for (int band : new int[]{-1, 1}) {
                        double y = band * span * .68, z = face * (depth / 2 + .006);
                        leaf(parts, pivot, angle, -side * along, y, z, section * .82, .035, .012, FRAME, false);
                        for (int rivet : new int[]{-1, 1}) {
                            leaf(parts, pivot, angle, -side * along + rivet * section * .32, y, z + face * .009,
                                    .023, .023, .01, COPPER, false);
                        }
                    }
                }
            }
            // Double-acting cylinders: stationary barrel, moving piston rod and a clevis on the final panel.
            for (int face : new int[]{-1, 1}) for (int band : size == 3 ? new int[]{-1, 0, 1} : new int[]{-1, 1}) {
                double y = band * span * .42, z = face * (thickness / 2 + .018);
                double body = section * .68, tip = end - .055, rod = Math.max(.015, tip - body);
                leaf(parts, pivot, angle, -side * body / 2, y, z, body, .055, .032, FRAME, false);
                leaf(parts, pivot, angle, -side * (body + rod / 2), y, z, rod, .023, .022, PANEL, false);
                leaf(parts, pivot, angle, -side * tip, y, z, .035, .075, .034, COPPER, false);
            }
            // Short shot bolts retract into dedicated drive bases beyond the actuator rows.
            for (int face : new int[]{-1, 1}) {
                double z = face * (thickness / 2 + .019), shaftX = -side * (end - .10);
                double length = size == 1 ? .10 : .16;
                double baseHeight = size == 1 ? .09 : .12;
                for (int edge : new int[]{-1, 1}) {
                    double shaftY = edge * (span - .055 - length / 2 + .075 * lock);
                    leaf(parts, pivot, angle, shaftX, shaftY, z, .034, length, .025, COPPER, false);
                    leaf(parts, pivot, angle, shaftX, edge * (span - .075 - length / 2), z,
                            .09, baseHeight, .038, FRAME, false);
                }
            }
        }
        return List.copyOf(parts);
    }

    private static void leaf(List<Part> parts, double pivot, double angle, double x, double y, double z,
                             double width, double height, double depth, int color, boolean solid) {
        double c = Math.cos(angle), s = Math.sin(angle);
        parts.add(new Part(pivot + c * x + s * z, y, .38 - s * x + c * z,
                width, height, depth, Math.toDegrees(angle), color, solid));
    }
    public static VoxelShape collision(int size, int column, int row, Direction facing, int ticks) {
        VoxelShape shape = Shapes.empty();
        var parts = new ArrayList<>(parts(size, ticks));
        // The closed seal lives on a block face, not in the middle of a waterlogged volume.
        if (ticks >= SEALED_AT) parts.add(new Part(0, 0, .45, size, size, .1, 0, PANEL, true));
        Direction right = SquareStructure.right(facing), up = SquareStructure.up(facing);
        for (Part p : parts) {
            if (!p.solid) continue;
            double c = Math.cos(Math.toRadians(p.yaw)), s = Math.sin(Math.toRadians(p.yaw));
            int segments = Math.abs(c * s) > .001 ? (int)Math.ceil(p.width * 16) : 1;
            for (int i = 0; i < segments; i++) {
                double width = p.width / segments, offset = -p.width / 2 + (i + .5) * width;
                double x = p.x + c * offset + (size - 1) / 2.0 - column;
                double y = p.y + (size - 1) / 2.0 - row, z = p.z - s * offset;
                double hx = (Math.abs(c) * width + Math.abs(s) * p.depth) / 2, hy = p.height / 2;
                double hz = (Math.abs(s) * width + Math.abs(c) * p.depth) / 2;
                double cx = .5 + x * right.getStepX() + y * up.getStepX() + z * facing.getStepX();
                double cy = .5 + x * right.getStepY() + y * up.getStepY() + z * facing.getStepY();
                double cz = .5 + x * right.getStepZ() + y * up.getStepZ() + z * facing.getStepZ();
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
