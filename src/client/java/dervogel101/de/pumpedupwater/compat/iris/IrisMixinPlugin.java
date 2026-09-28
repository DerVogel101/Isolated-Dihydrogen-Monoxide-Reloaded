package dervogel101.de.pumpedupwater.compat.iris;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Set;

/** Does not reference any optional mod classes: safe when Iris is absent. */
public final class IrisMixinPlugin implements IMixinConfigPlugin {
    private boolean irisSupported, sodiumSupported;
    @Override public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("iris")) return;
        irisSupported = version("iris", "1.11.6");
        sodiumSupported = version("sodium", "0.9.2") || version("sodium", "0.9.3-alpha.1");
        if (!irisSupported) LoggerFactory.getLogger("pumpedupwater").warn(
                "Shader integration requires Iris 1.11.6 for Minecraft 26.3; using ordinary rendering.");
        else if (!sodiumSupported) LoggerFactory.getLogger("pumpedupwater").warn(
                "Unverified Sodium version: Sodium renderer hooks disabled; Iris material and camera hooks remain enabled.");
    }
    private static boolean version(String id, String expected) {
        return FabricLoader.getInstance().getModContainer(id).map(mod ->
                mod.getMetadata().getVersion().getFriendlyString().split("\\+")[0].equals(expected)).orElse(false);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return irisSupported && (sodiumSupported || !mixin.endsWith(".SodiumBlockRendererMixin")
                && !mixin.endsWith(".SodiumFluidRendererMixin"));
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
