package riptide.util.multi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class MultiQuickAction {
   public static final int MAX_STEPS = 6;
   public String name = "";
   public final List<MultiQuickAction.Step> steps = new ArrayList<>();

   public MultiQuickAction() {
   }

   public MultiQuickAction(MultiQuickAction source) {
      if (source != null) {
         this.name = source.name;
         this.steps.addAll(source.steps);
         this.normalize();
      }
   }

   public MultiQuickAction(String name, String packetClass, String arguments) {
      this.name = name;
      if (packetClass != null && !packetClass.isBlank()) {
         this.steps.add(new MultiQuickAction.Step(packetClass, arguments));
      }

      this.normalize();
   }

   public boolean empty() {
      for (MultiQuickAction.Step step : this.steps) {
         if (!step.blank()) {
            return false;
         }
      }

      return true;
   }

   public String firstPacketClass() {
      for (MultiQuickAction.Step step : this.steps) {
         if (!step.blank()) {
            return step.packetClass();
         }
      }

      return "";
   }

   public int packetCount() {
      int count = 0;

      for (MultiQuickAction.Step step : this.steps) {
         if (!step.blank()) {
            count++;
         }
      }

      return count;
   }

   public String label(int index) {
      this.normalize();
      if (this.empty()) {
         return "Empty";
      } else if (this.name != null && !this.name.isBlank()) {
         return this.name;
      } else {
         String simple = shortLabel(this.firstPacketClass());
         if (simple.isBlank()) {
            return "Slot " + (index + 1);
         } else {
            int extra = this.packetCount() - 1;
            return extra > 0 ? simple + " +" + extra : simple;
         }
      }
   }

   public void normalize() {
      this.name = this.name == null ? "" : MultiManager.singleLine(this.name, 32);
      this.steps.removeIf(step -> step == null || step.blank());

      while (this.steps.size() > 6) {
         this.steps.remove(this.steps.size() - 1);
      }
   }

   public static String shortLabel(String packetClass) {
      if (packetClass != null && !packetClass.isBlank()) {
         int cut = Math.max(packetClass.lastIndexOf(46), packetClass.lastIndexOf(36));
         String simple = cut < 0 ? packetClass : packetClass.substring(cut + 1);
         simple = stripPrefix(stripPrefix(simple, "Serverbound"), "Clientbound");
         if (simple.endsWith("Packet")) {
            simple = simple.substring(0, simple.length() - "Packet".length());
         }

         return simple.isBlank() ? packetClass : simple;
      } else {
         return "";
      }
   }

   private static String stripPrefix(String value, String prefix) {
      return value.startsWith(prefix) && value.length() > prefix.length() ? value.substring(prefix.length()) : value;
   }

   CompoundTag toTag() {
      this.normalize();
      CompoundTag tag = new CompoundTag();
      tag.putString("name", this.name);
      ListTag list = new ListTag();

      for (MultiQuickAction.Step step : this.steps) {
         list.add(step.toTag());
      }

      tag.put("steps", list);
      return tag;
   }

   static MultiQuickAction fromTag(CompoundTag tag) {
      MultiQuickAction action = new MultiQuickAction();
      if (tag != null) {
         action.name = tag.getStringOr("name", "");
         ListTag list = tag.getListOrEmpty("steps");
         if (list.isEmpty()) {
            String packet = tag.getStringOr("packet", "");
            if (!packet.isBlank()) {
               action.steps.add(new MultiQuickAction.Step(packet, tag.getStringOr("args", "")));
            }
         } else {
            for (Tag value : list) {
               if (value instanceof CompoundTag compound) {
                  action.steps.add(MultiQuickAction.Step.fromTag(compound));
               }
            }
         }
      }

      action.normalize();
      return action;
   }

   public record Step(String packetClass, String arguments) {
      public Step(String packetClass, String arguments) {
         packetClass = packetClass == null ? "" : packetClass.trim();
         arguments = arguments == null ? "" : MultiManager.singleLine(arguments, 2048);
         this.packetClass = packetClass;
         this.arguments = arguments;
      }

      public boolean blank() {
         return this.packetClass.isBlank();
      }

      CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("packet", this.packetClass);
         tag.putString("args", this.arguments);
         return tag;
      }

      static MultiQuickAction.Step fromTag(CompoundTag tag) {
         return new MultiQuickAction.Step(tag.getStringOr("packet", ""), tag.getStringOr("args", ""));
      }
   }
}
