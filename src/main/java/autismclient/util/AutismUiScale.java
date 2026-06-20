package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AutismUiScale {
   public static final int FIXED_GUI_SCALE = 2;
   private static final double EPSILON = 1.0E-6;
   private static int overlayScaleDepth = 0;

   private AutismUiScale() {
   }

   public static double toVirtual(double value) {
      return value / getOverlayDrawScale();
   }

   public static int toVirtualInt(double value) {
      double virtual = toVirtual(value);
      return virtual < 0.0 ? (int)Math.floor(virtual) : (int)Math.round(virtual);
   }

   public static int getVirtualScreenWidth() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null) {
         int width = (int)(mc.getWindow().getWidth() / 2.0);
         return mc.getWindow().getWidth() / 2.0 > width ? width + 1 : width;
      } else {
         return 0;
      }
   }

   public static int getVirtualScreenHeight() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getWindow() != null) {
         int height = (int)(mc.getWindow().getHeight() / 2.0);
         return mc.getWindow().getHeight() / 2.0 > height ? height + 1 : height;
      } else {
         return 0;
      }
   }

   public static float getOverlayDrawScale() {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.getWindow() != null && mc.getWindow().getGuiScale() > 0 ? (float) (2.0F / mc.getWindow().getGuiScale()) : 1.0F;
   }

   public static boolean isOverlayScaleActive() {
      return overlayScaleDepth > 0;
   }

   public static boolean isFixedOverlayScaleActive() {
      return isOverlayScaleActive() && Math.abs(getOverlayDrawScale() - 1.0F) > 0.001F;
   }

   public static int virtualToFramebufferX(int x) {
      return (int)Math.floor(x * 2.0);
   }

   public static int virtualToFramebufferY(int y) {
      return (int)Math.floor(y * 2.0);
   }

   public static int virtualToFramebufferSize(int size) {
      return Math.max(0, (int)Math.ceil(size * 2.0));
   }

   public static void pushOverlayScale(GuiGraphicsExtractor context) {
      if (context != null) {
         context.pose().pushMatrix();
         if (overlayScaleDepth == 0) {
            float scale = getOverlayDrawScale();
            context.pose().scale(scale, scale);
         }

         overlayScaleDepth++;
      }
   }

   public static void popOverlayScale(GuiGraphicsExtractor context) {
      if (context != null) {
         if (overlayScaleDepth > 0) {
            overlayScaleDepth--;
         }

         context.pose().popMatrix();
      }
   }

   public static void enableOverlayScissor(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2) {
      if (context != null) {
         if (x2 > x1 && y2 > y1) {
            int expandRight = overlayScissorExpansionForEndpoint(x1, x2);
            int expandBottom = overlayScissorExpansionForEndpoint(y1, y2);
            int screenW = Math.max(0, getVirtualScreenWidth());
            int screenH = Math.max(0, getVirtualScreenHeight());
            int left = clamp(x1, 0, Math.max(0, screenW));
            int top = clamp(y1, 0, Math.max(0, screenH));
            int right = clamp(x2 + expandRight, left, Math.max(left, screenW + expandRight));
            int bottom = clamp(y2 + expandBottom, top, Math.max(top, screenH + expandBottom));
            context.enableScissor(left, top, right, bottom);
         } else {
            context.enableScissor(x1, y1, x1, y1);
         }
      }
   }

   private static int overlayScissorExpansionForEndpoint(int virtualStart, int virtualEnd) {
      int virtualLength = virtualEnd - virtualStart;
      if (isOverlayScaleActive() && virtualLength > 0) {
         double scale = getOverlayDrawScale();
         if (scale <= 0.0) {
            return 0;
         } else {
            int transformedStart = (int)Math.floor(virtualStart * scale + 1.0E-6);
            int desiredEnd = (int)Math.ceil(virtualEnd * scale - 1.0E-6);
            int desiredWidth = Math.max(0, desiredEnd - transformedStart);
            int currentWidth = (int)Math.floor(virtualLength * scale + 1.0E-6);
            if (currentWidth >= desiredWidth) {
               return 0;
            } else {
               int requiredVirtualLength = (int)Math.ceil((desiredWidth - 1.0E-6) / scale);
               return Math.max(1, requiredVirtualLength - virtualLength);
            }
         }
      } else {
         return 0;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }
}
