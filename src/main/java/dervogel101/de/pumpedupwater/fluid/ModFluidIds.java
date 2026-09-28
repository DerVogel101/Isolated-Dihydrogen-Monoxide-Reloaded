package dervogel101.de.pumpedupwater.fluid;

import dervogel101.de.pumpedupwater.WaterPhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;

public final class ModFluidIds {
    public static final ResourceKey<Fluid> FINITE_WATER = create("finite_water");
    public static final ResourceKey<Fluid> FLOWING_FINITE_WATER = create("flowing_finite_water");

    private ModFluidIds() {
    }

    private static ResourceKey<Fluid> create(String path) {
        return ResourceKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, path));
    }
}
