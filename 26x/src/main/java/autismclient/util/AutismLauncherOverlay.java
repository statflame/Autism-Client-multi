package autismclient.util;

import autismclient.commands.AutismCommands;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.ConnectedButton;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectAccordionSection;
import autismclient.gui.vanillaui.direct.DirectCompactRow;
import autismclient.gui.vanillaui.direct.DirectIconLabel;
import autismclient.gui.vanillaui.direct.DirectMetricLabel;
import autismclient.gui.vanillaui.direct.DirectPanel;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectSpacer;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectUiColumn;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.mixin.accessor.AutismHandledScreenAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

public class AutismLauncherOverlay extends AutismOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int DEFAULT_X = 0;
   private static final int DEFAULT_Y = 0;
   private static final String SHARED_MAIN_MENU_LAYOUT = "MAIN_MENU";
   private static final String LEGACY_MODULE_UTILITIES_LAYOUT = "UTILITIES";
   private static final int PANEL_PAD = 0;
   private static final int SECTION_PAD = 4;
   private static final int SECTION_GAP = 3;
   private static final int ROW_GAP = 2;
   private static final int BUTTON_HEIGHT = 20;
   private static final int CHAT_HEIGHT = 20;
   private static final int CHAT_MIN_WIDTH = 136;
   private static final int TEXT_PAD = 8;
   private static final int ICON_SIZE = 14;
   private static final int ICON_GAP = 4;
   private static final int LABEL_ICON_SIZE = 13;
   private static final int MIN_PAIR_WIDTH = 58;
   private static final int MIN_PANEL_WIDTH = 164;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private final CompactTheme theme = new CompactTheme();
   private final DirectPanel panelNode = new DirectPanel();
   private final DirectSurface surface = new DirectSurface(this.theme, this.panelNode);
   private final AutismMacroListOverlay macroListOverlay;
   private final AutismFabricatorOverlay fabricatorOverlay;
   private final AutismLANSyncOverlay lanSyncOverlay;
   private final AutismQueueEditorOverlay queueEditorOverlay;
   private final AutismPacketLoggerOverlay packetLoggerOverlay;
   private final AutismCustomFilterOverlay customFilterOverlay;
   private Supplier<AutismPacketLoggerOverlay> packetLoggerOverlaySupplier;
   private Supplier<AutismServerInfoOverlay> serverInfoOverlaySupplier;
   private final String overlayId;
   private final boolean includeFabricatorButton;
   private final boolean includeScreenSection;
   private final boolean includeChatSection;
   private Runnable closeWithoutPacketAction;
   private Runnable desyncAction;
   private AutismKeybindOverlay keybindOverlay;
   private AutismServerInfoOverlay serverInfoOverlay;
   private DirectUiButton sendButton;
   private DirectUiButton delayButton;
   private CompactTextInput chatField;
   private DirectMetricLabel revMetric;
   private DirectMetricLabel syncMetric;
   private DirectMetricLabel slotMetric;
   private DirectAccordionSection mainMenuSection;
   private int panelX = 0;
   private int panelY = 0;
   private int panelWidth = 164;
   private int panelHeight = 220;
   private boolean visible = true;
   private boolean dragging = false;
   private float dragOffsetX;
   private float dragOffsetY;
   private float pressStartUiX;
   private float pressStartUiY;
   private int pressStartPanelX;
   private int pressStartPanelY;
   private boolean dragMoved = false;

   public AutismLauncherOverlay(
      AutismMacroListOverlay macroListOverlay,
      AutismFabricatorOverlay fabricatorOverlay,
      AutismLANSyncOverlay lanSyncOverlay,
      AutismQueueEditorOverlay queueEditorOverlay,
      AutismPacketLoggerOverlay packetLoggerOverlay,
      AutismCustomFilterOverlay customFilterOverlay
   ) {
      this(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay, "autism-launcher", true, true);
   }

   public AutismLauncherOverlay(
      AutismMacroListOverlay macroListOverlay,
      AutismFabricatorOverlay fabricatorOverlay,
      AutismLANSyncOverlay lanSyncOverlay,
      AutismQueueEditorOverlay queueEditorOverlay,
      AutismPacketLoggerOverlay packetLoggerOverlay,
      AutismCustomFilterOverlay customFilterOverlay,
      String overlayId,
      boolean includeScreenSection,
      boolean includeChatSection
   ) {
      this(
         macroListOverlay,
         fabricatorOverlay,
         lanSyncOverlay,
         queueEditorOverlay,
         packetLoggerOverlay,
         customFilterOverlay,
         overlayId,
         true,
         includeScreenSection,
         includeChatSection
      );
   }

   public AutismLauncherOverlay(
      AutismMacroListOverlay macroListOverlay,
      AutismFabricatorOverlay fabricatorOverlay,
      AutismLANSyncOverlay lanSyncOverlay,
      AutismQueueEditorOverlay queueEditorOverlay,
      AutismPacketLoggerOverlay packetLoggerOverlay,
      AutismCustomFilterOverlay customFilterOverlay,
      String overlayId,
      boolean includeFabricatorButton,
      boolean includeScreenSection,
      boolean includeChatSection
   ) {
      this.macroListOverlay = macroListOverlay;
      this.fabricatorOverlay = fabricatorOverlay;
      this.lanSyncOverlay = lanSyncOverlay;
      this.queueEditorOverlay = queueEditorOverlay;
      this.packetLoggerOverlay = packetLoggerOverlay;
      this.customFilterOverlay = customFilterOverlay;
      this.packetLoggerOverlaySupplier = () -> this.packetLoggerOverlay;
      this.overlayId = overlayId != null && !overlayId.isBlank() ? overlayId : "autism-launcher";
      this.includeFabricatorButton = includeFabricatorButton;
      this.includeScreenSection = includeScreenSection;
      this.includeChatSection = includeChatSection;
      this.buildUi();
      this.restoreLayout();
      if (this.mainMenuSection != null) {
         this.mainMenuSection.syncExpanded(this.mainMenuSection.isExpanded());
      }
   }

   public void setCloseWithoutPacketAction(Runnable action) {
      this.closeWithoutPacketAction = action;
      this.buildUi();
   }

   public void setDesyncAction(Runnable action) {
      this.desyncAction = action;
      this.buildUi();
   }

   public void setKeybindOverlay(AutismKeybindOverlay overlay) {
      this.keybindOverlay = overlay;
   }

   public void setServerDataOverlay(AutismServerInfoOverlay overlay) {
      this.serverInfoOverlay = overlay;
      this.serverInfoOverlaySupplier = () -> this.serverInfoOverlay;
   }

   public void setPacketLoggerOverlaySupplier(Supplier<AutismPacketLoggerOverlay> supplier) {
      this.packetLoggerOverlaySupplier = supplier == null ? () -> this.packetLoggerOverlay : supplier;
   }

   public void setServerDataOverlaySupplier(Supplier<AutismServerInfoOverlay> supplier) {
      this.serverInfoOverlaySupplier = supplier == null ? () -> this.serverInfoOverlay : supplier;
   }

   private AutismPacketLoggerOverlay packetLoggerOverlay() {
      AutismPacketLoggerOverlay overlay = this.packetLoggerOverlaySupplier == null ? this.packetLoggerOverlay : this.packetLoggerOverlaySupplier.get();
      if (overlay != null) {
         AutismOverlayManager.get().register(overlay);
      }

      return overlay;
   }

   private AutismServerInfoOverlay serverInfoOverlay() {
      AutismServerInfoOverlay overlay = this.serverInfoOverlaySupplier == null ? this.serverInfoOverlay : this.serverInfoOverlaySupplier.get();
      if (overlay != null) {
         AutismOverlayManager.get().register(overlay);
      }

      return overlay;
   }

   private void buildUi() {
      this.panelNode.setPadding(DirectUiInsets.all(0)).setDrawBorder(false).setDrawFill(false);
      this.panelNode.content().clearChildren();
      this.panelNode.content().setPadding(DirectUiInsets.NONE).setGap(0);
      this.mainMenuSection = new DirectAccordionSection("Main Menu")
         .setHeaderHeight(this.sectionHeaderHeight())
         .setContentTopGap(this.sectionContentTopGap())
         .setExpanded(true);
      this.mainMenuSection.content().setPadding(new DirectUiInsets(1, 0, 1, 1)).setGap(this.rowGap());
      this.panelNode.content().add(this.mainMenuSection);
      DirectUiButton macrosButton = this.actionButton("Macros", UiAssets.ICON_MACROS, DirectUiButton.Variant.SECONDARY, () -> {
         AutismMacroEditorOverlay macroEditorOverlay = AutismMacroEditorOverlay.getSharedOverlay();
         if (macroEditorOverlay != null && macroEditorOverlay.isVisible()) {
            if (this.macroListOverlay != null) {
               this.macroListOverlay.setVisible(false);
            }

            AutismOverlayManager.get().bringToFront(macroEditorOverlay);
         } else if (this.macroListOverlay != null) {
            this.macroListOverlay.toggle();
         }
      }).setGrowX(true);
      if (this.includeFabricatorButton) {
         this.addPairRow(
            this.mainMenuSection.content(), macrosButton, this.actionButton("Fabricator", UiAssets.ICON_FABRICATOR, DirectUiButton.Variant.SECONDARY, () -> {
               if (this.fabricatorOverlay != null) {
                  this.fabricatorOverlay.toggle();
               }
            }).setGrowX(true)
         );
      } else {
         this.mainMenuSection.content().add(macrosButton);
      }

      this.addPairRow(this.mainMenuSection.content(), this.actionButton("LAN Sync", UiAssets.ICON_LANSYNC, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.lanSyncOverlay != null) {
            this.lanSyncOverlay.toggle();
         }
      }).setGrowX(true), this.actionButton("Queue", UiAssets.ICON_PACKET_Q_EDITOR, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.queueEditorOverlay != null) {
            this.queueEditorOverlay.toggle();
         }
      }).setGrowX(true));
      this.addPairRow(this.mainMenuSection.content(), this.actionButton("Logger", UiAssets.ICON_PACKET_LOGGER, DirectUiButton.Variant.SECONDARY, () -> {
         AutismPacketLoggerOverlay overlay = this.packetLoggerOverlay();
         this.toggleRegisteredUtilityOverlay(overlay);
      }).setGrowX(true), this.actionButton("Packets", UiAssets.ICON_FILTER, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.customFilterOverlay != null) {
            this.customFilterOverlay.toggle();
         }
      }).setGrowX(true));
      this.addPairRow(this.mainMenuSection.content(), this.actionButton("Server Info", UiAssets.ICON_SERVER_INFO, DirectUiButton.Variant.SECONDARY, () -> {
         AutismServerInfoOverlay overlay = this.serverInfoOverlay();
         this.toggleRegisteredUtilityOverlay(overlay);
      }).setGrowX(true), this.actionButton("Settings", UiAssets.ICON_KEYBINDS, DirectUiButton.Variant.SECONDARY, () -> {
         if (this.keybindOverlay != null) {
            this.keybindOverlay.toggle();
         }
      }).setGrowX(true));
      this.addSubCategory("PACKET", UiAssets.ICON_PACKET_CATEGORY);
      this.sendButton = this.actionButton("Send", null, DirectUiButton.Variant.DANGER, this::toggleSendPackets).setConnectedToggle(true).setGrowX(true);
      this.delayButton = this.actionButton("Delay", null, DirectUiButton.Variant.DANGER, this::toggleDelayPackets).setConnectedToggle(true).setGrowX(true);
      this.addPairRow(this.mainMenuSection.content(), this.sendButton, this.delayButton);
      this.addPairRow(
         this.mainMenuSection.content(),
         this.actionButton("Flush", null, DirectUiButton.Variant.SECONDARY, this::flushQueue).setGrowX(true),
         this.actionButton("Clear", null, DirectUiButton.Variant.SECONDARY, this::clearQueue).setGrowX(true)
      );
      if (this.includeScreenSection) {
         this.addSubCategory("SCREEN", UiAssets.ICON_SCREEN_CATEGORY);
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton(
                  "Close",
                  null,
                  DirectUiButton.Variant.SECONDARY,
                  this.closeWithoutPacketAction != null ? this.closeWithoutPacketAction : () -> AutismGuiActions.closeCurrentScreen(MC, false)
               )
               .setGrowX(true),
            this.actionButton("De-sync", null, DirectUiButton.Variant.DANGER, this.desyncAction != null ? this.desyncAction : this::sendDesync).setGrowX(true)
         );
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton("Save", null, DirectUiButton.Variant.SECONDARY, this::saveGui).setGrowX(true),
            this.actionButton("Load", null, DirectUiButton.Variant.SECONDARY, this::loadGui).setGrowX(true)
         );
         this.addPairRow(
            this.mainMenuSection.content(),
            this.actionButton("Title", null, DirectUiButton.Variant.SECONDARY, AutismGuiClipboardUtil::copyGuiTitleJson).setGrowX(true),
            this.actionButton("Disc+Send", null, DirectUiButton.Variant.DANGER, this::disconnectAndSend).setGrowX(true)
         );
      }

      if (this.includeChatSection) {
         this.chatField = new CompactTextInput()
            .setGrowX(true)
            .setFieldHeight(this.chatHeight())
            .setPreferredWidth(this.chatMinWidth())
            .setMinWidth(this.chatMinWidth())
            .setHorizontalPadding(6)
            .setPlaceholder("Type message or /command...")
            .setHistoryNavigationEnabled(true)
            .setHistoryProvider(() -> (Collection<String>)(MC.gui != null ? MC.gui.getChat().getRecentChat() : List.of()))
            .setOnSubmit(this::submitChat);
         this.mainMenuSection.content().add(this.chatField);
         this.mainMenuSection.content().add(new DirectSpacer(0.0F, 2.0F));
         DirectCompactRow syncRow = new DirectCompactRow().setGap(this.syncRowGap()).setPadding(new DirectUiInsets(4, 0, 2, 0)).setUnderlineOnHover(false);
         syncRow.setGrowX(true);
         this.revMetric = syncRow.add(new DirectMetricLabel("Rev: ", "--").setKeyColor(-4743522).setValueColor(-46518).setGrowX(true));
         this.syncMetric = syncRow.add(new DirectMetricLabel("SyncID: ", "--").setKeyColor(-4743522).setValueColor(-791321).setGrowX(true));
         this.slotMetric = syncRow.add(new DirectMetricLabel("Slot: ", "--").setKeyColor(-4743522).setValueColor(-7350273).setGrowX(true));
         this.mainMenuSection.content().add(syncRow);
      } else {
         this.chatField = null;
         this.revMetric = null;
         this.syncMetric = null;
         this.slotMetric = null;
      }
   }

   private void toggleRegisteredUtilityOverlay(IAutismOverlay overlay) {
      if (overlay != null) {
         AutismOverlayManager manager = AutismOverlayManager.get();
         boolean wasRegistered = manager.getOverlays().contains(overlay);
         manager.register(overlay);
         manager.setTemporarilyHidden(overlay, false);
         if (!wasRegistered && overlay.isVisible()) {
            manager.bringToFront(overlay);
         } else {
            overlay.setVisible(!overlay.isVisible());
            if (overlay.isVisible()) {
               manager.bringToFront(overlay);
            }
         }
      }
   }

   private void addSubCategory(String label, Identifier icon) {
      if (!this.mainMenuSection.content().children().isEmpty()) {
         this.mainMenuSection.content().add(new DirectSpacer(0.0F, this.sectionGap()));
      }

      this.mainMenuSection.content().add(new DirectIconLabel(label, icon).setIconSize(this.labelIconSize()).setConnectedStyle(true));
   }

   private DirectUiButton addFullButton(DirectUiColumn parent, String label, Identifier icon, Runnable action) {
      DirectUiButton button = this.actionButton(label, icon, DirectUiButton.Variant.SECONDARY, action).setGrowX(true);
      parent.add(button);
      return button;
   }

   private void addPairRow(DirectUiColumn parent, DirectUiButton left, DirectUiButton right) {
      DirectCompactRow row = new DirectCompactRow().setGap(this.rowGap()).setPadding(DirectUiInsets.NONE).setUnderlineOnHover(false);
      row.add(left.setConnectedEdges(ConnectedButton.LEFT_CELL));
      row.add(right.setConnectedEdges(ConnectedButton.RIGHT_CELL));
      row.setGrowX(true);
      parent.add(row);
   }

   private DirectUiButton actionButton(String label, Identifier icon, DirectUiButton.Variant variant, Runnable action) {
      DirectUiButton button = new DirectUiButton(label, variant, action)
         .setGrowX(false)
         .setConnectedStyle(true)
         .setButtonHeight(this.buttonHeight())
         .setHorizontalPadding(this.buttonPadding())
         .setMinWidth(this.requiredButtonWidth(label, icon))
         .setTextYOffset(0);
      if (icon != null) {
         button.setLeadingIcon(icon)
            .setContentAlignment(DirectUiButton.ContentAlignment.START)
            .setIconSize(this.buttonIconSize())
            .setIconGap(this.buttonIconGap());
      }

      return button;
   }

   @Override
   public String getOverlayId() {
      return this.overlayId;
   }

   @Override
   public int getMinWidth() {
      return this.computePanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.minimumPanelHeight();
   }

   @Override
   public AutismWindowLayout getBounds() {
      return new AutismWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, !this.mainMenuSection.isExpanded());
   }

   @Override
   public void setBounds(AutismWindowLayout bounds) {
      if (bounds != null) {
         AutismWindowLayout clamped = this.clampToViewport(bounds);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.visible = clamped.visible;
         if (this.mainMenuSection != null) {
            this.mainMenuSection.syncExpanded(!clamped.collapsed);
         }
      }
   }

   @Override
   public void saveLayout() {
      AutismSharedState.get().setWindowLayout(this.getOverlayId(), this.getBounds());
      AutismConfig config = AutismConfig.getGlobal();
      AutismConfig.ModuleCategoryLayout layout = config.moduleCategoryLayouts.computeIfAbsent("MAIN_MENU", ignored -> new AutismConfig.ModuleCategoryLayout());
      layout.x = this.panelX;
      layout.y = this.panelY;
      layout.collapsed = this.isCollapsed();
      config.save();
   }

   @Override
   public void restoreLayout() {
      AutismWindowLayout saved = AutismSharedState.get().getWindowLayout(this.getOverlayId());
      boolean savedVisible = saved == null || saved.visible;
      boolean savedCollapsed = saved != null && saved.collapsed;
      AutismConfig config = AutismConfig.getGlobal();
      AutismConfig.ModuleCategoryLayout layout = config.moduleCategoryLayouts.get("MAIN_MENU");
      if (layout == null) {
         AutismConfig.ModuleCategoryLayout old = config.moduleCategoryLayouts.get("UTILITIES");
         if (old != null) {
            layout = new AutismConfig.ModuleCategoryLayout();
            layout.x = old.x;
            layout.y = old.y;
            layout.collapsed = old.collapsed;
            config.moduleCategoryLayouts.put("MAIN_MENU", layout);
         }
      }

      if (layout != null && layout.x >= 0 && layout.y >= 0) {
         this.setBounds(new AutismWindowLayout(layout.x, layout.y, this.panelWidth, this.panelHeight, savedVisible, layout.collapsed || savedCollapsed));
      } else if (saved != null) {
         this.setBounds(saved);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         AutismSharedState shared = AutismSharedState.get();
         if (this.sendButton != null) {
            boolean sending = shared.shouldSendGuiPackets();
            this.sendButton.setText("Send");
            this.sendButton.setVariant(sending ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
         }

         if (this.delayButton != null) {
            boolean delaying = shared.shouldDelayGuiPackets();
            this.delayButton.setText("Delay");
            this.delayButton.setVariant(delaying ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
         }

         this.updateSyncMetrics();
         this.panelWidth = this.computePanelWidth();
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, this.theme, uiMouseX, uiMouseY, delta);
         this.panelHeight = Math.max(this.getMinHeight(), Math.round(this.panelNode.preferredHeight(metrics, this.panelWidth)));
         AutismWindowLayout clamped = this.clampToViewport(
            new AutismWindowLayout(
               this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.mainMenuSection != null && !this.mainMenuSection.isExpanded()
            )
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.panelNode.setActive(true);
         this.panelNode.setBounds(this.panelX, this.panelY, this.panelWidth, this.panelHeight);
         this.surface.render(context, mouseX, mouseY, delta);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (button == 0 && this.isOverMainMenuHeader(uiMouseX, uiMouseY) && this.mainMenuSection != null) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            this.pressStartUiX = uiMouseX;
            this.pressStartUiY = uiMouseY;
            this.pressStartPanelX = this.panelX;
            this.pressStartPanelY = this.panelY;
            return true;
         } else {
            return this.surface.mouseClicked(mouseX, mouseY, button) ? true : this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else {
         return this.surface.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (!this.dragging) {
         return this.surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         AutismWindowLayout clamped = this.clampToViewport(
            new AutismWindowLayout(
               Math.round(uiMouseX - this.dragOffsetX),
               Math.round(uiMouseY - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.mainMenuSection != null && !this.mainMenuSection.isExpanded()
            )
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.dragMoved = this.dragMoved
            || Math.abs(uiMouseX - this.pressStartUiX) >= 3.0F
            || Math.abs(uiMouseY - this.pressStartUiY) >= 3.0F
            || this.panelX != this.pressStartPanelX
            || this.panelY != this.pressStartPanelY;
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      return this.surface.mouseScrolled(mouseX, mouseY, amount);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.visible && !this.isCollapsed() ? this.surface.keyPressed(keyCode, scanCode, modifiers) : false;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.isCollapsed() ? this.surface.charTyped(chr, modifiers) : false;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (!visible) {
         this.surface.clearFocusedTextInputs();
         this.dragging = false;
         this.dragMoved = false;
      } else if (this.mainMenuSection != null) {
         this.mainMenuSection.syncExpanded(this.mainMenuSection.isExpanded());
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiX = viewport.toUiX(mouseX);
         float uiY = viewport.toUiY(mouseY);
         return uiX >= this.panelX && uiX < this.panelX + this.panelWidth && uiY >= this.panelY && uiY < this.panelY + this.panelHeight;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         return this.isOverMainMenuHeader(viewport.toUiX(mouseX), viewport.toUiY(mouseY));
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean isCollapsed() {
      return this.mainMenuSection != null && !this.mainMenuSection.isExpanded();
   }

   @Override
   public void setCollapsed(boolean collapsed) {
      if (this.isCollapsed() != collapsed) {
         if (this.mainMenuSection != null) {
            this.mainMenuSection.syncExpanded(!collapsed);
         }

         if (collapsed) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.surface.hasFocusedTextInput();
   }

   @Override
   public void clearTextFieldFocus() {
      this.surface.clearFocusedTextInputs();
   }

   private boolean isOverMainMenuHeader(float uiMouseX, float uiMouseY) {
      return this.mainMenuSection != null
         && uiMouseX >= this.panelX
         && uiMouseX < this.panelX + this.panelWidth
         && uiMouseY >= this.panelY
         && uiMouseY < this.panelY + this.sectionHeaderHeight();
   }

   private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
      DirectViewport viewport = this.surface.viewport();
      int margin = 4;
      int viewportW = Math.round(viewport.uiWidth());
      int viewportH = Math.round(viewport.uiHeight());
      int availableW = Math.max(1, viewportW - margin * 2);
      int availableH = Math.max(this.sectionHeaderHeight(), viewportH - margin * 2);
      int width = Math.max(Math.min(this.getMinWidth(), availableW), Math.min(bounds.width, availableW));
      int minHeight = bounds.collapsed ? this.sectionHeaderHeight() : this.getMinHeight();
      int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
      int renderedHeight = bounds.collapsed ? this.sectionHeaderHeight() : height;
      int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
      int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
      return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
   }

   private int computePanelWidth() {
      if (MC.font == null) {
         return this.minPanelWidth();
      } else {
         int pairWidth = this.minPairWidth();
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Macros", UiAssets.ICON_MACROS));
         if (this.includeFabricatorButton) {
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Fabricator", UiAssets.ICON_FABRICATOR));
         }

         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("LAN Sync", UiAssets.ICON_LANSYNC));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Queue", UiAssets.ICON_PACKET_Q_EDITOR));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Logger", UiAssets.ICON_PACKET_LOGGER));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Packets", UiAssets.ICON_FILTER));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Server Info", UiAssets.ICON_SERVER_INFO));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Settings", UiAssets.ICON_KEYBINDS));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Send", null));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Delay", null));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Flush", null));
         pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Clear", null));
         if (this.includeScreenSection) {
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Close", null));
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("De-sync", null));
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Save", null));
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Load", null));
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Title", null));
            pairWidth = Math.max(pairWidth, this.requiredButtonWidth("Disc+Send", null));
         }

         int chatWidth = this.includeChatSection ? Math.max(this.chatMinWidth(), this.requiredTextFieldWidth("Type message or /command...")) : 0;
         int headerWidth = UiText.width(MC.font, "MAIN MENU", this.theme.fontFor(UiTone.LABEL), this.theme.color(UiTone.LABEL))
            + this.labelIconSize()
            + this.buttonIconGap()
            + this.headerReserveWidth();
         int subLabelWidth = this.requiredIconLabelWidth("PACKET");
         if (this.includeScreenSection) {
            subLabelWidth = Math.max(subLabelWidth, this.requiredIconLabelWidth("SCREEN"));
         }

         int contentWidth = Math.max(headerWidth, Math.max(subLabelWidth, Math.max(pairWidth * 2 + this.rowGap(), chatWidth)));
         return Math.max(this.minPanelWidth(), contentWidth + this.sectionPadding() * 2 + this.panelWidthReserve());
      }
   }

   private int requiredButtonWidth(String label, Identifier icon) {
      if (MC.font == null) {
         return this.minPairWidth();
      } else {
         int width = UiText.width(MC.font, label, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY))
            + this.buttonPadding() * 2
            + this.buttonOutlineReserve();
         if (icon != null) {
            width += this.buttonIconSize() + this.buttonIconGap();
         }

         return width;
      }
   }

   private int requiredIconLabelWidth(String label) {
      return UiText.width(MC.font, label, this.theme.fontFor(UiTone.LABEL), this.theme.color(UiTone.LABEL))
         + this.labelIconSize()
         + this.buttonIconGap()
         + this.buttonOutlineReserve();
   }

   private int requiredTextFieldWidth(String placeholder) {
      return UiText.width(MC.font, placeholder, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.MUTED))
         + this.textFieldExtraWidth()
         + this.buttonOutlineReserve();
   }

   private int sectionPadding() {
      return 0;
   }

   private int sectionGap() {
      return 0;
   }

   private int rowGap() {
      return 0;
   }

   private int buttonHeight() {
      return 15;
   }

   private int buttonPadding() {
      return 5;
   }

   private int buttonIconSize() {
      return 12;
   }

   private int buttonIconGap() {
      return 3;
   }

   private int labelIconSize() {
      return 11;
   }

   private int minPairWidth() {
      return 54;
   }

   private int minPanelWidth() {
      return 152;
   }

   private int minimumPanelHeight() {
      return 56;
   }

   private int sectionHeaderHeight() {
      return 15;
   }

   private int sectionContentTopGap() {
      return 0;
   }

   private int chatHeight() {
      return 16;
   }

   private int chatMinWidth() {
      return 124;
   }

   private int syncRowGap() {
      return 4;
   }

   private int headerReserveWidth() {
      return 16;
   }

   private int textFieldExtraWidth() {
      return 10;
   }

   private int buttonOutlineReserve() {
      return 2;
   }

   private int panelWidthReserve() {
      return 2;
   }

   private void toggleSendPackets() {
      AutismSharedState shared = AutismSharedState.get();
      boolean newValue = !shared.shouldSendGuiPackets();
      AutismModule.get().applySendGuiPacketsUiBehavior(newValue);
      AutismNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? -13248397 : -50373);
   }

   private void toggleDelayPackets() {
      AutismSharedState shared = AutismSharedState.get();
      boolean newValue = !shared.shouldDelayGuiPackets();
      AutismModule module = AutismModule.get();
      int sent = module.applyDelayGuiPacketsUiBehavior(newValue);
      module.notifyDelayPacketsUiResult(newValue, sent);
   }

   private void flushQueue() {
      AutismModule module = AutismModule.get();
      int count = module.flushQueuedPacketsUiBehavior();
      module.notifyFlushQueuedPacketsUiResult(count);
   }

   private void clearQueue() {
      AutismModule module = AutismModule.get();
      int count = module.clearQueuedPacketsUiBehavior();
      module.notifyClearQueuedPacketsUiResult(count);
   }

   private void sendDesync() {
      if (!AutismGuiActions.desyncCurrentScreen(MC)) {
         AutismClientMessaging.sendPrefixed("Failed to desync: no open networked GUI.");
      }
   }

   private void saveGui() {
      AutismGuiActions.saveCurrentGui(MC);
   }

   private void loadGui() {
      if (AutismModule.get().restoreSavedScreenUiBehavior()) {
         AutismNotifications.show("GUI restored.", -13248397);
      } else {
         AutismNotifications.error("No stored GUI.");
      }
   }

   private void disconnectAndSend() {
      if (!PackHideState.isHardLocked()) {
         AutismSharedState shared = AutismSharedState.get();
         AutismModule.get().setDelayGuiPackets(false);
         if (MC.getConnection() != null) {
            shared.flushDelayedPackets(MC.getConnection());
            MC.getConnection().getConnection().disconnect(Component.literal("Disconnecting (Autism)"));
         }
      }
   }

   private void submitChat(String raw) {
      if (!PackHideState.isHardLocked()) {
         if (raw != null) {
            String message = raw.trim();
            if (!message.isEmpty()) {
               if (AutismCommands.isAutismCommandMessage(message)) {
                  if (AutismCommands.isBlockedPanicCommandMessage(message)) {
                     if (this.chatField != null) {
                        this.chatField.setText("");
                        this.chatField.setFocused(false);
                     }

                     return;
                  }

                  String body = AutismCommands.commandBody(message);
                  if (!body.isBlank()) {
                     AutismCommands.dispatch(body);
                  }
               } else {
                  if (MC.getConnection() == null) {
                     return;
                  }

                  if (message.startsWith("/") && message.length() > 1) {
                     MC.getConnection().sendCommand(message.substring(1));
                  } else {
                     MC.getConnection().sendChat(message);
                  }
               }

               if (MC.gui != null) {
                  MC.gui.getChat().addRecentChat(message);
               }

               if (this.chatField != null) {
                  this.chatField.addHistoryEntry(message);
               }

               if (this.chatField != null) {
                  this.chatField.setText("");
                  this.chatField.setFocused(false);
               }
            }
         }
      }
   }

   private void updateSyncMetrics() {
      String revisionText = "--";
      String syncIdText = "--";
      String slotText = "--";
      if (MC.player != null && MC.player.containerMenu != null) {
         revisionText = Integer.toString(MC.player.containerMenu.getStateId());
         syncIdText = Integer.toString(MC.player.containerMenu.containerId);
         if (MC.screen instanceof AbstractContainerScreen<?> handledScreen) {
            Slot focusedSlot = ((AutismHandledScreenAccessor)handledScreen).getFocusedSlot();
            if (focusedSlot != null) {
               slotText = Integer.toString(AutismInventoryHelper.toUserVisibleSlot(MC, focusedSlot.index));
            }
         }
      }

      if (this.revMetric != null) {
         this.revMetric.setValue(revisionText);
      }

      if (this.syncMetric != null) {
         this.syncMetric.setValue(syncIdText);
      }

      if (this.slotMetric != null) {
         this.slotMetric.setValue(slotText);
      }
   }
}
