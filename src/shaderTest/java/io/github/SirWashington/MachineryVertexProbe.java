package io.github.SirWashington;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.irisshaders.iris.vertices.ImmediateState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import java.lang.reflect.Proxy;

/** Inspect the real custom emitters through Iris's transformed BufferBuilder. */
final class MachineryVertexProbe {
    static void run() {
        boolean before = ImmediateState.isRenderingLevel;
        ImmediateState.isRenderingLevel = true;
        int[] count = {0};
        int[] submissions = {0};
        var sprite = net.minecraft.client.Minecraft.getInstance().getAtlasManager().get(
                new net.minecraft.client.resources.model.sprite.SpriteId(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "block/machinery_iron")));
        try {
            MachineryBulkProbe.run();
            var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                        if (method.getName().equals("submitCustomGeometry") || method.getName().equals("submitModelPart")) {
                            submissions[0]++;
                            try (var arena = new ByteBufferBuilder(65536)) {
                                var builder = new BufferBuilder(arena, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
                                if (method.getName().equals("submitCustomGeometry"))
                                    ((SubmitNodeCollector.CustomGeometryRenderer)args[2]).render(((PoseStack)args[0]).last(), builder);
                                else
                                    ((net.minecraft.client.model.geom.ModelPart)args[0]).render((PoseStack)args[1],
                                            ((net.minecraft.client.renderer.texture.TextureAtlasSprite)args[5]).wrap(builder),
                                            (int)args[3], (int)args[4], (int)args[6]);
                                try (var mesh = builder.buildOrThrow()) {
                                    var format = mesh.drawState().format();
                                    int normal = IrisVertexFormats.getOffset(format, "Normal");
                                    int uv = IrisVertexFormats.getOffset(format, "UV0");
                                    var data = mesh.vertexBuffer();

                                    for (int i=0; i<mesh.drawState().vertexCount(); i++) {
                                        int offset = i * format.getVertexSize();
                                        int nx=data.get(offset+normal), ny=data.get(offset+normal+1), nz=data.get(offset+normal+2);
                                        if (nx*nx+ny*ny+nz*nz < 15000) throw new AssertionError("Invalid machinery normal");
                                        float u=data.getFloat(offset+uv), v=data.getFloat(offset+uv+4);
                                        if (u < sprite.getU(0)-1E-6 || u > sprite.getU(1)+1E-6
                                                || v < sprite.getV(0)-1E-6 || v > sprite.getV(1)+1E-6)
                                            throw new AssertionError("Machinery samples outside iron atlas sprite");
                                    }
                                    count[0] += mesh.drawState().vertexCount();
                                }
                            }
                        }
                        return null;
                    });
            for (int size=1; size<=3; size++) for (Direction facing : Direction.values()) {
                for (int connections=0; connections<4; connections++) {
                    var pump = new WaterPumpRenderer.State();
                    pump.controller=true; pump.size=size; pump.connections=connections;
                    pump.facing=facing; pump.lightCoords=15728880;
                    pump.angle = 15 + connections * 90; pump.pitch = size * 10;
                    new WaterPumpRenderer(null).submit(pump, new PoseStack(), collector, null);
                }
                var valve = new WaterValveRenderer.State();
                valve.controller=true; valve.size=size; valve.facing=facing; valve.lightCoords=15728880;
                var renderer = new WaterValveRenderer(null);
                for (double progress : new double[]{0, .125, 30, 59.875, 60, 60.125, 90.5, 119.875, 120, 120.125, 139.875, 140}) {
                    valve.progress=progress;
                    int beforeSubmit=submissions[0];
                    renderer.submit(valve, new PoseStack(), collector, null);
                    if (submissions[0] != beforeSubmit+1) throw new AssertionError("Valve must use one custom submission");
                }
                valve.controller=false;
                int beforeSubmit=submissions[0];
                renderer.submit(valve, new PoseStack(), collector, null);
                if (submissions[0] != beforeSubmit) throw new AssertionError("Non-controller submitted geometry");
            }
            if (count[0] == 0) throw new AssertionError("No machinery emitted");
            System.out.println("MACHINERY_VERTEX_PASS vertices="+count[0]+" sizes=1-3 directions=6");
        } finally { ImmediateState.isRenderingLevel=before; }
    }
}
