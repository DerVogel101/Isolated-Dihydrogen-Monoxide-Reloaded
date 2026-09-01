package io.github.SirWashington.fluid;

import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

public final class ModFluidTags {
    public static final TagKey<Fluid> FINITE_WATER = TagKey.create(
            Registries.FLUID,
            Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "finite_water")
    );

    private ModFluidTags() {
    }
}
