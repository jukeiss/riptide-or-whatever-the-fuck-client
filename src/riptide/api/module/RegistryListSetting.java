package riptide.api.module;

import java.util.ArrayList;
import java.util.List;

public final class RegistryListSetting extends ListSetting<RegistryListSetting> {
   private final RegistryListSetting.BlockFilter blockFilter;

   private RegistryListSetting(Kind kind, String name, String title, String defaultValue) {
      this(kind, name, title, defaultValue, RegistryListSetting.BlockFilter.NONE);
   }

   private RegistryListSetting(Kind kind, String name, String title, String defaultValue, RegistryListSetting.BlockFilter blockFilter) {
      super(registryKindOrDefault(kind), name, title, parseDefault(defaultValue));
      this.blockFilter = kind == Kind.BLOCK_LIST && blockFilter != null ? blockFilter : RegistryListSetting.BlockFilter.NONE;
   }

   public static RegistryListSetting items(String name, String title) {
      return items(name, title, "");
   }

   public static RegistryListSetting items(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.ITEM_LIST, name, title, defaultValue);
   }

   public static RegistryListSetting blocks(String name, String title) {
      return blocks(name, title, "");
   }

   public static RegistryListSetting blocks(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue);
   }

   public static RegistryListSetting placeableBlocks(String name, String title) {
      return placeableBlocks(name, title, "");
   }

   public static RegistryListSetting placeableBlocks(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue, RegistryListSetting.BlockFilter.PLACEABLE);
   }

   public static RegistryListSetting oreSimOres(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue, RegistryListSetting.BlockFilter.ORE_SIM);
   }

   public static RegistryListSetting crops(String name, String title) {
      return crops(name, title, "");
   }

   public static RegistryListSetting crops(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue, RegistryListSetting.BlockFilter.CROPS);
   }

   public RegistryListSetting.BlockFilter blockFilter() {
      return this.blockFilter;
   }

   public boolean placeableBlocksOnly() {
      return this.blockFilter == RegistryListSetting.BlockFilter.PLACEABLE;
   }

   public static RegistryListSetting entityTypes(String name, String title) {
      return entityTypes(name, title, "");
   }

   public static RegistryListSetting entityTypes(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.ENTITY_TYPE_LIST, name, title, defaultValue);
   }

   public static RegistryListSetting soundEvents(String name, String title) {
      return soundEvents(name, title, "");
   }

   public static RegistryListSetting soundEvents(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.SOUND_EVENT_LIST, name, title, defaultValue);
   }

   public static RegistryListSetting storages(String name, String title) {
      return storages(name, title, "");
   }

   public static RegistryListSetting storages(String name, String title, String defaultValue) {
      return new RegistryListSetting(Kind.STORAGE_LIST, name, title, defaultValue);
   }

   private static List<String> parseDefault(String raw) {
      if (raw != null && !raw.isBlank()) {
         List<String> out = new ArrayList<>();

         for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
               out.add(trimmed);
            }
         }

         return List.copyOf(out);
      } else {
         return List.of();
      }
   }

   private static Kind registryKindOrDefault(Kind kind) {
      return switch (kind == null ? Kind.ITEM_LIST : kind) {
         case ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST -> kind;
         default -> Kind.ITEM_LIST;
      };
   }

   public static enum BlockFilter {
      NONE,
      PLACEABLE,
      ORE_SIM,
      CROPS;
   }
}
