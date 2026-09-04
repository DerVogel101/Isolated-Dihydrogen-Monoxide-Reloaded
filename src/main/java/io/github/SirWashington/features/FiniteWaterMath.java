package io.github.SirWashington.features;

final class FiniteWaterMath {
    private FiniteWaterMath() {
    }

    static int equalizeFromCenter(int center, int[] neighbors) {
        return equalizeFromCenter(center, neighbors, new int[neighbors.length]);
    }

    static int equalizeFromCenter(int center, int[] neighbors, int[] barriers) {
        return equalizeFromCenter(center, neighbors, barriers, 0, new int[neighbors.length]);
    }

    static int equalizeFromCenter(int center, int[] neighbors, int[] barriers, int floor, int[] floors) {
        if (neighbors.length != barriers.length) {
            throw new IllegalArgumentException("Each neighbor needs a flow barrier");
        }
        int stopped = 0;
        while (stopped < neighbors.length) {
            for (int i = 0; i < neighbors.length; i++) {
                if (center > 0 && neighbors[i] >= 0 && neighbors[i] + floors[i] < 8
                        && center + floor > barriers[i]
                        && center + floor > neighbors[i] + floors[i] + 1) {
                    neighbors[i]++;
                    center--;
                } else {
                    stopped++;
                }
            }
        }
        return center;
    }

    static int distribute(int amount, int[] targets) {
        int[] capacities = new int[targets.length];
        java.util.Arrays.fill(capacities, 8);
        return distribute(amount, targets, capacities);
    }

    static int distribute(int amount, int[] targets, int[] capacities) {
        for (int i = 0; i < targets.length && amount > 0; i++) {
            if (targets[i] < 0) {
                continue;
            }
            int moved = Math.min(capacities[i] - targets[i], amount);
            targets[i] += moved;
            amount -= moved;
        }
        return amount;
    }
}
