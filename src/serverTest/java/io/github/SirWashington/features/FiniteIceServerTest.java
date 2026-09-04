package io.github.SirWashington.features;

import com.mojang.authlib.GameProfile;
import io.github.SirWashington.block.FiniteIceBlock;
import io.github.SirWashington.block.LayeredFiniteIceBlock;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.component.ModDataComponentTypes;
import io.github.SirWashington.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.List;

/** ./gradlew -PiceTest runServer --args="--nogui" in build/ice-test-server. */
public final class FiniteIceServerTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(8, 200, 8);
    private int ticks;
    private boolean ran;
    private boolean waitingForThaw;
    private int thawTicks;
    private int lightingTicks;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.overworld().setChunkForced(0, 0, true));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) {
                if (!waitingForThaw) return;
                ServerLevel level = server.overworld();
                if (FrozenWaterloggedBlocks.isFrozen(level.getBlockState(POS)) && ++thawTicks < 200) return;
                try {
                    expect(!FrozenWaterloggedBlocks.isFrozen(level.getBlockState(POS)), "Scheduled light thaw completes; light="
                            + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, POS)
                            + ", above=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, POS.above())
                            + ", scheduled=" + level.getBlockTicks().hasScheduledTick(POS, Blocks.CHEST));
                    expect(level.getBlockState(POS).is(Blocks.CHEST), "Light thaw keeps chest");
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8, "Light thaw conserves water");
                    expect(((net.minecraft.world.Container) level.getBlockEntity(POS)).getItem(0).getCount() == 17,
                            "Light thaw preserves inventory");
                    System.out.println("FINITE_ICE_SERVER_TEST_PASS");
                } finally {
                    reset(level);
                    level.setChunkForced(0, 0, false);
                    server.halt(false);
                }
                return;
            }
            if (!server.overworld().areEntitiesActuallyLoadedAndTicking(net.minecraft.world.level.ChunkPos.containing(POS))) {
                if (++ticks > 600) {
                    server.halt(false);
                    throw new AssertionError("Fixture chunk did not become entity-ticking");
                }
                return;
            }
            // Clear persisted light from the preceding run before synchronous freeze assertions.
            if (lightingTicks++ == 0) {
                reset(server.overworld());
                for (BlockPos pos : BlockPos.betweenClosed(POS.offset(-3, -2, -3), POS.offset(3, 4, 3)))
                    server.overworld().getChunkSource().getLightEngine().checkBlock(pos);
            }
            if (lightingTicks < 20) return;
            ran = true;
            ServerLevel level = server.overworld();
            try {
                level.setChunkForced(0, 0, true);
                biome(level, "snowy_plains");
                ItemEntity probe = new ItemEntity(level, 8.5, 200.5, 8.5, new ItemStack(Items.STONE));
                expect(level.addFreshEntity(probe), "Probe can spawn");
                expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(POS).inflate(2)).contains(probe),
                        "Fixture chunk must track item entities");
                probe.discard();
                ServerPlayer player = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "IceTest"), ClientInformation.createDefault());
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                player.setPos(8.5, 200, 8.5);
                player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                        net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
                for (int amount = 1; amount <= 8; amount++) {
                    reset(level);
                    FiniteWaterPhysics.setWaterLevel(level, POS, amount);
                    level.tickPrecipitation(POS);
                    expect(FiniteIceBlock.frozenLayers(level.getBlockState(POS)) == amount, "Natural freeze " + amount);
                    expect(level.getBlockState(POS).is(amount == 8 ? ModBlocks.FINITE_ICE : ModBlocks.LAYERED_FINITE_ICE), "Correct variant");
                    expect(Math.abs(level.getBlockState(POS).getCollisionShape(level, POS).max(Direction.Axis.Y) - amount / 8.0) < 1E-9, "Collision height");
                    for (boolean silk : new boolean[]{false, true}) {
                        level.setBlockAndUpdate(POS, amount == 8 ? ModBlocks.FINITE_ICE.defaultBlockState()
                                : ModBlocks.LAYERED_FINITE_ICE.defaultBlockState().setValue(LayeredFiniteIceBlock.LAYERS, amount));
                        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
                        if (silk) tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
                        player.setItemSlot(EquipmentSlot.MAINHAND, tool);
                        expect(player.gameMode.destroyBlock(POS), "Survival break");
                        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(POS).inflate(2));
                        if (silk && amount == 8) {
                            expect(level.getBlockState(POS).isAir(), "Silk leaves no water");
                            expect(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.FINITE_ICE)
                                    && drops.getFirst().getItem().getCount() == 1, "Exactly one silk drop: " + drops
                                    + ", block item=" + ModBlocks.FINITE_ICE.asItem());
                        } else {
                            expect(FiniteWaterPhysics.getWaterLevel(level, POS) == amount, "Break conserves " + amount);
                            expect(drops.isEmpty(), "No drop on water release");
                        }
                        drops.forEach(ItemEntity::discard);
                    }
                }
                for (int ice = 1; ice < 8; ice++) {
                    reset(level);
                    layer(level, ice);
                    ItemStack bucket = new ItemStack(ModItems.PRECISION_BUCKET);
                    bucket.set(ModDataComponentTypes.BUCKET_FILL_LEVEL, 8);
                    player.setItemSlot(EquipmentSlot.MAINHAND, bucket);
                    bucket.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket,
                            new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false)));
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8 - ice, "Precision bucket capacity");
                    expect(bucket.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0) == ice, "Bucket conserves remainder");
                    level.tickPrecipitation(POS);
                    expect(level.getBlockState(POS).is(ModBlocks.FINITE_ICE), "Topping up freezes to full ice");

                    layer(level, ice);
                    FiniteWaterPhysics.setWaterLevel(level, POS, 1);
                    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
                    expect(player.gameMode.destroyBlock(POS), "Break wet layered ice");
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == ice + 1, "Break releases frozen plus liquid");

                    reset(level);
                    layer(level, ice);
                    FiniteWaterPhysics.setWaterLevel(level, POS.above(), 8);
                    FiniteWaterPhysics.tick(level, POS.above());
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8 - ice, "Downflow capacity");
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS.above()) == ice, "Downflow conservation");

                    reset(level);
                    layer(level, ice);
                    FiniteWaterPhysics.setWaterLevel(level, POS, 1);
                    level.tickPrecipitation(POS);
                    expect(FiniteIceBlock.frozenLayers(level.getBlockState(POS)) == ice + 1, "Partial top-up grows layers");
                    expect(Math.max(0, FiniteWaterPhysics.getWaterLevel(level, POS)) == 0, "Freezing consumes only liquid");

                    reset(level);
                    layer(level, ice);
                    BlockPos source = POS.west();
                    for (Direction d : Direction.values()) {
                        if (d != Direction.EAST) level.setBlockAndUpdate(source.relative(d), Blocks.STONE.defaultBlockState());
                    }
                    FiniteWaterPhysics.setWaterLevel(level, source, 8);
                    for (int step = 0; step < 8; step++) FiniteWaterPhysics.tick(level, source);
                    int added = FiniteWaterPhysics.getWaterLevel(level, POS);
                    expect(added == (8 - ice) / 2, "Horizontal flow above " + ice + " ice layers: " + added);
                    expect(FiniteWaterPhysics.getWaterLevel(level, source) + added == 8, "Horizontal conservation");
                    FiniteWaterPhysics.tick(level, POS);
                    expect(totalLiquid(level) == 8, "Layered outflow conservation");

                    reset(level);
                    layer(level, ice);
                    expect(FiniteWaterPhysics.placeFullBucket(level, POS), "Full bucket overflow succeeds");
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8 - ice, "Full bucket fills remaining space");
                    expect(totalLiquid(level) == 8, "Full bucket conserves all eight units");

                    reset(level);
                    layer(level, ice);
                    for (Direction d : Direction.values()) level.setBlockAndUpdate(POS.relative(d), Blocks.STONE.defaultBlockState());
                    expect(!FiniteWaterPhysics.placeFullBucket(level, POS), "Sealed overflow rejected");
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 0, "Failed bucket is atomic");
                }
                reset(level);
                biome(level, "plains");
                FiniteWaterPhysics.setWaterLevel(level, POS, 8);
                level.tickPrecipitation(POS);
                expect(level.getBlockState(POS).is(ModBlocks.FINITE_WATER), "Warm biome stays liquid");
                biome(level, "snowy_plains");
                level.setBlockAndUpdate(POS.above(2), Blocks.STONE.defaultBlockState());
                expect(!FiniteWaterFreezing.freeze(level, POS), "Roof blocks freezing");
                reset(level);
                level.setBlockAndUpdate(POS, Blocks.WATER.defaultBlockState());
                expect(!FiniteWaterFreezing.freeze(level, POS), "Custom hook ignores vanilla water");
                level.tickPrecipitation(POS);
                expect(level.getBlockState(POS).is(Blocks.ICE), "Vanilla freezing unchanged");
                reset(level);
                level.setBlockAndUpdate(POS, Blocks.OAK_SLAB.defaultBlockState());
                FiniteWaterPhysics.setWaterLevel(level, POS, 4);
                expect(FiniteWaterFreezing.freeze(level, POS), "Waterlogged slab freezes");
                expect(level.getBlockState(POS).is(Blocks.OAK_SLAB), "Frozen slab retains its block");
                testFrozenHosts(level, player);
                testSnow(level, player);
                reset(level);
                layer(level, 7);
                FiniteWaterPhysics.setWaterLevel(level, POS.above(), 8);
                expect(SpecialFlow.pushWater(level, List.of(POS.above()), Direction.DOWN), "Piston pressure into layered ice");
                expect(FiniteWaterPhysics.getWaterLevel(level, POS) <= 1 && totalLiquid(level) == 8,
                        "Piston capacity and conservation");
                reset(level);
                level.setBlockAndUpdate(POS, Blocks.CHEST.defaultBlockState());
                FiniteWaterPhysics.setWaterLevel(level, POS, 8);
                ((net.minecraft.world.Container) level.getBlockEntity(POS)).setItem(0, new ItemStack(Items.DIAMOND, 17));
                expect(FiniteWaterFreezing.freeze(level, POS), "Prepare natural light thaw");
                level.setBlockAndUpdate(POS.east(), Blocks.GLOWSTONE.defaultBlockState());
                waitingForThaw = true;
            } finally {
                if (!waitingForThaw) {
                    reset(level);
                    level.setChunkForced(0, 0, false);
                    server.halt(false);
                }
            }
        });
    }

    private static void layer(ServerLevel level, int ice) {
        level.setBlockAndUpdate(POS, ModBlocks.LAYERED_FINITE_ICE.defaultBlockState().setValue(LayeredFiniteIceBlock.LAYERS, ice));
    }

    private static void testSnow(ServerLevel level, ServerPlayer player) {
        for (int snow = 1; snow <= 8; snow++) {
            reset(level);
            var dry = Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, snow);
            level.setBlockAndUpdate(POS, dry);
            int capacity = 8 - snow;
            expect(FiniteWaterPhysics.getWaterCapacity(level, POS) == capacity, "Snow capacity " + snow);
            if (capacity == 0) {
                for (Direction d : Direction.Plane.HORIZONTAL) level.setBlockAndUpdate(POS.above().relative(d), Blocks.STONE.defaultBlockState());
            }
            FiniteWaterPhysics.setWaterLevel(level, POS.above(), 8);
            FiniteWaterPhysics.tick(level, POS.above());
            expect(FiniteWaterPhysics.getWaterLevel(level, POS) == capacity, "Snow downflow " + snow);
            expect(FiniteWaterPhysics.getWaterLevel(level, POS.above()) == snow, "Snow downflow conservation");
            FiniteWaterPhysics.setWaterLevel(level, POS.above(), 0);
            if (capacity == 0) {
                expect(!FiniteWaterFreezing.freeze(level, POS), "Dry full snow does not freeze");
                continue;
            }
            var wet = level.getBlockState(POS);
            if (snow < 8) {
                expect(!level.setBlockAndUpdate(POS, wet.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, snow + 1)),
                        "Cannot stack snow into occupied water space");
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SNOW));
                var placement = new net.minecraft.world.item.context.BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                        player.getMainHandItem(), new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false));
                expect(Blocks.SNOW.getStateForPlacement(placement) == null, "Full wet snow rejects item placement before consumption");
            }
            expect(FiniteWaterFreezing.freeze(level, POS), "Snow water freezes " + snow);
            var frozen = level.getBlockState(POS);
            expect(FiniteWaterloggedPlants.snowLayers(frozen) == snow, "Freeze preserves snow layers");
            expect(FrozenWaterloggedBlocks.frozenUnits(frozen) == capacity, "Freeze preserves water units");
            expect(FrozenWaterloggedBlocks.iceHeight(frozen) == 8, "Ice fills space above snow");
            expect(FiniteWaterPhysics.getWaterCapacity(level, POS) == 0, "Full frozen snow has no room");
            expect(frozen.getCollisionShape(level, POS, net.minecraft.world.phys.shapes.CollisionContext.of(player))
                    .max(Direction.Axis.Y) == 1.0, "Frozen snow collision reaches ice surface");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            expect(!player.gameMode.destroyBlock(POS), "Breaking frozen snow thaws ice first");
            expect(level.getBlockState(POS).equals(wet), "Breaking ice retains snow and water");
            level.removeBlock(POS, false);
            expect(FiniteWaterPhysics.getWaterLevel(level, POS) == capacity, "Snow removal preserves logical, not rendered, water amount");
            FiniteWaterPhysics.setWaterLevel(level, POS, 0);

            // Partial ice leaves room for more water, without counting snow as frozen water.
            if (capacity > 1) {
                level.setBlockAndUpdate(POS, dry);
                FiniteWaterPhysics.setWaterLevel(level, POS, 1);
                expect(FiniteWaterFreezing.freeze(level, POS), "Partial snow water freezes");
                ItemStack bucket = new ItemStack(ModItems.PRECISION_BUCKET);
                bucket.set(ModDataComponentTypes.BUCKET_FILL_LEVEL, 8);
                player.setItemSlot(EquipmentSlot.MAINHAND, bucket);
                bucket.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket,
                        new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false)));
                expect(bucket.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0) == snow + 1,
                        "Frozen snow top-up conserves bucket remainder");
                expect(FrozenWaterloggedBlocks.frozenUnits(level.getBlockState(POS)) == 1, "Top-up preserves snow ice");
                expect(FiniteWaterPhysics.getWaterLevel(level, POS) == capacity - 1, "Top-up fits remaining snow capacity");
                expect(FiniteWaterFreezing.freeze(level, POS), "Snow top-up refreezes");
                FrozenWaterloggedBlocks.thaw(level, POS);
                expect(level.getBlockState(POS).equals(wet), "Snow refreeze and thaw conserve layers and water");
                FiniteWaterPhysics.setWaterLevel(level, POS, 0);
            }
            reset(level);
            level.setBlockAndUpdate(POS, dry);
            BlockPos source = POS.west();
            for (Direction d : Direction.values()) {
                if (d != Direction.EAST) level.setBlockAndUpdate(source.relative(d), Blocks.STONE.defaultBlockState());
            }
            FiniteWaterPhysics.setWaterLevel(level, source, 8);
            for (int step = 0; step < 8; step++) FiniteWaterPhysics.tick(level, source);
            int added = FiniteWaterPhysics.getWaterLevel(level, POS);
            expect(added == capacity / 2, "Horizontal water enters above snow, not through it: " + snow + ", " + added);
            expect(FiniteWaterPhysics.getWaterLevel(level, source) + added == 8, "Horizontal snow flow conserves water");
        }
        reset(level);
    }

    private static void testFrozenHosts(ServerLevel level, ServerPlayer player) {
        for (Block block : List.of(Blocks.CHEST, Blocks.HOPPER, Blocks.OAK_STAIRS, Blocks.POTTED_DANDELION,
                Blocks.WHEAT, Blocks.DANDELION)) {
            for (int amount = 1; amount <= 8; amount++) {
                reset(level);
                if (block == Blocks.WHEAT) level.setBlockAndUpdate(POS.below(), Blocks.FARMLAND.defaultBlockState());
                if (block == Blocks.DANDELION) level.setBlockAndUpdate(POS.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(POS, block.defaultBlockState());
                FiniteWaterPhysics.setWaterLevel(level, POS, amount);
                var original = level.getBlockState(POS);
                var entity = level.getBlockEntity(POS);
                if (entity instanceof net.minecraft.world.Container inventory)
                    inventory.setItem(0, new ItemStack(Items.DIAMOND, 17));
                if (entity instanceof net.minecraft.world.Container inventory)
                    expect(inventory.stillValid(player), "Unfrozen inventory is usable");
                expect(FiniteWaterFreezing.freeze(level, POS), "Host freezes: " + block + " amount " + amount);
                var frozen = level.getBlockState(POS);
                var encoded = net.minecraft.world.level.block.state.BlockState.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, frozen).getOrThrow();
                expect(net.minecraft.world.level.block.state.BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow().equals(frozen), "Frozen state survives serialization");
                expect(frozen.setValue(FrozenWaterloggedBlocks.FROZEN, FrozenWaterloggedBlocks.Phase.NONE).equals(original), "Host properties preserved");
                expect(frozen.getFluidState().isEmpty() && FiniteWaterPhysics.getWaterLevel(level, POS) == 0,
                        "Frozen water is not flowing water");
                expect(FiniteWaterPhysics.getWaterCapacity(level, POS) == 8 - amount, "Frozen host retains free capacity");
                expect(!frozen.canBeReplaced(), "Cannot replace frozen plants");
                expect(frozen.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false))
                        == net.minecraft.world.InteractionResult.FAIL, "Frozen host cannot be used");
                expect(net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, POS) == null,
                        "Hoppers cannot access frozen inventory");
                if (entity instanceof net.minecraft.world.Container inventory)
                    expect(!inventory.stillValid(player), "Existing inventory menu becomes invalid");
                expect(frozen.getCollisionShape(level, POS).max(Direction.Axis.Y) >= FrozenWaterloggedBlocks.iceHeight(frozen) / 8.0,
                        "Ice adds collision");
                var fallingBox = new AABB(POS.getX() + 0.2, POS.getY() + 1.1, POS.getZ() + 0.2,
                        POS.getX() + 0.8, POS.getY() + 2.9, POS.getZ() + 0.8);
                var fall = net.minecraft.world.entity.Entity.collideBoundingBox(player, new Vec3(0, -1.2, 0),
                        fallingBox, level, List.of());
                expect(fallingBox.minY + fall.y >= POS.getY() + FrozenWaterloggedBlocks.iceHeight(frozen) / 8.0 - 1E-7,
                        "Actual entity collision supports frozen " + block + " at ice level " + amount + ": " + fall);
                var sideBox = new AABB(POS.getX() - 0.8, POS.getY(), POS.getZ() + 0.2,
                        POS.getX() - 0.2, POS.getY() + 1.8, POS.getZ() + 0.8);
                var sideways = net.minecraft.world.entity.Entity.collideBoundingBox(player, new Vec3(1, 0, 0),
                        sideBox, level, List.of());
                expect(sideways.x <= 0.2 + 1E-7, "Cannot move through frozen " + block);
                if (block == Blocks.WHEAT || block == Blocks.DANDELION) {
                    double surface = POS.getY() + FrozenWaterloggedBlocks.iceHeight(frozen) / 8.0;
                    player.setPos(POS.getX() + 0.5, surface, POS.getZ() + 0.5);
                    player.setOnGround(true);
                    player.setDeltaMovement(0.1, 0, 0);
                    player.travel(Vec3.ZERO);
                    expect(Math.abs(player.getDeltaMovement().x - 0.1 * Blocks.ICE.getFriction() * 0.91F) < 1E-6,
                            "Frozen plants use vanilla ice friction at level " + amount + ": " + player.getDeltaMovement());
                    player.setDeltaMovement(Vec3.ZERO);
                    player.setPos(8.5, 200, 8.5);
                }
                FiniteWaterPhysics.setWaterLevel(level, POS, 0);
                expect(level.getBlockState(POS).equals(frozen), "Drain cannot erase frozen contents");
                expect(!player.gameMode.destroyBlock(POS), "Breaking ice does not harvest host");
                expect(level.getBlockState(POS).equals(original), "Breaking thaws exact original state");
                expect(level.getBlockEntity(POS) == entity, "Block entity identity preserved");
                if (entity instanceof net.minecraft.world.Container inventory)
                    expect(inventory.getItem(0).is(Items.DIAMOND) && inventory.getItem(0).getCount() == 17,
                            "Inventory contents preserved");
                expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(POS).inflate(2)).isEmpty(), "No duplicated drops");
                expect(FiniteWaterPhysics.getWaterLevel(level, POS) == amount, "Thaw preserves water amount");
                expect(FiniteWaterFreezing.freeze(level, POS), "Can refreeze host");
                expect(FrozenWaterloggedBlocks.thaw(level, POS) && level.getBlockState(POS).equals(original),
                        "Melting restores original state too");
                if (amount < 8) {
                    expect(FiniteWaterFreezing.freeze(level, POS), "Freeze before top-up");
                    ItemStack bucket = new ItemStack(ModItems.PRECISION_BUCKET);
                    bucket.set(ModDataComponentTypes.BUCKET_FILL_LEVEL, 8);
                    player.setItemSlot(EquipmentSlot.MAINHAND, bucket);
                    var hit = new BlockHitResult(Vec3.atCenterOf(POS), Direction.UP, POS, false);
                    expect(net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(
                            player, level, InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.PASS,
                            "Water bucket interaction passes frozen lock");
                    expect(level.getBlockState(POS).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND, hit)
                            == net.minecraft.world.InteractionResult.PASS, "Host does not consume water-tool interaction");
                    bucket.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, bucket, hit));
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8 - amount, "Top-up fills available liquid capacity");
                    expect(FrozenWaterloggedBlocks.frozenUnits(level.getBlockState(POS)) == amount, "Top-up preserves original ice");
                    expect(bucket.getOrDefault(ModDataComponentTypes.BUCKET_FILL_LEVEL, 0) == amount, "Top-up conserves bucket remainder");
                    expect(!level.getFluidState(POS).isEmpty(), "Water above host ice has a fluid state");
                    FiniteWaterPhysics.setWaterLevel(level, POS, 0);
                    expect(level.getBlockState(POS).equals(frozen), "Draining liquid leaves original frozen host");
                    FiniteWaterPhysics.setWaterLevel(level, POS.above(), 8);
                    FiniteWaterPhysics.tick(level, POS.above());
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS) == 8 - amount, "Natural downflow can top up frozen host: "
                            + block + ", ice=" + amount + ", liquid=" + FiniteWaterPhysics.getWaterLevel(level, POS));
                    expect(FiniteWaterPhysics.getWaterLevel(level, POS.above()) == amount, "Downflow conserves overflow");
                    level.setBlock(POS.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    expect(FiniteWaterFreezing.freeze(level, POS), "Added liquid refreezes");
                    expect(FrozenWaterloggedBlocks.frozenUnits(level.getBlockState(POS)) == 8, "Refreeze incorporates top-up");
                    expect(FrozenWaterloggedBlocks.thaw(level, POS) && FiniteWaterPhysics.getWaterLevel(level, POS) == 8,
                            "Thaw returns frozen plus added units");
                    expect(level.getBlockEntity(POS) == entity, "Top-up keeps the original block entity");
                }
            }
        }
        reset(level);
        var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        BlockPos other = POS.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left));
        level.setBlock(POS, left, Block.UPDATE_CLIENTS);
        level.setBlock(other, left.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.RIGHT), Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, POS, 8);
        expect(FiniteWaterFreezing.freeze(level, POS), "Double chest freezes");
        expect(FrozenWaterloggedBlocks.isLocked(level, other), "Other chest half is locked");
        expect(net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, other) == null,
                "Other chest half cannot bypass ice");
        expect(!player.gameMode.destroyBlock(other), "Breaking other half thaws connected ice");
        expect(level.getBlockState(POS).is(Blocks.CHEST) && level.getBlockState(other).is(Blocks.CHEST), "Both chest halves remain");
        reset(level);
        var door = Blocks.OAK_DOOR.defaultBlockState();
        level.setBlock(POS, door, Block.UPDATE_CLIENTS);
        level.setBlock(POS.above(), door.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        FiniteWaterPhysics.setWaterLevel(level, POS.above(), 8);
        expect(FiniteWaterFreezing.freeze(level, POS.above()), "Upper door half freezes: " + level.getBlockState(POS.above())
                + ", light=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, POS.above())
                + ", height=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, POS.getX(), POS.getZ()));
        expect(FrozenWaterloggedBlocks.isLocked(level, POS), "Lower door half locked too");
        expect(!player.gameMode.destroyBlock(POS), "Breaking lower door half thaws ice above");
        expect(level.getBlockState(POS).is(Blocks.OAK_DOOR) && level.getBlockState(POS.above()).is(Blocks.OAK_DOOR),
                "Both door halves preserved");
        reset(level);
        player.setPos(8.5, 200.05, 8.5);
        player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.1, 0));
        player.setOnGround(true);
        player.setDeltaMovement(0.1, 0, 0);
        player.travel(Vec3.ZERO);
        expect(Math.abs(player.getDeltaMovement().x - 0.1 * Blocks.STONE.getFriction() * 0.91F) < 1E-6,
                "Unfrozen blocks retain normal friction");
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static int totalLiquid(ServerLevel level) {
        int amount = 0;
        for (BlockPos pos : BlockPos.betweenClosed(POS.offset(-2,-1,-2), POS.offset(2,2,2)))
            amount += Math.max(0, FiniteWaterPhysics.getWaterLevel(level, pos));
        return amount;
    }

    private static void reset(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(POS.offset(-2,-1,-2), POS.offset(2,3,2)))
            FiniteWaterPhysics.setWaterloggedBlock(level, pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(POS.below(), Blocks.STONE.defaultBlockState());
        level.getEntitiesOfClass(ItemEntity.class, new AABB(POS).inflate(4)).forEach(ItemEntity::discard);
    }

    private static void biome(ServerLevel level, String biome) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "fillbiome 0 192 0 15 207 15 minecraft:" + biome);
    }

    private static void expect(boolean passed, String reason) {
        if (!passed) throw new AssertionError(reason);
    }
}
