package riptide.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import riptide.util.mm.crypto.AtRestSeal;
import riptide.util.multi.MultiProxyVerifier;

public class RiptideProxy {
   public String id = UUID.randomUUID().toString();
   public String name = "";
   public RiptideProxyType type = RiptideProxyType.Socks5;
   public String address = "";
   public int port = 0;
   public boolean enabled = false;
   public String username = "";
   public String password = "";
   public volatile RiptideProxy.Status status = RiptideProxy.Status.UNCHECKED;
   public volatile long latency = 0L;
   public volatile RiptideProxy.GeoStatus geoStatus = RiptideProxy.GeoStatus.UNKNOWN;
   public volatile String geoResolvedIp = "";
   public volatile String geoCountryCode = "";
   public volatile String geoCountryName = "";
   public volatile String geoRegion = "";
   public volatile String geoCity = "";
   public volatile String geoSubdivision = "";
   public volatile long geoCheckedAt = 0L;
   transient boolean generatedStableId;

   public RiptideProxy() {
   }

   public RiptideProxy(Tag tag) {
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
      RiptideProxy.GeoStatus state = this.geoStatus == null ? RiptideProxy.GeoStatus.UNKNOWN : this.geoStatus;
      if (state == RiptideProxy.GeoStatus.LOOKING_UP) {
         return "Geo...";
      } else if (state != RiptideProxy.GeoStatus.RESOLVED) {
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
      RiptideProxy.GeoStatus state = this.geoStatus == null ? RiptideProxy.GeoStatus.UNKNOWN : this.geoStatus;

      return switch (state) {
         case UNKNOWN, FAILED -> -6645094;
         case LOOKING_UP -> -7429889;
         case RESOLVED -> -855310;
         case PRIVATE -> -6645094;
      };
   }

   public String geoSearchText() {
      return String.join(
         " ",
         this.geoLabel(),
         safe(this.geoResolvedIp),
         safe(this.geoCountryCode),
         safe(this.geoCountryName),
         safe(this.geoRegion),
         safe(this.geoCity),
         safe(this.geoSubdivision)
      );
   }

   public boolean needsGeoLookup(long now, boolean force) {
      if (!this.isValid()) {
         return false;
      } else if (force) {
         return true;
      } else {
         RiptideProxy.GeoStatus state = this.geoStatus == null ? RiptideProxy.GeoStatus.UNKNOWN : this.geoStatus;
         long age = this.geoCheckedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, now - this.geoCheckedAt);

         return switch (state) {
            case UNKNOWN -> true;
            case LOOKING_UP -> age > 30000L;
            case RESOLVED, PRIVATE -> age > 604800000L;
            case FAILED -> age > 21600000L;
         };
      }
   }

   public void markGeoLookupPending(long now) {
      this.geoStatus = RiptideProxy.GeoStatus.LOOKING_UP;
      this.geoCheckedAt = Math.max(0L, now);
   }

   public void clearGeo() {
      this.geoStatus = RiptideProxy.GeoStatus.UNKNOWN;
      this.geoResolvedIp = "";
      this.geoCountryCode = "";
      this.geoCountryName = "";
      this.geoRegion = "";
      this.geoCity = "";
      this.geoSubdivision = "";
      this.geoCheckedAt = 0L;
   }

