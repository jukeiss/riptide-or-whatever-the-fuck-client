package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.protocol.Packet;
import riptide.modules.RiptideModule;

public class RiptidePresetManager {
   private static final File PRESETS_FOLDER = new File(riptide.RiptideClientAddon.FOLDER, "presets");
   private static final RiptidePresetManager INSTANCE = new RiptidePresetManager();
   private static final String USER_PRESET_PREFIX = "User Preset ";
   private static final Map<String, String> LEGACY_PACKET_NAME_ALIASES = createLegacyPacketNameAliases();
   private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   private List<RiptidePresetManager.PresetEntry> cachedBuiltInPresetEntries = null;
   private List<RiptidePresetManager.PresetEntry> cachedUserPresetEntries = null;
   private String cachedUserPresetSignature = "";

   private RiptidePresetManager() {
      if (!PRESETS_FOLDER.exists()) {
         PRESETS_FOLDER.mkdirs();
      }
   }

   public static RiptidePresetManager get() {
      return INSTANCE;
   }

   public boolean savePreset(String name) {
      String sanitized = this.sanitizePresetName(name);
      if (sanitized == null) {
         RiptideClientMessaging.sendPrefixed("§ePreset name cannot be empty.");
         return false;
      } else if (this.isReservedPresetName(sanitized)) {
         RiptideClientMessaging.sendPrefixed("§cThat preset name is reserved for developer templates.");
         return false;
      } else {
         this.savePresetObject(this.captureCurrentPreset(sanitized), true);
         this.invalidateUserPresetCache();
         return true;
      }
   }

   public String saveCurrentAsUserPreset() {
      String name = this.nextAvailableUserPresetName();
      this.savePresetObject(this.captureCurrentPreset(name), true);
      this.invalidateUserPresetCache();
      return name;
   }

   public String savePacketCancellerPreset(Set<String> c2sPackets, Set<String> s2cPackets) {
      String name = this.nextAvailableUserPresetName();
      this.savePresetObject(
         new RiptidePacketPreset(
            name,
            c2sPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(c2sPackets),
            s2cPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(s2cPackets)
         ),
         true
      );
      this.invalidateUserPresetCache();
      return name;
   }

   public boolean overwriteUserPreset(String name) {
      String sanitized = this.sanitizePresetName(name);
      if (sanitized == null) {
         RiptideClientMessaging.sendPrefixed("§eSelect a user preset first.");
         return false;
      } else if (this.isReservedPresetName(sanitized)) {
         RiptideClientMessaging.sendPrefixed("§cDeveloper presets cannot be overwritten.");
         return false;
      } else {
         File file = new File(PRESETS_FOLDER, sanitized + ".json");
         if (!file.exists()) {
            RiptideClientMessaging.sendPrefixed("§cUser preset not found: " + sanitized);
            return false;
         } else {
            this.savePresetObject(this.captureCurrentPreset(sanitized), true);
            this.invalidateUserPresetCache();
            return true;
         }
      }
   }

   public boolean loadPreset(String name) {
      RiptidePresetManager.PresetEntry entry = this.getPresetEntry(name);
      if (entry == null) {
         RiptideClientMessaging.sendPrefixed("§cPreset not found: " + name);
         return false;
      } else {
         this.applyPreset(entry.preset());
         RiptideClientMessaging.sendPrefixed("§aLoaded preset: " + entry.name());
         return true;
      }
   }

   public boolean deleteUserPreset(String name) {
      String sanitized = this.sanitizePresetName(name);
      if (sanitized == null) {
         RiptideClientMessaging.sendPrefixed("§eSelect a user preset first.");
         return false;
      } else if (this.isReservedPresetName(sanitized)) {
         RiptideClientMessaging.sendPrefixed("§cDeveloper presets cannot be deleted.");
         return false;
      } else {
         File file = new File(PRESETS_FOLDER, sanitized + ".json");
         if (!file.exists()) {
            RiptideClientMessaging.sendPrefixed("§cUser preset not found: " + sanitized);
            return false;
         } else if (!file.delete()) {
            RiptideClientMessaging.sendPrefixed("§cFailed to delete preset: " + sanitized);
            return false;
         } else {
            this.invalidateUserPresetCache();
            RiptideClientMessaging.sendPrefixed("§aDeleted preset: " + sanitized);
            return true;
         }
      }
   }

