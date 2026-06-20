package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public class AutismXrayModelRendererMixin {
    @Unique private static final ThreadLocal<Integer> AUTISM_XRAY_ALPHA = ThreadLocal.withInitial(() -> -1);

    //? if >=1.21.5 {
    @Inject(method = {"tesselateWithAO", "tesselateWithoutAO"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xrayBlockAlpha(BlockAndTintGetter level, java.util.List<net.minecraft.client.renderer.block.model.BlockModelPart> parts, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, boolean cull, int packedLight, CallbackInfo ci) {
        autism$beginXray(state, pos, ci);
    }
    //?} else {
    /*@Inject(method = {"tesselateWithAO", "tesselateWithoutAO"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xrayBlockAlpha(BlockAndTintGetter level, net.minecraft.client.resources.model.BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, boolean cull, net.minecraft.util.RandomSource random, long seed, int packedLight, CallbackInfo ci) {
        autism$beginXray(state, pos, ci);
    }*///?}

    @Unique
    private void autism$beginXray(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!PackModuleRenderUtil.hasXrayRenderWork()) {
            AUTISM_XRAY_ALPHA.set(-1);
            return;
        }
        int alpha = PackModuleRenderUtil.xrayAlpha(state, pos);
        if (alpha == 0) {
            AUTISM_XRAY_ALPHA.set(-1);
            ci.cancel();
            return;
        }
        AUTISM_XRAY_ALPHA.set(alpha);
    }

    //? if >=1.21.11 {
    @ModifyArgs(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"), require = 0)
    private void autism$xrayQuadAlpha(Args args) {
        int alpha = AUTISM_XRAY_ALPHA.get();
        if (alpha > 0 && alpha < 255) args.set(6, alpha / 255.0F);
    }
    //?} else {
    /*@ModifyArgs(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"), require = 0)
    private void autism$xrayQuadAlpha(Args args) {
        int alpha = AUTISM_XRAY_ALPHA.get();
        if (alpha > 0 && alpha < 255) args.set(6, alpha / 255.0F);
    }*///?}
}
