package io.github.SirWashington.item;


import io.github.SirWashington.WaterPhysics;
import io.github.SirWashington.component.ModDataComponentTypes;
import io.github.SirWashington.block.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;

import java.util.function.Function;


public class ModItems {
    public static final Item WATER_VALVE = register(ModItemIds.WATER_VALVE,
            properties -> new BlockItem(ModBlocks.WATER_VALVE, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item WATER_PUMP = register(ModItemIds.WATER_PUMP,
            properties -> new BlockItem(ModBlocks.WATER_PUMP, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item MUTED_WATER_PUMP = register(ModItemIds.MUTED_WATER_PUMP,
            properties -> new BlockItem(ModBlocks.MUTED_WATER_PUMP, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item DIHYDROGEN_MONOXIDE_ASSEMBLER = register(ModItemIds.DIHYDROGEN_MONOXIDE_ASSEMBLER,
            properties -> new BlockItem(ModBlocks.DIHYDROGEN_MONOXIDE_ASSEMBLER, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER = register(ModItemIds.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER,
            properties -> new BlockItem(ModBlocks.MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item COMPRESSED_WOOL = register(ModItemIds.COMPRESSED_WOOL, Item::new, new Item.Properties());
    public static final Item DOUBLE_COMPRESSED_WOOL = register(ModItemIds.DOUBLE_COMPRESSED_WOOL, Item::new, new Item.Properties());
    public static final Item INSULATOR_SHARD = register(ModItemIds.INSULATOR_SHARD, Item::new, new Item.Properties());
    public static final Item FINITE_ICE = register(ModItemIds.FINITE_ICE,
            properties -> new BlockItem(ModBlocks.FINITE_ICE, properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item RAIN_SENSOR = register(ModItemIds.RAIN_SENSOR,
            properties -> new BlockItem(ModBlocks.RAIN_SENSOR, properties),
            new Item.Properties().useBlockDescriptionPrefix());

    public static final Item PRECISION_BUCKET = register(
            ModItemIds.PRECISION_BUCKET,
            PrecisionBucketItem::new,
            new Item.Properties().stacksTo(1).component(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0)
    );
    public static final Item FINITE_WATER_BUCKET = register(
            ModItemIds.FINITE_WATER_BUCKET,
            FiniteWaterBucketItem::new,
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
    );

    public static final ResourceKey<CreativeModeTab> CREATIVE_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "creative_tab")
    );

    private static Item register(ResourceKey<Item> key, Function<Item.Properties, Item> factory, Item.Properties properties) {
        Item item = factory.apply(properties.setId(key));
        if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                CREATIVE_TAB,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.immersivefluids"))
                        .icon(() -> new ItemStack(FINITE_WATER_BUCKET))
                        .displayItems((parameters, output) -> {
                            output.accept(WATER_VALVE);
                            output.accept(WATER_PUMP);
                            output.accept(MUTED_WATER_PUMP);
                            output.accept(DIHYDROGEN_MONOXIDE_ASSEMBLER);
                            output.accept(MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER);
                            output.accept(COMPRESSED_WOOL);
                            output.accept(DOUBLE_COMPRESSED_WOOL);
                            output.accept(INSULATOR_SHARD);
                            output.accept(FINITE_ICE);
                            output.accept(RAIN_SENSOR);
                            output.accept(PRECISION_BUCKET);
                            output.accept(FINITE_WATER_BUCKET);
                        })
                        .build()
        );
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(COMPRESSED_WOOL);
            entries.accept(DOUBLE_COMPRESSED_WOOL);
            entries.accept(INSULATOR_SHARD);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(WATER_PUMP); entries.accept(MUTED_WATER_PUMP); entries.accept(WATER_VALVE);
            entries.accept(DIHYDROGEN_MONOXIDE_ASSEMBLER); entries.accept(MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> entries.accept(FINITE_ICE));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries ->
                entries.accept(RAIN_SENSOR));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(PRECISION_BUCKET);
            entries.accept(FINITE_WATER_BUCKET);
        });
    }
}
