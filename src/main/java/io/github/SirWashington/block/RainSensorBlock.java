package io.github.SirWashington.block;

import com.mojang.serialization.MapCodec;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RainSensorBlock extends WaterloggedTransparentBlock {
    public static final MapCodec<RainSensorBlock> CODEC = simpleCodec(RainSensorBlock::new);
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 3, 4, 3), box(13, 0, 1, 15, 4, 3),
            box(1, 0, 13, 3, 4, 15), box(13, 0, 13, 15, 4, 15),
            box(0, 4, 0, 16, 6, 2), box(0, 4, 14, 16, 6, 16),
            box(0, 4, 2, 2, 6, 14), box(14, 4, 2, 16, 6, 14),
            box(4, 4, 2, 6, 6, 14), box(10, 4, 2, 12, 6, 14),
            box(2, 4, 7, 14, 6, 9));

    public RainSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(INVERTED, false).setValue(POWERED, false));
    }

    @Override
    public MapCodec<RainSensorBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, updatePower(state.cycle(INVERTED), level, pos), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, 20);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVERTED, POWERED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState updated = updatePower(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 20);
    }

    private BlockState updatePower(BlockState state, Level level, BlockPos pos) {
        var fluid = state.getFluidState();
        boolean submerged = fluid.getAmount() > 2 && (ModFluids.isFiniteWater(fluid.getType())
                || fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER));
        // Rain particles are client-only. The server checks whether rain reaches this column.
        boolean detected = submerged || level.isRainingAt(pos.above());
        return state.setValue(POWERED, detected != state.getValue(INVERTED));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
