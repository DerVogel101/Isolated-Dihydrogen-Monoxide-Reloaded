package io.github.SirWashington.features;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class FiniteWaterSounds {
    private static final Map<ServerLevel, Map<BlockPos, Flow>> PENDING = new WeakHashMap<>();

    private FiniteWaterSounds() {
    }

    static void record(ServerLevel level, BlockPos pos, double units) {
        accumulate(PENDING.computeIfAbsent(level, ignored -> new HashMap<>()), pos, units);
    }

    static void accumulate(Map<BlockPos, Flow> flows, BlockPos pos, double units) {
        if (units <= 0) {
            return;
        }
        // One sound candidate per 4x4x4 area per tick, located at an actual transfer.
        BlockPos area = new BlockPos(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        flows.compute(area, (ignored, previous) -> new Flow(
                previous == null ? pos.immutable() : previous.pos(),
                Math.min(64, units + (previous == null ? 0 : previous.units()))));
    }

    static float volume(double units) {
        double flow = Math.clamp(units, 0, 64) / 64;
        return (float) (0.55 * Math.sqrt(flow) + 0.45 * flow);
    }

    static float soundChance(double units) {
        return units <= 0 ? 0 : (float) ((1 + 12 * Math.clamp(units, 0, 64) / 64) / 64);
    }

    static float pitch(double units, float variation) {
        // More moving water sounds fuller; keep jitter small enough to preserve that trend.
        return (float) (1.2 - 0.9 * Math.clamp(units, 0, 64) / 64) + variation * 0.1F;
    }

    static void tick(ServerLevel level) {
        // Consume only this tick's successful transfers; settled water has no candidates.
        Map<BlockPos, Flow> flows = PENDING.remove(level);
        if (flows == null) {
            return;
        }
        for (Flow flow : flows.values()) {
            // Keep vanilla's ambient sample, but make heavier flow denser and lower-pitched.
            if (level.getRandom().nextFloat() < soundChance(flow.units())) {
                level.playSound(null, flow.pos(), SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                        volume(flow.units()) * (0.75F + level.getRandom().nextFloat() * 0.25F),
                        pitch(flow.units(), level.getRandom().nextFloat()));
            }
        }
    }

    record Flow(BlockPos pos, double units) {
    }
}
