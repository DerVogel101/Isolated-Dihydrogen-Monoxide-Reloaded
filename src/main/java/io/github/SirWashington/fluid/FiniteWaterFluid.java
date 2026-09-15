package io.github.SirWashington.fluid;

import io.github.SirWashington.WaterPhysicsConfig;
import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.features.FiniteWaterRainfall;
import io.github.SirWashington.features.FiniteWaterPhysics;
import io.github.SirWashington.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public abstract class FiniteWaterFluid extends FlowingFluid {
    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_FINITE_WATER;
    }

    @Override
    public Fluid getSource() {
        return ModFluids.FINITE_WATER;
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return ModFluids.isFiniteWater(fluid);
    }

    @Override
    public Item getBucket() {
        return ModItems.FINITE_WATER_BUCKET;
    }

    @Override
    protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.UNDERWATER,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
    }

    @Nullable
    @Override
    protected ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_WATER;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        effects.apply(InsideBlockEffectType.EXTINGUISH);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return WaterPhysicsConfig.puddleSearchRadius();
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.FINITE_WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public int getDropOff(LevelReader level) {
        return 1;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return WaterPhysicsConfig.flowTickDelay();
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    @Override
    protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        FiniteWaterRainfall.randomTick(level, pos, random);
    }

    @Override
    public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
        FiniteWaterPhysics.tick(level, pos);
    }

    public static final class Flowing extends FiniteWaterFluid {
        @Override
        protected boolean isRandomlyTicking() {
            return true;
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static final class Source extends FiniteWaterFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
