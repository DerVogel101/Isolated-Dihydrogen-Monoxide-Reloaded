package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Run with -PfireTest runServer --args="--nogui" in build/fire-test-server. */
public final class FiniteWaterFireServerTest implements ModInitializer {
    private static final BlockPos WATER_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FIRE_POS = WATER_POS.east();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                run(server.overworld());
                System.out.println("FINITE_WATER_FIRE_SERVER_TEST_PASS");
            } finally {
                clear(server.overworld());
                server.halt(false);
            }
        });
    }

    private static void run(ServerLevel level) {
        int threshold = WaterPhysicsConfig.extinguishingMinimumLevel();
        verify(level, Blocks.FIRE, Blocks.STONE, threshold - 1, false);
        verify(level, Blocks.FIRE, Blocks.STONE, threshold, true);
        verify(level, Blocks.SOUL_FIRE, Blocks.SOUL_SOIL, threshold, true);
    }

    private static void verify(ServerLevel level, Block fire, Block support, int amount, boolean extinguished) {
        clear(level);
        level.setBlock(FIRE_POS.below(), support.defaultBlockState(), Block.UPDATE_ALL);
        FiniteWaterPhysics.setWaterLevel(level, WATER_POS, amount);
        level.setBlock(FIRE_POS, fire.defaultBlockState(), Block.UPDATE_ALL);
        FiniteWaterPhysics.tick(level, WATER_POS);
        if (level.getBlockState(FIRE_POS).isAir() != extinguished) {
            throw new AssertionError(fire + " at water level " + amount + " extinguished=" + extinguished);
        }
    }

    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(WATER_POS.offset(-2, -1, -2), WATER_POS.offset(3, 1, 2))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
