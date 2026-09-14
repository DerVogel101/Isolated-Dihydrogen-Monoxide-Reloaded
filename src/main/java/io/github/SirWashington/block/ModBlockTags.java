package io.github.SirWashington.block;

import io.github.SirWashington.WaterPhysics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;

public final class ModBlockTags {
    private static final VarHandle HOLDER_TAGS;

    static {
        try {
            // Holder.isBound() only checks the key/value, not whether tags are ready.
            HOLDER_TAGS = MethodHandles.privateLookupIn(Holder.Reference.class, MethodHandles.lookup())
                    .findVarHandle(Holder.Reference.class, "tags", Set.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final TagKey<Block> EXTENDED_DRAIN_PATH = create("extended_drain_path");
    public static final TagKey<Block> IGNORES_OWN_SHAPE_FOR_OUTFLOW = create("ignores_own_shape_for_outflow");
    public static final TagKey<Block> WATER_PRESSURE_OPENABLE_DOORS = create("water_pressure_openable_doors");
    public static final TagKey<Block> FINITE_WATERLOGGING_EXCLUDED = create("finite_waterlogging_excluded");
    public static final TagKey<Block> FINITE_WATER_EXTINGUISHABLE = create("finite_water_extinguishable");

    private ModBlockTags() {
    }

    public static boolean contains(TagKey<Block> tag, BlockState state) {
        // Read each time: data-pack reloads replace this set.
        var tags = (Set<?>) HOLDER_TAGS.get(state.getBlock().builtInRegistryHolder());
        return tags != null && tags.contains(tag);
    }

    public static boolean matchesSelector(BlockState state, String selector) {
        boolean tagSelector = selector.startsWith("#");
        Identifier id = Identifier.tryParse(tagSelector ? selector.substring(1) : selector);
        if (id == null) {
            return false;
        }
        return tagSelector
                ? contains(TagKey.create(Registries.BLOCK, id), state)
                : BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(WaterPhysics.MODID, path)
        );
    }
}