   public boolean importSharedPreset(String presetName, String presetJson, String sender) {
      String sanitized = this.sanitizePresetName(presetName);
      if (sanitized != null && presetJson != null && !presetJson.isBlank()) {
         if (this.isReservedPresetName(sanitized)) {
            sanitized = sanitized + " (LAN)";
         }

         try {
            RiptidePacketPreset preset = (RiptidePacketPreset)this.gson.fromJson(presetJson, RiptidePacketPreset.class);
            if (preset == null) {
               return false;
            } else {
               preset.name = this.createUniquePresetName(sanitized);
               this.savePresetObject(preset, false);
               this.invalidateUserPresetCache();
               RiptideClientMessaging.sendPrefixed("§aReceived preset from " + sender + ": " + preset.name);
               return true;
            }
         } catch (Exception var6) {
            riptide.RiptideClientAddon.LOG.error("Failed to import shared preset", var6);
            RiptideClientMessaging.sendPrefixed("§cFailed to import preset: " + var6.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean isReservedPresetName(String name) {
      String normalized = this.normalizePresetName(name);
      if (normalized.isEmpty()) {
         return false;
      } else {
         for (RiptidePresetManager.PresetEntry entry : this.getBuiltInPresetEntries()) {
            if (this.normalizePresetName(entry.name()).equals(normalized)) {
               return true;
            }
         }

         return false;
      }
   }

   public RiptidePresetManager.PresetEntry getPresetEntry(String name) {
      String normalized = this.normalizePresetName(name);
      if (normalized.isEmpty()) {
         return null;
      } else {
         for (RiptidePresetManager.PresetEntry entry : this.getBuiltInPresetEntries()) {
            if (this.normalizePresetName(entry.name()).equals(normalized)) {
               return entry;
            }
         }

         for (RiptidePresetManager.PresetEntry entryx : this.getUserPresetEntries()) {
            if (this.normalizePresetName(entryx.name()).equals(normalized)) {
               return entryx;
            }
         }

         return null;
      }
   }

   public List<RiptidePresetManager.PresetEntry> getBuiltInPresetEntries() {
      if (this.cachedBuiltInPresetEntries != null) {
         return this.cachedBuiltInPresetEntries;
      } else {
         RiptideModule module = RiptideModule.get();
         List<RiptidePresetManager.PresetEntry> entries = new ArrayList<>();
         entries.add(new RiptidePresetManager.PresetEntry(this.presetFromClasses("Default", module.defaultC2SPackets(), module.defaultS2CPackets()), true));
         entries.add(
            new RiptidePresetManager.PresetEntry(
               this.presetFromPacketNames(
                  "Movement & World",
                  Set.of(
                     "ServerboundSwingPacket",
                     "ServerboundPlayerActionPacket",
                     "ServerboundUseItemOnPacket",
                     "ServerboundInteractPacket",
                     "ServerboundUseItemPacket",
                     "ServerboundMovePlayerPacket.Pos",
                     "ServerboundMovePlayerPacket.PosRot",
                     "ServerboundMovePlayerPacket.Rot",
                     "ServerboundMovePlayerPacket.StatusOnly",
                     "ServerboundAcceptTeleportationPacket",
                     "ServerboundMoveVehiclePacket"
                  ),
                  Set.of(
                     "ClientboundBlockEventPacket",
                     "ClientboundBlockUpdatePacket",
                     "ClientboundEntityPositionSyncPacket",
                     "ClientboundSetEntityMotionPacket",
                     "ClientboundPlayerPositionPacket",
                     "ClientboundPlayerRotationPacket",
                     "ClientboundMoveVehiclePacket",
                     "ClientboundLevelEventPacket"
                  )
               ),
               true
            )
         );
         entries.add(
            new RiptidePresetManager.PresetEntry(
               this.presetFromPacketNames(
                  "Inventory, Chat & Commands",
                  Set.of(
                     "ServerboundContainerButtonClickPacket",
                     "ServerboundChatPacket",
                     "ServerboundChatCommandPacket",
                     "ServerboundPlaceRecipePacket",
                     "ServerboundContainerClickPacket",
                     "ServerboundSetCreativeModeSlotPacket",
                     "ServerboundPickItemFromBlockPacket",
                     "ServerboundPickItemFromEntityPacket",
                     "ServerboundPlayerActionPacket",
                     "ServerboundRecipeBookChangeSettingsPacket",
                     "ServerboundRecipeBookSeenRecipePacket",
                     "ServerboundRenameItemPacket",
                     "ServerboundCommandSuggestionPacket",
                     "ServerboundChatCommandSignedPacket",
                     "ServerboundSignUpdatePacket"
                  ),
                  Set.of(
                     "ClientboundPlayerChatPacket",
                     "ClientboundContainerClosePacket",
                     "ClientboundCommandSuggestionsPacket",
                     "ClientboundCommandsPacket",
                     "ClientboundSystemChatPacket",
                     "ClientboundContainerSetContentPacket",
                     "ClientboundMountScreenOpenPacket",
                     "ClientboundOpenScreenPacket",
                     "ClientboundContainerSetDataPacket",
                     "ClientboundContainerSetSlotPacket",
                     "ClientboundSetCursorItemPacket",
                     "ClientboundMerchantOffersPacket"
                  )
               ),
               true
            )
         );
         entries.add(
            new RiptidePresetManager.PresetEntry(
               this.presetFromPacketNames(
                  "Chunk Freeze & Blocks",
                  Set.of(
                     "ServerboundChunkBatchReceivedPacket",
                     "ServerboundClientCommandPacket",
                     "ServerboundSwingPacket",
                     "ServerboundPlayerActionPacket",
                     "ServerboundPlayerInputPacket",
                     "ServerboundUseItemOnPacket",
                     "ServerboundUseItemPacket",
                     "ServerboundMovePlayerPacket.PosRot"
                  ),
                  Set.of(
                     "ClientboundBlockEventPacket",
                     "ClientboundBlockUpdatePacket",
                     "ClientboundLevelChunkWithLightPacket",
                     "ClientboundSectionBlocksUpdatePacket",
                     "ClientboundSetChunkCacheRadiusPacket",
                     "ClientboundSetChunkCacheCenterPacket",
                     "ClientboundGameEventPacket",
                     "ClientboundLightUpdatePacket",
                     "ClientboundChunkBatchStartPacket",
                     "ClientboundForgetLevelChunkPacket",
                     "ClientboundLevelEventPacket"
                  )
               ),
               true
            )
         );
         this.cachedBuiltInPresetEntries = List.copyOf(entries);
         return this.cachedBuiltInPresetEntries;
      }
   }

   public List<RiptidePresetManager.PresetEntry> getUserPresetEntries() {
      String signature = this.userPresetFilesSignature();
      if (this.cachedUserPresetEntries != null && this.cachedUserPresetSignature.equals(signature)) {
         return this.cachedUserPresetEntries;
      } else {
         List<RiptidePresetManager.PresetEntry> entries = new ArrayList<>();
         File[] files = PRESETS_FOLDER.listFiles((dir, fileName) -> fileName.endsWith(".json"));
         if (files == null) {
            this.cachedUserPresetSignature = signature;
            this.cachedUserPresetEntries = List.of();
            return this.cachedUserPresetEntries;
         } else {
            for (File file : files) {
               try (FileReader reader = new FileReader(file)) {
                  RiptidePacketPreset preset = (RiptidePacketPreset)this.gson.fromJson(reader, RiptidePacketPreset.class);
                  if (preset != null) {
                     if (preset.name == null || preset.name.isBlank()) {
                        preset.name = file.getName().replace(".json", "");
                     }

                     if (this.isReservedPresetName(preset.name)) {
                        riptide.RiptideClientAddon.LOG.warn("Ignoring user preset with reserved built-in name: {}", preset.name);
                     } else {
                        if (preset.c2sPackets == null) {
                           preset.c2sPackets = new LinkedHashSet<>();
                        }

                        if (preset.s2cPackets == null) {
                           preset.s2cPackets = new LinkedHashSet<>();
                        }

                        entries.add(new RiptidePresetManager.PresetEntry(preset, false));
                     }
                  }
               } catch (IOException var13) {
                  riptide.RiptideClientAddon.LOG.error("Failed to read preset file {}", file.getName(), var13);
               }
            }

            entries.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
            this.cachedUserPresetSignature = signature;
            this.cachedUserPresetEntries = List.copyOf(entries);
            return this.cachedUserPresetEntries;
         }
      }
   }

   public List<String> getPresetNames() {
      List<String> names = new ArrayList<>();

      for (RiptidePresetManager.PresetEntry entry : this.getBuiltInPresetEntries()) {
         names.add(entry.name());
      }

      for (RiptidePresetManager.PresetEntry entry : this.getUserPresetEntries()) {
         names.add(entry.name());
      }

      return names;
   }

   private RiptidePacketPreset captureCurrentPreset(String name) {
      RiptideSharedState shared = RiptideSharedState.get();
      Set<String> c2s = this.encodePacketNames(shared.getC2SPackets());
      Set<String> s2c = this.encodePacketNames(shared.getS2CPackets());
      return new RiptidePacketPreset(name, c2s, s2c);
   }

   private RiptidePacketPreset presetFromClasses(
      String name, Collection<Class<? extends Packet<?>>> c2sPackets, Collection<Class<? extends Packet<?>>> s2cPackets
   ) {
      return new RiptidePacketPreset(name, this.encodePacketNames(c2sPackets), this.encodePacketNames(s2cPackets));
   }

   private RiptidePacketPreset presetFromPacketNames(String name, Set<String> c2sNames, Set<String> s2cNames) {
      return new RiptidePacketPreset(
         name,
         this.resolvePacketNames(c2sNames, true).stream().map(this::encodePacketName).collect(Collectors.toCollection(LinkedHashSet::new)),
         this.resolvePacketNames(s2cNames, false).stream().map(this::encodePacketName).collect(Collectors.toCollection(LinkedHashSet::new))
      );
   }

   private Set<String> encodePacketNames(Collection<Class<? extends Packet<?>>> packets) {
      return packets.stream()
         .map(this::encodePacketName)
         .filter(packetName -> packetName != null && !packetName.isEmpty())
         .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   private String encodePacketName(Class<? extends Packet<?>> packetClass) {
      String name = RiptidePacketRegistry.getName(packetClass);
      return name != null ? name : packetClass.getName();
   }

   private Set<Class<? extends Packet<?>>> resolvePacketNames(Collection<String> names, boolean c2s) {
      Set<Class<? extends Packet<?>>> resolved = new LinkedHashSet<>();
      Set<Class<? extends Packet<?>>> pool = c2s ? RiptidePacketRegistry.getC2SPackets() : RiptidePacketRegistry.getS2CPackets();

      for (String packetName : names) {
         if (packetName != null && !packetName.isBlank()) {
            String resolvedName = this.normalizePresetPacketName(packetName);
            Class<? extends Packet<?>> packetClass = RiptidePacketRegistry.getPacket(resolvedName);
            if (packetClass == null) {
               packetClass = RiptidePacketRegistry.getPacket(RiptidePacketNamer.getFriendlyName(resolvedName));
            }

            if (packetClass != null && pool.contains(packetClass)) {
               resolved.add(packetClass);
            } else {
               riptide.RiptideClientAddon.LOG.warn("Unknown packet name in preset: {}", packetName);
            }
         }
      }

      return resolved;
   }

   private void savePresetObject(RiptidePacketPreset preset, boolean notify) {
      String safeName = this.sanitizePresetName(preset.name);
      if (safeName == null) {
         RiptideClientMessaging.sendPrefixed("§cInvalid preset name.");
      } else {
         File file = new File(PRESETS_FOLDER, safeName + ".json");

         try (FileWriter writer = new FileWriter(file)) {
            this.gson.toJson(preset, writer);
            if (notify) {
               RiptideClientMessaging.sendPrefixed("§aSaved preset: " + preset.name);
            }
         } catch (IOException var10) {
            riptide.RiptideClientAddon.LOG.error("Failed to save preset", var10);
            RiptideClientMessaging.sendPrefixed("§cFailed to save preset: " + var10.getMessage());
         }
      }
   }

   private void applyPreset(RiptidePacketPreset preset) {
      RiptideModule module = RiptideModule.get();
      module.setC2SPackets(this.resolvePacketNames(preset.c2sPackets, true));
      module.setS2CPackets(this.resolvePacketNames(preset.s2cPackets, false));
   }

   private String nextAvailableUserPresetName() {
      int index = 1;

      while (true) {
         String candidate = "User Preset " + index;
         if (this.getPresetEntry(candidate) == null) {
            return candidate;
         }

         index++;
      }
   }

   private String createUniquePresetName(String preferredName) {
      String baseName = this.sanitizePresetName(preferredName);
      if (baseName == null) {
         baseName = "User Preset LAN";
      }

      String candidate = baseName;
      int suffix = 1;

      while (this.getPresetEntry(candidate) != null || this.isReservedPresetName(candidate)) {
         candidate = baseName + " (" + suffix++ + ")";
      }

      return candidate;
   }

   private String sanitizePresetName(String name) {
      if (name == null) {
         return null;
      } else {
         String cleaned = name.trim()
            .replace('\\', ' ')
            .replace('/', ' ')
            .replaceAll("[\\x00-\\x1f]", "")
            .replace("..", "")
            .replaceAll("[:*?\"<>|]", "")
            .trim();

         while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
         }

         return cleaned.isEmpty() ? null : cleaned;
      }
   }

   private String normalizePresetName(String name) {
      return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
   }

   private String normalizePresetPacketName(String packetName) {
      if (packetName == null) {
         return "";
      } else {
         String trimmed = packetName.trim();
         String aliased = LEGACY_PACKET_NAME_ALIASES.get(trimmed);
         return aliased != null ? aliased : trimmed;
      }
   }

   private void invalidateUserPresetCache() {
      this.cachedUserPresetEntries = null;
      this.cachedUserPresetSignature = "";
   }

   private String userPresetFilesSignature() {
      File[] files = PRESETS_FOLDER.listFiles((dir, fileName) -> fileName.endsWith(".json"));
      if (files != null && files.length != 0) {
         List<File> orderedFiles = new ArrayList<>(List.of(files));
         orderedFiles.sort(Comparator.comparing(File::getName));
         StringBuilder signature = new StringBuilder();

         for (File file : orderedFiles) {
            signature.append(file.getName()).append(':').append(file.lastModified()).append(':').append(file.length()).append(';');
         }

         return signature.toString();
      } else {
         return "empty";
      }
   }

   private static Map<String, String> createLegacyPacketNameAliases() {
      return Map.ofEntries(
         Map.entry("HandSwingC2SPacket", "ServerboundSwingPacket"),
         Map.entry("PlayerMoveC2SPacket", "ServerboundMovePlayerPacket.PosRot"),
         Map.entry("PlayerMoveC2SPacket.Full", "ServerboundMovePlayerPacket.PosRot"),
         Map.entry("PlayerMoveC2SPacket.LookAndOnGround", "ServerboundMovePlayerPacket.Rot"),
         Map.entry("PlayerMoveC2SPacket.OnGroundOnly", "ServerboundMovePlayerPacket.StatusOnly"),
         Map.entry("PlayerMoveC2SPacket.PositionAndOnGround", "ServerboundMovePlayerPacket.Pos"),
         Map.entry("TeleportConfirmC2SPacket", "ServerboundAcceptTeleportationPacket"),
         Map.entry("VehicleMoveC2SPacket", "ServerboundMoveVehiclePacket"),
         Map.entry("AcknowledgeChunksC2SPacket", "ServerboundChunkBatchReceivedPacket"),
         Map.entry("PlayerInputC2SPacket", "ServerboundPlayerInputPacket"),
         Map.entry("ChatMessageC2SPacket", "ServerboundChatPacket"),
         Map.entry("CommandExecutionC2SPacket", "ServerboundChatCommandPacket"),
         Map.entry("PickItemFromBlockC2SPacket", "ServerboundPickItemFromBlockPacket"),
         Map.entry("PickItemFromEntityC2SPacket", "ServerboundPickItemFromEntityPacket"),
         Map.entry("RecipeBookDataC2SPacket", "ServerboundRecipeBookChangeSettingsPacket"),
         Map.entry("RenameItemC2SPacket", "ServerboundRenameItemPacket"),
         Map.entry("RequestCommandCompletionsC2SPacket", "ServerboundCommandSuggestionPacket"),
         Map.entry("BlockEventS2CPacket", "ClientboundBlockEventPacket"),
         Map.entry("BlockUpdateS2CPacket", "ClientboundBlockUpdatePacket"),
         Map.entry("PlayerRotationS2CPacket", "ClientboundPlayerRotationPacket"),
         Map.entry("VehicleMoveS2CPacket", "ClientboundMoveVehiclePacket"),
         Map.entry("WorldEventS2CPacket", "ClientboundLevelEventPacket"),
         Map.entry("ChatMessageS2CPacket", "ClientboundPlayerChatPacket"),
         Map.entry("CloseScreenS2CPacket", "ClientboundContainerClosePacket"),
         Map.entry("CommandTreeS2CPacket", "ClientboundCommandsPacket"),
         Map.entry("GameMessageS2CPacket", "ClientboundSystemChatPacket"),
         Map.entry("OpenMountScreenS2CPacket", "ClientboundMountScreenOpenPacket"),
         Map.entry("AbstractContainerMenuPropertyUpdateS2CPacket", "ClientboundContainerSetDataPacket"),
         Map.entry("SetCursorItemS2CPacket", "ClientboundSetCursorItemPacket"),
         Map.entry("SetClientboundContainerSetContentPacket", "ClientboundContainerSetContentPacket"),
         Map.entry("SetTradeOffersS2CPacket", "ClientboundMerchantOffersPacket"),
         Map.entry("ChunkDeltaUpdateS2CPacket", "ClientboundSectionBlocksUpdatePacket"),
         Map.entry("ChunkLoadDistanceS2CPacket", "ClientboundSetChunkCacheRadiusPacket"),
         Map.entry("ChunkRenderDistanceCenterS2CPacket", "ClientboundSetChunkCacheCenterPacket"),
         Map.entry("ChunkSentS2CPacket", "ClientboundLevelChunkWithLightPacket"),
         Map.entry("LightUpdateS2CPacket", "ClientboundLightUpdatePacket"),
         Map.entry("StartChunkSendS2CPacket", "ClientboundChunkBatchStartPacket"),
         Map.entry("UnloadChunkS2CPacket", "ClientboundForgetLevelChunkPacket")
      );
   }

   public record PresetEntry(RiptidePacketPreset preset, boolean builtIn) {
      public String name() {
         return this.preset != null && this.preset.name != null ? this.preset.name : "";
      }

      public int c2sCount() {
         return this.preset != null && this.preset.c2sPackets != null ? this.preset.c2sPackets.size() : 0;
      }

      public int s2cCount() {
         return this.preset != null && this.preset.s2cPackets != null ? this.preset.s2cPackets.size() : 0;
      }

      public boolean deletable() {
         return !this.builtIn;
      }
   }
}
