package io.github.SirWashington.features;

import com.mojang.authlib.GameProfile;
import io.github.SirWashington.block.*;
import io.github.SirWashington.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Runs in its own disposable world: -PpumpTest runServer --args="--nogui". */
public final class WaterPumpServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(8, 200, 8);
    private static final BoundingBox AREA = new BoundingBox(2, 194, 2, 14, 206, 14);
    private int ticks;
    private int waitingForChunk;
    private boolean running;
    private WaterPumpContactChecks contacts;
    private PumpFlowChecks flow;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            level.setChunkForced(0, 0, true);
            try {
                placementAndLoot(level);
                crafting(level);
                mutedPumpStaysSeparate(level);
                for (Direction facing : Direction.values()) {
                    for (int size = 1; size <= 3; size++) checkStage(level, facing, size);
                    parallel(level, facing);
                }
                clear(level);
                place(level, POS, Direction.NORTH, 2);
                level.setBlock(POS.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                running = true;
            } catch (Throwable failure) {
                clear(level);
                level.setChunkForced(0, 0, false);
                server.halt(false);
                throw failure;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) return;
            ServerLevel level = server.overworld();
            try {
                if (!level.isPositionTickingWithEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(POS))) {
                    expect(++waitingForChunk < 200, "Forced test chunk must finish loading entities before timed checks");
                    return;
                }
                ticks++;
                if (ticks == 10) {
                    assertStage(level, new PumpStructure(POS, 2, Direction.NORTH), 0, true);
                    level.setBlock(POS.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else if (ticks == 20) {
                    assertStage(level, new PumpStructure(POS, 2, Direction.NORTH), 0, false);
                    level.removeBlock(POS.west().above(), false);
                } else if (ticks == 30) {
                    expect(pump(level, POS).size() == 1, "Tick-driven disassembly after breaking a member");
                    clear(level);
                    contacts = new WaterPumpContactChecks(level);
                } else if (flow != null && flow.tick()) {
                    running = false;
                    flow.close();
                    clear(level);
                    level.setChunkForced(0, 0, false);
                        PumpArchitectureChecks.run(level);
                        System.out.println("WATER_PUMP_SERVER_TEST_PASS");
                    server.halt(false);
                } else if (flow == null && ticks > 30 && contacts.tick()) {
                    contacts.close();
                    clear(level);
                    flow = new PumpFlowChecks(level);
                }
            } catch (Throwable failure) {
                running = false;
                if (contacts != null) contacts.close();
                if (flow != null) flow.close();
                clear(level);
                level.setChunkForced(0, 0, false);
                server.halt(false);
                throw failure;
            }
        });
    }

    private static void placementAndLoot(ServerLevel level) {
        clear(level);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "PumpTest"), ClientInformation.createDefault());
        for (Direction facing : Direction.values()) {
            player.setYRot(facing.getAxis().isHorizontal() ? facing.getOpposite().toYRot() : 0);
            player.setYHeadRot(player.getYRot());
            player.setXRot(facing == Direction.UP ? 90 : facing == Direction.DOWN ? -90 : 0);
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.WATER_PUMP), new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false));
            BlockState placed = ModBlocks.WATER_PUMP.getStateForPlacement(context);
            expect(placed != null && placed.getValue(WaterPumpBlock.FACING) == facing,
                    "Placement follows player gaze in all six directions: " + facing);
        }
        place(level, POS, Direction.NORTH, 1);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        expect(pickaxe.isCorrectToolForDrops(level.getBlockState(POS)), "Pump supports pickaxe mining");
        var drops = Block.getDrops(level.getBlockState(POS), level, POS, pump(level, POS), player, pickaxe);
        expect(drops.size() == 1 && drops.getFirst().is(ModItems.WATER_PUMP) && drops.getFirst().getCount() == 1,
                "One constituent block drops one pump item");
        System.out.println("PUMP_PLACEMENT_AND_LOOT_PASS");
    }

    private static void checkStage(ServerLevel level, Direction facing, int size) {
        clear(level);
        PumpStructure stage = new PumpStructure(POS, size, facing);
        place(level, POS, facing, size);
        refresh(level);
        assertStage(level, stage, 0, false);
        expect(PumpStructure.find(level, POS).equals(stage), "Detection " + facing + " " + size);
        int solidParts = (int)PumpGeometry.parts(size, 0, 0, 0).stream().filter(PumpGeometry.Part::solid).count();
        expect(solidParts == (size == 3 ? 9 : 5), "Four cardinal supports; four extra diagonal supports only for 3x3");
        expect(PumpGeometry.parts(size, 0, 0, 0).stream().filter(p -> !p.solid()).count() == 8,
                "One hub, one cap, six blades per stage");
        for (int n = 1; n <= 3; n++) {
            if (n > 1) place(level, POS.relative(facing, n - 1), facing, size);
            refresh(level);
            for (int z = 0; z < n; z++) {
                assertStage(level, new PumpStructure(POS.relative(facing, z), size, facing),
                        (z > 0 ? 1 : 0) | (z < n - 1 ? 2 : 0), false);
            }
        }
        place(level, POS.relative(facing, 3), facing, size);
        refresh(level);
        for (int z = 0; z < 4; z++) assertStage(level,
                new PumpStructure(POS.relative(facing, z), size, facing), 0, false);
        // Returning to three reconnects, and every stage still owns its own rotor.
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            level.removeBlock(stage.cell(x, y).relative(facing, 3), false);
        }
        refresh(level);
        assertStage(level, stage, 2, false);
        BlockPos power = stage.cell(size - 1, size - 1).relative(PumpStructure.up(facing));
        level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        refresh(level);
        assertStage(level, stage, 2, true);
        assertStage(level, new PumpStructure(POS.relative(facing), size, facing), 3, false);

        // State changes and serialization must retain finite water and stage membership.
        for (int amount = 0; amount <= 8; amount++) {
            FiniteWaterPhysics.setWaterLevel(level, POS, amount);
            pump(level, POS).refresh();
            expect(FiniteWaterPhysics.getWaterLevel(level, POS) == amount, "No water creation/removal on refresh");
        }
        WaterPumpBlockEntity original = pump(level, POS);
        BlockEntity loaded = BlockEntity.loadStatic(POS, level.getBlockState(POS),
                original.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        expect(loaded instanceof WaterPumpBlockEntity saved && saved.size() == size && saved.connections() == 2,
                "Save/reload preserves geometry");
        expect(original.getUpdateTag(level.registryAccess()).getIntOr("Size", 0) == size,
                "Client update carries size");

        // Test passive axial flow separately from the selection box and all animation states.
        clear(level);
        place(level, POS, facing, size);
        refresh(level);
        expect(FiniteWaterPhysics.canFlowBetween(level, POS, POS.relative(facing), 8), "Open outlet face " + facing);
        expect(FiniteWaterPhysics.canFlowBetween(level, POS.relative(facing.getOpposite()), POS, 8), "Open intake face " + facing);
        level.removeBlock(POS, false);
        level.setBlock(POS, ModBlocks.WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, facing)
                .setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
        pump(level, POS).refresh();
        expect(level.getFluidState(POS).is(Fluids.WATER), "Vanilla water remains vanilla");
        if (size > 1) {
            level.removeBlock(stage.cell(size - 1, size - 1), false);
            refresh(level);
            expect(pump(level, POS).size() == size - 1, "Broken square regroups into remaining complete square");
            place(level, POS, facing, size);
            refresh(level);
            assertStage(level, stage, 0, false);
            level.setBlock(stage.cell(size, 0), ModBlocks.WATER_PUMP.defaultBlockState()
                    .setValue(WaterPumpBlock.FACING, facing), Block.UPDATE_ALL);
            refresh(level);
            expect(pump(level, POS).size() == size, "Extra adjacent block does not destroy a complete square");
        }
        clear(level);
        place(level, POS, facing, size);
        place(level, POS.relative(facing), facing.getOpposite(), 1);
        refresh(level);
        expect(pump(level, POS).connections() == 0, "Opposite-facing stage cannot connect");
        System.out.println("PUMP_ORIENTATION_SIZE_PASS " + facing + " " + size);
    }

    private static void crafting(ServerLevel level) {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
                new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BARS), new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.IRON_BARS), new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.withDefaultNamespace("waxed_copper_golem_statue"))), new ItemStack(Items.IRON_BARS),
                new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BARS), new ItemStack(Items.IRON_BLOCK)));
        var recipe = level.getServer().getRecipeManager().getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level).orElseThrow();
        var output = recipe.value().assemble(input);
        expect(output.is(ModItems.WATER_PUMP) && output.getCount() == 2, "Supplied recipe crafts two pumps");
        var mutedInput = net.minecraft.world.item.crafting.CraftingInput.of(1, 3, java.util.List.of(
                new ItemStack(ModItems.INSULATOR_SHARD), new ItemStack(ModItems.WATER_PUMP), new ItemStack(ModItems.INSULATOR_SHARD)));
        var mutedRecipe = level.getServer().getRecipeManager().getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, mutedInput, level).orElseThrow();
        expect(mutedRecipe.value().assemble(mutedInput).is(ModItems.MUTED_WATER_PUMP), "Muted-pump recipe crafts a muted pump");
        for (String id : java.util.List.of("compressed_wool", "double_compressed_wool", "insulator_shard",
                "muted_water_pump", "white_wool_from_double_compressed_wool_stonecutting")) {
            expect(level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("immersivefluids", id))).isPresent(),
                    "Supplied recipe loads: " + id);
        }
        System.out.println("PUMP_CRAFTING_PASS");
    }

    private static void mutedPumpStaysSeparate(ServerLevel level) {
        clear(level);
        level.setBlock(POS, ModBlocks.WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(POS.north(), ModBlocks.MUTED_WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        refresh(level);
        expect(PumpStructure.find(level, POS).size() == 1 && PumpStructure.find(level, POS.north()).size() == 1,
                "Muted and normal pumps do not form a series");
        expect(pump(level, POS).connections() == 0 && pump(level, POS.north()).connections() == 0,
                "Muted and normal pumps have no multiblock connections");
        expect(((WaterPumpBlock) ModBlocks.MUTED_WATER_PUMP).muted(), "Muted pump suppresses its pump sounds");
        System.out.println("MUTED_PUMP_SEPARATION_PASS");
    }

    private static void parallel(ServerLevel level, Direction facing) {
        for (int size = 2; size <= 3; size++) {
            clear(level);
            BlockPos origin = new BlockPos(7, 200, 7);
            Direction right = PumpStructure.right(facing);
            // Place both stages before refresh, as well as connecting a second row in series.
            for (int depth = 0; depth < 2; depth++) for (int side = 0; side < 2; side++) {
                place(level, origin.relative(right, side * size).relative(facing, depth), facing, size);
            }
            refresh(level);
            for (int depth = 0; depth < 2; depth++) for (int side = 0; side < 2; side++) {
                assertStage(level, new PumpStructure(origin.relative(right, side * size).relative(facing, depth), size, facing),
                        depth == 0 ? 2 : 1, false);
            }
        }
        clear(level);
        place(level, POS, facing, 2);
        refresh(level);
        place(level, POS, facing, 3);
        refresh(level);
        assertStage(level, new PumpStructure(POS, 3, facing), 0, false);
        System.out.println("PUMP_PARALLEL_REGROUP_PASS " + facing);
    }

    private static void assertStage(ServerLevel level, PumpStructure stage, int connections, boolean powered) {
        int controllers = 0;
        for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
            WaterPumpBlockEntity pump = pump(level, stage.cell(x, y));
            expect(pump.size() == stage.size() && pump.column() == x && pump.row() == y,
                    "Consistent member geometry: " + stage.cell(x, y) + " size=" + pump.size()
                            + " column=" + pump.column() + " row=" + pump.row() + " expected " + stage);
            expect(pump.connections() == connections, "Connection seams: expected " + connections + ", got " + pump.connections());
            expect(pump.getBlockState().getValue(WaterPumpBlock.POWERED) == powered, "Stage-wide redstone state");
            if (pump.isController()) controllers++;
            expect(pump.collisionShape().isEmpty() == (stage.size() == 3 && x == 1 && y == 1),
                    "Only the casing collides; the 3x3 center is fully passable");
        }
        expect(controllers == 1, "Exactly one rendered rotor per stage");
    }

    private static void place(ServerLevel level, BlockPos origin, Direction facing, int size) {
        PumpStructure stage = new PumpStructure(origin, size, facing);
        // Reverse order ensures the controller does not depend on placement order.
        for (int x = size - 1; x >= 0; x--) for (int y = size - 1; y >= 0; y--) {
            level.setBlock(stage.cell(x, y), ModBlocks.WATER_PUMP.defaultBlockState()
                    .setValue(WaterPumpBlock.FACING, facing), Block.UPDATE_ALL);
        }
    }

    private static WaterPumpBlockEntity pump(ServerLevel level, BlockPos pos) {
        return (WaterPumpBlockEntity)level.getBlockEntity(pos);
    }

    private static void refresh(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ())) {
            if (level.getBlockEntity(pos) instanceof WaterPumpBlockEntity pump) pump.refresh();
        }
    }

    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getBlockTicks().clearArea(AREA);
        level.getFluidTicks().clearArea(AREA);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
