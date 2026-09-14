package io.github.SirWashington.features;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.List;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.SirWashington.item.PrecisionBucketItem;
import io.github.SirWashington.item.FiniteWaterBucketItem;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** LEVEL stores total water; the phase records how much of it is frozen. */
public final class FrozenWaterloggedBlocks {
    // Keep false/true serialized names so existing dry and entirely frozen blocks load unchanged.
    public enum Phase implements StringRepresentable {
        NONE("false", 0), ALL("true", 8), ONE("1", 1), TWO("2", 2), THREE("3", 3),
        FOUR("4", 4), FIVE("5", 5), SIX("6", 6), SEVEN("7", 7);
        private final String name;
        private final int units;
        Phase(String name, int units) { this.name = name; this.units = units; }
        @Override public String getSerializedName() { return name; }
    }
    public static final EnumProperty<Phase> FROZEN = EnumProperty.create("finite_water_frozen", Phase.class);

    // Bound retention for mods that generate fresh shapes; weak keys also compare by identity.
    private static final List<LoadingCache<VoxelShape, VoxelShape>> ICE_SHAPES = IntStream.rangeClosed(0, 8)
            .mapToObj(height -> {
                VoxelShape ice = Block.box(0, 0, 0, 16, height * 2, 16);
                return CacheBuilder.newBuilder().weakKeys().maximumSize(2048)
                        .build(CacheLoader.from((VoxelShape original) -> Shapes.or(original, ice)));
            }).toList();

    private FrozenWaterloggedBlocks() {}

    public static boolean isFrozen(BlockState state) {
        return frozenUnits(state) > 0;
    }

    public static int frozenUnits(BlockState state) {
        if (!state.hasProperty(FROZEN)) return 0;
        return Math.min(state.getValue(FROZEN).units, state.getValue(FiniteWaterloggedPlants.LEVEL));
    }

    public static BlockState withLiquid(BlockState state, int liquid) {
        int ice = frozenUnits(state);
        Phase phase = Phase.ALL;
        if (liquid > 0) {
            for (Phase candidate : Phase.values()) if (candidate.units == ice) phase = candidate;
        }
        return state.setValue(FROZEN, phase).setValue(FiniteWaterloggedPlants.LEVEL, ice + liquid);
    }

    public static boolean isWaterTool(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.getItem() instanceof FiniteWaterBucketItem
                || stack.getItem() instanceof PrecisionBucketItem;
    }

    public static int iceHeight(BlockState state) {
        int units = frozenUnits(state);
        if (FiniteWaterloggedPlants.snowLayers(state) > 0) return Math.min(8, FiniteWaterloggedPlants.snowLayers(state) + units);
        // Rounded half-block fluid heights must not seal the last unfilled unit with collision.
        return Math.min(units == 8 ? 8 : 7,
                FiniteWaterloggedPlants.visualFluidLevel(state.setValue(FROZEN, Phase.NONE), units));
    }

    public static VoxelShape withIce(BlockState state, VoxelShape original) {
        if (!isFrozen(state)) return original;
        var cache = ICE_SHAPES.get(iceHeight(state));
        VoxelShape result = cache.getUnchecked(original);
        // Both collision overloads are hooked; composing an already iced shape is a no-op.
        cache.put(result, result);
        return result;
    }

    private static BlockPos partner(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
            return pos.relative(ChestBlock.getConnectedDirection(state));
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        if (state.hasProperty(BlockStateProperties.BED_PART))
            return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING),
                    state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? 1 : -1);
        return pos;
    }

    public static boolean isLocked(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isFrozen(state)) return true;
        BlockPos otherPos = partner(state, pos);
        if (otherPos.equals(pos)) return false;
        BlockState other = level.getBlockState(otherPos);
        return other.is(state.getBlock()) && isFrozen(other);
    }

    public static void freeze(ServerLevel level, BlockPos pos, BlockState state) {
        FiniteWaterPhysics.setWaterloggedBlock(level, pos, state.setValue(FROZEN, Phase.ALL));
        level.scheduleTick(pos, state.getBlock(), 20);
    }

    public static boolean thaw(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isFrozen(state)) return false;
        FiniteWaterPhysics.setWaterloggedBlock(level, pos, state.setValue(FROZEN, Phase.NONE));
        var fluid = level.getBlockState(pos).getFluidState();
        level.scheduleTick(pos, fluid.getType(), 1);
        return true;
    }

    public static void tick(ServerLevel level, BlockPos pos) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11) thaw(level, pos);
        else level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 20);
    }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                isLocked(level, hit.getBlockPos()) && !isWaterTool(player.getItemInHand(hand))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (!isLocked(level, pos)) return true;
            if (level instanceof ServerLevel server) {
                BlockPos other = partner(state, pos);
                thaw(server, pos);
                if (server.getBlockState(other).is(state.getBlock())) thaw(server, other);
                server.levelEvent(2001, pos, Block.getId(Blocks.ICE.defaultBlockState()));
            }
            return false;
        });
    }
}
