package io.github.SirWashington.features;

import io.github.SirWashington.block.PumpStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;

/** Retained geometry for one contiguous powered segment. Rebuilt only on topology/power changes. */
final class PumpAssembly {
    final List<PumpStructure> stages;
    final PumpStructure intake;
    final Direction facing;
    final net.minecraft.world.phys.Vec3 axis, center;
    final double openingRadius;
    final long[][] lanes;
    final long[] rotors;
    final boolean muted;
    final int depth, visits, budget, outletPlane;
    long lastStart = Long.MIN_VALUE;
    boolean wet;

    PumpAssembly(List<PumpStructure> stages, boolean muted, int depth, int visits, int units) {
        this.stages = List.copyOf(stages);
        intake = stages.getFirst();
        facing = intake.facing();
        axis = facing.getUnitVec3();
        center = net.minecraft.world.phys.Vec3.atCenterOf(intake.origin())
                .add(PumpStructure.right(facing).getUnitVec3().scale((intake.size() - 1) / 2.0))
                .add(PumpStructure.up(facing).getUnitVec3().scale((intake.size() - 1) / 2.0));
        openingRadius = io.github.SirWashington.block.PumpGeometry.openingRadius(intake.size());
        this.muted = muted;
        this.depth = depth;
        this.visits = visits;
        budget = PumpFlow.scaledLimit(units, intake.size() * intake.size());
        outletPlane = PumpStructure.coordinate(stages.getLast().origin(), facing);
        lanes = new long[intake.size() * intake.size()][3 + stages.size()];
        rotors = new long[intake.size() * intake.size() * stages.size()];
        int rotor = 0;
        for (int x = 0; x < intake.size(); x++) for (int y = 0; y < intake.size(); y++) {
            long[] lane = lanes[x * intake.size() + y];
            for (int d = 0; d < 3; d++) lane[d] = intake.cell(x, y).relative(facing, -d - 1).asLong();
            for (int stage = 0; stage < stages.size(); stage++)
                lane[3 + stage] = stages.get(stage).cell(x, y).asLong();
        }
        for (PumpStructure stage : stages)
            for (int x = 0; x < intake.size(); x++) for (int y = 0; y < intake.size(); y++)
                rotors[rotor++] = stage.cell(x, y).asLong();
    }

    long controller() { return intake.origin().asLong(); }
    int plane(long pos) { return BlockPos.getX(pos) * facing.getStepX()
            + BlockPos.getY(pos) * facing.getStepY() + BlockPos.getZ(pos) * facing.getStepZ(); }
}
