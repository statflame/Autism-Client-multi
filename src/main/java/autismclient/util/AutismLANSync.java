package autismclient.util;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.lan.LanPacket;
import autismclient.util.lan.LanPacketType;
import autismclient.util.macro.MacroConditionRegistry;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.ServerTickTracker;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

public class AutismLANSync {
   private static AutismLANSync instance;
   private static final int MACRO_INLINE_UTF_LIMIT = 60000;
   private static final int MACRO_CHUNK_SIZE = 32000;
   private static final long MACRO_CHUNK_TIMEOUT_MS = 120000L;
   private static final int TICK_EXECUTION_DELAY_TICKS = 10;
   private static final String COMMAND_QUEUE_FLUSH = "__QUEUE_FLUSH__";
   private static final String COMMAND_DELAY_PACKETS = "__DELAY_PACKETS__\t";
   private final AtomicBoolean running = new AtomicBoolean(false);
   private String sessionId;
   private boolean isHost = false;
   private String myUsername = "";
   private final Map<String, AutismLANSync.ClientInfo> connectedClients = new ConcurrentHashMap<>();
   private final Map<String, String> discoveredSessions = new ConcurrentHashMap<>();
   private final Set<String> clientsReady = ConcurrentHashMap.newKeySet();
   private volatile boolean isSearching = false;
   private int searchTicksRemaining = 0;
   private static final int SEARCH_DURATION_TICKS = 400;
   private static final int SEARCH_BROADCAST_INTERVAL = 5;
   private Runnable onClientJoined;
   private Runnable onClientLeft;
   private Runnable onCountdown;
   private Runnable onSendNow;
   private Runnable onSessionStateChanged;
   private Runnable onSyncStateChanged;
   private Runnable onSpreadCalculated;
   private Runnable onPeerStatusChanged;
   private volatile ConcurrentHashMap<String, Map<String, AutismMacro>> clientMacroLists = new ConcurrentHashMap<>();
   private volatile ConcurrentHashMap<String, Map<String, AutismMacro>> remoteMeteorMacros = new ConcurrentHashMap<>();
   private Map<String, String> lastBroadcastMeteorMacros = new ConcurrentHashMap<>();
   private final Map<String, AutismLANSync.PendingMacroTransfer> pendingMacroTransfers = new ConcurrentHashMap<>();
   private volatile Map<String, String> syncedAssignments = new ConcurrentHashMap<>();
   private volatile String assignmentSetBy = "";
   private Runnable onAssignmentsChanged;
   private volatile AutismLANSync.SpreadResult lastSpreadResult = null;
   private final CopyOnWriteArrayList<AutismLANSync.SpreadResult> spreadHistory = new CopyOnWriteArrayList<>();
   private static final int MAX_SPREAD_HISTORY = 10;
   private volatile AutismLANSync.SyncContext activeSyncCtx = null;
   private volatile AutismLANSync.SyncState syncState = AutismLANSync.SyncState.IDLE;
   private volatile AutismLANSync.ExecutionMethod executionMethod = AutismLANSync.ExecutionMethod.INSTANT;
   private volatile AutismLANSync.ScheduledTickExecution scheduledTickExecution = null;
   private volatile long observedGameTick = -1L;
   private final Map<String, Long> executionTimestamps = new ConcurrentHashMap<>();
   private final Map<String, Long> ackReceiptTimes = new ConcurrentHashMap<>();
   private final Map<String, AutismLANSync.TickExecutionReport> tickExecutionReports = new ConcurrentHashMap<>();
   private final Set<Long> locallyExecutedIds = ConcurrentHashMap.newKeySet();
   private final Map<String, Integer> peerStepProgress = new ConcurrentHashMap<>();
   private volatile Runnable onStepProgressChanged;
   private static final int TCP_PORT = 25568;
   private volatile ServerSocket tcpServer;
   private volatile Thread tcpAcceptThread;
   private final Map<String, Socket> tcpClients = new ConcurrentHashMap<>();
   private volatile Socket tcpHostSocket;
   private volatile Thread tcpReadThread;
   private final Object tcpHostWriteLock = new Object();
   private final Map<String, Object> tcpClientWriteLocks = new ConcurrentHashMap<>();
   private final Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
   private long lastHeartbeatSentMs = 0L;
   private static final long HEARTBEAT_INTERVAL_MS = 9000L;
   private static final long HEARTBEAT_STALE_MS = 15000L;
   private static final long HEARTBEAT_DEAD_MS = 30000L;
   private volatile boolean reconnecting = false;
   private volatile String lastHostIp = null;
   private volatile String lastSessionId = null;

   private AutismLANSync() {
   }

   public static AutismLANSync getInstance() {
      if (instance == null) {
         instance = new AutismLANSync();
      }

      return instance;
   }

   public void start() {
      if (!PackHideState.isHardLocked()) {
         if (!this.running.get()) {
            this.running.set(true);
            AutismClientMessaging.sendPrefixed("§aLAN Sync started (TCP mode on port 25568).");
            autismclient.AutismClientAddon.LOG.info("[Autism-LAN] Started in TCP-only mode on port {}", 25568);
         }
      }
   }

   public void stop() {
      if (this.running.get()) {
         if (this.sessionId != null) {
            this.leaveSession();
         }

         this.running.set(false);
         this.clearSession();
         this.discoveredSessions.clear();
         AutismClientMessaging.sendPrefixed("§eLAN Sync stopped.");
      }
   }

   public void stopSilently() {
      if (this.sessionId != null) {
         this.reconnecting = false;
         this.lastHostIp = null;
         this.lastSessionId = null;
         this.sendTcpPacket(new LanPacket.LeavePacket(this.sessionId, this.getUsername()));
      }

      this.running.set(false);
      this.clearSession();
      this.discoveredSessions.clear();
   }

   public void createSession() {
      if (!this.running.get()) {
         AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
      } else {
         this.cancelSearch();
         this.discoveredSessions.clear();
         if (this.sessionId != null) {
            this.leaveSession();
         }

         this.stopTcp();
         this.sessionId = this.generateSessionId();
         this.isHost = true;
         this.myUsername = this.getUsername();
         if (this.myUsername.isEmpty()) {
            this.myUsername = "Host";
         }

         this.connectedClients.put(this.myUsername, new AutismLANSync.ClientInfo(this.myUsername, true));
         this.resetSyncState();
         this.startTcpServer();
         AutismClientMessaging.sendPrefixed("§aSession created: " + this.sessionId + " [TCP Sync]");
         this.fireCallback(this.onSessionStateChanged);
      }
   }

   public void joinSession(String targetSessionId) {
      this.joinSession(targetSessionId, "127.0.0.1");
   }

   public void joinSession(String targetSessionId, String hostIp) {
      if (!this.running.get()) {
         AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
      } else if (this.sessionId != null && this.sessionId.equals(targetSessionId)) {
         AutismClientMessaging.sendPrefixed("§cAlready in session " + this.sessionId);
      } else {
         this.cancelSearch();
         this.discoveredSessions.clear();
         if (this.sessionId != null) {
            this.leaveSession();
         }

         this.stopTcp();
         this.sessionId = targetSessionId;
         this.lastSessionId = targetSessionId;
         this.isHost = false;
         this.myUsername = this.getUsername();
         if (this.myUsername.isEmpty()) {
            this.myUsername = "Player";
         }

         this.connectedClients.put(this.myUsername, new AutismLANSync.ClientInfo(this.myUsername, false));
         this.resetSyncState();
         this.lastHostIp = hostIp != null && !hostIp.isBlank() ? hostIp : "127.0.0.1";
         this.connectToTcpServer(this.lastHostIp);
         autismclient.AutismClientAddon.LOG.info("[TCP] Connecting to {}:{}", this.lastHostIp, 25568);
         this.fireCallback(this.onClientJoined);
         this.fireCallback(this.onSessionStateChanged);
      }
   }

   public void leaveSession() {
      if (this.sessionId != null) {
         this.reconnecting = false;
         this.lastHostIp = null;
         this.lastSessionId = null;
         this.sendTcpPacket(new LanPacket.LeavePacket(this.sessionId, this.getUsername()));
         this.clearSession();
         AutismClientMessaging.sendPrefixed("§eLeft session.");
         this.fireCallback(this.onSessionStateChanged);
      }
   }

   private void clearSession() {
      this.sessionId = null;
      this.isHost = false;
      this.myUsername = "";
      this.reconnecting = false;
      this.lastHostIp = null;
      this.lastSessionId = null;
      this.connectedClients.clear();
      this.clientMacroLists.clear();
      this.remoteMeteorMacros.clear();
      this.lastBroadcastMeteorMacros.clear();
      this.syncedAssignments.clear();
      this.assignmentSetBy = "";
      this.isSearching = false;
      this.searchTicksRemaining = 0;
      this.lastSpreadResult = null;
      this.observedGameTick = -1L;
      this.resetSyncState();
      this.lastHeartbeatTime.clear();
      this.stopTcp();
   }

   private void resetSyncState() {
      this.activeSyncCtx = null;
      this.setSyncState(AutismLANSync.SyncState.IDLE);
      this.executionTimestamps.clear();
      this.ackReceiptTimes.clear();
      this.tickExecutionReports.clear();
      this.scheduledTickExecution = null;
      this.locallyExecutedIds.clear();
      this.clientsReady.clear();
      this.peerStepProgress.clear();
   }

   private void setSyncState(AutismLANSync.SyncState state) {
      this.syncState = state;
      this.fireCallback(this.onSyncStateChanged);
   }

   private void initiateGoSync(boolean isMacro, String macroName, String command) {
      this.initiateGoSync(isMacro, macroName, command, null);
   }

   private void initiateGoSync(boolean isMacro, String macroName, String command, Map<String, String> macroAssignments) {
      long executionId = System.nanoTime();
      if (isMacro) {
         if (macroAssignments != null && !macroAssignments.isEmpty()) {
            Map<String, String> missingAssignments = this.getMissingAssignedMacros(macroAssignments);
            if (!missingAssignments.isEmpty()) {
               AutismClientMessaging.sendPrefixed("§cMissing assigned macros on: " + this.summarizeAssignments(missingAssignments));
               return;
            }
         } else if (macroName != null && !macroName.isBlank()) {
            List<String> missingPeers = this.getPeersMissingMacro(macroName);
            if (!missingPeers.isEmpty()) {
               AutismClientMessaging.sendPrefixed("§cMissing '" + macroName + "' on: " + this.summarizeNames(missingPeers));
               return;
            }
         }
      }

      String myMacroName = macroName;
      if (macroAssignments != null && macroAssignments.containsKey(this.myUsername)) {
         myMacroName = macroAssignments.get(this.myUsername);
      }

      AutismMacro macro = null;
      if (isMacro && myMacroName != null) {
         macro = AutismMacroManager.get().get(myMacroName);
         if (macro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: " + myMacroName);
            return;
         }

         macro.regenerateAllPackets();
      }

      AutismLANSync.ExecutionMethod method = this.executionMethod;
      this.activeSyncCtx = new AutismLANSync.SyncContext(executionId, isMacro, myMacroName, command, macro, true, method, -1L);
      this.clientsReady.clear();
      this.executionTimestamps.clear();
      this.ackReceiptTimes.clear();
      this.tickExecutionReports.clear();
      this.locallyExecutedIds.clear();
      this.scheduledTickExecution = null;
      this.peerStepProgress.clear();
      this.clientsReady.add(this.myUsername);
      this.setSyncState(AutismLANSync.SyncState.PREPARING);
      autismclient.AutismClientAddon.LOG.info("[Sync] Initiating GO-sync: execId={}", executionId);
      String assignmentStr = "";
      if (macroAssignments != null && !macroAssignments.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (Entry<String, String> entry : macroAssignments.entrySet()) {
            if (sb.length() > 0) {
               sb.append('\t');
            }

            sb.append(entry.getKey()).append('\t').append(entry.getValue());
         }

         assignmentStr = sb.toString();
      }

      LanPacket.PrepareExecutionPacket preparePacket = new LanPacket.PrepareExecutionPacket(
         this.sessionId, executionId, isMacro, macroName != null ? macroName : "", command, this.myUsername
      );
      preparePacket.macroAssignments = assignmentStr;
      preparePacket.executionMethod = method.ordinal();
      preparePacket.targetTick = -1L;
      this.sendTcpPacket(preparePacket);
      this.broadcastSyncState(AutismLANSync.SyncState.PREPARING, this.clientsReady.size() + "/" + this.connectedClients.size() + " ready");
      new Thread(() -> {
         Thread.currentThread().setPriority(10);

         try {
            long waitStart = System.currentTimeMillis();
            int expectedClients = this.connectedClients.size();

            while (this.clientsReady.size() < expectedClients) {
               if (System.currentTimeMillis() - waitStart > 3000L) {
                  autismclient.AutismClientAddon.LOG.warn("[Sync] Timeout waiting for clients. Got {}/{}", this.clientsReady.size(), expectedClients);
                  break;
               }

               LockSupport.parkNanos(500000L);
            }

            this.setSyncState(AutismLANSync.SyncState.ALL_READY);
            this.broadcastSyncState(AutismLANSync.SyncState.ALL_READY, this.clientsReady.size() + "/" + expectedClients + " ready");
            autismclient.AutismClientAddon.LOG.info("[Sync] All {} clients ready!", this.clientsReady.size());
            this.setSyncState(AutismLANSync.SyncState.DISPATCHING_GO);
            long targetTick = -1L;
            if (method == AutismLANSync.ExecutionMethod.TICK) {
               long currentTick = this.getCurrentGameTick();
               if (currentTick < 0L) {
                  AutismClientMessaging.sendPrefixed("§cCannot tick-sync without a loaded world.");
                  this.setSyncState(AutismLANSync.SyncState.IDLE);
                  return;
               }

               targetTick = currentTick + 10L;
               AutismLANSync.SyncContext ctx = this.activeSyncCtx;
               if (ctx != null && ctx.executionId == executionId) {
                  ctx.targetTick = targetTick;
               }
            }

            byte[] goPayload = this.serializePacket(new LanPacket.GoPacket(this.sessionId, executionId, method.ordinal(), targetTick));
            this.setSyncState(AutismLANSync.SyncState.EXECUTING);

            for (Entry<String, Socket> entryx : this.tcpClients.entrySet()) {
               Socket clientSocket = entryx.getValue();
               String clientName = entryx.getKey();

               try {
                  if (!clientSocket.isClosed()) {
                     Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                     synchronized (lock) {
                        OutputStream out = clientSocket.getOutputStream();
                        out.write(goPayload);
                        out.flush();
                     }
                  }
               } catch (IOException var20) {
                  autismclient.AutismClientAddon.LOG.warn("[Sync] Failed to send GO to {}", clientName);
               }
            }

            if (method == AutismLANSync.ExecutionMethod.TICK) {
               this.scheduleTickExecution(executionId, targetTick);
            } else {
               int hostOffset = this.getPlayerDelayOffset(this.myUsername);
               if (hostOffset > 0) {
                  try {
                     Thread.sleep(hostOffset);
                  } catch (InterruptedException var18) {
                  }
               }

               this.executeAction();
            }
         } catch (Exception var21) {
            autismclient.AutismClientAddon.LOG.error("[Sync] Error in GO-sync: ", var21);
            this.setSyncState(AutismLANSync.SyncState.IDLE);
         }
      }, "Sync-GO-Initiator").start();
   }

