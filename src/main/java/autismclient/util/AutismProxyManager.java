package autismclient.util;

import autismclient.AutismClientAddon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public final class AutismProxyManager implements Iterable<AutismProxy> {
   private static final AutismProxyManager INSTANCE = new AutismProxyManager();
   private final List<AutismProxy> proxies = new ArrayList();
   private final AtomicBoolean refreshing = new AtomicBoolean(false);
   private final AtomicBoolean refreshCancelRequested = new AtomicBoolean(false);
   private final AtomicInteger refreshChecked = new AtomicInteger();
   private final AtomicInteger refreshTotal = new AtomicInteger();
   private final AtomicBoolean importing = new AtomicBoolean(false);
   private final AtomicBoolean importCancelRequested = new AtomicBoolean(false);
   private final AtomicInteger importLinesRead = new AtomicInteger();
   private final AtomicInteger importCandidates = new AtomicInteger();
   private final AtomicInteger importAdded = new AtomicInteger();
   private final AtomicBoolean saveWorkerRunning = new AtomicBoolean(false);
   private final AtomicBoolean geoLookupRunning = new AtomicBoolean(false);
   private final AtomicBoolean geoLookupRequestedAgain = new AtomicBoolean(false);
   private boolean loaded;
   private int timeoutMs = 3000;
   private int threads = 64;
   private int retries = 0;
   private boolean sortByLatency = true;
   private boolean pruneDead = true;
   private int pruneLatency = 2000;
   private int pruneToCount = 0;
   private volatile long refreshGeneration;
   private volatile long refreshRevision;
   private volatile long refreshCancelNoticeUntilMs;
   private volatile long importGeneration;
   private volatile long importRevision;
   private volatile long importCancelNoticeUntilMs;
   private volatile long geoGeneration;
   private volatile long canceledImportGeneration = Long.MIN_VALUE;
   private volatile long listRevision;
   private volatile long lastRefreshRevisionMs;
   private volatile long lastImportRevisionMs;
   private volatile boolean saveRequested;
   private volatile ExecutorService refreshExecutor;
   private volatile Thread importThread;
   private volatile List<AutismProxy> activeRefreshSnapshot = List.of();
   private static final Pattern PROXY_PATTERN = Pattern.compile("^(?:([\\w\\s]+)=)?((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(?i:@(socks[45]))?$", 8);
   private static final Pattern PROXY_PATTERN_WEBSHARE = Pattern.compile("^((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]):([^:]+)(?::(.+))?$", 8);
   private static final Pattern PROXY_PATTERN_URI = Pattern.compile("^(?:(?<type>socks|socks4|socks5)://)?(?:(?<user>[\\w~-]+)(:(?<pass>[\\w~-]+))?@)?(?<addr>(?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(?<port>\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$", 8);
   private static final int GEO_BATCH_SIZE = 100;

   private AutismProxyManager() {
   }

   public static AutismProxyManager get() {
      INSTANCE.ensureLoaded();
      return INSTANCE;
   }

   private File saveFile() {
      return new File(Minecraft.getInstance().gameDirectory, "autism-proxies.nbt");
   }

   private synchronized void ensureLoaded() {
      if (!this.loaded) {
         this.loaded = true;
         File file = this.saveFile();
         if (file.exists()) {
            try {
               CompoundTag tag = NbtIo.read(file.toPath());
               if (tag == null) {
                  return;
               }

               this.timeoutMs = tag.getIntOr("timeoutMs", this.timeoutMs);
               this.threads = tag.getIntOr("threads", this.threads);
               this.retries = tag.getIntOr("retries", this.retries);
               this.sortByLatency = tag.getBooleanOr("sortByLatency", this.sortByLatency);
               this.pruneDead = tag.getBooleanOr("pruneDead", this.pruneDead);
               this.pruneLatency = tag.getIntOr("pruneLatency", this.pruneLatency);
               this.pruneToCount = tag.getIntOr("pruneToCount", this.pruneToCount);
               this.proxies.clear();

               for(Tag element : tag.getListOrEmpty("proxies")) {
                  if (element instanceof CompoundTag) {
                     CompoundTag compoundTag = (CompoundTag)element;
                     this.proxies.add((new AutismProxy()).fromTag(compoundTag));
                  }
               }
            } catch (Exception e) {
               AutismClientAddon.LOG.error("Failed to load Autism proxies", e);
            }

         }
      }
   }

   public void save() {
      try {
         CompoundTag tag = new CompoundTag();
         synchronized(this) {
            tag.putInt("timeoutMs", this.timeoutMs);
            tag.putInt("threads", this.threads);
            tag.putInt("retries", this.retries);
            tag.putBoolean("sortByLatency", this.sortByLatency);
            tag.putBoolean("pruneDead", this.pruneDead);
            tag.putInt("pruneLatency", this.pruneLatency);
            tag.putInt("pruneToCount", this.pruneToCount);
            ListTag list = new ListTag();

            for(AutismProxy proxy : this.proxies) {
               list.add(proxy.toTag());
            }

            tag.put("proxies", list);
         }

         NbtIo.write(tag, this.saveFile().toPath());
      } catch (Exception e) {
         AutismClientAddon.LOG.error("Failed to save Autism proxies", e);
      }

   }

   private void scheduleSave() {
      this.saveRequested = true;
      if (this.saveWorkerRunning.compareAndSet(false, true)) {
         Thread thread = new Thread(() -> {
            try {
               do {
                  this.saveRequested = false;
                  this.save();
               } while (this.saveRequested);
            } finally {
               this.saveWorkerRunning.set(false);
               if (this.saveRequested) {
                  this.scheduleSave();
               }

            }

         }, "Autism-Proxy-Save");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public synchronized List<AutismProxy> all() {
      return new ArrayList(this.proxies);
   }

   public synchronized int size() {
      return this.proxies.size();
   }

   public synchronized int enabledCount() {
      int count = 0;

      for(AutismProxy proxy : this.proxies) {
         if (proxy.enabled && proxy.isValid()) {
            ++count;
         }
      }

      return count;
   }

   public long listRevision() {
      return this.listRevision;
   }

   public synchronized boolean add(AutismProxy proxy) {
      if (proxy != null && proxy.isValid() && !this.proxies.contains(proxy)) {
         if (this.proxies.isEmpty()) {
            proxy.enabled = true;
         }

         this.proxies.add(proxy);
         this.bumpListRevision();
         this.scheduleSave();
         this.requestGeoLookup(false);
         return true;
      } else {
         return false;
      }
   }

   public synchronized void remove(AutismProxy proxy) {
      if (this.proxies.remove(proxy)) {
         this.bumpListRevision();
         this.scheduleSave();
      }

   }

   public synchronized int clearAll() {
      if (!this.refreshing.get() && !this.importing.get() && !this.proxies.isEmpty()) {
         int removed = this.proxies.size();
         this.proxies.clear();
         this.bumpListRevision();
         this.scheduleSave();
         return removed;
      } else {
         return 0;
      }
   }

   public synchronized boolean update(AutismProxy existing, AutismProxy updated) {
      if (existing != null && updated != null && updated.isValid()) {
         int index = -1;

         for(int i = 0; i < this.proxies.size(); ++i) {
            AutismProxy proxy = (AutismProxy)this.proxies.get(i);
            if (proxy == existing) {
               index = i;
               break;
            }
         }

         if (index < 0) {
            return false;
         } else {
            for(int i = 0; i < this.proxies.size(); ++i) {
               if (i != index && ((AutismProxy)this.proxies.get(i)).equals(updated)) {
                  return false;
               }
            }

            boolean identityChanged = existing.type != updated.type || existing.port != updated.port || !Objects.equals(existing.address, updated.address);
            existing.name = updated.name;
            existing.type = updated.type;
            existing.address = updated.address;
            existing.port = updated.port;
            existing.username = updated.username;
            existing.password = updated.password;
            if (identityChanged) {
               existing.status = AutismProxy.Status.UNCHECKED;
               existing.latency = 0L;
               existing.clearGeo();
            }

            this.bumpListRevision();
            this.scheduleSave();
            if (identityChanged) {
               this.requestGeoLookup(false);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public synchronized void setEnabled(AutismProxy proxy, boolean enabled) {
      for(AutismProxy current : this.proxies) {
         current.enabled = false;
      }

      if (proxy != null) {
         proxy.enabled = enabled;
      }

      this.bumpListRevision();
      this.scheduleSave();
   }

   public synchronized AutismProxy getEnabled() {
      for(AutismProxy proxy : this.proxies) {
         if (proxy.enabled && proxy.isValid()) {
            return proxy;
         }
      }

      return null;
   }

   public boolean isRefreshing() {
      return this.refreshing.get();
   }

   public RefreshStatus refreshStatus() {
      boolean running = this.refreshing.get();
      boolean canceling = !running && System.currentTimeMillis() < this.refreshCancelNoticeUntilMs;
      return new RefreshStatus(running, canceling, this.refreshChecked.get(), this.refreshTotal.get(), this.refreshGeneration, this.refreshRevision);
   }

   public ImportStatus importStatus() {
      boolean running = this.importing.get();
      boolean canceling = !running && System.currentTimeMillis() < this.importCancelNoticeUntilMs;
      boolean canceled = !running && this.importGeneration == this.canceledImportGeneration;
      return new ImportStatus(running, canceling, this.importLinesRead.get(), this.importCandidates.get(), this.importAdded.get(), this.importGeneration, this.importRevision, canceled);
   }

   public int getTimeoutMs() {
      return this.timeoutMs;
   }

   public void setTimeoutMs(int v) {
      this.timeoutMs = Math.max(1, v);
      this.scheduleSave();
   }

   public int getThreads() {
      return this.threads;
   }

   public void setThreads(int v) {
      this.threads = Math.max(1, v);
      this.scheduleSave();
   }

   public int getRetries() {
      return this.retries;
   }

   public void setRetries(int v) {
      this.retries = Math.max(0, v);
      this.scheduleSave();
   }

   public boolean isSortByLatency() {
      return this.sortByLatency;
   }

   public synchronized void setSortByLatency(boolean v) {
      this.sortByLatency = v;
      if (v) {
         this.sortByLatencyInternal();
      }

      this.bumpListRevision();
      this.scheduleSave();
   }

   public boolean isPruneDead() {
      return this.pruneDead;
   }

   public void setPruneDead(boolean v) {
      this.pruneDead = v;
      this.scheduleSave();
   }

   public int getPruneLatency() {
      return this.pruneLatency;
   }

   public void setPruneLatency(int v) {
      this.pruneLatency = Math.max(0, v);
      this.scheduleSave();
   }

   public int getPruneToCount() {
      return this.pruneToCount;
   }

   public void setPruneToCount(int v) {
      this.pruneToCount = Math.max(0, v);
      this.scheduleSave();
   }

   public synchronized boolean sortByLatencyNow() {
      if (!this.refreshing.get() && !this.importing.get() && this.proxies.size() >= 2) {
         this.sortByLatencyInternal();
         this.bumpListRevision();
         this.scheduleSave();
         return true;
      } else {
         return false;
      }
   }

   public void checkProxies(boolean all) {
      this.startRefresh(all);
   }

   public boolean requestGeoLookup(boolean force) {
      this.ensureLoaded();
      if (!this.geoLookupRunning.compareAndSet(false, true)) {
         this.geoLookupRequestedAgain.set(true);
         return false;
      } else {
         List<GeoTarget> targets;
         long generation;
         synchronized(this) {
            targets = this.collectGeoTargets(force);
            if (targets.isEmpty()) {
               this.geoLookupRunning.set(false);
               return false;
            }

            generation = this.geoGeneration + 1L;
            this.geoGeneration = generation;
            long now = System.currentTimeMillis();

            for(GeoTarget target : targets) {
               target.proxy().markGeoLookupPending(now);
            }

            this.bumpListRevision();
         }

         Thread thread = new Thread(() -> this.runGeoLookup(generation, targets), "Autism-Proxy-Geo");
         thread.setDaemon(true);
         thread.start();
         return true;
      }
   }

   public boolean startRefresh(boolean all) {
      List<RefreshTarget> targets = new ArrayList();
      List<AutismProxy> active = new ArrayList();
      ExecutorService executor;
      long generation;
      int timeout;
      int retryCount;
      int workerCount;
      synchronized(this) {
         if (this.refreshing.get() || this.importing.get() || this.proxies.isEmpty()) {
            return false;
         }

         for(AutismProxy proxy : this.proxies) {
            if (proxy != null && proxy.isValid() && (all || proxy.status == AutismProxy.Status.UNCHECKED)) {
               targets.add(new RefreshTarget(proxy, proxyKey(proxy)));
               active.add(proxy);
            }
         }

         if (targets.isEmpty()) {
            return false;
         }

         generation = this.refreshGeneration + 1L;
         this.refreshGeneration = generation;
         this.refreshCancelRequested.set(false);
         this.refreshCancelNoticeUntilMs = 0L;
         this.refreshChecked.set(0);
         this.refreshTotal.set(targets.size());
         this.activeRefreshSnapshot = List.copyOf(active);

         for(AutismProxy proxy : active) {
            proxy.status = AutismProxy.Status.CHECKING;
            proxy.latency = 0L;
         }

         timeout = this.effectiveRefreshTimeout(targets.size());
         retryCount = this.effectiveRefreshRetries(targets.size());
         workerCount = this.effectiveRefreshThreads(targets.size());
         executor = Executors.newFixedThreadPool(workerCount);
         this.refreshExecutor = executor;
         this.refreshing.set(true);
         this.bumpRefreshRevision(true);
      }

      Thread thread = new Thread(() -> this.runRefreshJob(generation, executor, targets, timeout, retryCount, workerCount), "Autism-Proxy-Refresh");
      thread.setDaemon(true);
      thread.start();
      return true;
   }

   public boolean cancelRefresh() {
      ExecutorService executor;
      synchronized(this) {
         if (!this.refreshing.get()) {
            return false;
         }

         this.refreshCancelRequested.set(true);
         this.refreshing.set(false);
         this.refreshCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
         executor = this.refreshExecutor;
         this.refreshExecutor = null;
         List<AutismProxy> active = this.activeRefreshSnapshot;
         this.activeRefreshSnapshot = List.of();

         for(AutismProxy proxy : active) {
            if (proxy.status == AutismProxy.Status.CHECKING) {
               proxy.status = AutismProxy.Status.UNCHECKED;
               proxy.latency = 0L;
            }
         }

         if (this.sortByLatency) {
            this.sortByLatencyInternal();
            this.bumpListRevision();
            this.scheduleSave();
         }

         this.bumpRefreshRevision(true);
      }

      if (executor != null) {
         executor.shutdownNow();
      }

      return true;
   }

   public boolean startImport(File file) {
      if (file != null && file.isFile()) {
         long generation;
         synchronized(this) {
            if (this.importing.get() || this.refreshing.get()) {
               return false;
            }

            generation = this.importGeneration + 1L;
            this.importGeneration = generation;
            this.canceledImportGeneration = Long.MIN_VALUE;
            this.importCancelRequested.set(false);
            this.importCancelNoticeUntilMs = 0L;
            this.importLinesRead.set(0);
            this.importCandidates.set(0);
            this.importAdded.set(0);
            this.importing.set(true);
            this.bumpImportRevision(true);
         }

         Thread thread = new Thread(() -> this.runImportJob(generation, file), "Autism-Proxy-Import");
         thread.setDaemon(true);
         this.importThread = thread;
         thread.start();
         return true;
      } else {
         return false;
      }
   }

   public boolean cancelImport() {
      Thread thread;
      synchronized(this) {
         if (!this.importing.get()) {
            return false;
         }

         this.importCancelRequested.set(true);
         this.importing.set(false);
         this.canceledImportGeneration = this.importGeneration;
         this.importCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
         thread = this.importThread;
         this.importThread = null;
         this.bumpImportRevision(true);
      }

      if (thread != null) {
         thread.interrupt();
      }

      return true;
   }

   private void runImportJob(long generation, File file) {
      List<AutismProxy> parsed = new ArrayList();
      Set<String> knownKeys;
      synchronized(this) {
         knownKeys = new HashSet(this.proxies.size() + 1024);

         for(AutismProxy proxy : this.proxies) {
            knownKeys.add(proxyKey(proxy));
         }
      }

      try {
         BufferedReader reader = new BufferedReader(new FileReader(file));

         String line;
         try {
            for(; this.isImportCurrent(generation) && (line = reader.readLine()) != null; this.bumpImportRevision(false)) {
               this.importLinesRead.incrementAndGet();
               AutismProxy proxy = parseProxyLine(line.trim());
               if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) {
                  parsed.add(proxy);
                  this.importCandidates.set(parsed.size());
               }
            }
         } catch (Throwable var17) {
            try {
               reader.close();
            } catch (Throwable var16) {
               var17.addSuppressed(var16);
            }

            throw var17;
         }

         reader.close();
      } catch (Exception e) {
         if (this.isImportCurrent(generation)) {
            AutismClientAddon.LOG.error("Failed to import proxies", e);
         }
      } finally {
         this.finishImport(generation, parsed);
      }

   }

   private synchronized void finishImport(long generation, List<AutismProxy> parsed) {
      if (this.importGeneration == generation && this.importing.get()) {
         int added = 0;
         if (!this.importCancelRequested.get() && parsed != null && !parsed.isEmpty()) {
            Set<String> knownKeys = new HashSet(this.proxies.size() + parsed.size() + 16);

            for(AutismProxy proxy : this.proxies) {
               knownKeys.add(proxyKey(proxy));
            }

            for(AutismProxy proxy : parsed) {
               if (knownKeys.add(proxyKey(proxy))) {
                  if (this.proxies.isEmpty() && added == 0) {
                     proxy.enabled = true;
                  }

                  this.proxies.add(proxy);
                  ++added;
               }
            }
         }

         this.importAdded.set(added);
         this.importing.set(false);
         this.importCancelRequested.set(false);
         this.importThread = null;
         if (added > 0) {
            this.bumpListRevision();
            this.scheduleSave();
            this.requestGeoLookup(false);
         }

         this.bumpImportRevision(true);
      }
   }

   private boolean isImportCurrent(long generation) {
      return this.importing.get() && this.importGeneration == generation && !this.importCancelRequested.get();
   }

   private void bumpImportRevision(boolean force) {
      long now = System.currentTimeMillis();
      if (force || now - this.lastImportRevisionMs >= 150L) {
         this.lastImportRevisionMs = now;
         ++this.importRevision;
      }
   }

   private void runRefreshJob(long generation, ExecutorService executor, List<RefreshTarget> targets, int timeout, int retryCount, int workerCount) {
      boolean completed = false;

      try {
         AtomicInteger nextIndex = new AtomicInteger();

         for(int i = 0; i < workerCount && this.isRefreshCurrent(generation); ++i) {
            executor.execute(() -> {
               while(true) {
                  if (this.isRefreshCurrent(generation)) {
                     int index = nextIndex.getAndIncrement();
                     if (index < targets.size()) {
                        this.refreshOne(generation, (RefreshTarget)targets.get(index), timeout, retryCount);
                        continue;
                     }
                  }

                  return;
               }
            });
         }

         executor.shutdown();

         while(this.isRefreshCurrent(generation)) {
            if (executor.awaitTermination(100L, TimeUnit.MILLISECONDS)) {
               completed = true;
               return;
            }
         }

         executor.shutdownNow();
      } catch (InterruptedException var16) {
         Thread.currentThread().interrupt();
         executor.shutdownNow();
      } catch (RejectedExecutionException e) {
         if (this.isRefreshCurrent(generation)) {
            AutismClientAddon.LOG.error("Proxy refresh rejected a check task", e);
         }

         executor.shutdownNow();
      } catch (RuntimeException e) {
         AutismClientAddon.LOG.error("Proxy refresh failed", e);
         executor.shutdownNow();
      } finally {
         this.finishRefresh(generation, completed && this.isRefreshCurrent(generation));
      }

   }

   private void refreshOne(long generation, RefreshTarget target, int timeout, int retryCount) {
      if (this.isRefreshCurrent(generation)) {
         AutismProxy.CheckResult result = target.proxy().probeStatus(timeout);

         for(int attempts = 0; result.code() == 3 && attempts < retryCount && this.isRefreshCurrent(generation); ++attempts) {
            result = target.proxy().probeStatus(timeout);
         }

         this.applyRefreshResult(generation, target, result);
      }
   }

   private synchronized void applyRefreshResult(long generation, RefreshTarget target, AutismProxy.CheckResult result) {
      if (this.refreshGeneration == generation && this.refreshing.get() && !this.refreshCancelRequested.get()) {
         AutismProxy proxy = target.proxy();
         if (this.proxies.contains(proxy) && target.key().equals(proxyKey(proxy))) {
            proxy.applyCheckResult(result);
         }

         this.refreshChecked.incrementAndGet();
         this.bumpRefreshRevision(false);
      }
   }

   private synchronized void finishRefresh(long generation, boolean completed) {
      if (this.refreshGeneration == generation && this.refreshing.get()) {
         this.refreshing.set(false);
         this.refreshCancelRequested.set(false);
         this.refreshExecutor = null;
         this.activeRefreshSnapshot = List.of();
         if (completed && this.sortByLatency) {
            this.sortByLatencyInternal();
         }

         if (completed && this.sortByLatency) {
            this.bumpListRevision();
            this.scheduleSave();
         }

         if (completed) {
            this.requestGeoLookup(false);
         }

         this.bumpRefreshRevision(true);
      }
   }

   private boolean isRefreshCurrent(long generation) {
      return this.refreshing.get() && this.refreshGeneration == generation && !this.refreshCancelRequested.get();
   }

   private synchronized List<GeoTarget> collectGeoTargets(boolean force) {
      long now = System.currentTimeMillis();
      List<GeoTarget> targets = new ArrayList();

      for(AutismProxy proxy : this.proxies) {
         if (proxy != null && proxy.needsGeoLookup(now, force)) {
            targets.add(new GeoTarget(proxy, proxyKey(proxy)));
         }
      }

      return targets;
   }

   private void runGeoLookup(long generation, List<GeoTarget> targets) {
      try {
         Map<String, List<GeoTarget>> byIp = new LinkedHashMap();
         List<GeoApplication> immediate = new ArrayList();

         for(GeoTarget target : targets) {
            if (!this.isGeoCurrent(generation)) {
               return;
            }

            AutismProxyGeoLookup.ResolveResult resolved = AutismProxyGeoLookup.resolveAddress(target.proxy().address);
            if (resolved.immediateResult() != null) {
               immediate.add(new GeoApplication(target, resolved.immediateResult()));
               if (immediate.size() >= 100) {
                  this.applyGeoResults(generation, immediate);
                  immediate.clear();
               }
            } else if (!resolved.ip().isBlank()) {
               ((List)byIp.computeIfAbsent(resolved.ip(), (ignored) -> new ArrayList())).add(target);
            }
         }

         if (!immediate.isEmpty()) {
            this.applyGeoResults(generation, immediate);
         }

         List<String> batch = new ArrayList(100);

         for(String ip : byIp.keySet()) {
            if (!this.isGeoCurrent(generation)) {
               return;
            }

            batch.add(ip);
            if (batch.size() >= 100) {
               this.lookupGeoBatch(generation, batch, byIp);
               batch.clear();
               sleepBetweenGeoBatches();
            }
         }

         if (!batch.isEmpty() && this.isGeoCurrent(generation)) {
            this.lookupGeoBatch(generation, batch, byIp);
         }

      } finally {
         this.finishGeoLookup(generation);
      }
   }

   private void lookupGeoBatch(long generation, List<String> batch, Map<String, List<GeoTarget>> byIp) {
      Map<String, AutismProxyGeoLookup.GeoResult> results = AutismProxyGeoLookup.lookupBatch(List.copyOf(batch));
      List<GeoApplication> applications = new ArrayList();

      for(String ip : batch) {
         AutismProxyGeoLookup.GeoResult result = (AutismProxyGeoLookup.GeoResult)results.getOrDefault(ip, AutismProxyGeoLookup.GeoResult.failed(ip, System.currentTimeMillis()));
         List<GeoTarget> targets = (List)byIp.get(ip);
         if (targets != null) {
            for(GeoTarget target : targets) {
               applications.add(new GeoApplication(target, result));
            }
         }
      }

      this.applyGeoResults(generation, applications);
   }

   private synchronized void applyGeoResults(long generation, List<GeoApplication> applications) {
      if (this.geoGeneration == generation && applications != null && !applications.isEmpty()) {
         int changed = 0;

         for(GeoApplication application : applications) {
            GeoTarget target = application.target();
            AutismProxy proxy = target.proxy();
            if (this.proxies.contains(proxy) && target.key().equals(proxyKey(proxy))) {
               proxy.applyGeoResult(application.result());
               ++changed;
            }
         }

         if (changed > 0) {
            this.bumpListRevision();
            this.scheduleSave();
         }

      }
   }

   private void finishGeoLookup(long generation) {
      boolean runAgain = false;
      synchronized(this) {
         if (this.geoGeneration == generation) {
            runAgain = this.geoLookupRequestedAgain.getAndSet(false);
            this.geoLookupRunning.set(false);
         }
      }

      if (runAgain) {
         this.requestGeoLookup(false);
      }

   }

   private boolean isGeoCurrent(long generation) {
      return this.geoLookupRunning.get() && this.geoGeneration == generation;
   }

   private static void sleepBetweenGeoBatches() {
      try {
         Thread.sleep(120L);
      } catch (InterruptedException var1) {
         Thread.currentThread().interrupt();
      }

   }

   private void bumpRefreshRevision(boolean force) {
      long now = System.currentTimeMillis();
      if (force || now - this.lastRefreshRevisionMs >= 150L || this.refreshChecked.get() >= this.refreshTotal.get()) {
         this.lastRefreshRevisionMs = now;
         ++this.refreshRevision;
      }
   }

   private void bumpListRevision() {
      ++this.listRevision;
      ++this.refreshRevision;
   }

   private int effectiveRefreshThreads(int targetCount) {
      int configured = Math.max(1, this.threads);
      int floor = targetCount >= 2000 ? 256 : (targetCount >= 500 ? 128 : (targetCount >= 100 ? 64 : (targetCount >= 32 ? 32 : configured)));
      return Math.max(1, Math.min(targetCount, Math.max(configured, floor)));
   }

   private int effectiveRefreshTimeout(int targetCount) {
      int configured = Math.max(1, this.timeoutMs);
      if (targetCount >= 500) {
         return Math.min(configured, 2500);
      } else {
         return targetCount >= 100 ? Math.min(configured, 3000) : configured;
      }
   }

   private int effectiveRefreshRetries(int targetCount) {
      return targetCount >= 100 ? 0 : Math.max(0, this.retries);
   }

   private static String proxyKey(AutismProxy proxy) {
      if (proxy == null) {
         return "";
      } else {
         String var10000 = proxy.type == null ? "" : proxy.type.name();
         return var10000 + "\u0000" + proxy.address + "\u0000" + proxy.port;
      }
   }

   public synchronized void clean() {
      if (!this.refreshing.get() && !this.importing.get()) {
         int before = this.proxies.size();
         this.proxies.removeIf((proxy) -> this.pruneDead && proxy.status == AutismProxy.Status.DEAD);
         this.proxies.removeIf((proxy) -> this.pruneLatency > 0 && proxy.status == AutismProxy.Status.ALIVE && proxy.latency >= (long)this.pruneLatency);
         List<AutismProxy> sorted = new ArrayList(this.proxies);
         sorted.sort(AutismProxyManager::compareByLatency);
         if (this.pruneToCount > 0 && sorted.size() > this.pruneToCount) {
            sorted.subList(this.pruneToCount, sorted.size()).clear();
            this.proxies.removeIf((proxy) -> !sorted.contains(proxy));
         }

         if (this.sortByLatency) {
            this.proxies.clear();
            this.proxies.addAll(sorted);
         }

         if (before != this.proxies.size() || this.sortByLatency) {
            this.bumpListRevision();
         }

         this.scheduleSave();
      }
   }

   public int importFromFile(File file) {
      List<AutismProxy> parsed = new ArrayList();
      int added = 0;
      Set<String> knownKeys;
      synchronized(this) {
         knownKeys = new HashSet(this.proxies.size() + 1024);

         for(AutismProxy proxy : this.proxies) {
            knownKeys.add(proxyKey(proxy));
         }
      }

      try {
         BufferedReader reader = new BufferedReader(new FileReader(file));

         String line;
         try {
            while((line = reader.readLine()) != null) {
               AutismProxy proxy = parseProxyLine(line.trim());
               if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) {
                  parsed.add(proxy);
               }
            }
         } catch (Throwable var12) {
            try {
               reader.close();
            } catch (Throwable var10) {
               var12.addSuppressed(var10);
            }

            throw var12;
         }

         reader.close();
      } catch (Exception e) {
         AutismClientAddon.LOG.error("Failed to import proxies", e);
      }

      if (parsed.isEmpty()) {
         return 0;
      } else {
         synchronized(this) {
            Set<String> currentKeys = new HashSet(this.proxies.size() + parsed.size() + 16);

            for(AutismProxy proxy : this.proxies) {
               currentKeys.add(proxyKey(proxy));
            }

            for(AutismProxy proxy : parsed) {
               if (currentKeys.add(proxyKey(proxy))) {
                  if (this.proxies.isEmpty() && added == 0) {
                     proxy.enabled = true;
                  }

                  this.proxies.add(proxy);
                  ++added;
               }
            }

            if (added > 0) {
               this.bumpListRevision();
               this.scheduleSave();
               this.requestGeoLookup(false);
            }

            return added;
         }
      }
   }

   private void sortByLatencyInternal() {
      this.proxies.sort(AutismProxyManager::compareByLatency);
   }

   private static int compareByLatency(AutismProxy a, AutismProxy b) {
      boolean aliveA = a != null && a.status == AutismProxy.Status.ALIVE;
      boolean aliveB = b != null && b.status == AutismProxy.Status.ALIVE;
      if (aliveA != aliveB) {
         return aliveA ? -1 : 1;
      } else {
         return aliveA ? Long.compare(a.latency, b.latency) : 0;
      }
   }

   private static AutismProxy parseProxyLine(String line) {
      if (!line.isBlank() && !line.startsWith("#")) {
         Matcher m = PROXY_PATTERN.matcher(line);
         if (m.find()) {
            return buildProxy(m.group(1), normalizeAddress(m.group(2)), Integer.parseInt(m.group(3)), m.group(4), AutismProxyType.Socks4);
         } else {
            m = PROXY_PATTERN_WEBSHARE.matcher(line);
            if (m.find()) {
               AutismProxy proxy = buildProxy((String)null, normalizeAddress(m.group(1)), Integer.parseInt(m.group(2)), (String)null, AutismProxyType.Socks5);
               if (m.group(3) != null) {
                  proxy.username = m.group(3);
               }

               if (m.group(4) != null) {
                  proxy.password = m.group(4);
               }

               return proxy;
            } else {
               m = PROXY_PATTERN_URI.matcher(line);
               if (!m.find()) {
                  return null;
               } else {
                  String typeName = m.group("type");
                  AutismProxyType defaultType = m.group("pass") == null && !"socks".equals(typeName) ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
                  AutismProxy proxy = buildProxy((String)null, normalizeAddress(m.group("addr")), Integer.parseInt(m.group("port")), typeName, defaultType);
                  if (m.group("user") != null) {
                     proxy.username = m.group("user");
                  }

                  if (m.group("pass") != null) {
                     proxy.password = m.group("pass");
                  }

                  return proxy;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static String normalizeAddress(String address) {
      return address == null ? "" : address.replaceAll("\\b0+\\B", "");
   }

   private static AutismProxy buildProxy(String name, String address, int port, String typeName, AutismProxyType defaultType) {
      AutismProxy proxy = new AutismProxy();
      proxy.name = name == null ? "" : name.trim();
      proxy.address = address;
      proxy.port = port;
      proxy.type = defaultType == null ? AutismProxyType.Socks5 : defaultType;
      if (typeName != null) {
         String lower = typeName.toLowerCase();
         if (lower.contains("4")) {
            proxy.type = AutismProxyType.Socks4;
         } else if (lower.contains("5")) {
            proxy.type = AutismProxyType.Socks5;
         }
      }

      return proxy;
   }

   public Iterator<AutismProxy> iterator() {
      return this.all().iterator();
   }

   public static record RefreshStatus(boolean running, boolean canceling, int checked, int total, long generation, long revision) {
   }

   public static record ImportStatus(boolean running, boolean canceling, int linesRead, int candidates, int added, long generation, long revision, boolean canceled) {
   }

   private static record RefreshTarget(AutismProxy proxy, String key) {
   }

   private static record GeoTarget(AutismProxy proxy, String key) {
   }

   private static record GeoApplication(GeoTarget target, AutismProxyGeoLookup.GeoResult result) {
   }
}
