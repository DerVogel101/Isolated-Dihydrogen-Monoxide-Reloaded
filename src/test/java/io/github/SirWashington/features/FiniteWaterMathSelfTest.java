package io.github.SirWashington.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;

public final class FiniteWaterMathSelfTest {
    public static void main(String[] args) {
        verifyAllHorizontalCombinations();
        verifySettledWaterStaysSettled();
        verifyDistribution();
        verifyPistonTransaction();
        verifyPistonDirectionPriority();
        verifyPistonSideFallback();
        verifyPistonDoesNotCrossBarrier();
        verifyBlockedPiston();
    }

    private static void verifyAllHorizontalCombinations() {
        for (int center = 0; center <= 8; center++) {
            for (int north = -1; north <= 8; north++) {
                for (int east = -1; east <= 8; east++) {
                    for (int south = -1; south <= 8; south++) {
                        for (int west = -1; west <= 8; west++) {
                            verifyConservation(center, new int[]{north, east, south, west});
                        }
                    }
                }
            }
        }
    }

    private static void verifyConservation(int center, int[] neighbors) {
        int before = center + sum(neighbors);
        int remaining = FiniteWaterMath.equalizeFromCenter(center, neighbors);
        int after = remaining + sum(neighbors);
        if (before != after) {
            throw new AssertionError("Finite-water volume changed from " + before + " to " + after);
        }
        for (int amount : neighbors) {
            if (amount < -1 || amount > 8) {
                throw new AssertionError("Invalid finite-water level: " + amount);
            }
        }
    }

    private static void verifySettledWaterStaysSettled() {
        int[] neighbors = {0, 1, -1, 0};
        int remaining = FiniteWaterMath.equalizeFromCenter(1, neighbors);
        if (remaining != 1 || neighbors[0] != 0 || neighbors[1] != 1 || neighbors[3] != 0) {
            throw new AssertionError("Settled finite water moved again");
        }
    }

    private static void verifyDistribution() {
        int[] targets = {-1, 6, 8, 0};
        int remaining = FiniteWaterMath.distribute(8, targets);
        if (remaining != 0 || targets[0] != -1 || targets[1] != 8 || targets[2] != 8 || targets[3] != 6) {
            throw new AssertionError("Finite-water distribution lost or crossed capacity");
        }
    }

    private static int sum(int[] amounts) {
        int result = 0;
        for (int amount : amounts) {
            result += Math.max(amount, 0);
        }
        return result;
    }

    private static void verifyPistonTransaction() {
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.east();
        Map<BlockPos, Integer> world = Map.of(first, 8, second, 8);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(first, second), Direction.EAST, pos -> world.getOrDefault(pos, 0)
        );
        if (plan == null
                || plan.values().stream().mapToInt(Integer::intValue).sum() != 16
                || plan.getOrDefault(second, -1) != 8
                || plan.getOrDefault(second.east(), -1) != 8) {
            throw new AssertionError("Multi-block piston plan did not conserve 16 units");
        }
    }

    private static void verifyPistonDirectionPriority() {
        BlockPos water = BlockPos.ZERO;
        BlockPos first = water.east();
        BlockPos second = first.east();
        Map<BlockPos, Integer> world = Map.of(water, 8, first, 4, first.above(), -1);
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST, pos -> world.getOrDefault(pos, 0)
        );
        if (plan == null || plan.getOrDefault(first, -1) != 8 || plan.getOrDefault(second, -1) != 4) {
            throw new AssertionError("Piston plan did not continue in the preferred direction");
        }
    }

    private static void verifyPistonSideFallback() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST,
                pos -> pos.equals(water) ? 8 : pos.getX() > 0 ? -1 : 0
        );
        if (plan == null || plan.getOrDefault(water.south(), -1) != 8) {
            throw new AssertionError("Piston plan did not use the clockwise side fallback");
        }
    }

    private static void verifyPistonDoesNotCrossBarrier() {
        BlockPos water = BlockPos.ZERO;
        BlockPos airBehindBarrier = water.east().east();
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST,
                pos -> pos.equals(water) ? 8 : pos.equals(airBehindBarrier) ? 0 : -1
        );
        if (plan != null) {
            throw new AssertionError("Piston plan crossed a solid barrier");
        }
    }

    private static void verifyBlockedPiston() {
        BlockPos water = BlockPos.ZERO;
        Map<BlockPos, Integer> plan = SpecialFlow.planPush(
                List.of(water), Direction.EAST, pos -> pos.equals(water) ? 8 : -1
        );
        if (plan != null) {
            throw new AssertionError("Fully blocked piston plan should fail");
        }
    }
}
