package riptide.util.macro;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideRegistryLabels;

public final class ItemTarget {
   public static final String TAG_SLOT = "slot";
   public static final String TAG_REGISTRY_ID = "registryId";
   public static final String TAG_DISPLAY = "display";
   public static final String TAG_TEXT_JSON = "textJson";
   public static final String TAG_MANUAL = "manual";
   public static final String TAG_TEMPLATE = "template";
   public int slot = -1;
   public String registryId = "";
   public String display = "";
   public String textJson = "";
   public boolean manual = false;
   public String template = "";

   public ItemTarget copy() {
      ItemTarget copy = new ItemTarget();
      copy.slot = this.slot;
      copy.registryId = this.registryId;
      copy.display = this.display;
      copy.textJson = this.textJson;
      copy.manual = this.manual;
      copy.template = this.template;
      return copy;
   }

   public boolean hasSlot() {
      return this.slot >= 0;
   }

   public boolean hasRegistryId() {
      return this.registryId != null && !this.registryId.isBlank();
   }

   public boolean hasDisplay() {
      return this.display != null && !this.display.isBlank();
   }

   public boolean hasRichText() {
      return this.textJson != null && !this.textJson.isBlank();
   }

   public boolean hasIdentity() {
      return this.hasRegistryId() || this.hasDisplay() || this.hasRichText();
   }

   public Component displayComponent() {
      Component rich = MacroExecutor.deserializeTextComponent(this.textJson);
      return rich != null ? rich.copy() : Component.literal(this.editorText());
   }

   public Component editorComponent(String editedValue) {
      String safeValue = editedValue == null ? "" : editedValue;
      Component rich = MacroExecutor.deserializeTextComponent(this.textJson);
      return (Component)(rich == null ? Component.literal(safeValue) : rebuildStyledComponent(rich, safeValue));
   }

   public ItemTarget withEditedDisplay(String editedValue) {
      ItemTarget target = this.copy();
      String safeValue = manualDisplayText(editedValue);
      target.display = safeValue;
      target.manual = true;
      target.textJson = "";
      target.template = safeValue.contains("{") ? safeValue : "";
      if (safeValue.isEmpty()) {
         target.registryId = "";
         return target;
      } else {
         return target;
      }
   }

   public ItemTarget withEditedRichDisplay(String editedValue) {
      String safeValue = editedValue == null ? "" : editedValue;
      ItemTarget target = this.copy();
      if (safeValue.isEmpty()) {
         target.display = "";
         target.textJson = "";
         target.registryId = "";
         target.manual = true;
         target.template = "";
         return target;
      } else {
         Component styled = this.editorComponent(safeValue);
         target.display = safeValue;
         target.textJson = styled == null ? "" : MacroExecutor.serializeTextComponent(styled);
         target.manual = false;
         target.template = safeValue.contains("{") ? safeValue : "";
         return target;
      }
   }

   public String editorText() {
      if (this.hasDisplay()) {
         return this.display;
      } else {
         return this.hasRegistryId() ? this.registryId : "";
      }
   }

   public String summaryText() {
      String name = this.displayLabel();
      if (this.hasSlot() && !name.isBlank()) {
         return "Slot " + this.slot + ": " + name;
      } else {
         return this.hasSlot() ? "Slot " + this.slot : name;
      }
   }

   public String displayLabel() {
      if (this.hasRichText()) {
         Component rich = MacroExecutor.deserializeTextComponent(this.textJson);
         if (rich != null && !rich.getString().isBlank()) {
            return rich.getString();
         }
      }

      if (this.hasDisplay()) {
         if (!this.hasRegistryId()) {
            return this.display;
         } else {
            String registryLabel = RiptideRegistryLabels.item(this.registryId);
            return !RiptideRegistryLabels.looksLikeRawIdentifierLabel(this.display, this.registryId) && !this.display.equalsIgnoreCase(registryLabel)
               ? this.display
               : registryLabel;
         }
      } else {
         return this.hasRegistryId() ? RiptideRegistryLabels.item(this.registryId) : "";
      }
   }

