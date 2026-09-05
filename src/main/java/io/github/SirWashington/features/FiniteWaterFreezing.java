package io.github.SirWashington.features;

import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.FiniteIceBlock;
import io.github.SirWashington.block.LayeredFiniteIceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

public final class FiniteWaterFreezing {
    private FiniteWaterFreezing() {}

    public static boolean freeze(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean host = state.hasProperty(FrozenWaterloggedBlocks.FROZEN) && FiniteWaterloggedPlants.getLevel(state) > 0;
        if (!host && !state.is(ModBlocks.FINITE_WATER) && !(state.getBlock() instanceof LayeredFiniteIceBlock)) return false;
        int liquid = FiniteWaterPhysics.getWaterLevel(level, pos);
        if (liquid <= 0 || level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) != pos.getY() + 1
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10
                || !level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) return false;
        if (host) {
            FrozenWaterloggedBlocks.freeze(level, pos, state);
            return true;
        }
        int frozen = liquid + FiniteIceBlock.frozenLayers(state);
        if (frozen > 8) return false;
        level.setBlockAndUpdate(pos, frozen == 8 ? ModBlocks.FINITE_ICE.defaultBlockState()
                : ModBlocks.LAYERED_FINITE_ICE.defaultBlockState().setValue(LayeredFiniteIceBlock.LAYERS, frozen));
        return true;
    }
}
