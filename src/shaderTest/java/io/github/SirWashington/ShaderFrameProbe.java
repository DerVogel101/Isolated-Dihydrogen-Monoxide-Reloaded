package io.github.SirWashington;

import io.github.SirWashington.block.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;

/** Real synchronized multiblock snapshots and baked model emission at a chunk boundary. */
final class ShaderFrameProbe {
    private static volatile BlockPos pumpOrigin, valveOrigin;
    static void populate(ServerLevel level, BlockPos center) {
        pumpOrigin = new BlockPos((center.getX() & ~15) + 16, center.getY() + 1, center.getZ() + 10);
        valveOrigin = pumpOrigin.south(5);
        for (var origin : new BlockPos[]{pumpOrigin, valveOrigin}) {
            var block = origin.equals(pumpOrigin) ? ModBlocks.WATER_PUMP : ModBlocks.WATER_VALVE;
            for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++)
                level.setBlockAndUpdate(origin.west(column).above(row), block.defaultBlockState().setValue(WaterPumpBlock.FACING, Direction.NORTH));
        }
    }
    static void shrink(ServerLevel level) {
        for (var origin : new BlockPos[]{pumpOrigin, valveOrigin})
            for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++)
                if (column == 2 || row == 2) level.setBlockAndUpdate(origin.west(column).above(row), Blocks.AIR.defaultBlockState());
    }
    static void verify(int size) {
        var client = Minecraft.getInstance();
        for (var origin : new BlockPos[]{pumpOrigin, valveOrigin}) {
            if (origin == null) throw new AssertionError("Multiblock fixture was not populated");
            int[] quads = {0};
            for (int column = 0; column < size; column++) for (int row = 0; row < size; row++) {
                var pos = origin.west(column).above(row);
                Object snapshot = client.level.getBlockEntityRenderData(pos);
                if (!(snapshot instanceof MachineryRenderData data) || data.size() != size
                        || data.column() != column || data.row() != row)
                    throw new AssertionError("Stale multiblock render snapshot at " + pos + ": " + snapshot);
                var state = client.level.getBlockState(pos);
                if (state.getRenderShape() != RenderShape.MODEL) throw new AssertionError("Frame is not chunk rendered");
                var emitter = Renderer.get().quadEmitter(quad -> {
                    quads[0]++;
                    for (int i = 0; i < 4; i++)
                        if (quad.x(i) < -1E-5 || quad.x(i) > 1.00001F || quad.y(i) < -1E-5 || quad.y(i) > 1.00001F
                                || quad.z(i) < -1E-5 || quad.z(i) > 1.00001F)
                            throw new AssertionError("Baked frame crosses its owning block");
                });
                client.getModelManager().getBlockStateModelSet().get(state)
                        .emitQuads(emitter, client.level, pos, state, RandomSource.create(0), face -> false);
            }
            if (quads[0] == 0) throw new AssertionError("No static frame quads emitted");
        }
        System.out.println("MACHINERY_CHUNK_FRAME_PASS size=" + size + " boundary=true");
    }
}
