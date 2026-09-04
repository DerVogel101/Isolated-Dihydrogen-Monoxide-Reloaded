package io.github.SirWashington.block;

import com.mojang.serialization.MapCodec;
import io.github.SirWashington.features.FiniteWaterloggedPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LayeredFiniteIceBlock extends FiniteIceBlock {
    public static final MapCodec<LayeredFiniteIceBlock> CODEC = simpleCodec(LayeredFiniteIceBlock::new);
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 7);
    private static final VoxelShape[] SHAPES = Block.boxes(7, height -> Block.column(16, 0, height * 2));

    public LayeredFiniteIceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(FiniteWaterloggedPlants.LEVEL, 0));
    }

    @Override
    protected MapCodec<LayeredFiniteIceBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, FiniteWaterloggedPlants.LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }
}
