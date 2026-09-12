package io.github.SirWashington.compat.iris;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Pure, all-or-nothing source transformation. Never reads or writes shader archives. */
public final class ShaderSourcePatch {
    public static final String BE = "/lib/materials/materialHandling/blockEntityIPBR.glsl";
    public static final String ITEM = "/lib/materials/materialHandling/irisIPBR.glsl";
    public static final String TRANSLUCENT = "/lib/materials/materialHandling/translucentIPBR.glsl";
    public static final String IRON = "/lib/materials/specificMaterials/terrain/ironBlock.glsl";
    public static final String BLOCK_PROGRAM = "/program/gbuffers_block.glsl";
    private static final String[] PATHS = {BE, ITEM, TRANSLUCENT, IRON, BLOCK_PROGRAM};
    private static final String[][] SIGNATURES = {
        { // Complementary r5.9
            "378b4c31d22f342140ee5fe18c598f1fa444b2627f9ca10b4977cdde7bcf46b0",
            "411920f4fc5c08dd08f5054c8fffd613474403685135f6290c9a6f1051dca2ad",
            "b6fa1829762d4fb029c1bfeb030a463184f05b8062358c4e1ea618aabfa6cff7",
            "a9ca08e30cdf8c7a1ff8efde2bf699fdda9c83cbe8a0ee5338799aea27b17d98",
            "ced4bc4bc319539386bd9e8a745dba16fbb3e40bb6f040afa0693853ab825b71"
        },
        { // Euphoria 1.10.0 on Complementary r5.9
            "7071e1d860fff6462baaf59e5547a45400a4fbaa479641d18c8e80edbeea8794",
            "67529fa393dfee4c6a08ee512f896c63d2b9a366e91111df4e04d22fe5efaffd",
            "eae932dae57d50a25a4d8fd33bbe21fb61c1e78120236aeec4215657b278eb26",
            "a9ca08e30cdf8c7a1ff8efde2bf699fdda9c83cbe8a0ee5338799aea27b17d98",
            "a3d8bc77a78ba44539145bd4fd5b7e847b59434fe7ccc3479b781d57eff19459"
        }
    };
    private ShaderSourcePatch() { }

    public static String profile(Map<String, String> sources) {
        for (int p = 0; p < SIGNATURES.length; p++) {
            boolean matches = true;
            for (int i = 0; i < PATHS.length; i++)
                matches &= SIGNATURES[p][i].equals(hash(sources.getOrDefault(PATHS[i], "")));
            if (matches) return p == 0 ? "Complementary r5.9" : "Euphoria 1.10.0 / Complementary r5.9";
        }
        return "";
    }

    public static Map<String, String> replacements(Map<String, String> sources) {
        if (profile(sources).isEmpty()) return Map.of();
        String be = "// Immersive Fluids: atlas-backed animated iron geometry\nif (blockEntityId == "
                + IrisMaterials.MACHINERY + ") {\n"
                + "color.rgb = texture2D(tex, texCoord).rgb;\n"
                + "#include \"" + IRON + "\"\n"
                + "color.rgb *= glColor.rgb;\n} else {\n" + sources.get(BE) + "\n}\n";
        // The translucent world program has extra variables unavailable in the item program.
        // Reuse its exact ice surface response; do not import its water/refraction program.
        String ice = "if (currentRenderedItemId == " + IrisMaterials.ICE_ITEM + ") {\n"
                + "smoothnessG = pow2(color.g) * color.g;\n"
                + "highlightMult = pow2(min1(pow2(color.g) * 1.5)) * 3.5;\n"
                + "smoothnessD = smoothnessG;\n} else ";
        String item = sources.get(ITEM).replaceFirst("if\\s*\\(\\s*currentRenderedItemId\\s*<\\s*45000\\s*\\)",
                java.util.regex.Matcher.quoteReplacement(ice + "if (currentRenderedItemId < 45000)"));
        return Map.of(BE, be, ITEM, item);
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
