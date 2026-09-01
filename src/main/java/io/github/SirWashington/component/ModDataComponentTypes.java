package io.github.SirWashington.component;

import com.mojang.serialization.Codec;
import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import java.util.function.UnaryOperator;

public class ModDataComponentTypes {

    public static final DataComponentType<Integer> BUCKET_FILL_LEVEL = register(
            "bucket_fill_level",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );


    private static <T>DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(WaterPhysics.MODID, name),
                builderOperator.apply(DataComponentType.builder()).build());
    }

    public static void registerDataComponentTypes() {

    }

}
