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
        if (Boolean.getBoolean("immersivefluids.shaderProbeIntegrated") && !supported)
            throw new AssertionError("Selected test shader was not recognized");
        try {
            for (boolean integrated : new boolean[]{false, true}) {
                IrisMaterials.supported = integrated;
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
                    expect(ids.containsKey(ModBlocks.WATER_PUMP.defaultBlockState()) == integrated,
                            "Private machinery dispatch requires an integrated-material adapter");
                    ids.put(ModBlocks.FINITE_WATER.defaultBlockState(), 88);
                    IrisMaterials.mapBlocks(ids);
                    expect(ids.getInt(ModBlocks.FINITE_WATER.defaultBlockState()) == 88, "Explicit finite-water support preserved");
                }
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
        ShaderCameraProbe.run();
        MachineryVertexProbe.run();
        var atlas = net.minecraft.client.Minecraft.getInstance().getAtlasManager();
        var ids = net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (Boolean.getBoolean("immersivefluids.shaderProbeLabPbr")) {
            var machinery = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "block/machinery_iron")));
            var pbr = ((net.irisshaders.iris.pbr.texture.SpriteContentsExtension) machinery.contents()).getPBRHolder();
            expect(pbr != null && pbr.getNormalSprite() != null && pbr.getSpecularSprite() != null,
                    "Iris loads both machinery LabPBR maps");
            expect(pbr.getNormalSprite().contents().name().getPath().endsWith("machinery_iron_n")
                    && pbr.getSpecularSprite().contents().name().getPath().endsWith("machinery_iron_s"),
                    "Machinery uses its own maps rather than Iris fallback materials");
            System.out.println("MACHINERY_LABPBR_ATLAS_PASS");
        }
        for (String texture : new String[]{"iron_block", "copper_block", "blue_ice", "amethyst_block", "black_concrete", "white_wool", "honeycomb_block"}) {
            var sprite = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                    net.minecraft.resources.Identifier.withDefaultNamespace("block/"+texture)));
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(texture));
            int expected = ids.getOrDefault(block.defaultBlockState(), -1);
            if (IrisMaterials.supported) expect(expected > 0, "Integrated terrain material " + texture);
            expect(IrisMaterials.surface(sprite, -99) == (expected == -1 ? -99 : expected), "Active per-sprite material " + texture);
        }
        for (var pair : new net.minecraft.world.level.block.Block[][]{
                {ModBlocks.FINITE_WATER, Blocks.WATER}, {ModBlocks.FINITE_ICE, Blocks.ICE},
                {ModBlocks.LAYERED_FINITE_ICE, Blocks.ICE}}) {
            int expected = ids.getOrDefault(pair[1].defaultBlockState(), -1);
            expect(expected != -1, "Test shader supplies the vanilla water/ice reference");
            expect(ids.getOrDefault(pair[0].defaultBlockState(), -1) == expected, "Active water/ice alias");
        }
        var ice = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.Identifier.withDefaultNamespace("block/ice")));
        var iron = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.Identifier.withDefaultNamespace("block/iron_block")));
        var machineryIron = atlas.get(new net.minecraft.client.resources.model.sprite.SpriteId(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WaterPhysics.MODID, "block/machinery_iron")));
        if (IrisMaterials.supported) {
            expect(IrisMaterials.blockSurface(ModBlocks.WATER_PUMP.defaultBlockState(), machineryIron, IrisMaterials.MACHINERY)
                    == IrisMaterials.MACHINERY, "Static machinery retains untinted integrated-material dispatch");
            expect(IrisMaterials.blockSurface(ModBlocks.WATER_PUMP.defaultBlockState(), machineryIron, 77)
                    == 77, "Explicit shader machinery mapping takes precedence");
        }
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
