package io.github.SirWashington;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.SirWashington.mixin.BlockPropertiesAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Immutable selection snapshot: state definitions cannot change on a config/datapack reload. */
public final class EarlyWaterloggingRules {
    public static final List<String> DEFAULT_EXCLUDED = List.of(
            "minecraft:barrier", "minecraft:beacon", "#minecraft:leaves",
            "#minecraft:shulker_boxes", "#minecraft:walls", "#c:glass_panes");
    private final List<Predicate<String>> excluded = new ArrayList<>();
    private final List<Predicate<String>> included = new ArrayList<>();
    private final Map<String, Set<String>> tags = new HashMap<>();
    private final boolean debug;
    private final int debugStateThreshold;

    EarlyWaterloggingRules(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (CommentedFileConfig config = CommentedFileConfig.of(file)) {
                config.load();
                boolean changed = false;
                if (!config.contains("excluded_blocks")) {
                    config.set("excluded_blocks", DEFAULT_EXCLUDED);
                    config.setComment("excluded_blocks", "Early exclusions: block IDs, @modid, #bundled:tag or * wildcards. Full game restart required; client and server must match.");
                    changed = true;
                }
                if (!config.contains("included_blocks")) {
                    config.set("included_blocks", List.of());
                    config.setComment("included_blocks", "Overrides exclusions for supported blocks. Full game restart required.");
                    changed = true;
                }
                if (!config.contains("debug")) {
                    config.set("debug", false);
                    config.setComment("debug", "Log blocks whose final state count exceeds debug_state_threshold during startup. Full game restart required.");
                    changed = true;
                }
                if (!config.contains("debug_state_threshold")) {
                    config.set("debug_state_threshold", 6480);
                    config.setComment("debug_state_threshold", "Only log state counts strictly above this nonnegative threshold when debug is true.");
                    changed = true;
                }
                Object debugValue = config.get("debug");
                Object threshold = config.get("debug_state_threshold");
                if (!(debugValue instanceof Boolean enabled)) throw new IllegalArgumentException("debug must be a boolean");
                if (!(threshold instanceof Integer || threshold instanceof Long)
                        || ((Number) threshold).longValue() < 0 || ((Number) threshold).longValue() > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("debug_state_threshold must be an integer from 0 to " + Integer.MAX_VALUE);
                debug = enabled;
                debugStateThreshold = ((Number) threshold).intValue();
                read(config.get("excluded_blocks"), excluded);
                read(config.get("included_blocks"), included);
                if (changed) config.save();
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read early waterlogging rules from " + file, e);
        }
    }

    private static final class Startup {
        private static final EarlyWaterloggingRules RULES = new EarlyWaterloggingRules(
                FabricLoader.getInstance().getConfigDir().resolve("immersivefluids/waterlogging.toml"));
    }

    public static boolean excludes(Block block) {
        var key = ((BlockPropertiesAccessor) block.properties()).immersivefluids$getId();
        if (key == null) return false;
        return Startup.RULES.excludes(key.identifier().toString());
    }

    boolean excludes(String id) {
        return excluded.stream().anyMatch(rule -> rule.test(id))
                && included.stream().noneMatch(rule -> rule.test(id));
    }

    boolean shouldLogStateCount(int count) {
        return debug && count > debugStateThreshold;
    }

    public static void logStateCount(Block block, int count) {
        if (!Startup.RULES.shouldLogStateCount(count)) return;
        var key = ((BlockPropertiesAccessor) block.properties()).immersivefluids$getId();
        if (key != null) org.slf4j.LoggerFactory.getLogger("immersivefluids").info(
                "Waterlogging state debug: {} has {} states (threshold: {})",
                key.identifier(), count, Startup.RULES.debugStateThreshold);
    }

    private void read(Object value, List<Predicate<String>> target) {
        if (value == null) return;
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Block selectors must be a TOML list");
        for (Object entry : list) {
            if (!(entry instanceof String selector)) throw new IllegalArgumentException("Block selectors must be strings");
            target.add(selector(selector));
        }
    }

    private Predicate<String> selector(String selector) {
        if (selector.startsWith("#")) {
            String tag = selector.substring(1);
            validate(tag, false);
            return tag(tag, new HashSet<>())::contains;
        }
        if (selector.startsWith("@")) selector = selector.substring(1) + ":*";
        validate(selector, true);
        Pattern pattern = Pattern.compile(Arrays.stream(selector.split("\\*", -1))
                .map(Pattern::quote).reduce((a, b) -> a + ".*" + b).orElseThrow());
        return id -> pattern.matcher(id).matches();
    }

    private static void validate(String id, boolean wildcard) {
        if (!id.matches(wildcard ? "[a-z0-9_.*-]+:[a-z0-9_./*-]+" : "[a-z0-9_.-]+:[a-z0-9_./-]+")
                || id.contains("..")) throw new IllegalArgumentException("Invalid block selector: " + id);
    }

    private Set<String> tag(String id, Set<String> visiting) {
        validate(id, false);
        if (tags.containsKey(id)) return tags.get(id);
        if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic early block tag: " + id);
        String[] parts = id.split(":", 2);
        String resource = "data/" + parts[0] + "/tags/block/" + parts[1] + ".json";
        Set<String> result = new HashSet<>();
        // Startup has no world resource manager: only vanilla and installed mod base resources apply.
        Set<java.net.URL> sources = new LinkedHashSet<>();
        try {
            var mods = FabricLoader.getInstance().getAllMods().stream()
                    .sorted(Comparator.comparing(mod -> mod.getMetadata().getId().equals("minecraft")
                            ? "" : mod.getMetadata().getId())).toList();
            for (var mod : mods) {
                for (Path root : mod.getRootPaths()) {
                    Path path = root.resolve(resource);
                    if (Files.exists(path)) sources.add(path.toUri().toURL());
                }
            }
            if (sources.isEmpty()) sources.addAll(Collections.list(
                    EarlyWaterloggingRules.class.getClassLoader().getResources(resource)));
            if (sources.isEmpty()) throw new IllegalArgumentException("No bundled block tag found: #" + id);
            for (var source : sources) {
                try (var reader = new java.io.InputStreamReader(source.openStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("replace") && json.get("replace").getAsBoolean()) result.clear();
                    for (JsonElement entry : json.getAsJsonArray("values")) {
                        String value = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
                        if (value.startsWith("#")) {
                            try { result.addAll(tag(value.substring(1), visiting)); }
                            catch (IllegalArgumentException e) {
                                if (!entry.isJsonObject() || !entry.getAsJsonObject().has("required")
                                        || entry.getAsJsonObject().get("required").getAsBoolean()) throw e;
                            }
                        } else result.add(value);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read early block tag #" + id, e);
        } finally { visiting.remove(id); }
        tags.put(id, Set.copyOf(result));
        return tags.get(id);
    }
}
