package autismclient.mixin;

import autismclient.commands.AutismCommands;
import autismclient.modules.AutismModule;
import autismclient.modules.InventoryTweaksModule;
import autismclient.modules.PackHideState;
import autismclient.modules.PackModuleRegistry;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class AutismClientPlayNetworkHandlerMixin {
   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void autism$dispatchAutismCommand(String message, CallbackInfo ci) {
      if (AutismCommands.isAutismCommandMessage(message)) {
         if (AutismCommands.isBlockedPanicCommandMessage(message)) {
            ci.cancel();
         } else {
            String body = AutismCommands.commandBody(message);
            if (body.isBlank()) {
               ci.cancel();
            } else {
               Minecraft mc = Minecraft.getInstance();
               autism$rememberChatCommand(mc, message);
               AutismCommands.dispatch(body);
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"handleContainerContent"},
      at = {@At("RETURN")}
   )
   private void yang$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
         boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
         if (macroWaits || inventoryTweaks) {
            if (macroWaits) {
               MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
            }

            if (inventoryTweaks) {
               InventoryTweaksModule.onContainerSynced(packet.containerId());
            }
         }
      }
   }

   @Inject(
      method = {"handleContainerSetSlot"},
      at = {@At("RETURN")}
   )
   private void yang$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
         boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
         if (macroWaits || inventoryTweaks) {
            if (macroWaits) {
               MacroConditionRegistry.onSlotUpdate(packet.getSlot());
            }

            if (inventoryTweaks) {
               InventoryTweaksModule.onContainerSynced(packet.getContainerId());
            }
         }
      }
   }

   @Inject(
      method = {"handleSetCursorItem"},
      at = {@At("RETURN")}
   )
   private void autism$onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
         }
      }
   }

   @Inject(
      method = {"handleSetPlayerInventory"},
      at = {@At("RETURN")}
   )
   private void autism$onSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
         }
      }
   }

   @Inject(
      method = {"handleSoundEvent"},
      at = {@At("RETURN")}
   )
   private void yang$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         boolean macroWaits = MacroConditionRegistry.hasPendingSoundConditions();
         boolean moduleHooks = PackModuleRegistry.hasSoundHooks();
         if (macroWaits || moduleHooks) {
            if (macroWaits) {
               autism$dispatchMacroSound(packet);
            }

            if (moduleHooks) {
               PackModuleRegistry.onSoundPacket(packet);
            }
         }
      }
   }

   @Inject(
      method = {"handleSoundEntityEvent"},
      at = {@At("RETURN")}
   )
   private void autism$onPlayEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (MacroConditionRegistry.hasPendingSoundConditions()) {
            try {
               Minecraft mc = Minecraft.getInstance();
               if (mc == null || mc.level == null || packet == null) {
                  return;
               }

               Entity entity = mc.level.getEntity(packet.getId());
               if (entity == null) {
                  return;
               }

               String soundId = ((SoundEvent)packet.getSound().value()).location().toString();
               MacroConditionRegistry.onSoundPacket(soundId, entity.getX(), entity.getY(), entity.getZ());
            } catch (Exception var6) {
            }
         }
      }
   }

   @Inject(
      method = {"handleSetTime"},
      at = {@At("RETURN")}
   )
   private void yang$onWorldTimeUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (this.autism$packetHooksActive()) {
            AutismSharedState.get().onServerTimeSyncReceived();
         }
      }
   }

   @Unique
   private boolean autism$packetHooksActive() {
      AutismModule module = AutismModule.get();
      return module != null && module.arePacketHooksActive();
   }

   @Unique
   private static void autism$dispatchMacroSound(ClientboundSoundPacket packet) {
      try {
         if (packet == null) {
            return;
         }

         String soundId = ((SoundEvent)packet.getSound().value()).location().toString();
         MacroConditionRegistry.onSoundPacket(soundId, packet.getX(), packet.getY(), packet.getZ());
      } catch (Exception var2) {
      }
   }

   @Unique
   private static void autism$rememberChatCommand(Minecraft mc, String message) {
      if (mc != null && message != null && !message.isBlank()) {
         try {
            if (mc.gui == null || mc.gui.getChat() == null) {
               return;
            }

            ArrayListDeque<String> recent = mc.gui.getChat().getRecentChat();
            if (recent == null || recent.isEmpty() || !message.equals(recent.getLast())) {
               mc.gui.getChat().addRecentChat(message);
            }
         } catch (Throwable var3) {
         }
      }
   }
}
