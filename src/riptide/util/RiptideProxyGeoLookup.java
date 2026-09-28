package riptide.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Locale.Builder;

public final class RiptideProxyGeoLookup {
   private static final String API_URL = "https://api.country.is/?fields=city,continent,subdivision,location,asn";

   private RiptideProxyGeoLookup() {
   }

   public static RiptideProxyGeoLookup.ResolveResult resolveAddress(String address) {
      long now = System.currentTimeMillis();

      try {
         String clean = cleanAddress(address);
         if (clean.isBlank()) {
            return new RiptideProxyGeoLookup.ResolveResult("", RiptideProxyGeoLookup.GeoResult.failed("", now));
         } else {
            InetAddress inetAddress = InetAddress.getByName(clean);
            String ip = inetAddress.getHostAddress();
            return !isPublicAddress(inetAddress)
               ? new RiptideProxyGeoLookup.ResolveResult(ip, RiptideProxyGeoLookup.GeoResult.privateAddress(ip, now))
               : new RiptideProxyGeoLookup.ResolveResult(ip, null);
         }
      } catch (Exception var6) {
         return new RiptideProxyGeoLookup.ResolveResult("", RiptideProxyGeoLookup.GeoResult.failed("", now));
      }
   }

   public static Map<String, RiptideProxyGeoLookup.GeoResult> lookupBatch(List<String> ips) {
      Map<String, RiptideProxyGeoLookup.GeoResult> out = new HashMap<>();
      if (ips != null && !ips.isEmpty()) {
         long now = System.currentTimeMillis();

         try {
            HttpURLConnection connection = (HttpURLConnection)URI.create("https://api.country.is/?fields=city,continent,subdivision,location,asn")
               .toURL()
               .openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Riptide-ProxyGeo");
            connection.setDoOutput(true);
            String body = jsonArray(ips);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));

            try (OutputStream output = connection.getOutputStream()) {
               output.write(bytes);
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
               return failedMap(ips, now);
            }

            String raw;
            try (InputStream input = connection.getInputStream()) {
               raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed.isJsonArray()) {
               for (JsonElement element : parsed.getAsJsonArray()) {
                  RiptideProxyGeoLookup.GeoResult result = parseResult(element, now);
                  if (result != null && !result.resolvedIp().isBlank()) {
                     out.put(result.resolvedIp(), result);
                  }
               }
            } else {
               RiptideProxyGeoLookup.GeoResult result = parseResult(parsed, now);
               if (result != null && !result.resolvedIp().isBlank()) {
                  out.put(result.resolvedIp(), result);
               }
            }
         } catch (Exception var17) {
            return failedMap(ips, now);
         }

         for (String ip : ips) {
            out.putIfAbsent(ip, RiptideProxyGeoLookup.GeoResult.failed(ip, now));
         }

