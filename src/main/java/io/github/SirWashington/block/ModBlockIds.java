package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockIds {
    public static final ResourceKey<Block> WATER_VALVE = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "water_valve"));
    public static final ResourceKey<Block> WATER_PUMP = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "water_pump"));
    public static final ResourceKey<Block> MUTED_WATER_PUMP = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "muted_water_pump"));
    public static final ResourceKey<Block> DIHYDROGEN_MONOXIDE_ASSEMBLER = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "generator"));
    public static final ResourceKey<Block> MUTED_DIHYDROGEN_MONOXIDE_ASSEMBLER = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "muted_generator"));
    public static final ResourceKey<Block> FINITE_ICE = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "finite_ice"));
    public static final ResourceKey<Block> LAYERED_FINITE_ICE = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "layered_finite_ice"));
    public static final ResourceKey<Block> RAIN_SENSOR = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "rain_sensor"));
    public static final ResourceKey<Block> ANTI_RAIN_GENERATOR = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "anti_rain_generator"));
    public static final ResourceKey<Block> FINITE_WATER = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "finite_water")
    );

    private ModBlockIds() {
    }
}
