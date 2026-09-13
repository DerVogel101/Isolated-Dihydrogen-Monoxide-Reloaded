package io.github.SirWashington;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import io.github.SirWashington.block.ModBlocks;
import org.slf4j.LoggerFactory;

/** Opt-in startup/Mixin probe, never included in a normal release build. */
public final class ShaderClientProbe implements ClientModInitializer {
    private boolean started, populated;
    private int ticks, totalTicks;
    private java.util.concurrent.CompletableFuture<Void> resourceReload;
    @Override public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                if (FabricLoader.getInstance().isModLoaded("iris")) {
                    for (String name : new String[]{
                            "net.irisshaders.iris.shaderpack.include.IncludeGraph",
                            "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping",
                            "net.irisshaders.iris.shaderpack.IdMap",
                            "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
                            "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",
                            "net.minecraft.client.renderer.feature.ItemFeatureRenderer", "net.minecraft.client.Camera"})
                        Class.forName(name);
                    ShaderMaterialProbe.run();
                }
                LoggerFactory.getLogger("immersivefluids").info("SHADER_CLIENT_STARTUP_PASS");
            } catch (Throwable failure) {
                LoggerFactory.getLogger("immersivefluids").error("SHADER_CLIENT_STARTUP_FAIL", failure);
                throw new RuntimeException(failure);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++totalTicks > 2400) throw new AssertionError("Shader world probe timed out");
            if (!started && client.gui.screen() instanceof TitleScreen) {
                started = true;
                client.createWorldOpenFlows().createFreshLevel("shader-probe-" + System.currentTimeMillis(),
                        new LevelSettings("Shader probe", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(12345, false, false), WorldPresets::createTestWorldDimensions, null);
            }
            if (client.level == null || client.player == null) return;
            if (!populated) {
                populated = true;
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.overworld();
                    var player = server.getPlayerList().getPlayers().getFirst();
                    BlockPos center = player.blockPosition().offset(0, -1, 0);
                    ShaderFrameProbe.populate(level, center);
                    var blocks = new net.minecraft.world.level.block.Block[]{ModBlocks.WATER_PUMP, ModBlocks.WATER_VALVE,
                            ModBlocks.DIHYDROGEN_MONOXIDE_ASSEMBLER, ModBlocks.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER,
                            ModBlocks.RAIN_SENSOR, ModBlocks.FINITE_ICE, ModBlocks.FINITE_WATER, Blocks.IRON_BLOCK};
                    for (int i = 0; i < blocks.length; i++) {
                        BlockPos pos = center.offset(i-4, 1, 4);
                        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(pos, blocks[i].defaultBlockState());
                    }
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(io.github.SirWashington.item.ModItems.RAIN_SENSOR));
                    player.connection.teleport(center.getX()-5.5, center.getY()+2, center.getZ()+.5, -35, 18);
                });
            }
            ++ticks;
            if (ticks == 80) ShaderFrameProbe.verify(3);
            if (ticks == 110) client.getSingleplayerServer().execute(() -> ShaderFrameProbe.shrink(client.getSingleplayerServer().overworld()));
            boolean shader = Boolean.getBoolean("immersivefluids.shaderProbePack");
            if (ticks == 70) net.minecraft.client.Screenshot.grab(client.gameDirectory,
                    "machinery.png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
            if (shader && ticks == 80) { ShaderMaterialProbe.verifyPipeline(); ShaderMaterialProbe.toggle(false); }
            if (shader && ticks == 100) ShaderMaterialProbe.toggle(true);
            if (shader && ticks == 130) resourceReload = client.reloadResourcePacks();
            if (ticks >= (shader ? 260 : 160) && (resourceReload == null || resourceReload.isDone())) {
                if (shader) ShadowProbe.verify();
                if (resourceReload != null) resourceReload.join();
                ShaderFrameProbe.verify(2);
                if (FabricLoader.getInstance().isModLoaded("iris")) ShaderMaterialProbe.verifyPipeline();
                LoggerFactory.getLogger("immersivefluids").info("SHADER_CLIENT_WORLD_RENDER_PASS");
                client.stop();
            }
        });
    }
}
