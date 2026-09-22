package io.github.SirWashington;

import com.sun.management.ThreadMXBean;
import jdk.jfr.Recording;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

/** Opt-in copied-world benchmark. Never compiled into a normal release. */
public final class FluidPerformanceClient implements ClientModInitializer {
    private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final ArrayList<Long> tickTimes = new ArrayList<>();
    private boolean opened, finished, captured;
    private volatile boolean positioned;
    private long readyAt, measuredAt, tickStarted, allocatedBefore, cpuBefore, allocated, cpu;
    private long gcCount, gcMillis;
    private Recording recording;

    @Override
    public void onInitializeClient() {
        if (threads.isThreadAllocatedMemorySupported()) threads.setThreadAllocatedMemoryEnabled(true);
        if (threads.isThreadCpuTimeSupported()) threads.setThreadCpuTimeEnabled(true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (positioned && client.player != null) {
                client.player.setPos(205, 121, 53);
                client.player.setYRot(0);
                client.player.setXRot(0);
                client.player.setDeltaMovement(Vec3.ZERO);
            }
            if (!opened && client.gui.screen() instanceof TitleScreen) {
                opened = true;
                client.options.pauseOnLostFocus = false;
                client.createWorldOpenFlows().openWorld("benchmark", () -> {
                    throw new AssertionError("Benchmark world did not open");
                });
            }
            if (positioned && !captured && System.nanoTime() - readyAt > Duration.ofSeconds(30).toNanos()) {
                captured = true;
                write("camera.txt", "position=" + client.player.position() + "\nyaw=" + client.player.getYRot()
                        + "\npitch=" + client.player.getXRot() + "\nhealth=" + client.player.getHealth() + "\n");
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "performance.png",
                        client.gameRenderer.mainRenderTarget(), 1, message -> {});
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerList().getPlayers().isEmpty()) return;
            if (!positioned) {
                var level = server.overworld();
                if (level.getSeed() != -2541210193554019765L) throw new AssertionError("Wrong benchmark seed");
                var player = server.getPlayerList().getPlayers().getFirst();
                player.connection.teleport(205, 121, 53, 0, 0);
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                level.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 100, server);
                boolean clear = Boolean.getBoolean("immersivefluids.performanceClear");
                server.setWeatherParameters(clear ? 600000 : 0, clear ? 0 : 600000, !clear, false);
                if (server.tickRateManager().tickrate() != 20.0F) throw new AssertionError("Expected 20 TPS");
                readyAt = System.nanoTime();
                writeEnvironment();
                write("player-start.txt", "position=" + player.position() + "\nhealth=" + player.getHealth()
                        + "\ngamemode=" + player.gameMode.getGameModeForPlayer() + "\n");
                positioned = true;
                System.out.println("FLUID_WORLD_WARMUP_START");
            }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setPos(205, 121, 53);
            player.setYRot(0);
            player.setXRot(0);
            player.setDeltaMovement(Vec3.ZERO);
            if (System.nanoTime() - readyAt < Duration.ofSeconds(60).toNanos()) return;
            if (measuredAt == 0) {
                measuredAt = System.nanoTime();
                gcCount = gcCount();
                gcMillis = gcMillis();
                recording = new Recording();
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
                recording.enable("jdk.GarbageCollection");
                recording.enable("jdk.GCPhasePause");
                recording.enable("jdk.GCHeapSummary");
                recording.enable("jdk.GCCPUTime");
                if (Boolean.getBoolean("immersivefluids.performanceAllocations")) {
                    recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                }
                recording.start();
                System.out.println("FLUID_WORLD_MEASUREMENT_START");
            }
            long thread = Thread.currentThread().threadId();
            allocatedBefore = allocated(thread);
            cpuBefore = threads.getCurrentThreadCpuTime();
            tickStarted = System.nanoTime();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || measuredAt == 0 || tickStarted == 0) return;
            tickTimes.add(System.nanoTime() - tickStarted);
            allocated += allocated(Thread.currentThread().threadId()) - allocatedBefore;
            cpu += threads.getCurrentThreadCpuTime() - cpuBefore;
            tickStarted = 0;
            if (System.nanoTime() - measuredAt >= Duration.ofSeconds(180).toNanos()) {
                finished = true;
                var player = server.getPlayerList().getPlayers().getFirst();
                write("player-end.txt", "position=" + player.position() + "\nhealth=" + player.getHealth()
                        + "\nyaw=" + player.getYRot() + "\npitch=" + player.getXRot() + "\n");
                finish();
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            }
        });
    }

    private long allocated(long thread) {
        return threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(thread) : 0;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
    }

    private void writeEnvironment() {
        var client = Minecraft.getInstance();
        String mods = FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId() + "=" + m.getMetadata().getVersion()).sorted().reduce("", (a, b) -> a + b + "\n");
        String details = "java=" + System.getProperty("java.version") + "\njvm_args="
                + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\nmax_heap=" + Runtime.getRuntime().maxMemory()
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\nrender_distance=" + client.options.renderDistance().get()
                + "\nsimulation_distance=" + client.options.simulationDistance().get()
                + "\nallocation_tracing=" + Boolean.getBoolean("immersivefluids.performanceAllocations") + "\n" + mods;
        write("environment.txt", details);
        if (client.options.renderDistance().get() != 17 || client.options.simulationDistance().get() != 10) {
            throw new AssertionError("Benchmark view/simulation distances changed");
        }
    }

    private void finish() {
        long[] values = tickTimes.stream().mapToLong(Long::longValue).sorted().toArray();
        double seconds = (System.nanoTime() - measuredAt) / 1e9;
        String result = "ticks=" + values.length + "\nseconds=" + seconds + "\ntps=" + values.length / seconds
                + "\np50_ms=" + percentile(values, .50) + "\np95_ms=" + percentile(values, .95)
                + "\np99_ms=" + percentile(values, .99) + "\nover_50ms=" + Arrays.stream(values).filter(v -> v > 50_000_000).count()
                + "\nserver_cpu_ms_per_tick=" + cpu / 1e6 / values.length
                + "\nserver_alloc_bytes_per_tick=" + allocated / values.length
                + "\nserver_alloc_bytes_per_second=" + allocated / seconds
                + "\ngc_count=" + (gcCount() - gcCount) + "\ngc_ms=" + (gcMillis() - gcMillis) + "\n";
        write("metrics.txt", result);
        try {
            recording.stop();
            recording.dump(Path.of("world.jfr"));
            recording.close();
        } catch (Exception e) { throw new RuntimeException(e); }
        System.out.println("FLUID_WORLD_PERFORMANCE_PASS\n" + result);
    }

    private static double percentile(long[] values, double p) {
        return values[Math.min(values.length - 1, (int) Math.ceil(values.length * p) - 1)] / 1e6;
    }

    private static void write(String file, String value) {
        try { Files.writeString(Path.of(file), value); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
