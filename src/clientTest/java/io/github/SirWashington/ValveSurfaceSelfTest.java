package io.github.SirWashington;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.SirWashington.block.ValveGeometry;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Original tessellation: retained vertices must match exactly; omitted quads must be buried. */
final class ValveSurfaceSelfTest {
    static void run() {
        var box = new ValveGeometry.Part(0, 0, 0, 1, 1, 1, 0, -1, true);
        expect(MachineryMesh.hiddenValveFaces(box, List.of(box)) == 0, "No self occlusion");
        var duplicate = new ValveGeometry.Part(0, 0, 0, 1, 1, 1, 0, -1, true);
        expect(MachineryMesh.hiddenValveFaces(box, List.of(duplicate)) == 0, "Keep coplanar exterior");
        var enclosing = new ValveGeometry.Part(0, 0, 0, 2, 2, 2, 0, -1, false);
        expect(MachineryMesh.hiddenValveFaces(box, List.of(enclosing)) == 63, "Visual parts occlude too");
        var partial = new ValveGeometry.Part(0, 0, 1, .5, 1, 1, 0, -1, true);
        expect(MachineryMesh.hiddenValveFaces(box, List.of(partial)) == 0, "Keep partial overlaps");
        var touching = new ValveGeometry.Part(0, 0, 1, 1, 1, 1, 0, -1, true);
        expect(MachineryMesh.hiddenValveFaces(box, List.of(touching)) == 1, "Remove shared internal face");
        for (int size = 1; size <= 3; size++) {
            for (double progress = 0; progress <= 140; progress += .5) check(size, progress);
            for (double progress : new double[]{.125, 59.875, 60.125, 119.875, 120.125, 139.875}) check(size, progress);
            for (double progress : new double[]{0, 90.5, 140})
                System.out.printf("VALVE_QUADS size=%d progress=%.1f before=%d after=%d%n", size, progress,
                        reference(size, progress).size()/4, MachineryMesh.buildValve(size, progress).vertices().size()/4);
        }
        System.out.println("VALVE_SURFACE_SELF_TEST_PASS states=861 retainedVerticesExact=true removedQuadsBuried=true");
    }

    private static void check(int size, double progress) {
        var before = reference(size, progress);
        var after = MachineryMesh.buildValve(size, progress).vertices();
        var parts = ValveGeometry.parts(size, progress);
        int next = 0;
        for (int i = 0; i < before.size(); i += 4) {
            boolean same = next + 4 <= after.size();
            for (int j = 0; same && j < 4; j++) same = before.get(i+j).equals(after.get(next+j));
            if (same) next += 4;
            else expect(buried(before, i, parts), "Exposed quad removed: size=" + size + " progress=" + progress + " vertex=" + i);
        }
        expect(next == after.size() && next < before.size(), "Only remove existing vertices, with a reduction");
    }

    private static boolean buried(List<MachineryMesh.Vertex> vertices, int start, List<ValveGeometry.Part> parts) {
        // Independent convex-box oracle, including arbitrary yaw. All outward-offset corners
        // must lie inside the SAME opaque box; corner coverage by different boxes is insufficient.
        for (var p : parts) {
            double angle = Math.toRadians(p.yaw()), c = Math.cos(angle), s = Math.sin(angle);
            boolean inside = true;
            for (int j = 0; inside && j < 4; j++) {
                var v = vertices.get(start+j);
                double dx = v.x() + v.nx()*2E-6 - p.x(), dz = v.z() + v.nz()*2E-6 - p.z();
                double x = c*dx - s*dz, y = v.y() + v.ny()*2E-6 - p.y(), z = s*dx + c*dz;
                inside = Math.abs(x) <= p.width()/2 + 5E-7 && Math.abs(y) <= p.height()/2 + 5E-7
                        && Math.abs(z) <= p.depth()/2 + 5E-7;
            }
            if (inside) return true;
        }
        return false;
    }

    static List<MachineryMesh.Vertex> reference(int size, double progress) {
        var result = new ArrayList<MachineryMesh.Vertex>();
        for (var p : ValveGeometry.parts(size, progress)) {
            var poses = new PoseStack();
            poses.translate(p.x(), p.y(), p.z());
            poses.mulPose(Axis.YP.rotationDegrees((float)p.yaw()));
            face(result, poses.last(), p, new Vec3(0,0,p.depth()/2), new Vec3(p.width(),0,0), new Vec3(0,p.height(),0));
            face(result, poses.last(), p, new Vec3(0,0,-p.depth()/2), new Vec3(-p.width(),0,0), new Vec3(0,p.height(),0));
            face(result, poses.last(), p, new Vec3(p.width()/2,0,0), new Vec3(0,0,-p.depth()), new Vec3(0,p.height(),0));
            face(result, poses.last(), p, new Vec3(-p.width()/2,0,0), new Vec3(0,0,p.depth()), new Vec3(0,p.height(),0));
            face(result, poses.last(), p, new Vec3(0,p.height()/2,0), new Vec3(p.width(),0,0), new Vec3(0,0,-p.depth()));
            face(result, poses.last(), p, new Vec3(0,-p.height()/2,0), new Vec3(p.width(),0,0), new Vec3(0,0,p.depth()));
        }
        return result;
    }

    private static void face(ArrayList<MachineryMesh.Vertex> result, PoseStack.Pose transform,
                             ValveGeometry.Part part, Vec3 center, Vec3 u, Vec3 v) {
        double width = u.length(), height = v.length();
        Vec3 normal = u.cross(v).normalize(), du = u.normalize(), dv = v.normalize();
        double ox = center.x - u.x*.5 - v.x*.5, oy = center.y - u.y*.5 - v.y*.5, oz = center.z - u.z*.5 - v.z*.5;
        var position = new org.joml.Vector3f();
        var n = transform.normal().transform((float)normal.x, (float)normal.y, (float)normal.z, new org.joml.Vector3f());
        for (double x=0; x<width-1E-8; x+=.5) for (double y=0; y<height-1E-8; y+=.5) {
            double w=Math.min(.5,width-x), h=Math.min(.5,height-y);
            for (int corner=0; corner<4; corner++) {
                double a=corner==1||corner==2?w:0, b=corner>=2?h:0;
                transform.pose().transformPosition((float)(ox+du.x*(x+a)+dv.x*(y+b)),
                        (float)(oy+du.y*(x+a)+dv.y*(y+b)), (float)(oz+du.z*(x+a)+dv.z*(y+b)), position);
                result.add(new MachineryMesh.Vertex(position.x,position.y,position.z,(float)(a*2),(float)(b*2),n.x,n.y,n.z,part.color()));
            }
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
