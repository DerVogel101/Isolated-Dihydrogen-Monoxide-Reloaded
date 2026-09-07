package io.github.SirWashington.features;

import io.github.SirWashington.block.DihydrogenMonoxideAssemblerBlock;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/** Run with -PgeneratorTest runServer in the disposable build/generator-test-server directory. */
public final class DihydrogenMonoxideAssemblerServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(8, 200, 8);
    private static final BoundingBox AREA = new BoundingBox(6, 197, 6, 10, 202, 10);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            level.setChunkForced(0, 0, true);
            try {
                crafting(level);
                redstonePulses(level, ModBlocks.DIHYDROGEN_MONOXIDE_ASSEMBLER);
                redstonePulses(level, ModBlocks.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER);
                expect(!((DihydrogenMonoxideAssemblerBlock) ModBlocks.DIHYDROGEN_MONOXIDE_ASSEMBLER).muted(),
                        "Normal assembler plays the completed-brew sound");
                expect(((DihydrogenMonoxideAssemblerBlock) ModBlocks.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER).muted(),
                        "Muted assembler suppresses the completed-brew sound");
                System.out.println("DIHYDROGEN_MONOXIDE_ASSEMBLER_SERVER_TEST_PASS");
            } finally {
                clear(level);
                level.setChunkForced(0, 0, false);
                server.halt(false);
            }
        });
    }

    private static void crafting(ServerLevel level) {
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.IRON_TRAPDOOR), new ItemStack(Items.DISPENSER), new ItemStack(Items.IRON_TRAPDOOR),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.CAULDRON), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.IRON_BLOCK)));
        ItemStack output = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow().value().assemble(input);
        expect(output.is(ModItems.DIHYDROGEN_MONOXIDE_ASSEMBLER), "Supplied recipe crafts the assembler");

        CraftingInput mutedInput = CraftingInput.of(1, 3, List.of(
                new ItemStack(ModItems.INSULATOR_SHARD), new ItemStack(ModItems.DIHYDROGEN_MONOXIDE_ASSEMBLER),
                new ItemStack(ModItems.INSULATOR_SHARD)));
        ItemStack mutedOutput = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, mutedInput, level)
                .orElseThrow().value().assemble(mutedInput);
        expect(mutedOutput.is(ModItems.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER),
                "Two insulator shards craft the muted assembler");
        for (String id : List.of("generator", "muted_generator")) {
            expect(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath("immersivefluids", id))).isPresent(), "Recipe loads: " + id);
        }
    }

    private static void redstonePulses(ServerLevel level, Block assembler) {
        clear(level);
        level.setBlock(POS, assembler.defaultBlockState(), Block.UPDATE_ALL);
        pulse(level);
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.below()) == 8, "Rising redstone edge creates eight finite-water units");
        expect(level.getBlockState(POS).getValue(DihydrogenMonoxideAssemblerBlock.POWERED), "Assembler records active power");
        FiniteWaterPhysics.setWaterLevel(level, POS.below(), 0);
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.below()) == 0, "Fixture cleared water while continuously powered");
        level.setBlock(POS.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        expect(!level.getBlockState(POS).getValue(DihydrogenMonoxideAssemblerBlock.POWERED), "Power-off resets edge detector");
        pulse(level);
        expect(FiniteWaterPhysics.getWaterLevel(level, POS.below()) == 8, "Second rising edge creates another full level");

        clear(level);
        level.setBlock(POS, assembler.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POS.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        pulse(level);
        expect(level.getBlockState(POS.below()).is(Blocks.STONE), "Assembler does not overwrite solid blocks");
    }

    private static void pulse(ServerLevel level) {
        level.setBlock(POS.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void clear(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(AREA.minX(), AREA.minY(), AREA.minZ(), AREA.maxX(), AREA.maxY(), AREA.maxZ())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getBlockTicks().clearArea(AREA);
        level.getFluidTicks().clearArea(AREA);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
