package io.github.SirWashington;

import io.github.SirWashington.block.ModBlocks;
import io.github.SirWashington.compat.iris.IrisMaterials;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;

/** Exercises the transformed camera against real client fluid states, restoring the disposable cell. */
final class ShaderCameraProbe extends Camera {
    static void run() {
        var client = Minecraft.getInstance();
        var level = client.level;
        var pos = client.player.blockPosition().above(8);
        var previous = level.getBlockState(pos);
        boolean integrated = IrisMaterials.supported;
        var camera = new ShaderCameraProbe();
        camera.setLevel(level);
        camera.setEntity(client.player);
        camera.update(client.getDeltaTracker());
        try {
            IrisMaterials.supported = false;
            for (var block : new Block[]{Blocks.WATER, ModBlocks.FINITE_WATER, Blocks.LAVA, Blocks.AIR}) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                camera.setPosition(pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5);
                FogType expected = block == Blocks.LAVA ? FogType.LAVA : block == Blocks.AIR ? FogType.NONE : FogType.WATER;
                if (camera.getFluidInCamera() != expected) throw new AssertionError("Camera fluid recognition: " + block);
                if (block == ModBlocks.FINITE_WATER) {
                    if (level.getFluidState(pos).is(FluidTags.WATER)) throw new AssertionError("Finite water leaked into vanilla gameplay tags");
                    verifyWaterVision(client, pos);
                    camera.setPosition(pos.getX() + .5, pos.getY() + .999, pos.getZ() + .5);
                    if (camera.getFluidInCamera() != FogType.NONE) throw new AssertionError("Camera above finite water is not submerged");
                }
            }
            System.out.println("SHADER_CAMERA_FLUID_PASS");
        } finally {
            level.setBlock(pos, previous, Block.UPDATE_CLIENTS);
            IrisMaterials.supported = integrated;
        }
    }

    private static void verifyWaterVision(Minecraft client, BlockPos pos) {
        var player = client.player;
        var previousPosition = player.position();
        try {
            var timer = player.getClass().getDeclaredField("waterVisionTime");
            timer.setAccessible(true);
            int previousTime = timer.getInt(player);
            try {
                timer.setInt(player, 600);
                player.setPos(pos.getX() + .5, pos.getY() + .25 - player.getEyeHeight(), pos.getZ() + .5);
                if (player.getWaterVision() != 1.0F) throw new AssertionError("Finite water vision differs from vanilla water");
            } finally {
                player.setPos(previousPosition);
                timer.setInt(player, previousTime);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not check finite water vision", e);
        }
        System.out.println("SHADER_WATER_VISION_PASS");
    }
}
