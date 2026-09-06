package io.github.SirWashington;

import io.github.SirWashington.block.PumpGeometry;
import io.github.SirWashington.block.PumpAnimation;
import io.github.SirWashington.block.PumpStructure;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Checks the renderer's shared mesh and writes a headless geometry preview, not an in-game screenshot. */
public final class PumpGeometrySelfTest {
    private record Face(double[][] vertices, int color) { }

    public static void main(String[] args) throws Exception {
        animationChecks();
        for (Direction facing : Direction.values()) {
            Direction u = PumpStructure.right(facing), v = PumpStructure.up(facing);
            expect(u.getStepY() * v.getStepZ() - u.getStepZ() * v.getStepY() == facing.getStepX()
                    && u.getStepZ() * v.getStepX() - u.getStepX() * v.getStepZ() == facing.getStepY()
                    && u.getStepX() * v.getStepY() - u.getStepY() * v.getStepX() == facing.getStepZ(),
                    "All six orientations preserve outward normals");
        }
        for (int size = 1; size <= 3; size++) {
            housingChecks(size);
            for (float power : new float[]{0, .5F, 1}) for (int angle = 0; angle < 360; angle += 5) {
                List<PumpGeometry.Part> parts = PumpGeometry.parts(size, 0, angle, power);
                for (PumpGeometry.Part part : parts) for (double[] point : corners(part)) {
                    expect(Math.abs(point[0]) <= size / 2.0 + .001 && Math.abs(point[1]) <= size / 2.0 + .001
                            && Math.abs(point[2]) <= .501, "Geometry stays inside the assembled footprint");
                }
                List<PumpGeometry.Part> blades = parts.subList(parts.size() - 6, parts.size());
                for (PumpGeometry.Part blade : blades) {
                    expect(blade.width() > 0 && blade.pitch() == 90 - power * 45, "Blades feather and pitch around radial axes");
                    double sweptRadius = Math.hypot(Math.hypot(blade.x(), blade.y()) + blade.width() / 2,
                            blade.height() / 2);
                    expect(sweptRadius < PumpGeometry.openingRadius(size) * Math.cos(Math.PI / 64) - .01,
                            "Rotating blade tips clear the enlarged polygonal opening");
                }
            }
        }
        BufferedImage preview = new BufferedImage(1440, 1060, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x101B24)); g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString("Water pump | geometry preview", 35, 42);
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.setColor(new Color(0xACBCC8));
        g.drawString("Same mesh as the mod; neutral lighting. Redstone animates the blades only.", 35, 73);
        for (int row = 0; row < 2; row++) for (int size = 1; size <= 3; size++) {
            int cx = 240 + (size - 1) * 480, cy = 245 + row * 315;
            draw(g, PumpGeometry.parts(size, 0, 15, row), PumpGeometry.housing(size, 0), cx, cy, 245.0 / (size + .8));
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.drawString(size + " x " + size + (row == 0 ? " | idle / feathered" : " | powered / pitched"), cx - 125, cy + 135);
        }
        var series = new ArrayList<PumpGeometry.Part>();
        var housing = new ArrayList<PumpGeometry.Quad>();
        for (int stage = 0; stage < 3; stage++) {
            for (PumpGeometry.Part p : PumpGeometry.parts(3, stage == 0 ? 2 : stage == 2 ? 1 : 3, stage * 20, 1)) {
                series.add(new PumpGeometry.Part(p.x(), p.y(), p.z() + stage - 1,
                        p.width(), p.height(), p.depth(), p.roll(), p.pitch(), p.color(), p.solid()));
            }
            for (PumpGeometry.Quad q : PumpGeometry.housing(3, stage == 0 ? 2 : stage == 2 ? 1 : 3)) {
                Vec3 offset = new Vec3(0, 0, stage - 1);
                housing.add(new PumpGeometry.Quad(q.a().add(offset), q.b().add(offset), q.c().add(offset), q.d().add(offset), q.color()));
            }
        }
        draw(g, series, housing, 430, 870, 76);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 21));
        g.drawString("Three connected 3 x 3 stages", 790, 850);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("Three motors, three rotors, one continuous housing", 790, 885);
        g.drawString("3 x 3: four side supports + four corner supports", 790, 920);
        g.dispose();
        File output = new File("build/pump-geometry-preview.png");
        ImageIO.write(preview, "png", output);
        System.out.println("PUMP_GEOMETRY_TEST_PASS " + output.getAbsolutePath());
    }

    private static double[][] corners(PumpGeometry.Part p) {
        double[][] vertices = new double[8][3];
        double roll = Math.toRadians(p.roll()), pitch = Math.toRadians(p.pitch());
        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) == 0 ? -.5 : .5) * p.width();
            double y = ((i & 2) == 0 ? -.5 : .5) * p.height();
            double z = ((i & 4) == 0 ? -.5 : .5) * p.depth();
            double yy = y * Math.cos(pitch) - z * Math.sin(pitch);
            vertices[i] = new double[]{p.x() + x * Math.cos(roll) - yy * Math.sin(roll),
                    p.y() + x * Math.sin(roll) + yy * Math.cos(roll), p.z() + y * Math.sin(pitch) + z * Math.cos(pitch)};
        }
        return vertices;
    }

    private static void animationChecks() {
        PumpAnimation animation = new PumpAnimation();
        for (int tick = 0; tick < 10; tick++) {
            animation.tick(true);
            expect(animation.angle == 0, "Startup pitches blades before any rotor motion");
        }
        expect(animation.pitch == 1, "Blade pitch completes in ten ticks");
        for (int tick = 0; tick < 20; tick++) animation.tick(true);
        for (int tick = 0; tick < 10; tick++) {
            animation.tick(false);
            expect(animation.pitch == 1, "Shutdown brakes the rotor before feathering");
        }
        for (int tick = 0; tick < 10; tick++) {
            animation.tick(false);
            expect(animation.angle == animation.previousAngle, "Rotor stays stopped throughout feathering");
        }
        expect(animation.pitch == 0, "Shutdown fully feathers the blades");
        var random = new java.util.Random(42);
        for (int sequence = 0; sequence < 200; sequence++) {
            boolean power = random.nextBoolean();
            int duration = 1 + random.nextInt(40);
            for (int tick = 0; tick < duration; tick++) {
                animation.tick(power);
                if (animation.pitch != animation.previousPitch) {
                    expect(animation.angle == animation.previousAngle, "Power reversals cannot mix pitch and spin");
                }
                if (animation.angle != animation.previousAngle) {
                    expect(animation.angle < animation.previousAngle, "Rotor spins in the corrected direction, including angle wrap");
                    expect(animation.pitch == 1 && animation.previousPitch == 1, "Interpolation starts spin only after full pitch");
                }
            }
        }
    }

    private static void housingChecks(int size) {
        double radius = PumpGeometry.openingRadius(size);
        expect(radius > size / 2.0 - .05, "Opening nearly reaches the case on all four sides");
        for (int connections = 0; connections < 4; connections++) {
            double frontArea = 0, backArea = 0;
            for (PumpGeometry.Quad q : PumpGeometry.housing(size, connections)) {
                Vec3 cross = q.b().subtract(q.a()).cross(q.c().subtract(q.a()));
                expect(cross.lengthSqr() > 1E-12, "Housing has no degenerate faces");
                for (Vec3 v : new Vec3[]{q.a(), q.b(), q.c(), q.d()}) {
                    expect(Math.abs(v.x) <= size / 2.0 + 1E-6 && Math.abs(v.y) <= size / 2.0 + 1E-6
                            && Math.abs(v.z) <= .5, "Casing never protrudes beyond its footprint");
                }
                if (q.a().z == q.b().z && q.a().z == q.c().z && q.a().z == q.d().z) {
                    expect(cross.z * q.a().z > 0, "End-cap faces point outward");
                    double area = (cross.length() + q.c().subtract(q.a()).cross(q.d().subtract(q.a())).length()) / 2;
                    if (q.a().z > 0) frontArea += area; else backArea += area;
                }
            }
            double filled = size * size - 32 * radius * radius * Math.sin(2 * Math.PI / 64);
            expect(Math.abs(frontArea - ((connections & 2) == 0 ? filled : 0)) < 1E-6,
                    "Front fills precisely the square minus its circular opening, with no duplicate trim faces");
            expect(Math.abs(backArea - ((connections & 1) == 0 ? filled : 0)) < 1E-6,
                    "Connected stages omit shared end caps");
        }
    }

    private static void draw(Graphics2D g, List<PumpGeometry.Part> parts, List<PumpGeometry.Quad> housing,
                             int cx, int cy, double scale) {
        var faces = new ArrayList<Face>();
        int[][] indices = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for (PumpGeometry.Part part : parts) {
            double[][] vertices = corners(part);
            for (int f = 0; f < indices.length; f++) {
                Color base = new Color(part.color());
                float shade = new float[]{.65F, 1F, .60F, .92F, .72F, .82F}[f];
                faces.add(new Face(new double[][]{vertices[indices[f][0]], vertices[indices[f][1]],
                        vertices[indices[f][2]], vertices[indices[f][3]]}, new Color((int)(base.getRed() * shade),
                        (int)(base.getGreen() * shade), (int)(base.getBlue() * shade)).getRGB()));
            }
        }
        for (PumpGeometry.Quad q : housing) {
            Vec3[] v = {q.a(), q.b(), q.c(), q.d()};
            double[][] points = new double[4][3];
            for (int i = 0; i < 4; i++) points[i] = new double[]{v[i].x, v[i].y, v[i].z};
            faces.add(new Face(points, q.color()));
        }
        // Depth-test the actual faces; painter sorting produced false overlap artifacts in the old preview.
        BufferedImage layer = new BufferedImage(1440, 1060, BufferedImage.TYPE_INT_ARGB);
        double[] depths = new double[1440 * 1060];
        java.util.Arrays.fill(depths, -Double.MAX_VALUE);
        for (Face face : faces) {
            double[][] p = new double[4][3];
            for (int i = 0; i < 4; i++) {
                double[] v = face.vertices[i];
                double z = .39 * v[0] + .92 * v[2];
                p[i] = new double[]{cx + (.92 * v[0] - .39 * v[2]) * scale,
                        cy - (.94 * v[1] - .34 * z) * scale, .34 * v[1] + .94 * z};
            }
            triangle(layer, depths, p[0], p[1], p[2], face.color);
            triangle(layer, depths, p[0], p[2], p[3], face.color);
        }
        g.drawImage(layer, 0, 0, null);
    }

    private static void triangle(BufferedImage image, double[] depths, double[] a, double[] b, double[] c, int color) {
        double det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(det) < 1E-9) return;
        int minX = Math.max(0, (int)Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(image.getWidth() - 1, (int)Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int)Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(image.getHeight() - 1, (int)Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            double u = ((b[1] - c[1]) * (x + .5 - c[0]) + (c[0] - b[0]) * (y + .5 - c[1])) / det;
            double v = ((c[1] - a[1]) * (x + .5 - c[0]) + (a[0] - c[0]) * (y + .5 - c[1])) / det;
            if (u < -1E-7 || v < -1E-7 || u + v > 1 + 1E-7) continue;
            double depth = u * a[2] + v * b[2] + (1 - u - v) * c[2];
            int index = y * image.getWidth() + x;
            if (depth > depths[index]) { depths[index] = depth; image.setRGB(x, y, color); }
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
