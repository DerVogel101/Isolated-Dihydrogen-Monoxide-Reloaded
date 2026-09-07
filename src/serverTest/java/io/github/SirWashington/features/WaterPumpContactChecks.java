package io.github.SirWashington.features;

import com.mojang.authlib.GameProfile;
import io.github.SirWashington.block.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Real server damage/armor paths, native mob block-contact ticks, and player-sized passage checks. */
final class WaterPumpContactChecks implements AutoCloseable {
    private final ServerLevel level;
    private final PumpStructure[] stages = new PumpStructure[3];
    private final Mob[] mobs = new Mob[3];
    private final ServerPlayer[] players = new ServerPlayer[3];
    private int ticks;

    WaterPumpContactChecks(ServerLevel level) {
        this.level = level;
        // Remove fixtures left by a previously interrupted run in this disposable test world.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(2, 194, 2, 15, 207, 15))) {
            if (mob.getType() == EntityTypes.RABBIT) mob.discard();
        }
        try {
        passageChecks(level);
        for (int i = 0; i < 3; i++) {
            int size = i + 1;
            PumpStructure stage = new PumpStructure(new BlockPos(i == 0 ? 4 : i == 1 ? 7 : 11, 200, 8), size, Direction.SOUTH);
            stages[i] = stage;
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                level.setBlock(stage.cell(x, y), ModBlocks.WATER_PUMP.defaultBlockState()
                        .setValue(WaterPumpBlock.FACING, stage.facing()), Block.UPDATE_ALL);
            }
            level.setBlock(stage.origin().below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            refresh(stage);
            Vec3 center = Vec3.atCenterOf(stage.origin()).add((size - 1) / 2.0, (size - 1) / 2.0, 0);
            Mob mob = EntityTypes.RABBIT.create(level, EntitySpawnReason.COMMAND);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            mob.setHealth(100); mob.setNoAi(true); mob.setNoGravity(true);
            mob.setPos(center.x, center.y - .25, center.z);
            mobs[i] = mob;
            level.addFreshEntity(mob);
            ServerPlayer player = player(level);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            player.setHealth(100); player.setPose(Pose.SWIMMING); player.refreshDimensions();
            player.setPos(center.x, center.y - .3, center.z);
            player.setDeltaMovement(.12, -.03, .06);
            players[i] = player;
        }
        // Extra entities exercise armor and invulnerability without sharing the timed targets' cooldown.
        ServerPlayer armored = player(level);
        armored.setPos(players[0].position());
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        // This disconnected player is not equipment-ticked; supply the chestplate's normal attributes.
        armored.getAttribute(Attributes.ARMOR).setBaseValue(8);
        armored.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        contact(stages[0], armored);
        expect(armored.getHealth() > 10 && armored.getHealth() < 20,
                "Melee armor mitigates pump damage: health=" + armored.getHealth());
        expect(armored.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "Pump damages armor like melee");
        ServerPlayer immune = player(level);
        immune.setPos(players[0].position());
        immune.getAbilities().invulnerable = true;
        contact(stages[0], immune);
        expect(immune.getHealth() == 20, "Normal player invulnerability remains respected");
        expect(!level.damageSources().source(DamageTypes.PLAYER_ATTACK).is(DamageTypeTags.NO_KNOCKBACK)
                && !level.damageSources().source(DamageTypes.PLAYER_ATTACK).is(DamageTypeTags.BYPASSES_COOLDOWN),
                "Ordinary melee retains its knockback and immunity rules");
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean tick() {
        ticks++;
        for (int i = 0; i < 3; i++) {
            contact(stages[i], players[i]); // Every member calls the same native block callback.
            int hits = Math.min(3, (ticks - 1) / 8 + 1);
            float expected = 100 - hits * (i == 0 ? 10 : 5);
            expect(players[i].getHealth() == expected, "Player size " + (i + 1) + " tick " + ticks + ": " + players[i].getHealth() + " expected " + expected);
            expect(mobs[i].getHealth() == expected, "Native mob contact size " + (i + 1) + " tick " + ticks + ": " + mobs[i].getHealth() + " expected " + expected);
            expect(players[i].getDeltaMovement().equals(new Vec3(.12, -.03, .06)), "Pump preserves existing player velocity");
            expect(mobs[i].getDeltaMovement().lengthSqr() < 1E-12, "Pump gives mobs no knockback");
            var source = players[i].getLastDamageSource();
            expect(source instanceof PumpDamage && source.is(DamageTypes.PLAYER_ATTACK)
                    && !source.is(DamageTypeTags.BYPASSES_ARMOR), "Exact player-melee damage type without armor bypass");
            expect(source.getEntity() instanceof FakePlayer fakePlayer
                            && level.getServer().getPlayerList().getPlayer(fakePlayer.getUUID()) == null
                            && mobs[i].getLastHurtByPlayer() instanceof FakePlayer
                            && mobs[i].getLastHurtByPlayerMemoryTime() > 0,
                    "Pump damage records a fake-player kill for mob loot and experience");
            if (ticks == 20) {
                level.removeBlock(stages[i].origin().below(), false);
                refresh(stages[i]);
            }
        }
        if (ticks == 30) System.out.println("PUMP_PASSAGE_DAMAGE_CADENCE_ARMOR_NO_KNOCKBACK_PASS");
        return ticks == 30;
    }

    private void refresh(PumpStructure stage) {
        for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
            ((WaterPumpBlockEntity)level.getBlockEntity(stage.cell(x, y))).refresh();
        }
    }

