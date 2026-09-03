package io.github.SirWashington.features;

final class FiniteWaterMath {
    private FiniteWaterMath() {
    }

    static int equalizeFromCenter(int center, int[] neighbors) {
        return equalizeFromCenter(center, neighbors, new int[neighbors.length]);
    }

    static int equalizeFromCenter(int center, int[] neighbors, int[] barriers) {
        if (neighbors.length != barriers.length) {
            throw new IllegalArgumentException("Each neighbor needs a flow barrier");
        }
        int stopped = 0;
        while (stopped < neighbors.length) {
            for (int i = 0; i < neighbors.length; i++) {
                if (neighbors[i] >= 0
                        && center > barriers[i]
                        && center > neighbors[i] + 1) {
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
        for (int i = 0; i < targets.length && amount > 0; i++) {
            if (targets[i] < 0) {
                continue;
            }
            int moved = Math.min(8 - targets[i], amount);
            targets[i] += moved;
            amount -= moved;
        }
        return amount;
    }
}
