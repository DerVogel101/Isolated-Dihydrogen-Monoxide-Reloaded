package io.github.SirWashington;

import io.github.SirWashington.block.PumpGeometry;
import io.github.SirWashington.block.PumpGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Reference tessellation retained only in tests to check the merged surfaces independently. */
final class PumpSurfaceSelfTest {
    private static final int SIDES=64, FRAME=0xFF39454D, EDGE=0xFF65747D, FILL=0xFF899399;
    static void run() {
        for(int size=1;size<=3;size++) for(int connections=0;connections<4;connections++) {
            var before=buildHousing(size,connections);
            var after=PumpGeometry.housing(size,connections);
            cover(before,after); cover(after,before);
            for(int color:new int[]{FRAME,EDGE,FILL}) {
                double a=area(before,color), b=area(after,color);
                expect(Math.abs(a-b)<1E-7,"Surface area/color changed");
            }
            // Atlas UVs on all planar surfaces must still implement the original affine projection.
            for(var p:MachineryMesh.pump(size,connections).vertices()) {
                if(Math.abs(p.nz())>.5) {
                    expect(Math.abs(p.u()-(p.x()/size+.5))<1E-6 && Math.abs(p.v()-(p.y()/size+.5))<1E-6,"Cap UV mapping");
                } else if(Math.abs(Math.abs(p.x())-size/2.0)<1E-6 && Math.abs(p.nx())>.5) {
                    expect(Math.abs(p.u()-(p.z()+.5))<1E-6 && Math.abs(p.v()-(p.y()/size+.5))<1E-6,"Side UV mapping");
                } else if(Math.abs(Math.abs(p.y())-size/2.0)<1E-6 && Math.abs(p.ny())>.5) {
                    expect(Math.abs(p.u()-(p.x()/size+.5))<1E-6 && Math.abs(p.v()-(p.z()+.5))<1E-6,"Side UV mapping");
                }
            }
        }
        System.out.println("PUMP_SURFACE_EQUIVALENCE_PASS");
    }
    private static double area(List<Quad> quads,int color) {
        double sum=0;
        for(var q:quads) if(q.color()==color)
            sum+=(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).length()
                +q.c().subtract(q.a()).cross(q.d().subtract(q.a())).length())*.5;
        return sum;
    }
    private static void cover(List<Quad> source,List<Quad> target) {
        for(var q:source) {
            var normal=q.b().subtract(q.a()).cross(q.c().subtract(q.a())).normalize();
            for(var triangle:List.of(List.of(q.a(),q.b(),q.c()),List.of(q.a(),q.c(),q.d()))) {
                for(double[] weights:new double[][]{{.2,.3,.5},{.8,.1,.1},{.1,.8,.1},{.1,.1,.8}}) {
                    var point=triangle.get(0).scale(weights[0]).add(triangle.get(1).scale(weights[1])).add(triangle.get(2).scale(weights[2]));
                    boolean found=false;
                    for(var other:target) {
                        if(other.color()!=q.color())continue;
                        var n=other.b().subtract(other.a()).cross(other.c().subtract(other.a())).normalize();
                        if(n.dot(normal)<1-1E-8 || Math.abs(point.subtract(other.a()).dot(n))>1E-8)continue;
                        if(inside(point,other.a(),other.b(),other.c()) || inside(point,other.a(),other.c(),other.d())) {found=true;break;}
                    }
                    expect(found,"Surface coverage or winding changed at "+point);
                }
            }
        }
    }
    private static boolean inside(Vec3 p,Vec3 a,Vec3 b,Vec3 c) {
        var v=b.subtract(a);var w=c.subtract(a);var d=p.subtract(a);
        double vv=v.dot(v),ww=w.dot(w),vw=v.dot(w),dv=d.dot(v),dw=d.dot(w);
        double determinant=vv*ww-vw*vw;
        if(determinant<1E-20)return false;
        double u=(ww*dv-vw*dw)/determinant,t=(vv*dw-vw*dv)/determinant;
        return u>=-1E-7 && t>=-1E-7 && u+t<=1+1E-7;
    }
    private static void expect(boolean value,String message) {if(!value)throw new AssertionError(message);}
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
        double half = size / 2.0, radius = PumpGeometry.openingRadius(size);
        double start = (connections & 1) != 0 ? -.5 : -.44;
        double end = (connections & 2) != 0 ? .5 : .44;
        for (int i = 0; i < SIDES; i++) {
            int j = i + 1;
            // One inward-facing barrel wall, with no intersecting ring segments.
            quads.add(new Quad(circle(i, radius, start), circle(i, radius, end),
                    circle(j, radius, end), circle(j, radius, start), FRAME));
            // Exterior seam strips partition the surface rather than overlaying another box.
            double[] z = {start, start + .025, end - .025, end};
            for (int band = 0; band < 3; band++) {
                quads.add(new Quad(square(i, half, z[band]), square(j, half, z[band]),
                        square(j, half, z[band + 1]), square(i, half, z[band + 1]), band == 1 ? FRAME : EDGE));
            }
            for (int side = 0; side < 2; side++) {
                if ((connections & (1 << side)) != 0) continue; // No coincident caps between connected stages.
                double faceZ = side == 0 ? start : end;
                Vec3[] a = {circle(i, radius, faceZ), circle(i, radius + .008, faceZ),
                        square(i, half - .025, faceZ), square(i, half, faceZ)};
                Vec3[] b = {circle(j, radius, faceZ), circle(j, radius + .008, faceZ),
                        square(j, half - .025, faceZ), square(j, half, faceZ)};
                for (int band = 0; band < 3; band++) {
                    int color = band == 1 ? FILL : FRAME;
                    quads.add(side == 1 ? new Quad(a[band], a[band + 1], b[band + 1], b[band], color)
                            : new Quad(b[band], b[band + 1], a[band + 1], a[band], color));
                }
            }
        }
        return List.copyOf(quads);
    }

}
