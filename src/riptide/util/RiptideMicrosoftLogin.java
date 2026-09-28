package riptide.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import net.minecraft.util.Util;

public final class RiptideMicrosoftLogin {
   private static final String DEFAULT_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
   public static final String CLIENT_ID = resolveClientId();
   public static final int PORT = 9675;
   private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
   private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
   private static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";
   private static volatile String codeVerifier = "";
   private static volatile String oauthState = "";
   private static volatile HttpServer server;
   private static volatile Consumer<String> callback;
   private static final Semaphore LOGIN_PERMITS = new Semaphore(2, true);
   private static final Object LOGIN_SPACING = new Object();
   private static volatile long lastLoginStart;
   private static final long MIN_LOGIN_SPACING_MS = 400L;
   private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .proxy(RiptideAuthNetwork.directProxySelector())
      .connectTimeout(Duration.ofSeconds(20L))
      .build();

   private static String resolveClientId() {
      String prop = System.getProperty("riptide.msauth.clientId");
      if (prop != null && !prop.isBlank()) {
         return prop.trim();
      } else {
         String env = System.getenv("RIPTIDE_MSAUTH_CLIENT_ID");
         return env != null && !env.isBlank() ? env.trim() : "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
      }
   }

   private RiptideMicrosoftLogin() {
   }

   public static String getRefreshToken(Consumer<String> callback) {
      RiptideMicrosoftLogin.callback = callback;
      codeVerifier = newCodeVerifier();
      oauthState = newCodeVerifier();
      startServer();
      String url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id="
         + CLIENT_ID
         + "&response_type=code&redirect_uri="
         + enc(redirectUri())
         + "&scope="
         + enc("XboxLive.SignIn XboxLive.offline_access")
         + "&code_challenge="
         + enc(codeChallenge(codeVerifier))
         + "&code_challenge_method=S256&state="
         + enc(oauthState)
         + "&prompt=select_account";
      Util.getPlatform().openUri(url);
      return url;
   }

   public static RiptideMicrosoftLogin.LoginData login(String refreshToken) {
      try {
         LOGIN_PERMITS.acquire();
      } catch (InterruptedException var10) {
         Thread.currentThread().interrupt();
         return RiptideMicrosoftLogin.LoginData.fail("Login interrupted");
      }

      RiptideMicrosoftLogin.LoginData wait;
      try {
         synchronized (LOGIN_SPACING) {
            long waitx = lastLoginStart + 400L - System.currentTimeMillis();
            if (waitx > 0L) {
               Thread.sleep(waitx);
            }

            lastLoginStart = System.currentTimeMillis();
         }

         return doLogin(refreshToken);
      } catch (InterruptedException var12) {
         Thread.currentThread().interrupt();
         wait = RiptideMicrosoftLogin.LoginData.fail("Login interrupted");
      } finally {
         LOGIN_PERMITS.release();
      }

      return wait;
   }

