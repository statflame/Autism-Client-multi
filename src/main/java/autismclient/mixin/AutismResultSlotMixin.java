package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismSharedState;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class AutismResultSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void autism$mayPlaceForXCarry(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (autism$craftingAllowed()) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean autism$craftingAllowed() {
        AutismModule mod = AutismModule.get();
        boolean modAllow = mod != null && mod.isXCarryUseCrafting();
        boolean bypass = AutismSharedState.get().isXCarryArmorBypass();
        return modAllow || bypass;
    }
}
