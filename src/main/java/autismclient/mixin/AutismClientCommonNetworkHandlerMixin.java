package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismResourcePackTruthGuard;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class AutismClientCommonNetworkHandlerMixin {
   @Shadow
   @Final
   protected Minecraft minecraft;

   @Shadow
   public abstract void send(Packet<?> var1);

   @Inject(
      method = {"handleResourcePackPush"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         AutismSharedState shared = AutismSharedState.get();
         AutismModule module = AutismModule.get();
         boolean shouldForceDeny = shared.shouldForceDenyResourcePack() || module != null && module.isForceDenyResourcePack();
         boolean shouldBypass = shared.shouldBypassResourcePack() || module != null && module.isBypassResourcePack();
         AutismResourcePackTruthGuard.Verdict verdict = AutismResourcePackTruthGuard.classify(packet, shouldForceDeny, shouldBypass);
         if (verdict.shouldCancelVanilla()) {
            AutismProtectorPackStrip.onPop(packet.id());
            switch (verdict.kind()) {
               case DECLINE -> {
                  this.send(new ServerboundResourcePackPacket(packet.id(), Action.DECLINED));
                  AutismClientMessaging.sendPrefixed("Autism denied server resource pack.");
               }
               case INVALID_URL -> this.send(new ServerboundResourcePackPacket(packet.id(), Action.INVALID_URL));
               case FAILED_DOWNLOAD -> {
                  this.send(new ServerboundResourcePackPacket(packet.id(), Action.ACCEPTED));
                  this.send(new ServerboundResourcePackPacket(packet.id(), Action.FAILED_DOWNLOAD));
               }
               default -> {
               }
            }

            ci.cancel();
         }
      }
   }
}
