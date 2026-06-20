package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.PackHideState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;

public final class AutismPayloadChannelSubscriptionManager {
   private static final boolean TRACE = Boolean.getBoolean("autism.payload.trace");
   public static final int REGISTRATION_WARNING_VERSION = 1;
   private static final int DEFAULT_LIMIT = 96;
   private static final int HARD_LIMIT = 120;
   private static final int RECENT_CHANNEL_CAP = 64;
   private static boolean dirty = true;
   private static int graceTicks;
   private static String lastKey = "";
   private static long lastSentMs;
   private static Status status = AutismPayloadChannelSubscriptionManager.Status.empty(96);
   private static final LinkedHashSet<String> lastRegisteredChannels = new LinkedHashSet();
   private static final LinkedHashSet<String> recentChannels = new LinkedHashSet();
   private static final LinkedHashSet<String> observedChannels = new LinkedHashSet();

   private AutismPayloadChannelSubscriptionManager() {
   }

   public static synchronized void requestRefresh() {
      dirty = true;
      graceTicks = 0;
   }

   public static synchronized void clear() {
      dirty = true;
      graceTicks = 0;
      lastKey = "";
      lastSentMs = 0L;
      recentChannels.clear();
      observedChannels.clear();
      lastRegisteredChannels.clear();
      status = AutismPayloadChannelSubscriptionManager.Status.empty(96);
   }

   public static synchronized Status status() {
      if (PackHideState.isHardLocked()) {
         status = AutismPayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
         return status;
      } else {
         if (!isRegistrationUnlocked() && !status.locked()) {
            status = AutismPayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), status.lastUnregisteredCount());
         }

