package io.github.SirWashington;

import io.github.SirWashington.block.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Static machinery is compiled into the chunk containing each constituent block. */
final class MachineryFrameModel {
    static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelOnLoad().register((original, load) ->
                load.state().getBlock() instanceof WaterPumpBlock || load.state().getBlock() instanceof WaterValveBlock
                        ? new Root(original) : original));
    }

    private record Root(BlockStateModel.UnbakedRoot original) implements BlockStateModel.UnbakedRoot {
        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) { original.resolveDependencies(resolver); }
        @Override public BlockStateModel bake(BlockState state, ModelBaker baker) {
            return new Model(original.bake(state, baker), state.getBlock() instanceof WaterValveBlock);
        }
        @Override public Object visualEqualityGroup(BlockState state) { return state; }
    }

    private record Model(BlockStateModel original, boolean valve) implements BlockStateModel {
        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) { }
        @Override public Material.Baked particleMaterial() { return original.particleMaterial(); }
        @Override public int materialFlags() { return original.materialFlags(); }

        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random, Predicate<Direction> cullTest) {
            Object snapshot = level.getBlockEntityRenderData(pos);
            var data = snapshot instanceof MachineryRenderData structure ? structure : new MachineryRenderData(1, 0, 0, 0);
            var mesh = valve ? MachineryMesh.buildValveFrame(data.size()) : MachineryMesh.pump(data.size(), data.connections());
            var facing = state.getValue(WaterPumpBlock.FACING);
            var vertices = slice(mesh.vertices(), data, facing);
            var material = particleMaterial();
            for (int first = 0; first < vertices.size(); first += 4) {
                for (int i = 0; i < 4; i++) {
                    var p = vertices.get(first + i);
                    emitter.pos(i, p.x(), p.y(), p.z()).normal(i, p.nx(), p.ny(), p.nz())
                            .color(i, p.color()).uv(i, p.u() * 16, p.v() * 16);
                }
                emitter.materialBake(material, 0).emit();
            }
        }
    }

    static List<MachineryMesh.Vertex> slice(List<MachineryMesh.Vertex> source, MachineryRenderData data, Direction facing) {
        var result = new ArrayList<MachineryMesh.Vertex>();
        float left = data.column() - data.size() / 2F, bottom = data.row() - data.size() / 2F;
        for (int first = 0; first < source.size(); first += 4) {
            List<MachineryMesh.Vertex> polygon = source.subList(first, first + 4);
            // A face exactly on an internal partition belongs to only one cell.
            if (!ownsPlane(polygon, true, data.column(), data.size())
                    || !ownsPlane(polygon, false, data.row(), data.size())) continue;
            polygon = clip(polygon, true, left, true);
            polygon = clip(polygon, true, left + 1, false);
            polygon = clip(polygon, false, bottom, true);
            polygon = clip(polygon, false, bottom + 1, false);
            polygon = removeCollinear(polygon);
            if (polygon.size() < 3) continue;
            if (polygon.size() == 4) {
                for (var p : polygon) result.add(transform(p, left, bottom, facing));
            } else {
                for (int i = 1; i < polygon.size() - 1; i++) {
                    for (var p : List.of(polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1)))
                        result.add(transform(p, left, bottom, facing));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean ownsPlane(List<MachineryMesh.Vertex> quad, boolean x, int cell, int size) {
        float coordinate = x ? quad.getFirst().x() : quad.getFirst().y();
        if (quad.stream().anyMatch(p -> Math.abs((x ? p.x() : p.y()) - coordinate) > 1E-6)) return true;
        return cell == Math.clamp((int)Math.floor(coordinate + size / 2F), 0, size - 1);
    }

    private static List<MachineryMesh.Vertex> clip(List<MachineryMesh.Vertex> input, boolean x, float edge, boolean lower) {
        var result = new ArrayList<MachineryMesh.Vertex>();
        if (input.isEmpty()) return result;
        var a = input.getLast();
        float da = (x ? a.x() : a.y()) - edge;
        for (var b : input) {
            float db = (x ? b.x() : b.y()) - edge;
            boolean insideA = lower ? da >= 0 : da <= 0, insideB = lower ? db >= 0 : db <= 0;
            if (insideA != insideB) {
                float t = da / (da - db);
                result.add(new MachineryMesh.Vertex(
                        x ? edge : a.x() + t * (b.x() - a.x()), x ? a.y() + t * (b.y() - a.y()) : edge,
                        a.z() + t * (b.z() - a.z()), a.u() + t * (b.u() - a.u()), a.v() + t * (b.v() - a.v()),
                        a.nx(), a.ny(), a.nz(), a.color()));
            }
            if (insideB) result.add(b);
            a = b; da = db;
        }
        return result;
    }

    private static MachineryMesh.Vertex transform(MachineryMesh.Vertex p, float left, float bottom, Direction facing) {
        var u = SquareStructure.right(facing); var v = SquareStructure.up(facing);
        float x = p.x() - left - .5F, y = p.y() - bottom - .5F;
        return new MachineryMesh.Vertex(
                .5F + x * u.getStepX() + y * v.getStepX() + p.z() * facing.getStepX(),
                .5F + x * u.getStepY() + y * v.getStepY() + p.z() * facing.getStepY(),
                .5F + x * u.getStepZ() + y * v.getStepZ() + p.z() * facing.getStepZ(), p.u(), p.v(),
                p.nx() * u.getStepX() + p.ny() * v.getStepX() + p.nz() * facing.getStepX(),
                p.nx() * u.getStepY() + p.ny() * v.getStepY() + p.nz() * facing.getStepY(),
                p.nx() * u.getStepZ() + p.ny() * v.getStepZ() + p.nz() * facing.getStepZ(), p.color());
    }

    private static List<MachineryMesh.Vertex> removeCollinear(List<MachineryMesh.Vertex> polygon) {
        var result = new ArrayList<>(polygon);
        for (int i = 0; result.size() >= 3 && i < result.size();) {
            var a = result.get((i + result.size() - 1) % result.size());
            var b = result.get(i); var c = result.get((i + 1) % result.size());
            double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
            double vx = c.x() - b.x(), vy = c.y() - b.y(), vz = c.z() - b.z();
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            if (nx * nx + ny * ny + nz * nz < 1E-16) { result.remove(i); i = 0; }
            else i++;
        }
        return result;
    }
}
