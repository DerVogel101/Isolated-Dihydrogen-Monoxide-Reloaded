package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

public final class AntiRainGeneratorBlockEntity extends BlockEntity {
    public static final BlockEntityType<AntiRainGeneratorBlockEntity> TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "anti_rain_generator"),
            FabricBlockEntityTypeBuilder.create(AntiRainGeneratorBlockEntity::new,
                    ModBlocks.ANTI_RAIN_GENERATOR).build());

    private static final Map<ServerLevel, Long2IntOpenHashMap> PROTECTED_CHUNKS = new WeakHashMap<>();
    private boolean registered;

    public AntiRainGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public static void initialize() {
    }

    public static boolean blocksRain(ServerLevel level, BlockPos pos) {
        Long2IntOpenHashMap chunks = PROTECTED_CHUNKS.get(level);
        return chunks != null && chunks.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4)) > 0;
    }

    @Override
    public void setLevel(Level level) {
        if (this.level != level) unregister();
        super.setLevel(level);
        syncRegistration();
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        syncRegistration();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        syncRegistration();
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    private void syncRegistration() {
        // The block state snapshot is authoritative here. Querying the world during chunk post-load
        // re-enters ServerChunkCache for this same chunk and can deadlock its load.
        BlockState state = getBlockState();
        boolean shouldRegister = level instanceof ServerLevel && !isRemoved()
                && state.is(ModBlocks.ANTI_RAIN_GENERATOR)
                && state.getValue(AntiRainGeneratorBlock.POWERED);
        if (shouldRegister == registered) return;
        if (shouldRegister) register(); else unregister();
    }

    private void register() {
        ServerLevel server = (ServerLevel) level;
        Long2IntOpenHashMap chunks = PROTECTED_CHUNKS.computeIfAbsent(server, ignored -> new Long2IntOpenHashMap());
        updateCoverage(chunks, 1);
        registered = true;
    }

    private void unregister() {
        if (!registered || !(level instanceof ServerLevel server)) return;
        Long2IntOpenHashMap chunks = PROTECTED_CHUNKS.get(server);
        if (chunks != null) {
            updateCoverage(chunks, -1);
            if (chunks.isEmpty()) PROTECTED_CHUNKS.remove(server);
        }
        registered = false;
    }

    private void updateCoverage(Long2IntOpenHashMap chunks, int delta) {
        int centerX = worldPosition.getX() >> 4;
        int centerZ = worldPosition.getZ() >> 4;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                long key = ChunkPos.pack(x, z);
                int count = chunks.get(key) + delta;
                if (count == 0) chunks.remove(key); else chunks.put(key, count);
            }
        }
    }
}
