package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
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

public final class WaterValveBlockEntity extends BlockEntity implements net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    public static final BlockEntityType<WaterValveBlockEntity> TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "water_valve"),
            FabricBlockEntityTypeBuilder.create(WaterValveBlockEntity::new, ModBlocks.WATER_VALVE).build());
    private int size = 1, column, row, startProgress = ValveGeometry.DURATION;
    private long startedAt;
    private int cachedProgress = -1;
    private int previousProgress = ValveGeometry.DURATION;
    private int previousSoundProgress = -1;
    private boolean previousSoundOpening;
    private VoxelShape collision;

    public WaterValveBlockEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
    public static void initialize() { }
    public int size() { return size; }
    public int column() { return column; }
    public int row() { return row; }
    public boolean isController() { return column == 0 && row == 0; }
    @Override public MachineryRenderData getRenderData() { return new MachineryRenderData(size, column, row, 0); }

    public double progress(float partialTick) {
        double elapsed = level == null ? 0 : Math.max(0, level.getGameTime() - startedAt + partialTick);
        return Math.clamp(startProgress + (getBlockState().getValue(WaterValveBlock.POWERED) ? -elapsed : elapsed),
                0, ValveGeometry.DURATION);
    }

    public VoxelShape collisionShape() {
        int progress = (int)progress(0);
        if (collision == null || progress != cachedProgress) {
            collision = ValveGeometry.collision(size, column, row, getBlockState().getValue(WaterValveBlock.FACING), progress);
            cachedProgress = progress;
        }
        return collision;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WaterValveBlockEntity valve) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 5 == 0) valve.refresh();
        int progress = (int)valve.progress(0);
        if ((progress >= ValveGeometry.SEALED_AT) != (valve.previousProgress >= ValveGeometry.SEALED_AT))
            io.github.SirWashington.features.PumpManager.geometryChanged(level, pos);
        if (valve.isController()) valve.tickSound(progress);
        // Geometry changes without replacing waterlogged states. Wake stationary water on both sides.
        if (level.getGameTime() % 5 == 0 && progress > 0 && progress < ValveGeometry.DURATION
                || progress != valve.previousProgress && (progress == 0 || progress == ValveGeometry.SEALED_AT
                || progress == ValveGeometry.SEALED_AT - 1 || progress == ValveGeometry.DURATION)) {
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                var fluid = level.getFluidState(neighbor);
                if (!fluid.isEmpty()) level.scheduleTick(neighbor, fluid.getType(), fluid.getType().getTickDelay(level));
            }
            var fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
        valve.previousProgress = progress;
    }

    private void tickSound(int progress) {
        boolean opening = getBlockState().getValue(WaterValveBlock.POWERED);
        int phase = ValveGeometry.motionPhase(progress, opening);
        // Loading an idle valve must not replay its impact; only the controller emits sound.
        if (previousSoundProgress >= 0 && progress != previousSoundProgress) {
            boolean changed = opening != previousSoundOpening
                    || phase != ValveGeometry.motionPhase(previousSoundProgress, previousSoundOpening);
            if (phase == 0) {
                playMechanismSound(opening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.ANVIL_LAND, .85F, .55F);
            } else if (changed || progress % (phase == 2 ? 15 : 20) == 0) {
                SoundEvent sound = switch (phase) {
                    case 1 -> opening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE;
                    case 2 -> opening ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND;
                    default -> opening ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE;
                };
                playMechanismSound(sound, phase == 2 ? .9F : .75F, phase == 2 ? .6F : .5F);
            }
        }
        previousSoundProgress = progress;
        previousSoundOpening = opening;
    }

    private void playMechanismSound(SoundEvent sound, float volume, float pitch) {
        Direction facing = getBlockState().getValue(WaterValveBlock.FACING);
        var center = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)
                .add(SquareStructure.right(facing).getUnitVec3().scale((size - 1) / 2.0))
                .add(SquareStructure.up(facing).getUnitVec3().scale((size - 1) / 2.0));
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.BLOCKS,
                volume + size * .12F, pitch - (size - 1) * .035F);
    }

    /** One update assigns the same time origin to every member, regardless of ticker order. */
    public void refresh() {
        if (level == null || level.isClientSide()) return;
        SquareStructure square = SquareStructure.find(level, worldPosition);
        if (!(level.getBlockEntity(square.origin()) instanceof WaterValveBlockEntity controller)) return;
        boolean powered = square.powered(level);
        int initial = controller.startProgress;
        long start = controller.startedAt;
        if (controller.getBlockState().getValue(WaterValveBlock.POWERED) != powered) {
            initial = (int)controller.progress(0);
            start = level.getGameTime();
        }
        for (int x = 0; x < square.size(); x++) for (int y = 0; y < square.size(); y++) {
            if (!(level.getBlockEntity(square.cell(x, y)) instanceof WaterValveBlockEntity member)) continue;
            BlockState state = member.getBlockState();
            if (member.size == square.size() && member.column == x && member.row == y
                    && member.startProgress == initial && member.startedAt == start
                    && state.getValue(WaterValveBlock.POWERED) == powered) continue;
            member.size = square.size(); member.column = x; member.row = y;
            member.startProgress = initial; member.startedAt = start; member.collision = null;
            member.setChanged();
            BlockState updated = state.setValue(WaterValveBlock.POWERED, powered);
            if (state != updated) level.setBlock(member.worldPosition, updated, Block.UPDATE_CLIENTS);
            level.sendBlockUpdated(member.worldPosition, state, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setBlockState(BlockState state) { super.setBlockState(state); collision = null; }
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Size", size); output.putInt("Column", column); output.putInt("Row", row);
        output.putInt("Progress", startProgress); output.putLong("StartedAt", startedAt);
    }
    @Override
    protected void loadAdditional(ValueInput input) {
        var previous = getRenderData();
        super.loadAdditional(input);
        size = Math.clamp(input.getIntOr("Size", 1), 1, 3);
        column = Math.clamp(input.getIntOr("Column", 0), 0, size - 1);
        row = Math.clamp(input.getIntOr("Row", 0), 0, size - 1);
        startProgress = Math.clamp(input.getIntOr("Progress", ValveGeometry.DURATION), 0, ValveGeometry.DURATION);
        startedAt = input.getLongOr("StartedAt", 0);
        collision = null;
        if (level != null && level.isClientSide() && !previous.equals(getRenderData()))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
}
