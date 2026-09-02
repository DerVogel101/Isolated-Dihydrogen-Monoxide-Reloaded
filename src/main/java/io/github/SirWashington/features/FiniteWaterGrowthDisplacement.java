package io.github.SirWashington.features;

import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FiniteWaterGrowthDisplacement {
    private static final ThreadLocal<Growth> ACTIVE = new ThreadLocal<>();

    private FiniteWaterGrowthDisplacement() {
    }

    public static void begin(ServerLevel level) {
        ACTIVE.set(new Growth(level, new LinkedHashMap<>()));
    }

    public static BlockState prepareReplacement(Level level, BlockPos pos, BlockState replacement) {
        Growth growth = ACTIVE.get();
        if (growth == null || growth.level() != level) {
            return replacement;
        }
        BlockState state = level.getBlockState(pos);
        if (FiniteWaterloggedPlants.canHoldFiniteWater(state)) {
            growth.originals().putIfAbsent(pos.immutable(), state);
            if (FiniteWaterloggedPlants.getLevel(state) > 0
                    && ModFluids.isFiniteWater(replacement.getFluidState().getType())) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return replacement;
    }

    public static void finish(ServerLevel level, boolean succeeded) {
        Growth growth = ACTIVE.get();
        ACTIVE.remove();
        if (growth == null || growth.level() != level
                || growth.originals().values().stream().noneMatch(state -> FiniteWaterloggedPlants.getLevel(state) > 0)) {
            return;
        }

        if (!succeeded) {
            growth.originals().forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            return;
        }

        growth.originals().forEach((pos, state) -> {
            int amount = FiniteWaterloggedPlants.getLevel(state);
            if (amount > 0 && !ModFluids.isFiniteWater(level.getFluidState(pos).getType())) {
                FiniteWaterPhysics.displaceWater(level, pos, amount);
            }
        });
    }

    private record Growth(ServerLevel level, Map<BlockPos, BlockState> originals) {
    }
}
