package riptide.util;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class RiptideMeteorImport {
   private static final String STATE_FILE = "riptide-meteor-import.nbt";
   private static final String METEOR_DIR = "meteor-client";
   private static final int PROXY_IMPORT_VERSION = 1;
   private static volatile boolean started;

   private RiptideMeteorImport() {
   }

   public static void ensureImported() {
      if (!started) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.gameDirectory != null) {
            synchronized (RiptideMeteorImport.class) {
               if (started) {
                  return;
               }

               started = true;
            }

            try {
               runImport(mc.gameDirectory);
            } catch (Throwable var3) {
               riptide.RiptideClientAddon.LOG.warn("Meteor import failed", var3);
            }
         }
      }
   }

   private static void runImport(File gameDir) {
      File meteorDir = new File(gameDir, "meteor-client");
      if (meteorDir.isDirectory()) {
         RiptideMeteorImport.State state = loadState(gameDir);
         boolean upgradeProxyImport = state.proxyImportVersion < 1;
         if (upgradeProxyImport) {
            state.proxiesSig = "";
         }

         boolean changed = importAccounts(new File(meteorDir, "accounts.nbt"), state);
         changed |= importProxies(new File(meteorDir, "proxies.nbt"), state);
         if (upgradeProxyImport) {
            state.proxyImportVersion = 1;
            changed = true;
         }

         if (changed) {
            saveState(gameDir, state);
         }
      }
   }

   private static boolean importAccounts(File file, RiptideMeteorImport.State state) {
      if (!file.isFile()) {
         return false;
      } else {
         String signature = fileSignature(file);
         if (signature.equals(state.accountsSig)) {
            return false;
         } else {
            try {
               CompoundTag root = NbtIo.read(file.toPath());
               if (root != null) {
                  for (Tag element : root.getListOrEmpty("accounts")) {
                     if (element instanceof CompoundTag tag) {
                        RiptideAccount account = mapAccount(tag);
                        if (account != null && state.importedAccounts.add(accountKey(account))) {
                           RiptideAccountManager.get().add(account);
                        }
                     }
                  }
               }
            } catch (Exception var8) {
               riptide.RiptideClientAddon.LOG.warn("Failed to read Meteor accounts", var8);
               return !state.importedAccounts.isEmpty();
            }

            state.accountsSig = signature;
            return true;
         }
      }
   }

   private static RiptideAccount mapAccount(CompoundTag tag) {
      String type = tag.getStringOr("type", "");
      String name = tag.getStringOr("name", "");
      String token = tag.getStringOr("token", "");
      String cacheUser = "";
      String cacheUuid = "";
      Optional<CompoundTag> cache = tag.getCompound("cache");
      if (cache.isPresent()) {
         cacheUser = cache.get().getStringOr("username", "");
         cacheUuid = cache.get().getStringOr("uuid", "");
      }

      RiptideAccount account = new RiptideAccount();
      account.uuid = cacheUuid;
      account.username = cacheUser;
      switch (type) {
         case "Cracked":
            account.type = RiptideAccountType.Cracked;
            account.label = name;
            if (account.username.isBlank()) {
               account.username = name;
            }
            break;
         case "Microsoft":
            account.type = RiptideAccountType.Microsoft;
            account.label = name;
            break;
         case "Session":
            account.type = RiptideAccountType.Session;
            account.label = name;
            account.token = token;
            break;
         case "TheAltening":
            account.type = RiptideAccountType.TheAltening;
            account.label = name;
            account.token = token.isBlank() ? name : token;
            break;
         default:
            return null;
      }

      boolean usable = !account.label.isBlank() || !account.username.isBlank() || !account.token.isBlank();
      return usable ? account : null;
   }

   private static String accountKey(RiptideAccount account) {
      String uuid = account.uuid == null ? "" : account.uuid.trim().toLowerCase(Locale.ROOT);
      if (!uuid.isEmpty()) {
         return account.type.name() + "|u|" + uuid;
      } else {
         String identity = account.username != null && !account.username.isBlank() ? account.username : account.label;
         return account.type.name() + "|n|" + (identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT));
      }
   }

   private static boolean importProxies(File file, RiptideMeteorImport.State state) {
      if (!file.isFile()) {
         return false;
      } else {
         String signature = fileSignature(file);
         if (signature.equals(state.proxiesSig)) {
            return false;
         } else {
            try {
               CompoundTag root = NbtIo.read(file.toPath());
               if (root != null) {
                  for (Tag element : root.getListOrEmpty("proxies")) {
                     if (element instanceof CompoundTag tag) {
                        RiptideProxy proxy = mapProxy(tag);
                        if (proxy != null && proxy.isValid() && state.importedProxies.add(proxyKey(proxy))) {
                           RiptideProxyManager.get().add(proxy);
                        }
                     }
                  }
               }
            } catch (Exception var8) {
               riptide.RiptideClientAddon.LOG.warn("Failed to read Meteor proxies", var8);
               return !state.importedProxies.isEmpty();
            }

            state.proxiesSig = signature;
            return true;
         }
      }
   }

   private static RiptideProxy mapProxy(CompoundTag proxyTag) {
      Optional<CompoundTag> settings = proxyTag.getCompound("settings");
      return settings.isPresent() ? mapProxyFromSettings(settings.get()) : mapProxyFlat(proxyTag);
   }

   private static RiptideProxy mapProxyFromSettings(CompoundTag settings) {
      Map<String, CompoundTag> byName = new HashMap<>();

      for (Tag groupTag : settings.getListOrEmpty("groups")) {
         if (groupTag instanceof CompoundTag group) {
            for (Tag settingTag : group.getListOrEmpty("settings")) {
               if (settingTag instanceof CompoundTag setting) {
                  String settingName = setting.getStringOr("name", "");
                  if (!settingName.isEmpty()) {
                     byName.put(settingName, setting);
                  }
               }
            }
         }
      }

      return buildImportedProxy(
         settingString(byName, "address", ""),
         settingInt(byName, "port", 0),
         settingString(byName, "name", ""),
         settingString(byName, "username", ""),
         settingString(byName, "password", ""),
         settingString(byName, "type", "Socks5")
      );
   }

   private static RiptideProxy mapProxyFlat(CompoundTag tag) {
      return buildImportedProxy(
         tag.getStringOr("address", ""),
         tag.getIntOr("port", 0),
         tag.getStringOr("name", ""),
         tag.getStringOr("username", ""),
         tag.getStringOr("password", ""),
         tag.getStringOr("type", "Socks5")
      );
   }

   private static RiptideProxy buildImportedProxy(String address, int port, String name, String username, String password, String type) {
      if (address != null && !address.isBlank() && port > 0) {
         RiptideProxy proxy = new RiptideProxy();
         proxy.address = address.trim();
         proxy.port = port;
         proxy.name = name == null ? "" : name;
         proxy.username = username == null ? "" : username;
         proxy.password = password == null ? "" : password;
         proxy.type = type != null && type.toLowerCase(Locale.ROOT).contains("4") ? RiptideProxyType.Socks4 : RiptideProxyType.Socks5;
         proxy.enabled = false;
         return proxy;
      } else {
         return null;
      }
   }

   private static String settingString(Map<String, CompoundTag> byName, String key, String def) {
      CompoundTag setting = byName.get(key);
      return setting == null ? def : setting.getStringOr("value", def);
   }

   private static int settingInt(Map<String, CompoundTag> byName, String key, int def) {
      CompoundTag setting = byName.get(key);
      return setting == null ? def : setting.getIntOr("value", def);
   }

   private static String proxyKey(RiptideProxy proxy) {
      return proxy.type.name() + "|" + proxy.address.trim().toLowerCase(Locale.ROOT) + "|" + proxy.port;
   }

   private static RiptideMeteorImport.State loadState(File gameDir) {
      RiptideMeteorImport.State state = new RiptideMeteorImport.State();
      File file = new File(gameDir, "riptide-meteor-import.nbt");
      if (!file.isFile()) {
         return state;
      } else {
         try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) {
               return state;
            }

            state.accountsSig = tag.getStringOr("accountsSig", "");
            state.proxiesSig = tag.getStringOr("proxiesSig", "");
            state.proxyImportVersion = tag.getIntOr("proxyImportVersion", 0);
            readKeys(tag.getListOrEmpty("importedAccounts"), state.importedAccounts);
            readKeys(tag.getListOrEmpty("importedProxies"), state.importedProxies);
         } catch (Exception var4) {
            riptide.RiptideClientAddon.LOG.warn("Failed to read Meteor import state", var4);
         }

         return state;
      }
   }

   private static void saveState(File gameDir, RiptideMeteorImport.State state) {
      try {
         CompoundTag tag = new CompoundTag();
         tag.putString("accountsSig", state.accountsSig);
         tag.putString("proxiesSig", state.proxiesSig);
         tag.putInt("proxyImportVersion", state.proxyImportVersion);
         tag.put("importedAccounts", writeKeys(state.importedAccounts));
         tag.put("importedProxies", writeKeys(state.importedProxies));
         NbtIo.write(tag, new File(gameDir, "riptide-meteor-import.nbt").toPath());
      } catch (Exception var3) {
         riptide.RiptideClientAddon.LOG.warn("Failed to save Meteor import state", var3);
      }
   }

   private static void readKeys(ListTag list, Set<String> into) {
      for (Tag element : list) {
         String value = element.asString().orElse("");
         if (!value.isEmpty()) {
            into.add(value);
         }
      }
   }

   private static ListTag writeKeys(Set<String> keys) {
      ListTag list = new ListTag();

      for (String key : keys) {
         list.add(StringTag.valueOf(key));
      }

      return list;
   }

   private static String fileSignature(File file) {
      return file.lastModified() + ":" + file.length();
   }

   private static final class State {
      String accountsSig = "";
      String proxiesSig = "";
      int proxyImportVersion = 0;
      final Set<String> importedAccounts = new LinkedHashSet<>();
      final Set<String> importedProxies = new LinkedHashSet<>();
   }
}