   private void initiateGoSyncForQueue() {
      long executionId = System.nanoTime();
      AutismLANSync.ExecutionMethod method = this.executionMethod;
      this.activeSyncCtx = new AutismLANSync.SyncContext(executionId, false, null, "__QUEUE_FLUSH__", null, true, method, -1L);
      this.clientsReady.clear();
      this.executionTimestamps.clear();
      this.ackReceiptTimes.clear();
      this.tickExecutionReports.clear();
      this.locallyExecutedIds.clear();
      this.scheduledTickExecution = null;
      this.clientsReady.add(this.myUsername);
      this.setSyncState(AutismLANSync.SyncState.PREPARING);
      autismclient.AutismClientAddon.LOG.info("[Sync] Initiating queue flush GO-sync: execId={}", executionId);
      LanPacket.PrepareExecutionPacket preparePacket = new LanPacket.PrepareExecutionPacket(
         this.sessionId, executionId, false, "", "__QUEUE_FLUSH__", this.myUsername
      );
      preparePacket.executionMethod = method.ordinal();
      preparePacket.targetTick = -1L;
      this.sendTcpPacket(preparePacket);
      this.broadcastSyncState(AutismLANSync.SyncState.PREPARING, this.clientsReady.size() + "/" + this.connectedClients.size() + " ready");
      new Thread(() -> {
         Thread.currentThread().setPriority(10);

         try {
            long waitStart = System.currentTimeMillis();
            int expectedClients = this.connectedClients.size();

            while (this.clientsReady.size() < expectedClients) {
               if (System.currentTimeMillis() - waitStart > 3000L) {
                  autismclient.AutismClientAddon.LOG.warn("[Sync] Timeout waiting for clients. Got {}/{}", this.clientsReady.size(), expectedClients);
                  break;
               }

               LockSupport.parkNanos(500000L);
            }

            this.setSyncState(AutismLANSync.SyncState.ALL_READY);
            this.broadcastSyncState(AutismLANSync.SyncState.ALL_READY, this.clientsReady.size() + "/" + expectedClients + " ready");
            this.setSyncState(AutismLANSync.SyncState.DISPATCHING_GO);
            long targetTick = -1L;
            if (method == AutismLANSync.ExecutionMethod.TICK) {
               long currentTick = this.getCurrentGameTick();
               if (currentTick < 0L) {
                  AutismClientMessaging.sendPrefixed("§cCannot tick-sync without a loaded world.");
                  this.setSyncState(AutismLANSync.SyncState.IDLE);
                  return;
               }

               targetTick = currentTick + 10L;
               AutismLANSync.SyncContext ctx = this.activeSyncCtx;
               if (ctx != null && ctx.executionId == executionId) {
                  ctx.targetTick = targetTick;
               }
            }

            byte[] goPayload = this.serializePacket(new LanPacket.GoPacket(this.sessionId, executionId, method.ordinal(), targetTick));
            this.setSyncState(AutismLANSync.SyncState.EXECUTING);

            for (Entry<String, Socket> entry : this.tcpClients.entrySet()) {
               Socket clientSocket = entry.getValue();
               String clientName = entry.getKey();

               try {
                  if (!clientSocket.isClosed()) {
                     Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                     synchronized (lock) {
                        OutputStream out = clientSocket.getOutputStream();
                        out.write(goPayload);
                        out.flush();
                     }
                  }
               } catch (IOException var20) {
                  autismclient.AutismClientAddon.LOG.warn("[Sync] Failed to send GO to {}", clientName);
               }
            }

            if (method == AutismLANSync.ExecutionMethod.TICK) {
               this.scheduleTickExecution(executionId, targetTick);
            } else {
               int hostOffset = this.getPlayerDelayOffset(this.myUsername);
               if (hostOffset > 0) {
                  try {
                     Thread.sleep(hostOffset);
                  } catch (InterruptedException var18) {
                  }
               }

               this.executeAction();
            }
         } catch (Exception var21) {
            autismclient.AutismClientAddon.LOG.error("[Sync] Error in queue GO-sync: ", var21);
            this.setSyncState(AutismLANSync.SyncState.IDLE);
         }
      }, "Sync-GO-QueueFlush").start();
   }

   private void handlePrepare(
      long executionId, boolean isMacro, String macroName, String command, String senderUsername, String macroAssignments, int methodOrdinal, long targetTick
   ) {
      if (senderUsername == null || !senderUsername.equals(this.myUsername)) {
         autismclient.AutismClientAddon.LOG.info("[Sync] Received PREPARE from {}", senderUsername);
         this.peerStepProgress.clear();
         this.tickExecutionReports.clear();
         this.locallyExecutedIds.clear();
         this.scheduledTickExecution = null;
         String myMacroName = macroName;
         if (macroAssignments != null && !macroAssignments.isEmpty()) {
            String[] parts = macroAssignments.split("\t");

            for (int pi = 0; pi + 1 < parts.length; pi += 2) {
               if (parts[pi].equals(this.myUsername)) {
                  myMacroName = parts[pi + 1];
                  break;
               }
            }
         }

         AutismMacro macro = null;
         if (isMacro && myMacroName != null && !myMacroName.isEmpty()) {
            macro = AutismMacroManager.get().get(myMacroName);
            if (macro != null) {
               macro.regenerateAllPackets();
            }
         } else if ("__QUEUE_FLUSH__".equals(command)) {
            AutismSharedState.get().regenerateQueue();
         }

         AutismLANSync.ExecutionMethod method = this.executionMethodFromOrdinal(methodOrdinal);
         this.executionMethod = method;
         this.activeSyncCtx = new AutismLANSync.SyncContext(executionId, isMacro, myMacroName, command, macro, false, method, targetTick);
         this.setSyncState(AutismLANSync.SyncState.PREPARING);
         this.sendTcpPacket(new LanPacket.ClientReadyPacket(this.sessionId, executionId, this.myUsername));
         autismclient.AutismClientAddon.LOG.info("[Sync] Sent READY signal");
      }
   }

   private void handleClientReady(long executionId, String username) {
      AutismLANSync.SyncContext ctx = this.activeSyncCtx;
      if (ctx != null && executionId == ctx.executionId) {
         this.clientsReady.add(username);
         this.broadcastSyncState(AutismLANSync.SyncState.PREPARING, this.clientsReady.size() + "/" + this.connectedClients.size() + " ready");
         autismclient.AutismClientAddon.LOG.info("[Sync] Client ready: {} (total: {})", username, this.clientsReady.size());
      }
   }

   private void handleGo(long executionId, int methodOrdinal, long targetTick) {
      AutismLANSync.SyncContext ctx = this.activeSyncCtx;
      if (ctx == null || executionId != ctx.executionId) {
         autismclient.AutismClientAddon.LOG.warn("[Sync] GO for wrong execId, ignoring");
      } else if (!ctx.isInitiator) {
         AutismLANSync.ExecutionMethod method = this.executionMethodFromOrdinal(methodOrdinal);
         ctx.targetTick = targetTick;
         this.executionMethod = method;
         if (method == AutismLANSync.ExecutionMethod.TICK && targetTick >= 0L) {
            autismclient.AutismClientAddon.LOG.info("[Sync] Received GO! Scheduling tick execution for {}", targetTick);
            this.setSyncState(AutismLANSync.SyncState.EXECUTING);
            this.scheduleTickExecution(executionId, targetTick);
         } else {
            int offset = this.getPlayerDelayOffset(this.myUsername);
            if (offset > 0) {
               autismclient.AutismClientAddon.LOG.info("[Sync] Received GO! Applying {}ms offset before executing.", offset);
               this.setSyncState(AutismLANSync.SyncState.EXECUTING);

               try {
                  Thread.sleep(offset);
               } catch (InterruptedException var10) {
               }

               this.executeAction();
            } else {
               autismclient.AutismClientAddon.LOG.info("[Sync] Received GO! Executing immediately.");
               this.setSyncState(AutismLANSync.SyncState.EXECUTING);
               this.executeAction();
            }
         }
      }
   }

