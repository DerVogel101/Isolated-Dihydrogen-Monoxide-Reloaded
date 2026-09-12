package io.github.SirWashington;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import io.github.SirWashington.block.PumpStructure;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Proxy;

/** Compares the ordinary and optional bulk emitters in real Iris buffers. */
final class MachineryBulkProbe {
    record Snapshot(byte[] bytes, VertexFormat format, int count) {}
    static void run() {
        var sprite = Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,
                Identifier.withDefaultNamespace("block/iron_block")));
        var context = CapturedRenderingState.INSTANCE;
        int block = context.getCurrentRenderedBlockEntity(), entity = context.getCurrentRenderedEntity(), item = context.getCurrentRenderedItem();
        try {
            context.setCurrentBlockEntity(29990); context.setCurrentEntity(17); context.setCurrentRenderedItem(23);
            for (int size=1;size<=3;size++) for (Direction facing:Direction.values()) {
                var poses = new PoseStack();
                var u=PumpStructure.right(facing); var v=PumpStructure.up(facing); var w=facing;
                poses.translate(.25,.75,-.5);
                poses.mulPose(new org.joml.Matrix4f().m00(u.getStepX()).m01(u.getStepY()).m02(u.getStepZ())
                        .m10(v.getStepX()).m11(v.getStepY()).m12(v.getStepZ()).m20(w.getStepX()).m21(w.getStepY()).m22(w.getStepZ()));
                for(int connection=0;connection<4;connection++) check(MachineryMesh.pump(size,connection),poses.last(),sprite);
                for(double progress:new double[]{0,.125,30,59.875,60,60.125,90.5,119.875,120,120.125,139.875,140})
                    check(MachineryMesh.buildValve(size,progress),poses.last(),sprite);
            }
            var mesh=MachineryMesh.pump(1,0);
            var poses=new PoseStack();
            compare(snapshot(mesh,poses.last(),sprite,0),snapshot(mesh,poses.last(),sprite,2));
            compare(snapshot(mesh,poses.last(),sprite,4),snapshot(mesh,poses.last(),sprite,3));
            compare(snapshot(mesh,poses.last(),sprite,4),snapshot(mesh,poses.last(),sprite,6));
            compare(snapshot(mesh,poses.last(),sprite,4),snapshot(mesh,poses.last(),sprite,7));
            boundaryGuards(mesh,poses.last(),sprite);
            compare(snapshot(mesh,poses.last(),sprite,9),snapshot(mesh,poses.last(),sprite,8));
            compare(snapshot(mesh,poses.last(),sprite,0),snapshot(mesh,poses.last(),sprite,5));
            // Normals must also survive a non-uniform outer transform.
            poses.mulPose(Axis.YP.rotationDegrees(23)); poses.scale(1.2F,.8F,1.1F);
            check(mesh,poses.last(),sprite);
            System.out.println("MACHINERY_BULK_PARITY_PASS variants=288 fallback=true transformedNormals=true mixedAndRepeated=true boundaryGuards=true changingMaterials=true");
        } finally {
            context.setCurrentBlockEntity(block); context.setCurrentEntity(entity); context.setCurrentRenderedItem(item);
        }
    }
    private static void boundaryGuards(MachineryMesh mesh,PoseStack.Pose pose,TextureAtlasSprite sprite) {
        var vertices=mesh.vertices().toArray(MachineryMesh.Vertex[]::new);
        try(var arena=new ByteBufferBuilder(262144)) {
            var builder=new BufferBuilder(arena,PrimitiveTopology.QUADS,DefaultVertexFormat.ENTITY);
            var access=(io.github.SirWashington.compat.iris.mixin.MachineryBufferAccess)(Object)builder;
            if(!SodiumMachineryEmitter.tryEmit(new MachineryMesh.Vertex[0],pose,builder,sprite,0)
                    || access.immersivefluids$vertexCount()!=0)throw new AssertionError("Empty emission changed buffer");
            if(SodiumMachineryEmitter.tryEmit(java.util.Arrays.copyOf(vertices,3),pose,builder,sprite,0))
                throw new AssertionError("Incomplete input accepted");
            for(int i=0;i<4;i++) {
                var p=vertices[i];
                builder.addVertex(pose,p.x(),p.y(),p.z()).setColor(p.color())
                        .setUv(sprite.getU(p.u()),sprite.getV(p.v())).setOverlay(0).setLight(0).setNormal(pose,p.nx(),p.ny(),p.nz());
                if(i==0 && (SodiumMachineryEmitter.tryEmit(vertices,pose,builder,sprite,0)
                        || access.immersivefluids$vertexCount()!=1))throw new AssertionError("Partial destination modified");
            }
            try(var output=builder.buildOrThrow()) {}
        }
        try(var arena=new ByteBufferBuilder(262144)) {
            var builder=new BufferBuilder(arena,PrimitiveTopology.TRIANGLES,DefaultVertexFormat.ENTITY);
            if(SodiumMachineryEmitter.tryEmit(vertices,pose,builder,sprite,0))throw new AssertionError("Non-quad target accepted");
        }
    }
    private static void check(MachineryMesh mesh,PoseStack.Pose pose,TextureAtlasSprite sprite) {
        compare(snapshot(mesh,pose,sprite,0),snapshot(mesh,pose,sprite,1));
    }
    private static Snapshot snapshot(MachineryMesh mesh,PoseStack.Pose pose,TextureAtlasSprite sprite,int mode) {
        try(var arena=new ByteBufferBuilder(262144)) {
            var builder=new BufferBuilder(arena,PrimitiveTopology.QUADS,DefaultVertexFormat.ENTITY);
            if(mode==0) mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
            else if(mode==1) {
                if(!SodiumMachineryEmitter.tryEmit(mesh.vertices().toArray(MachineryMesh.Vertex[]::new),pose,builder,sprite,0x00B000D0))
                    throw new AssertionError("Bulk path unexpectedly unavailable");
            } else if(mode==8 || mode==9) {
                var context=CapturedRenderingState.INSTANCE;
                int previous=context.getCurrentRenderedBlockEntity();
                try {
                    context.setCurrentBlockEntity(101);
                    mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                    context.setCurrentBlockEntity(29990);
                    if(mode==8) mesh.emit(pose,builder,sprite,0x00B000D0);
                    else mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                    context.setCurrentBlockEntity(202);
                    mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                } finally {context.setCurrentBlockEntity(previous);}
            } else if(mode==6 || mode==7) {
                mesh.emit(pose,builder,sprite,0x00B000D0);
                if(mode==7) mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                else mesh.emit(pose,builder,sprite,0x00B000D0);
                mesh.emit(pose,builder,sprite,0x00B000D0);
            } else if(mode==3 || mode==4) {
                mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                if(mode==3) mesh.emit(pose,builder,sprite,0x00B000D0);
                else mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
                mesh.emitOrdinary(pose,builder,sprite,0x00B000D0);
            } else {
                var wrapper=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                        mode==5 ? new Class<?>[]{VertexConsumer.class,net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.class}
                                : new Class<?>[]{VertexConsumer.class},(proxy,method,args)-> {
                            if(method.getName().equals("canUseIntrinsics"))return false;
                            if(method.getName().equals("push"))throw new AssertionError("Unsupported writer used");
                            return method.invoke(builder,args);
                        });
                mesh.emit(pose,wrapper,sprite,0x00B000D0);
            }
            try(var output=builder.buildOrThrow()) {
                var bytes=new byte[output.vertexBuffer().remaining()];
                output.vertexBuffer().get(bytes);
                return new Snapshot(bytes,output.drawState().format(),output.drawState().vertexCount());
            }
        }
    }
    private static void compare(Snapshot old,Snapshot current) {
        if(old.count!=current.count || !old.format.equals(current.format))throw new AssertionError("Format/count mismatch");
        var a=ByteBuffer.wrap(old.bytes).order(ByteOrder.nativeOrder());
        var b=ByteBuffer.wrap(current.bytes).order(ByteOrder.nativeOrder());
        for(int i=0;i<old.count;i++) {
            int start=i*old.format.getVertexSize();
            for(String name:new String[]{"Position","UV0","mc_midTexCoord"}) {
                int offset=start+old.format.getElement(name).offset();
                for(int c=0;c<(name.equals("Position")?3:2);c++) {
                    float x=a.getFloat(offset+c*4),y=b.getFloat(offset+c*4);
                    if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x-y)>2E-6)
                        throw new AssertionError(name+" mismatch at "+i+": "+x+" vs "+y);
                }
            }
            for(String name:new String[]{"Color","UV1","UV2"}) {
                int offset=start+old.format.getElement(name).offset();
                if(a.getInt(offset)!=b.getInt(offset))throw new AssertionError(name+" mismatch at "+i);
            }
            for(String name:new String[]{"Normal","at_tangent"}) {
                int offset=start+old.format.getElement(name).offset();
                for(int c=0;c<(name.equals("Normal")?3:4);c++)
                    if(Math.abs(a.get(offset+c)-b.get(offset+c))>(c==3?0:1))
                        throw new AssertionError(name+" mismatch at "+i+" component "+c+": "+a.get(offset+c)+" vs "+b.get(offset+c));
            }
            int offset=start+old.format.getElement("iris_Entity").offset();
            for(int c=0;c<3;c++)if(a.getShort(offset+c*2)!=b.getShort(offset+c*2))
                throw new AssertionError("Material ID mismatch");
        }
    }
}
