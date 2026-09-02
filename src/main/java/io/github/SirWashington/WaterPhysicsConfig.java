package io.github.SirWashington;

import com.mrcrayfish.framework.api.config.ConfigProperty;
import com.mrcrayfish.framework.api.config.ConfigType;
import com.mrcrayfish.framework.api.config.FrameworkConfig;
import com.mrcrayfish.framework.api.config.IntProperty;

public final class WaterPhysicsConfig {
    @FrameworkConfig(id = WaterPhysics.MODID, name = "server", type = ConfigType.SERVER)
    public static final Values SERVER = new Values();

    private WaterPhysicsConfig() {
    }

    public static int pistonPressureMaxDepth() {
        return SERVER.pistonPressure.maxDepth.get();
    }

    public static int pistonPressureMaxVisitedWaterCells() {
        return SERVER.pistonPressure.maxVisitedWaterCells.get();
    }

    public static final class Values {
        @ConfigProperty(name = "piston_pressure", comment = "Limits for piston pressure searches")
        public final PistonPressure pistonPressure = new PistonPressure();
    }

    public static final class PistonPressure {
        @ConfigProperty(name = "max_depth", comment = "Maximum pressure-search path length")
        public final IntProperty maxDepth = IntProperty.create(8, 1, Integer.MAX_VALUE);

        @ConfigProperty(name = "max_visited_water_cells", comment = "Maximum finite-water cells visited per search")
        public final IntProperty maxVisitedWaterCells = IntProperty.create(64, 1, Integer.MAX_VALUE);
    }
}