         return status;
      }
   }

   public static boolean isRegistrationUnlocked() {
      AutismConfig config = AutismConfig.getGlobal();
      return config != null && config.payloadRegistrationUnlocked && config.payloadRegistrationWarningAcceptedVersion >= 1;
   }

   public static synchronized void unlockRegistration() {
      if (!PackHideState.isHardLocked()) {
         AutismConfig config = AutismConfig.getGlobal();
         config.payloadRegistrationUnlocked = true;
         config.payloadRegistrationWarningAcceptedVersion = 1;
         config.save();
         requestRefresh();
         status = AutismPayloadChannelSubscriptionManager.Status.empty(subscriptionLimit());
      }
   }

   public static void lockRegistrationAndUnregister(Minecraft mc) {
      if (PackHideState.isHardLocked()) {
         synchronized(AutismPayloadChannelSubscriptionManager.class) {
            AutismConfig config = AutismConfig.getGlobal();
            config.payloadRegistrationUnlocked = false;
            config.save();
            dirty = false;
            graceTicks = 0;
            lastKey = "";
            lastSentMs = 0L;
            lastRegisteredChannels.clear();
            status = AutismPayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
         }
      } else {
         UnregisterResult unregister = new UnregisterResult(0, true);
         if (isReady(mc)) {
            unregister = unregisterRemovedChannels(List.of());
         }

         synchronized(AutismPayloadChannelSubscriptionManager.class) {
            AutismConfig config = AutismConfig.getGlobal();
            config.payloadRegistrationUnlocked = false;
            config.save();
            dirty = false;
            graceTicks = 0;
            lastKey = "";
            lastSentMs = System.currentTimeMillis();
            if (unregister.sent()) {
               lastRegisteredChannels.clear();
            }

            status = AutismPayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), unregister.count());
         }
      }
   }

   public static synchronized void rememberRequestedChannel(String channel) {
      String normalized = normalize(channel);
      if (isRegisterableChannel(normalized)) {
         if (!isHiddenProbeChannel(normalized)) {
            addCappedRecent(recentChannels, normalized);
         }
      }
   }

   public static synchronized void rememberObservedChannel(String channel) {
      String normalized = normalize(channel);
      if (isRegisterableChannel(normalized)) {
         if (!isHiddenProbeChannel(normalized)) {
            addCappedRecent(observedChannels, normalized);
         }
      }
   }

   public static synchronized void rememberObservedPayload(AutismPayloadSupport.PayloadSnapshot snapshot) {
      if (snapshot != null) {
         if ("S2C".equalsIgnoreCase(snapshot.direction())) {
            rememberObservedChannel(snapshot.channel());

            for(String channel : AutismPayloadSupport.extractRegisterChannelList(snapshot.channel(), snapshot.rawBytes())) {
               String normalized = normalize(channel);
               if (isRegisterableChannel(normalized) && !isHiddenProbeChannel(normalized)) {
                  addCappedRecent(observedChannels, normalized);
               }
            }

         }
      }
   }

   public static synchronized List<String> learnedChannels() {
      LinkedHashSet<String> channels = new LinkedHashSet();
      channels.addAll(recentChannels);
      channels.addAll(observedChannels);
      return List.copyOf(channels);
   }

   public static void tick(Minecraft mc, boolean packetLoggerCapturing) {
      if (!PackHideState.isHardLocked()) {
         if (isReady(mc)) {
            synchronized(AutismPayloadChannelSubscriptionManager.class) {
               if (graceTicks > 0) {
                  --graceTicks;
                  return;
               }

               if (!dirty) {
                  return;
               }
            }

            if (!isRegistrationUnlocked()) {
               synchronized(AutismPayloadChannelSubscriptionManager.class) {
                  dirty = false;
                  lastKey = "";
                  lastSentMs = 0L;
                  status = AutismPayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
               }
            } else {
               SubscriptionPlan plan = buildPlan(packetLoggerCapturing);
               if (plan.channels().isEmpty()) {
                  UnregisterResult unregister = unregisterRemovedChannels(List.of());
                  synchronized(AutismPayloadChannelSubscriptionManager.class) {
                     dirty = false;
                     lastKey = "";
                     lastSentMs = 0L;
                     status = new Status(0, plan.limit(), plan.skippedCount(), unregister.sent(), System.currentTimeMillis(), List.of(), plan.skippedChannels(), unregister.count(), false);
                  }
               } else {
                  String key = String.join("\n", plan.channels());
                  synchronized(AutismPayloadChannelSubscriptionManager.class) {
                     long now = System.currentTimeMillis();
                     if (!dirty && key.equals(lastKey)) {
                        return;
                     }

                     lastKey = key;
                     lastSentMs = now;
                     dirty = false;
                  }

                  byte[] body = registrationBody(plan.channels());
                  if (body.length != 0) {
                     UnregisterResult unregister = unregisterRemovedChannels(plan.channels());
                     boolean sent = AutismPayloadSupport.sendPayloadSilent("minecraft:register", body, "play");
                     Status newStatus = new Status(plan.channels().size(), plan.limit(), plan.skippedCount(), sent, System.currentTimeMillis(), plan.channels(), plan.skippedChannels(), unregister.count(), false);
                     synchronized(AutismPayloadChannelSubscriptionManager.class) {
                        status = newStatus;
                        if (sent) {
                           lastRegisteredChannels.clear();
                           lastRegisteredChannels.addAll(plan.channels());
                        }
                     }

                     if (TRACE) {
                        AutismClientAddon.LOG.info("[Autism Payload] registered {} / {} plugin channels, skipped {}, unregistered {}, sent={}, sample={}", new Object[]{newStatus.registeredCount(), newStatus.limit(), newStatus.skippedCount(), unregister.count(), sent, newStatus.sampleChannels()});
                     }

                  }
               }
            }
         }
      }
   }

   public static SubscriptionPlan buildPlan(boolean packetLoggerCapturing) {
      int limit = subscriptionLimit();
      ChannelCollector collector = new ChannelCollector(limit);
      AutismPayloadChannelRegistrations registrations = new AutismPayloadChannelRegistrations();

      for(String channel : registrations.enabledChannels()) {
         collector.add(channel);
      }

      return collector.plan();
   }

   private static UnregisterResult unregisterRemovedChannels(List<String> nextChannels) {
      LinkedHashSet<String> removed = new LinkedHashSet();
      synchronized(AutismPayloadChannelSubscriptionManager.class) {
         if (lastRegisteredChannels.isEmpty()) {
            return new UnregisterResult(0, true);
         }

         Set<String> next = new LinkedHashSet(nextChannels == null ? List.of() : nextChannels);

         for(String channel : lastRegisteredChannels) {
            if (!next.contains(channel)) {
               removed.add(channel);
            }
         }
      }

      if (removed.isEmpty()) {
         return new UnregisterResult(0, true);
      } else {
         boolean sent = AutismPayloadSupport.sendPayloadSilent("minecraft:unregister", registrationBody(List.copyOf(removed)), "play");
         if (sent) {
            synchronized(AutismPayloadChannelSubscriptionManager.class) {
               lastRegisteredChannels.removeAll(removed);
            }
         }

         if (TRACE) {
            AutismClientAddon.LOG.info("[Autism Payload] unregistered {} plugin channels: sent={}, sample={}", new Object[]{removed.size(), sent, sample(removed)});
         }

         return new UnregisterResult(removed.size(), sent);
      }
   }

   public static RegistrationImpact impactForPattern(String pattern) {
      String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
      if (normalized.isBlank()) {
         return new RegistrationImpact(false, false, 0, List.of());
      } else {
         LinkedHashSet<String> channels = new LinkedHashSet();
         boolean wildcard = normalized.indexOf(42) >= 0;
         if (!wildcard) {
            if (isRegisterableChannel(normalized)) {
               channels.add(normalized);
            }

            return new RegistrationImpact(false, !channels.isEmpty(), channels.size(), List.copyOf(channels));
         } else {
            for(AutismPayloadChannelListeners.Preset preset : AutismPayloadChannelListeners.presetCatalog()) {
               String presetPattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
               if (presetPattern.indexOf(42) < 0 && AutismPayloadChannelListeners.isRegisterablePreset(preset) && matchesPattern(normalized, presetPattern)) {
                  channels.add(presetPattern);
               }
            }

            return new RegistrationImpact(true, false, channels.size(), List.copyOf(channels));
         }
      }
   }

   public static Projection projectWithPatterns(Collection<String> patterns, boolean packetLoggerCapturing) {
      SubscriptionPlan plan = buildPlan(packetLoggerCapturing);
      LinkedHashSet<String> projected = new LinkedHashSet(plan.channels());
      int before = projected.size();
      if (patterns != null) {
         for(String pattern : patterns) {
            RegistrationImpact impact = impactForPattern(pattern);
            projected.addAll(impact.exactChannels());
         }
      }

      int exactAdded = Math.max(0, projected.size() - before);
      int overflow = Math.max(0, projected.size() - plan.limit());
      return new Projection(projected.size(), plan.limit(), exactAdded, overflow);
   }

   private static int subscriptionLimit() {
      String raw = System.getProperty("autism.payload.subscription.limit");
      if (raw != null && !raw.isBlank()) {
         try {
            return Math.max(1, Math.min(120, Integer.parseInt(raw.trim())));
         } catch (NumberFormatException var2) {
            return 96;
         }
      } else {
         return 96;
      }
   }

   private static boolean isReady(Minecraft mc) {
      return mc != null && mc.getConnection() != null && mc.player != null;
   }

   private static byte[] registrationBody(List<String> channels) {
      StringBuilder builder = new StringBuilder();

      for(String channel : channels) {
         if (isRegisterableChannel(channel)) {
            if (!builder.isEmpty()) {
               builder.append('\u0000');
            }

            builder.append(channel);
         }
      }

      if (builder.isEmpty()) {
         return new byte[0];
      } else {
         return builder.toString().getBytes(StandardCharsets.US_ASCII);
      }
   }

   private static boolean isRegisterableChannel(String channel) {
      return AutismPayloadChannelRegistrations.isRegisterableChannel(channel);
   }

   private static boolean isHiddenProbeChannel(String channel) {
      if (TRACE) {
         return false;
      } else {
         String normalized = normalize(channel);
         return normalized.contains("autismtest") || normalized.contains("payloadprobe") || normalized.contains("payload_probe") || normalized.contains("brandlike") || normalized.contains("mc_string") || normalized.contains("java_utf");
      }
   }

   private static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   private static void addCappedRecent(LinkedHashSet<String> set, String channel) {
      if (set.remove(channel)) {
         set.add(channel);
      } else {
         set.add(channel);

         while(set.size() > 64) {
            String first = (String)set.iterator().next();
            set.remove(first);
         }

      }
   }

   private static boolean matchesPattern(String pattern, String text) {
      String normalizedPattern = AutismPayloadChannelListeners.normalizePattern(pattern);
      String normalizedText = normalize(text);
      if (!normalizedPattern.isEmpty() && !normalizedText.isEmpty()) {
         if ("*".equals(normalizedPattern)) {
            return true;
         } else if (normalizedPattern.indexOf(42) < 0) {
            return normalizedText.equals(normalizedPattern);
         } else {
            int p = 0;
            int t = 0;
            int star = -1;
            int mark = 0;

            while(t < normalizedText.length()) {
               if (p < normalizedPattern.length() && normalizedPattern.charAt(p) == normalizedText.charAt(t)) {
                  ++p;
                  ++t;
               } else if (p < normalizedPattern.length() && normalizedPattern.charAt(p) == '*') {
                  star = p++;
                  mark = t;
               } else {
                  if (star == -1) {
                     return false;
                  }

                  p = star + 1;
                  ++mark;
                  t = mark;
               }
            }

            while(p < normalizedPattern.length() && normalizedPattern.charAt(p) == '*') {
               ++p;
            }

            return p == normalizedPattern.length();
         }
      } else {
         return false;
      }
   }

   private static String sample(Set<String> channels) {
      if (channels != null && !channels.isEmpty()) {
         List<String> values = new ArrayList(channels);
         int count = Math.min(8, values.size());
         String var10000 = String.join(", ", values.subList(0, count));
         return var10000 + (values.size() > count ? ", ..." : "");
      } else {
         return "";
      }
   }

   private static final class ChannelCollector {
      private final int limit;
      private final LinkedHashMap<String, String> channels = new LinkedHashMap();
      private final LinkedHashSet<String> skipped = new LinkedHashSet();

      private ChannelCollector(int limit) {
         this.limit = limit;
      }

      private void add(String channel) {
         String normalized = AutismPayloadChannelSubscriptionManager.normalize(channel);
         if (AutismPayloadChannelSubscriptionManager.isRegisterableChannel(normalized)) {
            if (!this.channels.containsKey(normalized)) {
               if (this.channels.size() >= this.limit) {
                  this.skipped.add(normalized);
               } else {
                  this.channels.put(normalized, normalized);
               }
            }
         }
      }

      private SubscriptionPlan plan() {
         return new SubscriptionPlan(List.copyOf(this.channels.values()), this.limit, this.skipped.size(), List.copyOf(this.skipped));
      }
   }

   public static record Status(int registeredCount, int limit, int skippedCount, boolean lastSendSucceeded, long lastSentMs, List<String> channels, List<String> skippedChannels, int lastUnregisteredCount, boolean locked) {
      private static Status empty(int limit) {
         return new Status(0, limit, 0, false, 0L, List.of(), List.of(), 0, false);
      }

      private static Status locked(int limit, int lastUnregisteredCount) {
         return new Status(0, limit, 0, false, System.currentTimeMillis(), List.of(), List.of(), Math.max(0, lastUnregisteredCount), true);
      }

      public String shortLabel() {
         if (this.locked) {
            return this.lastUnregisteredCount > 0 ? "Registration locked -" + this.lastUnregisteredCount : "Registration locked";
         } else {
            String base = "Registered " + this.registeredCount + "/" + this.limit;
            if (this.lastUnregisteredCount > 0) {
               base = base + " -" + this.lastUnregisteredCount;
            }

            return this.skippedCount > 0 ? base + " +" + this.skippedCount : base;
         }
      }

      public String sampleChannels() {
         if (this.channels != null && !this.channels.isEmpty()) {
            int count = Math.min(8, this.channels.size());
            String var10000 = String.join(", ", this.channels.subList(0, count));
            return var10000 + (this.channels.size() > count ? ", ..." : "");
         } else {
            return "";
         }
      }
   }

   public static record SubscriptionPlan(List<String> channels, int limit, int skippedCount, List<String> skippedChannels) {
   }

   public static record RegistrationImpact(boolean wildcard, boolean exact, int exactChannelCount, List<String> exactChannels) {
      public String compactLabel() {
         return this.exact ? "1ch" : this.exactChannelCount + "ch";
      }
   }

   public static record Projection(int projectedCount, int limit, int addedCount, int overflowCount) {
      public boolean exceedsLimit() {
         return this.overflowCount > 0;
      }
   }

   private static record UnregisterResult(int count, boolean sent) {
   }
}
