package autismclient.mixin;

import autismclient.mixin.accessor.AutismClientConnectionAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.modules.PackModule;
import autismclient.modules.PackModuleRegistry;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismSpoofPayloadFilter;
import autismclient.util.AutismContainerHold;
import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.PacketGateManager;
import autismclient.util.macro.ServerTickTracker;
import autismclient.util.macro.XCarryAction;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Connection.class})
public abstract class AutismClientConnectionMixin {
   @Unique
   private static final boolean AUTISM_PACKET_TRACE = Boolean.getBoolean("autism.packet.trace");
   @Unique
   private volatile boolean autism$spoofPipelineInstalled;
   @Unique
   private static final String AUTISM_SPOOF_FILTER = "autism_spoof_filter";

   @Inject(
      method = {"channelActive"},
      at = {@At("HEAD")}
   )
   private void autism$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
      this.autism$spoofPipelineInstalled = false;
      this.autism$ensureSpoofPipelineFilter();
   }

   @Inject(
      method = {"channelInactive"},
      at = {@At("HEAD")}
   )
   private void autism$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
      this.autism$spoofPipelineInstalled = false;
      AutismProtectorPackStrip.clearAll();
   }

   @Inject(
      method = {"configurePacketHandler"},
      at = {@At("TAIL")}
   )
   private void autism$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
      this.autism$ensureSpoofPipelineFilter();
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      this.autism$ensureSpoofPipelineFilter();
      if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
         AutismProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
      }

      PacketListener packetListener = ((Connection)(Object)this).getPacketListener();
      AutismModule module = AutismModule.get();
      AutismModule.PacketHookSnapshot hooks = module == null
         ? AutismModule.PacketHookSnapshot.inactive()
         : module.packetHookSnapshot(this.isPlayConnectionActive());
      boolean normalLoggerPath = hooks.normalPath();
      String protocolHint = autism$protocolHint(packetListener);
      if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
         ci.cancel();
      } else if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
         ci.cancel();
      } else if (!PackHideState.isHardLocked()) {
         boolean payloadLoggedEarly = false;
         if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "C2S", protocolHint);
         }

         if (module != null && !payloadLoggedEarly && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "C2S", protocolHint);
         }

         if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketSend(packet);
         }

         if (packet instanceof ServerboundUseItemOnPacket pibp
            && AutismSharedState.get().consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
            ci.cancel();
         } else if (normalLoggerPath) {
            if (packet instanceof ServerboundUseItemOnPacket pibp) {
               AutismSharedState.get().setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
               AutismSharedState.get().setLastContainerTarget(AutismContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
            }

            if (packet instanceof ServerboundInteractPacket entityPacket) {
               Entity targeted = Minecraft.getInstance().crosshairPickEntity;
               if (targeted != null && targeted != Minecraft.getInstance().player) {
                  InteractionHand capturedHand = entityPacket.hand();
                  Vec3 capturedHitPos = entityPacket.location();
                  AutismSharedState.get()
                     .setLastContainerTarget(
                        capturedHitPos != null
                           ? AutismContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                           : AutismContainerTarget.forEntity(targeted, capturedHand)
                     );
               }
            }

            if (packet instanceof ServerboundInteractPacket && AutismSharedState.get().hasEntityCaptureCallback()) {
               Entity targeted = Minecraft.getInstance().crosshairPickEntity;
               if (targeted != null && targeted != Minecraft.getInstance().player) {
                  AutismSharedState state = AutismSharedState.get();
                  String payload;
                  if (state.isEntityCaptureSpecific()) {
                     String uuid = targeted.getStringUUID();
                     String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                     String dispName = targeted.getDisplayName().getString().replaceAll("§.", "").trim();
                     payload = "~" + uuid + "~" + typeId + "~" + dispName;
                     state.setEntityCaptureSpecific(false);
                  } else {
                     payload = BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                  }

                  state.consumeEntityCaptureCallback(payload);
               }
            }

            AutismSharedState shared = AutismSharedState.get();
            if (packet instanceof ServerboundContainerClosePacket closeForHold) {
               if (shared.consumeSuppressNextContainerClosePacket()) {
                  ci.cancel();
                  return;
               }

               if (AutismContainerHold.isHeld(closeForHold.getContainerId())) {
                  AutismContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                  ci.cancel();
                  return;
               }
            }

            if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
               ci.cancel();
            } else if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
               ci.cancel();
            } else {
               boolean forceBookOrSignPacket = packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                  || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();
               if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
                  shared.setAllowSignEditing(true);
                  if (!forceBookOrSignPacket) {
                     ci.cancel();
                     return;
                  }
               }

               if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
                  shared.setAllowBookUpdate(true);
                  if (!forceBookOrSignPacket) {
                     ci.cancel();
                     return;
                  }
               }

               if (packet instanceof ServerboundContainerClosePacket closePacket && this.autism$shouldKeepXCarryOpen(shared, closePacket)) {
                  ci.cancel();
               } else if (shared.isGBreakCapturing()) {
                  if (packet instanceof ServerboundPlayerActionPacket) {
                     shared.onGBreakPacket(packet);
                  }
               } else {
                  if (packet instanceof ServerboundPlayerActionPacket actionPacket
                     && actionPacket.getAction() == Action.START_DESTROY_BLOCK
                     && PackModuleRegistry.hasStartBreakingHooks()) {
                     for (PackModule moduleEntry : PackModuleRegistry.startBreakingModulesForDispatch()) {
                        if (moduleEntry.shouldCancelStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                           ci.cancel();
                           return;
                        }

                        moduleEntry.onStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection());
                     }
                  }

                  if (module.handlePacketSend(packet, payloadLoggedEarly)) {
                     ci.cancel();
                  } else if (!forceBookOrSignPacket) {
                     if (!shared.isFlushing()) {
                        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
                        if (gateResult == PacketGateManager.Result.CANCEL) {
                           ci.cancel();
                           return;
                        }

                        if (gateResult == PacketGateManager.Result.DELAY) {
                           shared.enqueuePacket(packet);
                           ci.cancel();
                           return;
                        }
                     }

                     boolean anyFeatureActive = shared.shouldDelayGuiPackets() || !shared.shouldSendGuiPackets() || shared.shouldUseCustomPackets();
                     if (anyFeatureActive) {
                        if (!shared.isFlushing()) {
                           boolean shouldHandle = false;
                           if (shared.shouldUseCustomPackets()) {
                              shouldHandle = shared.getC2SPackets().contains(packet.getClass());
                           } else {
                              shouldHandle = this.isGuiPacket(packet);
                           }

                           if (shouldHandle) {
                              if (AUTISM_PACKET_TRACE) {
                                 autismclient.AutismClientAddon.LOG
                                    .debug(
                                       "[Autism] Packet detected: {} | Send={} Delay={} | Custom={}",
                                       new Object[]{
                                          packet.getClass().getSimpleName(),
                                          shared.shouldSendGuiPackets(),
                                          shared.shouldDelayGuiPackets(),
                                          shared.shouldUseCustomPackets()
                                       }
                                    );
                              }

                              if (!shared.shouldSendGuiPackets()) {
                                 if (AUTISM_PACKET_TRACE) {
                                    autismclient.AutismClientAddon.LOG.debug("[Autism] CANCELLED packet (send disabled)");
                                 }

                                 ci.cancel();
                              } else {
                                 if (shared.shouldDelayGuiPackets()) {
                                    if (AUTISM_PACKET_TRACE) {
                                       autismclient.AutismClientAddon.LOG.debug("[Autism] QUEUED packet (delay enabled)");
                                    }

                                    AutismModule captureModule = AutismModule.get();
                                    AutismSharedState.ReplayMode captureMode = captureModule != null && captureModule.isCaptureAsExact()
                                       ? AutismSharedState.ReplayMode.EXACT
                                       : AutismSharedState.ReplayMode.REGENERATE;
                                    shared.enqueuePacket(packet, captureMode);
                                    ci.cancel();
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = {@At("TAIL")}
   )
   private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (this.isAutismActive()) {
            if (this.isPlayConnectionActive()) {
               MacroExecutor.onPacketSent(packet);
            }
         }
      }
   }

   @Unique
   private boolean isGuiPacket(Packet<?> packet) {
      return packet instanceof ServerboundContainerClickPacket
         || packet instanceof ServerboundContainerButtonClickPacket
         || packet instanceof ServerboundSetCreativeModeSlotPacket
         || packet instanceof ServerboundPlayerActionPacket
         || packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundSignUpdatePacket
         || packet instanceof ServerboundEditBookPacket
         || packet instanceof ServerboundChatPacket
         || packet instanceof ServerboundChatCommandPacket
         || packet instanceof ServerboundChatCommandSignedPacket;
   }

   @Inject(
      method = {"channelRead0"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
      PacketListener listener = ((Connection)(Object)this).getPacketListener();
      AutismModule module = AutismModule.get();
      AutismModule.PacketHookSnapshot hooks = module == null
         ? AutismModule.PacketHookSnapshot.inactive()
         : module.packetHookSnapshot(isPlayReceiveListener(listener));
      boolean normalLoggerPath = hooks.normalPath();
      String protocolHint = autism$protocolHint(listener);
      if (PackHideState.isHardLocked()) {
         this.autism$clearRuntimeConnectionStateOnDisconnect(packet);
      } else {
         boolean payloadLoggedEarly = false;
         if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "S2C", protocolHint);
         }

         if (module != null && !payloadLoggedEarly && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "S2C", protocolHint);
         }

         if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketReceive(packet);
         }

         if (normalLoggerPath) {
            ServerTickTracker.onS2CPacket(packet);
            MacroExecutor.onPacketReceived(packet);
            if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
               AutismContainerHold.onContainerOpened(openScreenPacket.getContainerId());
            }

            if (packet instanceof ClientboundDisconnectPacket) {
               this.autism$clearRuntimeConnectionStateOnDisconnect(packet);
            }

            if (module.handlePacketReceive(packet, payloadLoggedEarly)) {
               ci.cancel();
            } else {
               AutismSharedState shared = AutismSharedState.get();
               PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
               if (gateResult == PacketGateManager.Result.CANCEL) {
                  ci.cancel();
               } else if (gateResult == PacketGateManager.Result.DELAY) {
                  shared.enqueuePacket(packet);
                  ci.cancel();
               } else {
                  boolean anyFeatureActive = shared.shouldDelayGuiPackets() || !shared.shouldSendGuiPackets() || shared.shouldUseCustomPackets();
                  if (anyFeatureActive) {
                     boolean shouldHandle = false;
                     if (shared.shouldUseCustomPackets()) {
                        shouldHandle = shared.getS2CPackets().contains(packet.getClass());
                     }

                     if (shouldHandle) {
                        if (AUTISM_PACKET_TRACE) {
                           autismclient.AutismClientAddon.LOG
                              .debug(
                                 "[Autism] S2C Packet detected: {} | Send={} Delay={}",
                                 new Object[]{packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(), shared.shouldDelayGuiPackets()}
                              );
                        }

                        if (!shared.shouldSendGuiPackets()) {
                           ci.cancel();
                        } else {
                           if (shared.shouldDelayGuiPackets()) {
                              shared.enqueuePacket(packet);
                              ci.cancel();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private boolean isAutismActive() {
      AutismModule module = AutismModule.get();
      return module != null && module.arePacketHooksActive();
   }

   @Unique
   private void autism$ensureSpoofPipelineFilter() {
      if (!this.autism$spoofPipelineInstalled) {
         Channel channel = null;

         try {
            channel = ((AutismClientConnectionAccessor)this).getChannel();
         } catch (Throwable var3) {
         }

         if (channel != null) {
            try {
               ChannelPipeline pipeline = channel.pipeline();
               if (pipeline == null || pipeline.get("autism_spoof_filter") != null) {
                  this.autism$spoofPipelineInstalled = true;
                  return;
               }

               if (pipeline.get("encoder") != null) {
                  pipeline.addAfter("encoder", "autism_spoof_filter", new AutismSpoofPayloadFilter());
                  this.autism$spoofPipelineInstalled = true;
               }
            } catch (Throwable var4) {
               autismclient.AutismClientAddon.LOG.debug("[Autism] Failed to install client spoof payload filter", var4);
            }
         }
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void autism$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      this.autism$ensureSpoofPipelineFilter();
      if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(AutismModule.get(), packet)) {
         ci.cancel();
      } else {
         if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
            ci.cancel();
         }
      }
   }

   @Unique
   private void autism$clearRuntimeConnectionStateOnDisconnect(Packet<?> packet) {
      if (packet instanceof ClientboundDisconnectPacket) {
         AutismContainerHold.clearAll();
         PacketGateManager.clearAll();
         AutismSharedState s = AutismSharedState.get();
         s.setXCarryForcedTargets(Collections.emptySet(), false);
         s.setXCarryForced(false);
         s.setXCarryActive(false);
      }
   }

   @Unique
   private boolean isPlayConnectionActive() {
      Minecraft client = Minecraft.getInstance();
      return client != null && client.getConnection() != null;
   }

   @Unique
   private static boolean isPlayReceiveListener(PacketListener listener) {
      return listener instanceof ClientGamePacketListener;
   }

   @Unique
   private static String autism$protocolHint(PacketListener listener) {
      if (listener == null) {
         return "";
      } else if (listener instanceof ClientGamePacketListener) {
         return "play";
      } else {
         String name = listener.getClass().getName().toLowerCase(Locale.ROOT);
         return name.contains("configuration") ? "configuration" : "";
      }
   }

   @Unique
   private boolean autism$shouldKeepXCarryOpen(AutismSharedState shared, ServerboundContainerClosePacket packet) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         AutismModule module = AutismModule.get();
         boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();
         if (!allowPassiveXCarry && !shared.isXCarryForced()) {
            return false;
         } else if (packet.getContainerId() != client.player.inventoryMenu.containerId) {
            return false;
         } else {
            Set<Integer> mask;
            boolean carryCursor;
            if (shared.isXCarryForced()) {
               mask = shared.getXCarryForcedSlotMask();
               carryCursor = shared.isXCarryForcedCarryCursor();
            } else {
               mask = module == null ? null : module.getXCarryModuleSlotMask();
               carryCursor = module == null || module.isXCarryCarryCursor();
            }

            boolean hasItems = XCarryAction.hasStoredItems(client.player.inventoryMenu, carryCursor, mask);
            shared.setXCarryActive(hasItems);
            return true;
         }
      } else {
         return false;
      }
   }
}
