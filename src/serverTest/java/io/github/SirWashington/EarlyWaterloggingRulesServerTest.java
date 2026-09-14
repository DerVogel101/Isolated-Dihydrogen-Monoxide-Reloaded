package io.github.SirWashington;

import io.github.SirWashington.features.FiniteWaterloggedPlants;
import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.util.List;

public final class EarlyWaterloggingRulesServerTest implements ModInitializer {
    @Override
    public void onInitialize() {
        boolean defaults = Boolean.getBoolean("immersivefluids.testDefaultRules");
        // These definitions were already built before any mod initializer or world tag reload.
        verify(Blocks.SPRUCE_SLAB, defaults, defaults ? 486 : 6);
        verify(Blocks.OAK_SLAB, true, 486);
        verify(Blocks.STONE_STAIRS, defaults, defaults ? 6480 : 80);
        verify(Blocks.OAK_STAIRS, true, 6480);
        verify(Blocks.STONE, false, 1);
        var formerlyExcluded = List.of(Blocks.OAK_LEAVES, Blocks.COBBLESTONE_WALL, Blocks.GLASS_PANE,
                Blocks.BARRIER, Blocks.BEACON, Blocks.SHULKER_BOX);
        for (Block block : formerlyExcluded) {
            if (block.defaultBlockState().hasProperty(FiniteWaterloggedPlants.LEVEL) == defaults)
                throw new AssertionError("Default/included selection mismatch: " + block);
        }
        if (Blocks.SPRUCE_LEAVES.defaultBlockState().hasProperty(FiniteWaterloggedPlants.LEVEL)
                || Blocks.STAINED_GLASS_PANE.asList().stream().anyMatch(block -> block.defaultBlockState().hasProperty(FiniteWaterloggedPlants.LEVEL)))
            throw new AssertionError("Default tags must still exclude leaves and colored panes");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verify(Blocks.SPRUCE_SLAB, defaults, defaults ? 486 : 6);
            verify(Blocks.OAK_SLAB, true, 486);
            for (Block block : formerlyExcluded) {
                if (FiniteWaterloggedPlants.canHoldFiniteWater(block.defaultBlockState()) == defaults)
                    throw new AssertionError("Runtime tags/config undid startup selection: " + block);
            }
            var configDir = FabricLoader.getInstance().getConfigDir().resolve("immersivefluids");
            if (!Files.isRegularFile(configDir.resolve("waterlogging.toml"))
                    || !Files.isRegularFile(configDir.resolve("server.toml")))
                throw new AssertionError("Missing configs in mod subfolder");
            if (defaults && !WaterPhysicsConfig.WATERLOGGING.excludedBlocks.get().equals(EarlyWaterloggingRules.DEFAULT_EXCLUDED))
                throw new AssertionError("Framework and early-reader defaults must match");
            System.out.println("EARLY_WATERLOGGING_SERVER_TEST_PASS defaults=" + defaults);
            server.halt(false);
        });
    }

    private static void verify(Block block, boolean included, int count) {
        var state = block.defaultBlockState();
        if (state.hasProperty(FiniteWaterloggedPlants.LEVEL) != included
                || state.hasProperty(FrozenWaterloggedBlocks.FROZEN) != included
                || block.getStateDefinition().getPossibleStates().size() != count) {
            throw new AssertionError("Early selection/state count mismatch: " + block);
        }
    }
}
