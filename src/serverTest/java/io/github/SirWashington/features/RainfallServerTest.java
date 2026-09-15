package io.github.SirWashington.features;

import com.mojang.authlib.GameProfile;
import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.ModBlockTags;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.UUID;

/** Opt-in dedicated-server regression: -PrainfallTest runServer --args="--nogui". */
public final class RainfallServerTest implements ModInitializer {
    private static final BlockPos GROUND = new BlockPos(8, 200, 8);
    private static final BlockPos WATER = GROUND.above();
    private static final BoundingBox AREA = new BoundingBox(6, 198, 6, 10, 205, 10);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            var config = WaterPhysicsConfig.SERVER.rainfall;
            boolean enabled = config.enabled.get();
            boolean limitToPlayerRadius = config.limitToPlayerRadius.get();
            int playerRadiusChunks = config.playerRadiusChunks.get();
            WaterPhysicsConfig.OutsidePlayerRadiusBehavior outsidePlayerRadiusBehavior =
                    config.outsidePlayerRadiusBehavior.get();
            double rainChance = config.rainChangeChance.get();
            double evaporationChance = config.dayEvaporationChance.get();
            List<String> collectionIncluded = List.copyOf(config.collectionIncludedBlocks.get());
            List<String> collectionExcluded = List.copyOf(config.collectionExcludedBlocks.get());
            List<String> absorptionIncluded = List.copyOf(config.absorptionIncludedBlocks.get());
            List<String> absorptionExcluded = List.copyOf(config.absorptionExcludedBlocks.get());
            float previousRain = level.getRainLevel(1.0F);
            try {
                level.setChunkForced(0, 0, true);
                configureDefaults(config);
                playerRadiusChecks(level);
                selectorChecks(level);
                rainChecks(level);
                dryingChecks(level);
                coveredDryingChecks(level);
                weatherBoundaryChecks(level);
                hostChecks(level);
                disabledChecks(level);
                System.out.println("RAINFALL_SERVER_TEST_PASS");
            } finally {
                config.enabled.set(enabled);
                config.limitToPlayerRadius.set(limitToPlayerRadius);
                config.playerRadiusChunks.set(playerRadiusChunks);
                config.outsidePlayerRadiusBehavior.set(outsidePlayerRadiusBehavior);
                config.rainChangeChance.set(rainChance);
                config.dayEvaporationChance.set(evaporationChance);
                config.collectionIncludedBlocks.set(collectionIncluded);
                config.collectionExcludedBlocks.set(collectionExcluded);
                config.absorptionIncludedBlocks.set(absorptionIncluded);
                config.absorptionExcludedBlocks.set(absorptionExcluded);
                level.setRainLevel(previousRain);
                clear(level);
                level.setChunkForced(0, 0, false);
                server.halt(false);
            }
        });
    }

    private static void configureDefaults(WaterPhysicsConfig.Rainfall config) {
        config.enabled.set(true);
        config.limitToPlayerRadius.set(false);
        config.playerRadiusChunks.set(6);
        config.outsidePlayerRadiusBehavior.set(WaterPhysicsConfig.OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY);
        config.rainChangeChance.set(0.125D);
        config.dayEvaporationChance.set(0.5D);
        config.collectionIncludedBlocks.set(List.of("*:*"));
        config.collectionExcludedBlocks.set(List.of());
        config.absorptionIncludedBlocks.set(List.of(
                "#minecraft:dirt", "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
                "minecraft:dirt_path", "minecraft:farmland", "minecraft:gravel", "minecraft:sand",
                "minecraft:red_sand", "minecraft:moss_block"
        ));
        config.absorptionExcludedBlocks.set(List.of());
    }

    private static void playerRadiusChecks(ServerLevel level) {
        var config = WaterPhysicsConfig.SERVER.rainfall;
        expect(config.limitToPlayerRadius.getDefaultValue(), "Player-radius limit defaults enabled");
        expect(config.playerRadiusChunks.getDefaultValue() == 6, "Player radius defaults to six chunks");
        expect(config.outsidePlayerRadiusBehavior.getDefaultValue()
                        == WaterPhysicsConfig.OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY,
                "Outside-radius behavior defaults to disappearance only");

        config.limitToPlayerRadius.set(true);
        config.playerRadiusChunks.set(6);
        config.outsidePlayerRadiusBehavior.set(WaterPhysicsConfig.OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY);
        fixture(level, Blocks.STONE);
        expect(level.players().isEmpty(), "Rainfall regression starts without players");
        expect(!rain(level, GROUND, 0.0D), "No player prevents rainwater creation");

        ServerPlayer player = testPlayer(level);
        try {
            level.addNewPlayer(player);
            expect(level.players().contains(player), "Test player registered in dimension");

            movePlayer(player, 6, 0);
            fixture(level, Blocks.STONE);
            expect(rain(level, GROUND, 0.0D), "Six-chunk axial boundary collects rain");

            movePlayer(player, 6, 6);
            fixture(level, Blocks.STONE);
            expect(rain(level, GROUND, 0.0D), "Six-chunk diagonal boundary collects rain");

            movePlayer(player, 7, 0);
            fixture(level, Blocks.STONE);
            expect(!rain(level, GROUND, 0.0D), "Seven chunks blocks rainwater creation");

            fixture(level, Blocks.GRASS_BLOCK);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(rain(level, WATER, 0.125D), "Disappear-only permits rain absorption outside radius");
            expectLevel(level, WATER, 0, "Rain absorption removes water outside radius");

            fixture(level, Blocks.STONE);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, true),
                    "Disappear-only permits daytime evaporation outside radius");
            expectLevel(level, WATER, 0, "Daytime evaporation removes water outside radius");

            fixture(level, Blocks.GRASS_BLOCK);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, false),
                    "Disappear-only permits nighttime absorption outside radius");
            expectLevel(level, WATER, 0, "Nighttime absorption removes water outside radius");

            config.outsidePlayerRadiusBehavior.set(WaterPhysicsConfig.OutsidePlayerRadiusBehavior.NOTHING);
            fixture(level, Blocks.STONE);
            expect(!rain(level, GROUND, 0.0D), "Nothing mode blocks collection outside radius");

            fixture(level, Blocks.GRASS_BLOCK);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(!rain(level, WATER, 0.125D), "Nothing mode blocks rain absorption outside radius");
            expectLevel(level, WATER, 1, "Nothing mode preserves water during rain");

            fixture(level, Blocks.STONE);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(!FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, true),
                    "Nothing mode blocks daytime evaporation outside radius");
            expectLevel(level, WATER, 1, "Nothing mode preserves water during daytime");

            fixture(level, Blocks.GRASS_BLOCK);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
            expect(!FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, false),
                    "Nothing mode blocks nighttime absorption outside radius");
            expectLevel(level, WATER, 1, "Nothing mode preserves water at night");

            movePlayer(player, 6, 0);
            for (WaterPhysicsConfig.OutsidePlayerRadiusBehavior behavior
                    : WaterPhysicsConfig.OutsidePlayerRadiusBehavior.values()) {
                config.outsidePlayerRadiusBehavior.set(behavior);
                fixture(level, Blocks.STONE);
                expect(rain(level, GROUND, 0.0D), "Inside radius collects rain in " + behavior);
                expect(FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, true),
                        "Inside radius evaporates in " + behavior);
            }

            config.outsidePlayerRadiusBehavior.set(WaterPhysicsConfig.OutsidePlayerRadiusBehavior.DISAPPEAR_ONLY);
            config.playerRadiusChunks.set(0);
            movePlayer(player, 0, 0);
            fixture(level, Blocks.STONE);
            expect(rain(level, GROUND, 0.0D), "Zero radius includes player chunk");
            movePlayer(player, 1, 0);
            fixture(level, Blocks.STONE);
            expect(!rain(level, GROUND, 0.0D), "Zero radius excludes adjacent chunk");

            config.limitToPlayerRadius.set(false);
            movePlayer(player, 7, 0);
            fixture(level, Blocks.STONE);
            expect(rain(level, GROUND, 0.0D), "Disabled radius limit restores collection");
        } finally {
            player.remove(Entity.RemovalReason.DISCARDED);
            configureDefaults(config);
        }
    }

    private static ServerPlayer testPlayer(ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "RainfallTest");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public boolean hasClientLoaded() {
                return true;
            }
        };
        movePlayer(player, 0, 0);
        return player;
    }

    private static void movePlayer(ServerPlayer player, int chunkX, int chunkZ) {
        player.setPos(chunkX * 16 + 8.5D, GROUND.getY() + 1.0D, chunkZ * 16 + 8.5D);
    }

    private static void selectorChecks(ServerLevel level) {
        expect(ModBlockTags.matchesSelector(Blocks.STONE.defaultBlockState(), "minecraft:stone"), "Exact selector");
        expect(ModBlockTags.matchesSelector(Blocks.STONE.defaultBlockState(), "@minecraft"), "Mod selector");
        expect(ModBlockTags.matchesSelector(Blocks.STONE.defaultBlockState(), "minecraft:st*"), "Wildcard selector");
        expect(ModBlockTags.matchesSelector(Blocks.DIRT.defaultBlockState(), "#minecraft:dirt"), "Tag selector");

        var config = WaterPhysicsConfig.SERVER.rainfall;
        config.collectionIncludedBlocks.set(List.of("minecraft:stone"));
        config.collectionExcludedBlocks.set(List.of("minecraft:stone"));
        fixture(level, Blocks.STONE);
        expect(!rain(level, GROUND, 0.0D), "Collection exclusion overrides inclusion");

        config.collectionExcludedBlocks.set(List.of());
        for (String selector : List.of("@minecraft", "minecraft:st*")) {
            config.collectionIncludedBlocks.set(List.of(selector));
            fixture(level, Blocks.STONE);
            expect(rain(level, GROUND, 0.0D), "Configured collector: " + selector);
        }
        config.collectionIncludedBlocks.set(List.of("#minecraft:dirt"));
        fixture(level, Blocks.DIRT);
        expect(rain(level, GROUND, 0.0D), "Configured tag collector");

        config.absorptionIncludedBlocks.set(List.of("@minecraft"));
        config.absorptionExcludedBlocks.set(List.of("minecraft:dirt"));
        fixture(level, Blocks.DIRT);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        expect(!rain(level, WATER, 0.125D), "Absorption exclusion overrides inclusion");
        configureDefaults(config);
    }

    private static void rainChecks(ServerLevel level) {
        fixture(level, Blocks.STONE);
        for (int amount = 1; amount <= 3; amount++) {
            expect(rain(level, amount == 1 ? GROUND : WATER, 0.0D), "Rain adds level " + amount);
            expectLevel(level, WATER, amount, "Rain accumulation");
        }
        expect(!rain(level, WATER, 0.0D), "Rain capped at level 3");
        expectLevel(level, WATER, 3, "Rain cap retained");

        fixture(level, Blocks.GRASS_BLOCK);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        expect(rain(level, WATER, 0.0D), "Absorbent surface receives rain");
        expectLevel(level, WATER, 2, "Rain addition branch");
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        expect(rain(level, WATER, 0.125D), "Absorbent surface loses rain");
        expectLevel(level, WATER, 0, "Equal rain absorption branch");

        fixture(level, Blocks.STONE);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        expect(!rain(level, WATER, 0.3D), "Failed rain roll");
        expectLevel(level, WATER, 1, "Failed roll preserves level");

        fixture(level, Blocks.OAK_LEAVES);
        expect(rain(level, GROUND, 0.0D), "Leaves collect by default");

        for (Block fluid : List.of(Blocks.WATER, Blocks.LAVA)) {
            clear(level);
            level.setBlock(GROUND, fluid.defaultBlockState(), Block.UPDATE_ALL);
            expect(!rain(level, GROUND, 0.0D), "Foreign fluid surface rejected: " + fluid);
            expect(level.getBlockState(GROUND).is(fluid), "Foreign fluid preserved: " + fluid);
        }
    }

    private static void dryingChecks(ServerLevel level) {
        for (Block ground : List.of(Blocks.STONE, Blocks.GRASS_BLOCK)) {
            fixture(level, ground);
            FiniteWaterPhysics.setWaterLevel(level, WATER, 2);
            expect(FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, true),
                    "Clear day evaporation: " + ground);
            expectLevel(level, WATER, 1, "Clear day removes one level");
        }

        fixture(level, Blocks.STONE);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 2);
        expect(!FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, false),
                "Stone retained at night");
        expectLevel(level, WATER, 2, "Night preserves sealed water");

        fixture(level, Blocks.GRASS_BLOCK);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 2);
        expect(FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, false),
                "Soil absorbs at night");
        expectLevel(level, WATER, 1, "Night absorption removes one level");

        for (int amount = 4; amount <= 8; amount++) {
            for (Biome.Precipitation precipitation : Biome.Precipitation.values()) {
                fixture(level, Blocks.GRASS_BLOCK);
                FiniteWaterPhysics.setWaterLevel(level, WATER, amount);
                FiniteWaterRainfall.tick(level, WATER, 0.0D, precipitation, true);
                expectLevel(level, WATER, amount, "Deep-water weather immunity: " + amount + ", " + precipitation);
            }
        }
    }

    private static void coveredDryingChecks(ServerLevel level) {
        var config = WaterPhysicsConfig.SERVER.rainfall;

        fixture(level, Blocks.GRASS_BLOCK);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        level.setBlock(WATER.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        expect(FiniteWaterRainfall.randomTick(level, WATER, 0.0D, false, 0),
                "Absorbent ground removes covered water without sky access");
        expectLevel(level, WATER, 0, "Covered absorption removes one level");

        fixture(level, Blocks.STONE);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        level.setBlock(WATER.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        expect(!FiniteWaterRainfall.randomTick(level, WATER, 0.0D, true, 0),
                "Opaque covered stone does not dry");
        expectLevel(level, WATER, 1, "Opaque covered stone preserves water");

        fixture(level, Blocks.STONE);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        level.setBlock(WATER.above(), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
        expect(FiniteWaterRainfall.randomTick(level, WATER, 0.25D, true, 1),
                "Skylight through transparent cover uses daytime evaporation chance");
        expectLevel(level, WATER, 0, "Skylit covered water evaporates");

        fixture(level, Blocks.GRASS_BLOCK);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        expect(!FiniteWaterRainfall.randomTick(level, WATER, 0.0D, false, 0),
                "Exposed water is not processed twice by fluid random ticks");
        expectLevel(level, WATER, 1, "Exposed random tick preserves rainfall equilibrium");

        for (int amount : List.of(4, 8)) {
            fixture(level, Blocks.GRASS_BLOCK);
            FiniteWaterPhysics.setWaterLevel(level, WATER, amount);
            level.setBlock(WATER.above(), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
            expect(!FiniteWaterRainfall.randomTick(level, WATER, 0.0D, true, 15),
                    "Covered weather ignores level " + amount);
            expectLevel(level, WATER, amount, "Covered deep-water immunity");
        }

        config.absorptionIncludedBlocks.set(List.of("minecraft:stone_slab"));
        clear(level);
        level.setBlock(GROUND, Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        FiniteWaterPhysics.setWaterLevel(level, GROUND, 1);
        level.setBlock(WATER, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        expect(level.getFluidState(GROUND).isRandomlyTicking(),
                "Finite waterlogged hosts participate in fluid random ticks");
        expect(FiniteWaterRainfall.randomTick(level, GROUND, 0.0D, false, 0),
                "Configured waterlogged host absorbs while covered");
        expectLevel(level, GROUND, 0, "Covered waterlogged host loses one level");

        configureDefaults(config);
        fixture(level, Blocks.GRASS_BLOCK);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        level.setBlock(WATER.above(), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
        level.setRainLevel(0.0F);
        expect(level.getFluidState(WATER).isRandomlyTicking(), "Finite water fluid is randomly ticking");
        config.rainChangeChance.set(1.0D);
        config.dayEvaporationChance.set(1.0D);
        level.getFluidState(WATER).randomTick(level, WATER, RandomSource.create(1L));
        expectLevel(level, WATER, 0, "Native fluid random tick applies skylit evaporation");
        configureDefaults(config);
    }

    private static void weatherBoundaryChecks(ServerLevel level) {
        fixture(level, Blocks.STONE);
        biome(level, "plains");
        level.setRainLevel(1.0F);
        expect(FiniteWaterRainfall.tick(level, GROUND, 0.0D), "Exposed plains rain");

        fixture(level, Blocks.STONE);
        biome(level, "snowy_plains");
        level.setRainLevel(1.0F);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 2);
        expect(!FiniteWaterRainfall.tick(level, WATER, 0.0D), "Snow does not run rainfall logic");
        expectLevel(level, WATER, 2, "Snow preserves liquid for freezing hook");
        level.setBlock(WATER.above(), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
        WaterPhysicsConfig.SERVER.rainfall.rainChangeChance.set(1.0D);
        WaterPhysicsConfig.SERVER.rainfall.dayEvaporationChance.set(1.0D);
        WaterPhysicsConfig.SERVER.rainfall.absorptionIncludedBlocks.set(List.of("*:*"));
        level.getFluidState(WATER).randomTick(level, WATER, RandomSource.create(2L));
        expectLevel(level, WATER, 2, "Covered random ticks also preserve water during snow");
        configureDefaults(WaterPhysicsConfig.SERVER.rainfall);

        fixture(level, Blocks.STONE);
        biome(level, "desert");
        level.setRainLevel(1.0F);
        expect(level.precipitationAt(WATER) == Biome.Precipitation.NONE, "Desert has no precipitation");
        expect(!FiniteWaterRainfall.tick(level, GROUND, 0.0D), "Desert does not collect rain");

        fixture(level, Blocks.STONE);
        biome(level, "plains");
        level.setRainLevel(0.0F);
        FiniteWaterPhysics.setWaterLevel(level, WATER, 2);
        level.setBlock(WATER.above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.tickPrecipitation(GROUND);
        expectLevel(level, WATER, 2, "Roof protects shallow installation");
    }

    private static void hostChecks(ServerLevel level) {
        fixture(level, Blocks.STONE_SLAB);
        expect(rain(level, GROUND, 0.0D), "Rain fills partial host");
        expect(level.getBlockState(GROUND).is(Blocks.STONE_SLAB), "Partial host retained");
        expectLevel(level, GROUND, 1, "Partial host receives first level");
        expect(rain(level, GROUND, 0.0D), "Rain tops up waterlogged host");
        expectLevel(level, GROUND, 2, "Waterlogged host receives second level");
        expectLevel(level, WATER, 0, "Host water does not replace space above");

        clear(level);
        level.setBlock(GROUND, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), Block.UPDATE_ALL);
        FiniteWaterPhysics.setWaterLevel(level, GROUND, 1);
        expect(rain(level, GROUND, 0.0D), "Rain uses space above a full shallow host");
        expectLevel(level, GROUND, 1, "Full shallow host retained");
        expectLevel(level, WATER, 1, "Rain collects above full shallow host");
    }

    private static void disabledChecks(ServerLevel level) {
        var config = WaterPhysicsConfig.SERVER.rainfall;
        fixture(level, Blocks.STONE);
        config.enabled.set(false);
        expect(!rain(level, GROUND, 0.0D), "Disabled rainfall");

        config.enabled.set(true);
        config.rainChangeChance.set(0.0D);
        expect(!rain(level, GROUND, 0.0D), "Zero rain chance");

        FiniteWaterPhysics.setWaterLevel(level, WATER, 1);
        config.dayEvaporationChance.set(0.0D);
        expect(!FiniteWaterRainfall.tick(level, WATER, 0.0D, Biome.Precipitation.NONE, true),
                "Zero evaporation chance");
        expectLevel(level, WATER, 1, "Zero chances preserve water");
        configureDefaults(config);
    }

    private static boolean rain(ServerLevel level, BlockPos surface, double roll) {
        return FiniteWaterRainfall.tick(level, surface, roll, Biome.Precipitation.RAIN, true);
    }

    private static void fixture(ServerLevel level, Block ground) {
        clear(level);
        level.setBlock(GROUND, ground.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void biome(ServerLevel level, String biome) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "fillbiome 0 192 0 15 207 15 minecraft:" + biome);
    }

    private static void clear(ServerLevel level) {
        level.getBlockTicks().clearArea(AREA);
        level.getFluidTicks().clearArea(AREA);
        for (BlockPos pos : BlockPos.betweenClosed(6, 198, 6, 10, 205, 10)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void expectLevel(ServerLevel level, BlockPos pos, int expected, String message) {
        int actual = FiniteWaterPhysics.getWaterLevel(level, pos);
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(boolean passed, String message) {
        if (!passed) {
            throw new AssertionError(message);
        }
    }
}