   private static RiptideMicrosoftLogin.LoginData doLogin(String refreshToken) {
      RiptideMicrosoftLogin.HttpReply tokenReply = httpPostForm(
         "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
         "client_id=" + CLIENT_ID + "&refresh_token=" + enc(refreshToken) + "&grant_type=refresh_token&scope=" + enc("XboxLive.SignIn XboxLive.offline_access")
      );
      JsonObject tokenResponse = tokenReply.json();
      if (tokenResponse != null && tokenResponse.has("access_token") && tokenResponse.has("refresh_token")) {
         String accessToken = tokenResponse.get("access_token").getAsString();
         refreshToken = tokenResponse.get("refresh_token").getAsString();
         RiptideMicrosoftLogin.HttpReply xblReply = httpPostJson(
            "https://user.auth.xboxlive.com/user/authenticate",
            "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d="
               + accessToken
               + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"
         );
         JsonObject xbl = xblReply.json();
         if (xblReply.ok() && xbl != null && xbl.has("Token")) {
            RiptideMicrosoftLogin.HttpReply xstsReply = httpPostJson(
               "https://xsts.auth.xboxlive.com/xsts/authorize",
               "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                  + xbl.get("Token").getAsString()
                  + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"
            );
            JsonObject xsts = xstsReply.json();
            if (xstsReply.ok() && xsts != null && xsts.has("Token")) {
               String uhs = extractUhs(xbl);
               if (uhs == null) {
                  uhs = extractUhs(xsts);
               }

               if (uhs == null) {
                  return RiptideMicrosoftLogin.LoginData.fail("Xbox Live response was malformed (no user hash)");
               } else {
                  RiptideMicrosoftLogin.HttpReply mcReply = httpPostJson(
                     "https://api.minecraftservices.com/authentication/login_with_xbox",
                     "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString() + "\"}"
                  );
                  JsonObject mc = mcReply.json();
                  if (mcReply.ok() && mc != null && mc.has("access_token")) {
                     String mcToken = mc.get("access_token").getAsString();
                     long expiresIn = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400L;
                     RiptideMicrosoftLogin.HttpReply profileReply = httpGetBearer("https://api.minecraftservices.com/minecraft/profile", mcToken);
                     JsonObject profile = profileReply.json();
                     if (profileReply.status() != 404 && profile != null && profile.has("id") && profile.has("name")) {
                        return new RiptideMicrosoftLogin.LoginData(
                           mcToken, refreshToken, profile.get("id").getAsString(), profile.get("name").getAsString(), expiresIn
                        );
                     } else {
                        String reason = profileReply.status() == 404
                           ? "This Microsoft account doesn't own Minecraft: Java Edition"
                           : "Couldn't read the Minecraft profile (" + httpDetail(profileReply) + ")";
                        riptide.RiptideClientAddon.LOG.warn("MS login: {}", reason);
                        return RiptideMicrosoftLogin.LoginData.fail(reason);
                     }
                  } else {
                     String reason = "Minecraft services login failed (" + httpDetail(mcReply) + ")";
                     riptide.RiptideClientAddon.LOG.warn("MS login: {}", reason);
                     return RiptideMicrosoftLogin.LoginData.fail(reason);
                  }
               }
            } else {
               String reason = xstsError(xsts);
               riptide.RiptideClientAddon.LOG.warn("MS login: XSTS failed ({}): {}", xstsReply.status(), reason);
               return RiptideMicrosoftLogin.LoginData.fail(reason);
            }
         } else {
            String reason = "Xbox Live authentication failed (" + httpDetail(xblReply) + ")";
            riptide.RiptideClientAddon.LOG.warn("MS login: {}", reason);
            return RiptideMicrosoftLogin.LoginData.fail(reason);
         }
      } else {
         String reason = tokenErrorMessage(tokenReply);
         riptide.RiptideClientAddon.LOG.warn("MS login: token refresh failed ({}): {}", tokenReply.status(), reason);
         return RiptideMicrosoftLogin.LoginData.fail(reason);
      }
   }

   private static String extractUhs(JsonObject xboxResponse) {
      try {
         return xboxResponse.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
      } catch (Exception var2) {
         return null;
      }
   }

   private static String httpDetail(RiptideMicrosoftLogin.HttpReply reply) {
      if (reply == null) {
         return "no response";
      } else {
         JsonObject body = reply.json();
         if (body != null) {
            for (String key : new String[]{"error_description", "message", "errorMessage", "error"}) {
               if (body.has(key)) {
                  try {
                     String value = body.get(key).getAsString();
                     if (value != null && !value.isBlank()) {
                        return "HTTP " + reply.status() + ": " + value.split("\\r?\\n")[0].trim();
                     }
                  } catch (Exception var7) {
                  }
               }
            }
         }

         return reply.status() < 0 ? "no response (network)" : "HTTP " + reply.status();
      }
   }