   private void executeAction() {
      if (!PackHideState.isHardLocked()) {
         AutismLANSync.SyncContext ctx = this.activeSyncCtx;
         if (ctx != null) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isSameThread()) {
               mc.execute(this::executeAction);
            } else if (this.locallyExecutedIds.add(ctx.executionId)) {
               long executedAtMs = System.currentTimeMillis();
               long actualTick = this.getCurrentGameTick();
               boolean tickMode = ctx.executionMethod == AutismLANSync.ExecutionMethod.TICK;
               boolean late = tickMode && (actualTick < 0L || ctx.targetTick >= 0L && actualTick > ctx.targetTick);
               if ("__QUEUE_FLUSH__".equals(ctx.command)) {
                  ClientPacketListener nh = mc.getConnection();
                  if (nh != null) {
                     int count = AutismSharedState.get().flushDelayedPackets(nh);
                     autismclient.AutismClientAddon.LOG.info("[Sync] Queue flushed! {} packets sent", count);
                  }
               } else if (ctx.command != null && ctx.command.startsWith("__DELAY_PACKETS__\t")) {
                  boolean enabled = "1".equals(ctx.command.substring("__DELAY_PACKETS__\t".length()));
                  AutismModule module = AutismModule.get();
                  int flushed = module.applyDelayGuiPacketsUiBehavior(enabled);
                  module.notifyDelayPacketsUiResult(enabled, flushed);
                  autismclient.AutismClientAddon.LOG.info("[Sync] Delay packets set to {} (flushed={})", enabled, flushed);
               } else if (ctx.command != null && ctx.command.startsWith("__CHAT__\t")) {
                  String chatMsg = ctx.command.substring("__CHAT__\t".length());
                  ClientPacketListener nh = mc.getConnection();
                  if (nh != null) {
                     if (chatMsg.startsWith("/")) {
                        nh.sendCommand(chatMsg.substring(1));
                     } else {
                        nh.sendChat(chatMsg);
                     }

                     autismclient.AutismClientAddon.LOG.info("[Sync] Chat sent: {}", chatMsg);
                  }
               } else if (ctx.isMacro && ctx.macro != null) {
                  ctx.macro.execute(false);
               }

               autismclient.AutismClientAddon.LOG.info("[Sync] EXECUTED at {} tick {}", executedAtMs, actualTick);
               this.executionTimestamps.put(this.myUsername, executedAtMs);
               this.ackReceiptTimes.put(this.myUsername, System.nanoTime());
               if (tickMode) {
                  this.tickExecutionReports.put(this.myUsername, new AutismLANSync.TickExecutionReport(actualTick, late));
               }

               this.sendTcpPacket(
                  new LanPacket.TcpCommandAckPacket(
                     this.sessionId, this.myUsername, executedAtMs, ctx.executionMethod.ordinal(), ctx.targetTick, actualTick, late
                  )
               );
               this.setSyncState(AutismLANSync.SyncState.REPORTING);
               if (ctx.isInitiator) {
                  new Thread(() -> {
                     try {
                        int expected = this.connectedClients.size();
                        long start = System.currentTimeMillis();

                        while (this.getReportCount(ctx.executionMethod) < expected && System.currentTimeMillis() - start < 1500L) {
                           LockSupport.parkNanos(1000000L);
                        }

                        if (ctx.executionMethod == AutismLANSync.ExecutionMethod.TICK) {
                           this.calculateAndDisplayTickSpread(ctx.targetTick);
                        } else {
                           this.calculateAndDisplaySpread();
                        }
                     } catch (Exception var5x) {
                        autismclient.AutismClientAddon.LOG.error("[Sync] Error calculating spread", var5x);
                     }
                  }, "Sync-SpreadCalc").start();
               }
            }
         }
      }
   }

   private void handleExecutionTimestamp(String username, long timestampMs, int methodOrdinal, long targetTick, long actualTick, boolean late) {
      this.executionTimestamps.put(username, timestampMs);
      this.ackReceiptTimes.put(username, System.nanoTime());
      AutismLANSync.ExecutionMethod method = this.executionMethodFromOrdinal(methodOrdinal);
      if (method == AutismLANSync.ExecutionMethod.TICK) {
         this.tickExecutionReports.put(username, new AutismLANSync.TickExecutionReport(actualTick, late || targetTick >= 0L && actualTick > targetTick));
      }

      autismclient.AutismClientAddon.LOG
         .info(
            "[Sync] ACK from {}: remoteMs={}, hostReceiptNano={}, tick={}", new Object[]{username, timestampMs, this.ackReceiptTimes.get(username), actualTick}
         );
   }

   private long getCurrentGameTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return -1L;
      } else {
         if (mc.isSameThread()) {
            this.observedGameTick = mc.level.getGameTime();
         }

         return this.observedGameTick >= 0L ? this.observedGameTick : mc.level.getGameTime();
      }
   }

   private void scheduleTickExecution(long executionId, long targetTick) {
      AutismLANSync.SyncContext ctx = this.activeSyncCtx;
      if (ctx != null && ctx.executionId == executionId) {
         ctx.targetTick = targetTick;
         long currentTick = this.getCurrentGameTick();
         if (currentTick >= 0L && currentTick < targetTick) {
            AutismLANSync.ScheduledTickExecution sched = new AutismLANSync.ScheduledTickExecution(executionId, targetTick);
            if (ServerTickTracker.isReady()) {
               long ticksOut = targetTick - currentTick;
               long now = System.nanoTime();
               long nextTickNano = ServerTickTracker.getNextServerTickNanos();
               long targetBoundaryNano = nextTickNano + Math.max(0L, ticksOut - 1L) * 50000000L;
               int pingMs = ServerTickTracker.getPingMs();
               long halfPingNano = pingMs * 1000000L / 2L;
               long fireAt = targetBoundaryNano - halfPingNano;
               if (fireAt <= now) {
                  fireAt = now;
               }

               sched.targetWallNanos = fireAt;
               long fireAtFinal = fireAt;
               Thread t = new Thread(() -> {
                  try {
                     Thread.currentThread().setPriority(10);
                  } catch (Throwable var11x) {
                  }

                  long parkUntil = fireAtFinal - 1000000L;

                  long nowNano;
                  while ((nowNano = System.nanoTime()) < parkUntil) {
                     LockSupport.parkNanos(parkUntil - nowNano);
                  }

                  while (System.nanoTime() < fireAtFinal) {
                  }

                  AutismLANSync.SyncContext sc = this.activeSyncCtx;
                  if (sc != null && sc.executionId == executionId && sc.executionMethod == AutismLANSync.ExecutionMethod.TICK) {
                     AutismLANSync.ScheduledTickExecution s = this.scheduledTickExecution;
                     if (s != null && s.executionId == executionId) {
                        this.scheduledTickExecution = null;
                        this.setSyncState(AutismLANSync.SyncState.EXECUTING);
                        this.executeAction();
                     }
                  }
               }, "Sync-TickPrecise-" + executionId);
               sched.preciseScheduler = t;
               t.setDaemon(true);
               t.start();
            }

            this.scheduledTickExecution = sched;
         } else {
            this.scheduledTickExecution = null;
            this.executeAction();
         }
      }
   }

   public void onLevelTick(long currentTick) {
      this.observedGameTick = currentTick;
      AutismLANSync.ScheduledTickExecution scheduled = this.scheduledTickExecution;
      if (scheduled != null && currentTick >= scheduled.targetTick) {
         AutismLANSync.SyncContext ctx = this.activeSyncCtx;
         if (ctx != null && ctx.executionId == scheduled.executionId && ctx.executionMethod == AutismLANSync.ExecutionMethod.TICK) {
            ctx.targetTick = scheduled.targetTick;
            this.scheduledTickExecution = null;
            this.setSyncState(AutismLANSync.SyncState.EXECUTING);
            this.executeAction();
         } else {
            this.scheduledTickExecution = null;
         }
      }
   }

   private int getReportCount(AutismLANSync.ExecutionMethod method) {
      return method == AutismLANSync.ExecutionMethod.TICK ? this.tickExecutionReports.size() : this.ackReceiptTimes.size();
   }

   private AutismLANSync.ExecutionMethod executionMethodFromOrdinal(int ordinal) {
      AutismLANSync.ExecutionMethod[] values = AutismLANSync.ExecutionMethod.values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AutismLANSync.ExecutionMethod.INSTANT;
   }

   private AutismLANSync.ExecutionMethod executionMethodFromName(String name) {
      if (name == null) {
         return AutismLANSync.ExecutionMethod.INSTANT;
      } else {
         try {
            return AutismLANSync.ExecutionMethod.valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var3) {
            return AutismLANSync.ExecutionMethod.INSTANT;
         }
      }
   }

   private void calculateAndDisplaySpread() {
      Map<String, Long> timings = this.ackReceiptTimes.isEmpty() ? this.executionTimestamps : this.ackReceiptTimes;
      if (timings.size() < 2) {
         this.setSyncState(AutismLANSync.SyncState.DONE);
      } else {
         long earliest = Long.MAX_VALUE;
         long latest = Long.MIN_VALUE;
         String earliestClient = "";
         String latestClient = "";

         for (Entry<String, Long> entry : timings.entrySet()) {
            long ts = entry.getValue();
            if (ts < earliest) {
               earliest = ts;
               earliestClient = entry.getKey();
            }

            if (ts > latest) {
               latest = ts;
               latestClient = entry.getKey();
            }
         }

         boolean isNanos = !this.ackReceiptTimes.isEmpty();
         long spreadNanos = latest - earliest;
         long totalSpreadMs = isNanos ? spreadNanos / 1000000L : latest - earliest;
         this.lastSpreadResult = new AutismLANSync.SpreadResult(isNanos ? spreadNanos : totalSpreadMs * 1000000L, latestClient, timings.size());
         this.spreadHistory.add(this.lastSpreadResult);

         while (this.spreadHistory.size() > 10) {
            this.spreadHistory.remove(0);
         }

         String report;
         if (totalSpreadMs == 0L) {
            report = "§aPerfect sync!";
         } else {
            report = "§e" + latestClient + " §7was §c+" + totalSpreadMs + "ms §7late";
         }

         AutismClientMessaging.sendPrefixed(report);
         this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, "__SYNC__", report));
         this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, "__SPREAD_DATA__", spreadNanos + "\t" + latestClient + "\t" + timings.size()));
         this.broadcastSyncState(AutismLANSync.SyncState.DONE, totalSpreadMs + "ms spread");
         this.setSyncState(AutismLANSync.SyncState.DONE);
         this.fireCallback(this.onSpreadCalculated);
         new Thread(() -> {
            try {
               Thread.sleep(3000L);
            } catch (InterruptedException var2x) {
            }

            if (this.syncState == AutismLANSync.SyncState.DONE) {
               this.setSyncState(AutismLANSync.SyncState.IDLE);
            }
         }, "Sync-ResetIdle").start();
      }
   }

   private void calculateAndDisplayTickSpread(long targetTick) {
      int expected = this.connectedClients.size();
      TreeSet<String> peers = new TreeSet<>(this.connectedClients.keySet());
      boolean perfect = targetTick >= 0L && this.tickExecutionReports.size() >= expected;
      int maxOffset = 0;
      List<String> offPeers = new ArrayList<>();

      for (String peer : peers) {
         AutismLANSync.TickExecutionReport report = this.tickExecutionReports.get(peer);
         if (report == null) {
            perfect = false;
            offPeers.add(peer + " no report");
         } else {
            long deltaLong = targetTick >= 0L ? report.actualTick - targetTick : 0L;
            int delta = (int)Math.max(-2147483648L, Math.min(2147483647L, deltaLong));
            maxOffset = Math.max(maxOffset, Math.abs(delta));
            if (delta != 0 || report.late) {
               perfect = false;
               String direction = delta >= 0 ? "+" : "";
               String timing = delta >= 0 ? "late" : "early";
               offPeers.add(peer + " was " + direction + delta + " tick(s) " + timing + " on tick " + report.actualTick + ", target " + targetTick);
            }
         }
      }

      String report;
      if (perfect) {
         report = "§aPerfect sync on server tick " + targetTick;
      } else {
         report = "§eTick sync target " + targetTick + ": §c" + String.join("; ", offPeers);
      }

      this.lastSpreadResult = new AutismLANSync.SpreadResult(
         maxOffset * 50000000L,
         perfect ? "" : "tick",
         Math.max(this.tickExecutionReports.size(), expected),
         AutismLANSync.ExecutionMethod.TICK,
         targetTick,
         maxOffset,
         perfect,
         report
      );
      this.spreadHistory.add(this.lastSpreadResult);

      while (this.spreadHistory.size() > 10) {
         this.spreadHistory.remove(0);
      }

      AutismClientMessaging.sendPrefixed(report);
      this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, "__SYNC__", report));
      this.sendTcpPacket(
         new LanPacket.ChatMessagePacket(
            this.sessionId,
            "__TICK_SPREAD_DATA__",
            targetTick + "\t" + maxOffset + "\t" + Math.max(this.tickExecutionReports.size(), expected) + "\t" + perfect + "\t" + report
         )
      );
      this.broadcastSyncState(AutismLANSync.SyncState.DONE, perfect ? "tick perfect" : maxOffset + " tick offset");
      this.setSyncState(AutismLANSync.SyncState.DONE);
      this.fireCallback(this.onSpreadCalculated);
      new Thread(() -> {
         try {
            Thread.sleep(3000L);
         } catch (InterruptedException var2) {
         }

         if (this.syncState == AutismLANSync.SyncState.DONE) {
            this.setSyncState(AutismLANSync.SyncState.IDLE);
         }
      }, "Sync-ResetIdle").start();
   }

   public void sendQueuedPackets() {
      if (this.sessionId == null) {
         AutismClientMessaging.sendPrefixed("§cJoin a session first.");
      } else {
         AutismSharedState.get().regenerateQueue();
         if (this.isHost) {
            this.initiateGoSyncForQueue();
         } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(this.sessionId, this.myUsername, false, "", "__QUEUE_FLUSH__");
            request.executionMethod = this.executionMethod.ordinal();
            this.sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync execution from host...");
         }
      }
   }

   public void setDelayPacketsSynchronized(boolean enabled) {
      if (this.sessionId == null) {
         AutismClientMessaging.sendPrefixed("§cJoin a session first.");
      } else {
         String command = "__DELAY_PACKETS__\t" + (enabled ? "1" : "0");
         if (this.isHost) {
            this.initiateGoSync(false, "", command);
         } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(this.sessionId, this.myUsername, false, "", command);
            request.executionMethod = this.executionMethod.ordinal();
            this.sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync delay toggle from host...");
         }
      }
   }

   public void executeMacroSynchronized(String macroName) {
      if (this.sessionId == null) {
         AutismClientMessaging.sendPrefixed("§cJoin a session first.");
      } else {
         if (this.isHost) {
            this.initiateGoSync(true, macroName, "");
         } else {
            LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(this.sessionId, this.myUsername, true, macroName, "");
            request.executionMethod = this.executionMethod.ordinal();
            this.sendTcpPacket(request);
            AutismClientMessaging.sendPrefixed("§eRequested sync execution from host...");
         }
      }
   }

   public void executeMacrosSynchronized(Map<String, String> assignments) {
      if (this.sessionId == null) {
         AutismClientMessaging.sendPrefixed("§cJoin a session first.");
      } else if (assignments != null && !assignments.isEmpty()) {
         String defaultMacro = assignments.values().iterator().next();
         if (this.isHost) {
            this.initiateGoSync(true, defaultMacro, "", assignments);
         } else {
            StringBuilder sb = new StringBuilder();

            for (Entry<String, String> entry : assignments.entrySet()) {
               if (sb.length() > 0) {
                  sb.append('\t');
               }

               sb.append(entry.getKey()).append('\t').append(entry.getValue());
            }

            LanPacket.RequestSyncPacket rsp = new LanPacket.RequestSyncPacket(this.sessionId, this.myUsername, true, defaultMacro, "__PER_USER__\t" + sb);
            rsp.executionMethod = this.executionMethod.ordinal();
            this.sendTcpPacket(rsp);
            AutismClientMessaging.sendPrefixed("§eRequested per-user sync from host...");
         }
      }
   }

   public void startSearching() {
      if (!this.running.get()) {
         AutismClientMessaging.sendPrefixed("§cStart LAN Sync first.");
      } else if (this.isInSession()) {
         AutismClientMessaging.sendPrefixed("§cAlready in session.");
      } else {
         if (this.myUsername.isEmpty()) {
            this.myUsername = this.getUsername();
         }

         this.discoveredSessions.clear();
         this.isSearching = true;
         this.searchTicksRemaining = 400;
         new Thread(() -> {
            while (this.isSearching && this.searchTicksRemaining > 0) {
               try {
                  Socket searchSocket = new Socket();
                  searchSocket.connect(new InetSocketAddress("127.0.0.1", 25568), 500);
                  searchSocket.setTcpNoDelay(true);
                  DataOutputStream out = new DataOutputStream(searchSocket.getOutputStream());
                  DataInputStream in = new DataInputStream(searchSocket.getInputStream());
                  out.writeInt(LanPacketType.SEARCH_REQUEST.getId());
                  out.writeUTF("");
                  out.flush();
                  int typeId = in.readInt();
                  String foundSessionId = in.readUTF();
                  if (typeId == LanPacketType.SESSION.getId()) {
                     LanPacket.SessionPacket session = new LanPacket.SessionPacket();
                     session.setSessionId(foundSessionId);
                     session.read(in);
                     this.discoveredSessions.put(foundSessionId, session.hostName);
                  }

                  searchSocket.close();
                  Thread.sleep(1000L);
               } catch (IOException var8) {
                  try {
                     Thread.sleep(1000L);
                  } catch (InterruptedException var7) {
                  }
               } catch (InterruptedException var9) {
                  break;
               }
            }
         }, "TCP-Search-Loop").start();
      }
   }

   public void cancelSearch() {
      this.isSearching = false;
      this.searchTicksRemaining = 0;
   }

   public void tick() {
      if (this.running.get() || this.sessionId != null) {
         if (this.sessionId != null) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - this.lastHeartbeatSentMs >= 9000L) {
               this.lastHeartbeatSentMs = nowMs;
               this.sendTcpPacket(new LanPacket.HeartbeatPacket(this.sessionId, this.myUsername, nowMs));
               this.checkPeerHealth();
            }
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            long currentTick = mc.level.getGameTime();
            if (this.sessionId != null && currentTick % 40L == 10L) {
               this.checkAndBroadcastMacroListChanges();
            }

            if (this.isSearching && this.searchTicksRemaining > 0) {
               this.searchTicksRemaining--;
               if (!this.discoveredSessions.isEmpty()) {
                  String firstSessionId = this.discoveredSessions.keySet().iterator().next();
                  String hostName = this.discoveredSessions.get(firstSessionId);
                  this.isSearching = false;
                  this.searchTicksRemaining = 0;
                  this.joinSession(firstSessionId);
                  AutismClientMessaging.sendPrefixed("§aJoined session " + firstSessionId + " (Host: " + hostName + ").");
                  return;
               }

               if (this.searchTicksRemaining == 0) {
                  this.isSearching = false;
                  AutismClientMessaging.sendPrefixed("§cNo sessions found.");
               }
            }
         }
      }
   }

   private void checkPeerHealth() {
      long now = System.currentTimeMillis();
      List<String> deadPeers = new ArrayList<>();

      for (Entry<String, Long> entry : this.lastHeartbeatTime.entrySet()) {
         String peer = entry.getKey();
         if (!peer.equals(this.myUsername)) {
            long elapsed = now - entry.getValue();
            if (elapsed > 30000L) {
               deadPeers.add(peer);
            }
         }
      }

      for (String dead : deadPeers) {
         this.connectedClients.remove(dead);
         this.lastHeartbeatTime.remove(dead);
         this.clientMacroLists.remove(dead);
         this.remoteMeteorMacros.remove(dead);
         this.peerStepProgress.remove(dead);
         if (this.isHost) {
            Socket s = this.tcpClients.remove(dead);
            if (s != null) {
               try {
                  s.close();
               } catch (IOException var9) {
               }
            }

            this.tcpClientWriteLocks.remove(dead);
         }

         AutismClientMessaging.sendPrefixed("§e" + dead + " timed out.");
         autismclient.AutismClientAddon.LOG.info("[Heartbeat] Peer timed out: {}", dead);
         this.fireCallback(this.onClientLeft);
         this.fireCallback(this.onPeerStatusChanged);
         MacroConditionRegistry.onLanStepProgress();
      }

      if (this.isHost && !deadPeers.isEmpty()) {
         this.broadcastClientList();
      }
   }

   public AutismLANSync.PeerStatus getPeerStatus(String username) {
      if (username.equals(this.myUsername)) {
         return AutismLANSync.PeerStatus.HEALTHY;
      } else {
         Long lastBeat = this.lastHeartbeatTime.get(username);
         if (lastBeat == null) {
            return AutismLANSync.PeerStatus.UNKNOWN;
         } else {
            long elapsed = System.currentTimeMillis() - lastBeat;
            return elapsed > 15000L ? AutismLANSync.PeerStatus.STALE : AutismLANSync.PeerStatus.HEALTHY;
         }
      }
   }

   private void handlePacket(LanPacket packet) {
      try {
         switch (packet.getType()) {
            case SEARCH_REQUEST:
            case PLAYER_OFFSETS:
            case CLIENT_OFFSET_UPDATE:
            case EXECUTE_NOW:
            default:
               break;
            case SESSION:
               if (packet instanceof LanPacket.SessionPacket p) {
                  String sid = p.getSessionId();
                  String host = p.hostName;
                  if (!host.equals(this.myUsername) && !this.isInSession()) {
                     boolean isNew = !this.discoveredSessions.containsKey(sid);
                     this.discoveredSessions.put(sid, host);
                     if (isNew) {
                        AutismClientMessaging.sendPrefixed("§eFound session " + sid + " (Host: " + host + ").");
                     }
                  }
               }
               break;
            case JOIN:
               if (packet instanceof LanPacket.JoinPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.JoinPacket p = (LanPacket.JoinPacket)packet;
                  String username = p.username;
                  if (username != null && !username.isEmpty() && !username.equals(this.myUsername) && !this.connectedClients.containsKey(username)) {
                     this.connectedClients.put(username, new AutismLANSync.ClientInfo(username, false));
                     this.lastHeartbeatTime.put(username, System.currentTimeMillis());
                     AutismClientMessaging.sendPrefixed("§e" + username + " joined.");
                     this.executionTimestamps.clear();
                     if (this.isHost) {
                        this.broadcastClientList();
                        this.broadcastMacroList();
                     }

                     this.fireCallback(this.onClientJoined);
                     this.fireCallback(this.onPeerStatusChanged);
                  }
               }
               break;
            case CLIENT_LIST:
               if (packet instanceof LanPacket.ClientListPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.ClientListPacket px = (LanPacket.ClientListPacket)packet;
                  if (!this.isHost) {
                     AutismLANSync.ExecutionMethod hostMethod = this.executionMethodFromOrdinal(px.executionMethod);
                     if (this.executionMethod != hostMethod) {
                        this.executionMethod = hostMethod;
                        this.fireCallback(this.onPeerStatusChanged);
                     }
                  }

                  if (!px.clients.isEmpty()) {
                     AutismLANSync.ClientInfo ourInfo = this.connectedClients.get(this.myUsername);
                     this.connectedClients.clear();
                     long now = System.currentTimeMillis();
                     Set<String> listedPeers = new HashSet<>();

                     for (LanPacket.ClientListPacket.ClientEntry entry : px.clients) {
                        this.connectedClients.put(entry.name, new AutismLANSync.ClientInfo(entry.name, entry.isHost));
                        if (!entry.name.equals(this.myUsername)) {
                           this.lastHeartbeatTime.put(entry.name, now);
                           listedPeers.add(entry.name);
                        }
                     }

                     this.lastHeartbeatTime.keySet().retainAll(listedPeers);
                     if (!this.connectedClients.containsKey(this.myUsername) && ourInfo != null) {
                        this.connectedClients.put(this.myUsername, ourInfo);
                     }
                  }
               }
               break;
            case LEAVE:
               if (packet instanceof LanPacket.LeavePacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.LeavePacket px = (LanPacket.LeavePacket)packet;
                  String username = px.username;
                  if (username != null && !username.isEmpty() && !username.equals(this.myUsername)) {
                     AutismLANSync.ClientInfo leavingClient = this.connectedClients.get(username);
                     boolean leavingWasHost = leavingClient != null && leavingClient.isHost;
                     this.connectedClients.remove(username);
                     this.lastHeartbeatTime.remove(username);
                     this.clientMacroLists.remove(username);
                     this.remoteMeteorMacros.remove(username);
                     this.peerStepProgress.remove(username);
                     AutismClientMessaging.sendPrefixed("§e" + username + " left.");
                     if (this.isHost) {
                        Socket s = this.tcpClients.remove(username);
                        if (s != null) {
                           try {
                              s.close();
                           } catch (IOException var9) {
                           }
                        }

                        this.tcpClientWriteLocks.remove(username);
                        this.relayTcpPacketToClients(packet, username);
                        this.broadcastClientList();
                     }

                     if (leavingWasHost && !this.connectedClients.isEmpty()) {
                        String newHostUsername = this.connectedClients.keySet().stream().sorted().findFirst().orElse(null);
                        if (newHostUsername != null && newHostUsername.equals(this.myUsername)) {
                           this.isHost = true;
                           this.connectedClients.put(this.myUsername, new AutismLANSync.ClientInfo(this.myUsername, true));
                           AutismClientMessaging.sendPrefixed("§aYou are now the host!");
                           this.executionTimestamps.clear();
                           this.broadcastClientList();
                        }
                     }

                     this.fireCallback(this.onClientLeft);
                     this.fireCallback(this.onPeerStatusChanged);
                     MacroConditionRegistry.onLanStepProgress();
                  }
               }
               break;
            case REQUEST_CLIENT_LIST:
               if (this.isHost) {
                  this.broadcastClientList();
               }
               break;
            case REQUEST_MACRO:
               this.handleMacroRequest(packet);
               break;
            case MACRO_DATA:
               this.handleMacroData(packet);
               break;
            case MACRO_DATA_CHUNK:
               this.handleMacroDataChunk(packet);
               break;
            case MACRO_DELETE:
               this.handleMacroDelete(packet);
               break;
            case PRESET_DATA:
               this.handlePresetData(packet);
               break;
            case QUEUE_SYNC:
               if (packet instanceof LanPacket.QueueSyncPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.QueueSyncPacket qp = (LanPacket.QueueSyncPacket)packet;
                  this.handleQueueSync(qp.queueData, qp.senderUsername);
               }
               break;
            case CHAT_MESSAGE:
               if (packet instanceof LanPacket.ChatMessagePacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.ChatMessagePacket cp = (LanPacket.ChatMessagePacket)packet;
                  this.handleChatMessage(cp.senderUsername, cp.message);
               }
               break;
            case PREPARE_EXECUTION:
               if (packet instanceof LanPacket.PrepareExecutionPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.PrepareExecutionPacket p = (LanPacket.PrepareExecutionPacket)packet;
                  this.handlePrepare(p.executionId, p.isMacro, p.targetName, p.queueData, p.senderUsername, p.macroAssignments, p.executionMethod, p.targetTick);
               }
               break;
            case CLIENT_READY:
               if (packet instanceof LanPacket.ClientReadyPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.ClientReadyPacket p = (LanPacket.ClientReadyPacket)packet;
                  this.handleClientReady(p.executionId, p.senderUsername);
               }
               break;
            case GO:
               if (packet instanceof LanPacket.GoPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.GoPacket p = (LanPacket.GoPacket)packet;
                  this.handleGo(p.executionId, p.executionMethod, p.targetTick);
               }
               break;
            case TCP_COMMAND_ACK:
               if (packet instanceof LanPacket.TcpCommandAckPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.TcpCommandAckPacket p = (LanPacket.TcpCommandAckPacket)packet;
                  this.handleExecutionTimestamp(p.senderUsername, p.executeNanoTime, p.executionMethod, p.targetTick, p.actualTick, p.late);
               }
               break;
            case HEARTBEAT:
               if (packet instanceof LanPacket.HeartbeatPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.HeartbeatPacket p = (LanPacket.HeartbeatPacket)packet;
                  if (!p.senderUsername.equals(this.myUsername)) {
                     this.lastHeartbeatTime.put(p.senderUsername, System.currentTimeMillis());
                     if (this.isHost) {
                        this.relayTcpPacketToClients(packet, p.senderUsername);
                     }
                  }
               }
               break;
            case REQUEST_SYNC:
               if (packet instanceof LanPacket.RequestSyncPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.RequestSyncPacket p = (LanPacket.RequestSyncPacket)packet;
                  this.handleRequestSync(p.senderUsername, p.isMacro, p.macroName, p.command);
               }
               break;
            case OFFSET_SYNC:
               if (packet instanceof LanPacket.OffsetSyncPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.OffsetSyncPacket p = (LanPacket.OffsetSyncPacket)packet;
                  this.handleOffsetSync(p.senderUsername, p.targetUsername, p.offsetMs);
               }
               break;
            case MACRO_STEP_PROGRESS:
               if (packet instanceof LanPacket.MacroStepProgressPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.MacroStepProgressPacket p = (LanPacket.MacroStepProgressPacket)packet;
                  this.handleStepProgress(p.senderUsername, p.completedStep, p.totalSteps, p.macroName);
               }
               break;
            case SYNC_STATE_UPDATE:
               if (packet instanceof LanPacket.SyncStateUpdatePacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.SyncStateUpdatePacket pxx = (LanPacket.SyncStateUpdatePacket)packet;
                  AutismLANSync.SyncState[] states = AutismLANSync.SyncState.values();
                  if (pxx.stateOrdinal >= 0 && pxx.stateOrdinal < states.length) {
                     AutismLANSync.SyncContext ctx = this.activeSyncCtx;
                     if (ctx == null || !ctx.isInitiator) {
                        this.syncState = states[pxx.stateOrdinal];
                        this.fireCallback(this.onSyncStateChanged);
                     }
                  }
               }
               break;
            case MACRO_ASSIGNMENT_SYNC:
               if (packet instanceof LanPacket.MacroAssignmentSyncPacket && packet.getSessionId().equals(this.sessionId)) {
                  LanPacket.MacroAssignmentSyncPacket p = (LanPacket.MacroAssignmentSyncPacket)packet;
                  if (!p.senderUsername.equals(this.myUsername)) {
                     this.handleAssignmentSync(p.senderUsername, p.assignments);
                  }
               }
         }
      } catch (Exception var10) {
         autismclient.AutismClientAddon.LOG.error("[Autism-LAN] Error handling packet: {}", packet.getType(), var10);
      }
   }

   private void startTcpServer() {
      try {
         this.tcpServer = new ServerSocket();
         this.tcpServer.setReuseAddress(true);
         this.tcpServer.bind(new InetSocketAddress(25568));
         this.tcpAcceptThread = new Thread(() -> {
            while (this.running.get() && this.isHost && this.tcpServer != null && !this.tcpServer.isClosed()) {
               try {
                  Socket clientSocket = this.tcpServer.accept();
                  clientSocket.setTcpNoDelay(true);
                  this.handleNewTcpClient(clientSocket);
               } catch (IOException var2x) {
                  if (this.running.get() && this.isHost) {
                     autismclient.AutismClientAddon.LOG.warn("[TCP] Accept error: {}", var2x.getMessage());
                  }
               }
            }
         }, "TCP-Accept");
         this.tcpAcceptThread.setDaemon(true);
         this.tcpAcceptThread.start();
         autismclient.AutismClientAddon.LOG.info("[TCP] Server started on port {}", 25568);
      } catch (IOException var2) {
         autismclient.AutismClientAddon.LOG.error("[TCP] Failed to start server: {}", var2.getMessage());
      }
   }

   private void handleNewTcpClient(Socket clientSocket) {
      new Thread(() -> {
         try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            int packetType = in.readInt();
            String packetSession = in.readUTF();
            if (packetType == LanPacketType.SEARCH_REQUEST.getId()) {
               autismclient.AutismClientAddon.LOG.info("[TCP] Received search request, responding with session info");
               out.writeInt(LanPacketType.SESSION.getId());
               out.writeUTF(this.sessionId);
               new LanPacket.SessionPacket(this.sessionId, 0L, this.myUsername).write(out);
               out.flush();
               clientSocket.close();
               return;
            }

            if (packetType != LanPacketType.JOIN.getId()) {
               return;
            }

            LanPacket.JoinPacket joinPacket = new LanPacket.JoinPacket();
            joinPacket.setSessionId(packetSession);
            joinPacket.read(in);
            String clientUsername = joinPacket.username;
            if (clientUsername == null || clientUsername.isBlank()) {
               autismclient.AutismClientAddon.LOG.warn("[TCP] Rejected client with empty username");
               clientSocket.close();
               return;
            }

            if (this.sessionId != null && this.sessionId.equals(packetSession)) {
               Socket oldSocket = this.tcpClients.put(clientUsername, clientSocket);
               if (oldSocket != null && oldSocket != clientSocket) {
                  try {
                     oldSocket.close();
                  } catch (IOException var27) {
                  }
               }

               this.tcpClientWriteLocks.put(clientUsername, new Object());
               this.lastHeartbeatTime.put(clientUsername, System.currentTimeMillis());
               if (!clientUsername.equals(this.myUsername) && !this.connectedClients.containsKey(clientUsername)) {
                  this.connectedClients.put(clientUsername, new AutismLANSync.ClientInfo(clientUsername, false));
                  AutismClientMessaging.sendPrefixed("§e" + clientUsername + " joined.");
                  this.executionTimestamps.clear();
                  this.broadcastClientList();
                  this.sendMacroListToClient(clientSocket, clientUsername);
                  this.fireCallback(this.onClientJoined);
                  this.fireCallback(this.onPeerStatusChanged);
               }

               this.sendClientListToClient(clientSocket, clientUsername);
               autismclient.AutismClientAddon.LOG.info("[TCP] Client joined: {}", clientUsername);

               while (this.running.get() && !clientSocket.isClosed()) {
                  int nextPacketType = in.readInt();
                  String nextPacketSession = in.readUTF();
                  LanPacketType type = LanPacketType.fromId(nextPacketType);
                  if (type != null) {
                     LanPacket packet = LanPacket.create(type, nextPacketSession);
                     if (packet != null) {
                        packet.read(in);
                        this.handlePacket(packet);
                     }
                  }
               }

               return;
            }

            autismclient.AutismClientAddon.LOG.warn("[TCP] Rejected client '{}' with wrong/missing session code", clientUsername);
            clientSocket.close();
         } catch (IOException var28) {
            return;
         } finally {
            try {
               clientSocket.close();
            } catch (IOException var26) {
            }

            if (this.running.get() && this.isHost) {
               String disconnectedUser = null;

               for (Entry<String, Socket> entry : this.tcpClients.entrySet()) {
                  if (entry.getValue() == clientSocket) {
                     disconnectedUser = entry.getKey();
                     break;
                  }
               }

               if (disconnectedUser != null) {
                  this.tcpClients.remove(disconnectedUser);
                  this.tcpClientWriteLocks.remove(disconnectedUser);
                  this.connectedClients.remove(disconnectedUser);
                  this.clientMacroLists.remove(disconnectedUser);
                  this.remoteMeteorMacros.remove(disconnectedUser);
                  this.lastHeartbeatTime.remove(disconnectedUser);
                  this.peerStepProgress.remove(disconnectedUser);
                  AutismClientMessaging.sendPrefixed("§e" + disconnectedUser + " disconnected.");
                  autismclient.AutismClientAddon.LOG.info("[TCP] Client disconnected: {}", disconnectedUser);
                  this.broadcastClientList();
                  this.fireCallback(this.onClientLeft);
                  MacroConditionRegistry.onLanStepProgress();
               }
            }
         }
      }, "TCP-Client-Handler").start();
   }

   private void sendClientListToClient(Socket clientSocket, String clientUsername) {
      try {
         List<LanPacket.ClientListPacket.ClientEntry> entries = new ArrayList<>();

         for (Entry<String, AutismLANSync.ClientInfo> entry : this.connectedClients.entrySet()) {
            entries.add(new LanPacket.ClientListPacket.ClientEntry(entry.getKey(), entry.getValue().isHost));
         }

         LanPacket.ClientListPacket packet = new LanPacket.ClientListPacket(this.sessionId, entries, this.executionMethod.ordinal());
         byte[] data = this.serializePacket(packet);
         Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientUsername, k -> new Object());
         synchronized (lock) {
            clientSocket.getOutputStream().write(data);
            clientSocket.getOutputStream().flush();
         }
      } catch (IOException var10) {
         autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to send client list: {}", var10.getMessage());
      }
   }

   private void sendMacroListToClient(Socket clientSocket, String clientUsername) {
      try {
         List<AutismMacro> localMacros = AutismMacroManager.get().getAll();
         Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientUsername, k -> new Object());

         for (AutismMacro macro : localMacros) {
            this.writeMacroDataPacketsToStream(clientSocket.getOutputStream(), lock, this.myUsername, macro, (byte)0);
         }
      } catch (IOException var7) {
         autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to send macro list: {}", var7.getMessage());
      }
   }

   private void connectToTcpServer(String hostIp) {
      new Thread(() -> {
         try {
            this.tcpHostSocket = new Socket(hostIp, 25568);
            this.tcpHostSocket.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(this.tcpHostSocket.getOutputStream());
            LanPacket.JoinPacket joinPacket = new LanPacket.JoinPacket(this.sessionId, this.myUsername);
            synchronized (this.tcpHostWriteLock) {
               out.writeInt(joinPacket.getType().getId());
               out.writeUTF(joinPacket.getSessionId());
               joinPacket.write(out);
               out.flush();
            }

            autismclient.AutismClientAddon.LOG.info("[TCP] Connected to host at {}:{}", hostIp, 25568);
            this.reconnecting = false;
            this.tcpReadThread = new Thread(() -> {
               try {
                  DataInputStream in = new DataInputStream(this.tcpHostSocket.getInputStream());

                  while (this.running.get() && this.tcpHostSocket != null && !this.tcpHostSocket.isClosed()) {
                     int packetType = in.readInt();
                     String packetSession = in.readUTF();
                     LanPacketType type = LanPacketType.fromId(packetType);
                     if (type != null) {
                        LanPacket packet = LanPacket.create(type, packetSession);
                        if (packet != null) {
                           packet.read(in);
                           this.handlePacket(packet);
                        }
                     }
                  }
               } catch (IOException var6) {
                  autismclient.AutismClientAddon.LOG.warn("[TCP] Disconnected from host");
                  this.attemptReconnect();
               }
            }, "TCP-Reader");
            this.tcpReadThread.setDaemon(true);
            this.tcpReadThread.start();
            this.sendTcpPacket(new LanPacket.RequestClientListPacket(this.sessionId));
            this.sendMacroListViaConnection(out);
         } catch (IOException var7) {
            autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to connect to host: {}", var7.getMessage());
            this.attemptReconnect();
         }
      }, "TCP-Connect").start();
   }

   private void attemptReconnect() {
      if (!this.reconnecting && this.running.get() && this.lastHostIp != null && this.lastSessionId != null) {
         if (!this.isHost) {
            this.reconnecting = true;
            AutismClientMessaging.sendPrefixed("§eConnection lost. Reconnecting...");
            new Thread(() -> {
               long backoffMs = 1000L;

               for (int attempt = 0; this.reconnecting && this.running.get() && this.lastHostIp != null; backoffMs = Math.min(backoffMs * 2L, 16000L)) {
                  try {
                     Thread.sleep(backoffMs);
                  } catch (InterruptedException var5) {
                     return;
                  }

                  if (!this.reconnecting || !this.running.get() || this.lastHostIp == null) {
                     return;
                  }

                  autismclient.AutismClientAddon.LOG.info("[TCP] Reconnect attempt {}...", ++attempt);

                  try {
                     Socket testSocket = new Socket();
                     testSocket.connect(new InetSocketAddress(this.lastHostIp, 25568), 2000);
                     testSocket.setTcpNoDelay(true);
                     testSocket.close();
                     this.sessionId = this.lastSessionId;
                     if (this.myUsername.isEmpty()) {
                        this.myUsername = this.getUsername();
                     }

                     this.connectedClients.putIfAbsent(this.myUsername, new AutismLANSync.ClientInfo(this.myUsername, false));
                     this.connectToTcpServer(this.lastHostIp);
                     AutismClientMessaging.sendPrefixed("§aReconnected to session!");
                     return;
                  } catch (IOException var6) {
                     autismclient.AutismClientAddon.LOG.warn("[TCP] Reconnect attempt {} failed", attempt);
                  }
               }

               this.reconnecting = false;
            }, "TCP-Reconnect").start();
         }
      }
   }

   private void sendMacroListViaConnection(DataOutputStream out) {
      try {
         List<AutismMacro> localMacros = AutismMacroManager.get().getAll();
         synchronized (this.tcpHostWriteLock) {
            for (AutismMacro macro : localMacros) {
               this.writeMacroDataPacketsToStream(out, null, this.myUsername, macro, (byte)0);
            }
         }
      } catch (IOException var8) {
         autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to send macro list: {}", var8.getMessage());
      }
   }

   private byte[] serializePacket(LanPacket packet) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);
      out.writeInt(packet.getType().getId());
      out.writeUTF(packet.getSessionId());
      packet.write(out);
      return baos.toByteArray();
   }

   private void writeMacroDataPacketsToStream(OutputStream out, Object lock, String senderUsername, AutismMacro macro, byte sourceType) throws IOException {
      if (macro != null) {
         String nbtString = macro.toShareableTag().toString();

         for (LanPacket packet : this.createMacroDataPackets(senderUsername, macro.name, nbtString, sourceType)) {
            byte[] data = this.serializePacket(packet);
            if (lock != null) {
               synchronized (lock) {
                  out.write(data);
                  out.flush();
               }
            } else {
               out.write(data);
               out.flush();
            }
         }
      }
   }

   private void sendTcpPacket(LanPacket packet) {
      if (!PackHideState.isHardLocked()) {
         try {
            byte[] data = this.serializePacket(packet);
            if (this.isHost) {
               for (Entry<String, Socket> entry : this.tcpClients.entrySet()) {
                  Socket client = entry.getValue();
                  String clientName = entry.getKey();

                  try {
                     if (!client.isClosed()) {
                        Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                        synchronized (lock) {
                           client.getOutputStream().write(data);
                           client.getOutputStream().flush();
                        }
                     }
                  } catch (IOException var13) {
                  }
               }
            } else {
               synchronized (this.tcpHostWriteLock) {
                  if (this.tcpHostSocket != null && !this.tcpHostSocket.isClosed()) {
                     this.tcpHostSocket.getOutputStream().write(data);
                     this.tcpHostSocket.getOutputStream().flush();
                  }
               }
            }
         } catch (IOException var14) {
            autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to send packet: {}", var14.getMessage());
         }
      }
   }

   private void relayTcpPacketToClients(LanPacket packet, String excludedUsername) {
      if (!PackHideState.isHardLocked()) {
         if (this.isHost) {
            try {
               byte[] data = this.serializePacket(packet);

               for (Entry<String, Socket> entry : this.tcpClients.entrySet()) {
                  String clientName = entry.getKey();
                  if (excludedUsername == null || !excludedUsername.equals(clientName)) {
                     Socket client = entry.getValue();

                     try {
                        if (!client.isClosed()) {
                           Object lock = this.tcpClientWriteLocks.computeIfAbsent(clientName, k -> new Object());
                           synchronized (lock) {
                              client.getOutputStream().write(data);
                              client.getOutputStream().flush();
                           }
                        }
                     } catch (IOException var12) {
                     }
                  }
               }
            } catch (IOException var13) {
               autismclient.AutismClientAddon.LOG.warn("[TCP] Failed to relay packet: {}", var13.getMessage());
            }
         }
      }
   }

   private void stopTcp() {
      try {
         if (this.tcpServer != null) {
            try {
               this.tcpServer.close();
            } catch (IOException ignored) {
            }
            this.tcpServer = null;
         }

         if (this.tcpHostSocket != null) {
            try {
               this.tcpHostSocket.close();
            } catch (IOException ignored) {
            }
            this.tcpHostSocket = null;
         }

         for (Socket s : this.tcpClients.values()) {
            try {
               s.close();
            } catch (IOException var4) {
            }
         }
      } finally {
         this.tcpClients.clear();
         this.tcpClientWriteLocks.clear();
         this.pendingMacroTransfers.clear();
      }
   }

   private void broadcastClientList() {
      if (this.isHost) {
         List<LanPacket.ClientListPacket.ClientEntry> entries = new ArrayList<>();

         for (Entry<String, AutismLANSync.ClientInfo> entry : this.connectedClients.entrySet()) {
            entries.add(new LanPacket.ClientListPacket.ClientEntry(entry.getKey(), entry.getValue().isHost));
         }

         this.sendTcpPacket(new LanPacket.ClientListPacket(this.sessionId, entries, this.executionMethod.ordinal()));
      }
   }

   private void broadcastSyncState(AutismLANSync.SyncState state, String detail) {
      if (this.isHost) {
         this.sendTcpPacket(new LanPacket.SyncStateUpdatePacket(this.sessionId, state.ordinal(), detail));
      }
   }

   private void checkAndBroadcastMacroListChanges() {
      try {
         Map<String, String> currentMeteorMacros = this.snapshotMeteorMacros();
         boolean changed = currentMeteorMacros.size() != this.lastBroadcastMeteorMacros.size();
         if (!changed) {
            for (Entry<String, String> entry : currentMeteorMacros.entrySet()) {
               String lastValue = this.lastBroadcastMeteorMacros.get(entry.getKey());
               if (!entry.getValue().equals(lastValue)) {
                  changed = true;
                  break;
               }
            }
         }

         if (changed) {
            for (String previousName : new ArrayList<>(this.lastBroadcastMeteorMacros.keySet())) {
               if (!currentMeteorMacros.containsKey(previousName)) {
                  this.broadcastMacroDeletion(previousName);
               }
            }

            this.lastBroadcastMeteorMacros = new ConcurrentHashMap<>(currentMeteorMacros);
            this.broadcastMacroList();
         }
      } catch (Exception var6) {
         autismclient.AutismClientAddon.LOG.error("[Autism-LAN] Failed to check macro list changes", var6);
      }
   }

   public void broadcastMacroList() {
      if (this.sessionId != null) {
         for (AutismMacro macro : AutismMacroManager.get().getAll()) {
            this.sendMacroData(this.myUsername, macro, (byte)0);
         }

         for (AutismMacro macro : MeteorMacroAdapter.getMeteorMacros()) {
            this.sendMacroData(this.myUsername, macro, (byte)1);
         }

         this.lastBroadcastMeteorMacros = new ConcurrentHashMap<>(this.snapshotMeteorMacros());
      }
   }

   private void sendMacroData(String senderUsername, AutismMacro macro, byte sourceType) {
      if (macro != null && this.sessionId != null) {
         String nbtString = macro.toShareableTag().toString();

         for (LanPacket packet : this.createMacroDataPackets(senderUsername, macro.name, nbtString, sourceType)) {
            this.sendTcpPacket(packet);
         }
      }
   }

   private List<LanPacket> createMacroDataPackets(String senderUsername, String macroName, String nbtData, byte sourceType) {
      String safeSender = senderUsername != null ? senderUsername : "";
      String safeName = macroName != null ? macroName : "";
      String safeNbt = nbtData != null ? nbtData : "";
      List<LanPacket> packets = new ArrayList<>();
      if (modifiedUtfLength(safeNbt) <= 60000) {
         packets.add(new LanPacket.MacroDataPacket(this.sessionId, safeSender, safeName, safeNbt, sourceType));
         return packets;
      } else {
         byte[] bytes = safeNbt.getBytes(StandardCharsets.UTF_8);
         int totalChunks = Math.max(1, (bytes.length + 32000 - 1) / 32000);

         for (int i = 0; i < totalChunks; i++) {
            int start = i * 32000;
            int len = Math.min(32000, bytes.length - start);
            byte[] chunk = new byte[len];
            System.arraycopy(bytes, start, chunk, 0, len);
            packets.add(new LanPacket.MacroDataChunkPacket(this.sessionId, safeSender, safeName, sourceType, i, totalChunks, chunk));
         }

         return packets;
      }
   }

   private static int modifiedUtfLength(String value) {
      if (value == null) {
         return 0;
      } else {
         int length = 0;

         for (int i = 0; i < value.length(); i++) {
            int c = value.charAt(i);
            if (c >= 1 && c <= 127) {
               length++;
            } else if (c <= 2047) {
               length += 2;
            } else {
               length += 3;
            }
         }

         return length;
      }
   }

   private Map<String, String> snapshotMeteorMacros() {
      Map<String, String> snapshot = new LinkedHashMap<>();

      for (AutismMacro macro : MeteorMacroAdapter.getMeteorMacros()) {
         if (macro != null && macro.name != null && !macro.name.isBlank()) {
            snapshot.put(macro.name, macro.toShareableTag().toString());
         }
      }

      return snapshot;
   }

   private void handleMacroData(LanPacket packet) {
      this.handleMacroData(packet, true);
   }

   private void handleMacroData(LanPacket packet, boolean relayIfHost) {
      if (packet instanceof LanPacket.MacroDataPacket && packet.getSessionId().equals(this.sessionId)) {
         LanPacket.MacroDataPacket p = (LanPacket.MacroDataPacket)packet;
         String sender = p.senderUsername;
         if (sender == null || !sender.equals(this.myUsername)) {
            try {
               CompoundTag nbt = TagParser.parseCompoundFully(p.nbtData);
               AutismMacro macro = new AutismMacro();
               macro.fromTag(nbt);
               macro.sanitizeForSharing();
               String safeNbtData = macro.toTag().toString();
               if (p.sourceType == 1) {
                  this.remoteMeteorMacros.putIfAbsent(sender, new ConcurrentHashMap<>());
                  this.remoteMeteorMacros.get(sender).put(macro.name, macro);
               } else {
                  this.clientMacroLists.putIfAbsent(sender, new ConcurrentHashMap<>());
                  this.clientMacroLists.get(sender).put(macro.name, macro);
               }

               if (p.sourceType == 2) {
                  this.importSharedMacroIfMissing(macro, sender);
               }

               if (relayIfHost && this.isHost && sender != null && !sender.isBlank()) {
                  this.relayTcpPacketToClients(new LanPacket.MacroDataPacket(this.sessionId, sender, macro.name, safeNbtData, p.sourceType), sender);
               }
            } catch (Exception var8) {
               autismclient.AutismClientAddon.LOG.error("[Autism-LAN] Failed to deserialize macro", var8);
            }
         }
      }
   }

   private void handleMacroDataChunk(LanPacket packet) {
      if (packet instanceof LanPacket.MacroDataChunkPacket && packet.getSessionId().equals(this.sessionId)) {
         LanPacket.MacroDataChunkPacket p = (LanPacket.MacroDataChunkPacket)packet;
         String sender = p.senderUsername;
         if (sender == null || !sender.equals(this.myUsername)) {
            if (p.totalChunks > 0 && p.totalChunks <= 4096 && p.chunkIndex >= 0 && p.chunkIndex < p.totalChunks) {
               if (p.chunkData != null && p.chunkData.length != 0 && p.chunkData.length <= 32000) {
                  if (this.isHost && sender != null && !sender.isBlank()) {
                     this.relayTcpPacketToClients(
                        new LanPacket.MacroDataChunkPacket(this.sessionId, sender, p.macroName, p.sourceType, p.chunkIndex, p.totalChunks, p.chunkData), sender
                     );
                  }

                  this.cleanupStaleMacroTransfers();
                  String key = sender + "\u0000" + p.macroName + "\u0000" + p.sourceType + "\u0000" + p.totalChunks;
                  AutismLANSync.PendingMacroTransfer transfer = this.pendingMacroTransfers
                     .computeIfAbsent(key, ignored -> new AutismLANSync.PendingMacroTransfer(p.totalChunks));
                  byte[] complete = transfer.addChunk(p.chunkIndex, p.chunkData);
                  if (complete != null) {
                     this.pendingMacroTransfers.remove(key);
                     String nbtData = new String(complete, StandardCharsets.UTF_8);
                     this.handleMacroData(new LanPacket.MacroDataPacket(this.sessionId, sender, p.macroName, nbtData, p.sourceType), false);
                  }
               }
            }
         }
      }
   }

   private void cleanupStaleMacroTransfers() {
      long now = System.currentTimeMillis();
      this.pendingMacroTransfers.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs > 120000L);
   }

   private void importSharedMacroIfMissing(AutismMacro macro, String sender) {
      if (macro != null && macro.name != null && !macro.name.isBlank()) {
         macro.sanitizeForSharing();
         AutismMacroManager manager = AutismMacroManager.get();
         AutismMacro existing = manager.get(macro.name);
         if (existing != null) {
            AutismMacro fresh = macro.deepCopy(existing.name);
            existing.actions.clear();
            existing.actions.addAll(fresh.actions);
            existing.loop = fresh.loop;
            existing.loopCount = fresh.loopCount;
            manager.save();
            AutismClientMessaging.sendPrefixed("§aUpdated macro from " + sender + ": " + existing.name);
         } else {
            AutismMacro installed = macro.deepCopy(macro.name);
            manager.add(installed);
            AutismClientMessaging.sendPrefixed("§aReceived macro from " + sender + ": " + installed.name);
         }
      }
   }

   private void handleMacroRequest(LanPacket packet) {
      if (packet instanceof LanPacket.RequestMacroPacket && packet.getSessionId().equals(this.sessionId)) {
         LanPacket.RequestMacroPacket p = (LanPacket.RequestMacroPacket)packet;
         String requestedName = p.macroName == null ? "" : p.macroName.trim();
         if (!requestedName.isEmpty()) {
            AutismMacro macro = AutismMacroManager.get().get(requestedName);
            if (macro != null) {
               this.sendMacroData(this.myUsername, macro, (byte)2);
            } else {
               for (AutismMacro meteorMacro : MeteorMacroAdapter.getMeteorMacros()) {
                  if (meteorMacro != null && meteorMacro.name != null && meteorMacro.name.equalsIgnoreCase(requestedName)) {
                     this.sendMacroData(this.myUsername, meteorMacro, (byte)2);
                     return;
                  }
               }
            }
         }
      }
   }

   private void handlePresetData(LanPacket packet) {
      if (packet instanceof LanPacket.PresetDataPacket && packet.getSessionId().equals(this.sessionId)) {
         LanPacket.PresetDataPacket p = (LanPacket.PresetDataPacket)packet;
         if (p.senderUsername == null || !p.senderUsername.equals(this.myUsername)) {
            if (AutismPresetManager.get().importSharedPreset(p.presetName, p.presetJson, p.senderUsername)
               && this.isHost
               && p.senderUsername != null
               && !p.senderUsername.isBlank()) {
               this.relayTcpPacketToClients(new LanPacket.PresetDataPacket(this.sessionId, p.senderUsername, p.presetName, p.presetJson), p.senderUsername);
            }
         }
      }
   }

   private void handleMacroDelete(LanPacket packet) {
      if (packet instanceof LanPacket.MacroDeletePacket && packet.getSessionId().equals(this.sessionId)) {
         LanPacket.MacroDeletePacket p = (LanPacket.MacroDeletePacket)packet;
         if (p.senderUsername == null || !p.senderUsername.equals(this.myUsername)) {
            if (this.clientMacroLists.containsKey(p.senderUsername)) {
               this.clientMacroLists.get(p.senderUsername).remove(p.macroName);
            }

            if (this.remoteMeteorMacros.containsKey(p.senderUsername)) {
               this.remoteMeteorMacros.get(p.senderUsername).remove(p.macroName);
            }

            if (this.isHost && p.senderUsername != null && !p.senderUsername.isBlank()) {
               this.relayTcpPacketToClients(new LanPacket.MacroDeletePacket(this.sessionId, p.senderUsername, p.macroName), p.senderUsername);
            }
         }
      }
   }

   private void handleQueueSync(String queueData, String senderUsername) {
      try {
         if (senderUsername != null && senderUsername.equals(this.myUsername)) {
            return;
         }

         AutismSharedState.QueuedPacket.resetIdCounter();
         List<AutismSharedState.QueuedPacket> receivedQueue = AutismClipboardHelper.deserializeQueueFromBase64(queueData);
         if (receivedQueue == null || receivedQueue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("§cReceived empty queue");
            return;
         }

         AutismSharedState.get().setDelayedPackets(receivedQueue);
         AutismClientMessaging.sendPrefixed("§aReceived queue: " + receivedQueue.size() + " packets");
      } catch (Exception var4) {
         autismclient.AutismClientAddon.LOG.error("[Autism-LAN] Failed to process queue sync", var4);
         AutismClientMessaging.sendPrefixed("§cFailed to receive queue");
      }
   }

   private void handleChatMessage(String senderUsername, String message) {
      String safeSender = senderUsername != null && !senderUsername.isBlank() ? senderUsername : "Peer";
      String safeMessage = message == null ? "" : message.trim();
      if (!safeMessage.isEmpty()) {
         if ("__SYNC__".equals(safeSender)) {
            AutismClientMessaging.sendPrefixed(safeMessage);
         } else if ("__STOP_ALL__".equals(safeSender)) {
            MacroExecutor.stop();
            AutismClientMessaging.sendPrefixed("§c[LAN] Macros stopped by " + safeMessage);
         } else if ("__SPREAD_DATA__".equals(safeSender)) {
            try {
               String[] parts = safeMessage.split("\t");
               if (parts.length >= 3) {
                  long nanos = Long.parseLong(parts[0]);
                  String lateClient = parts[1];
                  int count = Integer.parseInt(parts[2]);
                  this.lastSpreadResult = new AutismLANSync.SpreadResult(nanos, lateClient, count);
                  this.spreadHistory.add(this.lastSpreadResult);

                  while (this.spreadHistory.size() > 10) {
                     this.spreadHistory.remove(0);
                  }

                  this.fireCallback(this.onSpreadCalculated);
               }
            } catch (NumberFormatException var12) {
            }
         } else if ("__TICK_SPREAD_DATA__".equals(safeSender)) {
            try {
               String[] parts = safeMessage.split("\t", 5);
               if (parts.length >= 5) {
                  long targetTick = Long.parseLong(parts[0]);
                  int maxOffset = Integer.parseInt(parts[1]);
                  int count = Integer.parseInt(parts[2]);
                  boolean perfect = Boolean.parseBoolean(parts[3]);
                  String tickReport = parts[4];
                  this.lastSpreadResult = new AutismLANSync.SpreadResult(
                     maxOffset * 50000000L, perfect ? "" : "tick", count, AutismLANSync.ExecutionMethod.TICK, targetTick, maxOffset, perfect, tickReport
                  );
                  this.spreadHistory.add(this.lastSpreadResult);

                  while (this.spreadHistory.size() > 10) {
                     this.spreadHistory.remove(0);
                  }

                  this.fireCallback(this.onSpreadCalculated);
               }
            } catch (NumberFormatException var13) {
            }
         } else {
            boolean isExecMethodControl = "__EXEC_METHOD__".equals(safeSender) || safeMessage != null && safeMessage.startsWith("__EXEC_METHOD__:");
            if (!isExecMethodControl) {
               if (!safeSender.equals(this.myUsername)) {
                  AutismClientMessaging.send("[LAN] <" + safeSender + "> " + safeMessage);
               }
            } else {
               String originator;
               String modeName;
               if (safeMessage != null && safeMessage.startsWith("__EXEC_METHOD__:")) {
                  originator = "__EXEC_METHOD__".equals(safeSender) ? "host" : safeSender;
                  modeName = safeMessage.substring("__EXEC_METHOD__:".length());
               } else {
                  originator = "host";
                  modeName = safeMessage;
               }

               AutismLANSync.ExecutionMethod method = this.executionMethodFromName(modeName);
               if (this.isHost) {
                  if (this.syncState != AutismLANSync.SyncState.IDLE && this.syncState != AutismLANSync.SyncState.DONE) {
                     this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, this.myUsername, "__EXEC_METHOD__:" + this.executionMethod.name()));
                     return;
                  }

                  this.executionMethod = method;
                  this.broadcastClientList();
                  this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, originator, "__EXEC_METHOD__:" + this.executionMethod.name()));
                  if (!originator.equals(this.myUsername)) {
                     AutismClientMessaging.sendPrefixed("§eExecution method set to " + this.executionMethod.name() + " by " + originator);
                  }

                  this.fireCallback(this.onPeerStatusChanged);
               } else {
                  this.executionMethod = method;
                  if (!originator.equals(this.myUsername)) {
                     AutismClientMessaging.sendPrefixed("§eExecution method set to " + this.executionMethod.name() + " by " + originator);
                  }

                  this.fireCallback(this.onPeerStatusChanged);
               }
            }
         }
      }
   }

   private void handleRequestSync(String senderUsername, boolean isMacro, String macroName, String command) {
      if (senderUsername != null && !senderUsername.equals(this.myUsername)) {
         if (!this.isHost) {
            autismclient.AutismClientAddon.LOG.warn("[Sync] Non-host received REQUEST_SYNC from {} - ignoring", senderUsername);
         } else {
            autismclient.AutismClientAddon.LOG
               .info("[Sync] Received sync request from {}: isMacro={}, name={}, cmd={}", new Object[]{senderUsername, isMacro, macroName, command});
            if ("__QUEUE_FLUSH__".equals(command)) {
               this.initiateGoSyncForQueue();
            } else if (command != null && command.startsWith("__DELAY_PACKETS__\t")) {
               this.initiateGoSync(false, "", command);
            } else if (command != null && command.startsWith("__PER_USER__\t")) {
               String assignStr = command.substring("__PER_USER__\t".length());
               String[] parts = assignStr.split("\t");
               Map<String, String> assignments = new LinkedHashMap<>();

               for (int pi = 0; pi + 1 < parts.length; pi += 2) {
                  assignments.put(parts[pi], parts[pi + 1]);
               }

               this.initiateGoSync(isMacro, macroName, "", assignments);
            } else {
               this.initiateGoSync(isMacro, macroName, command);
            }
         }
      }
   }

   private void handleStepProgress(String senderUsername, int completedStep, int totalSteps, String macroName) {
      if (senderUsername != null && !senderUsername.equals(this.myUsername)) {
         if (this.isHost) {
            this.sendTcpPacket(new LanPacket.MacroStepProgressPacket(this.sessionId, senderUsername, completedStep, totalSteps, macroName));
         }

         if (completedStep < 0) {
            this.peerStepProgress.remove(senderUsername);
         } else {
            this.peerStepProgress.put(senderUsername, completedStep);
         }

         MacroConditionRegistry.onLanStepProgress();
         Runnable listener = this.onStepProgressChanged;
         if (listener != null) {
            listener.run();
         }
      }
   }

   private void handleOffsetSync(String senderUsername, String targetUsername, int offsetMs) {
      if (senderUsername != null && !senderUsername.equals(this.myUsername)) {
         AutismLANSync.ClientInfo info = this.connectedClients.get(targetUsername);
         if (info != null) {
            info.delayOffsetMs = Math.max(0, offsetMs);
            autismclient.AutismClientAddon.LOG.info("[Sync] Offset for {} set to {}ms by {}", new Object[]{targetUsername, offsetMs, senderUsername});
         }

         if (this.isHost) {
            this.sendTcpPacket(new LanPacket.OffsetSyncPacket(this.sessionId, senderUsername, targetUsername, offsetMs));
         }
      }
   }

   public boolean isRunning() {
      return this.running.get();
   }

   public boolean hasTickWork() {
      return this.running.get() && (this.sessionId != null || this.isSearching || this.reconnecting);
   }

   public boolean isInSession() {
      return this.sessionId != null;
   }

   public AutismLANSync.ExecutionMethod getExecutionMethod() {
      return this.executionMethod;
   }

   public int getTickExecutionDelayTicks() {
      return 10;
   }

   public void setExecutionMethod(AutismLANSync.ExecutionMethod method) {
      AutismLANSync.ExecutionMethod next = method != null ? method : AutismLANSync.ExecutionMethod.INSTANT;
      if (this.syncState != AutismLANSync.SyncState.IDLE && this.syncState != AutismLANSync.SyncState.DONE) {
         AutismClientMessaging.sendPrefixed("§cCannot change execution method while LAN Sync is active.");
      } else {
         if (this.sessionId != null && !this.isHost) {
            this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, this.myUsername, "__EXEC_METHOD__:" + next.name()));
            AutismClientMessaging.sendPrefixed("§eRequested execution method: " + next.name());
         } else {
            AutismLANSync.ExecutionMethod previous = this.executionMethod;
            this.executionMethod = next;
            if (this.sessionId != null) {
               this.broadcastClientList();
               this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, this.myUsername, "__EXEC_METHOD__:" + next.name()));
            }

            if (previous != next) {
               AutismClientMessaging.sendPrefixed("§eExecution method set to " + next.name());
            }

            this.fireCallback(this.onPeerStatusChanged);
         }
      }
   }

   public void cycleExecutionMethod() {
      this.setExecutionMethod(
         this.executionMethod == AutismLANSync.ExecutionMethod.INSTANT ? AutismLANSync.ExecutionMethod.TICK : AutismLANSync.ExecutionMethod.INSTANT
      );
   }

   public void onGameJoined() {
      if (this.sessionId != null) {
         if (this.isHost) {
            this.broadcastClientList();
         } else {
            this.sendTcpPacket(new LanPacket.RequestClientListPacket(this.sessionId));
         }
      }
   }

   public boolean isHost() {
      return this.isHost;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public int getConnectedCount() {
      return this.connectedClients.size();
   }

   public boolean isSearching() {
      return this.isSearching;
   }

   public int getSearchSecondsRemaining() {
      return this.searchTicksRemaining / 20;
   }

   public boolean isReconnecting() {
      return this.reconnecting;
   }

   public AutismLANSync.SyncState getSyncState() {
      return this.syncState;
   }

   public AutismLANSync.SyncContext getActiveSyncContext() {
      return this.activeSyncCtx;
   }

   public AutismLANSync.SpreadResult getLastSpreadResult() {
      return this.lastSpreadResult;
   }

   public List<AutismLANSync.SpreadResult> getSpreadHistory() {
      return new ArrayList<>(this.spreadHistory);
   }

   public String getMyUsername() {
      return this.myUsername;
   }

   public Map<String, AutismLANSync.ClientInfo> getConnectedClients() {
      return new ConcurrentHashMap<>(this.connectedClients);
   }

   public void broadcastMacroDeletion(String macroName) {
      if (this.sessionId != null) {
         this.sendTcpPacket(new LanPacket.MacroDeletePacket(this.sessionId, this.myUsername, macroName));
      }
   }

   public void broadcastQueueSync(String queueData) {
      if (this.isInSession()) {
         this.sendTcpPacket(new LanPacket.QueueSyncPacket(this.sessionId, queueData, this.myUsername));
      }
   }

   public void broadcastStopAll() {
      if (this.isInSession()) {
         MacroExecutor.stop();
         AutismClientMessaging.sendPrefixed("§c[LAN] Macros stopped by " + this.myUsername);
         this.sendTcpPacket(new LanPacket.ChatMessagePacket(this.sessionId, "__STOP_ALL__", this.myUsername));
      }
   }

   public boolean sendChatMessage(String message) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else {
         String safeMessage = message == null ? "" : message.trim();
         if (safeMessage.isEmpty()) {
            return false;
         } else if (!this.isInSession()) {
            AutismClientMessaging.sendPrefixed("Join a LAN Sync session first.");
            return false;
         } else {
            String command = "__CHAT__\t" + safeMessage;
            if (this.isHost) {
               this.initiateGoSync(false, null, command);
            } else {
               LanPacket.RequestSyncPacket request = new LanPacket.RequestSyncPacket(this.sessionId, this.myUsername, false, "", command);
               request.executionMethod = this.executionMethod.ordinal();
               this.sendTcpPacket(request);
               AutismClientMessaging.sendPrefixed("§eRequested sync chat from host...");
            }

            return true;
         }
      }
   }

   public int getConnectedClientCount() {
      return this.connectedClients.size();
   }

   public void broadcastStepProgress(int completedStep, int totalSteps, String macroName) {
      if (this.sessionId != null) {
         if (completedStep < 0) {
            this.peerStepProgress.remove(this.myUsername);
         } else {
            this.peerStepProgress.put(this.myUsername, completedStep);
         }

         this.sendTcpPacket(
            new LanPacket.MacroStepProgressPacket(this.sessionId, this.myUsername, completedStep, totalSteps, macroName != null ? macroName : "")
         );
         MacroConditionRegistry.onLanStepProgress();
      }
   }

   public int getPeerStep(String username) {
      return this.peerStepProgress.getOrDefault(username, 0);
   }

   public Map<String, Integer> getAllPeerSteps() {
      return new ConcurrentHashMap<>(this.peerStepProgress);
   }

   public void clearStepProgress() {
      this.peerStepProgress.clear();
   }

   public void setOnStepProgressChanged(Runnable callback) {
      this.onStepProgressChanged = callback;
   }

   public Map<String, Map<String, AutismMacro>> getAllRemoteMacros() {
      return new HashMap<>(this.clientMacroLists);
   }

   public Map<String, Map<String, AutismMacro>> getAllRemoteMeteorMacros() {
      return new HashMap<>(this.remoteMeteorMacros);
   }

   public List<String> getPeersMissingMacro(String macroName) {
      List<String> missing = new ArrayList<>();
      if (macroName != null && !macroName.isBlank()) {
         Map<String, Map<String, AutismMacro>> remote = this.getAllRemoteMacros();

         for (String peer : new TreeSet<>(this.connectedClients.keySet())) {
            if (!peer.equals(this.myUsername)) {
               Map<String, AutismMacro> peerMacros = remote.get(peer);
               if (peerMacros == null || !peerMacros.containsKey(macroName)) {
                  missing.add(peer);
               }
            }
         }

         return missing;
      } else {
         return missing;
      }
   }

   public Map<String, String> getMissingAssignedMacros(Map<String, String> assignments) {
      Map<String, String> missing = new LinkedHashMap<>();
      if (assignments != null && !assignments.isEmpty()) {
         Map<String, Map<String, AutismMacro>> remote = this.getAllRemoteMacros();

         for (Entry<String, String> entry : new LinkedHashMap<>(assignments).entrySet()) {
            String username = entry.getKey();
            String macroName = entry.getValue();
            if (username != null && !username.isBlank() && macroName != null && !macroName.isBlank()) {
               if (username.equals(this.myUsername)) {
                  if (AutismMacroManager.get().get(macroName) == null) {
                     missing.put(username, macroName);
                  }
               } else {
                  Map<String, AutismMacro> peerMacros = remote.get(username);
                  if (peerMacros == null || !peerMacros.containsKey(macroName)) {
                     missing.put(username, macroName);
                  }
               }
            }
         }

         return missing;
      } else {
         return missing;
      }
   }

   public List<String> getRemoteMacros() {
      Set<String> remoteMacros = new HashSet<>();
      Set<String> ownMacros = new HashSet<>();
      ownMacros.addAll(AutismCompatManager.getMeteorMacroNames());

      for (Entry<String, Map<String, AutismMacro>> entry : this.clientMacroLists.entrySet()) {
         if (!entry.getKey().equals(this.myUsername)) {
            for (String macroName : entry.getValue().keySet()) {
               if (!ownMacros.contains(macroName)) {
                  remoteMacros.add(macroName);
               }
            }
         }
      }

      return new ArrayList<>(remoteMacros);
   }

   public void importMacro(String clientUsername, String macroName) {
      Map<String, AutismMacro> clientMacros = this.clientMacroLists.get(clientUsername);
      if (clientMacros != null && clientMacros.containsKey(macroName)) {
         AutismMacro remoteMacro = clientMacros.get(macroName);
         String newName = macroName;

         for (int suffix = 1; AutismMacroManager.get().get(newName) != null; suffix++) {
            newName = macroName + " (" + suffix + ")";
         }

         AutismMacro newMacro = new AutismMacro(newName);
         newMacro.actions.addAll(remoteMacro.actions);
         AutismMacroManager.get().add(newMacro);
         AutismMacroManager.get().save();
         AutismClientMessaging.sendPrefixed("§aImported macro: " + newName);
         autismclient.AutismClientAddon.LOG.info("[Autism-LAN] Imported macro '{}' from {}", newName, clientUsername);
      } else {
         AutismClientMessaging.sendPrefixed("§cMacro not found: " + macroName);
      }
   }

   public void broadcastAssignments(Map<String, String> assignments) {
      if (this.sessionId != null && this.isInSession()) {
         this.syncedAssignments.clear();
         this.syncedAssignments.putAll(assignments);
         this.assignmentSetBy = this.myUsername;
         StringBuilder sb = new StringBuilder();

         for (Entry<String, String> entry : assignments.entrySet()) {
            if (sb.length() > 0) {
               sb.append('\t');
            }

            sb.append(entry.getKey()).append('\t').append(entry.getValue());
         }

         this.sendTcpPacket(new LanPacket.MacroAssignmentSyncPacket(this.sessionId, this.myUsername, sb.toString()));
      }
   }

   private void handleAssignmentSync(String sender, String encoded) {
      this.syncedAssignments.clear();
      this.assignmentSetBy = sender;
      if (encoded != null && !encoded.isEmpty()) {
         String[] parts = encoded.split("\t");

         for (int i = 0; i + 1 < parts.length; i += 2) {
            this.syncedAssignments.put(parts[i], parts[i + 1]);
         }
      }

      this.fireCallback(this.onAssignmentsChanged);
   }

   public Map<String, String> getSyncedAssignments() {
      return new LinkedHashMap<>(this.syncedAssignments);
   }

   public String getAssignmentSetBy() {
      return this.assignmentSetBy;
   }

   public void setOnAssignmentsChanged(Runnable callback) {
      this.onAssignmentsChanged = callback;
   }

   public List<String> getRemoteMacroNamesForPeer(String peerUsername) {
      Map<String, AutismMacro> peerMacros = this.clientMacroLists.get(peerUsername);
      return (List<String>)(peerMacros != null && !peerMacros.isEmpty() ? new ArrayList<>(peerMacros.keySet()) : Collections.emptyList());
   }

   public boolean shareMacroWithPeers(String macroName) {
      if (this.sessionId == null || !this.isInSession()) {
         AutismClientMessaging.sendPrefixed("§cJoin a session first.");
         return false;
      } else if (macroName != null && !macroName.isBlank()) {
         AutismMacro macro = AutismMacroManager.get().get(macroName);
         if (macro == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: " + macroName);
            return false;
         } else {
            this.sendMacroData(this.myUsername, macro, (byte)2);
            AutismClientMessaging.sendPrefixed("§aSent macro to peers: " + macro.name);
            return true;
         }
      } else {
         AutismClientMessaging.sendPrefixed("§cSelect a macro first.");
         return false;
      }
   }

   public void sharePreset(String presetName) {
      if (this.sessionId == null) {
         AutismClientMessaging.sendPrefixed("§cNot in session.");
      } else {
         File file = new File(new File(autismclient.AutismClientAddon.FOLDER, "presets"), presetName + ".json");
         if (!file.exists()) {
            AutismClientMessaging.sendPrefixed("§cPreset not found: " + presetName);
         } else {
            try {
               String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
               this.sendTcpPacket(new LanPacket.PresetDataPacket(this.sessionId, this.myUsername, presetName, json));
               AutismClientMessaging.sendPrefixed("§aShared preset '" + presetName + "' with LAN.");
            } catch (IOException var4) {
               AutismClientMessaging.sendPrefixed("§cFailed to read preset: " + var4.getMessage());
            }
         }
      }
   }

   public void requestMacro(String macroName) {
      if (this.sessionId != null && this.isInSession()) {
         this.sendTcpPacket(new LanPacket.RequestMacroPacket(this.sessionId, macroName));
         AutismClientMessaging.sendPrefixed("§eRequesting macro: " + macroName);
      } else {
         AutismClientMessaging.sendPrefixed("§cNot in a LAN Sync session!");
      }
   }

   public void startTwoPhaseExecution(String targetName, boolean isMacro) {
      if (this.isInSession()) {
         if (isMacro) {
            this.executeMacroSynchronized(targetName);
         } else {
            this.sendQueuedPackets();
         }
      }
   }

   private String summarizeNames(List<String> names) {
      if (names != null && !names.isEmpty()) {
         return names.size() <= 3 ? String.join(", ", names) : String.join(", ", names.subList(0, 3)) + " +" + (names.size() - 3) + " more";
      } else {
         return "";
      }
   }

   private String summarizeAssignments(Map<String, String> assignments) {
      if (assignments != null && !assignments.isEmpty()) {
         List<String> parts = new ArrayList<>();

         for (Entry<String, String> entry : assignments.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
            if (parts.size() == 3 && assignments.size() > 3) {
               break;
            }
         }

         if (assignments.size() > 3) {
            parts.add("+" + (assignments.size() - 3) + " more");
         }

         return String.join(", ", parts);
      } else {
         return "";
      }
   }

   public void setOnClientJoined(Runnable callback) {
      this.onClientJoined = callback;
   }

   public void setOnClientLeft(Runnable callback) {
      this.onClientLeft = callback;
   }

   public void setOnCountdown(Runnable callback) {
      this.onCountdown = callback;
   }

   public void setOnSendNow(Runnable callback) {
      this.onSendNow = callback;
   }

   public void setOnSessionStateChanged(Runnable callback) {
      this.onSessionStateChanged = callback;
   }

   public void setOnSyncStateChanged(Runnable callback) {
      this.onSyncStateChanged = callback;
   }

   public void setOnSpreadCalculated(Runnable callback) {
      this.onSpreadCalculated = callback;
   }

   public void setOnPeerStatusChanged(Runnable callback) {
      this.onPeerStatusChanged = callback;
   }

   private void fireCallback(Runnable callback) {
      if (callback != null) {
         Minecraft.getInstance().execute(callback);
      }
   }

   private String generateSessionId() {
      return String.format("%04d", (int)(Math.random() * 10000.0));
   }

   private String getUsername() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null ? mc.player.getName().getString() : "Player";
   }

   public void setPlayerDelayOffset(String username, int offsetMs) {
      AutismLANSync.ClientInfo info = this.connectedClients.get(username);
      if (info != null) {
         int clamped = Math.max(0, offsetMs);
         info.delayOffsetMs = clamped;
         if (this.sessionId != null) {
            this.sendTcpPacket(new LanPacket.OffsetSyncPacket(this.sessionId, this.myUsername, username, clamped));
         }
      }
   }

   public int getPlayerDelayOffset(String username) {
      AutismLANSync.ClientInfo info = this.connectedClients.get(username);
      return info != null ? info.delayOffsetMs : 0;
   }

   public void sendChat(String message) {
      this.sendChatMessage(message);
   }

   public static class ClientInfo {
      public String username;
      public boolean isHost;
      public long lastSeen;
      public int delayOffsetMs;

      ClientInfo(String username, boolean isHost) {
         this.username = username;
         this.isHost = isHost;
         this.lastSeen = System.currentTimeMillis();
         this.delayOffsetMs = 0;
      }
   }

   public static enum ExecutionMethod {
      INSTANT,
      TICK;
   }

   public static enum PeerStatus {
      HEALTHY,
      STALE,
      UNKNOWN;
   }

   private static final class PendingMacroTransfer {
      private final long createdAtMs = System.currentTimeMillis();
      private final byte[][] chunks;
      private int receivedChunks;
      private int totalBytes;

      private PendingMacroTransfer(int totalChunks) {
         this.chunks = new byte[totalChunks][];
      }

      private synchronized byte[] addChunk(int index, byte[] data) {
         if (index >= 0 && index < this.chunks.length && data != null) {
            if (this.chunks[index] == null) {
               this.chunks[index] = data;
               this.receivedChunks++;
               this.totalBytes += data.length;
            }

            if (this.receivedChunks != this.chunks.length) {
               return null;
            } else {
               byte[] complete = new byte[this.totalBytes];
               int offset = 0;

               for (byte[] chunk : this.chunks) {
                  if (chunk == null) {
                     return null;
                  }

                  System.arraycopy(chunk, 0, complete, offset, chunk.length);
                  offset += chunk.length;
               }

               return complete;
            }
         } else {
            return null;
         }
      }
   }

   private static final class ScheduledTickExecution {
      final long executionId;
      final long targetTick;
      volatile long targetWallNanos = 0L;
      volatile Thread preciseScheduler = null;

      ScheduledTickExecution(long executionId, long targetTick) {
         this.executionId = executionId;
         this.targetTick = targetTick;
      }
   }

   public static class SpreadResult {
      public final long spreadNanos;
      public final String lateClient;
      public final int clientCount;
      public final AutismLANSync.ExecutionMethod executionMethod;
      public final long targetTick;
      public final int maxTickOffset;
      public final boolean perfectTickSync;
      public final String tickReport;

      public SpreadResult(long spreadNanos, String lateClient, int clientCount) {
         this(spreadNanos, lateClient, clientCount, AutismLANSync.ExecutionMethod.INSTANT, -1L, 0, false, "");
      }

      public SpreadResult(
         long spreadNanos,
         String lateClient,
         int clientCount,
         AutismLANSync.ExecutionMethod executionMethod,
         long targetTick,
         int maxTickOffset,
         boolean perfectTickSync,
         String tickReport
      ) {
         this.spreadNanos = spreadNanos;
         this.lateClient = lateClient;
         this.clientCount = clientCount;
         this.executionMethod = executionMethod != null ? executionMethod : AutismLANSync.ExecutionMethod.INSTANT;
         this.targetTick = targetTick;
         this.maxTickOffset = maxTickOffset;
         this.perfectTickSync = perfectTickSync;
         this.tickReport = tickReport != null ? tickReport : "";
      }

      public double getSpreadMs() {
         return this.spreadNanos / 1000000.0;
      }
   }

   public static class SyncContext {
      public final long executionId;
      public final boolean isMacro;
      public final String macroName;
      public final String command;
      public final AutismMacro macro;
      public final boolean isInitiator;
      public final AutismLANSync.ExecutionMethod executionMethod;
      public volatile long targetTick;

      public SyncContext(long executionId, boolean isMacro, String macroName, String command, AutismMacro macro, boolean isInitiator) {
         this(executionId, isMacro, macroName, command, macro, isInitiator, AutismLANSync.ExecutionMethod.INSTANT, -1L);
      }

      public SyncContext(
         long executionId,
         boolean isMacro,
         String macroName,
         String command,
         AutismMacro macro,
         boolean isInitiator,
         AutismLANSync.ExecutionMethod executionMethod,
         long targetTick
      ) {
         this.executionId = executionId;
         this.isMacro = isMacro;
         this.macroName = macroName;
         this.command = command;
         this.macro = macro;
         this.isInitiator = isInitiator;
         this.executionMethod = executionMethod != null ? executionMethod : AutismLANSync.ExecutionMethod.INSTANT;
         this.targetTick = targetTick;
      }
   }

   public static enum SyncState {
      IDLE,
      PREPARING,
      ALL_READY,
      DISPATCHING_GO,
      EXECUTING,
      REPORTING,
      DONE;
   }

   private static final class TickExecutionReport {
      final long actualTick;
      final boolean late;

      TickExecutionReport(long actualTick, boolean late) {
         this.actualTick = actualTick;
         this.late = late;
      }
   }
}
