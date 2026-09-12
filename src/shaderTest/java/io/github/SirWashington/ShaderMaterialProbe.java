package io.github.SirWashington;

import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.compat.iris.IrisMaterials;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Runs with the real Fabric-bootstrapped registries; no mocked block identities. */
final class ShaderMaterialProbe {
    static void run() {
        boolean supported = IrisMaterials.supported;
        if (Boolean.getBoolean("immersivefluids.shaderProbePack") && net.irisshaders.iris.Iris.getCurrentPack().isEmpty())
            throw new AssertionError("Shader test must load the requested pack");
        if (IrisApi.getInstance().isShaderPackInUse() && !supported)
            throw new AssertionError("Selected test shader was not recognized");
        try {
            IrisMaterials.supported = true;
            for (int material : new int[]{32000, 1234}) {
                var ids = new Object2IntOpenHashMap<BlockState>();
                ids.put(Blocks.WATER.defaultBlockState(), material);
                ids.put(Blocks.ICE.defaultBlockState(), material+1);
                ids.put(ModBlocks.RAIN_SENSOR.defaultBlockState(), 77);
                IrisMaterials.mapBlocks(ids);
                for (var state : ModBlocks.FINITE_WATER.getStateDefinition().getPossibleStates())
                    expect(ids.getInt(state) == material, "All finite water states follow active material IDs");
                for (var state : ModBlocks.LAYERED_FINITE_ICE.getStateDefinition().getPossibleStates())
                    expect(ids.getInt(state) == material+1, "Layered ice aliases follow reload");
                expect(ids.getInt(ModBlocks.RAIN_SENSOR.defaultBlockState()) == 77, "Native mappings preserved");
                expect(!ids.containsKey(Blocks.LAVA.defaultBlockState()), "Vanilla lava untouched");
                ids.put(ModBlocks.FINITE_WATER.defaultBlockState(), 88);
                IrisMaterials.mapBlocks(ids);
                expect(ids.getInt(ModBlocks.FINITE_WATER.defaultBlockState()) == 88, "Explicit finite-water support preserved");
            }
        } finally { IrisMaterials.supported = supported; }
        var context = net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE;
        int before = context.getCurrentRenderedItem();
        RuntimeException failure = new RuntimeException("intentional render failure");
        try {
            IrisMaterials.withItemMaterial(123, () -> {
                expect(context.getCurrentRenderedItem() == 123, "Material visible while emitting");
                IrisMaterials.withItemMaterial(456, () -> expect(context.getCurrentRenderedItem() == 456, "Nested material"));
                expect(context.getCurrentRenderedItem() == 123, "Nested emission restores outer context");
                throw failure;
            });
            throw new AssertionError("Expected render failure");
        } catch (RuntimeException actual) {
            expect(actual == failure, "Render exceptions propagate");
        }
        expect(context.getCurrentRenderedItem() == before, "Exception restores original material");
        System.out.println("SHADER_MATERIAL_REGISTRY_PASS");
    }
    static void verifyPipeline() {
        if (Boolean.getBoolean("immersivefluids.shaderProbePack") && !IrisApi.getInstance().isShaderPackInUse())
            throw new AssertionError("Shader test must not silently fall back to vanilla rendering");
        if (!IrisMaterials.active()) return;
        MachineryVertexProbe.run();
        var atlas = net.minecraft.client.Minecraft.getInstance().getAtlasManager();
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        for (String texture : new String[]{"iron_block", "copper_block", "blue_ice", "amethyst_block", "black_concrete", "white_wool", "honeycomb_block"}) {
            var sprite = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.withDefaultNamespace("block/"+texture)));
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(texture));
            int expected = ids.getOrDefault(block.defaultBlockState(), -1);
            expect(expected > 0 && IrisMaterials.surface(sprite, -99) == expected, "Active per-sprite material " + texture);
        }
        var ice = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.Identifier.withDefaultNamespace("block/ice")));
        var iron = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.Identifier.withDefaultNamespace("block/iron_block")));
        var host = Blocks.CHEST.defaultBlockState()
                .setValue(io.github.SirWashington.features.FiniteWaterloggedPlants.LEVEL, 8)
                .setValue(io.github.SirWashington.features.FrozenWaterloggedBlocks.FROZEN,
                        io.github.SirWashington.features.FrozenWaterloggedBlocks.Phase.ALL);
        expect(IrisMaterials.blockSurface(host, ice, 88) == ids.getInt(Blocks.ICE.defaultBlockState()), "Frozen overlay gets ice");
        expect(IrisMaterials.blockSurface(host, iron, 88) == 88, "Frozen host retains its own material");
        expect(IrisMaterials.blockSurface(Blocks.IRON_BLOCK.defaultBlockState(), ice, 88) == 88, "Unrelated blocks not remapped");
        System.out.println("SHADER_ACTIVE_SURFACES_PASS");
    }
    static void toggle(boolean enabled) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
    }
    private static void expect(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
