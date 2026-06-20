package autismclient.mixin.security;

import autismclient.security.AutismProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(Options.class)
public class AutismProtectorOptionsMixin {
    //? if >=1.21.9 {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"), require = 0)
    private KeyMapping autism$keyMapping(String name, int key, KeyMapping.Category category, Operation<KeyMapping> original) {
    //?} else {
    /*@WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILjava/lang/String;)Lnet/minecraft/client/KeyMapping;"), require = 0)
    private KeyMapping autism$keyMapping(String name, int key, String category, Operation<KeyMapping> original) {*///?}
        AutismProtectorTracker.addVanillaKeybind(name);
        return original.call(name, key, category);
    }

    //? if >=1.21.9 {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"), require = 0)
    private KeyMapping autism$keyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category, Operation<KeyMapping> original) {
    //?} else {
    /*@WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)Lnet/minecraft/client/KeyMapping;"), require = 0)
    private KeyMapping autism$keyMapping(String name, InputConstants.Type type, int key, String category, Operation<KeyMapping> original) {*///?}
        AutismProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category);
    }

    //? if >=1.21.11 {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)Lnet/minecraft/client/KeyMapping;"), require = 0)
    private KeyMapping autism$keyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category, int order, Operation<KeyMapping> original) {
        AutismProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category, order);
    }
    //?}

    //? if >=1.21.9 {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"), require = 0)
    private ToggleKeyMapping autism$toggleKeyMapping(String name, int key, KeyMapping.Category category, BooleanSupplier needsToggle,
                                                     boolean shouldRestore, Operation<ToggleKeyMapping> original) {
    //?} else {
    /*@WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILjava/lang/String;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"), require = 0)
    private ToggleKeyMapping autism$toggleKeyMapping(String name, int key, String category, BooleanSupplier needsToggle,
                                                     boolean shouldRestore, Operation<ToggleKeyMapping> original) {*///?}
        AutismProtectorTracker.addVanillaKeybind(name);
        return original.call(name, key, category, needsToggle, shouldRestore);
    }

    //? if >=1.21.9 {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"), require = 0)
    private ToggleKeyMapping autism$toggleKeyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category,
                                                     BooleanSupplier needsToggle, boolean shouldRestore,
                                                     Operation<ToggleKeyMapping> original) {
    //?} else {
    /*@WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"), require = 0)
    private ToggleKeyMapping autism$toggleKeyMapping(String name, InputConstants.Type type, int key, String category,
                                                     BooleanSupplier needsToggle, boolean shouldRestore,
                                                     Operation<ToggleKeyMapping> original) {*///?}
        AutismProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category, needsToggle, shouldRestore);
    }
}
