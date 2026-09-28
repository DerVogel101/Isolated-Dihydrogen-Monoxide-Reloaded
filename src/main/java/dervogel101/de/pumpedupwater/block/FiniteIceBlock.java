package dervogel101.de.pumpedupwater.block;


import dervogel101.de.pumpedupwater.features.FiniteWaterloggedPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class FiniteIceBlock extends Block {


    public FiniteIceBlock(Properties properties) {
        super(properties);
    }


    public static int frozenLayers(BlockState state) {
        if (dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.isFrozen(state))
            return dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.frozenUnits(state);
        return state.getBlock() instanceof LayeredFiniteIceBlock ? state.getValue(LayeredFiniteIceBlock.LAYERS)
                : state.getBlock() instanceof FiniteIceBlock ? 8 : 0;
    }

    public static void releaseWater(Level level, BlockPos pos, BlockState state) {
        int amount = frozenLayers(state) + Math.max(0, FiniteWaterloggedPlants.getLevel(state));
        level.setBlockAndUpdate(pos, FiniteWaterloggedPlants.fluidState(amount).createLegacyBlock());
        level.scheduleTick(pos, level.getFluidState(pos).getType(), 1);
    }

    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity entity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, entity, tool);
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        if (!(this instanceof LayeredFiniteIceBlock) && EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0) {
            popResource(level, pos, new ItemStack(asItem()));
        } else {
            releaseWater(level, pos, state);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11) releaseWater(level, pos, state);
    }
}
