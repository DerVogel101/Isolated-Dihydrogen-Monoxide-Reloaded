package io.github.SirWashington.block;

import com.mojang.serialization.MapCodec;
import io.github.SirWashington.features.FiniteWaterPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/** Creates one full finite-water block per rising redstone edge. */
public final class DihydrogenMonoxideAssemblerBlock extends Block {
    public static final MapCodec<DihydrogenMonoxideAssemblerBlock> CODEC =
            simpleCodec(properties -> new DihydrogenMonoxideAssemblerBlock(properties, false));
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final boolean muted;

    public DihydrogenMonoxideAssemblerBlock(Properties properties, boolean muted) {
        super(properties);
        this.muted = muted;
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    public boolean muted() { return muted; }

    @Override
    public MapCodec<DihydrogenMonoxideAssemblerBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) refresh(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   Orientation orientation, boolean movedByPiston) {
        refresh(state, level, pos);
    }

    private void refresh(BlockState state, Level level, BlockPos pos) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) == powered) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        if (!powered || !(level instanceof ServerLevel server)) return;
        BlockPos target = pos.below();
        if (FiniteWaterPhysics.getWaterLevel(server, target) < 0
                || FiniteWaterPhysics.getWaterCapacity(server, target) < 8) return;
        FiniteWaterPhysics.setWaterLevel(server, target, 8);
        if (!muted) server.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1F, 1F);
    }
}
