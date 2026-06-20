package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismSharedState;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

public final class PacketGateManager {
   private static final ConcurrentHashMap<String, PacketGateManager.Gate> GATES = new ConcurrentHashMap<>();
   private static String lastOpenedGateId = null;

   private PacketGateManager() {
   }

   public static void install(PacketGateAction action) {
      if (!PackHideState.isHardLocked()) {
         if (action != null) {
            String id = action.gateId != null && !action.gateId.isBlank() ? action.gateId.trim() : "auto";
            if (action.mode == PacketGateAction.GateMode.DISABLE_GATE) {
               disable(id);
            } else {
               GATES.put(id, new PacketGateManager.Gate(id, action.mode, action.effectivePackets(), action.flushOnDisable));
               lastOpenedGateId = id;
            }
         }
      }
   }

   public static void disable(String id) {
      if (id != null && !id.isBlank() && !id.equalsIgnoreCase("all")) {
         GATES.remove(id.trim());
      } else {
         GATES.clear();
      }
   }

   public static void disableAndFlushConfigured(String id, ClientPacketListener connection, boolean flushRequested) {
      if (PackHideState.isHardLocked()) {
         disable(id);
      } else if (id != null && !id.isBlank() && !id.equalsIgnoreCase("all")) {
         PacketGateManager.Gate removed = GATES.remove(id.trim());
         if (removed != null && lastOpenedGateId != null && lastOpenedGateId.equals(removed.id)) {
            lastOpenedGateId = newestGateIdOrNull();
         }

         boolean shouldFlush = flushRequested || removed != null && removed.flushOnDisable;
         if (shouldFlush && !hasActiveDelayGate()) {
            AutismSharedState.get().flushDelayedPackets(connection);
         }
      } else {
         clearAllAndFlushConfigured(connection);
      }
   }

   public static void disableCurrentGate() {
      if (PackHideState.isHardLocked()) {
         clearAll();
      } else {
         if (lastOpenedGateId != null) {
            disableAndFlushConfigured(lastOpenedGateId, Minecraft.getInstance().getConnection(), true);
            lastOpenedGateId = null;
         }
      }
   }

   public static void clearAll() {
      GATES.clear();
      lastOpenedGateId = null;
   }

   public static void clearAllAndFlushConfigured(ClientPacketListener connection) {
      if (PackHideState.isHardLocked()) {
         clearAll();
      } else {
         boolean flush = GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY && g.flushOnDisable);
         clearAll();
         if (flush) {
            AutismSharedState.get().flushDelayedPackets(connection);
         }
      }
   }

   private static boolean hasActiveDelayGate() {
      return GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY);
   }

   private static String newestGateIdOrNull() {
      String newest = null;

      for (String id : GATES.keySet()) {
         newest = id;
      }

      return newest;
   }

   public static PacketGateManager.Result handle(Packet<?> packet, String direction) {
      if (PackHideState.isHardLocked()) {
         return PacketGateManager.Result.PASS;
      } else if (packet != null && !GATES.isEmpty()) {
         PacketGateManager.Result result = PacketGateManager.Result.PASS;

         for (PacketGateManager.Gate gate : GATES.values()) {
            boolean packetMatches = gate.packets.isEmpty() || gate.packets.stream().anyMatch(p -> matchesPacket(p, packet, direction));
            switch (gate.mode) {
               case CANCEL:
                  if (packetMatches) {
                     return PacketGateManager.Result.CANCEL;
                  }
                  break;
               case DELAY:
                  if (packetMatches) {
                     result = PacketGateManager.Result.DELAY;
                  }
                  break;
               case ALLOW_ONLY:
                  if (!packetMatches) {
                     return PacketGateManager.Result.CANCEL;
                  }
               case DISABLE_GATE:
            }
         }

         return result;
      } else {
         return PacketGateManager.Result.PASS;
      }
   }

   public static boolean hasActiveGates() {
      return PackHideState.isHardLocked() ? false : !GATES.isEmpty();
   }

   public static boolean matchesPacket(String expected, Packet<?> packet, String direction) {
      if (expected != null && !expected.isBlank()) {
         String want = normalize(expected);
         String friendlyDirectional = normalize(AutismPacketNamer.getFriendlyName(packet, direction));
         String friendly = normalize(AutismPacketNamer.getFriendlyName(packet));
         String simple = normalize(packet.getClass().getSimpleName());
         String registry = normalize(AutismPacketRegistry.getName((Class<? extends Packet<?>>) packet.getClass()));
         return contains(want, friendlyDirectional) || contains(want, friendly) || contains(want, simple) || contains(want, registry);
      } else {
         return true;
      }
   }

   private static boolean contains(String a, String b) {
      return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.endsWith(b) || b.endsWith(a));
   }

   private static String normalize(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   public static final class Gate {
      public final String id;
      public final PacketGateAction.GateMode mode;
      public final ArrayList<String> packets;
      public final boolean flushOnDisable;

      Gate(String id, PacketGateAction.GateMode mode, ArrayList<String> packets, boolean flushOnDisable) {
         this.id = id;
         this.mode = mode;
         this.packets = packets;
         this.flushOnDisable = flushOnDisable;
      }
   }

   public static enum Result {
      PASS,
      CANCEL,
      DELAY;
   }
}
