package io.github.SirWashington.mixin;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PointedDripstoneBlock.class)
public abstract class MixinPointedDripstoneBlock extends SpeleothemBlock {
    protected MixinPointedDripstoneBlock(BlockState support, Properties properties) {
        super(support, properties);
    }

    @Shadow
    private static Optional<BlockPos> findRootBlock(Level level, BlockPos pos, BlockState state, int limit) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean canDripThrough(BlockGetter level, BlockPos pos, BlockState state) {
        throw new AssertionError();
    }

    @Shadow
    private static void spawnDripParticle(Level level, BlockPos tip, BlockState state, Fluid fluid, BlockPos source) {
        throw new AssertionError();
    }

    @Unique
    private static boolean immersivefluids$hasFullSource(Level level, BlockPos source) {
        return level.hasChunkAt(source)
                && ModFluids.isFiniteWater(level.getFluidState(source).getType())
                && FiniteWaterPhysics.getWaterLevel(level, source) == 8;
    }

    @Inject(method = "maybeTransferFluid", at = @At("HEAD"))
    private static void immersivefluids$drip(BlockState state, ServerLevel level, BlockPos pos,
                                            float randomValue, CallbackInfo ci) {
        if (!WaterPhysicsConfig.dripstoneEnabled()
                || randomValue >= WaterPhysicsConfig.dripstoneFillChance()
                || !isStalactiteStartPos(state, level, pos)
                || !immersivefluids$hasFullSource(level, pos.above(2))) {
            return;
        }
        BlockPos tip = findTip(state, level, pos, 11, false);
        if (tip == null || !isFreeHangingStalactite(level.getBlockState(tip))) {
            return;
        }
        // Match vanilla's bounded drip path. Only air and finite-water puddles receive water.
        BlockPos lastAir = null;
        for (int distance = 1; distance < 11; distance++) {
            BlockPos target = tip.below(distance);
            if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target)) {
                return;
            }
            BlockState targetState = level.getBlockState(target);
            boolean finiteWater = ModFluids.isFiniteWater(targetState.getFluidState().getType());
            if (finiteWater && targetState.getCollisionShape(level, target).isEmpty()) {
                int amount = FiniteWaterPhysics.getWaterLevel(level, target);
                if (amount > 0 && amount < 8) {
                    FiniteWaterPhysics.setWaterLevel(level, target, amount + 1);
                    level.levelEvent(1504, tip, 0);
                }
                return;
            }
            if ((!targetState.getFluidState().isEmpty() && !finiteWater)
                    || targetState.getBlock() instanceof AbstractCauldronBlock) {
                return;
            }
            if (!canDripThrough(level, target, targetState)) {
                if (lastAir != null) {
                    FiniteWaterPhysics.setWaterLevel(level, lastAir, 1);
                    level.levelEvent(1504, tip, 0);
                }
                return;
            }
            lastAir = targetState.isAir() ? target : null;
        }
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void immersivefluids$animateDrip(BlockState state, Level level, BlockPos pos,
                                            RandomSource random, CallbackInfo ci) {
        if (WaterPhysicsConfig.dripstoneEnabled() && isFreeHangingStalactite(state)) {
            findRootBlock(level, pos, state, 11)
                    .map(BlockPos::above)
                    .filter(source -> immersivefluids$hasFullSource(level, source))
                    .ifPresent(source -> {
                        if (random.nextFloat() <= 0.12F) {
                            spawnDripParticle(level, pos, state, Fluids.WATER, source);
                        }
                        ci.cancel();
                    });
        }
    }
}
