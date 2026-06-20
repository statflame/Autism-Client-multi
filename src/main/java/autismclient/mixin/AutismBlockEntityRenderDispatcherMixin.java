package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
//? if >=1.21.9 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public class AutismBlockEntityRenderDispatcherMixin {
    //? if >=1.21.9 {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void autism$xrayBlockEntity(E blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfoReturnable<S> cir) {
        if (!PackModuleRenderUtil.hasXrayRenderWork()) return;
        if (blockEntity != null && PackModuleRenderUtil.xrayAlpha(blockEntity.getBlockState(), blockEntity.getBlockPos()) == 0) cir.setReturnValue(null);
    }
    //?}
}
