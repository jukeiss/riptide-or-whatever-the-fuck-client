package riptide.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;

public final class RiptideHttp {
   private RiptideHttp() {
   }

   public static JsonObject getJson(String url, String bearerToken) {
      return getJson(url, bearerToken, Map.of());
   }

   public static JsonObject getJsonDirect(String url, String bearerToken) {
      try {
         HttpURLConnection connection = openDirect(url, 15000);
         connection.setRequestMethod("GET");
         if (bearerToken != null && !bearerToken.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
         }

         connection.setRequestProperty("Accept", "application/json");
         return readJson(connection);
      } catch (Exception var3) {
         return null;
      }
   }

   public static JsonObject getJson(String url, String bearerToken, Map<String, String> headers) {
      try {
         HttpURLConnection connection = open(url);
         connection.setRequestMethod("GET");
         if (bearerToken != null && !bearerToken.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
         }

         applyHeaders(connection, headers);
         connection.setRequestProperty("Accept", "application/json");
         return readJson(connection);
      } catch (Exception var4) {
         return null;
      }
   }

   public static RiptideHttp.JsonResult getJsonResult(String url, String bearerToken) {
      return getJsonResult(url, bearerToken, Map.of());
   }

   public static RiptideHttp.JsonResult getJsonResult(String url, String bearerToken, Map<String, String> headers) {
      try {
         HttpURLConnection connection = open(url);
         connection.setRequestMethod("GET");
         if (bearerToken != null && !bearerToken.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
         }

         applyHeaders(connection, headers);
         connection.setRequestProperty("Accept", "application/json");
         int code = connection.getResponseCode();
         return new RiptideHttp.JsonResult(code, readBodyJson(connection, code));
      } catch (Exception var5) {
         return new RiptideHttp.JsonResult(-1, null);
      }
   }

   public static JsonObject postJson(String url, String body) {
      try {
         HttpURLConnection connection = open(url);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setDoOutput(true);
         writeBody(connection, body);
         return readJson(connection);
      } catch (Exception var3) {
         return null;
      }
   }

   public static RiptideHttp.JsonResult postJsonResult(String url, String body) {
      return postJsonResult(url, body, 15000, Map.of());
   }

   public static RiptideHttp.JsonResult postJsonResult(String url, String body, int timeoutMs) {
      return postJsonResult(url, body, timeoutMs, Map.of());
   }

   public static RiptideHttp.JsonResult postJsonResult(String url, String body, Map<String, String> headers) {
      return postJsonResult(url, body, 15000, headers);
   }

   public static RiptideHttp.JsonResult postJsonResult(String url, String body, int timeoutMs, Map<String, String> headers) {
      try {
         HttpURLConnection connection = open(url, timeoutMs);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("Accept", "application/json");
         applyHeaders(connection, headers);
         connection.setDoOutput(true);
         writeBody(connection, body);
         int code = connection.getResponseCode();
         return new RiptideHttp.JsonResult(code, readBodyJson(connection, code));
      } catch (Exception var6) {
         return new RiptideHttp.JsonResult(-1, null);
      }
   }

   private static JsonObject readBodyJson(HttpURLConnection connection, int code) {
      try {
         JsonObject var4;
         try (InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (input == null) {
               return null;
            }

            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var4 = text.isEmpty() ? null : JsonParser.parseString(text).getAsJsonObject();
         }

         return var4;
      } catch (Exception var7) {
         return null;
      }
   }

   public static JsonObject postForm(String url, String body) {
      return postForm(url, body, Map.of());
   }

   public static JsonObject postForm(String url, String body, Map<String, String> headers) {
      try {
         HttpURLConnection connection = open(url);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         applyHeaders(connection, headers);
         connection.setDoOutput(true);
         writeBody(connection, body);
         return readJson(connection);
      } catch (Exception var4) {
         return null;
      }
   }

   public static RiptideHttp.JsonResult postFormResult(String url, String body) {
      return postFormResult(url, body, Map.of());
   }

   public static RiptideHttp.JsonResult postFormResult(String url, String body, Map<String, String> headers) {
      try {
         HttpURLConnection connection = open(url);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         connection.setRequestProperty("Accept", "application/json");
         applyHeaders(connection, headers);
         connection.setDoOutput(true);
         writeBody(connection, body);
         int code = connection.getResponseCode();
         return new RiptideHttp.JsonResult(code, readBodyJson(connection, code));
      } catch (Exception var5) {
         return new RiptideHttp.JsonResult(-1, null);
      }
   }

   private static HttpURLConnection open(String url) throws Exception {
      return open(url, 15000);
   }

   private static HttpURLConnection open(String url, int timeoutMs) throws Exception {
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection(Minecraft.getInstance().getProxy());
      configure(connection, timeoutMs);
      return connection;
   }

   private static HttpURLConnection openDirect(String url, int timeoutMs) throws Exception {
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection(RiptideAuthNetwork.directProxy());
      configure(connection, timeoutMs);
      return connection;
   }

   private static void configure(HttpURLConnection connection, int timeoutMs) {
      connection.setConnectTimeout(timeoutMs);
      connection.setReadTimeout(timeoutMs);
      connection.setRequestProperty(
         "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"
      );
   }

   private static void writeBody(HttpURLConnection connection, String body) throws Exception {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));

      try (OutputStream output = connection.getOutputStream()) {
         output.write(bytes);
      }
   }

   private static void applyHeaders(HttpURLConnection connection, Map<String, String> headers) {
      if (headers != null) {
         for (Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
               connection.setRequestProperty(e.getKey(), e.getValue());
            }
         }
      }
   }

   private static JsonObject readJson(HttpURLConnection connection) throws Exception {
      int code = connection.getResponseCode();
      if (code >= 200 && code < 300) {
         JsonObject var4;
         try (InputStream input = connection.getInputStream()) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var4 = JsonParser.parseString(text).getAsJsonObject();
         }

         return var4;
      } else {
         return null;
      }
   }

   public record JsonResult(int status, JsonObject body) {
      public boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      public String error() {
         try {
            return this.body != null && this.body.has("error") ? this.body.get("error").getAsString() : "";
         } catch (Exception var2) {
            return "";
         }
      }
   }
}
