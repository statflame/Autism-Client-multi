//? if <1.21.6 {
/*package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AutismPanoramaCompat {
    private static final PanoramaRenderer PANORAMA =
        new PanoramaRenderer(new CubeMap(ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama")));

    private AutismPanoramaCompat() {
    }

    public static void render(GuiGraphics graphics, int width, int height, boolean spin) {
        PANORAMA.render(graphics, width, height, 1.0F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
*///?}
