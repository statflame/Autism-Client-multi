package autismclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiGraphics.class)
public abstract class AutismGuiGraphicsDuckMixin implements GuiGraphicsExtractor {
}