   private static String tokenErrorMessage(RiptideMicrosoftLogin.HttpReply reply) {
      JsonObject body = reply == null ? null : reply.json();
      if (body != null) {
         try {
            if (body.has("error_description") && !body.get("error_description").getAsString().isBlank()) {
               return "Microsoft: " + body.get("error_description").getAsString().split("\\r?\\n")[0].trim();
            }

            if (body.has("error") && !body.get("error").getAsString().isBlank()) {
               return "Microsoft error: " + body.get("error").getAsString();
            }
         } catch (Exception var3) {
         }
      }

      int status = reply == null ? -1 : reply.status();
      return status < 0 ? "Couldn't reach Microsoft (check your connection)" : "Microsoft token request failed (HTTP " + status + ")";
   }

   private static String xstsError(JsonObject xsts) {
      if (xsts == null) {
         return "Xbox authorization failed";
      } else {
         if (xsts.has("XErr")) {
            long code = xsts.get("XErr").getAsLong();
            if (code == 2148916233L) {
               return "This account has no Xbox profile — create one at xbox.com first";
            }

            if (code == 2148916235L) {
               return "Xbox Live isn't available in this account's country/region";
            }

            if (code == 2148916236L || code == 2148916237L) {
               return "This account needs adult verification";
            }

            if (code == 2148916238L) {
               return "Child account — an adult must add it to a Microsoft Family";
            }
         }

         return "Xbox authorization failed";
      }
   }

   private static String redirectUri() {
      return "http://127.0.0.1:9675";
   }

   private static String enc(String value) {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
   }

   private static String newCodeVerifier() {
      byte[] bytes = new byte[64];
      new SecureRandom().nextBytes(bytes);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   }

