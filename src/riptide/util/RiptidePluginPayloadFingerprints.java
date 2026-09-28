package riptide.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RiptidePluginPayloadFingerprints {
   private static final int MAX_CHANNELS_PER_SERVER = 512;
   private static final Pattern CHANNEL_TOKEN = Pattern.compile("(?i)\\b[a-z0-9_.-]{2,64}:[a-z0-9_./-]{1,128}\\b");
   private static final Map<String, RiptidePluginPayloadFingerprints.State> STATES = new HashMap<>();
   private static final Map<String, String> NAMESPACE_ALIASES = Map.ofEntries(
      Map.entry("vv", "ViaVersion"),
      Map.entry("viaversion", "ViaVersion"),
      Map.entry("viabackwards", "ViaBackwards"),
      Map.entry("viarewind", "ViaRewind"),
      Map.entry("bungeecord", "BungeeCord"),
      Map.entry("velocity", "Velocity"),
      Map.entry("geyser", "Geyser"),
      Map.entry("floodgate", "Floodgate"),
      Map.entry("worldedit", "WorldEdit"),
      Map.entry("fawe", "FAWE"),
      Map.entry("fastasyncworldedit", "FastAsyncWorldEdit"),
      Map.entry("worldguard", "WorldGuard"),
      Map.entry("coreprotect", "CoreProtect"),
      Map.entry("claimmaps", "ClaimMessenger"),
      Map.entry("claimmessenger", "ClaimMessenger"),
      Map.entry("openpartiesandclaims", "Open Parties and Claims"),
      Map.entry("openpac", "Open Parties and Claims"),
      Map.entry("ftbchunks", "FTB Chunks"),
      Map.entry("ftbteams", "FTB Teams"),
      Map.entry("griefprevention", "GriefPrevention"),
      Map.entry("griefdefender", "GriefDefender"),
      Map.entry("residence", "Residence"),
      Map.entry("lands", "Lands"),
      Map.entry("towny", "Towny"),
      Map.entry("townyadvanced", "Towny"),
      Map.entry("plotsquared", "PlotSquared"),
      Map.entry("bentobox", "BentoBox"),
      Map.entry("huskclaims", "HuskClaims"),
      Map.entry("husktowns", "HuskTowns"),
      Map.entry("protectionstones", "ProtectionStones"),
      Map.entry("redprotect", "RedProtect"),
      Map.entry("claimchunk", "ClaimChunk"),
      Map.entry("lwc", "LWC"),
      Map.entry("lwcx", "LWCX"),
      Map.entry("factions", "Factions"),
      Map.entry("kingdoms", "Kingdoms"),
      Map.entry("kingdomsx", "KingdomsX"),
      Map.entry("luckperms", "LuckPerms"),
      Map.entry("tab", "TAB"),
      Map.entry("sr", "SkinsRestorer"),
      Map.entry("skinsrestorer", "SkinsRestorer"),
      Map.entry("authme", "AuthMeReloaded"),
      Map.entry("authmereloaded", "AuthMeReloaded"),
      Map.entry("librelogin", "LibreLogin"),
      Map.entry("nlogin", "nLogin"),
      Map.entry("openlogin", "OpeNLogin"),
      Map.entry("limboauth", "LimboAuth"),
      Map.entry("loginsecurity", "LoginSecurity"),
      Map.entry("fastlogin", "FastLogin"),
      Map.entry("bungeeguard", "BungeeGuard"),
      Map.entry("emotecraft", "Emotecraft"),
      Map.entry("journeymap", "JourneyMap"),
      Map.entry("worldinfo", "WorldInfo"),
      Map.entry("xaerominimap", "Xaero Minimap"),
      Map.entry("xaeroworldmap", "Xaero World Map"),
      Map.entry("voxelmap", "VoxelMap"),
      Map.entry("cardinal-components", "Cardinal Components API"),
      Map.entry("cca", "Cardinal Components API"),
      Map.entry("polymer", "Polymer"),
      Map.entry("quickshop", "QuickShop-Hikari"),
      Map.entry("quickshophikari", "QuickShop-Hikari"),
      Map.entry("quickshop-hikari", "QuickShop-Hikari"),
      Map.entry("quickshopreremake", "QuickShop Reremake"),
      Map.entry("economyshopgui", "EconomyShopGUI"),
      Map.entry("economyshopguipremium", "EconomyShopGUI Premium"),
      Map.entry("economyshopguiplus", "EconomyShopGUI"),
      Map.entry("esgui", "EconomyShopGUI"),
      Map.entry("shopguiplus", "ShopGUI+"),
      Map.entry("shopgui", "ShopGUI+"),
      Map.entry("bossshoppro", "BossShopPro"),
      Map.entry("bossshop", "BossShop"),
      Map.entry("chestshop", "ChestShop"),
      Map.entry("shopchest", "ShopChest"),
      Map.entry("shopkeepers", "Shopkeepers"),
      Map.entry("ultimateshop", "UltimateShop"),
      Map.entry("excellentshop", "ExcellentShop"),
      Map.entry("zshop", "zShop"),
      Map.entry("guishop", "GUIShop"),
      Map.entry("simpleshop", "SimpleShop"),
      Map.entry("simpleshopgui", "SimpleShopGUI"),
      Map.entry("shopx", "Shop X"),
      Map.entry("bettershop", "BetterShop"),
      Map.entry("bettershops", "Better Shops"),
      Map.entry("deluxemenus", "DeluxeMenus"),
      Map.entry("commandpanels", "CommandPanels"),
      Map.entry("interactivechat", "InteractiveChat"),
      Map.entry("interchat", "InteractiveChat"),
      Map.entry("auctionmaster", "AuctionMaster"),
      Map.entry("crazyauctions", "CrazyAuctions"),
      Map.entry("zauctionhouse", "zAuctionHouse"),
      Map.entry("playerauctions", "PlayerAuctions"),
      Map.entry("auctionguiplus", "AuctionGUI+"),
      Map.entry("excellentauctionhouse", "ExcellentAuctionHouse"),
      Map.entry("excellentauctions", "ExcellentAuctions"),
      Map.entry("axauctions", "AxAuctions"),
      Map.entry("axauctionhouse", "AxAuctionHouse"),
      Map.entry("nexusauctionhouse", "NexusAuctionHouse"),
      Map.entry("deluxeauctions", "DeluxeAuctions"),
      Map.entry("outauction", "OutAuction"),
      Map.entry("fauction", "FAuction"),
      Map.entry("auctionhouseplus", "AuctionHousePlus"),
      Map.entry("crazycrates", "CrazyCrates"),
      Map.entry("crazycrate", "CrazyCrates"),
      Map.entry("excellentcrates", "ExcellentCrates"),
      Map.entry("excellentcrate", "ExcellentCrates"),
      Map.entry("goldencrates", "GoldenCrates"),
      Map.entry("goldencrate", "GoldenCrates"),
      Map.entry("cratereloaded", "CrateReloaded"),
      Map.entry("magiccrates", "MagicCrates"),
      Map.entry("magiccrate", "MagicCrates"),
      Map.entry("simplelootcrates", "SimpleLootCrates"),
      Map.entry("simplelootcrate", "SimpleLootCrates"),
      Map.entry("axcrates", "AxCrates"),
      Map.entry("azcrates", "AzCrates"),
      Map.entry("cratesplus", "CratesPlus"),
      Map.entry("cratekeys", "CrateKeys"),
      Map.entry("mysterycrates", "MysteryCrates"),
      Map.entry("phoenixcrates", "PhoenixCrates"),
      Map.entry("advancedcrates", "AdvancedCrates"),
      Map.entry("specializedcrates", "SpecializedCrates"),
      Map.entry("oraxen", "Oraxen"),
      Map.entry("itemsadder", "ItemsAdder"),
      Map.entry("nexo", "Nexo"),
      Map.entry("modelengine", "ModelEngine"),
      Map.entry("mythicmobs", "MythicMobs"),
      Map.entry("mythiclib", "MythicLib"),
      Map.entry("mmoitems", "MMOItems"),
      Map.entry("placeholderapi", "PlaceholderAPI"),
      Map.entry("protocollib", "ProtocolLib"),
      Map.entry("packetevents", "PacketEvents"),
      Map.entry("axiom", "Axiom Paper Plugin"),
      Map.entry("vault", "Vault"),
      Map.entry("essentials", "EssentialsX"),
      Map.entry("essentialsx", "EssentialsX"),
      Map.entry("cmi", "CMI"),
      Map.entry("citizens", "Citizens"),
      Map.entry("fancyholograms", "FancyHolograms"),
      Map.entry("fancynpcs", "FancyNpcs"),
      Map.entry("voicechat", "Simple Voice Chat"),
      Map.entry("vc", "Simple Voice Chat"),
      Map.entry("plasmo", "Plasmo Voice"),
      Map.entry("plasmovoice", "Plasmo Voice"),
      Map.entry("minecraftafk", "MinecraftAFK"),
      Map.entry("donutaddon", "DonutAddon"),
      Map.entry("donutsmp", "DonutSMP"),
      Map.entry("nighthawk", "Nighthawk"),
      Map.entry("feather", "Feather"),
      Map.entry("proantitab", "ProAntiTab"),
      Map.entry("pat", "ProAntiTab"),
      Map.entry("consumable_optimizer", "Consumable Optimizer"),
      Map.entry("clientcrystal", "FastCrystal"),
      Map.entry("fastcrystal", "FastCrystal"),
      Map.entry("optiplus", "OptiPlus"),
      Map.entry("optiplus-crystal", "OptiPlus Crystal"),
      Map.entry("optiplus-anchor", "OptiPlus Anchor"),
      Map.entry("fabric", "Fabric"),
      Map.entry("fabric-networking-api-v1", "Fabric Networking"),
      Map.entry("fabric-networking-v0", "Fabric Networking"),
      Map.entry("labymod", "LabyMod"),
      Map.entry("labymod3", "LabyMod"),
      Map.entry("essential", "Essential")
   );
   private static final Set<String> NON_PLUGIN_NAMESPACES = Set.of("minecraft", "brigadier", "riptide", "riptidx", "c");
   private static final Set<String> DISCOVERY_CHANNELS = Set.of(
      "minecraft:register", "minecraft:unregister", "register", "unregister", "minecraft:brand", "brand", "bungeecord", "bungeecord:main"
   );

   private RiptidePluginPayloadFingerprints() {
   }

   public static boolean shouldObserveChannel(String channel) {
      String normalized = normalizeChannel(channel);
      if (normalized.isBlank()) {
         return false;
      } else if (DISCOVERY_CHANNELS.contains(normalized)) {
         return true;
      } else {
         int colon = normalized.indexOf(58);
         if (colon <= 0) {
            return isLegacyInterestingChannel(normalized);
         } else {
            String namespace = normalized.substring(0, colon);
            return !NON_PLUGIN_NAMESPACES.contains(namespace);
         }
      }
   }

   public static synchronized boolean observe(String serverAddress, String brand, RiptidePayloadSupport.PayloadSnapshot snapshot) {
      if (snapshot == null) {
         return false;
      } else if (!"S2C".equalsIgnoreCase(snapshot.direction())) {
         return false;
      } else {
         String addressKey = normalizeServerAddress(serverAddress);
         if (addressKey.isBlank()) {
            return false;
         } else {
            RiptidePluginPayloadFingerprints.State state = STATES.computeIfAbsent(addressKey, unused -> new RiptidePluginPayloadFingerprints.State());
            state.brand = normalizeBrand(brand);
            boolean changed = false;
            String channel = normalizeChannel(snapshot.channel());
            if (!channel.isBlank() && shouldObserveChannel(channel)) {
               changed |= state.addChannel(channel, snapshot.direction());
            }

            if (isRegistrationChannel(channel)) {
               for (String registered : extractRegisteredChannels(snapshot.rawBytes())) {
                  if (shouldObserveChannel(registered)) {
                     changed |= state.addRegisteredChannel(registered, snapshot.direction());
                  }
               }
            } else {
               for (String embedded : extractChannelTokens(snapshot.rawBytes())) {
                  if (shouldObserveChannel(embedded)) {
                     changed |= state.addEmbeddedChannel(embedded, snapshot.direction());
                  }
               }
            }

            if (changed) {
               state.revision++;
            }

            return changed;
         }
      }
   }

   public static synchronized List<RiptidePluginPayloadFingerprints.PluginFingerprint> pluginsFor(String serverAddress, String brand) {
      RiptidePluginPayloadFingerprints.State state = STATES.get(normalizeServerAddress(serverAddress));
      if (state == null) {
         return List.of();
      } else {
         Map<String, RiptidePluginPayloadFingerprints.PluginAccumulator> byPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

         for (RiptidePluginPayloadFingerprints.ChannelStats stats : state.allChannelStats()) {
            if (stats.hasServerEvidence()) {
               RiptidePluginPayloadFingerprints.PluginIdentity identity = pluginFromChannel(stats.channel);
               if (identity != null && !identity.displayName().isBlank() && !identity.key().isBlank()) {
                  RiptidePluginPayloadFingerprints.PluginAccumulator acc = byPlugin.computeIfAbsent(
                     identity.key(), unused -> new RiptidePluginPayloadFingerprints.PluginAccumulator(identity.displayName(), identity.key())
                  );
                  acc.add(stats);
               }
            }
         }

         if (byPlugin.isEmpty()) {
            return List.of();
         } else {
            List<RiptidePluginPayloadFingerprints.PluginFingerprint> out = new ArrayList<>();

            for (RiptidePluginPayloadFingerprints.PluginAccumulator acc : byPlugin.values()) {
               out.add(acc.toFingerprint());
            }

            return out;
         }
      }
   }

   public static synchronized long revisionFor(String serverAddress, String brand) {
      RiptidePluginPayloadFingerprints.State state = STATES.get(normalizeServerAddress(serverAddress));
      return state == null ? 0L : state.revision;
   }

   public static synchronized String signatureFor(String serverAddress, String brand) {
      RiptidePluginPayloadFingerprints.State state = STATES.get(normalizeServerAddress(serverAddress));
      if (state == null) {
         return "";
      } else {
         List<String> channels = new ArrayList<>(state.allChannels());
         channels.sort(String.CASE_INSENSITIVE_ORDER);
         if (channels.size() > 96) {
            channels = new ArrayList<>(channels.subList(0, 96));
         }

         return String.join(",", channels).toLowerCase(Locale.ROOT);
      }
   }

   public static synchronized void clearSession() {
      STATES.clear();
   }

   private static boolean isRegistrationChannel(String channel) {
      String normalized = normalizeChannel(channel);
      return "minecraft:register".equals(normalized)
         || "register".equals(normalized)
         || "minecraft:unregister".equals(normalized)
         || "unregister".equals(normalized);
   }

   private static boolean isLegacyInterestingChannel(String channel) {
      String normalized = normalizeChannel(channel);
      return "bungeecord".equals(normalized) || "register".equals(normalized) || "unregister".equals(normalized);
   }

   private static List<String> extractRegisteredChannels(byte[] rawBytes) {
      if (rawBytes != null && rawBytes.length != 0) {
         LinkedHashSet<String> channels = new LinkedHashSet<>();
         String text = new String(rawBytes, StandardCharsets.UTF_8);

         for (String part : text.split("[\\u0000\\r\\n\\t ,;]+")) {
            String token = normalizeChannel(part);
            if (!token.isBlank() && (token.indexOf(58) > 0 || isLegacyInterestingChannel(token))) {
               channels.add(token);
            }
         }

         channels.addAll(extractChannelTokens(rawBytes));
         return List.copyOf(channels);
      } else {
         return List.of();
      }
   }

   private static List<String> extractChannelTokens(byte[] rawBytes) {
      if (rawBytes != null && rawBytes.length != 0) {
         String text = new String(rawBytes, StandardCharsets.UTF_8);
         Matcher matcher = CHANNEL_TOKEN.matcher(text);
         LinkedHashSet<String> channels = new LinkedHashSet<>();

         while (matcher.find()) {
            String channel = normalizeChannel(matcher.group());
            if (!channel.isBlank()) {
               channels.add(channel);
            }

            if (channels.size() >= 512) {
               break;
            }
         }

         return List.copyOf(channels);
      } else {
         return List.of();
      }
   }

   private static RiptidePluginPayloadFingerprints.PluginIdentity pluginFromChannel(String channel) {
      String normalized = normalizeChannel(channel);
      if (normalized.isBlank()) {
         return null;
      } else if ("bungeecord".equals(normalized) || "bungeecord:main".equals(normalized)) {
         return new RiptidePluginPayloadFingerprints.PluginIdentity("BungeeCord", "bungeecord");
      } else if (!"register".equals(normalized) && !"unregister".equals(normalized)) {
         int colon = normalized.indexOf(58);
         if (colon <= 0) {
            return null;
         } else {
            String namespace = normalized.substring(0, colon);
            if (NON_PLUGIN_NAMESPACES.contains(namespace)) {
               return null;
            } else {
               String alias = NAMESPACE_ALIASES.get(namespace);
               if (alias != null) {
                  return new RiptidePluginPayloadFingerprints.PluginIdentity(alias, namespace);
               } else {
                  String presetLabel = pluginLabelFromPreset(normalized);
                  return presetLabel != null && !presetLabel.isBlank()
                     ? new RiptidePluginPayloadFingerprints.PluginIdentity(presetLabel, namespace)
                     : new RiptidePluginPayloadFingerprints.PluginIdentity(humanizeNamespace(namespace), namespace);
               }
            }
         }
      } else {
         return null;
      }
   }

   private static String pluginLabelFromPreset(String channel) {
      for (RiptidePayloadChannelListeners.Preset preset : RiptidePayloadChannelListeners.presetCatalog()) {
         if (RiptidePayloadChannelListeners.presetCanIdentifyPlugin(preset) && RiptidePayloadChannelListeners.presetMatchesChannel(preset, channel)) {
            return preset.label();
         }
      }

      return null;
   }

   private static String humanizeNamespace(String namespace) {
      String clean = namespace == null ? "" : namespace.trim();
      if (clean.isBlank()) {
         return "";
      } else {
         String[] parts = clean.split("[_\\-.]+");
         StringBuilder sb = new StringBuilder();

         for (String part : parts) {
            if (!part.isBlank()) {
               if (!sb.isEmpty()) {
                  sb.append(' ');
               }

               if (part.length() <= 3 && part.chars().allMatch(Character::isLetter)) {
                  sb.append(part.toUpperCase(Locale.ROOT));
               } else {
                  sb.append(Character.toUpperCase(part.charAt(0)));
                  if (part.length() > 1) {
                     sb.append(part.substring(1));
                  }
               }
            }
         }

         return sb.isEmpty() ? clean : sb.toString();
      }
   }

   private static String normalizeChannel(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   private static String normalizeServerAddress(String address) {
      return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
   }

   private static String normalizeBrand(String brand) {
      return brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
   }

   public static String canonicalPluginKey(String value) {
      if (value == null) {
         return "";
      } else {
         String clean = value.trim().toLowerCase(Locale.ROOT);
         if (clean.isBlank()) {
            return "";
         } else {
            int colon = clean.indexOf(58);
            if (colon > 0) {
               clean = clean.substring(0, colon);
            }

            String alias = NAMESPACE_ALIASES.get(clean);
            return alias != null ? compactKey(alias) : compactKey(clean);
         }
      }
   }

   public static List<String> probableNamespacesForPlugin(String pluginName) {
      String canonical = canonicalPluginKey(pluginName);
      if (canonical.isBlank()) {
         return List.of();
      } else {
         LinkedHashSet<String> namespaces = new LinkedHashSet<>();

         for (Entry<String, String> entry : NAMESPACE_ALIASES.entrySet()) {
            if (canonicalPluginKey(entry.getKey()).equals(canonical) || canonicalPluginKey(entry.getValue()).equals(canonical)) {
               namespaces.add(entry.getKey());
            }
         }

         String fromName = namespaceFromDisplay(pluginName);
         if (!fromName.isBlank() && !NON_PLUGIN_NAMESPACES.contains(fromName)) {
            namespaces.add(fromName);
         }

         return List.copyOf(namespaces);
      }
   }

   private static String compactKey(String value) {
      String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char ch = text.charAt(i);
         if (Character.isLetterOrDigit(ch)) {
            out.append(ch);
         }
      }

      return out.toString();
   }

   private static String namespaceFromDisplay(String pluginName) {
      if (pluginName == null) {
         return "";
      } else {
         String clean = pluginName.trim().toLowerCase(Locale.ROOT);
         if (clean.isBlank()) {
            return "";
         } else {
            int colon = clean.indexOf(58);
            if (colon > 0) {
               clean = clean.substring(0, colon);
            }

            clean = clean.replace('+', 'p');
            StringBuilder sb = new StringBuilder(clean.length());
            boolean lastWasSeparator = false;

            for (int i = 0; i < clean.length(); i++) {
               char ch = clean.charAt(i);
               if (Character.isLetterOrDigit(ch)) {
                  sb.append(ch);
                  lastWasSeparator = false;
               } else if ((ch == '_' || ch == '-' || ch == '.' || Character.isWhitespace(ch)) && !lastWasSeparator && !sb.isEmpty()) {
                  sb.append('_');
                  lastWasSeparator = true;
               }
            }

            while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '_') {
               sb.deleteCharAt(sb.length() - 1);
            }

            return sb.toString();
         }
      }
   }

   private static final class ChannelStats {
      final String channel;
      int liveCount;
      int registeredCount;
      int embeddedCount;
      final Set<String> directions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      ChannelStats(String channel) {
         this.channel = RiptidePluginPayloadFingerprints.normalizeChannel(channel);
      }

      void add(String source, String direction) {
         switch (source) {
            case "registered":
               this.registeredCount++;
               break;
            case "embedded":
               this.embeddedCount++;
               break;
            default:
               this.liveCount++;
         }

         if (direction != null && !direction.isBlank()) {
            this.directions.add(direction.trim().toUpperCase(Locale.ROOT));
         }
      }

      int score() {
         return this.liveCount * 4 + this.registeredCount * 3 + this.embeddedCount;
      }

      int sourceRank() {
         return this.liveCount > 0 ? 4 : (this.registeredCount > 0 ? 3 : (this.embeddedCount > 0 ? 1 : 0));
      }

      String bestSource() {
         return this.liveCount > 0 ? "live" : (this.registeredCount > 0 ? "registered" : (this.embeddedCount > 0 ? "embedded" : ""));
      }

      boolean hasServerEvidence() {
         return this.directions.contains("S2C");
      }

      String label() {
         StringBuilder sb = new StringBuilder(this.channel);
         String source = this.bestSource();
         if (!source.isBlank()) {
            sb.append(" [").append(source);
         }

         if (!this.directions.isEmpty()) {
            sb.append(" ").append(String.join("/", this.directions));
         }

         int count = this.liveCount + this.registeredCount + this.embeddedCount;
         if (count > 1) {
            sb.append(" x").append(count);
         }

         if (!source.isBlank()) {
            sb.append(']');
         }

         return sb.toString();
      }
   }

   private static final class PluginAccumulator {
      final String key;
      String displayName;
      int score;
      String basis = "";
      final Set<String> labels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      PluginAccumulator(String displayName, String key) {
         this.displayName = displayName == null ? "" : displayName;
         this.key = key == null ? "" : key;
      }

      void add(RiptidePluginPayloadFingerprints.ChannelStats stats) {
         if (stats != null && stats.channel != null && !stats.channel.isBlank()) {
            this.score = this.score + stats.score();
            if (this.basis.isBlank() || stats.sourceRank() > this.sourceRank(this.basis)) {
               this.basis = stats.bestSource();
            }

            this.labels.add(stats.label());
         }
      }

      RiptidePluginPayloadFingerprints.PluginFingerprint toFingerprint() {
         return new RiptidePluginPayloadFingerprints.PluginFingerprint(this.displayName, this.key, List.copyOf(this.labels), this.score, this.basis);
      }

      private int sourceRank(String source) {
         String var2 = source == null ? "" : source;

         return switch (var2) {
            case "live" -> 4;
            case "registered" -> 3;
            case "embedded" -> 1;
            default -> 0;
         };
      }
   }

   public record PluginFingerprint(String plugin, String key, List<String> channels, int score, String basis) {
   }

   private record PluginIdentity(String displayName, String key) {
      PluginIdentity(String displayName, String key) {
         displayName = displayName == null ? "" : displayName.trim();
         key = RiptidePluginPayloadFingerprints.canonicalPluginKey(key != null && !key.isBlank() ? key : displayName);
         this.displayName = displayName;
         this.key = key;
      }
   }

   private static final class State {
      String brand = "";
      long revision;
      final Set<String> directChannels = new LinkedHashSet<>();
      final Set<String> registeredChannels = new LinkedHashSet<>();
      final Set<String> embeddedChannels = new LinkedHashSet<>();
      final Map<String, RiptidePluginPayloadFingerprints.ChannelStats> channelStats = new LinkedHashMap<>();

      boolean addChannel(String channel, String direction) {
         return this.addCapped(this.directChannels, channel, "live", direction);
      }

      boolean addRegisteredChannel(String channel, String direction) {
         return this.addCapped(this.registeredChannels, channel, "registered", direction);
      }

      boolean addEmbeddedChannel(String channel, String direction) {
         return this.addCapped(this.embeddedChannels, channel, "embedded", direction);
      }

      Set<String> allChannels() {
         LinkedHashSet<String> out = new LinkedHashSet<>();
         out.addAll(this.directChannels);
         out.addAll(this.registeredChannels);
         out.addAll(this.embeddedChannels);
         return Collections.unmodifiableSet(out);
      }

      List<RiptidePluginPayloadFingerprints.ChannelStats> allChannelStats() {
         return List.copyOf(this.channelStats.values());
      }

      private boolean addCapped(Set<String> set, String channel, String source, String direction) {
         String normalized = RiptidePluginPayloadFingerprints.normalizeChannel(channel);
         if (normalized.isBlank()) {
            return false;
         } else if (this.directChannels.size() + this.registeredChannels.size() + this.embeddedChannels.size() >= 512
            && !this.directChannels.contains(normalized)
            && !this.registeredChannels.contains(normalized)
            && !this.embeddedChannels.contains(normalized)) {
            return false;
         } else {
            RiptidePluginPayloadFingerprints.ChannelStats stats = this.channelStats
               .computeIfAbsent(normalized, RiptidePluginPayloadFingerprints.ChannelStats::new);
            int beforeScore = stats.score();
            stats.add(source, direction);
            return set.add(normalized) || stats.score() != beforeScore;
         }
      }
   }
}
