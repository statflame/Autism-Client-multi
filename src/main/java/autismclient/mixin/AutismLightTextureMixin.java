package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public abstract class AutismLightTextureMixin {
    //? if >=1.21.5 {
    @Shadow @Final private com.mojang.blaze3d.textures.GpuTexture texture;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", shift = At.Shift.AFTER), cancellable = true, require = 0)
    private void autism$fullbright(float partialTick, CallbackInfo ci) {
        if (!PackModuleRenderUtil.shouldUseBrightLightmap()) return;
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, -1);
        net.minecraft.util.profiling.Profiler.get().pop();
        ci.cancel();
    }
    //?} else {
    /*@Shadow @Final private com.mojang.blaze3d.pipeline.TextureTarget target;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;unbindWrite()V", shift = At.Shift.BEFORE), require = 0)
    private void autism$fullbright(float partialTick, CallbackInfo ci) {
        if (!PackModuleRenderUtil.shouldUseBrightLightmap()) return;
        this.target.setClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.target.clear();
    }
    *///?}
}
