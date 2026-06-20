package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
//? if >=1.21.5 {
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
//?} else {
/*@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)*///?}
public abstract class AutismIndigoTerrainMixin {
    @Shadow protected BlockRenderInfo blockInfo;

    @Inject(method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xrayTerrain(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!PackModuleRenderUtil.hasXrayRenderWork() || blockInfo == null) return;
        BlockState state = blockInfo.blockState;
        BlockPos pos = blockInfo.blockPos;
        int alpha = PackModuleRenderUtil.xrayAlpha(state, pos);
        if (alpha == 0) {
            ci.cancel();
            return;
        }
        if (alpha <= 0 || alpha >= 255) return;
        int alphaBits = (alpha & 0xFF) << 24;
        for (int i = 0; i < 4; i++) {
            quad.color(i, alphaBits | (quad.color(i) & 0x00FFFFFF));
        }
    }
}
