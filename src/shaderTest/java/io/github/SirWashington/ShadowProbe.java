package io.github.SirWashington;

public final class ShadowProbe {
    static int pumps, valves;
    public static void pump(WaterPumpRenderer.State state) { if (state.controller) pumps++; }
    public static void valve(WaterValveRenderer.State state) { if (state.controller) valves++; }
    static void verify() {
        if (pumps == 0 || valves == 0) throw new AssertionError("Missing machinery shadow submissions: pump="+pumps+", valve="+valves);
        System.out.println("MACHINERY_SHADOW_SUBMISSION_PASS pump="+pumps+" valve="+valves);
    }
}
