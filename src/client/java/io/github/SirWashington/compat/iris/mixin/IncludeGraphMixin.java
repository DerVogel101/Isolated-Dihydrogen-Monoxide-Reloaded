package io.github.SirWashington.compat.iris.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.SirWashington.compat.iris.IrisMaterials;
import io.github.SirWashington.compat.iris.ShaderSourcePatch;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.FileNode;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@Mixin(value = IncludeGraph.class, remap = false)
public abstract class IncludeGraphMixin {
    @Shadow @Final @Mutable private ImmutableMap<AbsolutePackPath, FileNode> nodes;
    @Inject(method = "<init>(Ljava/nio/file/Path;Lcom/google/common/collect/ImmutableList;Z)V", at = @At("RETURN"))
    private void immersivefluids$patch(Path root, ImmutableList<AbsolutePackPath> starts, boolean zip, CallbackInfo ci) {
        IrisMaterials.supported = false;
        var sources = new HashMap<String, String>();
        nodes.forEach((path, node) -> sources.put(path.getPathString(), String.join("\n", node.getLines())));
        var replacements = ShaderSourcePatch.replacements(sources);
        if (replacements.isEmpty()) {
            if (sources.containsKey(ShaderSourcePatch.BE)) LoggerFactory.getLogger("immersivefluids").warn(
                    "Unrecognized Complementary/Euphoria material sources; Immersive Fluids shader integration disabled.");
            return;
        }
        // Reject reserved dispatch IDs in any property include, before modifying any source.
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".properties")).toList()) {
                String text = Files.readString(path);
                if (text.contains(Integer.toString(IrisMaterials.MACHINERY))
                        || text.contains(Integer.toString(IrisMaterials.ITEM))
                        || text.contains(Integer.toString(IrisMaterials.ICE_ITEM))) {
                    LoggerFactory.getLogger("immersivefluids").warn("Shader dispatch ID collision; integration disabled.");
                    return;
                }
            }
        } catch (IOException e) {
            LoggerFactory.getLogger("immersivefluids").warn("Could not validate shader material IDs; integration disabled.", e);
            return;
        }
        var patched = new HashMap<>(nodes);
        replacements.forEach((name, source) -> {
            var path = AbsolutePackPath.fromAbsolutePath(name);
            patched.put(path, new FileNode(path, ImmutableList.copyOf(source.split("\\R"))));
        });
        nodes = ImmutableMap.copyOf(patched);
        IrisMaterials.supported = true;
        LoggerFactory.getLogger("immersivefluids").info("Enabled shader materials for {} (in memory only)", ShaderSourcePatch.profile(sources));
    }
}
