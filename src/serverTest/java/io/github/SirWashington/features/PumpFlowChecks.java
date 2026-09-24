package io.github.SirWashington.features;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Actual finite-water states, check valves, pressure limits and server-ticked suction. */
final class PumpFlowChecks implements AutoCloseable {
    private static final BlockPos ORIGIN = new BlockPos(8, 215, 8);
    private static final BlockPos BLOCKED_ORIGIN = new BlockPos(0, 230, 0);
    private static final BoundingBox AREA = new BoundingBox(1, 207, 1, 14, 245, 14);
    private final ServerLevel level;
    private Mob mob;
    private Mob blockedMob;
    private int ticks, cycles;
    private int steadyCurrentTicks;
    private boolean steadyCurrentStarted;

    PumpFlowChecks(ServerLevel level) {
        this.level = level;
        clearBlockedFixture();
        try {
            expect(WaterPhysicsConfig.pumpTickInterval() == 20 && WaterPhysicsConfig.pumpWaterUnitsPerCycle() == 8,
                    "Default throughput is one block per 20 ticks");
            expect(FiniteWaterPhysics.currentDurationTicks(true) == 20
                            && FiniteWaterPhysics.currentDurationTicks(false) == WaterPhysicsConfig.currentDurationTicks(),
                    "Pump currents last one pump interval without changing ordinary current duration");
            int interval = WaterPhysicsConfig.pumpTickInterval();
            try {
                WaterPhysicsConfig.SERVER.pump.tickInterval.set(37);
                expect(FiniteWaterPhysics.currentDurationTicks(true) == 37,
                        "Pump current duration follows configured pump interval");
            } finally {
                WaterPhysicsConfig.SERVER.pump.tickInterval.set(interval);
            }
            for (Direction facing : Direction.values()) for (int size = 1; size <= 3; size++) {
                basic(facing, size);
            }
            System.out.println("PUMP_SOUND_STATE_PASS");
            limitsAndSeries();
            partialPrimingMustDischarge();
            seriesWithNaturalFlow();
            downstreamAndBentOutlet();
            saturatedBentOutlet();
            fullWidthCurrent();
            guideChecks();
            clear();
            pipe(Direction.UP, 1, 3, 5);
            level.setBlock(ORIGIN.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            expect(!FiniteWaterPhysics.canFlowBetween(level, ORIGIN, ORIGIN.east())
                    && !FiniteWaterPhysics.canFlowBetween(level, ORIGIN.east(), ORIGIN),
                    "Pump housing prevents side leakage in both directions");
            System.out.println("PUMP_SIDE_SEAL_PASS");
            level.setBlock(ORIGIN.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
            mob = EntityTypes.RABBIT.create(level, EntitySpawnReason.COMMAND);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            mob.setHealth(1000);
            // NoAI also disables travel in 26.2. Keep travel active but remove self-propulsion.
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            mob.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0);
            mob.setPos(8.85, 214.2, 8.5);
            level.addFreshEntity(mob);
            blockedCurrentFixture();
        } catch (RuntimeException | Error failure) { close(); throw failure; }
    }

    private void basic(Direction facing, int size) {
        clear();
        var stages = pipe(facing, size, 1, 2);
        BlockPos source = ORIGIN.relative(facing.getOpposite()), outlet = ORIGIN.relative(facing);
        FiniteWaterPhysics.setWaterLevel(level, source, 8);
        expect(!PumpFlow.transfer(level, stages, 1, 64), "Depth limit rejects too-short path");
        expect(FiniteWaterPhysics.getWaterLevel(level, source) == 8, "Failed plan conserves intake");
        expect(PumpFlow.transfer(level, stages, 2, 64), "Dry pump draws from behind: " + facing + " size " + size);
        expect(FiniteWaterPhysics.getWaterLevel(level, source) == 0 && totalBeyond(facing, 1) == 8,
                "Exactly eight units cross the pump");
        expect(!FiniteWaterPhysics.canFlowBetween(level, outlet, ORIGIN), "Pressure cannot enter outlet backwards");
        expect(!FiniteWaterPhysics.canFlowBetween(level, ORIGIN, source, 8), "Natural flow cannot leave intake backwards");
        FiniteWaterPhysics.tick(level, outlet);
        expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN) == 0, "Natural flow honors powered valve");
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN, 5);
        FiniteWaterPhysics.setWaterLevel(level, source, 7);
        // Reopen discharge capacity, then verify a partial source is retained exactly.
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            FiniteWaterPhysics.setWaterLevel(level, stages.getFirst().cell(x, y).relative(facing), 0);
            FiniteWaterPhysics.setWaterLevel(level, stages.getFirst().cell(x, y).relative(facing, 2), 0);
        }
        expect(PumpFlow.transfer(level, stages, 3, 64), "Pump drains its own water and intake together: " + facing + " size " + size);
        int retained = size == 1 ? 4 : 0;
        expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN) == retained
                && FiniteWaterPhysics.getWaterLevel(level, source) == 0, "Intake-first transfer preserves water inside the pump");
        power(stages.getFirst(), false);
        expect(FiniteWaterPhysics.canFlowBetween(level, outlet, ORIGIN), "Off pump restores backward passage");
        int before = total();
        PumpFlow.tick(level, ORIGIN);
        expect(total() == before && FiniteWaterPhysics.getWaterLevel(level, ORIGIN) == retained, "Off pump does not transfer");
        clear();
        stages = pipe(facing, size, 1, 0);
        FiniteWaterPhysics.setWaterLevel(level, source, 8);
        expect(!PumpFlow.transfer(level, stages, 8, 64) && FiniteWaterPhysics.getWaterLevel(level, source) == 8,
                "Closed discharge leaves source intact");
        level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        expect(!PumpFlow.transfer(level, stages, 8, 64) && level.getBlockState(source).is(Blocks.WATER),
                "Vanilla source remains untouched");
        clear();
        stages = pipe(facing, size, 1, 1);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            FiniteWaterPhysics.setWaterLevel(level, stages.getFirst().cell(x, y).relative(facing.getOpposite()), 8);
        }
        expect(PumpFlow.transfer(level, stages, 8, 64), "Full cross-section transfer succeeds");
        expect(totalBeyond(facing, 1) == 8 * size * size && total() == 8 * size * size,
                "One full block per constituent block: " + facing + " size " + size);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            BlockPos cell = stages.getFirst().cell(x, y);
            FiniteWaterPhysics.setWaterLevel(level, cell.relative(facing), 0);
            FiniteWaterPhysics.setWaterLevel(level, cell.relative(facing.getOpposite()), 8);
        }
        int configured = WaterPhysicsConfig.pumpWaterUnitsPerCycle();
        try {
            WaterPhysicsConfig.SERVER.pump.waterUnitsPerCycle.set(3);
            expect(PumpFlow.transfer(level, stages, 8, 64), "Configured fractional throughput succeeds");
            expect(totalBeyond(facing, 1) == 3 * size * size && total() == 8 * size * size,
                    "Three configured water levels per constituent block, conserving remainders");
        } finally {
            WaterPhysicsConfig.SERVER.pump.waterUnitsPerCycle.set(configured);
        }
        clear();
        stages = pipe(facing, size, 1, 1);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            level.setBlock(stages.getFirst().cell(x, y).relative(facing.getOpposite(), 2),
                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            FiniteWaterPhysics.setWaterLevel(level,
                    stages.getFirst().cell(x, y).relative(facing.getOpposite(), 3), 8);
        }
        expect(PumpFlow.hasWater(level, stages.getFirst()),
                "Remote intake selects wet running sound");
        expect(PumpFlow.transfer(level, stages, 4, 64),
                "Pump reaches water three unobstructed intake cells back: " + facing
                + " size " + size);
        expect(totalBeyond(facing, 1) == 8 * size * size && total() == 8 * size * size,
                "Remote intake supplies the full cross-section budget");
        clear();
        stages = pipe(facing, size, 1, 1);
        BlockPos remote = stages.getFirst().cell(0, 0).relative(facing.getOpposite(), 3);
        FiniteWaterPhysics.setWaterLevel(level, remote, 8);
        level.setBlock(remote.relative(facing), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        expect(!PumpFlow.hasWater(level, stages.getFirst()),
                "Obstructed remote water selects dry running sound");
        expect(!PumpFlow.transfer(level, stages, 8, 64)
                        && FiniteWaterPhysics.getWaterLevel(level, remote) == 8,
                "Solid obstruction blocks remote intake");
        clear();
        stages = pipe(facing, size, 1, 1);
        remote = stages.getFirst().cell(0, 0).relative(facing.getOpposite(), 4);
        level.setBlock(stages.getFirst().cell(0, 0).relative(facing.getOpposite(), 2),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(stages.getFirst().cell(0, 0).relative(facing.getOpposite(), 3),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, remote, 8);
        expect(!PumpFlow.hasWater(level, stages.getFirst()),
                "Water outside intake reach selects dry running sound");
        expect(!PumpFlow.transfer(level, stages, 8, 64)
                        && FiniteWaterPhysics.getWaterLevel(level, remote) == 8,
                "Pump does not draw from four cells back");
    }

    private void limitsAndSeries() {
        for (int count = 1; count <= 3; count++) {
            clear();
            var stages = pipe(Direction.UP, 1, count, 19);
            FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
            for (int i = 0; i <= 8; i++) FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(i), 8);
            int before = total();
            PumpFlow.tick(level, ORIGIN);
            expect((FiniteWaterPhysics.getWaterLevel(level, ORIGIN.below()) == 0) == (count > 1),
                    "Stage count adds depth: " + count);
            expect(total() == before, "Series conserves all water");
        }
        clear();
        var stages = pipe(Direction.UP, 1, 2, 4);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
        for (int i = 0; i < 5; i++) FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(i), 8);
        expect(!PumpFlow.transfer(level, stages, 16, 5), "Visited-water budget enforced");
        expect(PumpFlow.transfer(level, stages, 16, 6), "Exact visited-water budget accepted");
        expect(PumpFlow.scaledLimit(Integer.MAX_VALUE, 3) == Integer.MAX_VALUE, "Config scaling cannot overflow");
        clear();
        stages = pipe(Direction.UP, 1, 2, 19);
        power(stages.getLast(), false);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
        for (int i = 1; i <= 8; i++) FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(i), 8);
        PumpFlow.tick(level, ORIGIN);
        expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN.below()) == 8, "Unpowered stage adds no depth");
        System.out.println("PUMP_FLOW_ORIENTATIONS_CONSERVATION_LIMITS_VALVE_PASS");
    }

    private void partialPrimingMustDischarge() {
        for (Direction facing : Direction.values()) {
            clear();
            var stages = pipe(facing, 1, 3, 1);
            FiniteWaterPhysics.setWaterLevel(level, ORIGIN.relative(facing.getOpposite()), 8);
            for (int i = 0; i < 3; i++) FiniteWaterPhysics.setWaterLevel(level, ORIGIN.relative(facing, i), i == 1 ? 7 : 8);
            int before = total();
            PumpFlow.tick(level, ORIGIN);
            expect(totalBeyond(facing, 3) == 7, "One missing internal level must not cancel discharge: " + facing);
            expect(total() == before, "Priming and discharge conserve water together");
            for (int i = 0; i < 3; i++) expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN.relative(facing, i)) == 8,
                    "Refilling a gap must not open another gap upstream");
        }
        System.out.println("PUMP_PARTIAL_PRIMING_DISCHARGE_PASS");
    }

    private void seriesWithNaturalFlow() {
        for (Direction facing : Direction.values()) {
            for (int size = 1; size <= 3; size++) {
                clear();
                var stages = pipe(facing, size, 3, 2);
                for (int tick = 0; tick < 120; tick++) {
                    if (tick % 20 == 0) {
                        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                            FiniteWaterPhysics.setWaterLevel(level, stages.getFirst().cell(x, y).relative(facing.getOpposite()), 8);
                        }
                        PumpFlow.tick(level, ORIGIN);
                    }
                    for (int z = -1; z < 5; z++) for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                        FiniteWaterPhysics.tick(level, stages.getFirst().cell(x, y).relative(facing, z));
                    }
                }
                expect(totalBeyond(facing, 3) > 0, "Series must discharge with natural flow: " + facing + " size " + size);
            }
        }
        System.out.println("PUMP_SERIES_NATURAL_FLOW_PASS");
    }

    private void downstreamAndBentOutlet() {
        clear();
        var stages = pipe(Direction.UP, 1, 1, 6);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(), 8);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(2), 8);
        var downstream = new PumpStructure(ORIGIN.above(3), 1, Direction.UP);
        level.setBlock(downstream.origin(), ModBlocks.WATER_PUMP.defaultBlockState()
                .setValue(WaterPumpBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
        power(downstream, true);
        expect(!PumpFlow.transfer(level, stages, 16, 64), "Independent powered downstream pump blocks pressure");
        expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN.below()) == 8, "Boundary rejection is atomic");
        power(downstream, false);
        expect(PumpFlow.transfer(level, stages, 16, 64), "Unpowered downstream pump accepts pressure");
        clear();
        stages = pipe(Direction.UP, 1, 1, 1);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.above(), 8);
        level.setBlock(ORIGIN.above().east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        expect(PumpFlow.transfer(level, stages, 8, 64), "Pressure search can use a bent outlet");
        System.out.println("PUMP_DOWNSTREAM_BOUNDARY_AND_BENT_OUTLET_PASS");
    }

    private void saturatedBentOutlet() {
        clear();
        var stage = new PumpStructure(ORIGIN, 1, Direction.EAST);
        level.setBlock(ORIGIN, ModBlocks.WATER_PUMP.defaultBlockState()
                .setValue(WaterPumpBlock.FACING, Direction.EAST).setValue(WaterPumpBlock.POWERED, true), Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.west(), 8);
        for (int x = 9; x <= 13; x++) for (int y = 214; y <= 216; y++) for (int z = 4; z <= 12; z++)
            FiniteWaterPhysics.setWaterLevel(level, new BlockPos(x, y, z), 8);
        for (int y = 214; y <= 216; y++) for (int z = 4; z <= 12; z++)
            level.setBlock(new BlockPos(14, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int x = 9; x <= 13; x++) for (int y = 214; y <= 216; y++) {
            level.setBlock(new BlockPos(x, y, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(x, y, 13), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int x = 9; x <= 13; x++) for (int z = 4; z <= 12; z++) {
            level.setBlock(new BlockPos(x, 213, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (x != 9 || z != 8) level.setBlock(new BlockPos(x, 217, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        expect(PumpFlow.transfer(level, List.of(stage), 8, 64), "Bent outlet must survive a saturated downstream network");
        expect(FiniteWaterPhysics.getWaterLevel(level, new BlockPos(9, 217, 8)) == 8,
                "Pressure must turn upward into the remaining outlet");
        System.out.println("PUMP_SATURATED_BENT_OUTLET_PASS");
        for (BlockPos pos : BlockPos.betweenClosed(9, 213, 3, 14, 217, 13))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private void guideChecks() {
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PumpGuideTest"),
                net.minecraft.server.level.ClientInformation.createDefault());
        for (Direction facing : Direction.values()) for (int size = 1; size <= 3; size++) {
            var stage = new PumpStructure(ORIGIN, size, facing);
            player.setPose(size == 1 ? net.minecraft.world.entity.Pose.SWIMMING : net.minecraft.world.entity.Pose.CROUCHING);
            player.refreshDimensions();
            var right = PumpStructure.right(facing).getUnitVec3();
            var up = PumpStructure.up(facing).getUnitVec3();
            var center = net.minecraft.world.phys.Vec3.atCenterOf(ORIGIN)
                    .add(right.add(up).scale((size - 1) / 2.0));
            var target = center.add(right.scale(size / 2.0 - .05));
            player.setPos(target.x, target.y - player.getBbHeight() / 2.0, target.z);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            var corrected = PumpFlow.guidedCurrent(player, stage, facing.getUnitVec3().scale(8));
            expect(corrected.dot(right) < 0 && corrected.dot(facing.getUnitVec3()) < 8,
                    "Off-center player is steered inward before crossing casing: " + facing + " size " + size);
        }
        System.out.println("PUMP_PLAYER_CENTERING_ALL_ORIENTATIONS_PASS");
    }

    private void fullWidthCurrent() {
        for (Direction facing : Direction.values()) for (int size = 1; size <= 3; size++) {
            clear();
            var stage = pipe(facing, size, 1, 1).getFirst();
            BlockPos blocked = stage.cell(size - 1, size - 1).relative(facing);
            if (size > 1) level.setBlock(blocked, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            var currents = new HashMap<BlockPos, net.minecraft.world.phys.Vec3>();
            BlockPos suppliedLane = stage.cell(0, 0).relative(facing);
            currents.put(suppliedLane, facing.getUnitVec3().scale(5));
            PumpFlow.spreadAxialCurrent(level, stage, currents);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                BlockPos pos = stage.cell(x, y).relative(facing);
                if (size > 1 && pos.equals(blocked)) {
                    expect(!currents.containsKey(pos), "Solid block excludes its lane from the current field");
                } else {
                    expect(currents.containsKey(pos) && currents.get(pos).dot(facing.getUnitVec3()) >= 5,
                            "Pump current fills cross-section: " + facing + " size " + size);
                }
            }
        }
        System.out.println("PUMP_FULL_WIDTH_CURRENT_ALL_ORIENTATIONS_PASS");
    }

    private void blockedCurrentFixture() {
        var stage = new PumpStructure(BLOCKED_ORIGIN, 1, Direction.UP);
        level.setBlock(stage.origin(), ModBlocks.WATER_PUMP.defaultBlockState()
                .setValue(WaterPumpBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
        level.setBlock(stage.origin().above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(stage.origin().east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos intake = stage.origin().below();
        level.setBlock(intake.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(intake.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        FiniteWaterPhysics.setWaterLevel(level, intake, 8);
        ((WaterPumpBlockEntity) level.getBlockEntity(stage.origin())).refresh();
        blockedMob = EntityTypes.RABBIT.create(level, EntitySpawnReason.COMMAND);
        blockedMob.setNoAi(true);
        blockedMob.setNoGravity(true);
        blockedMob.setPos(BLOCKED_ORIGIN.getX() + .5, BLOCKED_ORIGIN.getY() - .8,
                BLOCKED_ORIGIN.getZ() + .5);
        level.addFreshEntity(blockedMob);
    }

    boolean tick() {
        ticks++;
        if (blockedMob.getDeltaMovement().y > 0) {
            steadyCurrentStarted = true;
            steadyCurrentTicks++;
        } else if (steadyCurrentStarted) {
            throw new AssertionError("Wet powered pump current stopped between intervals at tick " + ticks);
        }
        blockedMob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        cycles = (int) PumpManager.get(level).groups.stream()
                .filter(group -> group.machines.stream().anyMatch(machine -> machine.controller() == ORIGIN.asLong()))
                .mapToLong(group -> group.completedCycles).findFirst().orElse(0);
        expect(totalBeyond(Direction.UP, 3) == Math.max(0, cycles - 3) * 8,
                "Committed cycles: prime three stages, then discharge 8 units per cycle; cycles=" + cycles
                        + " discharged=" + totalBeyond(Direction.UP, 3));
        if (cycles >= 3) for (int i = 0; i < 3; i++) {
            expect(FiniteWaterPhysics.getWaterLevel(level, ORIGIN.above(i)) == 8, "Supplied series has no internal air gaps");
        }
        FiniteWaterPhysics.setWaterLevel(level, ORIGIN.below(), 8);
        if (ticks < 125) return false;
        expect(cycles >= 5, "Priming and at least two native discharge cycles ran");
        expect(steadyCurrentTicks >= WaterPhysicsConfig.pumpTickInterval() * 2,
                "Blocked wet pump maintains current continuously across intervals: " + steadyCurrentTicks);
        expect(mob.getY() > ORIGIN.getY() + 3, "Native current sucks a mob through all three rotors: y=" + mob.getY());
        System.out.println("PUMP_FLOW_NATIVE_CADENCE_AND_MOB_SUCTION_PASS");
        return true;
    }

    private List<PumpStructure> pipe(Direction facing, int size, int count, int outletLength) {
        Direction right = PumpStructure.right(facing), up = PumpStructure.up(facing);
        for (int z = -2; z <= count + outletLength; z++) {
            for (int x = -1; x <= size; x++) for (int y = -1; y <= size; y++) {
                BlockPos pos = ORIGIN.relative(facing, z).relative(right, x).relative(up, y);
                boolean wall = x == -1 || y == -1 || x == size || y == size || z == -2 || z == count + outletLength;
                level.setBlock(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        var stages = new ArrayList<PumpStructure>();
        for (int z = 0; z < count; z++) {
            var stage = new PumpStructure(ORIGIN.relative(facing, z), size, facing);
            stages.add(stage);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                level.setBlock(stage.cell(x, y), ModBlocks.WATER_PUMP.defaultBlockState()
                        .setValue(WaterPumpBlock.FACING, facing), Block.UPDATE_CLIENTS);
            }
        }
        stages.forEach(stage -> power(stage, true));
        return stages;
    }

    private void power(PumpStructure stage, boolean on) {
        level.setBlock(stage.origin().relative(PumpStructure.right(stage.facing()).getOpposite()),
                (on ? Blocks.REDSTONE_BLOCK : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
            ((WaterPumpBlockEntity)level.getBlockEntity(stage.cell(x, y))).refresh();
        }
    }

    private int totalBeyond(Direction facing, int distance) {
        int sum = 0;
        int plane = PumpStructure.coordinate(ORIGIN.relative(facing, distance), facing);
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ())) {
            if (PumpStructure.coordinate(pos, facing) >= plane) sum += Math.max(0, FiniteWaterPhysics.getWaterLevel(level, pos));
        }
        return sum;
    }

    private int total() { return totalBeyond(Direction.UP, -8); }
    private void clear() {
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getBlockTicks().clearArea(AREA); level.getFluidTicks().clearArea(AREA);
    }
    @Override public void close() {
        if (mob != null) mob.discard();
        if (blockedMob != null) blockedMob.discard();
        clearBlockedFixture();
        clear();
    }
    private void clearBlockedFixture() {
        for (BlockPos pos : BlockPos.betweenClosed(
                BLOCKED_ORIGIN.offset(-1, -2, -1), BLOCKED_ORIGIN.offset(1, 1, 1))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
