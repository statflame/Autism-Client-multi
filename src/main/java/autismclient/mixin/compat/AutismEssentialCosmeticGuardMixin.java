package autismclient.mixin.compat;

//? if >=1.21.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
//?}
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public abstract class AutismEssentialCosmeticGuardMixin {
    //? if >=1.21.9 {
    @WrapMethod(method = "submitEntities")
    private void autism$catchEssentialCosmeticNpe(
            PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output,
            Operation<Void> original) {
        try {
            original.call(poseStack, levelRenderState, output);
        } catch (NullPointerException npe) {
            String msg = npe.getMessage();
            if (msg == null || !msg.contains("\"o\" is null")) throw npe;

        }
    }
    //?}
}
