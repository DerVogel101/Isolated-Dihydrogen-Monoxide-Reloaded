package io.github.SirWashington;

import io.github.SirWashington.compat.iris.ShaderSourcePatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShaderSelfTest {
    public static void main(String[] args) throws Exception {
        verifyLabPbr();
        verifyAntiRainResources();
        expect(ShaderSourcePatch.replacements(Map.of()).isEmpty(), "Unrelated shaders stay untouched");
        expect(ShaderSourcePatch.replacements(Map.of(ShaderSourcePatch.BE, "unknown version")).isEmpty(), "Incomplete profiles rejected");
        for (float start : new float[]{-1.25F, 0, .9F, 1}) {
            for (float width : new float[]{.1F, .5F, 1, 2.5F}) {
                var quad = List.of(new AtlasTiling.Vertex(0,0,0,start,0), new AtlasTiling.Vertex(width,0,0,start+width,0),
                        new AtlasTiling.Vertex(width,1,0,start+width,1), new AtlasTiling.Vertex(0,1,0,start,1));
                double[] area = {0};
                AtlasTiling.split(quad, tile -> {
                    expect(tile.size() == 4, "Quad output");
                    for (var p : tile) expect(p.u() >= 0 && p.u() <= 1 && p.v() >= 0 && p.v() <= 1, "No atlas bleed");
                    for (int i = 0; i < 4; i++) {
                        var a = tile.get(i); var b = tile.get((i+1)%4);
                        area[0] += (a.x()*b.y()-a.y()*b.x()) / 2.0;
                    }
                });
                expect(Math.abs(area[0]-width) < 1E-5, "Tiling preserves area and winding across repeat seams");
            }
        }
        for (String arg : args) verifyPack(Path.of(arg));
        System.out.println("SHADER_SELF_TEST_PASS");
    }
    private static void verifyPack(Path root) throws Exception {
        var sources = new HashMap<String,String>();
        try (var paths = Files.walk(root)) {
            for (var file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".glsl")).toList())
                sources.put("/" + root.relativize(file).toString().replace('\\','/'), Files.readString(file));
        }
        String profile = ShaderSourcePatch.profile(sources);
        expect(!profile.isEmpty(), "Expected a supported shader fixture: " + root);
        var original = Map.copyOf(sources);
        var replacements = ShaderSourcePatch.replacements(sources);
        expect(replacements.size() == 5, "All adjustments prepared together");
        expect(sources.equals(original), "Original shader source never mutated");
        expect(replacements.get(ShaderSourcePatch.BE).contains("blockEntityId == 29990"), "Machinery dispatch");
        String machinery = replacements.get(ShaderSourcePatch.BE);
        int untinted = machinery.indexOf("color.rgb = texture2D(tex, texCoord).rgb;");
        int iron = machinery.indexOf("#include \"" + ShaderSourcePatch.IRON);
        int tinted = machinery.indexOf("color.rgb *= glColor.rgb;");
        expect(untinted >= 0 && untinted < iron && iron < tinted, "Iron gloss is resolved before applying machinery tint");
        String terrain = replacements.get(ShaderSourcePatch.TERRAIN);
        expect(terrain.contains("mat == 29990")
                && terrain.indexOf("color.rgb = texture2D") < terrain.indexOf("#include \"" + ShaderSourcePatch.IRON)
                && terrain.indexOf("#include \"" + ShaderSourcePatch.IRON) < terrain.indexOf("color.rgb *= glColor.rgb;"),
                "Static iron gloss is resolved before applying machinery tint");
        expect(replacements.get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29992"), "Item ice dispatch");
        expect(terrain.contains("mat == 29994") && terrain.contains("mat == 29996")
                        && terrain.contains("? 3.5 : 0.35"),
                "Powered and unpowered anti-rain core materials");
        expect(replacements.get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29994")
                        && replacements.get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29996"),
                "Anti-rain item core material");
        String voxel = replacements.get(ShaderSourcePatch.LIGHT_VOXELIZATION);
        expect(voxel.contains("mat == 29994") && voxel.contains("return 98")
                        && voxel.contains("mat == 29996") && voxel.contains("return 99"),
                "Anti-rain colored-light voxel dispatch");
        String colorsPath = replacements.containsKey(ShaderSourcePatch.BLOCKLIGHT_COLORS)
                ? ShaderSourcePatch.BLOCKLIGHT_COLORS : ShaderSourcePatch.BLOCKLIGHT_COLORS_ACT;
        String colors = replacements.get(colorsPath);
        expect(colors.contains("mat == 98") && colors.contains("0.3, 1.0, 8.0, 2.0")
                        && colors.contains("mat == 99") && colors.contains("3.8, 4.0, 4.3, 0.2"),
                "Deep-blue and neutral-grey colored light");
        sources.put(ShaderSourcePatch.BLOCK_PROGRAM, sources.get(ShaderSourcePatch.BLOCK_PROGRAM) + "\n// unknown revision");
        expect(ShaderSourcePatch.replacements(sources).size() == 5, "Unrelated revision changes remain supported");
        sources.put(ShaderSourcePatch.BLOCK_PROGRAM, sources.get(ShaderSourcePatch.BLOCK_PROGRAM)
                .replace(ShaderSourcePatch.BE, "/removed-material-interface.glsl"));
        expect(ShaderSourcePatch.replacements(sources).isEmpty(), "Missing material interface rejected atomically");
        sources.putAll(original);
        expect(ShaderSourcePatch.replacements(sources).size() == 5, "Reload does not retain failed profile state");
        sources.put(ShaderSourcePatch.LIGHT_VOXELIZATION,
                sources.get(ShaderSourcePatch.LIGHT_VOXELIZATION) + "\nint collision() { return 98; }");
        expect(ShaderSourcePatch.replacements(sources).isEmpty(), "Private voxel collision rejected atomically");
        sources.putAll(original);
        sources.put(ShaderSourcePatch.ITEM, sources.get(ShaderSourcePatch.ITEM)
                .replace("if (currentRenderedItemId < 45000)", "if( currentRenderedItemId<45000 )"));
        expect(ShaderSourcePatch.replacements(sources).get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29992"),
                "Whitespace-tolerant signature still installs item dispatch");
        System.out.println("SHADER_SOURCE_PROFILE_PASS " + profile);
    }
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void verifyLabPbr() throws Exception {
        String base = "/assets/immersivefluids/textures/block/machinery_iron";
        var color = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + ".png"));
        var normal = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + "_n.png"));
        var specular = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + "_s.png"));
        expect(normal.getWidth() == color.getWidth() && specular.getWidth() == color.getWidth()
                && normal.getHeight() == color.getHeight() && specular.getHeight() == color.getHeight(), "PBR atlas dimensions");
        for (int y = 0; y < color.getHeight(); y++) for (int x = 0; x < color.getWidth(); x++) {
            expect(normal.getRGB(x, y) == 0xFF8080FF, "Flat LabPBR normal, full AO and height");
            int pixel = specular.getRGB(x, y), smoothness = pixel >>> 16 & 255;
            expect((pixel & 0xFF00FFFF) == 0xFF00E600 && smoothness >= 110 && smoothness <= 190,
                    "LabPBR iron, bounded smoothness and no emission");
        }
        System.out.println("MACHINERY_LABPBR_TEXTURE_PASS");
    }

    private static void verifyAntiRainResources() throws Exception {
        for (String name : List.of("beacon", "beacon_off")) {
            String base = "/assets/immersivefluids/textures/block/" + name;
            var color = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + ".png"));
            var normal = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + "_n.png"));
            var specular = javax.imageio.ImageIO.read(ShaderSelfTest.class.getResource(base + "_s.png"));
            expect(color.getWidth() == 16 && color.getHeight() == 16
                            && normal.getWidth() == 16 && normal.getHeight() == 16
                            && specular.getWidth() == 16 && specular.getHeight() == 16,
                    "Anti-rain textures are 16x16");
            int expectedSpecular = name.equals("beacon") ? 0xFEB40A00 : 0x20B40A00;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                expect(normal.getRGB(x, y) == 0xFF8080FF, "Anti-rain flat LabPBR normal");
                expect(specular.getRGB(x, y) == expectedSpecular, "Anti-rain dielectric and emission channels");
            }
        }
        String model;
        try (var input = ShaderSelfTest.class.getResourceAsStream(
                "/assets/immersivefluids/models/block/anti_rain_generator_off.json")) {
            model = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        expect(model.contains("minecraft:block/water_still")
                        && model.contains("minecraft:block/glass")
                        && model.contains("minecraft:block/crying_obsidian")
                        && model.contains("minecraft:block/conduit")
                        && model.contains("\"force_translucent\": true")
                        && model.contains("\"tintindex\": 0"),
                "Anti-rain model keeps vanilla material references and water tinting");
        System.out.println("ANTI_RAIN_GENERATOR_RESOURCE_PASS");
    }
}
