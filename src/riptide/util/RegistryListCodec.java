package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import riptide.api.module.Kind;
import riptide.modules.ModuleStorageEsp;

public final class RegistryListCodec {
   private RegistryListCodec() {
   }

   public static boolean matches(String registryId, String entry) {
      if (registryId != null && entry != null) {
         String normalized = entry.trim().toLowerCase(Locale.ROOT);
         return registryId.equalsIgnoreCase(normalized) || registryId.toLowerCase(Locale.ROOT).endsWith(":" + normalized);
      } else {
         return false;
      }
   }

   public static List<String> invalidTokens(Kind type, List<String> values) {
      List<String> invalid = new ArrayList<>();
      if (values == null) {
         return invalid;
      } else {
         for (String value : values) {
            if (!value.isBlank() && !exists(type, value)) {
               invalid.add(value);
            }
         }

         return invalid;
      }
   }

   public static boolean exists(Kind type, String id) {
      String normalized = normalizeId(id);
      if (type == Kind.STORAGE_LIST) {
         return ModuleStorageEsp.byId(normalized) != null;
      } else {
         Identifier parsed = Identifier.tryParse(normalized);
         if (parsed == null) {
            return false;
         } else {
            return switch (type) {
               case ITEM_LIST -> BuiltInRegistries.ITEM.getOptional(parsed).isPresent();
               case BLOCK_LIST -> BuiltInRegistries.BLOCK.getOptional(parsed).isPresent();
               case ENTITY_TYPE_LIST -> BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).isPresent();
               case SOUND_EVENT_LIST -> BuiltInRegistries.SOUND_EVENT.getOptional(parsed).isPresent();
               default -> false;
            };
         }
      }
   }

   public static String normalizeId(String id) {
      if (id == null) {
         return "";
      } else {
         String trimmed = id.trim().toLowerCase(Locale.ROOT);
         return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
      }
   }
}
