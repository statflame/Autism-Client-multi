package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismPayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"net.minecraft.network.protocol.common.custom.CustomPacketPayload$1"}
)
public abstract class AutismCustomPayloadCodecMixin {
   @Unique
   private int autism$encodeStartIndex = -1;
   @Unique
   private int autism$decodeStartIndex = -1;

   @Inject(
      method = {"encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void autism$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
      if (PackHideState.isHardLocked()) {
         this.autism$encodeStartIndex = -1;
      } else {
         this.autism$encodeStartIndex = autism$shouldCapturePayloadBytes() && buf != null ? buf.writerIndex() : -1;
         boolean isRaw = payload instanceof AutismPayloadSupport.RawCustomPacketPayload;
         if (isRaw || autism$shouldCapturePayloadBytes()) {
            byte[] rememberedBytes = AutismPayloadSupport.getRememberedUnknownPayloadBytes(payload);
            if (rememberedBytes != null || isRaw) {
               Type<?> type = payload.type();
               if (type != null && type.id() != null) {
                  byte[] bytes = rememberedBytes != null ? rememberedBytes : ((AutismPayloadSupport.RawCustomPacketPayload)payload).bytes();
                  buf.writeIdentifier(type.id());
                  if (bytes.length > 0) {
                     buf.writeBytes(bytes);
                  }

                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"},
      at = {@At("TAIL")}
   )
   private void autism$captureEncodedCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
      if (PackHideState.isHardLocked()) {
         this.autism$encodeStartIndex = -1;
      } else if (payload != null && buf != null && this.autism$encodeStartIndex >= 0) {
         int end = buf.writerIndex();
         if (end > this.autism$encodeStartIndex) {
            byte[] encodedBytes = new byte[end - this.autism$encodeStartIndex];
            buf.getBytes(this.autism$encodeStartIndex, encodedBytes);
            AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
            this.autism$encodeStartIndex = -1;
         }
      }
   }

   @Inject(
      method = {"decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;"},
      at = {@At("HEAD")}
   )
   private void autism$captureDecodeStart(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
      if (PackHideState.isHardLocked()) {
         this.autism$decodeStartIndex = -1;
      } else {
         this.autism$decodeStartIndex = autism$shouldCapturePayloadBytes() && buf != null ? buf.readerIndex() : -1;
      }
   }

   @Inject(
      method = {"decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;"},
      at = {@At("RETURN")}
   )
   private void autism$captureDecodedCustomPacketPayload(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
      if (PackHideState.isHardLocked()) {
         this.autism$decodeStartIndex = -1;
      } else {
         CustomPacketPayload payload = (CustomPacketPayload)cir.getReturnValue();
         if (payload != null && buf != null && this.autism$decodeStartIndex >= 0) {
            int end = buf.readerIndex();
            if (end > this.autism$decodeStartIndex) {
               byte[] encodedBytes = new byte[end - this.autism$decodeStartIndex];
               buf.getBytes(this.autism$decodeStartIndex, encodedBytes);
               AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
               this.autism$decodeStartIndex = -1;
            }
         }
      }
   }

   @Unique
   private static boolean autism$shouldCapturePayloadBytes() {
      if (PackHideState.isHardLocked()) {
         return false;
      } else {
         AutismModule module = AutismModule.get();
         return module != null && module.shouldCapturePayloadBytes();
      }
   }

   @Unique
   private static String autism$payloadChannel(CustomPacketPayload payload) {
      try {
         Type<?> type = payload == null ? null : payload.type();
         if (type != null && type.id() != null) {
            return type.id().toString();
         }
      } catch (Throwable var2) {
      }

      return "";
   }
}
