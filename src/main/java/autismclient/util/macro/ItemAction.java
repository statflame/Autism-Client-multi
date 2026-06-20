package autismclient.util.macro;

import autismclient.util.AutismCursorClickHelper;
import autismclient.util.AutismDropAction;
import autismclient.util.AutismDropHelper;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class ItemAction implements MacroAction, WaitsForGui {
   public List<String> itemNames = new ArrayList<>();
   public List<ItemTarget> itemTargets = new ArrayList<>();
   public List<Integer> itemTimes = new ArrayList<>();
   public List<Integer> itemActionIdx = new ArrayList<>();
   public List<Integer> itemButtons = new ArrayList<>();
   public List<Boolean> preferPlayerInventory = new ArrayList<>();
   public List<Integer> stackAmountModes = new ArrayList<>();
   public int targetSlot = -1;
   public boolean useSlot = false;
   public int actionIndex = 0;
   public int button = 0;
   public int times = 1;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";
   public boolean waitForItem = false;
   public boolean useCursorItemForPickupAll = true;
   private static final Minecraft MC = Minecraft.getInstance();
   private static final AutismDropAction[] ACTIONS = AutismDropAction.values();
   private static final int[] ITEM_CLICK_ACTIONS = new int[]{
      AutismDropAction.PICKUP.ordinal(),
      AutismDropAction.QUICK_MOVE.ordinal(),
      AutismDropAction.SWAP.ordinal(),
      AutismDropAction.CLONE.ordinal(),
      AutismDropAction.DROP_ITEM.ordinal(),
      AutismDropAction.DROP_STACK.ordinal()
   };
   private static final int[] LEGACY_ITEM_CLICK_ACTIONS = new int[]{
      AutismDropAction.PICKUP.ordinal(),
      AutismDropAction.PICKUP_ALL.ordinal(),
      AutismDropAction.QUICK_MOVE.ordinal(),
      AutismDropAction.SWAP.ordinal(),
      AutismDropAction.CLONE.ordinal(),
      AutismDropAction.DROP_ITEM.ordinal(),
      AutismDropAction.DROP_STACK.ordinal()
   };

   public int getItemTime(int index) {
      return index < this.itemTimes.size() && this.itemTimes.get(index) > 0 ? this.itemTimes.get(index) : 1;
   }

   public int getItemActionIdx(int index) {
      int stored = index < this.itemActionIdx.size() ? this.itemActionIdx.get(index) : this.actionIndex;
      return normalizeItemClickActionIndex(stored);
   }

   public int getItemButton(int index) {
      int raw = index < this.itemButtons.size() ? this.itemButtons.get(index) : this.button;
      return normalizeButtonForItemClick(this.getItemAction(index), raw);
   }

   public AutismDropAction getItemAction(int index) {
      return ACTIONS[this.getItemActionIdx(index)];
   }

   public String getItemButtonName(int index) {
      if (this.getItemAction(index) == AutismDropAction.PICKUP_ALL) {
         return "All";
      } else if (this.getItemAction(index) == AutismDropAction.SWAP) {
         int hotbarSlot = Math.max(0, Math.min(8, this.getItemButton(index))) + 1;
         return "Hotbar " + hotbarSlot;
      } else {
         return switch (this.getItemButton(index)) {
            case 0 -> "Left";
            case 1 -> "Right";
            case 2 -> "Middle";
            default -> "?";
         };
      }
   }

   public void cycleItemAction(int index) {
      while (this.itemActionIdx.size() <= index) {
         this.itemActionIdx.add(this.actionIndex);
      }

      this.itemActionIdx.set(index, cycleItemClickActionIndex(this.itemActionIdx.get(index), 1));
      this.normalizeStoredItemButton(index);
   }

   public void cycleItemActionBackwards(int index) {
      while (this.itemActionIdx.size() <= index) {
         this.itemActionIdx.add(this.actionIndex);
      }

      this.itemActionIdx.set(index, cycleItemClickActionIndex(this.itemActionIdx.get(index), -1));
      this.normalizeStoredItemButton(index);
   }

   public void cycleItemButton(int index) {
      while (this.itemButtons.size() <= index) {
         this.itemButtons.add(this.button);
      }

      if (this.getItemAction(index) != AutismDropAction.PICKUP_ALL) {
         int limit = buttonLimitForItemClick(this.getItemAction(index));
         if (limit > 1) {
            int current = this.getItemButton(index);
            this.itemButtons.set(index, (current + 1) % limit);
         }
      }
   }

   public void cycleItemButtonBackwards(int index) {
      while (this.itemButtons.size() <= index) {
         this.itemButtons.add(this.button);
      }

      if (this.getItemAction(index) != AutismDropAction.PICKUP_ALL) {
         int limit = buttonLimitForItemClick(this.getItemAction(index));
         if (limit > 1) {
            int current = this.getItemButton(index);
            this.itemButtons.set(index, (current - 1 + limit) % limit);
         }
      }
   }

   public boolean getPreferPlayerInventory(int index) {
      return index >= 0 && index < this.preferPlayerInventory.size() && Boolean.TRUE.equals(this.preferPlayerInventory.get(index));
   }

   public void setPreferPlayerInventory(int index, boolean value) {
      if (index >= 0) {
         while (this.preferPlayerInventory.size() <= index) {
            this.preferPlayerInventory.add(false);
         }

         this.preferPlayerInventory.set(index, value);
      }
   }

   public ItemAction.StackAmountMode getStackAmountMode(int index) {
      int raw = index >= 0 && index < this.stackAmountModes.size() ? this.stackAmountModes.get(index) : ItemAction.StackAmountMode.DEFAULT.ordinal();
      return ItemAction.StackAmountMode.values()[normalizeStackAmountMode(raw)];
   }

   public void cycleStackAmountMode(int index, int direction) {
      if (index >= 0) {
         while (this.stackAmountModes.size() <= index) {
            this.stackAmountModes.add(ItemAction.StackAmountMode.DEFAULT.ordinal());
         }

         int current = normalizeStackAmountMode(this.stackAmountModes.get(index));
         int next = (current + direction) % ItemAction.StackAmountMode.values().length;
         if (next < 0) {
            next += ItemAction.StackAmountMode.values().length;
         }

         this.stackAmountModes.set(index, next);
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.getConnection() != null) {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler != null) {
            if (!this.itemNames.isEmpty()) {
               for (int ei = 0; ei < this.itemNames.size(); ei++) {
                  String entry = this.itemNames.get(ei);
                  int clickCount = this.getItemTime(ei);
                  AutismDropAction eiAction = this.getItemAction(ei);
                  int eiButton = this.getItemButton(ei);
                  if (usesFixedButtonForItemClick(eiAction)) {
                     eiButton = eiAction.getButton();
                  }

                  ItemTarget entryTarget = this.getItemTarget(ei);
                  String entryName = parseEntryName(entryTarget.toLegacyEntry());
                  int configuredEntrySlot = entryTarget.hasSlot() ? entryTarget.slot : -1;
                  if (eiAction == AutismDropAction.PICKUP_ALL && this.useCursorItemForPickupAll) {
                     AutismCursorClickHelper.click(mc, entryTarget, clickCount);
                  } else {
                     int slotToUse;
                     if (configuredEntrySlot >= 0 && !entryName.isEmpty()) {
                        int resolvedSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredEntrySlot);
                        if (resolvedSlot < 0) {
                           continue;
                        }

                        slotToUse = this.findExactMatchingSlot(handler, entryTarget, resolvedSlot);
                     } else if (configuredEntrySlot >= 0) {
                        slotToUse = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredEntrySlot);
                     } else if (this.useSlot && this.targetSlot >= 0) {
                        int configuredSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, this.targetSlot);
                        if (configuredSlot < 0) {
                           continue;
                        }

                        slotToUse = this.findExactMatchingSlot(handler, entryTarget, configuredSlot);
                     } else {
                        slotToUse = this.findSlotByItemTarget(handler, entryTarget, this.getPreferPlayerInventory(ei), this.getStackAmountMode(ei));
                     }

                     if (slotToUse >= 0) {
                        int fs = slotToUse;
                        int fb = eiButton;
                        AutismDropAction fa = eiAction;
                        if (eiAction != AutismDropAction.DROP_ITEM && eiAction != AutismDropAction.DROP_STACK) {
                           for (int j = 0; j < clickCount; j++) {
                              AutismInventoryClickHelper.click(mc, fs, fb, fa.toClickType());
                           }
                        } else {
                           AutismDropHelper.dropFromHandlerSlot(mc, slotToUse, eiAction == AutismDropAction.DROP_STACK ? 0 : clickCount);
                        }
                     }
                  }
               }
            } else if (this.useSlot && this.targetSlot >= 0) {
               AutismDropAction action = this.getAction();
               int effectiveButton = this.getButton();
               if (usesFixedButtonForItemClick(action)) {
                  effectiveButton = action.getButton();
               }

               if (action == AutismDropAction.PICKUP_ALL && this.useCursorItemForPickupAll) {
                  AutismCursorClickHelper.click(mc, ItemTarget.slotOnly(this.targetSlot), this.times);
                  return;
               }

               int resolvedTargetSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, this.targetSlot);
               if (resolvedTargetSlot < 0) {
                  return;
               }

               if (action == AutismDropAction.DROP_ITEM || action == AutismDropAction.DROP_STACK) {
                  AutismDropHelper.dropFromHandlerSlot(mc, resolvedTargetSlot, action == AutismDropAction.DROP_STACK ? 0 : this.times);
                  return;
               }

               for (int i = 0; i < this.times; i++) {
                  AutismInventoryClickHelper.click(mc, resolvedTargetSlot, effectiveButton, action.toClickType());
               }
            } else if (this.getAction() == AutismDropAction.PICKUP_ALL && this.useCursorItemForPickupAll) {
               AutismCursorClickHelper.click(mc, null, this.times);
            }
         }
      }
   }

   private int findSlotByItemName(AbstractContainerMenu handler, String name) {
      return this.findSlotByItemTarget(handler, ItemTarget.fromLegacyEntry(name));
   }

   private int findSlotByItemTarget(AbstractContainerMenu handler, ItemTarget target) {
      return this.findSlotByItemTarget(handler, target, false, ItemAction.StackAmountMode.DEFAULT);
   }

   private int findSlotByItemTarget(AbstractContainerMenu handler, ItemTarget target, boolean preferInventory, ItemAction.StackAmountMode amountMode) {
      if (!target.hasIdentity()) {
         return -1;
      } else {
         ItemAction.StackAmountMode mode = amountMode == null ? ItemAction.StackAmountMode.DEFAULT : amountMode;
         if (preferInventory) {
            int inventorySlot = this.findMatchingSlotInPartition(handler, target, mode, true);
            return inventorySlot >= 0 ? inventorySlot : this.findMatchingSlotInPartition(handler, target, mode, false);
         } else {
            return this.findMatchingSlotInPartition(handler, target, mode, null);
         }
      }
   }

   private int findMatchingSlotInPartition(AbstractContainerMenu handler, ItemTarget target, ItemAction.StackAmountMode mode, Boolean playerInventoryOnly) {
      int bestSlot = -1;
      int bestScore = -1;
      int bestCount = mode == ItemAction.StackAmountMode.LEAST ? Integer.MAX_VALUE : -1;

      for (Slot slot : handler.slots) {
         if (slot != null && !slot.getItem().isEmpty()) {
            if (playerInventoryOnly != null) {
               boolean isPlayerInventory = AutismInventoryHelper.isInventorySlot(MC, slot);
               if (playerInventoryOnly != isPlayerInventory) {
                  continue;
               }
            }

            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(MC, slot.index);
            int score = target.score(slot.getItem(), visibleSlot);
            if (score >= 0) {
               int count = slot.getItem().getCount();
               if (this.isBetterItemCandidate(mode, score, count, bestScore, bestCount)) {
                  bestScore = score;
                  bestCount = count;
                  bestSlot = slot.index;
               }
            }
         }
      }

      return bestScore >= 0 ? bestSlot : -1;
   }

   private boolean isBetterItemCandidate(ItemAction.StackAmountMode mode, int score, int count, int bestScore, int bestCount) {
      if (bestScore < 0) {
         return true;
      } else if (mode == ItemAction.StackAmountMode.LEAST) {
         return count != bestCount ? count < bestCount : score > bestScore;
      } else if (mode == ItemAction.StackAmountMode.MOST) {
         return count != bestCount ? count > bestCount : score > bestScore;
      } else {
         return score > bestScore;
      }
   }

   private int findExactMatchingSlot(AbstractContainerMenu handler, String name, int requiredSlot) {
      return this.findExactMatchingSlot(handler, ItemTarget.fromLegacyEntry(name), requiredSlot);
   }

   private int findExactMatchingSlot(AbstractContainerMenu handler, ItemTarget target, int requiredSlot) {
      if (requiredSlot >= 0 && requiredSlot < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(requiredSlot);
         if (slot == null || slot.getItem().isEmpty()) {
            return -1;
         } else if (!target.hasIdentity()) {
            return requiredSlot;
         } else {
            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(MC, requiredSlot);
            return target.matches(slot.getItem(), visibleSlot) ? requiredSlot : -1;
         }
      } else {
         return -1;
      }
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGuiAfter = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name;
   }

   @Override
   public boolean isWaitForGuiChange() {
      return true;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.ITEM;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "ITEM");
      tag.put("itemNames", ItemTarget.toTagList(this.resolvedItemTargets()));
      ListTag times2 = new ListTag();

      for (int t : this.itemTimes) {
         times2.add(StringTag.valueOf(String.valueOf(t)));
      }

      tag.put("itemTimes", times2);
      ListTag aidx = new ListTag();

      for (int a : this.itemActionIdx) {
         aidx.add(StringTag.valueOf(String.valueOf(a)));
      }

      tag.put("itemActionIdx", aidx);
      ListTag btns = new ListTag();

      for (int b : this.itemButtons) {
         btns.add(StringTag.valueOf(String.valueOf(b)));
      }

      tag.put("itemButtons", btns);
      ListTag preferInv = new ListTag();

      for (int i = 0; i < this.itemNames.size(); i++) {
         preferInv.add(StringTag.valueOf(String.valueOf(this.getPreferPlayerInventory(i))));
      }

      tag.put("preferPlayerInventory", preferInv);
      ListTag stackModes = new ListTag();

      for (int i = 0; i < this.itemNames.size(); i++) {
         stackModes.add(StringTag.valueOf(String.valueOf(this.getStackAmountMode(i).ordinal())));
      }

      tag.put("stackAmountModes", stackModes);
      tag.putInt("targetSlot", this.targetSlot);
      tag.putBoolean("useSlot", this.useSlot);
      tag.putInt("actionIndex", this.actionIndex);
      tag.putInt("button", this.button);
      tag.putInt("times", this.times);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("waitForItem", this.waitForItem);
      tag.putBoolean("useCursorItemForPickupAll", this.useCursorItemForPickupAll);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.itemTargets.clear();
      this.itemNames.clear();
      if (tag.contains("itemNames")) {
         ListTag nl = tag.getList("itemNames").orElse(new ListTag());
         this.itemTargets.addAll(ItemTarget.fromElementList(nl));
      } else if (tag.contains("itemName")) {
         String old = tag.getStringOr("itemName", "");
         if (!old.isEmpty()) {
            this.itemTargets.add(ItemTarget.fromLegacyEntry(old));
         }
      }

      this.syncLegacyItemNames();
      this.itemTimes.clear();
      if (tag.contains("itemTimes")) {
         for (Tag el : tag.getList("itemTimes").orElse(new ListTag())) {
            try {
               this.itemTimes.add(Integer.parseInt(el.asString().orElse("1")));
            } catch (NumberFormatException var9) {
               this.itemTimes.add(1);
            }
         }
      }

      while (this.itemTimes.size() < this.itemNames.size()) {
         this.itemTimes.add(1);
      }

      this.itemActionIdx.clear();
      if (tag.contains("itemActionIdx")) {
         for (Tag el : tag.getList("itemActionIdx").orElse(new ListTag())) {
            try {
               this.itemActionIdx.add(Integer.parseInt(el.asString().orElse("0")));
            } catch (NumberFormatException var8) {
               this.itemActionIdx.add(0);
            }
         }
      }

      while (this.itemActionIdx.size() < this.itemNames.size()) {
         this.itemActionIdx.add(0);
      }

      for (int i = 0; i < this.itemActionIdx.size(); i++) {
         this.itemActionIdx.set(i, normalizeItemClickActionIndex(this.itemActionIdx.get(i)));
      }

      this.itemButtons.clear();
      if (tag.contains("itemButtons")) {
         for (Tag el : tag.getList("itemButtons").orElse(new ListTag())) {
            try {
               this.itemButtons.add(Integer.parseInt(el.asString().orElse("0")));
            } catch (NumberFormatException var7) {
               this.itemButtons.add(0);
            }
         }
      }

      while (this.itemButtons.size() < this.itemNames.size()) {
         this.itemButtons.add(0);
      }

      for (int i = 0; i < this.itemButtons.size(); i++) {
         this.itemButtons.set(i, normalizeButtonForItemClick(this.getItemAction(i), this.itemButtons.get(i)));
      }

      this.preferPlayerInventory.clear();
      if (tag.contains("preferPlayerInventory")) {
         for (Tag el : tag.getList("preferPlayerInventory").orElse(new ListTag())) {
            this.preferPlayerInventory.add(Boolean.parseBoolean(el.asString().orElse("false")));
         }
      }

      while (this.preferPlayerInventory.size() < this.itemNames.size()) {
         this.preferPlayerInventory.add(false);
      }

      while (this.preferPlayerInventory.size() > this.itemNames.size()) {
         this.preferPlayerInventory.remove(this.preferPlayerInventory.size() - 1);
      }

      this.stackAmountModes.clear();
      if (tag.contains("stackAmountModes")) {
         for (Tag el : tag.getList("stackAmountModes").orElse(new ListTag())) {
            try {
               this.stackAmountModes.add(normalizeStackAmountMode(Integer.parseInt(el.asString().orElse("0"))));
            } catch (NumberFormatException var6) {
               this.stackAmountModes.add(ItemAction.StackAmountMode.DEFAULT.ordinal());
            }
         }
      }

      while (this.stackAmountModes.size() < this.itemNames.size()) {
         this.stackAmountModes.add(ItemAction.StackAmountMode.DEFAULT.ordinal());
      }

      while (this.stackAmountModes.size() > this.itemNames.size()) {
         this.stackAmountModes.remove(this.stackAmountModes.size() - 1);
      }

      this.targetSlot = tag.getIntOr("targetSlot", -1);
      this.useSlot = tag.getBooleanOr("useSlot", false);
      this.actionIndex = normalizeItemClickActionIndex(tag.getIntOr("actionIndex", 0));
      this.button = normalizeButtonForItemClick(this.getAction(), tag.getIntOr("button", 0));
      this.times = tag.getIntOr("times", 1);
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      if (tag.contains("waitForItem")) {
         this.waitForItem = tag.getBooleanOr("waitForItem", false);
      }

      this.useCursorItemForPickupAll = tag.getBooleanOr("useCursorItemForPickupAll", true);
   }

   @Override
   public String getDisplayName() {
      List<ItemTarget> targets = this.resolvedItemTargets();
      if (targets.isEmpty()) {
         return this.useSlot && this.targetSlot >= 0
            ? "#" + this.targetSlot + " (" + this.getAction().shortName + ")" + WaitsForGui.timingLabel(this)
            : "Item (empty)" + WaitsForGui.timingLabel(this);
      } else {
         String actionName = this.getItemAction(0).shortName;
         String e0 = targets.get(0).toLegacyEntry();
         int slotNumber = parseEntrySlot(e0);
         String itemName = parseEntryName(e0);
         String first;
         if (slotNumber >= 0 && !itemName.isEmpty()) {
            first = itemName + " @ " + slotNumber;
         } else if (slotNumber >= 0) {
            first = "#" + slotNumber;
         } else {
            first = itemName;
         }

         String shortName = first.length() > 12 ? first.substring(0, 10) + ".." : first;
         String suffix = this.itemNames.size() > 1 ? " (+" + (this.itemNames.size() - 1) + ")" : "";
         String base = "Item " + shortName + suffix + " (" + actionName + ")";
         if (this.waitForItem) {
            base = base + " [wait]";
         }

         return base + WaitsForGui.timingLabel(this);
      }
   }

   @Override
   public String getIcon() {
      return "I";
   }

   public AutismDropAction getAction() {
      return ACTIONS[normalizeItemClickActionIndex(this.actionIndex)];
   }

   public void cycleAction() {
      this.actionIndex = cycleItemClickActionIndex(this.actionIndex, 1);
      this.button = normalizeButtonForItemClick(this.getAction(), this.button);
   }

   public void cycleActionBackwards() {
      this.actionIndex = cycleItemClickActionIndex(this.actionIndex, -1);
      this.button = normalizeButtonForItemClick(this.getAction(), this.button);
   }

   public String getButtonName() {
      if (this.getAction() == AutismDropAction.PICKUP_ALL) {
         return "All";
      } else if (this.getAction() == AutismDropAction.SWAP) {
         int hotbarSlot = Math.max(0, Math.min(8, this.getButton())) + 1;
         return "Hotbar " + hotbarSlot;
      } else {
         return switch (this.getButton()) {
            case 0 -> "Left";
            case 1 -> "Right";
            case 2 -> "Middle";
            default -> "?";
         };
      }
   }

   public void cycleButton() {
      if (this.getAction() != AutismDropAction.PICKUP_ALL) {
         int limit = buttonLimitForItemClick(this.getAction());
         if (limit > 1) {
            this.button = (this.getButton() + 1) % limit;
         }
      }
   }

   public void cycleButtonBackwards() {
      if (this.getAction() != AutismDropAction.PICKUP_ALL) {
         int limit = buttonLimitForItemClick(this.getAction());
         if (limit > 1) {
            this.button = (this.getButton() - 1 + limit) % limit;
         }
      }
   }

   public int getButton() {
      return normalizeButtonForItemClick(this.getAction(), this.button);
   }

   private void normalizeStoredItemButton(int index) {
      while (this.itemButtons.size() <= index) {
         this.itemButtons.add(this.button);
      }

      this.itemButtons.set(index, normalizeButtonForItemClick(this.getItemAction(index), this.itemButtons.get(index)));
   }

   private static boolean usesFixedButtonForItemClick(AutismDropAction action) {
      return action != AutismDropAction.QUICK_MOVE && action.usesFixedButton();
   }

   private static int buttonLimitForItemClick(AutismDropAction action) {
      return switch (action) {
         case SWAP -> 9;
         case PICKUP -> 3;
         case QUICK_MOVE -> 2;
         default -> 1;
      };
   }

   private static int normalizeButtonForItemClick(AutismDropAction action, int rawButton) {
      if (action == null) {
         action = AutismDropAction.PICKUP;
      }

      if (usesFixedButtonForItemClick(action)) {
         return action.getButton();
      } else {
         int limit = buttonLimitForItemClick(action);
         if (limit <= 1) {
            return 0;
         } else {
            int normalized = rawButton % limit;
            return normalized < 0 ? normalized + limit : normalized;
         }
      }
   }

   private static int normalizeItemClickActionIndex(int rawIndex) {
      for (int allowed : LEGACY_ITEM_CLICK_ACTIONS) {
         if (rawIndex == allowed) {
            return rawIndex;
         }
      }

      return AutismDropAction.PICKUP.ordinal();
   }

   private static int normalizeStackAmountMode(int rawMode) {
      return rawMode >= 0 && rawMode < ItemAction.StackAmountMode.values().length ? rawMode : ItemAction.StackAmountMode.DEFAULT.ordinal();
   }

   private static int cycleItemClickActionIndex(int current, int direction) {
      int selected = direction >= 0 ? -1 : 0;

      for (int i = 0; i < ITEM_CLICK_ACTIONS.length; i++) {
         if (ITEM_CLICK_ACTIONS[i] == current) {
            selected = i;
            break;
         }
      }

      int next = (selected + direction) % ITEM_CLICK_ACTIONS.length;
      if (next < 0) {
         next += ITEM_CLICK_ACTIONS.length;
      }

      return ITEM_CLICK_ACTIONS[next];
   }

   private static int parseEntrySlot(String entry) {
      if (entry != null && entry.startsWith("#")) {
         int separator = entry.indexOf(124);
         String raw = separator >= 0 ? entry.substring(1, separator) : entry.substring(1);

         try {
            return Integer.parseInt(raw);
         } catch (NumberFormatException var4) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static String parseEntryName(String entry) {
      if (entry == null || entry.isBlank()) {
         return "";
      } else if (!entry.startsWith("#")) {
         return entry;
      } else {
         int separator = entry.indexOf(124);
         return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
      }
   }

   private List<ItemTarget> resolvedItemTargets() {
      if (!this.itemTargets.isEmpty()) {
         return this.itemTargets;
      } else {
         for (String itemName : this.itemNames) {
            ItemTarget target = ItemTarget.fromLegacyEntry(itemName);
            if (target.hasSlot() || target.hasIdentity()) {
               this.itemTargets.add(target);
            }
         }

         return this.itemTargets;
      }
   }

   private ItemTarget getItemTarget(int index) {
      List<ItemTarget> targets = this.resolvedItemTargets();
      return index >= 0 && index < targets.size() ? targets.get(index) : new ItemTarget();
   }

   private void syncLegacyItemNames() {
      this.itemNames.clear();

      for (ItemTarget target : this.itemTargets) {
         if (target != null) {
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) {
               this.itemNames.add(entry);
            }
         }
      }
   }

   public static enum StackAmountMode {
      DEFAULT("Default"),
      LEAST("Least"),
      MOST("Most");

      public final String label;

      private StackAmountMode(String label) {
         this.label = label;
      }
   }
}
