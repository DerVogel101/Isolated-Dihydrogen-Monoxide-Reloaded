package io.github.SirWashington.item;

import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class FiniteWaterBucketItem extends BucketItem {
    public FiniteWaterBucketItem(Properties properties) {
        super(ModFluids.FINITE_WATER, properties);
    }

    @Override
    public boolean emptyContents(LivingEntity user, Level level, BlockPos pos, BlockHitResult hitResult) {
        int current = FiniteWaterPhysics.getWaterLevel(level, pos);
        if (current < 0) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel) || !FiniteWaterPhysics.placeFullBucket(serverLevel, pos)) {
            return false;
        }
        playEmptySound(user, level, pos);
        return true;
    }
}
