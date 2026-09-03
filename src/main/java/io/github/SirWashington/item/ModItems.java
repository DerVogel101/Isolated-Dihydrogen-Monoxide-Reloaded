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
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries ->
                entries.accept(RAIN_SENSOR));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(PRECISION_BUCKET);
            entries.accept(FINITE_WATER_BUCKET);
        });
    }
}
