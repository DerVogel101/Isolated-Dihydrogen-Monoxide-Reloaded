package io.github.SirWashington;

import io.github.SirWashington.compat.iris.ShaderSourcePatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShaderSelfTest {
    public static void main(String[] args) throws Exception {
        expect(ShaderSourcePatch.replacements(Map.of()).isEmpty(), "Unrelated shaders stay untouched");
        expect(ShaderSourcePatch.replacements(Map.of(ShaderSourcePatch.BE, "unknown version")).isEmpty(), "Incomplete profiles rejected");
        expect(ShaderSourcePatch.hash(" a\r\nb ").equals(ShaderSourcePatch.hash("ab")), "Line ending independence");
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
        expect(replacements.size() == 2, "Both adjustments prepared together");
        expect(sources.equals(original), "Original shader source never mutated");
        expect(replacements.get(ShaderSourcePatch.BE).contains("blockEntityId == 29990"), "Machinery dispatch");
        String machinery = replacements.get(ShaderSourcePatch.BE);
        int untinted = machinery.indexOf("color.rgb = texture2D(tex, texCoord).rgb;");
        int iron = machinery.indexOf("#include \"" + ShaderSourcePatch.IRON);
        int tinted = machinery.indexOf("color.rgb *= glColor.rgb;");
        expect(untinted >= 0 && untinted < iron && iron < tinted, "Iron gloss is resolved before applying machinery tint");
        expect(replacements.get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29992"), "Item ice dispatch");
        sources.put(ShaderSourcePatch.BLOCK_PROGRAM, sources.get(ShaderSourcePatch.BLOCK_PROGRAM) + "\n// unknown revision");
        expect(ShaderSourcePatch.replacements(sources).isEmpty(), "Unknown revision rejected atomically");
        sources.putAll(original);
        expect(ShaderSourcePatch.replacements(sources).size() == 2, "Reload does not retain failed profile state");
        sources.put(ShaderSourcePatch.ITEM, sources.get(ShaderSourcePatch.ITEM)
                .replace("if (currentRenderedItemId < 45000)", "if( currentRenderedItemId<45000 )"));
        expect(ShaderSourcePatch.replacements(sources).get(ShaderSourcePatch.ITEM).contains("currentRenderedItemId == 29992"),
                "Whitespace-tolerant signature still installs item dispatch");
        System.out.println("SHADER_SOURCE_PROFILE_PASS " + profile);
    }
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
