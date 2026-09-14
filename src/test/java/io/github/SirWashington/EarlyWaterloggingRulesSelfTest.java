package io.github.SirWashington;

import java.nio.file.Files;

public final class EarlyWaterloggingRulesSelfTest {
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("early-waterlogging-", ".toml");
        try {
            Files.writeString(file, """
                    excluded_blocks = [
                      '@railways', # namespace
                      'example:train_track_*', 'other:*_track', 'minecraft:oak_slab'
                    ]
                    included_blocks = ['railways:allowed_*', 'minecraft:oak_slab']
                    """);
            var rules = new EarlyWaterloggingRules(file);
            if (rules.shouldLogStateCount(Integer.MAX_VALUE)) throw new AssertionError("Debug must default to off");
            for (String id : new String[]{"railways:anything", "example:train_track_", "example:train_track_long", "other:train_track"}) {
                if (!rules.excludes(id)) throw new AssertionError("Missing exclusion: " + id);
            }
            for (String id : new String[]{"railways:allowed_track", "minecraft:oak_slab", "minecraft:stone", "railways2:anything", "other:track_extra"}) {
                if (rules.excludes(id)) throw new AssertionError("Unexpected exclusion: " + id);
            }
            Files.writeString(file, "excluded_blocks = []\n");
            if (!rules.excludes("railways:anything") || new EarlyWaterloggingRules(file).excludes("railways:anything"))
                throw new AssertionError("Rules must remain fixed until the next startup snapshot");
            for (String invalid : new String[]{"bad id", "@", "../outside:block", "#bad tag"}) {
                Files.writeString(file, "excluded_blocks = ['" + invalid + "']\n");
                try { new EarlyWaterloggingRules(file); throw new AssertionError("Accepted " + invalid); }
                catch (IllegalStateException expected) { }
            }
            Files.writeString(file, "excluded_blocks = []\ndebug = true\n");
            rules = new EarlyWaterloggingRules(file);
            if (rules.shouldLogStateCount(6480) || !rules.shouldLogStateCount(6481))
                throw new AssertionError("Default debug threshold must be strictly greater than 6480");
            Files.writeString(file, "excluded_blocks = []\ndebug = true\ndebug_state_threshold = 486\n");
            rules = new EarlyWaterloggingRules(file);
            if (rules.shouldLogStateCount(486) || !rules.shouldLogStateCount(487))
                throw new AssertionError("Custom debug threshold ignored");
            for (String invalid : new String[]{"debug = 'true'", "debug_state_threshold = -1", "debug_state_threshold = 1.5", "debug_state_threshold = 2147483648"}) {
                Files.writeString(file, "excluded_blocks = []\n" + invalid + "\n");
                try { new EarlyWaterloggingRules(file); throw new AssertionError("Accepted " + invalid); }
                catch (IllegalStateException expected) { }
            }
            System.out.println("EARLY_WATERLOGGING_RULES_SELF_TEST_PASS");
        } finally { Files.deleteIfExists(file); }
    }
}
