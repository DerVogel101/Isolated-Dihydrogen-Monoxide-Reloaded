package io.github.SirWashington.features;

import com.mojang.authlib.GameProfile;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.block.RainSensorBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Run with -PsensorTest runServer in the disposable build/sensor-test-server directory. */
public final class RainSensorServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(8, 200, 8);
    private static final BoundingBox AREA = new BoundingBox(6, 198, 6, 10, 204, 10);
    private int ticks;
    private boolean running;
    private float previousRain;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            previousRain = level.getRainLevel(1);
            try {
                level.setChunkForced(0, 0, true);
                sensorChecks(level);
                sensorFlowCheck(level);
                grateChecks(level);
                clear(level);
                biome(level, "plains");
                level.setRainLevel(0);
                level.setBlock(POS, ModBlocks.RAIN_SENSOR.defaultBlockState(), Block.UPDATE_ALL);
                running = true;
            } catch (Throwable failure) {
                cleanup(level);
                server.halt(false);
                throw failure;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) {
                return;
            }
            ServerLevel level = server.overworld();
            try {
                ticks++;
                if (ticks == 25) {
                    expectSignal(level, 0, "Scheduled dry check");
                    level.setRainLevel(1);
                } else if (ticks == 50) {
                    expectSignal(level, 15, "Scheduled rain check without a connected player");
                    level.setBlock(POS.above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                } else if (ticks == 75) {
                    expectSignal(level, 0, "Scheduled roof check");
                    running = false;
                    cleanup(level);
                    System.out.println("RAIN_SENSOR_SERVER_TEST_PASS");
                    server.halt(false);
                }
            } catch (Throwable failure) {
                running = false;
                cleanup(level);
                server.halt(false);
                throw failure;
            }
        });
    }

    private static void sensorChecks(ServerLevel level) {
        clear(level);
        biome(level, "plains");
        level.setRainLevel(0);
        level.setBlock(POS.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS.east().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS, ModBlocks.RAIN_SENSOR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS.east(), Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "SensorTest"), ClientInformation.createDefault());
        for (int amount = 0; amount <= 8; amount++) {
            FiniteWaterPhysics.setWaterLevel(level, POS, amount);
            tickSensor(level);
            expectSignal(level, amount > 2 ? 15 : 0, "Normal water level " + amount);
            toggle(level, player);
            expectSignal(level, amount > 2 ? 0 : 15, "Inverted water level " + amount);
            expectLevel(level, POS, amount, "Toggle preserves water");
            toggle(level, player);
            expectSignal(level, amount > 2 ? 15 : 0, "Second toggle restores output");
        }
        FiniteWaterPhysics.setWaterLevel(level, POS, 0);
        level.setRainLevel(1);
        tickSensor(level);
        expectSignal(level, 15, "Exposed rain");
        toggle(level, player);
        expectSignal(level, 0, "Inverted rain");
        toggle(level, player);
        for (Block roof : new Block[]{Blocks.STONE, Blocks.GLASS}) {
            level.setBlock(POS.above(2), roof.defaultBlockState(), Block.UPDATE_ALL);
            tickSensor(level);
            expectSignal(level, 0, "Rain blocked by " + roof);
            FiniteWaterPhysics.setWaterLevel(level, POS, 3);
            tickSensor(level);
            expectSignal(level, 15, "Water still detected beneath roof");
            FiniteWaterPhysics.setWaterLevel(level, POS, 0);
        }
        level.setBlock(POS.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (String biome : new String[]{"desert", "snowy_plains"}) {
            biome(level, biome);
            tickSensor(level);
            expectSignal(level, 0, "No rain detection in " + biome);
        }
        biome(level, "plains");
        level.setRainLevel(0);
        level.setBlock(POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS, ModBlocks.RAIN_SENSOR.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
        tickSensor(level);
        expectSignal(level, 15, "Vanilla waterlogged sensor");
        toggle(level, player);
        expectSignal(level, 0, "Inverted vanilla waterlogged sensor");
        if (!level.getFluidState(POS).is(Fluids.WATER)) {
            throw new AssertionError("Sensor toggle converted vanilla water");
        }
        System.out.println("SENSOR_BOUNDARIES_RAIN_INVERSION_REDSTONE_PASS");
    }

    private static void sensorFlowCheck(ServerLevel level) {
        clear(level);
        for (BlockPos p : BlockPos.betweenClosed(6, 198, 6, 10, 202, 10)) {
            level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(POS, ModBlocks.RAIN_SENSOR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        FiniteWaterPhysics.setWaterLevel(level, POS.west(), 8);
        for (int step = 0; step < 8; step++) {
            FiniteWaterPhysics.tick(level, POS.west());
        }
        expectLevel(level, POS, 4, "Water flows into sensor");
        expectLevel(level, POS.west(), 4, "Sensor inflow conserves water");
        tickSensor(level);
        expectSignal(level, 15, "Flow activates sensor");
        level.setBlock(POS.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        FiniteWaterPhysics.tick(level, POS);
        expectLevel(level, POS, 0, "Sensor drains through bottom");
        expectLevel(level, POS.below(), 4, "Sensor drainage conserves water");
        tickSensor(level);
        expectSignal(level, 0, "Drainage deactivates sensor");
        System.out.println("SENSOR_NATURAL_FLOW_PASS");
    }

    private static void grateChecks(ServerLevel level) {
        for (Block grate : Blocks.COPPER_GRATE.asList()) {
            for (Direction direction : Direction.values()) {
                BlockPos target = POS.relative(direction);
                clear(level);
                level.setBlock(POS, grate.defaultBlockState(), Block.UPDATE_ALL);
                if (!level.getBlockState(POS).isCollisionShapeFullBlock(level, POS)
                        || !FiniteWaterPhysics.canFlowBetween(level, POS, target, 1)
                        || !FiniteWaterPhysics.canFlowBetween(level, target, POS, 1)
                        || !FiniteWaterPhysics.canFlowBetween(level, POS, target)) {
                    throw new AssertionError("Grate must retain collision and admit flow both ways: " + grate + direction);
                }
                level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                if (FiniteWaterPhysics.canFlowBetween(level, POS, target, 8)
                        || FiniteWaterPhysics.canFlowBetween(level, target, POS, 8)) {
                    throw new AssertionError("Grate must not bypass solid neighbor: " + direction);
                }
                if (direction == Direction.UP) {
                    continue;
                }
                for (boolean entering : new boolean[]{true, false}) {
                    clear(level);
                    for (BlockPos p : BlockPos.betweenClosed(6, 198, 6, 10, 202, 10)) {
                        level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(POS, entering ? Blocks.AIR.defaultBlockState() : grate.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(target, entering ? grate.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    FiniteWaterPhysics.setWaterLevel(level, POS, 8);
                    for (int step = 0; step < 8; step++) {
                        FiniteWaterPhysics.tick(level, POS);
                    }
                    int moved = direction == Direction.DOWN ? 8 : 4;
                    expectLevel(level, POS, 8 - moved, "Grate source conservation");
                    expectLevel(level, target, moved, "Grate destination conservation");
                    if (!level.getBlockState(entering ? target : POS).is(grate)) {
                        throw new AssertionError("Flow replaced the grate");
                    }
                }
            }
        }
        System.out.println("ALL_EIGHT_COPPER_GRATES_FLOW_CONSERVATION_PASS");
    }

    private static void biome(ServerLevel level, String biome) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "fillbiome 0 192 0 15 207 15 minecraft:" + biome);
    }

    private static void toggle(ServerLevel level, ServerPlayer player) {
        // Also covers a sensor saved by the visual-only prototype, which had no scheduled tick.
        level.getBlockTicks().clearArea(AREA);
        level.getBlockState(POS).useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false));
        if (!level.getBlockTicks().hasScheduledTick(POS, ModBlocks.RAIN_SENSOR)) {
            throw new AssertionError("Toggling must start periodic detection for existing prototype sensors");
        }
    }

    private static void tickSensor(ServerLevel level) {
        level.getBlockState(POS).tick(level, POS, level.getRandom());
    }

    private static void expectSignal(ServerLevel level, int expected, String message) {
        for (Direction direction : Direction.values()) {
            int actual = level.getBlockState(POS).getSignal(level, POS, direction);
            if (actual != expected) {
                throw new AssertionError(message + ": expected " + expected + ", got " + actual);
            }
        }
        BlockState wire = level.getBlockState(POS.east());
        if (wire.is(Blocks.REDSTONE_WIRE) && wire.getValue(BlockStateProperties.POWER) != expected) {
            throw new AssertionError(message + ": adjacent redstone wire did not update");
        }
    }

    private static void expectLevel(ServerLevel level, BlockPos pos, int expected, String message) {
        int actual = FiniteWaterPhysics.getWaterLevel(level, pos);
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void clear(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(6, 198, 6, 10, 204, 10)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getBlockTicks().clearArea(AREA);
        level.getFluidTicks().clearArea(AREA);
    }

    private void cleanup(ServerLevel level) {
        clear(level);
        level.setRainLevel(previousRain);
        level.setChunkForced(0, 0, false);
    }
}
