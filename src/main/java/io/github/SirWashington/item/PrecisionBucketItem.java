package io.github.SirWashington.item;

import io.github.SirWashington.component.ModDataComponentTypes;
import io.github.SirWashington.features.FiniteWaterPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.List;

public class PrecisionBucketItem extends Item {

    public PrecisionBucketItem(Item.Properties properties) {
        super(properties);
    }

    public InteractionResult useOn(UseOnContext useOnContext) {
        Level level = useOnContext.getLevel();
        ItemStack itemStack = useOnContext.getItemInHand();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int fill = itemStack.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0);
        BlockPos clicked = useOnContext.getClickedPos();
        boolean pickingUp = useOnContext.getPlayer() != null && useOnContext.getPlayer().isCrouching();
        BlockPos adjacent = clicked.relative(useOnContext.getClickedFace());
        BlockPos target = FiniteWaterPhysics.getWaterLevel(level, clicked) >= 0 ? clicked : adjacent;
        int targetLevel = FiniteWaterPhysics.getWaterLevel(level, target);

        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || targetLevel < 0) {
            return InteractionResult.FAIL;
        }

        if (pickingUp) {
            if (targetLevel == 0) {
                return InteractionResult.FAIL;
            }
            int moved = Math.min(8 - fill, targetLevel);
            if (moved == 0) {
                return InteractionResult.FAIL;
            }
            FiniteWaterPhysics.setWaterLevel(serverLevel, target, targetLevel - moved);
            setFill(itemStack, fill + moved);
        } else {
            int moved = Math.min(fill, FiniteWaterPhysics.getWaterCapacity(level, target) - targetLevel);
            if (moved == 0) {
                return InteractionResult.FAIL;
            }
            FiniteWaterPhysics.setWaterLevel(serverLevel, target, targetLevel + moved);
            setFill(itemStack, fill - moved);
        }
        return InteractionResult.SUCCESS;
    }

    private static void setFill(ItemStack itemStack, int fill) {
        itemStack.set(ModDataComponentTypes.BUCKET_FILL_LEVEL, fill);
        itemStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(fill == 8), List.of(), List.of()));
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        int fill = itemStack.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0);
        tooltip.accept(Component.literal("Bucket contains: " + fill + "/8 finite-water units"));
    }

    @Override
    public boolean isBarVisible(ItemStack itemStack) {
        return true;
    }

    @Override
    public int getBarColor(ItemStack itemStack) {
        return ARGB.opaque(0x388CFC);
    }

    @Override
    public int getBarWidth(ItemStack itemStack) {
        int fillLevel = itemStack.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0);
        int maxFillLevel = 8;
        float fraction = (float) fillLevel / (float) maxFillLevel;
        return (int) (13f * fraction);
    }
}
