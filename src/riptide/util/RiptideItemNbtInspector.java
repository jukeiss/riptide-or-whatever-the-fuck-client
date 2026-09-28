package riptide.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class RiptideItemNbtInspector {
   private static final int RAW_DISPLAY_CHAR_CAP = 40000;

   private RiptideItemNbtInspector() {
   }

   public static RiptideItemNbtInspector.ItemInspection inspect(ItemStack source) {
      ItemStack stack = source == null ? ItemStack.EMPTY : source.copy();
      String title = stack.isEmpty() ? "Item NBT" : stack.getHoverName().getString();
      RiptideItemNbtInspector.InspectionBuilder nice = new RiptideItemNbtInspector.InspectionBuilder(title);
      RiptideItemNbtInspector.InspectionBuilder raw = new RiptideItemNbtInspector.InspectionBuilder(title + " Raw");
      String rawSnbt = RiptideItemCommandSerializer.itemStackSnbt(stack);
      String giveCommand = RiptideItemCommandSerializer.giveCommand(stack);
      buildNice(stack, nice);
      buildRaw(rawSnbt, raw);
      return new RiptideItemNbtInspector.ItemInspection(title, stack, nice.buildLines(), raw.buildLines(), nice.copyText(), rawSnbt, giveCommand);
   }

   private static void buildNice(ItemStack stack, RiptideItemNbtInspector.InspectionBuilder builder) {
      if (stack != null && !stack.isEmpty()) {
         builder.section("Identity", RiptideColors.packetLightYellow());
         builder.line("Item: " + BuiltInRegistries.ITEM.getKey(stack.getItem()), RiptideColors.packetWhite());
         builder.line("Count: " + stack.getCount(), RiptideColors.textSecondary());
         builder.line("Hover Name: " + stack.getHoverName().getString(), RiptideColors.textSecondary());
         builder.line("Components: " + stack.getComponentsPatch().size() + " non-default", RiptideColors.textMuted());
         addIdentityData(builder, stack);
         Component customName = (Component)stack.get(DataComponents.CUSTOM_NAME);
         Component itemName = (Component)stack.get(DataComponents.ITEM_NAME);
         ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
         if (customName != null || itemName != null || lore != null && !lore.lines().isEmpty()) {
            builder.blank();
            builder.section("Display", RiptideColors.packetCyan());
            if (customName != null) {
               builder.line("Custom Name: " + customName.getString(), RiptideColors.packetWhite());
            }

            if (itemName != null) {
               builder.line("Item Name: " + itemName.getString(), RiptideColors.textSecondary());
            }

            if (lore != null && !lore.lines().isEmpty()) {
               int i = 1;

               for (Component line : lore.lines()) {
                  builder.line("Lore " + i++ + ": " + line.getString(), RiptideColors.textSecondary());
               }
            }
         }

         addPresentComponents(
            builder,
            stack,
            "Model / Visuals",
            RiptideColors.packetCyan(),
            component(DataComponents.ITEM_MODEL, "Item Model"),
            component(DataComponents.CUSTOM_MODEL_DATA, "Custom Model Data"),
            component(DataComponents.TOOLTIP_STYLE, "Tooltip Style"),
            component(DataComponents.DYED_COLOR, "Dyed Color"),
            component(DataComponents.DYE, "Dye"),
            component(DataComponents.RARITY, "Rarity"),
            component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, "Glint Override"),
            component(DataComponents.TOOLTIP_DISPLAY, "Tooltip Display")
         );
         addEnchantments(builder, "Enchantments", (ItemEnchantments)stack.get(DataComponents.ENCHANTMENTS));
         addEnchantments(builder, "Stored Enchantments", (ItemEnchantments)stack.get(DataComponents.STORED_ENCHANTMENTS));
         ItemAttributeModifiers attributes = (ItemAttributeModifiers)stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
         if (attributes != null && !attributes.modifiers().isEmpty()) {
            builder.blank();
            builder.section("Attributes", RiptideColors.packetOrange());

            for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : attributes.modifiers()) {
               String attribute = holderId(entry.attribute());
               builder.line(
                  attribute
                     + ": "
                     + entry.modifier().amount()
                     + " "
                     + entry.modifier().operation().getSerializedName()
                     + " ("
                     + entry.slot()
                     + ", "
                     + entry.modifier().id()
                     + ")",
                  RiptideColors.textSecondary()
               );
            }
         }

         if (stack.has(DataComponents.MAX_DAMAGE)
            || stack.has(DataComponents.DAMAGE)
            || stack.has(DataComponents.UNBREAKABLE)
            || stack.has(DataComponents.MAX_STACK_SIZE)
            || stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
            || stack.has(DataComponents.RARITY)) {
            builder.blank();
            builder.section("Item Rules", RiptideColors.packetYellow());
            if (stack.has(DataComponents.MAX_DAMAGE)) {
               builder.line("Max Damage: " + stack.getMaxDamage(), RiptideColors.textSecondary());
            }

            if (stack.has(DataComponents.DAMAGE)) {
               builder.line("Damage: " + stack.getDamageValue(), RiptideColors.textSecondary());
            }

            if (stack.has(DataComponents.UNBREAKABLE)) {
               builder.line("Unbreakable: true", RiptideColors.textSecondary());
            }

            if (stack.has(DataComponents.MAX_STACK_SIZE)) {
               builder.line("Max Stack: " + stack.getMaxStackSize(), RiptideColors.textSecondary());
            }
         }

         addPresentComponents(
            builder,
            stack,
            "Usage / Combat",
            RiptideColors.packetOrange(),
            component(DataComponents.USE_EFFECTS, "Use Effects"),
            component(DataComponents.MINIMUM_ATTACK_CHARGE, "Minimum Attack Charge"),
            component(DataComponents.DAMAGE_TYPE, "Damage Type"),
            component(DataComponents.FOOD, "Food"),
            component(DataComponents.CONSUMABLE, "Consumable"),
            component(DataComponents.USE_REMAINDER, "Use Remainder"),
            component(DataComponents.USE_COOLDOWN, "Use Cooldown"),
            component(DataComponents.DAMAGE_RESISTANT, "Damage Resistant"),
            component(DataComponents.TOOL, "Tool"),
            component(DataComponents.WEAPON, "Weapon"),
            component(DataComponents.ATTACK_RANGE, "Attack Range"),
            component(DataComponents.BLOCKS_ATTACKS, "Blocks Attacks"),
            component(DataComponents.PIERCING_WEAPON, "Piercing Weapon"),
            component(DataComponents.KINETIC_WEAPON, "Kinetic Weapon"),
            component(DataComponents.SWING_ANIMATION, "Swing Animation")
         );
         addPresentComponents(
            builder,
            stack,
            "Equipment / Enchanting",
            RiptideColors.packetBlue(),
            component(DataComponents.ENCHANTABLE, "Enchantable"),
            component(DataComponents.EQUIPPABLE, "Equippable"),
            component(DataComponents.REPAIRABLE, "Repairable"),
            component(DataComponents.GLIDER, "Glider"),
            component(DataComponents.REPAIR_COST, "Repair Cost"),
            component(DataComponents.CREATIVE_SLOT_LOCK, "Creative Slot Lock"),
            component(DataComponents.INTANGIBLE_PROJECTILE, "Intangible Projectile"),
            component(DataComponents.DEATH_PROTECTION, "Death Protection"),
            component(DataComponents.TRIM, "Armor Trim"),
            component(DataComponents.PROVIDES_TRIM_MATERIAL, "Provides Trim Material"),
            component(DataComponents.BREAK_SOUND, "Break Sound")
         );
         addPresentComponents(
            builder,
            stack,
            "Placement / Mining",
            RiptideColors.packetYellow(),
            component(DataComponents.CAN_PLACE_ON, "Can Place On"),
            component(DataComponents.CAN_BREAK, "Can Break"),
            component(DataComponents.BLOCK_STATE, "Block State"),
            component(DataComponents.LOCK, "Lock")
         );
         ItemContainerContents container = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         BundleContents bundle = (BundleContents)stack.get(DataComponents.BUNDLE_CONTENTS);
         if (container != null || bundle != null) {
            builder.blank();
            builder.section("Contents", RiptideColors.packetGreen());
            if (container != null) {
               long count = container.nonEmptyItemCopyStream().count();
               builder.line("Container Items: " + count, RiptideColors.textSecondary());
            }

            if (bundle != null) {
               builder.line("Bundle Items: " + bundle.size(), RiptideColors.textSecondary());
            }
         }

         addPresentComponents(
            builder,
            stack,
            "Containers / Projectiles",
            RiptideColors.packetGreen(),
            component(DataComponents.CONTAINER, "Container"),
            component(DataComponents.BUNDLE_CONTENTS, "Bundle Contents"),
            component(DataComponents.CHARGED_PROJECTILES, "Charged Projectiles"),
            component(DataComponents.CONTAINER_LOOT, "Container Loot"),
            component(DataComponents.BEES, "Bees"),
            component(DataComponents.RECIPES, "Recipes")
         );
         if (stack.has(DataComponents.BLOCK_ENTITY_DATA)
            || stack.has(DataComponents.BUCKET_ENTITY_DATA)
            || stack.has(DataComponents.ENTITY_DATA)
            || stack.has(DataComponents.MAP_ID)
            || stack.has(DataComponents.BANNER_PATTERNS)) {
            builder.blank();
            builder.section("Special Data", RiptideColors.packetPink());
            addComponentSummary(builder, stack, DataComponents.BLOCK_ENTITY_DATA, "Block Entity Data");
            addComponentSummary(builder, stack, DataComponents.BUCKET_ENTITY_DATA, "Bucket Entity Data");
            addComponentSummary(builder, stack, DataComponents.ENTITY_DATA, "Entity Data");
            addComponentSummary(builder, stack, DataComponents.MAP_ID, "Map ID");
            addComponentSummary(builder, stack, DataComponents.BANNER_PATTERNS, "Banner Patterns");
         }

         addPresentComponents(
            builder,
            stack,
            "Potions / Maps",
            RiptideColors.packetPink(),
            component(DataComponents.POTION_CONTENTS, "Potion Contents"),
            component(DataComponents.POTION_DURATION_SCALE, "Potion Duration Scale"),
            component(DataComponents.SUSPICIOUS_STEW_EFFECTS, "Suspicious Stew Effects"),
            component(DataComponents.MAP_COLOR, "Map Color"),
            component(DataComponents.MAP_ID, "Map ID"),
            component(DataComponents.MAP_DECORATIONS, "Map Decorations"),
            component(DataComponents.MAP_POST_PROCESSING, "Map Post Processing")
         );
         addPresentComponents(
            builder,
            stack,
            "Fireworks / Music / Profiles",
            RiptideColors.packetLightYellow(),
            component(DataComponents.FIREWORK_EXPLOSION, "Firework Explosion"),
            component(DataComponents.FIREWORKS, "Fireworks"),
            component(DataComponents.PROFILE, "Profile"),
            component(DataComponents.INSTRUMENT, "Instrument"),
            component(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, "Ominous Bottle Amplifier"),
            component(DataComponents.JUKEBOX_PLAYABLE, "Jukebox Playable"),
            component(DataComponents.NOTE_BLOCK_SOUND, "Note Block Sound")
         );
         addPresentComponents(
            builder,
            stack,
            "Banners / Decorations",
            RiptideColors.packetCyan(),
            component(DataComponents.PROVIDES_BANNER_PATTERNS, "Provides Banner Patterns"),
            component(DataComponents.BANNER_PATTERNS, "Banner Patterns"),
            component(DataComponents.BASE_COLOR, "Base Color"),
            component(DataComponents.POT_DECORATIONS, "Pot Decorations")
         );
         WrittenBookContent written = (WrittenBookContent)stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
         WritableBookContent writable = (WritableBookContent)stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
         if (written != null || writable != null) {
            builder.blank();
            builder.section("Book", RiptideColors.packetBlue());
            if (written != null) {
               builder.line("Title: " + (String)written.title().raw(), RiptideColors.textSecondary());
               builder.line("Author: " + written.author(), RiptideColors.textSecondary());
               builder.line("Pages: " + written.pages().size(), RiptideColors.textSecondary());
            }

            if (writable != null) {
               builder.line("Pages: " + writable.pages().size(), RiptideColors.textSecondary());
            }
         }

         CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (customData != null) {
            builder.blank();
            builder.section("Custom Data", RiptideColors.successText());
            CompoundTag tag = customData.copyTag();
            if (tag.isEmpty()) {
               builder.line("{}", RiptideColors.textMuted());
            } else {
               for (Entry<String, Tag> entry : tag.entrySet()) {
                  builder.line(entry.getKey() + ": " + RiptideItemCommandSerializer.tagToSnbt(entry.getValue()), RiptideColors.textSecondary());
               }
            }
         }

         boolean wroteOtherComponents = false;

         for (Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            String id = componentId(entry.getKey());
            if (!isKnownNiceComponent(id)) {
               if (!wroteOtherComponents) {
                  builder.blank();
                  builder.section("Other Components", RiptideColors.packetGray());
                  wroteOtherComponents = true;
               }

               Optional<?> value = entry.getValue();
               if (value.isPresent()) {
                  builder.structuredLine(id + ": " + componentValueString(value.get()));
               } else {
                  builder.structuredLine("!" + id);
               }
            }
         }
      } else {
         builder.section("Empty", RiptideColors.dangerText());
         builder.line("No item stack was selected.", RiptideColors.dangerText());
      }
   }

   private static void buildRaw(String rawSnbt, RiptideItemNbtInspector.InspectionBuilder builder) {
      builder.section("Raw ItemStack SNBT", RiptideColors.packetLightYellow());
      boolean truncated = rawSnbt != null && rawSnbt.length() > 40000;
      String shown = truncated ? rawSnbt.substring(0, 40000) : rawSnbt;

      for (String line : prettySnbtLines(shown)) {
         builder.structuredLine(line);
      }

      if (truncated) {
         builder.structuredLine("... (truncated for display; use Copy for the full NBT)");
      }
   }

   private static void addEnchantments(RiptideItemNbtInspector.InspectionBuilder builder, String title, ItemEnchantments enchantments) {
      if (enchantments != null && !enchantments.isEmpty()) {
         builder.blank();
         builder.section(title, RiptideColors.packetBlue());

         for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            builder.line(holderId((Holder<?>)entry.getKey()) + ": " + entry.getIntValue(), RiptideColors.textSecondary());
         }
      }
   }

   private static <T> void addComponentSummary(RiptideItemNbtInspector.InspectionBuilder builder, ItemStack stack, DataComponentType<T> type, String label) {
      T value = (T)stack.get(type);
      if (value != null) {
         builder.structuredLine(label + ": " + componentValueString(value));
      }
   }

   @SafeVarargs
   private static void addPresentComponents(
      RiptideItemNbtInspector.InspectionBuilder builder,
      ItemStack stack,
      String title,
      int sectionColor,
      RiptideItemNbtInspector.ComponentSummary<?>... summaries
   ) {
      boolean wroteTitle = false;

      for (RiptideItemNbtInspector.ComponentSummary<?> summary : summaries) {
         Object value = getComponent(stack, summary.type());
         if (value != null) {
            if (!wroteTitle) {
               builder.blank();
               builder.section(title, sectionColor);
               wroteTitle = true;
            }

            builder.structuredLine(summary.label() + ": " + componentValueString(value));
         }
      }
   }

   private static Object getComponent(ItemStack stack, DataComponentType<?> type) {
      return stack.get(type);
   }

   private static <T> RiptideItemNbtInspector.ComponentSummary<T> component(DataComponentType<T> type, String label) {
      return new RiptideItemNbtInspector.ComponentSummary<>(type, label);
   }

   private static void addIdentityData(RiptideItemNbtInspector.InspectionBuilder builder, ItemStack stack) {
      Map<String, String> ids = new LinkedHashMap<>();
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         collectIdentityTags("", customData.copyTag(), ids);
      }

      addIdentityComponent(ids, stack, DataComponents.BLOCK_ENTITY_DATA, "block_entity_data");
      addIdentityComponent(ids, stack, DataComponents.ENTITY_DATA, "entity_data");
      addIdentityComponent(ids, stack, DataComponents.BUCKET_ENTITY_DATA, "bucket_entity_data");

      for (Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
         String id = componentId(entry.getKey());
         if (looksLikeIdentityKey(id) && entry.getValue().isPresent()) {
            ids.put(id, componentValueString(entry.getValue().get()));
         }
      }

      if (!ids.isEmpty()) {
         builder.blank();
         builder.section("Identity / IDs", RiptideColors.packetGreen());
         ids.forEach((key, value) -> builder.structuredLine(key + ": " + value));
      }
   }

   private static <T> void addIdentityComponent(Map<String, String> ids, ItemStack stack, DataComponentType<T> type, String label) {
      T value = (T)stack.get(type);
      if (value != null) {
         String text = componentValueString(value);
         if (text.contains("id") || text.contains("uuid") || text.contains("owner") || text.contains("profile")) {
            ids.put(label, text);
         }
      }
   }

   private static void collectIdentityTags(String path, Tag tag, Map<String, String> ids) {
      if (tag instanceof CompoundTag compound) {
         for (Entry<String, Tag> entry : compound.entrySet()) {
            String key = entry.getKey();
            String childPath = path.isBlank() ? key : path + "." + key;
            if (looksLikeIdentityKey(key)) {
               ids.put(childPath, RiptideItemCommandSerializer.tagToSnbt(entry.getValue()));
            }

            collectIdentityTags(childPath, entry.getValue(), ids);
         }
      }
   }

   private static boolean looksLikeIdentityKey(String key) {
      if (key == null) {
         return false;
      } else {
         String normalized = key.replace("-", "_").replace(".", "_").replace("/", "_").toLowerCase(Locale.ROOT);
         return normalized.equals("id")
            || normalized.endsWith("_id")
            || normalized.contains("original_id")
            || normalized.contains("unique_id")
            || normalized.contains("uuid")
            || normalized.contains("owner")
            || normalized.contains("creator")
            || normalized.contains("profile");
      }
   }

   private static String componentValueString(Object value) {
      if (value == null) {
         return "";
      } else if (value instanceof Component component) {
         return component.getString();
      } else {
         return value instanceof CustomData customData ? RiptideItemCommandSerializer.tagToSnbt(customData.copyTag()) : String.valueOf(value);
      }
   }

   private static String holderId(Holder<?> holder) {
      return holder == null ? "<unknown>" : holder.unwrapKey().<Identifier>map(ResourceKey::identifier).map(Object::toString).orElse(String.valueOf(holder));
   }

   private static String componentId(DataComponentType<?> type) {
      if (type == null) {
         return "<unknown>";
      } else {
         Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
         if (id == null) {
            return String.valueOf(type);
         } else {
            String text = id.toString();
            return text.startsWith("minecraft:") ? text.substring("minecraft:".length()) : text;
         }
      }
   }

   private static boolean isKnownNiceComponent(String id) {
      return switch (id) {
         case "custom_name", "item_name", "lore", "enchantments", "stored_enchantments", "attribute_modifiers", "max_damage", "damage", "unbreakable", "max_stack_size", "use_effects", "minimum_attack_charge", "damage_type", "item_model", "custom_model_data", "tooltip_display", "repair_cost", "creative_slot_lock", "enchantment_glint_override", "intangible_projectile", "food", "consumable", "use_remainder", "use_cooldown", "damage_resistant", "tool", "weapon", "attack_range", "enchantable", "equippable", "repairable", "glider", "tooltip_style", "death_protection", "blocks_attacks", "piercing_weapon", "kinetic_weapon", "swing_animation", "rarity", "dye", "dyed_color", "map_color", "container", "bundle_contents", "charged_projectiles", "potion_contents", "potion_duration_scale", "suspicious_stew_effects", "trim", "debug_stick_state", "instrument", "provides_trim_material", "ominous_bottle_amplifier", "jukebox_playable", "provides_banner_patterns", "recipes", "lodestone_tracker", "firework_explosion", "fireworks", "profile", "note_block_sound", "base_color", "pot_decorations", "block_state", "bees", "lock", "container_loot", "block_entity_data", "bucket_entity_data", "entity_data", "map_id", "map_decorations", "map_post_processing", "banner_patterns", "written_book_content", "writable_book_content", "custom_data" -> true;
         default -> false;
      };
   }

   public static String prettySnbt(String text) {
      return String.join("\n", prettySnbtLines(text));
   }

   public static List<String> prettySnbtLines(String text) {
      if (text != null && !text.isBlank()) {
         List<String> out = new ArrayList<>();
         StringBuilder current = new StringBuilder();
         int indent = 0;
         char quote = 0;
         boolean escape = false;

         for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
               current.append(ch);
               if (escape) {
                  escape = false;
               } else if (ch == '\\') {
                  escape = true;
               } else if (ch == quote) {
                  quote = 0;
               }
            } else if (ch != '"' && ch != '\'') {
               switch (ch) {
                  case ',':
                     current.append(ch);
                     emitPrettyLine(out, current, indent);
                     break;
                  case '[':
                  case '{':
                     current.append(ch);
                     emitPrettyLine(out, current, indent);
                     indent++;
                     skipWhitespace(text, i);
                     break;
                  case ']':
                  case '}':
                     emitPrettyLine(out, current, indent);
                     indent = Math.max(0, indent - 1);
                     current.append(ch);
                     if (i + 1 < text.length() && text.charAt(i + 1) == ',') {
                        current.append(',');
                        i++;
                     }

                     emitPrettyLine(out, current, indent);
                     break;
                  default:
                     if (!Character.isWhitespace(ch)) {
                        current.append(ch);
                     }
               }
            } else {
               quote = ch;
               current.append(ch);
            }
         }

         emitPrettyLine(out, current, indent);
         return out.isEmpty() ? List.of(text) : out;
      } else {
         return List.of("{}");
      }
   }

   private static int skipWhitespace(String text, int index) {
      return index;
   }

   private static void emitPrettyLine(List<String> out, StringBuilder current, int indent) {
      if (!current.isEmpty()) {
         String line = current.toString().trim();
         current.setLength(0);
         if (!line.isEmpty()) {
            out.add("  ".repeat(Math.max(0, indent)) + line);
         }
      }
   }

   public static List<RiptideItemNbtInspector.TextToken> tokenizeStructuredText(String text, int fallbackColor) {
      if (text != null && !text.isEmpty()) {
         List<RiptideItemNbtInspector.TextToken> tokens = new ArrayList<>();
         int i = 0;

         while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
               int start = i++;

               while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                  i++;
               }

               tokens.add(new RiptideItemNbtInspector.TextToken(text.substring(start, i), fallbackColor));
            } else if (ch != '"' && ch != '\'') {
               if ("{}[](),:=".indexOf(ch) >= 0) {
                  tokens.add(new RiptideItemNbtInspector.TextToken(String.valueOf(ch), RiptideColors.packetGray()));
                  i++;
               } else if (ch == '!') {
                  tokens.add(new RiptideItemNbtInspector.TextToken("!", RiptideColors.dangerText()));
                  i++;
               } else {
                  int start = i++;

                  while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && "{}[](),:=\"'".indexOf(text.charAt(i)) < 0) {
                     i++;
                  }

                  String word = text.substring(start, i);
                  int color = tokenColor(text, i, word, fallbackColor);
                  tokens.add(new RiptideItemNbtInspector.TextToken(word, color));
               }
            } else {
               int start = i++;
               boolean escape = false;

               while (true) {
                  if (i < text.length()) {
                     char c = text.charAt(i++);
                     if (escape) {
                        escape = false;
                        continue;
                     }

                     if (c == '\\') {
                        escape = true;
                        continue;
                     }

                     if (c != ch) {
                        continue;
                     }
                  }

                  tokens.add(new RiptideItemNbtInspector.TextToken(text.substring(start, i), RiptideColors.packetGreen()));
                  break;
               }
            }
         }

         return List.copyOf(tokens);
      } else {
         return List.of(new RiptideItemNbtInspector.TextToken("", fallbackColor));
      }
   }

   private static int tokenColor(String text, int tokenEnd, String word, int fallbackColor) {
      String lower = word.toLowerCase(Locale.ROOT);
      int next = tokenEnd;

      while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
         next++;
      }

      if (next >= text.length() || text.charAt(next) != ':' && text.charAt(next) != '=') {
         if (lower.equals("true") || lower.equals("false")) {
            return RiptideColors.packetPink();
         } else if (lower.equals("null")) {
            return RiptideColors.textMuted();
         } else if (word.matches("[-+]?\\d+(\\.\\d+)?[bBsSlLfFdD]?")) {
            return RiptideColors.packetYellow();
         } else {
            return word.contains(":") ? RiptideColors.packetBlue() : fallbackColor;
         }
      } else {
         return RiptideColors.packetCyan();
      }
   }

   private record ComponentSummary<T>(DataComponentType<T> type, String label) {
   }

   public interface Inspection {
      String title();

      String windowTitle();

      String subject();

      ItemStack stack();

      List<RiptideItemNbtInspector.InspectionLine> niceLines();

      List<RiptideItemNbtInspector.InspectionLine> rawLines();

      String prettyCopyText();

      String rawCopyText();
   }

   private static final class InspectionBuilder {
      private final String title;
      private final List<RiptideItemNbtInspector.InspectionLine> lines = new ArrayList<>();

      private InspectionBuilder(String title) {
         this.title = title != null && !title.isBlank() ? title : "Item NBT";
      }

      private void section(String text, int color) {
         this.line("[" + text + "]", color);
      }

      private void line(String text, int color) {
         this.lines.add(new RiptideItemNbtInspector.InspectionLine(text == null ? "" : text, color));
      }

      private void structuredLine(String text) {
         String safe = text == null ? "" : text;
         this.lines
            .add(
               new RiptideItemNbtInspector.InspectionLine(
                  safe, RiptideColors.packetWhite(), RiptideItemNbtInspector.tokenizeStructuredText(safe, RiptideColors.packetWhite())
               )
            );
      }

      private void blank() {
         this.lines.add(new RiptideItemNbtInspector.InspectionLine("", RiptideColors.textMuted()));
      }

      private List<RiptideItemNbtInspector.InspectionLine> buildLines() {
         return List.copyOf(this.lines);
      }

      private String copyText() {
         StringBuilder out = new StringBuilder(this.title);

         for (RiptideItemNbtInspector.InspectionLine line : this.lines) {
            out.append('\n').append(line.text());
         }

         return out.toString();
      }
   }

   public record InspectionLine(String text, int color, List<RiptideItemNbtInspector.TextToken> tokens) {
      public InspectionLine(String text, int color) {
         this(text, color, List.of());
      }
   }

   public record ItemInspection(
      String title,
      ItemStack stack,
      List<RiptideItemNbtInspector.InspectionLine> niceLines,
      List<RiptideItemNbtInspector.InspectionLine> rawLines,
      String prettyCopyText,
      String rawCopyText,
      String giveCommand
   ) implements RiptideItemNbtInspector.Inspection {
      @Override
      public String windowTitle() {
         return "Item NBT - " + this.title;
      }

      @Override
      public String subject() {
         return "item";
      }
   }

   public record TextToken(String text, int color) {
   }
}
