package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.AntiRainGeneratorBlockEntity;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FiniteWaterRainfall {
    private static final int MAX_RAIN_LEVEL = 3;

    private FiniteWaterRainfall() {
    }

    public static boolean tick(ServerLevel level, BlockPos surface) {
        if (!WaterPhysicsConfig.rainfallEnabled()) {
            return false;
        }
        return tick(level, surface, level.getRandom().nextDouble());
    }

    static boolean tick(ServerLevel level, BlockPos surface, double roll) {
        return tick(level, surface, roll, level.precipitationAt(surface.above()), level.isBrightOutside());
    }

    static boolean tick(ServerLevel level, BlockPos surface, double roll,
                        Biome.Precipitation precipitation, boolean brightOutside) {
        if (!WaterPhysicsConfig.rainfallEnabled() || level.isOutsideBuildHeight(surface)
                || !level.hasChunk(surface.getX() >> 4, surface.getZ() >> 4)) {
            return false;
        }
        double rainChance = WaterPhysicsConfig.rainfallChangeChance();
        boolean addingRain = precipitation == Biome.Precipitation.RAIN && roll < rainChance;

        BlockState surfaceState = level.getBlockState(surface);
        if (hasForeignFluid(surfaceState)) {
            return false;
        }

        BlockPos target = surface;
        int amount = FiniteWaterPhysics.getWaterLevel(level, target);
        int capacity = FiniteWaterPhysics.getWaterCapacity(level, target);
        if (amount < 0 || capacity == 0
                || (addingRain && amount < MAX_RAIN_LEVEL && amount >= capacity)) {
            target = surface.above();
            if (level.isOutsideBuildHeight(target) || !level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) {
                return false;
            }
            amount = FiniteWaterPhysics.getWaterLevel(level, target);
            capacity = FiniteWaterPhysics.getWaterCapacity(level, target);
        }

        BlockState targetState = level.getBlockState(target);
        if (amount < 0 || capacity == 0 || hasForeignFluid(targetState)
                || amount > MAX_RAIN_LEVEL) {
            return false;
        }

        BlockState selectorState = targetState.isAir() || targetState.is(ModBlocks.FINITE_WATER)
                ? level.getBlockState(target.below())
                : targetState;
        if (precipitation == Biome.Precipitation.RAIN) {
            if (addingRain) {
                return amount < MAX_RAIN_LEVEL
                        && WaterPhysicsConfig.isRainCollectionSurface(selectorState)
                        && changeAllowed(level, surface, true)
                        && !AntiRainGeneratorBlockEntity.blocksRain(level, target)
                        && setLevel(level, target, amount + 1);
            }
            if (roll < rainChance * 2.0D) {
                return amount > 0
                        && WaterPhysicsConfig.isRainAbsorbingSurface(selectorState)
                        && changeAllowed(level, surface, false)
                        && setLevel(level, target, amount - 1);
            }
            return false;
        }

        if (precipitation == Biome.Precipitation.SNOW || amount == 0) {
            return false;
        }
        if (brightOutside) {
            return roll < WaterPhysicsConfig.dayEvaporationChance()
                    && changeAllowed(level, surface, false)
                    && setLevel(level, target, amount - 1);
        }
        return roll < rainChance
                && WaterPhysicsConfig.isRainAbsorbingSurface(selectorState)
                && changeAllowed(level, surface, false)
                && setLevel(level, target, amount - 1);
    }

    public static boolean randomTick(ServerLevel level, BlockPos target, RandomSource random) {
        if (level.isRaining()
                && level.getBiome(target).value().getPrecipitationAt(target, level.getSeaLevel())
                == Biome.Precipitation.SNOW) {
            return false;
        }
        return randomTick(level, target, random.nextDouble(), level.isBrightOutside(),
                level.getBrightness(LightLayer.SKY, target));
    }

    static boolean randomTick(ServerLevel level, BlockPos target, double roll,
                              boolean brightOutside, int skyLight) {
        if (!WaterPhysicsConfig.rainfallEnabled()
                || level.isOutsideBuildHeight(target)
                || !level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) {
            return false;
        }

        int amount = FiniteWaterPhysics.getWaterLevel(level, target);
        if (amount <= 0 || amount > MAX_RAIN_LEVEL
                || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target).below().equals(target)) {
            return false;
        }

        BlockState targetState = level.getBlockState(target);
        BlockState selectorState = targetState.is(ModBlocks.FINITE_WATER)
                ? level.getBlockState(target.below())
                : targetState;
        double chance;
        if (brightOutside && skyLight > 0
                && FiniteWaterPhysics.getWaterLevel(level, target.above()) <= 0) {
            chance = WaterPhysicsConfig.dayEvaporationChance();
        } else if (WaterPhysicsConfig.isRainAbsorbingSurface(selectorState)) {
            chance = WaterPhysicsConfig.rainfallChangeChance();
        } else {
            return false;
        }

        return roll < chance
                && changeAllowed(level, target, false)
                && setLevel(level, target, amount - 1);
    }

    private static boolean changeAllowed(ServerLevel level, BlockPos surface, boolean addingRain) {
        if (!WaterPhysicsConfig.rainfallLimitedToPlayerRadius()
                || (!addingRain && WaterPhysicsConfig.rainfallOutsidePlayerRadiusBehavior()
                == WaterPhysicsConfig.OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY)) {
            return true;
        }

        int chunkX = surface.getX() >> 4;
        int chunkZ = surface.getZ() >> 4;
        int radius = WaterPhysicsConfig.rainfallPlayerRadiusChunks();
        for (var player : level.players()) {
            long xDistance = Math.abs((long) chunkX - (player.getBlockX() >> 4));
            long zDistance = Math.abs((long) chunkZ - (player.getBlockZ() >> 4));
            if (Math.max(xDistance, zDistance) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasForeignFluid(BlockState state) {
        return !state.getFluidState().isEmpty() && !ModFluids.isFiniteWater(state.getFluidState().getType());
    }

    private static boolean setLevel(ServerLevel level, BlockPos pos, int amount) {
        FiniteWaterPhysics.setWaterLevel(level, pos, amount);
        return true;
    }
}
