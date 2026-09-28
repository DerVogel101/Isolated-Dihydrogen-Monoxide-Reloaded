package dervogel101.de.pumpedupwater.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@Mixin(MappedRegistry.class)
public abstract class LegacyRegistryAliasMixin<T> {
    @Shadow @Final private Map<Identifier, Holder.Reference<T>> byLocation;

    private static Identifier renamed(Identifier id) {
        return id != null && id.getNamespace().equals("immersivefluids")
                ? Identifier.fromNamespaceAndPath("pumpedupwater", id.getPath()) : null;
    }

    @Inject(method = "get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$legacyHolder(Identifier id, CallbackInfoReturnable<Optional<Holder.Reference<T>>> cir) {
        if (cir.getReturnValue().isPresent()) return;
        Identifier replacement = renamed(id);
        if (replacement != null) {
            Holder.Reference<T> holder = byLocation.get(replacement);
            if (holder != null) cir.setReturnValue(Optional.of(holder));
        }
    }

    @Inject(method = "get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$legacyKey(ResourceKey<T> key, CallbackInfoReturnable<Optional<Holder.Reference<T>>> cir) {
        if (cir.getReturnValue().isPresent()) return;
        Identifier replacement = renamed(key.identifier());
        if (replacement != null) {
            Holder.Reference<T> holder = byLocation.get(replacement);
            if (holder != null) cir.setReturnValue(Optional.of(holder));
        }
    }

    @Inject(method = "getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$legacyValue(Identifier id, CallbackInfoReturnable<T> cir) {
        if (cir.getReturnValue() != null) return;
        Identifier replacement = renamed(id);
        if (replacement != null) {
            Holder.Reference<T> holder = byLocation.get(replacement);
            if (holder != null) cir.setReturnValue(holder.value());
        }
    }

    @Inject(method = "getValue(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", at = @At("RETURN"), cancellable = true)
    private void pumpedupwater$legacyKeyValue(ResourceKey<T> key, CallbackInfoReturnable<T> cir) {
        if (cir.getReturnValue() != null) return;
        Identifier replacement = renamed(key.identifier());
        if (replacement != null) {
            Holder.Reference<T> holder = byLocation.get(replacement);
            if (holder != null) cir.setReturnValue(holder.value());
        }
    }
}
