package io.github.SirWashington.compat.iris;

import java.util.Map;

/** Pure, all-or-nothing source transformation. Never reads or writes shader archives. */
public final class ShaderSourcePatch {
    public static final String BE = "/lib/materials/materialHandling/blockEntityIPBR.glsl";
    public static final String TERRAIN = "/lib/materials/materialHandling/terrainIPBR.glsl";
    public static final String TERRAIN_PROGRAM = "/program/gbuffers_terrain.glsl";
    public static final String ITEM = "/lib/materials/materialHandling/irisIPBR.glsl";
    public static final String TRANSLUCENT = "/lib/materials/materialHandling/translucentIPBR.glsl";
    public static final String IRON = "/lib/materials/specificMaterials/terrain/ironBlock.glsl";
    public static final String BLOCK_PROGRAM = "/program/gbuffers_block.glsl";
    private static final java.util.regex.Pattern ITEM_BRANCH = java.util.regex.Pattern.compile(
            "if\\s*\\(\\s*currentRenderedItemId\\s*<\\s*45000\\s*\\)");
    private ShaderSourcePatch() { }

    public static String profile(Map<String, String> sources) {
        // Check the interface we inject into, not a particular release's entire source.
        String program = code(sources.getOrDefault(BLOCK_PROGRAM, ""));
        String iron = code(sources.getOrDefault(IRON, ""));
        String be = code(sources.getOrDefault(BE, ""));
        String item = stripComments(sources.getOrDefault(ITEM, ""));
        String terrainProgram = code(sources.getOrDefault(TERRAIN_PROGRAM, ""));
        if (!code(sources.getOrDefault(TERRAIN, "")).contains("mat>=10000")
                || !terrainProgram.contains("vec4color=texture2D(tex,texCoord);")
                || !terrainProgram.contains("color.rgb*=glColor.rgb;")
                || !terrainProgram.contains("flatinintmat;")
                || !program.contains("#include\"" + BE + "\"")
                || !program.contains("vec4color=texture2D(tex,texCoord);")
                || !program.contains("color*=glColor;")
                || !program.contains("smoothnessG=") || !program.contains("smoothnessD=")
                || !program.contains("highlightMult=") || !program.contains("materialMask=")
                || !program.contains("noiseFactor=") || !program.contains("#ifdefIPBR")
                || !be.contains("blockEntityId")
                || !iron.contains("smoothnessG=") || !iron.contains("smoothnessD=")
                || !iron.contains("highlightMult=") || !iron.contains("materialMask=OSIEBCA;")
                || ITEM_BRANCH.matcher(item).results().count() != 1) return "";
        return "Complementary/Euphoria material interface";
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
    }

    private static String code(String text) { return stripComments(text).replaceAll("\\s", ""); }

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
        String item = ITEM_BRANCH.matcher(stripComments(sources.get(ITEM))).replaceFirst(
                java.util.regex.Matcher.quoteReplacement(ice + "if (currentRenderedItemId < 45000)"));
        String terrain = "// Immersive Fluids: evaluate iron before machinery tint, as in the animated path\n"
                + "if (mat == " + IrisMaterials.MACHINERY + ") {\n"
                + "color.rgb = texture2D(tex, texCoord).rgb;\n"
                + "#include \"" + IRON + "\"\n"
                + "color.rgb *= glColor.rgb;\n} else {\n" + sources.get(TERRAIN) + "\n}\n";
        return Map.of(BE, be, ITEM, item, TERRAIN, terrain);
    }

}
