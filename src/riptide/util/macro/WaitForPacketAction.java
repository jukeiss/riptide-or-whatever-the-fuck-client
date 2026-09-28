package riptide.util.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import riptide.util.RiptidePacketNamer;

public class WaitForPacketAction implements MacroAction, MacroCaptureOutput {
   public static final String C2S_PREFIX = "C2S:";
   public static final String S2C_PREFIX = "S2C:";
   public String packetName = "";
   public List<String> packetNames = new ArrayList<>();
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";
   public transient volatile Packet<?> matchedPacket;
   public transient volatile String matchedDirection = "";
   private boolean enabled = true;

   public WaitForPacketAction() {
   }

   public WaitForPacketAction(String packetName) {
      this.packetName = packetName;
   }

   public static String getDirection(String target) {
      if (target == null) {
         return "";
      } else {
         String trimmed = target.trim();
         if (trimmed.regionMatches(true, 0, "C2S:", 0, "C2S:".length())) {
            return "C2S";
         } else {
            return trimmed.regionMatches(true, 0, "S2C:", 0, "S2C:".length()) ? "S2C" : "";
         }
      }
   }

   public static String getPacketName(String target) {
      if (target == null) {
         return "";
      } else {
         String trimmed = target.trim();
         if (trimmed.regionMatches(true, 0, "C2S:", 0, "C2S:".length())) {
            return trimmed.substring("C2S:".length()).trim();
         } else {
            return trimmed.regionMatches(true, 0, "S2C:", 0, "S2C:".length()) ? trimmed.substring("S2C:".length()).trim() : trimmed;
         }
      }
   }

   public static String normalizeTarget(String target) {
      String direction = getDirection(target);
      String packet = getPacketName(target);
      if (packet.isEmpty()) {
         return "";
      } else {
         return direction.isEmpty() ? packet : direction + ":" + packet;
      }
   }

   public static String withDirection(String direction, String packetName) {
      String packet = getPacketName(packetName);
      if (packet.isEmpty()) {
         return "";
      } else if ("C2S".equalsIgnoreCase(direction)) {
         return "C2S:" + packet;
      } else {
         return "S2C".equalsIgnoreCase(direction) ? "S2C:" + packet : packet;
      }
   }

   public static String getDisplayLabel(String target) {
      String packet = getPacketName(target);
      String friendly = RiptidePacketNamer.getFriendlyName(packet);
      String direction = getDirection(target);
      return direction.isEmpty() ? friendly : direction + " " + friendly;
   }

   public List<String> effectiveList() {
      if (!this.packetNames.isEmpty()) {
         List<String> normalized = new ArrayList<>();

         for (String target : this.packetNames) {
            String safe = normalizeTarget(target);
            if (!safe.isEmpty()) {
               normalized.add(safe);
            }
         }

         if (!normalized.isEmpty()) {
            return normalized;
         }
      }

      String legacy = normalizeTarget(this.packetName);
      return !legacy.isEmpty() ? Collections.singletonList(legacy) : Collections.emptyList();
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("packetName", this.packetName);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      ListTag list = new ListTag();

      for (String n : this.packetNames) {
         list.add(StringTag.valueOf(n));
      }

      tag.put("packetNames", list);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("packetName")) {
         this.packetName = tag.getStringOr("packetName", "");
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
      this.packetNames.clear();
      if (tag.contains("packetNames")) {
         ListTag list = (ListTag)tag.get("packetNames");
         if (list != null) {
            for (int i = 0; i < list.size(); i++) {
               this.packetNames.add(list.getString(i).orElse(""));
            }
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_PACKET;
   }

   @Override
   public String getDisplayName() {
      List<String> eff = this.effectiveList();
      if (eff.isEmpty()) {
         return "Wait Pkt: Any";
      } else if (eff.size() == 1) {
         return "Wait Pkt: " + getDisplayLabel(eff.get(0));
      } else {
         Map<String, Integer> counts = new LinkedHashMap<>();

         for (String n : eff) {
            counts.merge(n, 1, Integer::sum);
         }

         if (counts.size() == 1) {
            String n = counts.keySet().iterator().next();
            int c = counts.get(n);
            return "Wait Pkt: " + getDisplayLabel(n) + " x" + c;
         } else {
            return "Wait Pkts (" + eff.size() + ")";
         }
      }
   }

   @Override
   public String getIcon() {
      return "Ptk";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }
}
