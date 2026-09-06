package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.*;
import io.github.SirWashington.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Disposable dedicated-server regression: -PvalveTest runServer --args="--nogui". */
public final class WaterValveServerTest implements ModInitializer {
    private static final BoundingBox AREA = new BoundingBox(0, 170, 0, 15, 295, 15);
    private final List<SquareStructure> valves = new ArrayList<>();
    private int ticks, waiting;
    private boolean running;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            level.setChunkForced(0, 0, true);
            try {
                clear(level);
                crafting(level);
                for (Direction facing : Direction.values()) for (int size = 1; size <= 3; size++) {
                    int index = facing.ordinal();
                    var square = new SquareStructure(new BlockPos(4 + index % 2 * 7, 180 + index / 2 * 35 + size * 8, 8), size, facing);
                    valves.add(square);
                    for (int x = size - 1; x >= 0; x--) for (int y = size - 1; y >= 0; y--) {
                        level.setBlock(square.cell(x, y), ModBlocks.WATER_VALVE.defaultBlockState()
                                .setValue(WaterValveBlock.FACING, facing), Block.UPDATE_ALL);
                    }
                    valve(level, square.origin()).refresh();
                    expect(SquareStructure.find(level, square.origin()).equals(square), "Formation " + square);
                    check(level, square, 140);
                }
                running = true;
            } catch (Throwable failure) { finish(level); throw failure; }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!running) return;
            ServerLevel level = server.overworld();
            try {
                if (!level.isPositionTickingWithEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(valves.getFirst().origin()))) {
                    expect(++waiting < 200, "Test chunk must finish loading"); return;
                }
                ticks++;
                if (ticks == 1) {
                    for (var square : valves) { pumpFlow(level, square, false); power(level, square, true); }
                } else if (ticks == 21) {
                    for (var square : valves) check(level, square, 120);
                } else if (ticks == 140) {
                    for (var square : valves) check(level, square, 1);
                } else if (ticks == 141) {
                    for (var square : valves) {
                        check(level, square, 0); clearance(level, square); pumpFlow(level, square, true);
                        power(level, square, false);
                    }
                } else if (ticks == 281) {
                    for (var square : valves) { check(level, square, 140); power(level, square, true); }
                } else if (ticks == 341) {
                    for (var square : valves) {
                        check(level, square, 80); power(level, square, false);
                        expect(valve(level, square.origin()).progress(0) == 80, "Reversal is continuous");
                        savedState(level, square);
                    }
                } else if (ticks == 421) {
                    for (var square : valves) check(level, square, 140);
                    var square = valves.stream().filter(s -> s.size() == 2).findFirst().orElseThrow();
                    level.removeBlock(square.cell(1, 1), false);
                } else if (ticks == 431) {
                    var square = valves.stream().filter(s -> s.size() == 2).findFirst().orElseThrow();
                    expect(valve(level, square.origin()).size() == 1, "Broken square disassembles on server tick");
                    System.out.println("WATER_VALVE_SERVER_TEST_PASS");
                    finish(level);
                }
            } catch (Throwable failure) { finish(level); throw failure; }
        });
    }

    private static void check(ServerLevel level, SquareStructure square, int progress) {
        int controllers = 0;
        for (int x = 0; x < square.size(); x++) for (int y = 0; y < square.size(); y++) {
            BlockPos pos = square.cell(x, y), outside = pos.relative(square.facing());
            var member = valve(level, pos);
            expect(member.size() == square.size() && member.column() == x && member.row() == y, "Member geometry " + square);
            if (member.isController()) controllers++;
            expect(member.progress(0) == progress, "Exact duration / synchronized member: expected " + progress + " got " + member.progress(0));
            if (progress >= 120 || progress == 0) {
                boolean open = progress == 0;
                expect(FiniteWaterPhysics.canFlowBetween(level, pos, outside) == open, "Outlet face " + square);
                expect(FiniteWaterPhysics.canFlowBetween(level, outside, pos) == open, "Reverse outlet face " + square);
                for (int amount = 1; amount <= 8; amount++) {
                    expect(FiniteWaterPhysics.canFlowBetween(level, pos, outside, amount) == open, "Natural water threshold " + amount);
                }
            }
        }
        expect(controllers == 1, "One controller per square");
    }

    private static void power(ServerLevel level, SquareStructure square, boolean on) {
        BlockPos power = square.cell(square.size() - 1, square.size() - 1).relative(SquareStructure.up(square.facing()));
        level.setBlock(power, (on ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        valve(level, square.origin()).refresh();
    }

    private static void clearance(ServerLevel level, SquareStructure square) {
        Direction u = SquareStructure.right(square.facing()), v = SquareStructure.up(square.facing()), w = square.facing();
        double height = square.size() == 1 || w.getAxis().isVertical() ? .6 : 1.8;
        Vec3 center = Vec3.atCenterOf(square.origin()).add(u.getUnitVec3().scale((square.size() - 1) / 2.0))
                .add(v.getUnitVec3().scale(-.5 + .041 + height / 2));
        double hx = .3 * Math.abs(u.getStepX()) + height / 2 * Math.abs(v.getStepX()) + .75 * Math.abs(w.getStepX());
        double hy = .3 * Math.abs(u.getStepY()) + height / 2 * Math.abs(v.getStepY()) + .75 * Math.abs(w.getStepY());
        double hz = .3 * Math.abs(u.getStepZ()) + height / 2 * Math.abs(v.getStepZ()) + .75 * Math.abs(w.getStepZ());
        expect(level.noCollision(null, new AABB(center.x - hx, center.y - hy, center.z - hz,
                center.x + hx, center.y + hy, center.z + hz)), "Open passage clearance " + square);
    }

    private static void savedState(ServerLevel level, SquareStructure square) {
        BlockPos pos = square.origin();
        for (int amount = 0; amount <= 8; amount++) {
            FiniteWaterPhysics.setWaterLevel(level, pos, amount);
            valve(level, pos).refresh();
            expect(FiniteWaterPhysics.getWaterLevel(level, pos) == amount, "Preserve water amount during animation/state refresh");
        }
        var original = valve(level, pos);
        var restored = BlockEntity.loadStatic(pos, level.getBlockState(pos), original.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        expect(restored instanceof WaterValveBlockEntity, "Saved valve type");
        restored.setLevel(level);
        expect(((WaterValveBlockEntity)restored).progress(0) == 80 && ((WaterValveBlockEntity)restored).size() == square.size(), "Reload keeps travel and size");
        expect(original.getUpdateTag(level.registryAccess()).getIntOr("Progress", -1) == 80, "Client receives reversal position");
        FiniteWaterPhysics.setWaterLevel(level, pos, 0);
    }

    private static void pumpFlow(ServerLevel level, SquareStructure square, boolean open) {
        BlockPos pump = square.origin().relative(square.facing().getOpposite());
        BlockPos stop = pump.relative(square.facing().getOpposite());
        level.setBlock(stop, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(pump, ModBlocks.WATER_PUMP.defaultBlockState().setValue(WaterPumpBlock.FACING, square.facing())
                .setValue(WaterPumpBlock.POWERED, true), Block.UPDATE_CLIENTS);
        boolean previous = WaterPhysicsConfig.pumpStraightOnly();
        try {
            for (boolean straight : new boolean[]{false, true}) {
                WaterPhysicsConfig.SERVER.pump.straightOnly.set(straight);
                for (int x = 0; x < square.size(); x++) for (int y = 0; y < square.size(); y++)
                    FiniteWaterPhysics.setWaterLevel(level, square.cell(x, y), 8);
                FiniteWaterPhysics.setWaterLevel(level, pump, 8);
                expect(PumpFlow.transfer(level, List.of(new PumpStructure(pump, 1, square.facing())), 3, 64) == open,
                        "Pump-driven water respects valve " + square + " open=" + open + " straight=" + straight);
                int total = 0;
                for (BlockPos pos : BlockPos.betweenClosed(square.origin().offset(-3, -3, -3), square.origin().offset(3, 3, 3)))
                    total += Math.max(0, FiniteWaterPhysics.getWaterLevel(level, pos));
                expect(total == (square.size() * square.size() + 1) * 8, "Transfer conserves water exactly");
                for (BlockPos pos : BlockPos.betweenClosed(square.origin().offset(-3, -3, -3), square.origin().offset(3, 3, 3))) {
                    if (FiniteWaterPhysics.getWaterLevel(level, pos) > 0) FiniteWaterPhysics.setWaterLevel(level, pos, 0);
                }
            }
        } finally { WaterPhysicsConfig.SERVER.pump.straightOnly.set(previous); }
        level.removeBlock(pump, false); level.removeBlock(stop, false);
    }

    private static void crafting(ServerLevel level) {
        var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_DOOR), new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.STICKY_PISTON), new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("waxed_lightning_rod"))),
                new ItemStack(Items.STICKY_PISTON), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_DOOR), new ItemStack(Items.IRON_BLOCK)));
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        ItemStack output = recipe.value().assemble(input);
        expect(output.is(ModItems.WATER_VALVE) && output.getCount() == 2, "Supplied recipe yields two valves");
    }
    private static WaterValveBlockEntity valve(ServerLevel level, BlockPos pos) { return (WaterValveBlockEntity)level.getBlockEntity(pos); }
    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ()))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.getBlockTicks().clearArea(AREA); level.getFluidTicks().clearArea(AREA);
    }
    private void finish(ServerLevel level) {
        running = false; clear(level); level.setChunkForced(0, 0, false); level.getServer().halt(false);
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
