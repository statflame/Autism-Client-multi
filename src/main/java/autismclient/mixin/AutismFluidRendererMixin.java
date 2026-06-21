package autismclient.mixin;

import autismclient.modules.PackModuleRenderUtil;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public class AutismFluidRendererMixin {
    @Unique private static final ThreadLocal<Integer> PACKUTIL_XRAY_FLUID_ALPHA = ThreadLocal.withInitial(() -> -1);

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true, require = 1)
    private void autism$xrayFluidMode(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
        if (!PackModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = PackModuleRenderUtil.xrayFluidAlpha(level, pos, fluidState);
        if (alpha == 0) {
            ci.cancel();
            return;
        }
        PACKUTIL_XRAY_FLUID_ALPHA.set(alpha);
    }

    @Inject(method = "tesselate", at = @At("RETURN"), require = 1)
    private void autism$clearXrayFluidMode(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
    }

    @Inject(method = "vertex", at = @At("HEAD"), cancellable = true, require = 1)
    private void autism$xrayFluidAlpha(VertexConsumer consumer, float x, float y, float z, float red, float green, float blue, float u, float v, int packedLight, CallbackInfo ci) {
        int alpha = PACKUTIL_XRAY_FLUID_ALPHA.get();
        if (alpha == -1) return;
        consumer.addVertex(x, y, z)
            .setColor(red, green, blue, alpha / 255.0F)
            .setUv(u, v)
            .setLight(packedLight)
            .setNormal(0.0F, 1.0F, 0.0F);
        ci.cancel();
    }
}