    private void contact(PumpStructure stage, LivingEntity target) {
        for (int x = 0; x < stage.size(); x++) for (int y = 0; y < stage.size(); y++) {
            BlockPos pos = stage.cell(x, y);
            level.getBlockState(pos).entityInside(level, pos, target, InsideBlockEffectApplier.NOOP, true);
        }
    }

    private static ServerPlayer player(ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "PumpContactTest");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        return player;
    }

    private static void passageChecks(ServerLevel level) {
        ServerPlayer player = player(level);
        for (Direction facing : Direction.values()) for (int size = 1; size <= 3; size++) {
            PumpStructure stage = new PumpStructure(new BlockPos(6, 200, 6), size, facing);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                level.setBlock(stage.cell(x, y), ModBlocks.WATER_PUMP.defaultBlockState()
                        .setValue(WaterPumpBlock.FACING, facing), Block.UPDATE_ALL);
                ((WaterPumpBlockEntity)level.getBlockEntity(stage.cell(x, y))).refresh();
            }
            // Refresh again once every member exists.
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                ((WaterPumpBlockEntity)level.getBlockEntity(stage.cell(x, y))).refresh();
            }
            Direction u = PumpStructure.right(facing), v = PumpStructure.up(facing);
            double offset = (size - 1) / 2.0;
            Vec3 center = Vec3.atCenterOf(stage.origin()).add(offset * (u.getStepX() + v.getStepX()),
                    offset * (u.getStepY() + v.getStepY()), offset * (u.getStepZ() + v.getStepZ()));
            for (Pose pose : size == 1 ? new Pose[]{Pose.SWIMMING} : new Pose[]{Pose.CROUCHING, Pose.SWIMMING}) {
                var dimensions = player.getDimensions(pose);
                for (int step = -16; step <= 16; step++) {
                    Vec3 feet = center.add(facing.getStepX() * step / 8.0, facing.getStepY() * step / 8.0 - dimensions.height() / 2,
                            facing.getStepZ() * step / 8.0);
                    expect(level.noCollision(player, dimensions.makeBoundingBox(feet)), "Clear " + pose + " passage size " + size + " " + facing);
                }
            }
            expect(PumpDamage.intersectsPassage(AABB.ofSize(center, .2, .2, .2), center, facing, size), "Opening detects contact");
            Vec3 outside = center.add(facing.getStepX() * .7, facing.getStepY() * .7, facing.getStepZ() * .7);
            expect(!PumpDamage.intersectsPassage(AABB.ofSize(outside, .1, .1, .1), center, facing, size), "No damage outside the duct");
            Vec3 corner = center.add((size / 2.0 - .01) * (u.getStepX() + v.getStepX()),
                    (size / 2.0 - .01) * (u.getStepY() + v.getStepY()), (size / 2.0 - .01) * (u.getStepZ() + v.getStepZ()));
            expect(!PumpDamage.intersectsPassage(AABB.ofSize(corner, .01, .01, .01), center, facing, size), "Solid corners are not the damage volume");
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) level.removeBlock(stage.cell(x, y), false);
        }
    }

    @Override public void close() {
        for (Mob mob : mobs) if (mob != null) mob.discard();
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
