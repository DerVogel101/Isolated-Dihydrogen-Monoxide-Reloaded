package io.github.SirWashington.item;

import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItemIds {
    public static final ResourceKey<Item> WATER_VALVE = create("water_valve");
    public static final ResourceKey<Item> WATER_PUMP = create("water_pump");
    public static final ResourceKey<Item> MUTED_WATER_PUMP = create("muted_water_pump");
    public static final ResourceKey<Item> DIHYDROGEN_MONOXIDE_ASSEMBLER = create("generator");
    public static final ResourceKey<Item> MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER = create("muted_generator");
    public static final ResourceKey<Item> COMPRESSED_WOOL = create("compressed_wool");
    public static final ResourceKey<Item> DOUBLE_COMPRESSED_WOOL = create("double_compressed_wool");
    public static final ResourceKey<Item> INSULATOR_SHARD = create("insulator_shard");
    public static final ResourceKey<Item> FINITE_ICE = create("finite_ice");
    public static final ResourceKey<Item> RAIN_SENSOR = create("rain_sensor");
    public static final ResourceKey<Item> PRECISION_BUCKET = create("precision_bucket");
    public static final ResourceKey<Item> FINITE_WATER_BUCKET = create("finite_water_bucket");

    private ModItemIds() {
    }

    private static ResourceKey<Item> create(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, path));
    }
}
