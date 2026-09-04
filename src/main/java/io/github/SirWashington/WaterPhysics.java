package io.github.SirWashington;

import io.github.SirWashington.component.ModDataComponentTypes;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.fluid.ModFluids;
import io.github.SirWashington.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

public class WaterPhysics implements ModInitializer {

    public static final String MODID = "immersivefluids";

    @Override
    public void onInitialize() {
        ModDataComponentTypes.registerDataComponentTypes();
        ModFluids.initialize();
        ModBlocks.initialize();
        ModItems.initialize();
        FiniteWaterPhysics.initialize();
        io.github.SirWashington.features.FrozenWaterloggedBlocks.initialize();

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
