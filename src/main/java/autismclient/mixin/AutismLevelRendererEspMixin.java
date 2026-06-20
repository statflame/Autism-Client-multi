package autismclient.mixin;

//? if >=1.21.9 {
import autismclient.modules.PackModuleWorldRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class AutismLevelRendererEspMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void autism$captureTracerMatrices(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean bl, Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog, Vector4f color, boolean bl2, CallbackInfo ci) {
        PackModuleWorldRenderer.captureTracerOrigin(projection, view);
    }

    @Inject(method = "renderBlockOutline", at = @At("HEAD"), require = 0)
    private void autism$renderWorldEsp(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, boolean afterTranslucent, LevelRenderState renderState, CallbackInfo ci) {
        if (!afterTranslucent) return;
        PackModuleWorldRenderer.renderWorld(poseStack, bufferSource);
    }
}
//?} else {
/*@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public class AutismLevelRendererEspMixin { }*/
//?}
