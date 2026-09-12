package io.github.SirWashington.compat.iris;

import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.features.FrozenWaterloggedBlocks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class IrisMaterials {
    // Private dispatch markers, NOT shader material IDs. Source validation checks for collisions.
    public static final int MACHINERY = 29990, ITEM = 29991, ICE_ITEM = 29992;
    public static volatile boolean supported;
    private IrisMaterials() { }

    public static boolean active() { return supported && IrisApi.getInstance().isShaderPackInUse(); }

    public static void mapBlocks(Object2IntMap<BlockState> ids) {
        if (!supported) return;
        alias(ids, ModBlocks.FINITE_WATER, Blocks.WATER);
        alias(ids, ModBlocks.FINITE_ICE, Blocks.ICE);
        alias(ids, ModBlocks.LAYERED_FINITE_ICE, Blocks.ICE);
        for (Block block : new Block[]{ModBlocks.WATER_PUMP, ModBlocks.MUTED_WATER_PUMP, ModBlocks.WATER_VALVE})
            for (BlockState state : block.getStateDefinition().getPossibleStates())
                if (!ids.containsKey(state)) ids.put(state, MACHINERY);
    }

    private static void alias(Object2IntMap<BlockState> ids, Block block, Block reference) {
        int id = ids.getOrDefault(reference.defaultBlockState(), -1);
        if (id == -1) return;
        for (BlockState state : block.getStateDefinition().getPossibleStates())
            if (!ids.containsKey(state)) ids.put(state, id);
    }

    public static int blockSurface(BlockState state, TextureAtlasSprite sprite, int original) {
        if (!active() || sprite == null) return original;
        boolean own = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("immersivefluids");
        boolean ice = FrozenWaterloggedBlocks.isFrozen(state)
                && sprite.contents().name().equals(Identifier.withDefaultNamespace("block/ice"));
        if (!own && !ice) return original;
        // Explicit shader support for a mod block takes precedence over our fallback.
        var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (own && ids != null && ids.containsKey(state) && original != MACHINERY
                && state.getBlock() != ModBlocks.FINITE_ICE && state.getBlock() != ModBlocks.LAYERED_FINITE_ICE) return original;
        return surface(sprite, original);
    }

    public static int surface(TextureAtlasSprite sprite, int fallback) {
        var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids == null || sprite == null) return fallback;
        Identifier texture = sprite.contents().name();
        if (!texture.getNamespace().equals("minecraft") || !texture.getPath().startsWith("block/")) return fallback;
        String name = texture.getPath().substring(6);
        name = switch (name) {
            case "cauldron_side", "cauldron_top", "cauldron_bottom", "cauldron_inner" -> "cauldron";
            case "water_still", "water_flow", "water_overlay" -> "water";
            default -> name;
        };
        Block reference = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
        if (reference == null || reference == Blocks.AIR) return fallback;
        return ids.getOrDefault(reference.defaultBlockState(), fallback);
    }

    public static int itemSurface(TextureAtlasSprite sprite) {
        if (sprite.contents().name().equals(Identifier.withDefaultNamespace("block/ice"))) return ICE_ITEM;
        return surface(sprite, 0);
    }

    public static void withItemMaterial(int material, Runnable emit) {
        var context = net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE;
        int previous = context.getCurrentRenderedItem();
        try {
            context.setCurrentRenderedItem(material);
            emit.run();
        } finally { context.setCurrentRenderedItem(previous); }
    }
}
