package dervogel101.de.pumpedupwater;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dervogel101.de.pumpedupwater.ConfigProperties.AbstractProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.BoolProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.ConfigProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.DoubleProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.EnumProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.IntProperty;
import dervogel101.de.pumpedupwater.ConfigProperties.ListProperty;
import dervogel101.de.pumpedupwater.block.ModBlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WaterPhysicsConfig {
    public static final Values SERVER = new Values();

    private WaterPhysicsConfig() {
    }

    public static int pistonPressureMaxDepth() {
        return get(SERVER.pistonPressure.maxDepth);
    }

    public static int pumpMaxDepth() { return get(SERVER.pump.maxDepth); }
    public static int pumpMaxVisitedWaterCells() { return get(SERVER.pump.maxVisitedWaterCells); }
    public static int pumpTickInterval() { return get(SERVER.pump.tickInterval); }
    public static int pumpWaterUnitsPerCycle() { return get(SERVER.pump.waterUnitsPerCycle); }

    public static boolean dripstoneEnabled() {
        return get(SERVER.dripstone.enabled);
    }

    public static boolean cropFertilizationEnabled() {
        return get(SERVER.cropFertilization.enabled);
    }

    public static double cropGrowthSpeedIncrease() {
        return get(SERVER.cropFertilization.growthSpeedIncrease);
    }

    public static double dripstoneFillChance() {
        return get(SERVER.dripstone.fillChance);
    }

    public static boolean rainfallEnabled() {
        return get(SERVER.rainfall.enabled);
    }

    public static double rainfallChangeChance() {
        return get(SERVER.rainfall.rainChangeChance);
    }

    public static double dayEvaporationChance() {
        return get(SERVER.rainfall.dayEvaporationChance);
    }

    public static boolean rainfallLimitedToPlayerRadius() {
        return get(SERVER.rainfall.limitToPlayerRadius);
    }

    public static int rainfallPlayerRadiusChunks() {
        return get(SERVER.rainfall.playerRadiusChunks);
    }

    public static OutsidePlayerRadiusBehavior rainfallOutsidePlayerRadiusBehavior() {
        return get(SERVER.rainfall.outsidePlayerRadiusBehavior);
    }

    public static boolean isRainCollectionSurface(BlockState state) {
        return matchesConfiguredBlocks(
                state,
                get(SERVER.rainfall.collectionIncludedBlocks),
                get(SERVER.rainfall.collectionExcludedBlocks)
        );
    }

    public static boolean isRainAbsorbingSurface(BlockState state) {
        return matchesConfiguredBlocks(
                state,
                get(SERVER.rainfall.absorptionIncludedBlocks),
                get(SERVER.rainfall.absorptionExcludedBlocks)
        );
    }

    public static int pistonPressureMaxVisitedWaterCells() {
        return get(SERVER.pistonPressure.maxVisitedWaterCells);
    }

    public static int flowTickDelay() {
        return get(SERVER.flow.tickDelay);
    }

    public static int puddleSearchRadius() {
        return get(SERVER.flow.puddleSearchRadius);
    }

    public static int extendedDrainMaxPathLength() {
        return Math.max(puddleSearchRadius(), get(SERVER.flow.extendedDrainMaxPathLength));
    }

    public static int extendedDrainMaxVisitedCells() {
        return get(SERVER.flow.extendedDrainMaxVisitedCells);
    }

    public static boolean isConfiguredExtendedDrainPath(BlockState state) {
        List<String> selectors = get(SERVER.flow.extendedDrainPathBlocks);
        for (int i = 0; i < selectors.size(); i++) {
            if (ModBlockTags.matchesSelector(state, selectors.get(i))) return true;
        }
        return false;
    }

    private static boolean matchesConfiguredBlocks(BlockState state, List<String> included, List<String> excluded) {
        for (String selector : excluded) {
            if (ModBlockTags.matchesSelector(state, selector)) {
                return false;
            }
        }
        for (String selector : included) {
            if (ModBlockTags.matchesSelector(state, selector)) {
                return true;
            }
        }
        return false;
    }

    public static int extinguishingMinimumLevel() {
        return get(SERVER.extinguishing.minimumLevel);
    }

    public static boolean doorPressureEnabled() {
        return get(SERVER.doorPressure.enabled);
    }

    public static int doorPressureRequiredLevelPerHalf() {
        return get(SERVER.doorPressure.requiredLevelPerHalf);
    }

    public static boolean currentsEnabled() {
        return get(SERVER.currents.enabled);
    }

    public static int currentDurationTicks() {
        return get(SERVER.currents.durationTicks);
    }

    public static double horizontalCurrentStrength() {
        return get(SERVER.currents.horizontalStrength);
    }

    public static double upwardCurrentStrength() {
        return get(SERVER.currents.upwardStrength);
    }

    public static double downwardCurrentStrength() {
        return get(SERVER.currents.downwardStrength);
    }

    public static double maxHorizontalCurrentSpeed() {
        return get(SERVER.currents.maxHorizontalSpeed);
    }

    public static double maxUpwardCurrentSpeed() {
        return get(SERVER.currents.maxUpwardSpeed);
    }

    public static double maxDownwardCurrentSpeed() {
        return get(SERVER.currents.maxDownwardSpeed);
    }

    private static <T> T get(AbstractProperty<T> property) { return property.get(); }

    public static void loadServer(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
                config.load();
                boolean changed = false;
                List<Runnable> updates = new ArrayList<>();
                for (Field groupField : Values.class.getFields()) {
                    ConfigProperty groupInfo = groupField.getAnnotation(ConfigProperty.class);
                    if (groupInfo == null) continue;
                    Object group = groupField.get(SERVER);
                    boolean newGroup = !config.contains(groupInfo.name());
                    for (Field field : group.getClass().getFields()) {
                        ConfigProperty info = field.getAnnotation(ConfigProperty.class);
                        if (info == null) continue;
                        AbstractProperty<?> property = (AbstractProperty<?>) field.get(group);
                        List<String> key = List.of(groupInfo.name(), info.name());
                        if (!config.contains(key)) {
                            config.set(key, property.tomlDefault());
                            config.setComment(key, info.comment());
                            changed = true;
                        }
                        try {
                            Object value = property.parse(config.get(key));
                            updates.add(() -> property.setParsed(value));
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Invalid setting " + String.join(".", key) + ": " + e.getMessage(), e);
                        }
                    }
                    if (newGroup) config.setComment(groupInfo.name(), groupInfo.comment());
                }
                if (changed) config.save();
                updates.forEach(Runnable::run);
            }
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot load server config from " + file, e);
        }
    }

    public static final class Values {
        @ConfigProperty(name = "crop_fertilization", comment = "Growth of farmland crops submerged in finite-water levels 1-2")
        public final CropFertilization cropFertilization = new CropFertilization();

        @ConfigProperty(name = "dripstone", comment = "Finite-water production from hanging pointed dripstone")
        public final Dripstone dripstone = new Dripstone();

        @ConfigProperty(name = "rainfall", comment = "Finite-water production, absorption, and evaporation from weather")
        public final Rainfall rainfall = new Rainfall();

        @ConfigProperty(name = "flow", comment = "Finite-water flow timing and drain searches")
        public final Flow flow = new Flow();

        @ConfigProperty(name = "extinguishing", comment = "Finite-water extinguishing behavior")
        public final Extinguishing extinguishing = new Extinguishing();

        @ConfigProperty(name = "door_pressure", comment = "Water pressure opening configured doors")
        public final DoorPressure doorPressure = new DoorPressure();

        @ConfigProperty(name = "currents", comment = "Entity currents produced by moving finite water")
        public final Currents currents = new Currents();


        @ConfigProperty(name = "piston_pressure", comment = "Limits for piston pressure searches")
        public final PistonPressure pistonPressure = new PistonPressure();

        @ConfigProperty(name = "pump", comment = "Powered finite-water pumps; search limits add per powered stage in series")
        public final Pump pump = new Pump();
    }

    public static final class Flow {
        @ConfigProperty(name = "tick_delay", comment = "Ticks between finite-water updates")
        public final IntProperty tickDelay = IntProperty.create(2, 1, 20);

        @ConfigProperty(name = "puddle_search_radius", comment = "Normal horizontal radius used to find a downward outlet")
        public final IntProperty puddleSearchRadius = IntProperty.create(4, 0, 16);

        @ConfigProperty(name = "extended_drain_max_path_length", comment = "Maximum path length when the extended_drain_path block tag is used")
        public final IntProperty extendedDrainMaxPathLength = IntProperty.create(32, 4, 128);

        @ConfigProperty(name = "extended_drain_max_visited_cells", comment = "Maximum cells inspected by one drain search")
        public final IntProperty extendedDrainMaxVisitedCells = IntProperty.create(256, 64, 4096);

        @ConfigProperty(name = "extended_drain_path_blocks", comment = "Block IDs or #block tags allowed to extend drain searches")
        public final ListProperty<String> extendedDrainPathBlocks = ListProperty.create(
                ListProperty.STRING,
                () -> List.of("#minecraft:slabs", "#minecraft:stairs")
        );
    }

    public static final class Dripstone {
        @ConfigProperty(name = "enabled", comment = "Allow level-8 finite water above the support block to supply drips")
        public final BoolProperty enabled = BoolProperty.create(true);

        @ConfigProperty(name = "fill_chance", comment = "Chance per random tick to add one finite-water unit below; default matches vanilla water cauldron filling; zero disables accumulation")
        public final DoubleProperty fillChance = DoubleProperty.create(0.17578125D, 0.0D, 1.0D);
    }

    public static final class Rainfall {
        @ConfigProperty(name = "enabled", comment = "Allow finite water levels 0-3 to change through rain, absorption, and evaporation; randomTickSpeed affects covered water")
        public final BoolProperty enabled = BoolProperty.create(true);

        @ConfigProperty(name = "limit_to_player_radius", comment = "Limit rainfall changes around players; outside behavior is controlled below")
        public final BoolProperty limitToPlayerRadius = BoolProperty.create(true);

        @ConfigProperty(name = "player_radius_chunks", comment = "Square chunk radius around same-dimension players; zero means their current chunks only")
        public final IntProperty playerRadiusChunks = IntProperty.create(6, 0, Integer.MAX_VALUE);

        @ConfigProperty(name = "outside_player_radius_behavior", comment = "DISAPPEAR_ONLY prevents collection; NOTHING prevents all rainfall changes")
        public final EnumProperty<OutsidePlayerRadiusBehavior> outsidePlayerRadiusBehavior =
                EnumProperty.create(OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY);

        @ConfigProperty(name = "rain_change_chance", comment = "Chance to add rain or absorb one level per eligible precipitation or covered-water random tick; maximum 0.5 keeps rain branches disjoint")
        public final DoubleProperty rainChangeChance = DoubleProperty.create(0.075D, 0.0D, 0.5D);

        @ConfigProperty(name = "day_evaporation_chance", comment = "Chance to evaporate one level per exposed clear-day sample or skylit covered-water random tick")
        public final DoubleProperty dayEvaporationChance = DoubleProperty.create(0.5D, 0.0D, 1.0D);

        @ConfigProperty(name = "collection_included_blocks", comment = "Surface block IDs, @modid, #block tags, or * wildcards that collect rain")
        public final ListProperty<String> collectionIncludedBlocks = ListProperty.create(
                ListProperty.STRING, () -> List.of("*:*")
        );

        @ConfigProperty(name = "collection_excluded_blocks", comment = "Collection exclusions using the same selectors; exclusions override inclusions")
        public final ListProperty<String> collectionExcludedBlocks = ListProperty.create(ListProperty.STRING);

        @ConfigProperty(name = "absorption_included_blocks", comment = "Surface block IDs, @modid, #block tags, or * wildcards that absorb shallow water")
        public final ListProperty<String> absorptionIncludedBlocks = ListProperty.create(
                ListProperty.STRING,
                () -> List.of(
                        "#minecraft:dirt",
                        "minecraft:grass_block",
                        "minecraft:podzol",
                        "minecraft:mycelium",
                        "minecraft:dirt_path",
                        "minecraft:farmland",
                        "minecraft:gravel",
                        "minecraft:sand",
                        "minecraft:red_sand",
                        "minecraft:moss_block"
                )
        );

        @ConfigProperty(name = "absorption_excluded_blocks", comment = "Absorption exclusions using the same selectors; exclusions override inclusions")
        public final ListProperty<String> absorptionExcludedBlocks = ListProperty.create(ListProperty.STRING);
    }

    public enum OutsidePlayerRadiusBehavior {
        DISAPPEAR_ONLY,
        NOTHING
    }

    public static final class CropFertilization {
        @ConfigProperty(name = "enabled", comment = "Allow shallow finite water to fertilize farmland crops by one stage, consuming one water level")
        public final BoolProperty enabled = BoolProperty.create(true);

        @ConfigProperty(name = "growth_speed_increase", comment = "Relative growth-rate increase while water levels 1-2 are maintained; 0.5 means 50 percent faster, 0 disables fertilization")
        public final DoubleProperty growthSpeedIncrease = DoubleProperty.create(0.5D, 0.0D, 1.0D);
    }

    public static final class Extinguishing {
        @ConfigProperty(name = "minimum_level", comment = "Minimum finite-water level that extinguishes tagged blocks")
        public final IntProperty minimumLevel = IntProperty.create(3, 1, 8);
    }

    public static final class DoorPressure {
        @ConfigProperty(name = "enabled", comment = "Whether outside water pressure can open tagged doors")
        public final BoolProperty enabled = BoolProperty.create(true);

        @ConfigProperty(name = "required_level_per_half", comment = "Required outside finite-water level beside each door half")
        public final IntProperty requiredLevelPerHalf = IntProperty.create(8, 1, 8);
    }

    public static final class Currents {
        @ConfigProperty(name = "enabled", comment = "Whether moving finite water pushes entities")
        public final BoolProperty enabled = BoolProperty.create(true);

        @ConfigProperty(name = "duration_ticks", comment = "How long a recorded current remains active")
        public final IntProperty durationTicks = IntProperty.create(10, 1, 100);

        @ConfigProperty(name = "horizontal_strength", comment = "Horizontal acceleration from a full-level transfer")
        public final DoubleProperty horizontalStrength = DoubleProperty.create(0.06D, 0.0D, 1.0D);

        @ConfigProperty(name = "upward_strength", comment = "Upward acceleration from a full-level transfer")
        public final DoubleProperty upwardStrength = DoubleProperty.create(0.06D, 0.0D, 1.0D);

        @ConfigProperty(name = "downward_strength", comment = "Downward acceleration magnitude from a full-level transfer")
        public final DoubleProperty downwardStrength = DoubleProperty.create(0.039D, 0.0D, 1.0D);

        @ConfigProperty(name = "max_horizontal_speed", comment = "Maximum horizontal speed caused by currents")
        public final DoubleProperty maxHorizontalSpeed = DoubleProperty.create(0.7D, 0.0D, 2.0D);

        @ConfigProperty(name = "max_upward_speed", comment = "Maximum upward speed caused by currents")
        public final DoubleProperty maxUpwardSpeed = DoubleProperty.create(0.7D, 0.0D, 2.0D);

        @ConfigProperty(name = "max_downward_speed", comment = "Maximum downward speed magnitude caused by currents")
        public final DoubleProperty maxDownwardSpeed = DoubleProperty.create(0.3D, 0.0D, 2.0D);
    }

    public static final class Pump {
        @ConfigProperty(name = "max_depth", comment = "Pressure-search path length per powered stage")
        public final IntProperty maxDepth = IntProperty.create(8, 1, Integer.MAX_VALUE);
        @ConfigProperty(name = "max_visited_water_cells", comment = "Water cells visited per transfer, per powered stage")
        public final IntProperty maxVisitedWaterCells = IntProperty.create(64, 1, Integer.MAX_VALUE);
        @ConfigProperty(name = "tick_interval", comment = "Ticks between pump transfers")
        public final IntProperty tickInterval = IntProperty.create(20, 1, 1200);
        @ConfigProperty(name = "water_units_per_cycle", comment = "Water levels moved per constituent pump block per cycle; 8 equals one full block. Multiplied by square area (1, 4 or 9); series add reach, not throughput")
        public final IntProperty waterUnitsPerCycle = IntProperty.create(8, 1, Integer.MAX_VALUE);
    }

    public static final class PistonPressure {
        @ConfigProperty(name = "max_depth", comment = "Maximum pressure-search path length")
        public final IntProperty maxDepth = IntProperty.create(8, 1, Integer.MAX_VALUE);

        @ConfigProperty(name = "max_visited_water_cells", comment = "Maximum finite-water cells visited per search")
        public final IntProperty maxVisitedWaterCells = IntProperty.create(64, 1, Integer.MAX_VALUE);
    }
}
