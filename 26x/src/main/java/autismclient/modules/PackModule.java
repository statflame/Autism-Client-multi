package autismclient.modules;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class PackModule {
   protected static final Minecraft MC = Minecraft.getInstance();
   private final String id;
   private final String name;
   private PackModuleCategory category;
   private final String description;
   private final List<PackModuleOption> options = new ArrayList<>();
   private String replacementToggleMessage;
   private boolean addon;

   protected PackModule(String id, String name, PackModuleCategory category, String description) {
      this.id = id;
      this.name = name;
      this.category = category;
      this.description = description;
   }

   protected PackModule(String id, String name, String description) {
      this(id, name, null, description);
   }

   final void assignCategory(PackModuleCategory category) {
      if (this.category == null) {
         this.category = category;
      }
   }

   public final String id() {
      return this.id;
   }

   public final boolean isAddon() {
      return this.addon;
   }

   final void markAddon() {
      this.addon = true;
   }

   public final String name() {
      return this.name;
   }

   public final PackModuleCategory category() {
      return this.category;
   }

   public final String description() {
      return this.description;
   }

   public final boolean isEnabled() {
      return this.state().enabled;
   }

   public final void setEnabled(boolean enabled) {
      if (!enabled || !PackHideState.blocksEnable(this)) {
         boolean wasEnabled = this.isEnabled();
         if (wasEnabled == enabled) {
            this.replacementToggleMessage = null;
         } else {
            this.state().enabled = enabled;
            if (enabled) {
               this.onEnable();
            } else {
               this.onDisable();
            }

            PackModuleRegistry.markModuleEnabledChanged();
            this.save();
            String message = this.replacementToggleMessage;
            this.replacementToggleMessage = null;
            if (this.emitsToggleMessage() && !PackHideState.isSilenced()) {
               AutismClientMessaging.sendPrefixed(
                  message != null && !message.isBlank() ? message : this.name + ": " + (this.isEnabled() ? "enabled" : "disabled")
               );
            }
         }
      }
   }

   protected final void setEnabledSilently(boolean enabled) {
      if (!enabled || !PackHideState.blocksEnable(this)) {
         boolean wasEnabled = this.isEnabled();
         if (wasEnabled != enabled) {
            this.state().enabled = enabled;
            if (enabled) {
               this.onEnable();
            } else {
               this.onDisable();
            }

            PackModuleRegistry.markModuleEnabledChanged();
            this.save();
         }
      }
   }

   protected final void replaceNextToggleMessage(String message) {
      this.replacementToggleMessage = message;
   }

   protected final void disableSilentlyWithToggleMessage(String message) {
      this.replaceNextToggleMessage(message);
      this.setEnabledSilently(false);
   }

   protected final void disableWithToggleMessage(String message) {
      this.replaceNextToggleMessage(message);
      this.setEnabled(false);
   }

   public final void toggle() {
      this.setEnabled(!this.isEnabled());
   }

   protected int defaultKeybind() {
      return -1;
   }

   public final int keybind() {
      return this.state().keybind;
   }

   public final void setKeybind(int keybind) {
      this.state().keybind = keybind;
      PackModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   public final List<PackModuleOption> options() {
      return Collections.unmodifiableList(this.options);
   }

   public final List<PackModuleOption> visibleOptions() {
      List<PackModuleOption> visible = new ArrayList<>();

      for (PackModuleOption option : this.options) {
         if (option.isVisible(this)) {
            visible.add(option);
         }
      }

      return visible;
   }

   protected final void option(PackModuleOption option) {
      this.options.add(option);
      this.state().settings.putIfAbsent(option.id(), option.defaultValue());
      PackModuleRegistry.markModuleSettingsChanged();
   }

   public String info() {
      return "";
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void tick() {
   }

   public boolean ticksWhenDisabled() {
      return false;
   }

   public boolean hasDisabledTickWork() {
      return this.ticksWhenDisabled();
   }

   public boolean opensSettingsOnClick() {
      return false;
   }

   public boolean hasActivationToggle() {
      return true;
   }

   public boolean showInModuleMenu() {
      return true;
   }

   public boolean emitsToggleMessage() {
      return true;
   }

   public void preMovementTick() {
   }

   public void onRenderLevel(float partialTick) {
   }

   public void onMouseRotation(double deltaYaw, double deltaPitch) {
   }

   public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
      return movement;
   }

   public boolean shouldApplySpeedTimer() {
      return false;
   }

   public void onGameJoin() {
   }

   public void onGameLeft() {
   }

   public boolean onPacketSend(Packet<?> packet) {
      return false;
   }

   public boolean onPacketReceive(Packet<?> packet) {
      return false;
   }

   public void onSoundPacket(ClientboundSoundPacket packet) {
   }

   public void appendTooltip(ItemStack stack, List<?> lines) {
   }

   public boolean shouldCancelAttack(HitResult hitResult) {
      return false;
   }

   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return false;
   }

   public void onStartBreakingBlock(BlockPos pos, Direction direction) {
   }

   public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
      return false;
   }

   public void onBlockBreakingProgress(BlockPos pos, Direction direction) {
   }

   public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
      return false;
   }

   public boolean shouldTraceEntity(Entity entity) {
      return false;
   }

   public int traceColor(Entity entity) {
      return -2130706433;
   }

   protected final boolean bool(String optionId) {
      return Boolean.parseBoolean(this.value(optionId));
   }

   protected final int integer(String optionId) {
      try {
         return Integer.parseInt(this.value(optionId));
      } catch (NumberFormatException var4) {
         PackModuleOption option = this.option(optionId);
         return option == null ? 0 : Integer.parseInt(option.defaultValue());
      }
   }

   protected final double decimal(String optionId) {
      try {
         return Double.parseDouble(this.value(optionId));
      } catch (NumberFormatException var4) {
         PackModuleOption option = this.option(optionId);
         return option == null ? 0.0 : Double.parseDouble(option.defaultValue());
      }
   }

   protected final String text(String optionId) {
      return this.value(optionId);
   }

   protected final String choice(String optionId) {
      return this.value(optionId);
   }

   protected final List<String> list(String optionId) {
      String value = this.value(optionId);
      if (value != null && !value.isBlank()) {
         List<String> out = new ArrayList<>();

         for (String raw : value.split("\\|")) {
            String item = raw.trim();
            if (!item.isEmpty()) {
               out.add(item);
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   public final String value(String optionId) {
      PackModuleOption option = this.option(optionId);
      String fallback = option == null ? "" : option.defaultValue();
      return this.state().settings.getOrDefault(optionId, fallback);
   }

   public final void setValue(String optionId, String value) {
      PackModuleOption option = this.option(optionId);
      this.state().settings.put(optionId, this.sanitizeValue(option, value));
      this.onOptionValueChanged(optionId);
      PackModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   public final void resetValue(String optionId) {
      PackModuleOption option = this.option(optionId);
      if (option != null) {
         this.state().settings.put(optionId, option.defaultValue());
         this.onOptionValueChanged(optionId);
         PackModuleRegistry.markModuleSettingsChanged();
         this.save();
      }
   }

   public final void resetSettings() {
      for (PackModuleOption option : this.options) {
         this.state().settings.put(option.id(), option.defaultValue());
      }

      this.onSettingsReset();
      PackModuleRegistry.markModuleSettingsChanged();
      this.save();
   }

   protected void onSettingsReset() {
   }

   protected void onOptionValueChanged(String optionId) {
   }

   public final void adjustOption(PackModuleOption option, int direction) {
      if (option != null) {
         switch (option.type()) {
            case BOOLEAN:
               this.setValue(option.id(), Boolean.toString(!this.bool(option.id())));
               break;
            case INTEGER: {
               int value = this.integer(option.id());
               int adjusted = (int)clamp(value + (int)option.step() * direction, option.min(), option.max());
               this.setValue(option.id(), Integer.toString(adjusted));
               break;
            }
            case DOUBLE: {
               double value = this.decimal(option.id());
               double adjusted = clamp(value + option.step() * direction, option.min(), option.max());
               this.setValue(option.id(), String.format(Locale.ROOT, "%.2f", adjusted));
               break;
            }
            case ENUM:
               List<String> choices = option.choices();
               if (!choices.isEmpty()) {
                  int index = choices.indexOf(this.value(option.id()));
                  if (index < 0) {
                     index = 0;
                  }

                  int next = Math.floorMod(index + direction, choices.size());
                  this.setValue(option.id(), choices.get(next));
               }
               break;
            case ACTION:
               if (option.action() != null) {
                  option.action().run();
               }
            case STRING:
            case STRING_LIST:
            case PACKET_LIST:
            case ITEM_LIST:
            case BLOCK_LIST:
            case ENTITY_TYPE_LIST:
            case SOUND_EVENT_LIST:
            case STORAGE_LIST:
            case COLOR:
            case KEYBIND:
         }
      }
   }

   public final String displayValue(PackModuleOption option) {
      if (option == null) {
         return "";
      } else {
         String override = this.displayValueOverride(option);
         if (override != null) {
            return override;
         } else {
            return switch (option.type()) {
               case BOOLEAN -> this.bool(option.id()) ? "ON" : "OFF";
               case INTEGER, DOUBLE, ENUM, STRING, STRING_LIST, PACKET_LIST, ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST, COLOR, KEYBIND -> option.format(
                  this.value(option.id())
               );
               case ACTION -> "RUN";
            };
         }
      }
   }

   protected String displayValueOverride(PackModuleOption option) {
      return null;
   }

   private String sanitizeValue(PackModuleOption option, String value) {
      if (option == null) {
         return value == null ? "" : value;
      } else {
         String safe = value == null ? option.defaultValue() : value;

         try {
            return switch (option.type()) {
               case BOOLEAN -> Boolean.toString(Boolean.parseBoolean(safe));
               case INTEGER -> Integer.toString((int)clamp(Integer.parseInt(safe), option.min(), option.max()));
               case DOUBLE -> Double.toString(clamp(Double.parseDouble(safe), option.min(), option.max()));
               case ENUM -> !option.choices().isEmpty() && !option.choices().contains(safe) ? option.defaultValue() : safe;
               case ACTION -> option.defaultValue();
               case STRING, STRING_LIST, PACKET_LIST, ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST, COLOR, KEYBIND -> safe;
            };
         } catch (Exception var5) {
            return option.defaultValue();
         }
      }
   }

   public final PackModuleOption option(String optionId) {
      for (PackModuleOption option : this.options) {
         if (option.id().equals(optionId)) {
            return option;
         }
      }

      return null;
   }

   protected final boolean isAdminContext() {
      return MC != null && MC.player != null && MC.player.canUseGameMasterBlocks();
   }

   protected final boolean isCreativeContext() {
      return MC != null && MC.player != null && MC.player.hasInfiniteMaterials();
   }

   protected final void sendCommand(String command) {
      if (!PackHideState.isHardLocked()) {
         if (MC != null && MC.getConnection() != null && command != null && !command.isBlank()) {
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            MC.getConnection().sendCommand(normalized);
         }
      }
   }

   private AutismConfig.ModuleState state() {
      AutismConfig config = AutismConfig.getGlobal();
      return config.modules.computeIfAbsent(this.id, ignored -> {
         AutismConfig.ModuleState created = new AutismConfig.ModuleState();
         created.keybind = this.defaultKeybind();
         return created;
      });
   }

   private void save() {
      AutismConfig.getGlobal().save();
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
