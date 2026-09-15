package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import jdk.jfr.Recording;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

/** Dedicated, disposable server fixtures; reflection keeps production helpers private. */
public final class FluidOptimizationServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(8, 200, 8);
    private static final Method CONTACT = method("disappearsOnVanillaFluidContact");
    private static final Method PUDDLE = method("movePuddleTowardDrop");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var level = server.overworld();
                contacts(level);
                puddles(level);
                transfers(level);
                benchmark(level);
                System.out.println("FLUID_OPTIMIZATION_SERVER_TEST_PASS");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                server.halt(false);
            }
        });
    }

    private static Method method(String name) {
        return Arrays.stream(FiniteWaterPhysics.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(name)).peek(m -> m.setAccessible(true)).findFirst().orElseThrow();
    }

    private static boolean contact(ServerLevel level) throws Exception {
        return (boolean) (CONTACT.getParameterCount() == 2 ? CONTACT.invoke(null, level, POS)
            : CONTACT.invoke(null, level, POS, FiniteWaterPhysics.getWaterLevel(level, POS)));
    }

    private static boolean puddle(ServerLevel level) throws Exception {
        return (boolean) PUDDLE.invoke(null, level, POS);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.hasProperty(FiniteWaterloggedPlants.LEVEL)
            || FiniteWaterloggedPlants.getLevel(level.getBlockState(pos)) > 0) {
            FiniteWaterPhysics.setWaterloggedBlock(level, pos, state);
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void water(ServerLevel level, BlockPos pos, int amount) {
        put(level, pos, amount == 0 ? Blocks.AIR.defaultBlockState() : FiniteWaterloggedPlants.fluidState(amount).createLegacyBlock());
    }

    private static void box(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(POS.offset(-5, -2, -5), POS.offset(5, 2, 5))) {
            put(level, p, p.getY() == POS.getY() && Math.abs(p.getX() - POS.getX()) < 5
                && Math.abs(p.getZ() - POS.getZ()) < 5 ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
    }

    private static void contacts(ServerLevel level) throws Exception {
        for (var fluid : new net.minecraft.world.level.material.Fluid[]{Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA, Fluids.FLOWING_LAVA}) {
            for (var direction : Direction.values()) {
                box(level);
                water(level, POS, 1);
                var neighbor = direction == Direction.UP ? POS.above() : POS.relative(direction);
                var state = fluid.defaultFluidState().createLegacyBlock();
                put(level, neighbor, state);
                expect(contact(level), "Ordinary contact " + fluid + " " + direction);
                expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 0, "Contact removes finite water");
                expect(level.getBlockState(neighbor).equals(state), "Contact preserves vanilla neighbor");
            }
        }
        box(level);
        water(level, POS, 1);
        put(level, POS.north(), Blocks.WATER.defaultBlockState());
        put(level, POS.east(), Blocks.LAVA.defaultBlockState());
        expect(contact(level), "Simultaneous water/lava contact");
        expect(level.getBlockState(POS.north()).is(Blocks.WATER) && level.getBlockState(POS.east()).is(Blocks.LAVA), "Vanilla isolation");
        for (var slabType : new SlabType[]{SlabType.BOTTOM, SlabType.TOP}) {
            box(level);
            var slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, slabType);
            put(level, POS, FiniteWaterloggedPlants.withLevel(slab, 1));
            put(level, POS.below(), Blocks.WATER.defaultBlockState());
            expect(contact(level) == (slabType == SlabType.TOP), "Waterlogged source exit " + slabType);
            box(level);
            water(level, POS, 1);
            put(level, POS.above(), slab.setValue(BlockStateProperties.WATERLOGGED, true));
            expect(contact(level) == (slabType == SlabType.TOP), "Waterlogged destination entrance " + slabType);
        }
        box(level);
        water(level, POS, 1);
        expect(!contact(level), "No vanilla contact");
        System.out.println("FLUID_CONTACT_REGRESSION_PASS");
    }

    private static void corridor(ServerLevel level, int length, boolean extended, boolean drop) {
        for (BlockPos p : BlockPos.betweenClosed(POS.offset(-1, -2, -1), POS.offset(length + 1, 1, 1))) {
            put(level, p, Blocks.STONE.defaultBlockState());
        }
        for (int x = 0; x <= length; x++) {
            put(level, POS.east(x), extended ? Blocks.OAK_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        if (drop) {
            put(level, POS.east(length), extended ? Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP) : Blocks.AIR.defaultBlockState());
            put(level, POS.east(length).below(), Blocks.AIR.defaultBlockState());
        }
        if (extended) put(level, POS, FiniteWaterloggedPlants.withLevel(level.getBlockState(POS), 1));
        else water(level, POS, 1);
    }

    private static void puddles(ServerLevel level) throws Exception {
        corridor(level, 4, false, true);
        expect(puddle(level), "Reachable drop");
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.east()) == 1, "First step toward drop");
        corridor(level, 4, false, false);
        expect(!puddle(level), "No drop");
        corridor(level, WaterPhysicsConfig.puddleSearchRadius() + 1, false, true);
        expect(!puddle(level), "Normal radius boundary");
        corridor(level, 8, true, true);
        expect(puddle(level), "Extended tagged route beyond normal radius");
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.east()) == 1, "Extended first step");
        corridor(level, WaterPhysicsConfig.extendedDrainMaxPathLength(), true, true);
        expect(puddle(level), "Exact extended path limit");
        corridor(level, WaterPhysicsConfig.extendedDrainMaxPathLength() + 1, true, true);
        expect(!puddle(level), "Beyond extended path limit");
        box(level);
        water(level, POS, 1);
        put(level, POS.east(3).below(), Blocks.AIR.defaultBlockState());
        put(level, POS.west(3).below(), Blocks.AIR.defaultBlockState());
        expect(puddle(level), "Branching outlets");
        expect(total(level, 5) == 1, "Branching conservation");
        int rotation = Math.floorMod((int) (level.getGameTime() + POS.asLong()), 4);
        expect(FiniteWaterPhysics.getWaterLevel(level, rotation < 2 ? POS.east() : POS.west()) == 1,
            "Branching preserves direction priority");
        visitBoundary(level);
        // A one-cell source at a distant chunk edge must not load the missing neighbor.
        BlockPos edge = new BlockPos(320015, 200, 320008);
        level.getChunk(edge);
        expect(!level.hasChunk((edge.getX() + 1) >> 4, edge.getZ() >> 4), "Unloaded fixture neighbor");
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(edge, FiniteWaterloggedPlants.fluidState(1).createLegacyBlock(), flags);
        level.setBlock(edge.below(), Blocks.STONE.defaultBlockState(), flags);
        for (var d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST}) level.setBlock(edge.relative(d), Blocks.STONE.defaultBlockState(), flags);
        expect(!level.hasChunk((edge.getX() + 1) >> 4, edge.getZ() >> 4), "Fixture must leave neighbor unloaded");
        PUDDLE.invoke(null, level, edge);
        expect(!level.hasChunk((edge.getX() + 1) >> 4, edge.getZ() >> 4), "Search must not load neighbor");
        System.out.println("FLUID_PUDDLE_REGRESSION_PASS");
    }

    private static int total(ServerLevel level, int radius) {
        int total = 0;
        for (BlockPos p : BlockPos.betweenClosed(POS.offset(-radius, -1, -radius), POS.offset(radius, 0, radius))) {
            total += Math.max(0, FiniteWaterPhysics.getWaterLevel(level, p));
        }
        return total;
    }

    private static void visitBoundary(ServerLevel level) throws Exception {
        int budget = WaterPhysicsConfig.extendedDrainMaxVisitedCells();
        expect(budget == 256, "Visit-boundary fixture uses default 256-cell budget");
        var order = new java.util.LinkedHashSet<BlockPos>();
        var queue = new java.util.ArrayDeque<BlockPos>();
        order.add(POS);
        queue.add(POS);
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int rotation = Math.floorMod((int) (level.getGameTime() + POS.asLong()), 4);
        while (order.size() <= budget) {
            var current = queue.remove();
            for (int i = 0; i < 4 && order.size() <= budget; i++) {
                var next = current.relative(directions[(rotation + i) % 4]);
                if (Math.abs(next.getX() - POS.getX()) <= 12 && Math.abs(next.getZ() - POS.getZ()) <= 12 && order.add(next)) queue.add(next);
            }
        }
        var cells = order.toArray(BlockPos[]::new);
        for (int index : new int[]{budget - 1, budget}) {
            for (BlockPos p : BlockPos.betweenClosed(POS.offset(-13, -1, -13), POS.offset(13, 1, 13))) {
                put(level, p, p.getY() == POS.getY() && Math.abs(p.getX() - POS.getX()) <= 12 && Math.abs(p.getZ() - POS.getZ()) <= 12
                    ? Blocks.OAK_SLAB.defaultBlockState() : Blocks.STONE.defaultBlockState());
            }
            put(level, cells[index], Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
            put(level, cells[index].below(), Blocks.AIR.defaultBlockState());
            put(level, POS, FiniteWaterloggedPlants.withLevel(Blocks.OAK_SLAB.defaultBlockState(), 1));
            expect(puddle(level) == (index == budget - 1), "Exact visited-cell boundary " + index);
            expect(total(level, 13) == 1, "Visit-boundary conservation");
        }
    }

    private static void transfers(ServerLevel level) {
        box(level);
        water(level, POS, 8);
        put(level, POS.below(), Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 6));
        FiniteWaterPhysics.tick(level, POS);
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.below()) == 2, "Downflow respects occupied layers");
        expect(total(level, 5) == 8, "Downflow conservation");
        for (int tick = 0; tick < 12; tick++) {
            for (BlockPos p : BlockPos.betweenClosed(POS.offset(-4, 0, -4), POS.offset(4, 0, 4))) FiniteWaterPhysics.tick(level, p.immutable());
            expect(total(level, 5) == 8, "Multi-tick equalization conservation " + tick);
        }
        System.out.println("FLUID_TRANSFER_REGRESSION_PASS");
    }

    private static void benchmark(ServerLevel level) throws Exception {
        try (var recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            recording.start();
            for (String fixture : new String[]{"puddle", "drainage", "contact", "waterlogged"}) {
                long[] samples = new long[3];
                for (int run = -2; run < 3; run++) {
                    box(level);
                    if (fixture.equals("drainage")) corridor(level, 4, false, true);
                    if (fixture.equals("contact")) put(level, POS.east(), Blocks.WATER.defaultBlockState());
                    if (fixture.equals("waterlogged")) {
                        for (BlockPos p : BlockPos.betweenClosed(POS.offset(-4, 0, -4), POS.offset(4, 0, 4))) put(level, p, Blocks.OAK_SLAB.defaultBlockState());
                    }
                    long nanos = 0;
                    for (int i = 0; i < 1000; i++) {
                        if (fixture.equals("waterlogged")) put(level, POS, FiniteWaterloggedPlants.withLevel(Blocks.OAK_SLAB.defaultBlockState(), 1));
                        else water(level, POS, 1);
                        if (fixture.equals("drainage")) water(level, POS.east(), 0);
                        long start = System.nanoTime();
                        FiniteWaterPhysics.tick(level, POS);
                        nanos += System.nanoTime() - start;
                    }
                    if (run >= 0) samples[run] = nanos;
                }
                System.out.println("FLUID_BENCH " + fixture + " samples_ns=" + Arrays.toString(samples));
                Arrays.sort(samples);
                System.out.println("FLUID_BENCH " + fixture + " median_ns_per_tick=" + samples[1] / 1000);
            }
            recording.stop();
            Path profile = Path.of("fluid-optimization.jfr");
            recording.dump(profile);
            var counts = new java.util.TreeMap<String, Integer>();
            try (var events = new jdk.jfr.consumer.RecordingFile(profile)) {
                while (events.hasMoreEvents()) {
                    var event = events.readEvent();
                    if (!event.getEventType().getName().equals("jdk.ExecutionSample") || event.getStackTrace() == null) continue;
                    var methods = new java.util.HashSet<String>();
                    for (var frame : event.getStackTrace().getFrames()) {
                        if (frame.getMethod().getType().getName().equals(FiniteWaterPhysics.class.getName())) methods.add(frame.getMethod().getName());
                    }
                    if (methods.contains("tick")) for (var name : methods) counts.merge(name, 1, Integer::sum);
                }
            }
            System.out.println("FLUID_PROFILE inclusive_tick_samples=" + counts);
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
