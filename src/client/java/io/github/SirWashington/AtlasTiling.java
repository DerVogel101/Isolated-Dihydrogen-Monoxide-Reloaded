package io.github.SirWashington;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Split repeating UVs at tile boundaries before mapping them into an atlas sprite. */
final class AtlasTiling {
    record Vertex(float x, float y, float z, float u, float v) {
        Vertex mix(Vertex b, float t) {
            return new Vertex(x + (b.x-x)*t, y + (b.y-y)*t, z + (b.z-z)*t, u + (b.u-u)*t, v + (b.v-v)*t);
        }
    }
    private AtlasTiling() { }
    static void split(List<Vertex> quad, Consumer<List<Vertex>> output) {
        float minU = Float.POSITIVE_INFINITY, minV = minU, maxU = Float.NEGATIVE_INFINITY, maxV = maxU;
        for (Vertex p : quad) {
            minU = Math.min(minU, p.u); minV = Math.min(minV, p.v);
            maxU = Math.max(maxU, p.u); maxV = Math.max(maxV, p.v);
        }
        for (int u = (int)Math.floor(minU); u < Math.ceil(maxU); u++) {
            for (int v = (int)Math.floor(minV); v < Math.ceil(maxV); v++) {
                List<Vertex> polygon = clip(clip(clip(clip(quad, true, u, true), true, u+1, false), false, v, true), false, v+1, false);
                if (polygon.size() < 3) continue;
                var local = new ArrayList<Vertex>(polygon.size());
                for (Vertex p : polygon) local.add(new Vertex(p.x, p.y, p.z, Math.clamp(p.u-u, 0, 1), Math.clamp(p.v-v, 0, 1)));
                if (local.size() == 4) output.accept(local);
                else for (int i = 1; i < local.size()-1; i++)
                    output.accept(List.of(local.getFirst(), local.get(i), local.get(i+1), local.get(i+1)));
            }
        }
    }
    private static List<Vertex> clip(List<Vertex> input, boolean u, float bound, boolean above) {
        if (input.isEmpty()) return input;
        var result = new ArrayList<Vertex>();
        Vertex a = input.getLast();
        for (Vertex b : input) {
            float av = u ? a.u : a.v, bv = u ? b.u : b.v;
            boolean ai = above ? av >= bound : av <= bound, bi = above ? bv >= bound : bv <= bound;
            if (ai != bi) result.add(a.mix(b, (bound-av)/(bv-av)));
            if (bi) result.add(b);
            a = b;
        }
        return result;
    }
}
