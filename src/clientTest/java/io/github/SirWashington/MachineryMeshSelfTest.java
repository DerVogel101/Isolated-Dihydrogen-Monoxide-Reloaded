package io.github.SirWashington;

import io.github.SirWashington.block.PumpGeometry;
import io.github.SirWashington.block.ValveGeometry;
import io.github.SirWashington.block.MachineryRenderData;
import net.minecraft.core.Direction;
import java.util.List;
import java.lang.management.ManagementFactory;
import java.util.function.Supplier;

public final class MachineryMeshSelfTest {
    private static volatile Object sink;
    public static void main(String[] args) {
        PumpSurfaceSelfTest.run();
        ValveSurfaceSelfTest.run();
        for (int size = 1; size <= 3; size++) {
            for (int connection = 0; connection < 4; connection++) {
                var mesh = MachineryMesh.pump(size, connection);
                expect(mesh == MachineryMesh.pump(size, connection), "Pump cache reuse");
                expect(mesh.vertices().equals(MachineryMesh.buildPump(size, connection).vertices()), "Pump cache key");
                double sourceArea = 0;
                var stationary = PumpGeometry.parts(size, connection, 0, 0).stream().filter(PumpGeometry.Part::solid).toList();
                expect(stationary.size() == (size == 3 ? 9 : 5), "All motor supports and motor body are stationary");
                expect(stationary.equals(PumpGeometry.parts(size, connection, 73, .6F).stream()
                        .filter(PumpGeometry.Part::solid).toList()), "Chunk-baked pump parts never animate");
                for (var q : PumpGeometry.housing(size, connection)) {
                    sourceArea += q.b().subtract(q.a()).cross(q.c().subtract(q.a())).length() / 2;
                    sourceArea += q.c().subtract(q.a()).cross(q.d().subtract(q.a())).length() / 2;
                }
                for (var p : PumpGeometry.parts(size, connection, 0, 0)) if (p.solid())
                    sourceArea += 2 * (p.width()*p.height() + p.width()*p.depth() + p.height()*p.depth());
                expect(Math.abs(validate(mesh) - sourceArea) < 1E-4, "Pump preserves housing, support and motor surface area");
                verifySlices(mesh, size, connection);
                if (connection > 0) expect(mesh != MachineryMesh.pump(size, connection - 1), "Connection changes mesh");
            }
            var cache = new MachineryMesh.ValveCache();
            verifySlices(MachineryMesh.buildValveFrame(size), size, 0);
            for (double progress : new double[]{0, .125, 30, 59.875, 60, 60.125, 90.5, 119.875, 120, 120.125, 139.875, 140}) {
                var mesh = cache.get(size, progress);
                expect(mesh == cache.get(size, progress), "Valve cache reuse");
                expect(mesh.vertices().equals(MachineryMesh.buildMovingValve(size, progress).vertices()), "Valve progress key");
                double sourceArea = 0;
                for (var p : ValveGeometry.parts(size, progress))
                    sourceArea += 2 * (p.width()*p.height() + p.width()*p.depth() + p.height()*p.depth());
                expect(validate(mesh) <= sourceArea + 2E-4, "Valve only removes surface area");
                if (progress == 0 || progress == 140)
                    expect(mesh == new MachineryMesh.ValveCache().get(size, progress), "Shared endpoint");
            }
            var old = cache.get(size, 70.25);
            expect(old != cache.get(size, 70.5), "Exact progress invalidation");
            expect(old != cache.get(size == 3 ? 1 : size + 1, 70.25), "Size invalidation");
        }
        var cache = new MachineryMesh.ValveCache();
        benchmark("pump preparation uncached", () -> MachineryMesh.buildPump(3, 0));
        benchmark("pump preparation cached", () -> MachineryMesh.pump(3, 0));
        benchmark("valve preparation uncached", () -> MachineryMesh.buildValve(3, 0));
        benchmark("valve preparation cached", () -> cache.get(3, 0));
        System.out.println("MACHINERY_MESH_SELF_TEST_PASS");
    }
    private static double validate(MachineryMesh mesh) {
        expect(!mesh.vertices().isEmpty(), "Nonempty geometry");
        return validate(mesh.vertices());
    }
    private static void verifySlices(MachineryMesh mesh, int size, int connections) {
        double originalArea = validate(mesh);
        for (var facing : Direction.values()) {
            double area = 0;
            for (int column = 0; column < size; column++) for (int row = 0; row < size; row++) {
                var slice = MachineryFrameModel.slice(mesh.vertices(), new MachineryRenderData(size, column, row, connections), facing);
                area += validate(slice);
                for (int i = 0; i < slice.size(); i += 4) {
                    var a = slice.get(i); var b = slice.get(i + 1); var c = slice.get(i + 2);
                    double ux = b.x()-a.x(), uy=b.y()-a.y(), uz=b.z()-a.z();
                    double vx = c.x()-a.x(), vy=c.y()-a.y(), vz=c.z()-a.z();
                    double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
                    expect(nx*nx+ny*ny+nz*nz > 1E-16, "Clipping preserves a nondegenerate first triangle for shader tangents");
                }
                for (var p : slice) expect(p.x() >= -1E-5 && p.x() <= 1.00001F
                        && p.y() >= -1E-5 && p.y() <= 1.00001F && p.z() >= -1E-5 && p.z() <= 1.00001F,
                        "Frame geometry stays in its owning block");
            }
            expect(Math.abs(area - originalArea) < 3E-4, "Chunk partitions preserve area without duplicated boundary faces");
        }
    }
    private static double validate(List<MachineryMesh.Vertex> vertices) {
        expect(vertices.size() % 4 == 0, "Complete quads");
        double area = 0;
        for (var p : vertices) {
            expect(Float.isFinite(p.x()+p.y()+p.z()), "Finite positions");
            expect(p.u() >= 0 && p.u() <= 1 && p.v() >= 0 && p.v() <= 1, "Sprite-local UV bounds");
            expect(Math.abs(p.nx()*p.nx()+p.ny()*p.ny()+p.nz()*p.nz()-1) < 1E-5, "Unit normals");
            expect((p.color() >>> 24) == 255, "Opaque colors");
        }
        for (int i = 0; i < vertices.size(); i += 4) {
            var a = vertices.get(i);
            for (int t = 1; t < 3; t++) {
                var b = vertices.get(i+t); var c = vertices.get(i+t+1);
                double ux=b.x()-a.x(), uy=b.y()-a.y(), uz=b.z()-a.z();
                double vx=c.x()-a.x(), vy=c.y()-a.y(), vz=c.z()-a.z();
                double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
                expect(nx*a.nx()+ny*a.ny()+nz*a.nz() >= -1E-7, "Face winding");
                area += Math.sqrt(nx*nx+ny*ny+nz*nz)/2;
            }
        }
        return area;
    }
    private static void benchmark(String label, Supplier<Object> operation) {
        for (int i=0; i<1000; i++) sink=operation.get();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId();
        long bytes = bean.getThreadAllocatedBytes(id), start = System.nanoTime();
        for (int i=0; i<2000; i++) sink=operation.get();
        System.out.printf("%s: %.3f us/op, %.0f bytes/op%n", label,
                (System.nanoTime()-start)/2000000.0, (bean.getThreadAllocatedBytes(id)-bytes)/2000.0);
    }
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

