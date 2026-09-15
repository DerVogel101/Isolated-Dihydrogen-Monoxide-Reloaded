package io.github.SirWashington.compat.iris;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure, all-or-nothing source transformation. Never reads or writes shader archives. */
public final class ShaderSourcePatch {
    public static final String BE = "/lib/materials/materialHandling/blockEntityIPBR.glsl";
    public static final String TERRAIN = "/lib/materials/materialHandling/terrainIPBR.glsl";
    public static final String TERRAIN_PROGRAM = "/program/gbuffers_terrain.glsl";
    public static final String ITEM = "/lib/materials/materialHandling/irisIPBR.glsl";
    public static final String TRANSLUCENT = "/lib/materials/materialHandling/translucentIPBR.glsl";
    public static final String IRON = "/lib/materials/specificMaterials/terrain/ironBlock.glsl";
    public static final String BLOCK_PROGRAM = "/program/gbuffers_block.glsl";
    public static final String LIGHT_VOXELIZATION = "/lib/voxelization/lightVoxelization.glsl";
    public static final String BLOCKLIGHT_COLORS = "/lib/colors/blocklightColors.glsl";
    public static final String BLOCKLIGHT_COLORS_ACT = "/lib/colors/blocklightColorsACT.glsl";

    private static final Pattern ITEM_BRANCH = Pattern.compile(
            "if\\s*\\(\\s*currentRenderedItemId\\s*<\\s*45000\\s*\\)");
    private static final Pattern VOXEL_FUNCTION = Pattern.compile("\\bint\\s+GetVoxelIDs\\s*\\(");
    private static final Pattern COLOR_FUNCTION = Pattern.compile("\\bvec4\\s+GetSpecialBlocklightColor\\s*\\(");

    private ShaderSourcePatch() {
    }

    public static String profile(Map<String, String> sources) {
        // Check the interface we inject into, not a particular release's entire source.
        String program = code(sources.getOrDefault(BLOCK_PROGRAM, ""));
        String iron = code(sources.getOrDefault(IRON, ""));
        String be = code(sources.getOrDefault(BE, ""));
        String item = stripComments(sources.getOrDefault(ITEM, ""));
        String terrain = code(sources.getOrDefault(TERRAIN, ""));
        String terrainProgram = code(sources.getOrDefault(TERRAIN_PROGRAM, ""));
        String voxelSource = stripComments(sources.getOrDefault(LIGHT_VOXELIZATION, ""));
        String voxel = code(voxelSource);
        String colorsPath = blocklightColorsPath(sources);
        String colorsSource = colorsPath.isEmpty() ? "" : stripComments(sources.get(colorsPath));
        String colors = code(colorsSource);

        if (!terrain.contains("mat>=10000")
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
                || ITEM_BRANCH.matcher(item).results().count() != 1
                || VOXEL_FUNCTION.matcher(voxelSource).results().count() != 1
                || COLOR_FUNCTION.matcher(colorsSource).results().count() != 1
                || !voxel.contains("mat==32016") || !voxel.contains("return4;")
                || !colors.contains("mat==4")
                || terrain.contains(Integer.toString(IrisMaterials.ANTI_RAIN_ON))
                || terrain.contains(Integer.toString(IrisMaterials.ANTI_RAIN_OFF))
                || voxel.contains("return98;") || voxel.contains("return99;")
                || colors.contains("mat==98") || colors.contains("mat==99")) return "";
        return "Complementary/Euphoria material and colored-light interface";
    }

    private static String blocklightColorsPath(Map<String, String> sources) {
        if (COLOR_FUNCTION.matcher(stripComments(sources.getOrDefault(BLOCKLIGHT_COLORS, "")))
                .results().count() == 1) return BLOCKLIGHT_COLORS;
        if (COLOR_FUNCTION.matcher(stripComments(sources.getOrDefault(BLOCKLIGHT_COLORS_ACT, "")))
                .results().count() == 1) return BLOCKLIGHT_COLORS_ACT;
        return "";
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
    }

    private static String code(String text) {
        return stripComments(text).replaceAll("\\s", "");
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
        String itemDispatch = "if (currentRenderedItemId == " + IrisMaterials.ICE_ITEM + ") {\n"
                + "smoothnessG = pow2(color.g) * color.g;\n"
                + "highlightMult = pow2(min1(pow2(color.g) * 1.5)) * 3.5;\n"
                + "smoothnessD = smoothnessG;\n"
                + "} else if (currentRenderedItemId == " + IrisMaterials.ANTI_RAIN_ON
                + " || currentRenderedItemId == " + IrisMaterials.ANTI_RAIN_OFF + ") {\n"
                + "smoothnessG = 0.7;\n"
                + "smoothnessD = 0.7;\n"
                + "highlightMult = 2.0;\n"
                + "emission = currentRenderedItemId == " + IrisMaterials.ANTI_RAIN_ON + " ? 3.5 : 0.35;\n"
                + "} else ";
        String item = ITEM_BRANCH.matcher(stripComments(sources.get(ITEM))).replaceFirst(
                Matcher.quoteReplacement(itemDispatch + "if (currentRenderedItemId < 45000)"));

        String terrain = "// Immersive Fluids: powered anti-rain core and atlas-backed machinery\n"
                + "if (mat == " + IrisMaterials.ANTI_RAIN_ON + " || mat == "
                + IrisMaterials.ANTI_RAIN_OFF + ") {\n"
                + "smoothnessG = 0.7;\n"
                + "smoothnessD = 0.7;\n"
                + "highlightMult = 2.0;\n"
                + "emission = mat == " + IrisMaterials.ANTI_RAIN_ON + " ? 3.5 : 0.35;\n"
                + "} else if (mat == " + IrisMaterials.MACHINERY + ") {\n"
                + "color.rgb = texture2D(tex, texCoord).rgb;\n"
                + "#include \"" + IRON + "\"\n"
                + "color.rgb *= glColor.rgb;\n} else {\n" + sources.get(TERRAIN) + "\n}\n";

        String voxel = VOXEL_FUNCTION.matcher(sources.get(LIGHT_VOXELIZATION)).replaceFirst(
                Matcher.quoteReplacement("int immersivefluidsOriginalGetVoxelIDs(int mat);\n"
                        + "int GetVoxelIDs(int mat) {\n"
                        + "    if (mat == " + IrisMaterials.ANTI_RAIN_ON + ") return 98;\n"
                        + "    if (mat == " + IrisMaterials.ANTI_RAIN_OFF + ") return 99;\n"
                        + "    return immersivefluidsOriginalGetVoxelIDs(mat);\n}\n"
                        + "int immersivefluidsOriginalGetVoxelIDs("));

        String colorsPath = blocklightColorsPath(sources);
        String colors = COLOR_FUNCTION.matcher(sources.get(colorsPath)).replaceFirst(
                Matcher.quoteReplacement("vec4 immersivefluidsOriginalBlocklightColor(int mat);\n"
                        + "vec4 GetSpecialBlocklightColor(int mat) {\n"
                        + "    if (mat == 98) return vec4(0.61, 1.95, 6.0, 0.0);\n"
                        + "    if (mat == 99) return vec4(4.0, 4.0, 4.0, 0.0);\n"
                        + "    return immersivefluidsOriginalBlocklightColor(mat);\n}\n"
                        + "vec4 immersivefluidsOriginalBlocklightColor("));

        return Map.of(BE, be, ITEM, item, TERRAIN, terrain,
                LIGHT_VOXELIZATION, voxel, colorsPath, colors);
    }
}
