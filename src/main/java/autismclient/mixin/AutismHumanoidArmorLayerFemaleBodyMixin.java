package autismclient.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.9 {
import autismclient.render.AutismFemaleBodyRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Mixin(HumanoidArmorLayer.class)
public class AutismHumanoidArmorLayerFemaleBodyMixin {
    //? if >=1.21.9 {
    @Inject(
        method = "<init>(Lnet/minecraft/client/renderer/entity/RenderLayerParent;Lnet/minecraft/client/renderer/entity/ArmorModelSet;Lnet/minecraft/client/renderer/entity/ArmorModelSet;Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;)V",
        at = @At("TAIL")
    )
    private void autism$rememberPlayerArmorModels(RenderLayerParent<?, ?> renderer, ArmorModelSet<?> modelSet, ArmorModelSet<?> babyModelSet, EquipmentLayerRenderer equipmentRenderer, CallbackInfo ci) {
        if (renderer instanceof AvatarRenderer) {
            AutismFemaleBodyRenderer.markArmorModels(modelSet);
            if (babyModelSet != modelSet) {
                AutismFemaleBodyRenderer.markArmorModels(babyModelSet);
            }
        }
    }
    //?}
}
