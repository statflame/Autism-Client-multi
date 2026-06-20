package autismclient.mixin;

import autismclient.ducks.AutismExternalButtonScreen;
import autismclient.gui.vanillaui.components.ScreenButton;
import autismclient.mixin.accessor.AutismScreenAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismLecternButtons;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.AutismUiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class AutismScreenMixin {
   @Unique
   private static final Minecraft MC = Minecraft.getInstance();
   @Unique
   private boolean autism$lecternInitialized;
   @Unique
   private AutismQueueEditorOverlay autism$queueEditorOverlay;
   @Unique
   private AutismPacketLoggerOverlay autism$packetLoggerOverlay;

   @Inject(
      method = {"init()V"},
      at = {@At("TAIL")}
   )
   private void autism$onInit(CallbackInfo ci) {
      Screen screen = (Screen)(Object)this;
      if (screen instanceof LecternScreen && this.autism$isModuleActive()) {
         if (this.autism$lecternInitialized) {
            if (this.autism$queueEditorOverlay != null) {
               this.autism$queueEditorOverlay.restoreState();
            }

            if (this.autism$queueEditorOverlay != null) {
               AutismOverlayManager.get().register(this.autism$queueEditorOverlay);
            }
         } else {
            Font textRenderer = ((AutismScreenAccessor)this).getFont();
            this.autism$queueEditorOverlay = new AutismQueueEditorOverlay(textRenderer);
            this.autism$queueEditorOverlay.restoreState();
            AutismOverlayManager.get().register(this.autism$queueEditorOverlay);
            this.autism$lecternInitialized = true;
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void autism$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      Screen screen = (Screen)(Object)this;
      if (screen instanceof AutismExternalButtonScreen externalButtonScreen) {
         externalButtonScreen.autism$renderExternalButtons(context, mouseX, mouseY, delta);
      }

      if (this.autism$isModuleActive()) {
         Font textRenderer = ((AutismScreenAccessor)this).getFont();
         if (textRenderer != null) {
            if (screen instanceof LecternScreen && MC.player != null) {
               AbstractContainerMenu handler = MC.player.containerMenu;
               if (handler != null) {
                  int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
                  int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
                  AutismUiScale.pushOverlayScale(context);

                  try {
                     for (ScreenButton button : AutismLecternButtons.build(MC, this.autism$queueEditorOverlay)) {
                        button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                     }
                  } finally {
                     AutismUiScale.popOverlayScale(context);
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void autism$renderTopmostNotifications(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.autism$isModuleActive() && AutismNotifications.hasVisible()) {
         context.nextStratum();
         AutismUiScale.pushOverlayScale(context);

         try {
            AutismNotifications.render(context);
         } finally {
            AutismUiScale.popOverlayScale(context);
         }
      }
   }

   @Inject(
      method = {"onClose"},
      at = {@At("HEAD")}
   )
   private void autism$onClose(CallbackInfo ci) {
      Screen screen = (Screen)(Object)this;
      if (screen instanceof LecternScreen) {
         AutismOverlayManager.get().unregister(this.autism$queueEditorOverlay);
         AutismOverlayManager.get().unregister(this.autism$packetLoggerOverlay);
      }
   }

   @Unique
   private boolean autism$isModuleActive() {
      if (PackHideState.isHardLocked()) {
         return false;
      } else {
         AutismModule module = AutismModule.get();
         return module != null && module.isActive();
      }
   }
}
