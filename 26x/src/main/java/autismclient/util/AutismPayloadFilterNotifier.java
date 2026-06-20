package autismclient.util;

import autismclient.modules.PackHideState;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AutismPayloadFilterNotifier {
   private static final long THROTTLE_MS = 2000L;
   private static final long STALE_MS = 30000L;
   private static final int ACCENT = -14249;
   private static final Map<String, State> STATES = new LinkedHashMap();

   private AutismPayloadFilterNotifier() {
   }

   public static synchronized void onMatch(String channel, String direction, AutismPayloadChannelListeners.Match match) {
      if ("S2C".equalsIgnoreCase(direction)) {
         if (!PackHideState.isActive()) {
            String normalized = normalize(channel);
            if (!normalized.isBlank()) {
               long now = System.currentTimeMillis();
               State state = (State)STATES.computeIfAbsent(normalized, (unused) -> new State());
               state.channel = normalized;
               state.summaryLabel = summaryLabel(normalized, match);
               state.lastSeenMs = now;
               if (now >= state.nextToastMs) {
                  if (state.pendingHits > 0) {
                     ++state.pendingHits;
                     showSummary(state);
                  } else {
                     AutismNotifications.show("Payload: " + normalized, -14249);
                  }

                  state.pendingHits = 0;
                  state.nextToastMs = now + 2000L;
               } else {
                  ++state.pendingHits;
               }

            }
         }
      }
   }

   public static synchronized void tick() {
      if (PackHideState.isHardLocked()) {
         clear();
      } else if (!STATES.isEmpty()) {
         long now = System.currentTimeMillis();
         Iterator<Map.Entry<String, State>> it = STATES.entrySet().iterator();

         while(it.hasNext()) {
            State state = (State)((Map.Entry)it.next()).getValue();
            if (state.pendingHits > 0 && now >= state.nextToastMs) {
               showSummary(state);
               state.pendingHits = 0;
               state.nextToastMs = now + 2000L;
            }

            if (state.pendingHits == 0 && now - state.lastSeenMs > 30000L) {
               it.remove();
            }
         }

      }
   }

   public static synchronized void clear() {
      STATES.clear();
   }

   private static void showSummary(State state) {
      String label = state.summaryLabel != null && !state.summaryLabel.isBlank() ? state.summaryLabel : state.channel;
      AutismNotifications.show("Payload: " + label + " +" + state.pendingHits, -14249);
   }

   private static String summaryLabel(String channel, AutismPayloadChannelListeners.Match match) {
      if (match != null && match.pattern() != null && !match.pattern().isBlank()) {
         String pattern = match.pattern().trim().toLowerCase(Locale.ROOT);
         return pattern.contains("*") ? pattern : channel;
      } else {
         return channel;
      }
   }

   private static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   private static final class State {
      String channel = "";
      String summaryLabel = "";
      long nextToastMs;
      long lastSeenMs;
      int pendingHits;

      private State() {
      }
   }
}
