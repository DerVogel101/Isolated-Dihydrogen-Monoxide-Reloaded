package io.github.SirWashington.item;

import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class FiniteWaterBucketItem extends BucketItem {
    public FiniteWaterBucketItem(Properties properties) {
        super(ModFluids.FINITE_WATER, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, getFluidContext());
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            if (FiniteWaterloggedPlants.canHoldFiniteWater(level.getBlockState(pos))) {
                if (!level.mayInteract(player, pos)
                        || !player.mayUseItemAt(pos, hitResult.getDirection(), itemStack)) {
                    return InteractionResult.FAIL;
                }
                if (emptyContents(player, level, pos, hitResult)) {
                    checkExtraContent(player, level, itemStack, pos);
                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, itemStack);
                    }
                    player.awardStat(Stats.ITEM_USED.get(this));
                    ItemStack result = ItemUtils.createFilledResult(
                            itemStack, player, getEmptySuccessItem(itemStack, player)
                    );
                    return InteractionResult.SUCCESS.heldItemTransformedTo(result);
                }
            }
        }
        return super.use(level, player, hand);
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