   public void applyGeoResult(RiptideProxyGeoLookup.GeoResult result) {
      if (result == null) {
         this.geoStatus = RiptideProxy.GeoStatus.FAILED;
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
      this.status = RiptideProxy.Status.CHECKING;
      RiptideProxy.CheckResult result = this.probeStatus(timeoutMs);
      this.applyCheckResult(result);
      return result.code();
   }

   public synchronized RiptideProxy.CheckResult probeStatus(int timeoutMs) {
      MultiProxyVerifier.Result verified = MultiProxyVerifier.verify(this, "example.com", 80, timeoutMs);
      if (!verified.ok()) {
         return new RiptideProxy.CheckResult(RiptideProxy.Status.DEAD, 0L, 2);
      } else {
         if (verified.workingType() != null) {
            this.type = verified.workingType();
         }

         return new RiptideProxy.CheckResult(RiptideProxy.Status.ALIVE, verified.latencyMs(), 1);
      }
   }

   public synchronized void applyCheckResult(RiptideProxy.CheckResult result) {
      if (result == null) {
         this.status = RiptideProxy.Status.UNCHECKED;
         this.latency = 0L;
      } else {
         this.status = result.status();
         this.latency = result.status() == RiptideProxy.Status.ALIVE ? result.latency() : 0L;
      }
   }

   private RiptideProxy.TypeProbeResult checkType(RiptideProxyType checkType, int timeoutMs) {
      try {
         Instant before = Instant.now();
         boolean alive = checkType == RiptideProxyType.Socks4 ? this.isSocks4(timeoutMs) : this.isSocks5(timeoutMs);
         if (alive) {
            return new RiptideProxy.TypeProbeResult(true, false, Duration.between(before, Instant.now()).toMillis());
         }
      } catch (SocketTimeoutException var5) {
         return new RiptideProxy.TypeProbeResult(false, true, 0L);
      } catch (IOException var6) {
      }

      return new RiptideProxy.TypeProbeResult(false, false, 0L);
   }

   private boolean isSocks4(int timeoutMs) throws IOException {
      byte[] user = safe(this.username).getBytes();
      ByteBuffer bb;
      if (isIpv4Address(this.address)) {
         bb = ByteBuffer.allocate(9 + user.length)
            .put((byte)4)
            .put((byte)1)
            .putShort((short)this.port)
            .put(InetAddress.getByName(this.address).getAddress())
            .put(user)
            .put((byte)0);
      } else {
         byte[] addr = safe(this.address).getBytes();
         bb = ByteBuffer.allocate(10 + user.length + addr.length)
            .put((byte)4)
            .put((byte)1)
            .putShort((short)this.port)
            .put(new byte[]{0, 0, 0, 1})
            .put(user)
            .put((byte)0)
            .put(addr)
            .put((byte)0);
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
      byte[] var7;
      try (Socket socket = new Socket()) {
         int timeout = Math.max(1, timeoutMs);
         socket.setSoTimeout(timeout);
         socket.connect(new InetSocketAddress(this.address, this.port), timeout);
         OutputStream output = socket.getOutputStream();
         output.write(data);
         var7 = socket.getInputStream().readNBytes(read);
      }

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
            for (String part : parts) {
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

   private static String sealString(String value) {
      if (value != null && !value.isEmpty()) {
         byte[] sealed = AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
         return sealed == null ? "" : Base64.getEncoder().encodeToString(sealed);
      } else {
         return "";
      }
   }

   private static String unsealString(String encoded, String plainFallback) {
      String fallback = plainFallback == null ? "" : plainFallback;
      if (encoded != null && !encoded.isEmpty()) {
         try {
            byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
            return plain == null ? fallback : new String(plain, StandardCharsets.UTF_8);
         } catch (Throwable var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.stableId());
      tag.putString("name", safe(this.name));
      tag.putString("type", this.type == null ? RiptideProxyType.Socks5.name() : this.type.name());
      tag.putString("address", safe(this.address));
      tag.putInt("port", this.port);
      tag.putBoolean("enabled", this.enabled);
      tag.putString("encUsername", sealString(this.username));
      tag.putString("encPassword", sealString(this.password));
      tag.putString("geoStatus", this.geoStatus == null ? RiptideProxy.GeoStatus.UNKNOWN.name() : this.geoStatus.name());
      tag.putString("geoResolvedIp", safe(this.geoResolvedIp));
      tag.putString("geoCountryCode", safe(this.geoCountryCode));
      tag.putString("geoCountryName", safe(this.geoCountryName));
      tag.putString("geoRegion", safe(this.geoRegion));
      tag.putString("geoCity", safe(this.geoCity));
      tag.putString("geoSubdivision", safe(this.geoSubdivision));
      tag.putLong("geoCheckedAt", this.geoCheckedAt);
      return tag;
   }

   public RiptideProxy fromTag(CompoundTag tag) {
      String storedId = tag.getStringOr("id", "");
      if (storedId.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      } else {
         this.id = storedId;
      }

      this.name = tag.getStringOr("name", "");
      String typeName = tag.getStringOr("type", RiptideProxyType.Socks5.name());

      try {
         this.type = RiptideProxyType.valueOf(typeName);
      } catch (IllegalArgumentException var6) {
         this.type = typeName.toLowerCase(Locale.ROOT).contains("4") ? RiptideProxyType.Socks4 : RiptideProxyType.Socks5;
      }

      this.address = tag.getStringOr("address", "");
      this.port = tag.getIntOr("port", 0);
      this.enabled = tag.getBooleanOr("enabled", false);
      this.username = unsealString(tag.getStringOr("encUsername", ""), tag.getStringOr("username", ""));
      this.password = unsealString(tag.getStringOr("encPassword", ""), tag.getStringOr("password", ""));

      try {
         this.geoStatus = RiptideProxy.GeoStatus.valueOf(tag.getStringOr("geoStatus", RiptideProxy.GeoStatus.UNKNOWN.name()));
      } catch (IllegalArgumentException var5) {
         this.geoStatus = RiptideProxy.GeoStatus.UNKNOWN;
      }

      this.geoResolvedIp = tag.getStringOr("geoResolvedIp", "");
      this.geoCountryCode = tag.getStringOr("geoCountryCode", "");
      this.geoCountryName = tag.getStringOr("geoCountryName", "");
      this.geoRegion = tag.getStringOr("geoRegion", "");
      this.geoCity = tag.getStringOr("geoCity", "");
      this.geoSubdivision = tag.getStringOr("geoSubdivision", "");
      this.geoCheckedAt = tag.getLongOr("geoCheckedAt", 0L);
      this.status = RiptideProxy.Status.UNCHECKED;
      this.latency = 0L;
      return this;
   }

   public String stableId() {
      if (this.id == null || this.id.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      }

      return this.id;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof RiptideProxy proxy)
            ? false
            : this.port == proxy.port && Objects.equals(this.address, proxy.address) && this.type == proxy.type;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.type, this.address, this.port);
   }

   public record CheckResult(RiptideProxy.Status status, long latency, int code) {
      public CheckResult(RiptideProxy.Status status, long latency, int code) {
         status = status == null ? RiptideProxy.Status.DEAD : status;
         latency = Math.max(0L, latency);
         this.status = status;
         this.latency = latency;
         this.code = code;
      }
   }

   public static enum GeoStatus {
      UNKNOWN,
      LOOKING_UP,
      RESOLVED,
      FAILED,
      PRIVATE;
   }

   public static enum Status {
      UNCHECKED,
      CHECKING,
      DEAD,
      ALIVE;

      public String display() {
         return switch (this) {
            case UNCHECKED -> "";
            case CHECKING -> "...";
            case DEAD -> "X";
            case ALIVE -> "O";
         };
      }

      public int color() {
         return switch (this) {
            case UNCHECKED, CHECKING -> -5723992;
            case DEAD -> -42406;
            case ALIVE -> -10813551;
         };
      }
   }

   private record TypeProbeResult(boolean alive, boolean timeout, long latency) {
   }
}
