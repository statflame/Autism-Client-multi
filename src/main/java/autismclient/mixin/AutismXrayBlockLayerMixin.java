package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public class AutismXrayBlockLayerMixin {
    //? if >=1.21.6 {
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$xrayLayer(BlockState state, CallbackInfoReturnable<net.minecraft.client.renderer.chunk.ChunkSectionLayer> cir) {
        if (!PackModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = PackModuleRenderUtil.xrayAlpha(state, null);
        if (alpha > 0 && alpha < 255) cir.setReturnValue(net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
    }
    //?} else {
    /*@Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$xrayLayer(BlockState state, CallbackInfoReturnable<net.minecraft.client.renderer.RenderType> cir) {
        if (!PackModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = PackModuleRenderUtil.xrayAlpha(state, null);
        if (alpha > 0 && alpha < 255) cir.setReturnValue(net.minecraft.client.renderer.RenderType.translucent());
    }*///?}
}
