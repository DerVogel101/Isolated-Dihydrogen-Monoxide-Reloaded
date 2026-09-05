package io.github.SirWashington;

import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.block.FiniteIceBlock;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Function;

/** Add ice to the existing model without changing the host's block-entity renderer. */
final class FrozenWaterloggedModel {
    private FrozenWaterloggedModel() {}

    static net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform cancelPlantOffset(net.minecraft.world.phys.Vec3 offset) {
        return quad -> {
            quad.translate((float) -offset.x, (float) -offset.y, (float) -offset.z);
            return true;
        };
    }

    static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelOnLoad().register((original, load) -> {
            if (load.state().getBlock() instanceof FiniteIceBlock && !FrozenWaterloggedBlocks.isFrozen(load.state()))
                return new CullingRoot(original);
            if (!FrozenWaterloggedBlocks.isFrozen(load.state())) return original;
            int height = FrozenWaterloggedBlocks.iceHeight(load.state());
            Variant ice = new Variant(height == 8 ? Identifier.withDefaultNamespace("block/ice")
                    : Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "block/finite_ice_" + height));
            return new Root(original, ice);
        }));
    }

    static net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform aboveSnow(float floor) {
        return quad -> {
            if (floor == 0) return true;
            for (int i = 0; i < 4; i++) quad.pos(i, quad.x(i), Math.max(floor, quad.y(i)), quad.z(i));
            return true;
        };
    }

    static net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform iceFaceCulling(
            VoxelShape ice, Function<Direction, VoxelShape> neighborFace) {
        return quad -> {
            Direction face = quad.cullFace();
            if (face == null) return true;
            if (!Shapes.joinIsNotEmpty(ice.getFaceShape(face), neighborFace.apply(face), BooleanOp.ONLY_FIRST)) return false;
            // The renderer must not reapply the host's (snow-only) face test to this ice quad.
            quad.cullFace(null);
            return true;
        };
    }

    static VoxelShape iceShape(BlockState state) {
        int bottom = 0;
        int top;
        if (FrozenWaterloggedBlocks.isFrozen(state)) {
            bottom = FiniteWaterloggedPlants.snowLayers(state);
            top = FrozenWaterloggedBlocks.iceHeight(state);
        } else if (state.getBlock() instanceof FiniteIceBlock) {
            top = FiniteIceBlock.frozenLayers(state);
        } else {
            return Shapes.empty();
        }
        return iceShape(bottom, top);
    }

    static VoxelShape iceShape(int bottom, int top) {
        return top <= bottom ? Shapes.empty() : Block.box(0, bottom * 2, 0, 16, top * 2, 16);
    }

    static VoxelShape neighborFace(BlockState state, Direction face) {
        VoxelShape ice = iceShape(state);
        return ice.isEmpty() ? state.getFaceOcclusionShape(face.getOpposite()) : ice.getFaceShape(face.getOpposite());
    }

    private record Root(BlockStateModel.UnbakedRoot host, Variant ice) implements BlockStateModel.UnbakedRoot {
        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) {
            host.resolveDependencies(resolver);
            ice.resolveDependencies(resolver);
        }

        @Override public BlockStateModel bake(BlockState state, ModelBaker baker) {
            return new FrozenModel(host.bake(state, baker), ice.bake(baker));
        }

        @Override public Object visualEqualityGroup(BlockState state) {
            return new VisualGroup(host.visualEqualityGroup(state), ice);
        }
    }

    private record VisualGroup(Object host, Variant ice) {}

    private record CullingRoot(BlockStateModel.UnbakedRoot host) implements BlockStateModel.UnbakedRoot {
        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) { host.resolveDependencies(resolver); }

        @Override public BlockStateModel bake(BlockState state, ModelBaker baker) {
            return new CulledModel(host.bake(state, baker));
        }

        @Override public Object visualEqualityGroup(BlockState state) { return host.visualEqualityGroup(state); }
    }

    private record CulledModel(BlockStateModel host) implements BlockStateModel {
        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            host.collectParts(random, parts);
        }

        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random, Predicate<Direction> cullTest) {
            emitter.pushTransform(iceFaceCulling(iceShape(state),
                    face -> neighborFace(level.getBlockState(pos.relative(face)), face)));
            try {
                host.emitQuads(emitter, level, pos, state, random, cullTest);
            } finally {
                emitter.popTransform();
            }
        }

        @Override public Material.Baked particleMaterial() { return host.particleMaterial(); }
        @Override public int materialFlags() { return host.materialFlags(); }
    }

    private record FrozenModel(BlockStateModel host, BlockStateModelPart ice) implements BlockStateModel {
        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            host.collectParts(random, parts);
            parts.add(ice);
        }

        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random, Predicate<Direction> cullTest) {
            host.emitQuads(emitter, level, pos, state, random, cullTest);
            var offset = state.getOffset(pos);
            emitter.pushTransform(cancelPlantOffset(offset));
            emitter.pushTransform(aboveSnow(FiniteWaterloggedPlants.snowLayers(state) / 8.0F));
            emitter.pushTransform(iceFaceCulling(iceShape(state),
                    face -> neighborFace(level.getBlockState(pos.relative(face)), face)));
            try {
                // Host culling can hide ice that rises above the host's own opaque shape.
                ice.emitQuads(emitter, face -> false);
            } finally {
                emitter.popTransform();
                emitter.popTransform();
                emitter.popTransform();
            }
        }

        @Override public Material.Baked particleMaterial() { return ice.particleMaterial(); }
        @Override public int materialFlags() { return host.materialFlags() | ice.materialFlags(); }
    }
}