         return out;
      } else {
         return out;
      }
   }

   private static RiptideProxyGeoLookup.GeoResult parseResult(JsonElement element, long now) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         String ip = firstString(object, "ip", "query");
         if (ip.isBlank()) {
            return null;
         } else {
            String countryCode = firstString(object, "country", "countryCode", "country_code").toUpperCase(Locale.ROOT);
            String countryName = firstString(object, "countryName", "country_name");
            if (countryName.isBlank()) {
               countryName = displayCountry(countryCode);
            }

            String continent = firstString(object, "continent", "continentCode", "continent_code");
            String region = displayContinent(continent);
            String city = firstString(object, "city");
            String subdivision = subdivision(object.get("subdivision"));
            return countryCode.isBlank() && region.isBlank() && city.isBlank()
               ? RiptideProxyGeoLookup.GeoResult.failed(ip, now)
               : new RiptideProxyGeoLookup.GeoResult(RiptideProxy.GeoStatus.RESOLVED, ip, countryCode, countryName, region, city, subdivision, now);
         }
      } else {
         return null;
      }
   }

   private static Map<String, RiptideProxyGeoLookup.GeoResult> failedMap(List<String> ips, long now) {
      Map<String, RiptideProxyGeoLookup.GeoResult> out = new HashMap<>();

      for (String ip : ips) {
         out.put(ip, RiptideProxyGeoLookup.GeoResult.failed(ip, now));
      }

      return out;
   }

   private static String jsonArray(List<String> ips) {
      JsonArray array = new JsonArray();

      for (String ip : ips) {
         array.add(ip);
      }

      return array.toString();
   }

   private static String subdivision(JsonElement element) {
      if (element == null || element.isJsonNull()) {
         return "";
      } else if (element.isJsonPrimitive()) {
         return element.getAsString();
      } else {
         return element.isJsonObject() ? firstString(element.getAsJsonObject(), "name", "code", "iso_code") : "";
      }
   }

   private static String displayCountry(String countryCode) {
      if (countryCode != null && !countryCode.isBlank()) {
         try {
            return new Builder().setRegion(countryCode.toUpperCase(Locale.ROOT)).build().getDisplayCountry(Locale.ENGLISH);
         } catch (RuntimeException var2) {
            return "";
         }
      } else {
         return "";
      }
   }

   private static String displayContinent(String continent) {
      String code = safe(continent).toUpperCase(Locale.ROOT);

      return switch (code) {
         case "AF" -> "Africa";
         case "AN" -> "Antarctica";
         case "AS" -> "Asia";
         case "EU" -> "Europe";
         case "NA" -> "North America";
         case "OC" -> "Oceania";
         case "SA" -> "South America";
         default -> safe(continent);
      };
   }

   private static String firstString(JsonObject object, String... keys) {
      if (object != null && keys != null) {
         for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
               String value = element.getAsString();
               if (value != null && !value.isBlank()) {
                  return value.trim();
               }
            }
         }

         return "";
      } else {
         return "";
      }
   }

   private static String cleanAddress(String address) {
      String clean = safe(address).trim();
      return clean.startsWith("[") && clean.endsWith("]") ? clean.substring(1, clean.length() - 1) : clean;
   }

   private static boolean isPublicAddress(InetAddress address) {
      if (address != null
         && !address.isAnyLocalAddress()
         && !address.isLoopbackAddress()
         && !address.isLinkLocalAddress()
         && !address.isSiteLocalAddress()
         && !address.isMulticastAddress()) {
         byte[] bytes = address.getAddress();
         if (bytes.length == 4) {
            return isPublicIpv4(bytes);
         } else {
            return bytes.length == 16 ? isPublicIpv6(bytes) : true;
         }
      } else {
         return false;
      }
   }

   private static boolean isPublicIpv4(byte[] bytes) {
      int a = Byte.toUnsignedInt(bytes[0]);
      int b = Byte.toUnsignedInt(bytes[1]);
      int c = Byte.toUnsignedInt(bytes[2]);
      if (a == 0 || a == 10 || a == 127 || a >= 224) {
         return false;
      } else if (a == 100 && b >= 64 && b <= 127) {
         return false;
      } else if (a == 169 && b == 254) {
         return false;
      } else if (a == 172 && b >= 16 && b <= 31) {
         return false;
      } else if (a != 192 || b != 0 && b != 2 && b != 168) {
         return a != 198 || b != 18 && b != 19 && (b != 51 || c != 100) ? a != 203 || b != 0 || c != 113 : false;
      } else {
         return false;
      }
   }

   private static boolean isPublicIpv6(byte[] bytes) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      int third = Byte.toUnsignedInt(bytes[2]);
      int fourth = Byte.toUnsignedInt(bytes[3]);
      return (first & 254) == 252 ? false : first != 32 || second != 1 || third != 13 || fourth != 184;
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }

   public record GeoResult(
      RiptideProxy.GeoStatus status, String resolvedIp, String countryCode, String countryName, String region, String city, String subdivision, long checkedAt
   ) {
      public static RiptideProxyGeoLookup.GeoResult failed(String ip, long now) {
         return new RiptideProxyGeoLookup.GeoResult(RiptideProxy.GeoStatus.FAILED, RiptideProxyGeoLookup.safe(ip), "", "", "", "", "", now);
      }

      public static RiptideProxyGeoLookup.GeoResult privateAddress(String ip, long now) {
         return new RiptideProxyGeoLookup.GeoResult(RiptideProxy.GeoStatus.PRIVATE, RiptideProxyGeoLookup.safe(ip), "", "", "", "", "", now);
      }
   }

   public record ResolveResult(String ip, RiptideProxyGeoLookup.GeoResult immediateResult) {
   }
}
