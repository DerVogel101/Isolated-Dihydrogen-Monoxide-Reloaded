package dervogel101.de.pumpedupwater;

import dervogel101.de.pumpedupwater.component.ModDataComponentTypes;
import dervogel101.de.pumpedupwater.block.ModBlocks;
import dervogel101.de.pumpedupwater.features.FiniteWaterPhysics;
import dervogel101.de.pumpedupwater.fluid.ModFluids;
import dervogel101.de.pumpedupwater.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

public class WaterPhysics implements ModInitializer {

    public static final String MODID = "pumpedupwater";

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WaterPhysicsConfig.loadServer(
                FabricLoader.getInstance().getConfigDir().resolve("pumpedupwater/server.toml")));
        ModDataComponentTypes.registerDataComponentTypes();
        ModFluids.initialize();
        ModBlocks.initialize();
        dervogel101.de.pumpedupwater.block.WaterPumpBlockEntity.initialize();
        dervogel101.de.pumpedupwater.block.WaterValveBlockEntity.initialize();
        dervogel101.de.pumpedupwater.block.AntiRainGeneratorBlockEntity.initialize();
        ModItems.initialize();
        dervogel101.de.pumpedupwater.features.PumpManager.initialize();
        FiniteWaterPhysics.initialize();
        dervogel101.de.pumpedupwater.block.PumpStructure.initialize();
        dervogel101.de.pumpedupwater.features.FrozenWaterloggedBlocks.initialize();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("waterlevel")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(context -> {
                                var pos = BlockPosArgument.getSpawnablePos(context, "pos");
                                int result = FiniteWaterPhysics.getWaterLevel(context.getSource().getLevel(), pos);
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Finite-water level at " + pos + " is " + result),
                                        false
                                );
                                return result;
                            })));
        });
    }
}
