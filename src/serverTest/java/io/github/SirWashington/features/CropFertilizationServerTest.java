package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Run with -PcropTest runServer in the isolated build/crop-test-server directory. */
public final class CropFertilizationServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(0, 200, 0);
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
            Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM};

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                run(server.overworld());
                System.out.println("CROP_FERTILIZATION_SERVER_TEST_PASS");
            } finally {
                server.halt(false);
            }
        });
    }

    private static void run(ServerLevel level) {
        var config = WaterPhysicsConfig.SERVER.cropFertilization;
        boolean enabled = config.enabled.get();
        double bonus = config.growthSpeedIncrease.get();
        try {
            config.enabled.set(true);
            config.growthSpeedIncrease.set(0.5D);
            // Artificial local light makes these direct random-tick checks independent of sky time/light propagation.
            level.setBlock(POS.west(), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
            for (Block block : CROPS) {
                for (int water = 0; water <= 8; water++) {
                    fixture(level, block.defaultBlockState(), water);
                    tick(level, roll(block, false, 0D));
                    boolean eligible = water == 1 || water == 2;
                    expect(level, eligible ? 1 : 0, eligible ? water - 1 : water, "Level boundary " + block);
                }
                fixture(level, block.defaultBlockState(), 2);
                tick(level, roll(block, true, 0D));
                expect(level, 1, 2, "Normal growth must not consume water");
                fixture(level, block.defaultBlockState(), 2);
                tick(level, roll(block, false, 0.99D));
                expect(level, 0, 2, "Missed bonus roll");
                fixture(level, block.defaultBlockState(), 2);
                config.enabled.set(false);
                tick(level, roll(block, false, 0D));
                expect(level, 0, 2, "Disabled feature");
                config.enabled.set(true);
                config.growthSpeedIncrease.set(0D);
                tick(level, roll(block, false, 0D));
                expect(level, 0, 2, "Zero bonus");
                config.growthSpeedIncrease.set(0.5D);

                fixture(level, block.defaultBlockState(), 2);
                level.setBlock(POS.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                tick(level, roll(block, false, 0D));
                expect(level, 0, 2, "Only farmland qualifies");
            }
            fixture(level, Blocks.WHEAT.defaultBlockState(), 2);
            tick(level, roll(Blocks.WHEAT, false, 0D));
            tick(level, roll(Blocks.WHEAT, false, 0D));
            tick(level, roll(Blocks.WHEAT, false, 0D));
            expect(level, 2, 0, "Fertilization stops after consuming the last water level");
            fixture(level, Blocks.WHEAT.defaultBlockState(), 2);
            tick(level, roll(Blocks.WHEAT, false, 0.1D));
            expect(level, 0, 2, "Default chance rejects this roll");
            config.growthSpeedIncrease.set(1D);
            tick(level, roll(Blocks.WHEAT, false, 0.1D));
            expect(level, 1, 1, "Configured higher growth chance");
            config.growthSpeedIncrease.set(0.5D);
            for (Block block : CROPS) {
                if (block instanceof CropBlock crop) {
                    fixture(level, crop.getStateForAge(crop.getMaxAge() - 1), 2);
                    tick(level, roll(block, false, 0D));
                    if (!level.getBlockState(POS).is(crop.getStateForAge(crop.getMaxAge()).getBlock())) {
                        throw new AssertionError("Final crop stage/torchflower transformation");
                    }
                    expectWater(level, 1);
                }
                BlockState mature = block instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge())
                        : block.defaultBlockState().setValue(block instanceof StemBlock ? StemBlock.AGE : PitcherCropBlock.AGE,
                                block instanceof StemBlock ? 7 : 4);
                fixture(level, mature, 2);
                tick(level, roll(block, false, 0D));
                if (!level.getBlockState(POS).equals(mature.setValue(FiniteWaterloggedPlants.LEVEL, 2))) {
                    throw new AssertionError("Mature plants/stems must not receive bonus growth or fruit");
                }
            }
            fixture(level, Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 2), 2);
            level.setBlock(POS.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            tick(level, roll(Blocks.PITCHER_CROP, false, 0D));
            expect(level, 2, 2, "Obstructed pitcher");
            level.setBlock(POS.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            tick(level, roll(Blocks.PITCHER_CROP, false, 0D));
            expect(level, 3, 1, "Pitcher grows to two blocks");
            if (!level.getBlockState(POS.above()).is(Blocks.PITCHER_CROP)
                    || level.getBlockState(POS.above()).getValue(PitcherCropBlock.HALF) != DoubleBlockHalf.UPPER
                    || FiniteWaterPhysics.getWaterLevel(level, POS.above()) != 0) {
                throw new AssertionError("Pitcher upper half must grow without duplicating lower water");
            }

            for (Block block : new Block[]{Blocks.WHEAT, Blocks.BEETROOTS}) {
                int normal = sample(level, block, 0D);
                int boosted = sample(level, block, 0.5D);
                double ratio = (double) boosted / normal;
                System.out.println("Crop rate " + block + ": normal=" + normal + ", boosted=" + boosted + ", ratio=" + ratio);
                if (Math.abs(ratio - 1.5D) > 0.06D) {
                    throw new AssertionError("Expected approximately 50 percent faster growth");
                }
            }
        } finally {
            config.enabled.set(enabled);
            config.growthSpeedIncrease.set(bonus);
            for (BlockPos pos : BlockPos.betweenClosed(POS.offset(-1, -1, -1), POS.offset(1, 2, 1))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            var area = new BoundingBox(-1, 199, -1, 1, 202, 1);
            level.getBlockTicks().clearArea(area);
            level.getFluidTicks().clearArea(area);
        }
    }

    private static int sample(ServerLevel level, Block block, double bonus) {
        WaterPhysicsConfig.SERVER.cropFertilization.growthSpeedIncrease.set(bonus);
        RandomSource random = RandomSource.create(73419L);
        int growth = 0;
        fixture(level, block.defaultBlockState(), 2);
        for (int i = 0; i < 60000; i++) {
            level.setBlock(POS, block.defaultBlockState(), Block.UPDATE_CLIENTS);
            FiniteWaterPhysics.setWaterLevel(level, POS, 2);
            tick(level, random);
            if (age(level.getBlockState(POS)) == 1) {
                growth++;
            }
        }
        return growth;
    }

    private static void fixture(ServerLevel level, BlockState crop, int water) {
        level.setBlock(POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(POS.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(POS.below(), Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
        level.setBlock(POS, crop, Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, POS, water);
        if (level.getRawBrightness(POS, 0) < 9) {
            throw new AssertionError("Fixture must be lit for normal growth");
        }
    }

    private static RandomSource roll(Block block, boolean normalGrowth, double bonusRoll) {
        return new LegacyRandomSource(0L) {
            private boolean gate = block instanceof BeetrootBlock || block instanceof TorchflowerCropBlock;
            @Override public int nextInt(int bound) {
                if (gate) {
                    gate = false;
                    return 1;
                }
                return normalGrowth ? 0 : bound - 1;
            }
            @Override public double nextDouble() { return bonusRoll; }
        };
    }

    private static void tick(ServerLevel level, RandomSource random) {
        level.getBlockState(POS).randomTick(level, POS, random);
    }

    private static int age(BlockState state) {
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state)
                : state.getValue(state.getBlock() instanceof StemBlock ? StemBlock.AGE : PitcherCropBlock.AGE);
    }

    private static void expect(ServerLevel level, int age, int water, String message) {
        if (age(level.getBlockState(POS)) != age) {
            throw new AssertionError(message + ": unexpected state " + level.getBlockState(POS));
        }
        expectWater(level, water);
    }

    private static void expectWater(ServerLevel level, int water) {
        if (FiniteWaterPhysics.getWaterLevel(level, POS) != water) {
            throw new AssertionError("Expected " + water + " water, got " + level.getBlockState(POS));
        }
    }
}
