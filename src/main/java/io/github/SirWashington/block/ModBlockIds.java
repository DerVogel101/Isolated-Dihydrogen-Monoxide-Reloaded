package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockIds {
    public static final ResourceKey<Block> FINITE_ICE = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "finite_ice"));
    public static final ResourceKey<Block> LAYERED_FINITE_ICE = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "layered_finite_ice"));
    public static final ResourceKey<Block> RAIN_SENSOR = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "rain_sensor"));
    public static final ResourceKey<Block> FINITE_WATER = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "finite_water")
    );

    private ModBlockIds() {
    }
}
