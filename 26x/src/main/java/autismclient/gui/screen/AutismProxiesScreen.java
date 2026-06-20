package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismProxyType;
import autismclient.util.AutismUiScale;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class AutismProxiesScreen extends Screen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int BG = -15856112;
   private static final int PANEL_BG = -401074149;
   private static final int PANEL_BG_SOFT = -1206643689;
   private static final int BORDER = -13425624;
   private static final int BORDER_ACTIVE = -13248397;
   private static final int TEXT = -855310;
   private static final int MUTED = -6645094;
   private static final int SUCCESS = -13248397;
   private static final int ERROR = -42149;
   private static final int WARN = -14249;
   private static final int DEFAULT_COLOR = -7429889;
   private static final int PANEL_WIDTH = 530;
   private static final int PANEL_MARGIN = 12;
   private static final int ROW_HEIGHT = 32;
   private static final int TOP_PANEL_Y = 20;
   private static final int TOP_PANEL_HEIGHT = 156;
   private static final int LIST_TOP = 178;
   private static final int LIST_HEADER_HEIGHT = 22;
   private static final int LIST_BOTTOM_MARGIN = 12;
   private static final int LIST_SCROLLBAR_WIDTH = 4;
   private static final int LIST_SCROLLBAR_GUTTER = 12;
   private static final int ROW_TOGGLE_W = 44;
   private static final int ROW_CHECK_W = 56;
   private static final int ROW_DELETE_W = 22;
   private static final int ROW_ACTION_GAP = 6;
   private static final int ROW_ACTION_RIGHT_PAD = 8;
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<AutismProxiesScreen.ProxyRow> proxyRows = new ArrayList<>();
   private final ScrollState proxyListScroll = new ScrollState();
   private EditBox nameField;
   private EditBox addressField;
   private EditBox portField;
   private EditBox usernameField;
   private EditBox passwordField;
   private EditBox searchField;
   private AutismProxyType type = AutismProxyType.Socks5;
   private AutismProxiesScreen.ProxyFilter filter = AutismProxiesScreen.ProxyFilter.ALL;
   private AutismProxy selectedProxy;
   private String searchQuery = "";
   private int proxyListScrollOffset;
   private boolean proxyScrollbarDragging;
   private int proxyScrollbarGrabOffset;
   private long lastRefreshRevision = Long.MIN_VALUE;
   private long lastImportRevision = Long.MIN_VALUE;
   private long cachedListRevision = Long.MIN_VALUE;
   private long cachedRefreshRevision = Long.MIN_VALUE;
   private String cachedSearchQuery = null;
   private AutismProxiesScreen.ProxyFilter cachedFilter = null;
   private List<AutismProxy> cachedFilteredProxies = List.of();

   public AutismProxiesScreen(Screen parent) {
      super(Component.literal("Proxies"));
      this.parent = parent;
   }

   protected void init() {
      int formX = this.panelX() + 10;
      int inputX = formX + 8;
      int inputW = Math.max(1, this.panelWidth() - 36);
      int gap = 8;
      int portW = Math.max(42, Math.min(72, inputW / 5));
      int remaining = Math.max(2, inputW - portW - gap * 2);
      int nameW = Math.max(1, remaining * 2 / 5);
      int addressW = Math.max(1, remaining - nameW);
      int halfW = Math.max(1, (inputW - gap) / 2);
      this.nameField = this.addField(inputX, 48, nameW, "Name");
      this.addressField = this.addField(inputX + nameW + gap, 48, addressW, "Address");
      this.portField = this.addField(inputX + nameW + gap + addressW + gap, 48, portW, "Port");
      this.usernameField = this.addField(inputX, 72, halfW, "Username");
      this.passwordField = this.addField(inputX + halfW + gap, 72, Math.max(1, inputW - halfW - gap), "Password");
      this.searchField = this.addField(inputX, 122, inputW, "Search proxies");
      this.searchField.setHint(Component.literal("Search name/address..."));
      this.searchField.setResponder(value -> {
         this.searchQuery = safeTrim(value);
         this.proxyListScrollOffset = 0;
         this.proxyListScroll.jumpTo(0, 0);
         this.rebuildButtons();
      });
      this.setFormFieldsVisible(!this.compactLayout());
      AutismProxyManager.get().requestGeoLookup(false);
      this.rebuildButtons();
   }

   private EditBox addField(int x, int y, int w, String hint) {
      EditBox field = new EditBox(this.font, x, y, w, 18, Component.literal(hint));
      field.setHint(Component.literal(hint));
      field.setMaxLength(256);
      this.addRenderableWidget(field);
      return field;
   }

   private void rebuildButtons() {
      this.buttons.clear();
      this.proxyRows.clear();
      int formX = this.panelX() + 10;
      this.buttons
         .add(
            CompactOverlayButton.create(10, 10, 76, 18, Component.literal("Back"), b -> this.minecraft.setScreen(this.parent))
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      if (!this.compactLayout()) {
         int actionX = formX + 8;
         int actionGap = 6;
         int usableW = Math.max(1, this.panelWidth() - 36);
         AutismProxyManager manager = AutismProxyManager.get();
         AutismProxyManager.RefreshStatus refreshStatus = manager.refreshStatus();
         AutismProxyManager.ImportStatus importStatus = manager.importStatus();
         boolean refreshRunning = refreshStatus.running();
         boolean importRunning = importStatus.running();
         int[] actionWidths = this.scaledWidths(usableW, actionGap, 112, 64, 64, 72, 72, 64, 64);
         this.buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[0], 18, Component.literal("Type: " + this.type), b -> {
            this.type = this.type == AutismProxyType.Socks5 ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
            this.rebuildButtons();
         }).setVariant(CompactOverlayButton.Variant.SECONDARY));
         actionX += actionWidths[0] + actionGap;
         this.buttons
            .add(CompactOverlayButton.create(actionX, 96, actionWidths[1], 18, Component.literal(this.selectedProxy == null ? "Add" : "Update"), b -> {
               if (this.selectedProxy == null) {
                  this.addProxy();
               } else {
                  this.updateProxy();
               }
            }).setVariant(CompactOverlayButton.Variant.PRIMARY));
         actionX += actionWidths[1] + actionGap;
         this.buttons
            .add(
               CompactOverlayButton.create(
                     actionX, 96, actionWidths[2], 18, Component.literal(this.selectedProxy == null ? "Clear" : "Cancel"), b -> this.clearProxySelection()
                  )
                  .setVariant(this.selectedProxy == null ? CompactOverlayButton.Variant.SECONDARY : CompactOverlayButton.Variant.DANGER)
            );
         actionX += actionWidths[2] + actionGap;
         CompactOverlayButton refresh = CompactOverlayButton.create(
               actionX, 96, actionWidths[3], 18, Component.literal(refreshRunning ? "Cancel" : "Refresh"), b -> this.refreshProxies()
            )
            .setVariant(refreshRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SECONDARY);
         refresh.active = !importRunning;
         this.buttons.add(refresh);
         actionX += actionWidths[3] + actionGap;
         CompactOverlayButton cleanup = CompactOverlayButton.create(actionX, 96, actionWidths[4], 18, Component.literal("Cleanup"), b -> this.cleanupProxies())
            .setVariant(CompactOverlayButton.Variant.SECONDARY);
         cleanup.active = !refreshRunning && !importRunning;
         this.buttons.add(cleanup);
         actionX += actionWidths[4] + actionGap;
         this.buttons
            .add(
               CompactOverlayButton.create(actionX, 96, actionWidths[5], 18, Component.literal(importRunning ? "Cancel" : "Import"), b -> this.importProxies())
                  .setVariant(importRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SECONDARY)
            );
         actionX += actionWidths[5] + actionGap;
         this.buttons
            .add(
               CompactOverlayButton.create(
                     actionX, 96, actionWidths[6], 18, Component.literal("Config"), b -> this.minecraft.setScreen(new AutismProxyConfigScreen(this))
                  )
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
         int filterX = formX + 8;
         int filterGap = 6;
         int[] filterWidths = this.scaledWidths(usableW, filterGap, 52, 56, 56, 78, 68, 48, 70);
         this.addFilterButton(filterX, 148, filterWidths[0], AutismProxiesScreen.ProxyFilter.ALL, "All");
         filterX += filterWidths[0] + filterGap;
         this.addFilterButton(filterX, 148, filterWidths[1], AutismProxiesScreen.ProxyFilter.ALIVE, "Alive");
         filterX += filterWidths[1] + filterGap;
         this.addFilterButton(filterX, 148, filterWidths[2], AutismProxiesScreen.ProxyFilter.DEAD, "Dead");
         filterX += filterWidths[2] + filterGap;
         this.addFilterButton(filterX, 148, filterWidths[3], AutismProxiesScreen.ProxyFilter.UNCHECKED, "Unchecked");
         filterX += filterWidths[3] + filterGap;
         this.addFilterButton(filterX, 148, filterWidths[4], AutismProxiesScreen.ProxyFilter.ENABLED, "Enabled");
         filterX += filterWidths[4] + filterGap;
         CompactOverlayButton sort = CompactOverlayButton.create(filterX, 148, filterWidths[5], 18, Component.literal("Sort"), b -> this.sortProxies())
            .setVariant(CompactOverlayButton.Variant.SECONDARY);
         sort.active = !refreshRunning && !importRunning && manager.size() > 1;
         this.buttons.add(sort);
         filterX += filterWidths[5] + filterGap;
         CompactOverlayButton clearAll = CompactOverlayButton.create(
               filterX, 148, filterWidths[6], 18, Component.literal("Clear All"), b -> this.clearAllProxies()
            )
            .setVariant(CompactOverlayButton.Variant.DANGER);
         clearAll.active = !refreshRunning && !importRunning && manager.size() > 0;
         this.buttons.add(clearAll);
         List<AutismProxy> proxies = this.filteredProxies();
         int maxScroll = this.proxyMaxScroll(proxies.size());
         this.proxyListScrollOffset = clampScrollOffset(this.proxyListScrollOffset, maxScroll);
         this.proxyListScroll.jumpTo(this.proxyListScrollOffset, maxScroll);
         int firstVisible = this.proxyListScrollOffset / 32;
         int rowY = this.proxyRowsTop() - this.proxyListScrollOffset % 32;

         for (int i = firstVisible; i < proxies.size() && rowY + 32 - 3 <= this.proxyRowsBottom(); i++) {
            AutismProxy proxy = proxies.get(i);
            if (rowY + 32 - 3 <= this.proxyRowsTop()) {
               rowY += 32;
            } else {
               CompactOverlayButton toggle = CompactOverlayButton.create(
                  this.proxyRowX() + 8, rowY + 7, 44, 16, Component.literal(proxy.enabled ? "ON" : "OFF"), b -> this.toggleProxy(proxy)
               );
               toggle.setVariant(proxy.enabled ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER)
                  .setToggleState(proxy.enabled)
                  .setAnimationKey("proxy-enabled:" + proxy.address + ":" + proxy.port);
               int deleteX = this.proxyRowRight() - 8 - 22;
               int checkX = deleteX - 6 - 56;
               CompactOverlayButton check = CompactOverlayButton.create(
                  checkX, rowY + 7, 56, 16, Component.literal(proxy.status == AutismProxy.Status.CHECKING ? "..." : "Check"), b -> this.checkProxy(proxy)
               );
               check.setVariant(CompactOverlayButton.Variant.PRIMARY);
               check.active = proxy.status != AutismProxy.Status.CHECKING;
               CompactOverlayButton delete = CompactOverlayButton.create(deleteX, rowY + 7, 22, 16, Component.literal("X"), b -> this.deleteProxy(proxy));
               delete.setVariant(CompactOverlayButton.Variant.DANGER);
               this.proxyRows.add(new AutismProxiesScreen.ProxyRow(proxy, rowY, toggle, check, delete));
               rowY += 32;
            }
         }
      }
   }

   private void addFilterButton(int x, int y, int width, AutismProxiesScreen.ProxyFilter value, String label) {
      CompactOverlayButton button = CompactOverlayButton.create(x, y, width, 18, Component.literal(label), b -> {
         this.filter = value;
         this.proxyListScrollOffset = 0;
         this.proxyListScroll.jumpTo(0, 0);
         this.rebuildButtons();
      });
      button.setVariant(this.filter == value ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.FILTER_OFF);
      this.buttons.add(button);
   }

   public void tick() {
      super.tick();
      int maxScroll = this.proxyMaxScroll(this.filteredProxies().size());
      this.proxyListScrollOffset = clampScrollOffset(this.proxyListScrollOffset, maxScroll);
      this.proxyListScroll.jumpTo(this.proxyListScrollOffset, maxScroll);
      AutismProxyManager.RefreshStatus status = AutismProxyManager.get().refreshStatus();
      if (status.revision() != this.lastRefreshRevision) {
         this.lastRefreshRevision = status.revision();
         this.rebuildButtons();
      }

      AutismProxyManager.ImportStatus importStatus = AutismProxyManager.get().importStatus();
      if (importStatus.revision() != this.lastImportRevision) {
         this.lastImportRevision = importStatus.revision();
         this.rebuildButtons();
      }
   }

   private void addProxy() {
      AutismProxy proxy = this.proxyFromFields();
      if (proxy != null) {
         if (AutismProxyManager.get().add(proxy)) {
            this.selectedProxy = null;
            this.clearProxyFields();
            this.showProxyToast("Added proxy " + proxy.address + ":" + proxy.port + ".", -13248397);
            this.checkProxy(proxy);
         } else {
            this.showProxyToast("Proxy already exists or is invalid.", -42149);
         }

         this.rebuildButtons();
      }
   }

   private void updateProxy() {
      if (this.selectedProxy != null) {
         AutismProxy updated = this.proxyFromFields();
         if (updated != null) {
            if (AutismProxyManager.get().update(this.selectedProxy, updated)) {
               this.showProxyToast("Updated " + this.selectedProxy.displayName() + ".", -13248397);
               this.clearProxySelection();
            } else {
               this.showProxyToast("Proxy already exists or is invalid.", -42149);
               this.rebuildButtons();
            }
         }
      }
   }

   private void toggleProxy(AutismProxy proxy) {
      if (proxy != null) {
         AutismProxyManager.get().setEnabled(proxy, !proxy.enabled);
         this.showProxyToast(proxy.enabled ? "Enabled " + proxy.displayName() + "." : "Disabled proxy.", proxy.enabled ? -13248397 : -6645094);
         this.rebuildButtons();
      }
   }

   private void deleteProxy(AutismProxy proxy) {
      if (proxy != null) {
         AutismProxyManager.get().remove(proxy);
         if (proxy.equals(this.selectedProxy)) {
            this.selectedProxy = null;
         }

         this.showProxyToast("Deleted " + proxy.displayName() + ".", -6645094);
         this.rebuildButtons();
      }
   }

   private void checkProxy(AutismProxy proxy) {
      if (proxy != null && proxy.status != AutismProxy.Status.CHECKING) {
         proxy.status = AutismProxy.Status.CHECKING;
         proxy.latency = 0L;
         this.rebuildButtons();
         Thread thread = new Thread(() -> {
            proxy.checkStatus(AutismProxyManager.get().getTimeoutMs());
            if (this.minecraft != null) {
               this.minecraft.execute(() -> {
                  this.showProxyToast(proxy.displayName() + " is " + this.statusText(proxy) + ".", proxy.status.color());
                  this.rebuildButtons();
               });
            }
         }, "Autism-Proxy-Check");
         thread.setDaemon(true);
         thread.start();
      }
   }

   private void refreshProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      if (manager.importStatus().running()) {
         this.showProxyToast("Finish import before refreshing.", -14249);
      } else {
         AutismProxyManager.RefreshStatus currentStatus = manager.refreshStatus();
         if (currentStatus.running()) {
            if (manager.cancelRefresh()) {
               this.showProxyToast("Proxy refresh canceled.", -14249);
               this.rebuildButtons();
            }
         } else if (manager.size() == 0) {
            this.showProxyToast("No proxies to refresh.", -14249);
         } else if (!manager.startRefresh(true)) {
            this.showProxyToast("Proxy refresh could not start.", -14249);
         } else {
            long generation = manager.refreshStatus().generation();
            this.showProxyToast("Refreshing proxies...", -7429889);
            this.rebuildButtons();
            Thread watcher = new Thread(() -> {
               while (manager.refreshStatus().running()) {
                  try {
                     Thread.sleep(100L);
                  } catch (InterruptedException var5x) {
                     return;
                  }
               }

               if (this.minecraft != null) {
                  this.minecraft.execute(() -> {
                     AutismProxyManager.RefreshStatus finished = manager.refreshStatus();
                     if (finished.generation() == generation && finished.checked() >= finished.total()) {
                        this.showProxyToast("Proxy refresh finished.", -13248397);
                     }

                     this.rebuildButtons();
                  });
               }
            }, "Autism-Proxy-Refresh-Watch");
            watcher.setDaemon(true);
            watcher.start();
         }
      }
   }

   private void cleanupProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      if (manager.refreshStatus().running()) {
         this.showProxyToast("Cancel refresh before cleanup.", -14249);
      } else if (manager.importStatus().running()) {
         this.showProxyToast("Finish import before cleanup.", -14249);
      } else {
         int before = manager.size();
         manager.clean();
         int removed = Math.max(0, before - manager.size());
         this.showProxyToast(
            removed == 0 ? "Cleanup finished. Nothing removed." : "Cleanup removed " + removed + " proxies.", removed == 0 ? -6645094 : -13248397
         );
         this.rebuildButtons();
      }
   }

   private void sortProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      if (manager.refreshStatus().running()) {
         this.showProxyToast("Cancel refresh before sorting proxies.", -14249);
      } else if (manager.importStatus().running()) {
         this.showProxyToast("Finish import before sorting proxies.", -14249);
      } else {
         if (manager.sortByLatencyNow()) {
            this.showProxyToast("Sorted proxies by latency.", -13248397);
         } else {
            this.showProxyToast("Nothing to sort.", -6645094);
         }

         this.rebuildButtons();
      }
   }

   private void clearAllProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      if (manager.refreshStatus().running()) {
         this.showProxyToast("Cancel refresh before clearing proxies.", -14249);
      } else if (manager.importStatus().running()) {
         this.showProxyToast("Finish import before clearing proxies.", -14249);
      } else {
         int removed = manager.clearAll();
         this.selectedProxy = null;
         this.clearProxyFields();
         this.proxyListScrollOffset = 0;
         this.proxyListScroll.jumpTo(0, 0);
         this.showProxyToast(removed == 0 ? "Proxy list is already empty." : "Cleared " + removed + " proxies.", removed == 0 ? -6645094 : -13248397);
         this.rebuildButtons();
      }
   }

   private void importProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      AutismProxyManager.ImportStatus status = manager.importStatus();
      if (status.running()) {
         if (manager.cancelImport()) {
            this.showProxyToast("Proxy import canceled.", -14249);
            this.rebuildButtons();
         }
      } else if (manager.refreshStatus().running()) {
         this.showProxyToast("Cancel refresh before importing.", -14249);
      } else {
         PointerBuffer filters = BufferUtils.createPointerBuffer(1);
         ByteBuffer txtFilter = MemoryUtil.memASCII("*.txt");
         filters.put(txtFilter);
         filters.rewind();
         String selectedFile = TinyFileDialogs.tinyfd_openFileDialog("Import Proxies", null, filters, null, false);
         if (selectedFile != null) {
            File file = new File(selectedFile);
            if (!manager.startImport(file)) {
               this.showProxyToast("Proxy import could not start.", -14249);
            } else {
               long generation = manager.importStatus().generation();
               this.showProxyToast("Importing proxies...", -7429889);
               this.rebuildButtons();
               Thread watcher = new Thread(() -> {
                  while (manager.importStatus().running()) {
                     try {
                        Thread.sleep(100L);
                     } catch (InterruptedException var5x) {
                        return;
                     }
                  }

                  if (this.minecraft != null) {
                     this.minecraft.execute(() -> {
                        AutismProxyManager.ImportStatus finished = manager.importStatus();
                        if (finished.generation() == generation && !finished.canceled()) {
                           this.showProxyToast("Imported " + finished.added() + " proxies.", finished.added() > 0 ? -13248397 : -14249);
                           this.proxyListScrollOffset = 0;
                           this.proxyListScroll.jumpTo(0, 0);
                        }

                        this.rebuildButtons();
                     });
                  }
               }, "Autism-Proxy-Import-Watch");
               watcher.setDaemon(true);
               watcher.start();
            }
         }

         MemoryUtil.memFree(txtFilter);
      }
   }

   private void clearProxyFields() {
      this.nameField.setValue("");
      this.addressField.setValue("");
      this.portField.setValue("");
      this.usernameField.setValue("");
      this.passwordField.setValue("");
   }

   private void clearProxySelection() {
      this.selectedProxy = null;
      this.clearProxyFields();
      this.clearInputFocus();
      this.rebuildButtons();
   }

   private AutismProxy proxyFromFields() {
      AutismProxy proxy = new AutismProxy();
      proxy.name = safeTrim(this.nameField.getValue());
      proxy.address = safeTrim(this.addressField.getValue());
      proxy.username = safeTrim(this.usernameField.getValue());
      proxy.password = this.passwordField.getValue();
      proxy.type = this.type;
      if (proxy.address.isBlank()) {
         this.showProxyToast("Enter a proxy address first.", -14249);
         return null;
      } else {
         try {
            proxy.port = Integer.parseInt(safeTrim(this.portField.getValue()));
         } catch (NumberFormatException var3) {
            this.showProxyToast("Invalid proxy port.", -14249);
            return null;
         }

         if (!proxy.isValid()) {
            this.showProxyToast("Proxy must have a valid address and port.", -14249);
            return null;
         } else {
            return proxy;
         }
      }
   }

   private void showProxyToast(String message, int accentColor) {
      if (message != null && !message.isBlank() && this.minecraft != null) {
         this.minecraft.execute(() -> AutismNotifications.show(message, accentColor));
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
      int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
      AutismUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -15856112);
         int panelX = this.panelX();
         this.drawPanel(graphics, panelX + 10, 20, this.panelWidth() - 20, 156, -401074149);
         this.drawPanel(graphics, this.listX(), 178, this.listWidth(), this.listPanelHeight(), -1206643689);
         this.drawText(graphics, "Proxies", panelX + 22, 30, -855310, false);
         if (this.compactLayout()) {
            this.drawText(graphics, "Window too small.", panelX + 22, 54, -6645094, false, Math.max(0, this.panelWidth() - 44));
         }

         List<AutismProxy> proxies = this.filteredProxies();
         AutismProxyManager manager = AutismProxyManager.get();
         int total = manager.size();
         int enabled = manager.enabledCount();
         AutismProxyManager.RefreshStatus refreshStatus = manager.refreshStatus();
         AutismProxyManager.ImportStatus importStatus = manager.importStatus();
         String summary = proxies.size() == total ? total + " proxies  enabled " + enabled : proxies.size() + " / " + total + " proxies  enabled " + enabled;
         String workLabel = this.importStatusText(importStatus);
         int workColor = importStatus.running() ? -7429889 : -14249;
         if (workLabel.isBlank()) {
            workLabel = this.refreshStatusText(refreshStatus);
            workColor = refreshStatus.canceling() ? -14249 : -7429889;
         }

         int workLabelW = workLabel.isBlank() ? 0 : 160;
         this.drawText(graphics, summary, this.listX() + 12, 188, -855310, false, this.listWidth() - 24 - workLabelW);
         if (!workLabel.isBlank()) {
            this.drawText(graphics, workLabel, this.listX() + this.listWidth() - workLabelW - 12, 188, workColor, false, workLabelW);
         }

         this.renderWorkProgress(graphics, refreshStatus, importStatus);
         UiScissorStack.global()
            .push(
               graphics,
               UiBounds.of(
                  this.proxyRowX(),
                  this.proxyRowsTop(),
                  Math.max(0, this.proxyRowRight() - this.proxyRowX()),
                  Math.max(0, this.proxyRowsBottom() - this.proxyRowsTop())
               )
            );

         try {
            for (AutismProxiesScreen.ProxyRow row : this.proxyRows) {
               this.renderProxyRow(graphics, row, virtualMouseX, virtualMouseY);
            }
         } finally {
            UiScissorStack.global().pop(graphics);
         }

         if (total == 0) {
            this.drawText(
               graphics,
               "No proxies saved yet. Add one manually or import a .txt list.",
               this.listX() + 12,
               this.proxyRowsTop() + 12,
               -6645094,
               false,
               this.listWidth() - 24
            );
         } else if (proxies.isEmpty()) {
            this.drawText(
               graphics, "No proxies match the current search or filter.", this.listX() + 12, this.proxyRowsTop() + 12, -6645094, false, this.listWidth() - 24
            );
         }

         for (CompactOverlayButton button : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
         }

         if (!this.compactLayout()) {
            CompactScrollbar.Metrics scrollbar = this.proxyScrollbarMetrics(proxies.size());
            CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), this.proxyScrollbarDragging);
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         AutismUiScale.popOverlayScale(graphics);
      }
   }

   public void removed() {
      super.removed();
   }

   private void renderProxyRow(GuiGraphicsExtractor graphics, AutismProxiesScreen.ProxyRow row, int mouseX, int mouseY) {
      AutismProxy proxy = row.proxy;
      boolean selected = proxy.equals(this.selectedProxy);
      int x = this.proxyRowX();
      int y = row.y;
      int w = this.proxyRowWidth();
      int fill = proxy.enabled ? 858052714 : (selected ? 606804509 : 403771667);
      int border = proxy.enabled ? -13248397 : (selected ? -10866632 : -13425624);
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, 28), fill, border);
      String name = proxy.displayName().isBlank() ? "(unnamed)" : proxy.displayName();
      boolean hasAuth = proxy.username != null && !proxy.username.isBlank();
      String address = proxy.type + "  " + proxy.address + ":" + proxy.port + (hasAuth ? "  Auth" : "");
      String region = proxy.geoLabel();
      String status = this.statusText(proxy);
      int nameX = row.toggleButton.getX() + row.toggleButton.getWidth() + 12;
      int metaRight = row.checkButton.getX() - 8;
      int metaW = Math.min(96, Math.max(48, metaRight - nameX - 44));
      int metaX = Math.max(nameX + 36, metaRight - metaW);
      int textRight = Math.max(nameX + 24, metaX - 8);
      int textW = Math.max(20, textRight - nameX);
      int metaTextW = Math.max(20, metaRight - metaX);
      this.drawText(graphics, name, nameX, y + 6, proxy.enabled ? -13248397 : -855310, false, textW);
      this.drawText(graphics, address, nameX, y + 18, -6645094, false, textW);
      this.drawText(graphics, region, metaX, y + 6, proxy.geoColor(), false, metaTextW);
      this.drawText(graphics, status, metaX, y + 18, proxy.status.color(), false, metaTextW);
      CompactOverlayButton.renderStyled(graphics, this.font, row.toggleButton, mouseX, mouseY);
      CompactOverlayButton.renderStyled(graphics, this.font, row.checkButton, mouseX, mouseY);
      CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() != 0) {
         return super.mouseClicked(virtualEvent, doubleClick);
      } else if (this.compactLayout()) {
         return super.mouseClicked(virtualEvent, doubleClick);
      } else {
         CompactScrollbar.Metrics scrollbar = this.proxyScrollbarMetrics(this.filteredProxies().size());
         if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            this.proxyScrollbarDragging = true;
            this.proxyScrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
               ? Math.max(0, (int)Math.round(virtualEvent.y()) - scrollbar.thumbY())
               : scrollbar.thumbHeight() / 2;
            this.proxyListScrollOffset = clampScrollOffset(
               CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.proxyScrollbarGrabOffset), scrollbar.maxScroll()
            );
            this.proxyListScroll.jumpTo(this.proxyListScrollOffset, scrollbar.maxScroll());
            this.rebuildButtons();
            this.clearInputFocus();
            return true;
         } else {
            for (CompactOverlayButton button : this.buttons) {
               if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }
            }

            for (AutismProxiesScreen.ProxyRow row : this.proxyRows) {
               if (this.overButton(row.toggleButton, virtualEvent.x(), virtualEvent.y())
                  || this.overButton(row.checkButton, virtualEvent.x(), virtualEvent.y())
                  || this.overButton(row.deleteButton, virtualEvent.x(), virtualEvent.y())) {
                  CompactOverlayButton.fireIfHit(row.toggleButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                  CompactOverlayButton.fireIfHit(row.checkButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                  CompactOverlayButton.fireIfHit(row.deleteButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                  return true;
               }

               if (virtualEvent.x() >= this.proxyRowX()
                  && virtualEvent.x() < this.proxyRowRight()
                  && virtualEvent.y() >= row.y
                  && virtualEvent.y() < row.y + 32 - 3) {
                  this.selectProxy(row.proxy);
                  return true;
               }
            }

            this.clearInputFocus();
            return super.mouseClicked(virtualEvent, doubleClick);
         }
      }
   }

   private boolean overButton(CompactOverlayButton button, double mouseX, double mouseY) {
      return button != null
         && button.visible
         && mouseX >= button.getX()
         && mouseX < button.getX() + button.getWidth()
         && mouseY >= button.getY()
         && mouseY < button.getY() + button.getHeight();
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.proxyScrollbarDragging) {
         this.proxyScrollbarDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent(event));
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.proxyScrollbarDragging) {
         CompactScrollbar.Metrics scrollbar = this.proxyScrollbarMetrics(this.filteredProxies().size());
         this.proxyListScrollOffset = clampScrollOffset(
            CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.proxyScrollbarGrabOffset), scrollbar.maxScroll()
         );
         this.proxyListScroll.jumpTo(this.proxyListScrollOffset, scrollbar.maxScroll());
         this.rebuildButtons();
         return true;
      } else {
         return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dx), AutismUiScale.toVirtual(dy));
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      x = AutismUiScale.toVirtual(x);
      y = AutismUiScale.toVirtual(y);
      if (this.compactLayout()) {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      } else if (!(x < this.listX()) && !(x >= this.listX() + this.listWidth()) && !(y < 178.0) && !(y >= 178 + this.listPanelHeight())) {
         int maxScroll = this.proxyMaxScroll(this.filteredProxies().size());
         if (maxScroll <= 0) {
            return true;
         } else {
            int next = this.proxyListScrollOffset - (int)Math.signum(scrollY) * 32;
            this.proxyListScrollOffset = clampScrollOffset(next, maxScroll);
            this.proxyListScroll.setTarget(this.proxyListScrollOffset, maxScroll);
            this.rebuildButtons();
            return true;
         }
      } else {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   private void selectProxy(AutismProxy proxy) {
      this.selectedProxy = proxy;
      if (proxy != null) {
         this.nameField.setValue(safeTrim(proxy.name));
         this.addressField.setValue(safeTrim(proxy.address));
         this.portField.setValue(proxy.port <= 0 ? "" : Integer.toString(proxy.port));
         this.usernameField.setValue(safeTrim(proxy.username));
         this.passwordField.setValue(proxy.password == null ? "" : proxy.password);
         this.type = proxy.type == null ? AutismProxyType.Socks5 : proxy.type;
      }

      this.rebuildButtons();
   }

   private List<AutismProxy> filteredProxies() {
      AutismProxyManager manager = AutismProxyManager.get();
      long listRevision = manager.listRevision();
      long refreshRevision = manager.refreshStatus().revision();
      String query = normalize(this.searchQuery);
      boolean statusSensitive = this.filter != AutismProxiesScreen.ProxyFilter.ALL || !query.isEmpty();
      boolean cacheValid = this.cachedFilter == this.filter
         && query.equals(this.cachedSearchQuery)
         && this.cachedListRevision == listRevision
         && (!statusSensitive || this.cachedRefreshRevision == refreshRevision);
      if (cacheValid) {
         return this.cachedFilteredProxies;
      } else {
         List<AutismProxy> proxies = manager.all();
         List<AutismProxy> filtered;
         if (query.isEmpty() && this.filter == AutismProxiesScreen.ProxyFilter.ALL) {
            filtered = proxies;
         } else {
            filtered = new ArrayList<>();

            for (AutismProxy proxy : proxies) {
               if (proxy != null && this.matchesFilter(proxy) && (query.isEmpty() || this.matchesSearch(proxy, query))) {
                  filtered.add(proxy);
               }
            }
         }

         this.cachedFilteredProxies = filtered;
         this.cachedSearchQuery = query;
         this.cachedFilter = this.filter;
         this.cachedListRevision = listRevision;
         this.cachedRefreshRevision = refreshRevision;
         return this.cachedFilteredProxies;
      }
   }

   private boolean matchesFilter(AutismProxy proxy) {
      return switch (this.filter) {
         case ALL -> true;
         case ALIVE -> proxy.status == AutismProxy.Status.ALIVE;
         case DEAD -> proxy.status == AutismProxy.Status.DEAD;
         case UNCHECKED -> proxy.status == AutismProxy.Status.UNCHECKED || proxy.status == AutismProxy.Status.CHECKING;
         case ENABLED -> proxy.enabled;
      };
   }

   private boolean matchesSearch(AutismProxy proxy, String query) {
      String haystack = normalize(
         proxy.displayName()
            + " "
            + proxy.address
            + " "
            + proxy.port
            + " "
            + proxy.type
            + " "
            + proxy.status
            + " "
            + safeTrim(proxy.username)
            + " "
            + proxy.geoSearchText()
      );
      return haystack.contains(query);
   }

   private String statusText(AutismProxy proxy) {
      if (proxy == null) {
         return "";
      } else {
         return switch (proxy.status) {
            case ALIVE -> proxy.latency + "ms";
            case DEAD -> "Dead";
            case CHECKING -> "Checking";
            case UNCHECKED -> "Unchecked";
         };
      }
   }

   private String refreshStatusText(AutismProxyManager.RefreshStatus status) {
      if (status == null) {
         return "";
      } else if (status.running()) {
         return "Refreshing " + status.checked() + "/" + status.total();
      } else {
         return status.canceling() ? "Canceling" : "";
      }
   }

   private String importStatusText(AutismProxyManager.ImportStatus status) {
      if (status == null) {
         return "";
      } else if (status.running()) {
         return "Importing " + status.linesRead() + " +" + status.candidates();
      } else {
         return status.canceling() ? "Canceling import" : "";
      }
   }

   private void renderWorkProgress(GuiGraphicsExtractor graphics, AutismProxyManager.RefreshStatus refreshStatus, AutismProxyManager.ImportStatus importStatus) {
      boolean importing = importStatus != null && importStatus.running();
      boolean refreshing = refreshStatus != null && refreshStatus.running();
      if (importing || refreshing) {
         int trackX = this.listX() + 12;
         int trackY = 195;
         int trackW = Math.max(1, this.listWidth() - 24);
         UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, trackW, 2), 1429414952);
         if (refreshing) {
            int total = Math.max(1, refreshStatus.total());
            int fillW = Math.max(1, Math.min(trackW, Math.round((float)(trackW * Math.max(0, refreshStatus.checked())) / total)));
            UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, fillW, 2), -7429889);
         } else {
            int sweepW = Math.max(24, trackW / 4);
            int range = Math.max(1, trackW + sweepW);
            int offset = (int)(System.currentTimeMillis() / 8L % range) - sweepW;
            int fillX = Math.max(trackX, trackX + offset);
            int fillRight = Math.min(trackX + trackW, trackX + offset + sweepW);
            if (fillRight > fillX) {
               UiRenderer.rect(graphics, UiBounds.of(fillX, trackY, fillRight - fillX, 2), -7429889);
            }
         }
      }
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelWidth(), 12);
   }

   private int panelWidth() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 12, 530);
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int listWidth() {
      return Math.max(1, this.panelWidth() - 20);
   }

   private int listPanelHeight() {
      return Math.max(1, this.screenHeight() - 178 - 12);
   }

   private int proxyRowsTop() {
      return 200;
   }

   private int proxyRowsBottom() {
      return 178 + this.listPanelHeight() - 6;
   }

   private int proxyViewportHeight() {
      return Math.max(32, Math.max(1, this.proxyRowsBottom() - this.proxyRowsTop()));
   }

   private int proxyMaxScroll(int rows) {
      return Math.max(0, rows * 32 - this.proxyViewportHeight());
   }

   private CompactScrollbar.Metrics proxyScrollbarMetrics(int rows) {
      int contentPixels = Math.max(0, rows) * 32;
      int trackX = this.listX() + this.listWidth() - 8;
      int trackY = this.proxyRowsTop();
      int trackHeight = this.proxyViewportHeight();
      int maxScroll = this.proxyMaxScroll(rows);
      this.proxyListScrollOffset = clampScrollOffset(this.proxyListScrollOffset, maxScroll);
      this.proxyListScroll.jumpTo(this.proxyListScrollOffset, maxScroll);
      return CompactScrollbar.compute(contentPixels, this.proxyViewportHeight(), trackX, trackY, 4, trackHeight, this.proxyListScrollOffset);
   }

   private int proxyRowX() {
      return this.listX() + 8;
   }

   private int proxyRowRight() {
      return this.listX() + this.listWidth() - 8 - 12;
   }

   private int proxyRowWidth() {
      return Math.max(1, this.proxyRowRight() - this.proxyRowX());
   }

   private boolean compactLayout() {
      return this.panelWidth() < 360 || this.screenHeight() < 222;
   }

   private void setFormFieldsVisible(boolean visible) {
      if (this.nameField != null) {
         this.nameField.visible = visible;
      }

      if (this.addressField != null) {
         this.addressField.visible = visible;
      }

      if (this.portField != null) {
         this.portField.visible = visible;
      }

      if (this.usernameField != null) {
         this.usernameField.visible = visible;
      }

      if (this.passwordField != null) {
         this.passwordField.visible = visible;
      }

      if (this.searchField != null) {
         this.searchField.visible = visible;
      }
   }

   private int[] scaledWidths(int availableWidth, int gap, int... preferredWidths) {
      int[] widths = new int[preferredWidths.length];
      int gaps = Math.max(0, preferredWidths.length - 1) * Math.max(0, gap);
      int preferredTotal = 0;

      for (int preferred : preferredWidths) {
         preferredTotal += Math.max(1, preferred);
      }

      int usable = Math.max(preferredWidths.length, availableWidth - gaps);
      int assigned = 0;

      for (int i = 0; i < preferredWidths.length; i++) {
         widths[i] = i == preferredWidths.length - 1
            ? Math.max(1, usable - assigned)
            : Math.max(1, Math.round((float)(Math.max(1, preferredWidths[i]) * usable) / preferredTotal));
         assigned += widths[i];
      }

      return widths;
   }

   private void clearInputFocus() {
      if (this.nameField != null) {
         this.nameField.setFocused(false);
      }

      if (this.addressField != null) {
         this.addressField.setFocused(false);
      }

      if (this.portField != null) {
         this.portField.setFocused(false);
      }

      if (this.usernameField != null) {
         this.usernameField.setFocused(false);
      }

      if (this.passwordField != null) {
         this.passwordField.setFocused(false);
      }

      if (this.searchField != null) {
         this.searchField.setFocused(false);
      }

      this.setFocused(null);
   }

   private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
      SectionPanel.renderBody(UiContexts.overlay(graphics, this.font, -10000, -10000), UiBounds.of(x, y, w, h), fill);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
      this.drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
      Font renderer = this.font;
      Identifier font = THEME.fontFor(UiTone.BODY);
      String value = text == null ? "" : text;
      if (maxWidth != Integer.MAX_VALUE && !center) {
         UiText.drawFitted(graphics, renderer, value, font, color, x, y, Math.max(1, maxWidth), false);
      } else {
         if (maxWidth != Integer.MAX_VALUE) {
            value = UiText.trimToWidth(renderer, value, maxWidth, font, color);
         }

         int w = UiText.width(renderer, value, font, color);
         int drawX = center ? x - w / 2 : x;
         UiText.draw(graphics, renderer, value, font, color, drawX, y, false);
      }
   }

   private static int clampScrollOffset(int value, int maxScroll) {
      return Math.max(0, Math.min(maxScroll, value));
   }

   private static String safeTrim(String value) {
      return value == null ? "" : value.trim();
   }

   private static String normalize(String value) {
      return safeTrim(value).toLowerCase(Locale.ROOT);
   }

   private int screenWidth() {
      int width = AutismUiScale.getVirtualScreenWidth();
      return width <= 0 ? this.width : width;
   }

   private int screenHeight() {
      int height = AutismUiScale.getVirtualScreenHeight();
      return height <= 0 ? this.height : height;
   }

   private static MouseButtonEvent virtualEvent(MouseButtonEvent event) {
      return new MouseButtonEvent(AutismUiScale.toVirtual(event.x()), AutismUiScale.toVirtual(event.y()), new MouseButtonInfo(event.button(), 0));
   }

   private static enum ProxyFilter {
      ALL,
      ALIVE,
      DEAD,
      UNCHECKED,
      ENABLED;
   }

   private record ProxyRow(AutismProxy proxy, int y, CompactOverlayButton toggleButton, CompactOverlayButton checkButton, CompactOverlayButton deleteButton) {
   }
}