   private static String codeChallenge(String verifier) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
         return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
      } catch (Exception var2) {
         return verifier;
      }
   }

   private static Builder base(String url) {
      return HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(20L))
         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36")
         .header("Accept", "application/json");
   }

   private static RiptideMicrosoftLogin.HttpReply send(HttpRequest request) {
      try {
         HttpResponse<String> res = HTTP.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
         return new RiptideMicrosoftLogin.HttpReply(res.statusCode(), res.body());
      } catch (Exception var2) {
         return new RiptideMicrosoftLogin.HttpReply(-1, null);
      }
   }

   private static RiptideMicrosoftLogin.HttpReply httpPostForm(String url, String body) {
      return send(base(url).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
   }

   private static RiptideMicrosoftLogin.HttpReply httpPostJson(String url, String body) {
      return send(base(url).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
   }

   private static RiptideMicrosoftLogin.HttpReply httpGetBearer(String url, String bearer) {
      return send(base(url).header("Authorization", "Bearer " + bearer).GET().build());
   }

   private static void startServer() {
      if (server == null) {
         try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 9675), 0);
            server.createContext("/", RiptideMicrosoftLogin::handleRequest);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
         } catch (IOException var1) {
            stopServer();
            RiptideClientMessaging.sendPrefixed("Failed to start Microsoft login server.");
         }
      }
   }

   public static void stopServer() {
      if (server != null) {
         server.stop(0);
         server = null;
         callback = null;
         oauthState = "";
      }
   }

   private static void handleRequest(HttpExchange request) throws IOException {
      if ("GET".equals(request.getRequestMethod())) {
         List<RiptideMicrosoftLogin.QueryPair> query = parseURL(request.getRequestURI().getRawQuery());
         String code = null;
         String returnedState = null;

         for (RiptideMicrosoftLogin.QueryPair pair : query) {
            if ("code".equals(pair.name())) {
               code = pair.value();
            } else if ("state".equals(pair.name())) {
               returnedState = pair.value();
            }
         }

         boolean ok = code != null && !oauthState.isBlank() && oauthState.equals(returnedState);
         if (ok) {
            handleCode(code);
         }

         if (!ok) {
            writeText(request, "Cannot authenticate.");
            Consumer<String> current = callback;
            if (current != null) {
               current.accept(null);
            }
         } else {
            writeText(request, "You may now close this page.");
         }
      }

      stopServer();
   }

   private static void handleCode(String code) {
      RiptideMicrosoftLogin.HttpReply reply = httpPostForm(
         "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
         "client_id="
            + CLIENT_ID
            + "&code="
            + enc(code)
            + "&grant_type=authorization_code&redirect_uri="
            + enc(redirectUri())
            + "&scope="
            + enc("XboxLive.SignIn XboxLive.offline_access")
            + "&code_verifier="
            + enc(codeVerifier)
      );
      JsonObject response = reply.json();
      Consumer<String> current = callback;
      if (current != null) {
         current.accept(response != null && response.has("refresh_token") ? response.get("refresh_token").getAsString() : null);
      }
   }

   private static void writeText(HttpExchange request, String text) throws IOException {
      byte[] responseBody = text.getBytes(StandardCharsets.UTF_8);
      request.sendResponseHeaders(200, responseBody.length);

      try (OutputStream output = request.getResponseBody()) {
         output.write(responseBody);
      }
   }

   private static List<RiptideMicrosoftLogin.QueryPair> parseURL(String string) {
      List<RiptideMicrosoftLogin.QueryPair> query = new ArrayList<>();
      if (string == null) {
         return query;
      } else {
         char[] buf = string.toCharArray();
         int i = 0;

         while (i < buf.length) {
            StringBuilder name = new StringBuilder();

            StringBuilder value;
            for (value = new StringBuilder(); i < buf.length && buf[i] != '&' && buf[i] != ';' && buf[i] != '='; i++) {
               name.append(buf[i]);
            }

            if (i < buf.length) {
               char ch = buf[i++];
               if (ch == '=') {
                  while (i < buf.length) {
                     if (buf[i] == '&' || buf[i] == ';') {
                        i++;
                        break;
                     }

                     value.append(buf[i]);
                     i++;
                  }
               }
            }

            if (!name.isEmpty()) {
               query.add(new RiptideMicrosoftLogin.QueryPair(urlDecode(name.toString()), urlDecode(value.toString())));
            }
         }

         return query;
      }
   }

   private static String urlDecode(String s) {
      if (s == null) {
         return null;
      } else {
         ByteBuffer bb = ByteBuffer.allocate(s.length());
         CharBuffer cb = CharBuffer.wrap(s);

         while (cb.hasRemaining()) {
            char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
               char uc = cb.get();
               char lc = cb.get();
               int u = Character.digit(uc, 16);
               int l = Character.digit(lc, 16);
               if (u != -1 && l != -1) {
                  bb.put((byte)((u << 4) + l));
               } else {
                  bb.put((byte)37);
                  bb.put((byte)uc);
                  bb.put((byte)lc);
               }
            } else if (c == '+') {
               bb.put((byte)32);
            } else {
               bb.put((byte)c);
            }
         }

         bb.flip();
         return StandardCharsets.UTF_8.decode(bb).toString();
      }
   }

   private record HttpReply(int status, String body) {
      boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      JsonObject json() {
         try {
            return this.body != null && !this.body.isBlank() ? JsonParser.parseString(this.body).getAsJsonObject() : null;
         } catch (Exception var2) {
            return null;
         }
      }
   }

   public static final class LoginData {
      public final String mcToken;
      public final String newRefreshToken;
      public final String uuid;
      public final String username;
      public final String error;
      public final long expiresInSeconds;

      public LoginData() {
         this(null, null, null, null, 0L, null);
      }

      public LoginData(String mcToken, String newRefreshToken, String uuid, String username, long expiresInSeconds) {
         this(mcToken, newRefreshToken, uuid, username, expiresInSeconds, null);
      }

      private LoginData(String mcToken, String newRefreshToken, String uuid, String username, long expiresInSeconds, String error) {
         this.mcToken = mcToken;
         this.newRefreshToken = newRefreshToken;
         this.uuid = uuid;
         this.username = username;
         this.expiresInSeconds = expiresInSeconds;
         this.error = error;
      }

      static RiptideMicrosoftLogin.LoginData fail(String error) {
         return new RiptideMicrosoftLogin.LoginData(null, null, null, null, 0L, error);
      }

      public boolean isGood() {
         return this.mcToken != null;
      }

      public long expiresAtEpochMs() {
         return this.expiresInSeconds > 0L ? System.currentTimeMillis() + this.expiresInSeconds * 1000L : 0L;
      }
   }

   private record QueryPair(String name, String value) {
   }
}
