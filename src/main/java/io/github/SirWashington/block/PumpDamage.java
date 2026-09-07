package io.github.SirWashington.block;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Ordinary player-melee damage with pump-local knockback/cooldown exceptions. */
public final class PumpDamage extends DamageSource {
    private static final GameProfile PUMP_PLAYER = new GameProfile(
            UUID.fromString("7bf4d759-f922-4ec2-a68c-8dc2537acfb8"), "[Water Pump]");
    private static final Map<LivingEntity, Long> LAST_CONTACT = new WeakHashMap<>();

    private PumpDamage(ServerLevel level, Vec3 center) {
        super(level.damageSources().source(DamageTypes.PLAYER_ATTACK).typeHolder(), attacker(level, center));
    }

    private static FakePlayer attacker(ServerLevel level, Vec3 center) {
        FakePlayer player = FakePlayer.get(level, PUMP_PLAYER);
        player.setPos(center);
        return player;
    }

    @Override
    public boolean is(TagKey<DamageType> tag) {
        // Vanilla's ten-tick immunity would otherwise reject equal damage at tick eight.
        return tag.equals(DamageTypeTags.NO_KNOCKBACK) || tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) || super.is(tag);
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        return Component.translatable("death.attack.immersivefluids.water_pump", victim.getDisplayName());
    }

    public static void touch(ServerLevel level, BlockPos pos, WaterPumpBlockEntity pump, LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !pump.getBlockState().getValue(WaterPumpBlock.POWERED)) return;
        Direction facing = pump.getBlockState().getValue(WaterPumpBlock.FACING);
        Direction right = PumpStructure.right(facing), up = PumpStructure.up(facing);
        double x = (pump.size() - 1) / 2.0 - pump.column();
        double y = (pump.size() - 1) / 2.0 - pump.row();
        Vec3 center = Vec3.atCenterOf(pos).add(x * right.getStepX() + y * up.getStepX(),
                x * right.getStepY() + y * up.getStepY(), x * right.getStepZ() + y * up.getStepZ());
        if (!intersectsPassage(entity.getBoundingBox(), center, facing, pump.size())) return;
        long now = level.getGameTime();
        Long previous = LAST_CONTACT.get(entity);
        if (previous != null && now - previous < 8) return;
        // Shared across constituent blocks and stages, including blocked/immune attempts.
        LAST_CONTACT.put(entity, now);
        entity.hurtServer(level, new PumpDamage(level, center), pump.size() == 1 ? 10F : 5F);
    }

    public static boolean intersectsPassage(AABB bounds, Vec3 center, Direction facing, int size) {
        Vec3 closest = new Vec3(Math.clamp(center.x, bounds.minX, bounds.maxX),
                Math.clamp(center.y, bounds.minY, bounds.maxY), Math.clamp(center.z, bounds.minZ, bounds.maxZ)).subtract(center);
        double axial = facing.getAxis().choose(closest.x, closest.y, closest.z);
        double radialSquared = closest.lengthSqr() - axial * axial;
        double radius = PumpGeometry.openingRadius(size);
        return Math.abs(axial) < .5 && radialSquared < radius * radius;
    }
}