   public Component listComponent() {
      if (this.hasRichText()) {
         Component rich = MacroExecutor.deserializeTextComponent(this.textJson);
         if (rich != null) {
            return rich.copy();
         }
      }

      return Component.literal(this.displayLabel());
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      if (this.hasSlot()) {
         tag.putInt("slot", this.slot);
      }

      if (this.hasRegistryId()) {
         tag.putString("registryId", this.registryId);
      }

      if (this.hasDisplay()) {
         tag.putString("display", this.display);
      }

      if (this.hasRichText()) {
         tag.putString("textJson", this.textJson);
      }

      if (this.template != null && !this.template.isBlank()) {
         tag.putString("template", this.template);
      }

      tag.putBoolean("manual", this.manual);
      return tag;
   }

   public static ItemTarget fromTag(CompoundTag tag) {
      ItemTarget target = new ItemTarget();
      if (tag == null) {
         return target;
      } else {
         if (tag.contains("slot")) {
            target.slot = tag.getIntOr("slot", -1);
         }

         if (tag.contains("registryId")) {
            target.registryId = tag.getStringOr("registryId", "");
         }

         if (tag.contains("display")) {
            target.display = tag.getStringOr("display", "");
         }

         if (tag.contains("textJson")) {
            target.textJson = tag.getStringOr("textJson", "");
         }

         if (tag.contains("manual")) {
            target.manual = tag.getBooleanOr("manual", false);
         }

         target.template = tag.getStringOr("template", "");
         if (target.manual) {
            target.display = manualDisplayText(target.display);
            target.textJson = "";
         } else if (!target.hasRichText() && target.hasDisplay()) {
            target.display = manualDisplayText(target.display);
         }

         return target;
      }
   }

   public static ItemTarget fromElement(Tag element) {
      return element instanceof CompoundTag compound ? fromTag(compound) : fromLegacyEntry(element == null ? "" : element.asString().orElse(""));
   }

   public static List<ItemTarget> fromElementList(ListTag list) {
      List<ItemTarget> targets = new ArrayList<>();
      if (list == null) {
         return targets;
      } else {
         for (Tag element : list) {
            ItemTarget target = fromElement(element);
            if (target.hasSlot() || target.hasIdentity()) {
               targets.add(target);
            }
         }

         return targets;
      }
   }

   public static ListTag toTagList(List<ItemTarget> targets) {
      ListTag list = new ListTag();
      if (targets == null) {
         return list;
      } else {
         for (ItemTarget target : targets) {
            if (target != null && (target.hasSlot() || target.hasIdentity())) {
               list.add(target.toTag());
            }
         }

         return list;
      }
   }

   public static List<ItemTarget> copyList(List<ItemTarget> targets) {
      List<ItemTarget> copies = new ArrayList<>();
      if (targets == null) {
         return copies;
      } else {
         for (ItemTarget target : targets) {
            if (target != null) {
               copies.add(target.copy());
            }
         }

         return copies;
      }
   }

   public static ItemTarget manualText(String text) {
      ItemTarget target = new ItemTarget();
      target.display = manualDisplayText(text);
      target.manual = true;
      if (text != null && text.contains("{")) {
         target.template = text;
      }

      return target;
   }

   public static ItemTarget registry(String registryId) {
      ItemTarget target = new ItemTarget();
      target.registryId = registryId == null ? "" : registryId.trim();
      target.display = target.registryId;
      target.manual = false;
      return target;
   }

   public static ItemTarget slotOnly(int slot) {
      ItemTarget target = new ItemTarget();
      target.slot = slot;
      return target;
   }

