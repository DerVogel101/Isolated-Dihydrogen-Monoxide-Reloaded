package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Opt-in dedicated-server regression: -PdripstoneTest runServer --args="--nogui". */
public final class DripstoneServerTest implements ModInitializer {
    private static final BlockPos ROOT = new BlockPos(0, 200, 0);
    private static final BlockPos SOURCE = ROOT.above(2);
    private static final BlockPos PUDDLE = ROOT.below(3);
    private static final BlockState TIP = Blocks.POINTED_DRIPSTONE.defaultBlockState()
            .setValue(SpeleothemBlock.TIP_DIRECTION, Direction.DOWN);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                run(server.overworld());
                System.out.println("DRIPSTONE_SERVER_TEST_PASS");
            } finally {
                server.halt(false);
            }
        });
    }

    private static void run(ServerLevel level) {
        var config = WaterPhysicsConfig.SERVER.dripstone;
        boolean enabled = config.enabled.get();
        double chance = config.fillChance.get();
        try {
            config.enabled.set(true);
            config.fillChance.set(0.17578125D);
            fixture(level);
            drip(level, 0.17578125F);
            expect(level, PUDDLE, 0, "Vanilla probability boundary");
            drip(level, Math.nextDown(0.17578125F));
            expect(level, PUDDLE, 1, "First successful drip");
            for (int amount = 2; amount <= 8; amount++) {
                drip(level, 0F);
                expect(level, PUDDLE, amount, "Repeated accumulation");
            }
            drip(level, 0F);
            expect(level, PUDDLE, 8, "Full puddle does not overflow");
            expect(level, PUDDLE.above(), 0, "No spill above full puddle");
            expect(level, SOURCE, 8, "Source retained like vanilla");

            // Solid waterlogged hosts must still receive drips above their collision shape.
            for (BlockState host : new BlockState[]{
                    Blocks.STONE_SLAB.defaultBlockState(),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
                    Blocks.STONE_STAIRS.defaultBlockState()}) {
                for (int amount : new int[]{0, 1, 7, 8}) {
                    fixture(level);
                    level.setBlock(PUDDLE, host, Block.UPDATE_CLIENTS);
                    FiniteWaterPhysics.setWaterLevel(level, PUDDLE, amount);
                    drip(level, 0F);
                    expect(level, PUDDLE.above(), 1, "Drip above dry/partially/full waterlogged host");
                    expect(level, PUDDLE, amount, "Drip must not bypass host collision");
                    drip(level, 0F);
                    expect(level, PUDDLE.above(), 2, "Repeated drips above waterlogged host");
                }
            }
            fixture(level);
            level.setBlock(PUDDLE, Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
            FiniteWaterPhysics.setWaterLevel(level, PUDDLE, 1);
            for (int amount = 2; amount <= 8; amount++) {
                drip(level, 0F);
                FiniteWaterPhysics.tick(level, PUDDLE.above());
                expect(level, PUDDLE, amount, "Drips continue after settling into bottom slab");
                expect(level, PUDDLE.above(), 0, "One drip transferred into slab");
            }

            for (int amount = 0; amount < 8; amount++) {
                fixture(level);
                FiniteWaterPhysics.setWaterLevel(level, SOURCE, amount);
                drip(level, 0F);
                expect(level, PUDDLE, 0, "Partial/missing source rejected");
            }
            fixture(level);
            config.enabled.set(false);
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Disabled feature");
            config.enabled.set(true);
            config.fillChance.set(0D);
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Zero chance");
            config.fillChance.set(1D);
            drip(level, 0.99F);
            expect(level, PUDDLE, 1, "Configured high chance");
            config.fillChance.set(0.17578125D);

            for (BlockState fluid : new BlockState[]{Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
                fixture(level);
                level.setBlock(SOURCE, fluid, Block.UPDATE_CLIENTS);
                drip(level, 0F);
                expect(level, PUDDLE, 0, "Vanilla source does not make finite water");
                level.setBlock(PUDDLE, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_CLIENTS);
                drip(level, 0F);
                if (!level.getBlockTicks().hasScheduledTick(PUDDLE, Blocks.CAULDRON)) {
                    throw new AssertionError("Vanilla cauldron transfer must still schedule a fill");
                }
                fixture(level);
                level.setBlock(PUDDLE, fluid, Block.UPDATE_CLIENTS);
                drip(level, 0F);
                if (!level.getBlockState(PUDDLE).equals(fluid)) {
                    throw new AssertionError("Do not overwrite vanilla fluids");
                }
                expect(level, PUDDLE.above(), 0, "No finite water above other fluid");
            }

            fixture(level);
            level.setBlock(PUDDLE, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            if (!level.getBlockState(PUDDLE).is(Blocks.CAULDRON)) {
                throw new AssertionError("Finite water must not convert cauldrons");
            }
            expect(level, PUDDLE.above(), 0, "No puddle over cauldron");

            fixture(level);
            level.setBlock(ROOT.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Solid obstruction directly below tip");

            fixture(level);
            level.setBlock(ROOT, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ROOT, TIP.setValue(SpeleothemBlock.WATERLOGGED, true), Block.UPDATE_CLIENTS);
            if (!level.getBlockState(ROOT).getValue(SpeleothemBlock.WATERLOGGED)) {
                throw new AssertionError("Waterlogged fixture was not placed");
            }
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Waterlogged tip rejected");

            fixture(level);
            level.setBlock(ROOT, TIP.setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP_MERGE), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Merged tip rejected");

            fixture(level);
            level.setBlock(ROOT, TIP.setValue(SpeleothemBlock.TIP_DIRECTION, Direction.UP), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            expect(level, PUDDLE, 0, "Upward dripstone rejected");

            fixture(level);
            level.setBlock(ROOT, TIP.setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.FRUSTUM), Block.UPDATE_CLIENTS);
            level.setBlock(ROOT.below(), TIP, Block.UPDATE_CLIENTS);
            PointedDripstoneBlock.maybeTransferFluid(TIP, level, ROOT.below(), 0F);
            expect(level, PUDDLE, 0, "Only root random tick produces water");
            drip(level, 0F);
            expect(level, PUDDLE, 1, "Multi-block stalactite");

            fixture(level);
            level.setBlock(ROOT.below(4), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ROOT.below(10), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            expect(level, ROOT.below(9), 1, "Last vanilla search position");
            fixture(level);
            level.setBlock(ROOT.below(4), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ROOT.below(11), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            drip(level, 0F);
            expect(level, ROOT.below(10), 0, "Beyond vanilla search limit");
        } finally {
            config.enabled.set(enabled);
            config.fillChance.set(chance);
            clear(level);
        }
    }

    private static void clear(ServerLevel level) {
        var area = new BoundingBox(0, 188, 0, 0, 202, 0);
        level.getBlockTicks().clearArea(area);
        level.getFluidTicks().clearArea(area);
        for (int y = 188; y <= 202; y++) {
            level.setBlock(new BlockPos(0, y, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void fixture(ServerLevel level) {
        clear(level);
        level.setBlock(ROOT.above(), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(ROOT, TIP, Block.UPDATE_CLIENTS);
        level.setBlock(ROOT.below(4), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, SOURCE, 8);
    }

    private static void drip(ServerLevel level, float randomValue) {
        PointedDripstoneBlock.maybeTransferFluid(level.getBlockState(ROOT), level, ROOT, randomValue);
    }

    private static void expect(ServerLevel level, BlockPos pos, int amount, String message) {
        int actual = FiniteWaterPhysics.getWaterLevel(level, pos);
        if (actual != amount) {
            throw new AssertionError(message + ": expected " + amount + ", got " + actual);
        }
    }
}
