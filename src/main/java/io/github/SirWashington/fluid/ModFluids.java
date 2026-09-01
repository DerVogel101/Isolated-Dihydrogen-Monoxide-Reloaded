package io.github.SirWashington.fluid;

import net.fabricmc.fabric.api.registry.fluid.EntityFluidInteractionRegistry;
import net.fabricmc.fabric.api.registry.fluid.FluidBehavior;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

public final class ModFluids {
    public static final FlowingFluid FINITE_WATER = register(ModFluidIds.FINITE_WATER, new FiniteWaterFluid.Source());
    public static final FlowingFluid FLOWING_FINITE_WATER = register(ModFluidIds.FLOWING_FINITE_WATER, new FiniteWaterFluid.Flowing());

    private ModFluids() {
    }

    private static FlowingFluid register(ResourceKey<Fluid> key, FlowingFluid fluid) {
        return Registry.register(BuiltInRegistries.FLUID, key, fluid);
    }

    public static boolean isFiniteWater(Fluid fluid) {
        return fluid == FINITE_WATER || fluid == FLOWING_FINITE_WATER;
    }

    public static void initialize() {
        EntityFluidInteractionRegistry.register(ModFluidTags.FINITE_WATER, FluidBehavior.WATER_LIKE);
    }
}
