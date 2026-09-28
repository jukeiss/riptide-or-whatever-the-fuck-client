package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class RiptideBlockNbtInspector {
   private static final int RAW_DISPLAY_CHAR_CAP = 40000;

   private RiptideBlockNbtInspector() {
   }

   public static RiptideBlockNbtInspector.BlockInspection inspect(CompoundTag source, List<ItemStack> containerItems, String note) {
      CompoundTag root = source == null ? new CompoundTag() : source.copy();
      List<ItemStack> items = copyItems(containerItems);
      CompoundTag state = root.getCompound("state").orElse(new CompoundTag());
      String blockId = state.getStringOr("Name", "minecraft:air");
      String raw = root.toString();
      List<RiptideItemNbtInspector.InspectionLine> nice = new ArrayList<>();
      section(nice, "Identity", RiptideColors.packetLightYellow());
      line(nice, "Block: " + blockId, RiptideColors.packetWhite());
      line(nice, "State: " + root.getStringOr("block_state", blockId), RiptideColors.textSecondary());
      line(nice, "Dimension: " + root.getStringOr("dimension", "<unknown>"), RiptideColors.textSecondary());
      int[] pos = root.getIntArray("position").orElse(new int[0]);
      line(nice, "Position: " + position(pos), RiptideColors.textSecondary());
      line(nice, "Source: " + root.getStringOr("source", "client"), RiptideColors.textMuted());
      CompoundTag entity = (CompoundTag)root.getCompound("block_entity").orElse(null);
      if (entity != null) {
         blank(nice);
         section(nice, "Block Entity", RiptideColors.packetCyan());
         line(nice, "Type: " + entity.getStringOr("id", "<unknown>"), RiptideColors.packetWhite());
         line(nice, "Fields: " + entity.size(), RiptideColors.textSecondary());
      }

      if (!items.isEmpty() || root.getBooleanOr("contents_available", false)) {
         blank(nice);
         section(nice, "Contents", RiptideColors.packetGreen());
         line(nice, "Slots: " + root.getIntOr("container_slots", items.size()), RiptideColors.textSecondary());
         int nonEmpty = 0;

         for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack != null && !stack.isEmpty()) {
               nonEmpty++;
               String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
               line(nice, "Slot " + slot + ": " + stack.getCount() + "x " + id + " (" + stack.getHoverName().getString() + ")", RiptideColors.packetWhite());
            }
         }

         if (nonEmpty == 0) {
            line(nice, "Container is empty.", RiptideColors.textMuted());
         }
      }

      if (note != null && !note.isBlank()) {
         blank(nice);
         section(nice, "Access", RiptideColors.packetOrange());
         line(nice, note, RiptideColors.textSecondary());
      }

      List<RiptideItemNbtInspector.InspectionLine> rawLines = new ArrayList<>();
      section(rawLines, "Raw Block SNBT", RiptideColors.packetLightYellow());
      boolean truncated = raw.length() > 40000;
      String shown = truncated ? raw.substring(0, 40000) : raw;

      for (String rawLine : RiptideItemNbtInspector.prettySnbtLines(shown)) {
         rawLines.add(
            new RiptideItemNbtInspector.InspectionLine(
               rawLine, RiptideColors.packetWhite(), RiptideItemNbtInspector.tokenizeStructuredText(rawLine, RiptideColors.packetWhite())
            )
         );
      }

      if (truncated) {
         line(rawLines, "... (display truncated; Copy Raw keeps everything)", RiptideColors.textMuted());
      }

      StringBuilder pretty = new StringBuilder("Block NBT - ").append(blockId);

      for (RiptideItemNbtInspector.InspectionLine inspectionLine : nice) {
         pretty.append('\n').append(inspectionLine.text());
      }

      return new RiptideBlockNbtInspector.BlockInspection(blockId, List.copyOf(nice), List.copyOf(rawLines), pretty.toString(), raw);
   }

   private static List<ItemStack> copyItems(List<ItemStack> source) {
      if (source != null && !source.isEmpty()) {
         List<ItemStack> copy = new ArrayList<>(source.size());

         for (ItemStack stack : source) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
         }

         return List.copyOf(copy);
      } else {
         return List.of();
      }
   }

   private static String position(int[] pos) {
      return pos.length >= 3 ? pos[0] + ", " + pos[1] + ", " + pos[2] : "<unknown>";
   }

   private static void section(List<RiptideItemNbtInspector.InspectionLine> lines, String title, int color) {
      line(lines, "[" + title + "]", color);
   }

   private static void line(List<RiptideItemNbtInspector.InspectionLine> lines, String text, int color) {
      lines.add(new RiptideItemNbtInspector.InspectionLine(text == null ? "" : text, color));
   }

   private static void blank(List<RiptideItemNbtInspector.InspectionLine> lines) {
      line(lines, "", RiptideColors.textMuted());
   }

   public record BlockInspection(
      String title,
      List<RiptideItemNbtInspector.InspectionLine> niceLines,
      List<RiptideItemNbtInspector.InspectionLine> rawLines,
      String prettyCopyText,
      String rawCopyText
   ) implements RiptideItemNbtInspector.Inspection {
      @Override
      public String windowTitle() {
         return "Block NBT - " + this.title;
      }

      @Override
      public String subject() {
         return "block";
      }

      @Override
      public ItemStack stack() {
         return ItemStack.EMPTY;
      }
   }
}
