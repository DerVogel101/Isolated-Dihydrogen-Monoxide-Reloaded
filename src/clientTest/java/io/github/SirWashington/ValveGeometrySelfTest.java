package io.github.SirWashington;

import io.github.SirWashington.block.ValveGeometry;
import io.github.SirWashington.block.PumpGeometry;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Checks the actual renderer geometry, including every frame of the seven-second travel. */
public final class ValveGeometrySelfTest {
    public static void main(String[] args) throws Exception {
        for (boolean opening : new boolean[]{false, true}) {
            int[] counts = new int[4];
            for (int elapsed = 0; elapsed < 140; elapsed++) {
                int progress = opening ? 140 - elapsed : elapsed;
                counts[ValveGeometry.motionPhase(progress, opening)]++;
            }
            if (counts[1] != 60 || counts[2] != 60 || counts[3] != 20)
                throw new AssertionError("Sound stages must follow 60/60/20 ticks in either direction");
        }
        for (int size = 1; size <= 3; size++) for (int tick = 0; tick <= 140; tick++) {
            for (var quad : mesh(size, tick, false)) for (Vec3 p : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                if (Math.abs(p.x) > size / 2.0 + .001 || Math.abs(p.y) > size / 2.0 + .001 || Math.abs(p.z) > .501)
                    throw new AssertionError("Valve leaves footprint: size=" + size + " tick=" + tick + " " + p);
            }
            var parts = ValveGeometry.parts(size, tick);
            if (parts.get(4).yaw() != 0 && tick >= 60) throw new AssertionError("Swing must finish before extension");
            var actuators = parts.stream().filter(p -> p.height() == .055 && p.depth() == .032).toList();
            if (actuators.size() != (size == 3 ? 12 : 8))
                throw new AssertionError("Two actuator rows per face/leaf, three on 3x3");
            for (var base : parts) if (base.width() == .09 && base.depth() == .038) {
                for (var actuator : actuators) {
                    if (Math.abs(base.y() - actuator.y()) <= base.height() / 2 + .075 / 2)
                        throw new AssertionError("Bolt drives must clear the full actuator/clevis height throughout travel");
                }
            }
        }
        for (boolean back : new boolean[]{false, true}) {
            BufferedImage preview = new BufferedImage(1440, 1060, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = preview.createGraphics();
            g.setColor(new Color(0x101B24)); g.fillRect(0, 0, 1440, 1060);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 24));
            g.drawString("Water valve | " + (back ? "intake face" : "outlet face") + " | headless preview", 30, 38);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            g.drawString("Closing: swing 60 ticks / extend 60 ticks / lock 20 ticks. Powered opens in reverse.", 30, 67);
            int[] ticks = {0, 90, 140};
            for (int row = 0; row < 3; row++) for (int size = 1; size <= 3; size++) {
                int cx = 240 + (size - 1) * 480, cy = 210 + row * 290;
                PumpGeometrySelfTest.draw(g, List.of(), mesh(size, ticks[row], back), cx, cy, 205.0 / size);
                g.setColor(Color.WHITE);
                g.drawString(size + " x " + size + " | " + new String[]{"open", "extending", "closed / locked"}[row], cx - 100, cy + 145);
            }
            g.dispose();
            ImageIO.write(preview, "png", new File(back ? "build/valve-geometry-back-preview.png" : "build/valve-geometry-preview.png"));
            }
        System.out.println("VALVE_GEOMETRY_TEST_PASS");
    }

    static List<PumpGeometry.Quad> mesh(int size, double tick, boolean back) {
        var quads = new ArrayList<PumpGeometry.Quad>();
        int[][] faces = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for (var p : ValveGeometry.parts(size, tick)) {
            Vec3[] vertices = new Vec3[8];
            double c = Math.cos(Math.toRadians(p.yaw())), s = Math.sin(Math.toRadians(p.yaw()));
            for (int i = 0; i < 8; i++) {
                double x = ((i & 4) == 0 ? -1 : 1) * p.width() / 2;
                double y = ((i & 2) == 0 ? -1 : 1) * p.height() / 2;
                double z = ((i & 1) == 0 ? -1 : 1) * p.depth() / 2;
                vertices[i] = new Vec3((p.x() + c * x + s * z) * (back ? -1 : 1), p.y() + y,
                        (p.z() - s * x + c * z) * (back ? -1 : 1));
            }
            for (int i = 0; i < faces.length; i++) {
                int[] f = faces[i];
                Color base = new Color(p.color());
                double shade = new double[]{.7, .9, .65, 1, .75, .85}[i];
                int color = new Color((int)(base.getRed() * shade), (int)(base.getGreen() * shade), (int)(base.getBlue() * shade)).getRGB();
                quads.add(new PumpGeometry.Quad(vertices[f[0]], vertices[f[1]], vertices[f[2]], vertices[f[3]], color));
            }
        }
        return quads;
    }
}
