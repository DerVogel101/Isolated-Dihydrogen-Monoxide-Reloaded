package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.features.PumpFlow;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WaterPumpBlockEntity extends BlockEntity implements net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    public static final BlockEntityType<WaterPumpBlockEntity> TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "water_pump"),
            FabricBlockEntityTypeBuilder.create(WaterPumpBlockEntity::new, ModBlocks.WATER_PUMP, ModBlocks.MUTED_WATER_PUMP).build());
    private int size = 1, column, row, connections;
    private VoxelShape collision;
    public final PumpAnimation animation = new PumpAnimation();

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
    public static void initialize() { }
    public int size() { return size; }
    public int column() { return column; }
    public int row() { return row; }
    public int connections() { return connections; }
    public boolean isController() { return column == 0 && row == 0; }
    @Override public MachineryRenderData getRenderData() { return new MachineryRenderData(size, column, row, connections); }

    public VoxelShape collisionShape() {
        if (collision == null) collision = PumpGeometry.collision(size, column, row, getBlockState().getValue(WaterPumpBlock.FACING));
        return collision;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WaterPumpBlockEntity pump) {
        if (level.isClientSide()) {
            pump.animation.tick(state.getValue(WaterPumpBlock.POWERED));
        }
    }

    public void playRunningSound(ServerLevel level, PumpStructure stage, boolean wet) {
        if (muted() || !getBlockState().getValue(WaterPumpBlock.POWERED)) return;
        float mechanicalVolume = 0.22F + stage.size() * 0.07F;
        float variation = (level.getRandom().nextFloat() - 0.5F) * 0.06F;
        BlockPos soundPos = stage.cell((stage.size() - 1) / 2, (stage.size() - 1) / 2);
        if (wet) {
            // Volume above one also extends vanilla's attenuation radius: 1.5 gives 24 blocks.
            level.playSound(null, soundPos, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                    SoundSource.BLOCKS, 1.5F, 0.78F + variation);
            level.playSound(null, soundPos, SoundEvents.MINECART_RIDING,
                    SoundSource.BLOCKS, mechanicalVolume * 0.45F, 0.76F + variation);
        } else {
            level.playSound(null, soundPos, SoundEvents.MINECART_RIDING,
                    SoundSource.BLOCKS, mechanicalVolume, 0.72F + variation);
        }
    }

    public void refresh() {
        if (level == null || level.isClientSide()) return;
        PumpStructure stage = PumpStructure.find(level, worldPosition);
        int nextColumn = PumpStructure.coordinate(worldPosition, PumpStructure.right(stage.facing()))
                - PumpStructure.coordinate(stage.origin(), PumpStructure.right(stage.facing()));
        int nextRow = PumpStructure.coordinate(worldPosition, PumpStructure.up(stage.facing()))
                - PumpStructure.coordinate(stage.origin(), PumpStructure.up(stage.facing()));
        int nextConnections = stage.connections(level);
        boolean powered = stage.powered(level);
        BlockState state = getBlockState();
        if (state.getValue(WaterPumpBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(WaterPumpBlock.POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered && nextColumn == 0 && nextRow == 0 && !muted()) {
                level.playSound(null, stage.cell((stage.size() - 1) / 2, (stage.size() - 1) / 2),
                        SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS,
                        0.5F + stage.size() * 0.12F, 0.68F);
            }
        }
        if (size != stage.size() || column != nextColumn || row != nextRow || connections != nextConnections) {
            size = stage.size(); column = nextColumn; row = nextRow; connections = nextConnections;
            collision = null;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean muted() {
        return getBlockState().getBlock() instanceof WaterPumpBlock pump && pump.muted();
    }

    @Override
    public void setBlockState(BlockState state) { super.setBlockState(state); collision = null; }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        io.github.SirWashington.features.PumpManager.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        io.github.SirWashington.features.PumpManager.unregister(level, worldPosition);
        PumpStructure.invalidate(level);
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        PumpStructure.invalidate(level);
        super.clearRemoved();
        io.github.SirWashington.features.PumpManager.register(level, worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Size", size); output.putInt("Column", column); output.putInt("Row", row);
        output.putInt("Connections", connections);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        var previous = getRenderData();
        super.loadAdditional(input);
        size = Math.clamp(input.getIntOr("Size", 1), 1, 3);
        column = Math.clamp(input.getIntOr("Column", 0), 0, size - 1);
        row = Math.clamp(input.getIntOr("Row", 0), 0, size - 1);
        connections = input.getIntOr("Connections", 0) & 3;
        collision = null;
        if (level != null && level.isClientSide() && !previous.equals(getRenderData()))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
}
