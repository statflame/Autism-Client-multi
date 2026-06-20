package autismclient.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class AutismProxy {
   public String name = "";
   public AutismProxyType type;
   public String address;
   public int port;
   public boolean enabled;
   public String username;
   public String password;
   public volatile Status status;
   public volatile long latency;
   public volatile GeoStatus geoStatus;
   public volatile String geoResolvedIp;
   public volatile String geoCountryCode;
   public volatile String geoCountryName;
   public volatile String geoRegion;
   public volatile String geoCity;
   public volatile String geoSubdivision;
   public volatile long geoCheckedAt;

   public AutismProxy() {
      this.type = AutismProxyType.Socks5;
      this.address = "";
      this.port = 0;
      this.enabled = false;
      this.username = "";
      this.password = "";
      this.status = AutismProxy.Status.UNCHECKED;
      this.latency = 0L;
      this.geoStatus = AutismProxy.GeoStatus.UNKNOWN;
      this.geoResolvedIp = "";
      this.geoCountryCode = "";
      this.geoCountryName = "";
      this.geoRegion = "";
      this.geoCity = "";
      this.geoSubdivision = "";
      this.geoCheckedAt = 0L;
   }

   public AutismProxy(Tag tag) {
      this.type = AutismProxyType.Socks5;
      this.address = "";
      this.port = 0;
      this.enabled = false;
      this.username = "";
      this.password = "";
      this.status = AutismProxy.Status.UNCHECKED;
      this.latency = 0L;
      this.geoStatus = AutismProxy.GeoStatus.UNKNOWN;
      this.geoResolvedIp = "";
      this.geoCountryCode = "";
      this.geoCountryName = "";
      this.geoRegion = "";
      this.geoCity = "";
      this.geoSubdivision = "";
      this.geoCheckedAt = 0L;
      if (tag instanceof CompoundTag compoundTag) {
         this.fromTag(compoundTag);
      }

   }

   public boolean isValid() {
      return this.address != null && !this.address.isBlank() && this.port > 0 && this.port <= 65535;
   }

   public String displayName() {
      String label = this.name != null && !this.name.isBlank() ? this.name : this.address;
      return label == null ? "" : label;
   }

   public String geoLabel() {
      GeoStatus state = this.geoStatus == null ? AutismProxy.GeoStatus.UNKNOWN : this.geoStatus;
      if (state == AutismProxy.GeoStatus.LOOKING_UP) {
         return "Geo...";
      } else if (state != AutismProxy.GeoStatus.RESOLVED) {
         return "Unknown";
      } else {
         String code = safe(this.geoCountryCode).toUpperCase(Locale.ROOT);
         String region = safe(this.geoRegion);
         if (!code.isBlank() && !region.isBlank()) {
            return code + " " + region;
         } else if (!code.isBlank()) {
            return code;
         } else {
            return !region.isBlank() ? region : "Unknown";
         }
      }
   }

   public int geoColor() {
      GeoStatus state = this.geoStatus == null ? AutismProxy.GeoStatus.UNKNOWN : this.geoStatus;
      int var10000;
      switch (state.ordinal()) {
         case 0:
         case 3:
            var10000 = -6645094;
            break;
         case 1:
            var10000 = -7429889;
            break;
         case 2:
            var10000 = -855310;
            break;
         case 4:
            var10000 = -6645094;
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public String geoSearchText() {
      return String.join(" ", this.geoLabel(), safe(this.geoResolvedIp), safe(this.geoCountryCode), safe(this.geoCountryName), safe(this.geoRegion), safe(this.geoCity), safe(this.geoSubdivision));
   }

   public boolean needsGeoLookup(long now, boolean force) {
      if (!this.isValid()) {
         return false;
      } else if (force) {
         return true;
      } else {
         GeoStatus state = this.geoStatus == null ? AutismProxy.GeoStatus.UNKNOWN : this.geoStatus;
         long age = this.geoCheckedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, now - this.geoCheckedAt);
         boolean var10000;
         switch (state.ordinal()) {
            case 0:
               var10000 = true;
               break;
            case 1:
               var10000 = age > 30000L;
               break;
            case 2:
            case 4:
               var10000 = age > 604800000L;
               break;
            case 3:
               var10000 = age > 21600000L;
               break;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   public void markGeoLookupPending(long now) {
      this.geoStatus = AutismProxy.GeoStatus.LOOKING_UP;
      this.geoCheckedAt = Math.max(0L, now);
   }

   public void clearGeo() {
      this.geoStatus = AutismProxy.GeoStatus.UNKNOWN;
      this.geoResolvedIp = "";
      this.geoCountryCode = "";
      this.geoCountryName = "";
      this.geoRegion = "";
      this.geoCity = "";
      this.geoSubdivision = "";
      this.geoCheckedAt = 0L;
   }

   public void applyGeoResult(AutismProxyGeoLookup.GeoResult result) {
      if (result == null) {
         this.geoStatus = AutismProxy.GeoStatus.FAILED;
         this.geoCheckedAt = System.currentTimeMillis();
      } else {
         this.geoStatus = result.status();
         this.geoResolvedIp = safe(result.resolvedIp());
         this.geoCountryCode = safe(result.countryCode()).toUpperCase(Locale.ROOT);
         this.geoCountryName = safe(result.countryName());
         this.geoRegion = safe(result.region());
         this.geoCity = safe(result.city());
         this.geoSubdivision = safe(result.subdivision());
         this.geoCheckedAt = result.checkedAt() <= 0L ? System.currentTimeMillis() : result.checkedAt();
      }
   }

   public synchronized int checkStatus(int timeoutMs) {
      this.status = AutismProxy.Status.CHECKING;
      CheckResult result = this.probeStatus(timeoutMs);
      this.applyCheckResult(result);
      return result.code();
   }

   public synchronized CheckResult probeStatus(int timeoutMs) {
      boolean timeout = false;
      TypeProbeResult primary = this.checkType(this.type == AutismProxyType.Socks4 ? AutismProxyType.Socks4 : AutismProxyType.Socks5, timeoutMs);
      if (primary.alive()) {
         return new CheckResult(AutismProxy.Status.ALIVE, primary.latency(), 1);
      } else {
         if (primary.timeout()) {
            timeout = true;
         }

         TypeProbeResult fallback = this.checkType(this.type == AutismProxyType.Socks4 ? AutismProxyType.Socks5 : AutismProxyType.Socks4, timeoutMs);
         if (fallback.alive()) {
            return new CheckResult(AutismProxy.Status.ALIVE, fallback.latency(), 1);
         } else {
            if (fallback.timeout()) {
               timeout = true;
            }

            return new CheckResult(AutismProxy.Status.DEAD, 0L, timeout ? 3 : 2);
         }
      }
   }

   public synchronized void applyCheckResult(CheckResult result) {
      if (result == null) {
         this.status = AutismProxy.Status.UNCHECKED;
         this.latency = 0L;
      } else {
         this.status = result.status();
         this.latency = result.status() == AutismProxy.Status.ALIVE ? result.latency() : 0L;
      }
   }

   private TypeProbeResult checkType(AutismProxyType checkType, int timeoutMs) {
      try {
         Instant before = Instant.now();
         boolean alive = checkType == AutismProxyType.Socks4 ? this.isSocks4(timeoutMs) : this.isSocks5(timeoutMs);
         if (alive) {
            return new TypeProbeResult(true, false, Duration.between(before, Instant.now()).toMillis());
         }
      } catch (SocketTimeoutException var5) {
         return new TypeProbeResult(false, true, 0L);
      } catch (IOException var6) {
      }

      return new TypeProbeResult(false, false, 0L);
   }

   private boolean isSocks4(int timeoutMs) throws IOException {
      byte[] user = safe(this.username).getBytes();
      ByteBuffer bb;
      if (isIpv4Address(this.address)) {
         bb = ByteBuffer.allocate(9 + user.length).put((byte)4).put((byte)1).putShort((short)this.port).put(InetAddress.getByName(this.address).getAddress()).put(user).put((byte)0);
      } else {
         byte[] addr = safe(this.address).getBytes();
         bb = ByteBuffer.allocate(10 + user.length + addr.length).put((byte)4).put((byte)1).putShort((short)this.port).put(new byte[]{0, 0, 0, 1}).put(user).put((byte)0).put(addr).put((byte)0);
      }

      byte[] data = this.sendData(bb.array(), 8, timeoutMs);
      return data.length >= 2 && data[0] == 0 && data[1] == 90;
   }

   private boolean isSocks5(int timeoutMs) throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(4).put((byte)5).put((byte)2).put((byte)0).put((byte)2);
      byte[] data = this.sendData(bb.array(), 2, timeoutMs);
      return data.length >= 2 && data[0] == 5 && (data[1] == 0 || data[1] == 2);
   }

   private byte[] sendData(byte[] data, int read, int timeoutMs) throws IOException {
      Socket socket = new Socket();

      byte[] var7;
      try {
         int timeout = Math.max(1, timeoutMs);
         socket.setSoTimeout(timeout);
         socket.connect(new InetSocketAddress(this.address, this.port), timeout);
         OutputStream output = socket.getOutputStream();
         output.write(data);
         var7 = socket.getInputStream().readNBytes(read);
      } catch (Throwable var9) {
         try {
            socket.close();
         } catch (Throwable var8) {
            var9.addSuppressed(var8);
         }

         throw var9;
      }

      socket.close();
      return var7;
   }

   private static boolean isIpv4Address(String value) {
      if (value == null) {
         return false;
      } else {
         String[] parts = value.split("\\.");
         if (parts.length != 4) {
            return false;
         } else {
            for(String part : parts) {
               try {
                  int i = Integer.parseInt(part);
                  if (i < 0 || i > 255) {
                     return false;
                  }
               } catch (NumberFormatException var7) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("name", safe(this.name));
      tag.putString("type", this.type == null ? AutismProxyType.Socks5.name() : this.type.name());
      tag.putString("address", safe(this.address));
      tag.putInt("port", this.port);
      tag.putBoolean("enabled", this.enabled);
      tag.putString("username", safe(this.username));
      tag.putString("password", safe(this.password));
      tag.putString("geoStatus", this.geoStatus == null ? AutismProxy.GeoStatus.UNKNOWN.name() : this.geoStatus.name());
      tag.putString("geoResolvedIp", safe(this.geoResolvedIp));
      tag.putString("geoCountryCode", safe(this.geoCountryCode));
      tag.putString("geoCountryName", safe(this.geoCountryName));
      tag.putString("geoRegion", safe(this.geoRegion));
      tag.putString("geoCity", safe(this.geoCity));
      tag.putString("geoSubdivision", safe(this.geoSubdivision));
      tag.putLong("geoCheckedAt", this.geoCheckedAt);
      return tag;
   }

   public AutismProxy fromTag(CompoundTag tag) {
      this.name = tag.getStringOr("name", "");
      String typeName = tag.getStringOr("type", AutismProxyType.Socks5.name());

      try {
         this.type = AutismProxyType.valueOf(typeName);
      } catch (IllegalArgumentException var5) {
         this.type = typeName.toLowerCase(Locale.ROOT).contains("4") ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
      }

      this.address = tag.getStringOr("address", "");
      this.port = tag.getIntOr("port", 0);
      this.enabled = tag.getBooleanOr("enabled", false);
      this.username = tag.getStringOr("username", "");
      this.password = tag.getStringOr("password", "");

      try {
         this.geoStatus = AutismProxy.GeoStatus.valueOf(tag.getStringOr("geoStatus", AutismProxy.GeoStatus.UNKNOWN.name()));
      } catch (IllegalArgumentException var4) {
         this.geoStatus = AutismProxy.GeoStatus.UNKNOWN;
      }

      this.geoResolvedIp = tag.getStringOr("geoResolvedIp", "");
      this.geoCountryCode = tag.getStringOr("geoCountryCode", "");
      this.geoCountryName = tag.getStringOr("geoCountryName", "");
      this.geoRegion = tag.getStringOr("geoRegion", "");
      this.geoCity = tag.getStringOr("geoCity", "");
      this.geoSubdivision = tag.getStringOr("geoSubdivision", "");
      this.geoCheckedAt = tag.getLongOr("geoCheckedAt", 0L);
      this.status = AutismProxy.Status.UNCHECKED;
      this.latency = 0L;
      return this;
   }

   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!(obj instanceof AutismProxy)) {
         return false;
      } else {
         AutismProxy proxy = (AutismProxy)obj;
         return this.port == proxy.port && Objects.equals(this.address, proxy.address) && this.type == proxy.type;
      }
   }

   public int hashCode() {
      return Objects.hash(new Object[]{this.type, this.address, this.port});
   }

   public static enum Status {
      UNCHECKED,
      CHECKING,
      DEAD,
      ALIVE;

      private Status() {
      }

      public String display() {
         String var10000;
         switch (this.ordinal()) {
            case 0 -> var10000 = "";
            case 1 -> var10000 = "...";
            case 2 -> var10000 = "X";
            case 3 -> var10000 = "O";
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }

      public int color() {
         int var10000;
         switch (this.ordinal()) {
            case 0:
            case 1:
               var10000 = -5723992;
               break;
            case 2:
               var10000 = -42406;
               break;
            case 3:
               var10000 = -10813551;
               break;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   public static enum GeoStatus {
      UNKNOWN,
      LOOKING_UP,
      RESOLVED,
      FAILED,
      PRIVATE;

      private GeoStatus() {
      }
   }

   public static record CheckResult(Status status, long latency, int code) {
      public CheckResult(Status status, long latency, int code) {
         status = status == null ? AutismProxy.Status.DEAD : status;
         latency = Math.max(0L, latency);
         this.status = status;
         this.latency = latency;
         this.code = code;
      }
   }

   private static record TypeProbeResult(boolean alive, boolean timeout, long latency) {
   }
}
