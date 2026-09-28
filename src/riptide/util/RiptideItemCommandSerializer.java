package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

public final class RiptideItemCommandSerializer {
   private static final Minecraft MC = Minecraft.getInstance();

   private RiptideItemCommandSerializer() {
   }

   public static String giveCommand(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
         String components = componentPatch(stack);
         return "/give @p " + id + components + " " + Math.max(1, Math.min(99, stack.getCount()));
      } else {
         return "";
      }
   }

   public static String itemStackSnbt(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && MC.player != null) {
         try {
            Tag tag = (Tag)ItemStack.CODEC.encodeStart(MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
            return tagToSnbt(tag);
         } catch (Throwable var2) {
            return "{error:\"" + snbtString(var2.getClass().getSimpleName()) + "\"}";
         }
      } else {
         return "{}";
      }
   }

   public static ItemStack itemStackFromSnbt(String snbt) {
      if (snbt != null && !snbt.isBlank() && MC.player != null) {
         try {
            CompoundTag tag = TagParser.parseCompoundFully(snbt.trim());
            return (ItemStack)ItemStack.CODEC.parse(MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
         } catch (Throwable var2) {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static String validationError(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? ItemStack.validateStrict(stack).error().map(Object::toString).orElse("") : "item stack is empty";
   }

   public static String componentPatch(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && MC.player != null) {
         try {
            Tag encoded = (Tag)DataComponentPatch.CODEC
               .encodeStart(MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack.getComponentsPatch())
               .getOrThrow();
            if (encoded instanceof CompoundTag compound && !compound.isEmpty()) {
               List<String> parts = new ArrayList<>();

               for (Entry<String, Tag> entry : compound.entrySet()) {
                  String key = componentCommandKey(entry.getKey());
                  Tag value = entry.getValue();
                  if (!key.isBlank() && value != null) {
                     if (key.charAt(0) == '!') {
                        parts.add(key);
                     } else {
                        parts.add(key + "=" + tagToSnbt(value));
                     }
                  }
               }

               return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
            } else {
               return "";
            }
         } catch (Throwable var8) {
            return "";
         }
      } else {
         return "";
      }
   }

   public static String tagToSnbt(Tag tag) {
      return tag == null ? "" : tag.toString();
   }

   public static String snbtString(String text) {
      return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   private static String componentCommandKey(String raw) {
      if (raw != null && !raw.isBlank()) {
         String key = raw.trim();
         boolean removed = key.startsWith("!");
         if (removed) {
            key = key.substring(1);
         }

         if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
         }

         return removed ? "!" + key : key;
      } else {
         return "";
      }
   }
}
