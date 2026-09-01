package io.github.SirWashington;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class WaterPhysicsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaterPhysics.MODID);
    private static final String FILE_NAME = WaterPhysics.MODID + ".properties";
    private static final String MAX_DEPTH_KEY = "piston_pressure.max_depth";
    private static final String MAX_VISITED_KEY = "piston_pressure.max_visited_water_cells";
    private static final int DEFAULT_MAX_DEPTH = 8;
    private static final int DEFAULT_MAX_VISITED = 64;

    private static int pistonPressureMaxDepth = DEFAULT_MAX_DEPTH;
    private static int pistonPressureMaxVisitedWaterCells = DEFAULT_MAX_VISITED;

    private WaterPhysicsConfig() {
    }

    public static synchronized void load(Path configDirectory) {
        Path path = configDirectory.resolve(FILE_NAME);
        Properties properties = new Properties();
        boolean existed = Files.exists(path);

        try {
            Files.createDirectories(configDirectory);
            if (existed) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }

            pistonPressureMaxDepth = readPositive(properties, MAX_DEPTH_KEY, DEFAULT_MAX_DEPTH);
            pistonPressureMaxVisitedWaterCells = readPositive(properties, MAX_VISITED_KEY, DEFAULT_MAX_VISITED);

            if (!existed
                    || !Integer.toString(pistonPressureMaxDepth).equals(properties.getProperty(MAX_DEPTH_KEY))
                    || !Integer.toString(pistonPressureMaxVisitedWaterCells).equals(properties.getProperty(MAX_VISITED_KEY))) {
                properties.setProperty(MAX_DEPTH_KEY, Integer.toString(pistonPressureMaxDepth));
                properties.setProperty(MAX_VISITED_KEY, Integer.toString(pistonPressureMaxVisitedWaterCells));
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Immersive Fluids server configuration");
                }
            }
        } catch (IOException exception) {
            pistonPressureMaxDepth = DEFAULT_MAX_DEPTH;
            pistonPressureMaxVisitedWaterCells = DEFAULT_MAX_VISITED;
            LOGGER.error("Could not load {}; using piston-pressure defaults", path, exception);
        }
    }

    public static int pistonPressureMaxDepth() {
        return pistonPressureMaxDepth;
    }

    public static int pistonPressureMaxVisitedWaterCells() {
        return pistonPressureMaxVisitedWaterCells;
    }

    private static int readPositive(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
            LOGGER.warn("Invalid value '{}' for {}; using {}", value, key, fallback);
        }
        return fallback;
    }
}
