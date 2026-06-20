package autismclient.mixin.security;

import autismclient.security.AutismRegistryComponentCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Properties.class)
public abstract class AutismItemPropertiesMixin {
    @WrapOperation(
        method = "lambda$delayedHolderComponent$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/HolderLookup$Provider;getOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"),
        require = 0)
    private static <T> Reference<T> autism$skipMissingTrimMaterialHolder(Provider context, ResourceKey<T> valueKey, Operation<Reference<T>> original) {
        try {
            return original.call(context, valueKey);
        } catch (RuntimeException e) {
            if (!AutismRegistryComponentCompat.shouldSkipMissingDelayedHolder(valueKey, e)) {
                throw e;
            }
            AutismRegistryComponentCompat.reportSkippedMissingTrimMaterial(valueKey);
            return null;
        }
    }
}
