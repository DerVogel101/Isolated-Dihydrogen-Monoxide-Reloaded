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
    private static final ThreadLocal<TagSelectors> TAG_SELECTORS = ThreadLocal.withInitial(TagSelectors::new);
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
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (selector.startsWith("#")) {
            TagKey<Block> tag = TAG_SELECTORS.get().parse(selector);
            return tag != null && contains(tag, state);
        }
        if (selector.startsWith("@")) {
            String namespace = selector.substring(1);
            return Identifier.tryParse(namespace + ":block") != null && blockId.getNamespace().equals(namespace);
        }
        if (selector.indexOf('*') >= 0) {
            return wildcardMatches(blockId.toString(), selector);
        }
        Identifier id = Identifier.tryParse(selector);
        return id != null && blockId.equals(id);
    }

    /** Cache only parsing; contains() still reads the current tag set after every data-pack reload. */
    private static final class TagSelectors {
        private final String[] selectors = new String[256];
        private final TagKey<?>[] tags = new TagKey<?>[256];

        @SuppressWarnings("unchecked")
        private TagKey<Block> parse(String selector) {
            int slot = selector.hashCode() & (selectors.length - 1);
            if (!selector.equals(selectors[slot])) {
                Identifier id = Identifier.tryParse(selector.substring(1));
                tags[slot] = id == null ? null : TagKey.create(Registries.BLOCK, id);
                selectors[slot] = selector;
            }
            return (TagKey<Block>) tags[slot];
        }
    }

    private static boolean wildcardMatches(String value, String pattern) {
        int valueIndex = 0;
        int patternIndex = 0;
        int starIndex = -1;
        int starValueIndex = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(WaterPhysics.MODID, path)
        );
    }
}
