package io.github.SirWashington.item;


import io.github.SirWashington.component.ModDataComponentTypes;
import io.github.SirWashington.block.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
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

    private static Item register(ResourceKey<Item> key, Function<Item.Properties, Item> factory, Item.Properties properties) {
        Item item = factory.apply(properties.setId(key));
        if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(COMPRESSED_WOOL);
            entries.accept(DOUBLE_COMPRESSED_WOOL);
            entries.accept(INSULATOR_SHARD);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(WATER_PUMP); entries.accept(MUTED_WATER_PUMP); entries.accept(WATER_VALVE);
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
