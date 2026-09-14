package io.github.SirWashington.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mrcrayfish.framework.config.FrameworkConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Framework only supports dot/dash separators; scope the directory override to our two files. */
@Mixin(value = FrameworkConfigManager.class, remap = false)
public abstract class FrameworkConfigPathMixin {
    @ModifyExpressionValue(method = {"createFrameworkConfig", "createTempConfig"},
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private static String immersivefluids$configDirectory(String filename) {
        return switch (filename) {
            case "immersivefluids.server.toml" -> "immersivefluids/server.toml";
            case "immersivefluids.waterlogging.toml" -> "immersivefluids/waterlogging.toml";
            default -> filename;
        };
    }
}
