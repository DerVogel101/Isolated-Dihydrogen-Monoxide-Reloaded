package io.github.SirWashington.compat.iris.mixin;

import io.github.SirWashington.compat.iris.IrisMaterials;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IdMap.class, remap = false)
public abstract class ItemIdMapMixin {
    @Inject(method = "parseItemIdMap", at = @At("RETURN"), cancellable = true)
    private static void immersivefluids$items(CallbackInfoReturnable<Object2IntMap<NamespacedId>> cir) {
        if (!IrisMaterials.supported) return;
        var ids = new Object2IntOpenHashMap<>(cir.getReturnValue());
        ids.defaultReturnValue(-1);
        for (var key : BuiltInRegistries.ITEM.keySet()) {
            if (!key.getNamespace().equals("immersivefluids")) continue;
            var id = new NamespacedId(key.getNamespace(), key.getPath());
            if (ids.containsKey(id)) continue;
            int value = IrisMaterials.ITEM;
            if (key.getPath().equals("precision_bucket") || key.getPath().equals("finite_water_bucket"))
                value = ids.getOrDefault(new NamespacedId("minecraft", "bucket"), value);
            ids.put(id, value);
        }
        cir.setReturnValue(Object2IntMaps.unmodifiable(ids));
    }
}