   public static ItemTarget capture(ItemStack stack, int visibleSlot) {
      ItemTarget target = new ItemTarget();
      target.slot = visibleSlot;
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         target.registryId = id == null ? "" : id.toString();
         target.display = stack.getHoverName().getString();
         target.manual = false;
         if (shouldCaptureRichText(stack)) {
            target.textJson = MacroExecutor.serializeTextComponent(stack.getHoverName());
         }

         return target;
      } else {
         return target;
      }
   }

   public static ItemTarget fromLegacyEntry(String raw) {
      if (raw == null) {
         return new ItemTarget();
      } else {
         String trimmed = raw.trim();
         if (trimmed.isEmpty()) {
            return new ItemTarget();
         } else {
            ItemTarget target = new ItemTarget();
            String body = trimmed;
            if (trimmed.startsWith("#")) {
               int separator = trimmed.indexOf(124);
               String slotPart = separator >= 0 ? trimmed.substring(1, separator).trim() : trimmed.substring(1).trim();

               try {
                  int parsedSlot = Integer.parseInt(slotPart);
                  if (parsedSlot >= 0) {
                     target.slot = parsedSlot;
                  }
               } catch (NumberFormatException var7) {
                  return classifyFreeform(trimmed);
               }

               body = separator >= 0 && separator + 1 < trimmed.length() ? trimmed.substring(separator + 1).trim() : "";
            }

            if (!body.isBlank()) {
               ItemTarget bodyTarget = classifyFreeform(body);
               target.registryId = bodyTarget.registryId;
               target.display = bodyTarget.display;
               target.textJson = bodyTarget.textJson;
               target.manual = bodyTarget.manual;
            }

            return target;
         }
      }
   }

   public String toLegacyEntry() {
      String body = this.editorText();
      if (this.hasSlot() && body.isBlank()) {
         return "#" + this.slot;
      } else {
         return this.hasSlot() ? "#" + this.slot + "|" + body : body;
      }
   }

   public int score(ItemStack stack, int visibleSlot) {
      if (stack == null || stack.isEmpty()) {
         return -1;
      } else if (this.hasSlot() && this.slot != visibleSlot) {
         return -1;
      } else if (this.hasRichText()) {
         String stackJson = MacroExecutor.serializeTextComponent(stack.getHoverName());
         if (this.textJson.equals(stackJson)) {
            return 400;
         } else {
            return this.hasDisplay() && this.display.equals(stack.getHoverName().getString()) ? 350 : -1;
         }
      } else if (this.hasRegistryId()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (id == null) {
            return -1;
         } else {
            String fullId = id.toString();
            String rawPath = id.getPath();
            String spacedPath = rawPath.replace('_', ' ');
            if (this.registryId.equalsIgnoreCase(fullId)) {
               return 320;
            } else if (this.registryId.equalsIgnoreCase(rawPath)) {
               return 315;
            } else if (this.registryId.equalsIgnoreCase(spacedPath)) {
               return 310;
            } else {
               if (this.hasDisplay()) {
                  String normalizedDisplay = normalize(this.display);
                  if (normalizedDisplay.equals(normalize(fullId))) {
                     return 305;
                  }

                  if (normalizedDisplay.equals(normalize(rawPath))) {
                     return 304;
                  }

                  if (normalizedDisplay.equals(normalize(spacedPath))) {
                     return 303;
                  }
               }

               return -1;
            }
         }
      } else if (this.hasDisplay()) {
         String normalizedTarget = normalize(this.display);
         if (normalizedTarget.isBlank()) {
            return this.hasSlot() ? 100 : -1;
         } else {
            String normalizedDisplayx = normalize(stack.getHoverName().getString());
            if (normalizedTarget.equals(normalizedDisplayx)) {
               return this.manual ? 220 : 240;
            } else {
               Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
               if (id != null) {
                  String fullId = normalize(id.toString());
                  String rawPath = normalize(id.getPath());
                  String spacedPath = normalize(id.getPath().replace('_', ' '));
                  if (normalizedTarget.equals(fullId)) {
                     return this.manual ? 210 : 230;
                  }

                  if (normalizedTarget.equals(rawPath)) {
                     return this.manual ? 205 : 225;
                  }

                  if (normalizedTarget.equals(spacedPath)) {
                     return this.manual ? 200 : 220;
                  }
               }

               return -1;
            }
         }
      } else {
         return this.hasSlot() ? 100 : -1;
      }
   }

   public boolean matches(ItemStack stack, int visibleSlot) {
      return this.score(stack, visibleSlot) >= 0;
   }

   public static String normalize(String text) {
      if (text != null && !text.isBlank()) {
         String normalized = Normalizer.normalize(text, Form.NFKC).toLowerCase(Locale.ROOT);
         StringBuilder out = new StringBuilder(normalized.length());

         for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
               out.append(ch);
            } else if (Character.isWhitespace(ch) || ch == '_' || ch == '-' || ch == ':' || ch == '/' || ch == '.') {
               out.append(' ');
            } else if (ch != '\'' && ch != '"' && ch != '`' && ch != 8217) {
               out.append(' ');
            }
         }

         return out.toString().trim().replaceAll("\\s+", " ");
      } else {
         return "";
      }
   }

   private static ItemTarget classifyFreeform(String raw) {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty()) {
         return new ItemTarget();
      } else {
         ItemTarget target = new ItemTarget();
         if (looksLikeRegistryId(trimmed)) {
            target.registryId = trimmed;
            target.display = trimmed;
            target.manual = false;
            return target;
         } else {
            target.display = manualDisplayText(trimmed);
            target.manual = true;
            if (trimmed.contains("{")) {
               target.template = trimmed;
            }

            return target;
         }
      }
   }

   public ItemTarget resolveTemplate(Minecraft mc) {
      String source = this.template != null && !this.template.isBlank() ? this.template : this.editorText();
      if (source != null && source.indexOf(123) >= 0) {
         String trimmed = source.trim();
         if (trimmed.matches("\\{[A-Za-z_][A-Za-z0-9_.-]{0,63}}")) {
            String name = trimmed.substring(1, trimmed.length() - 1);
            Optional<MacroValue> value = MacroVariables.value(name);
            if (value.isPresent() && value.get().kind() == MacroValue.Kind.ITEM) {
               MacroValue item = value.get();
               ItemTarget resolved = new ItemTarget();
               resolved.slot = item.property("slot").map(v -> parseInt(v.value(), this.slot)).orElse(this.slot);
               resolved.registryId = item.property("id").map(MacroValue::value).orElse("");
               resolved.display = item.property("name").map(MacroValue::value).orElse(item.value());
               resolved.manual = false;
               return resolved;
            }
         }

         MacroTemplate.Resolution resolution = MacroVariables.resolve(source, mc);
         if (resolution.success() && !resolution.value().isBlank()) {
            ItemTarget resolved = fromLegacyEntry(resolution.value());
            if (this.hasSlot()) {
               resolved.slot = this.slot;
            }

            return resolved;
         } else {
            return null;
         }
      } else {
         return this.copy();
      }
   }

   private static int parseInt(String raw, int fallback) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }

   private static String manualDisplayText(String text) {
      return MacroExecutor.normalizeManualText(text);
   }

   private static boolean looksLikeRegistryId(String text) {
      if (text == null || text.isBlank()) {
         return false;
      } else {
         return text.contains(" ") ? false : text.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
      }
   }

   private static int wholeWordScore(String normalizedTarget, String normalizedCandidate, int baseScore) {
      if (!normalizedTarget.isBlank() && !normalizedCandidate.isBlank()) {
         String[] queryWords = normalizedTarget.split(" ");
         String[] candidateWords = normalizedCandidate.split(" ");
         int matches = 0;

         for (String queryWord : queryWords) {
            boolean found = false;

            for (String candidateWord : candidateWords) {
               if (queryWord.equals(candidateWord)) {
                  found = true;
                  matches++;
                  break;
               }
            }

            if (!found) {
               return -1;
            }
         }

         return baseScore + matches;
      } else {
         return -1;
      }
   }

   private static boolean shouldCaptureRichText(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Component stackName = stack.getHoverName();
         Component defaultName = stack.getItem().getName(stack);
         if (stackName != null && defaultName != null && !stackName.getString().equals(defaultName.getString())) {
            return true;
         } else {
            String json = MacroExecutor.serializeTextComponent(stackName);
            return json.contains("\"color\"")
               || json.contains("\"bold\"")
               || json.contains("\"italic\"")
               || json.contains("\"underlined\"")
               || json.contains("\"strikethrough\"")
               || json.contains("\"obfuscated\"");
         }
      } else {
         return false;
      }
   }

   private static Component rebuildStyledComponent(Component previousComponent, String editedValue) {
      String safeValue = editedValue == null ? "" : editedValue;
      if (previousComponent == null) {
         return Component.literal(safeValue);
      } else {
         String previousValue = previousComponent.getString();
         if (previousValue.equals(safeValue)) {
            return previousComponent.copy();
         } else if (safeValue.isEmpty()) {
            return Component.empty();
         } else if (previousValue.isEmpty()) {
            return Component.literal(safeValue);
         } else {
            List<Style> previousStyles = flattenStyles(previousComponent, previousValue.length());
            if (previousStyles.isEmpty()) {
               return Component.literal(safeValue);
            } else {
               int prefix = longestCommonPrefix(previousValue, safeValue);
               int suffix = longestCommonSuffix(previousValue, safeValue, prefix);
               int previousLength = previousValue.length();
               int nextLength = safeValue.length();
               MutableComponent rebuilt = Component.empty();
               StringBuilder segment = new StringBuilder();
               Style segmentStyle = null;

               for (int i = 0; i < nextLength; i++) {
                  Style style = styleForEditedIndex(previousStyles, previousLength, nextLength, prefix, suffix, i);
                  if (segmentStyle == null) {
                     segmentStyle = style;
                  } else if (!segmentStyle.equals(style)) {
                     rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle));
                     segment.setLength(0);
                     segmentStyle = style;
                  }

                  segment.append(safeValue.charAt(i));
               }

               if (!segment.isEmpty()) {
                  rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle == null ? Style.EMPTY : segmentStyle));
               }

               return rebuilt;
            }
         }
      }
   }

   private static List<Style> flattenStyles(Component text, int expectedLength) {
      if (text != null && expectedLength > 0) {
         List<Style> rawStyles = new ArrayList<>(expectedLength);
         text.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
               Style safeStyle = style == null ? Style.EMPTY : style;

               for (int i = 0; i < part.length(); i++) {
                  rawStyles.add(safeStyle);
               }
            }

            return Optional.empty();
         }, Style.EMPTY);
         List<Style> styles = new ArrayList<>(rawStyles);
         if (styles.isEmpty()) {
            for (int i = 0; i < expectedLength; i++) {
               styles.add(Style.EMPTY);
            }
         } else if (styles.size() < expectedLength) {
            Style fill = styles.get(styles.size() - 1);

            while (styles.size() < expectedLength) {
               styles.add(fill);
            }
         } else if (styles.size() > expectedLength) {
            styles = new ArrayList<>(styles.subList(0, expectedLength));
         }

         return styles;
      } else {
         return Collections.emptyList();
      }
   }

   private static int longestCommonPrefix(String left, String right) {
      int max = Math.min(left.length(), right.length());
      int index = 0;

      while (index < max && left.charAt(index) == right.charAt(index)) {
         index++;
      }

      return index;
   }

   private static int longestCommonSuffix(String previous, String next, int prefixLength) {
      int previousRemaining = previous.length() - prefixLength;
      int nextRemaining = next.length() - prefixLength;
      int max = Math.min(previousRemaining, nextRemaining);
      int suffix = 0;

      while (suffix < max && previous.charAt(previous.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
         suffix++;
      }

      return suffix;
   }

   private static Style styleForEditedIndex(List<Style> previousStyles, int previousLength, int nextLength, int prefixLength, int suffixLength, int nextIndex) {
      if (previousStyles.isEmpty()) {
         return Style.EMPTY;
      } else if (nextIndex < prefixLength) {
         return previousStyles.get(Math.min(nextIndex, previousStyles.size() - 1));
      } else {
         int nextSuffixStart = nextLength - suffixLength;
         int previousSuffixStart = previousLength - suffixLength;
         if (suffixLength > 0 && nextIndex >= nextSuffixStart) {
            int mappedIndex = previousSuffixStart + (nextIndex - nextSuffixStart);
            return previousStyles.get(Math.max(0, Math.min(mappedIndex, previousStyles.size() - 1)));
         } else {
            int anchorIndex;
            if (prefixLength > 0) {
               anchorIndex = prefixLength - 1;
            } else if (suffixLength > 0) {
               anchorIndex = previousSuffixStart;
            } else {
               anchorIndex = 0;
            }

            return previousStyles.get(Math.max(0, Math.min(anchorIndex, previousStyles.size() - 1)));
         }
      }
   }
}
