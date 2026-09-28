package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import riptide.modules.RiptideModule;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideSharedState;

public class DelayPacketsAction implements MacroAction {
   public DelayPacketsAction.DelayMode mode = DelayPacketsAction.DelayMode.ENABLE;
   public boolean flushOnDisable = true;
   public List<String> c2sPacketNames = new ArrayList<>();
   public List<String> s2cPacketNames = new ArrayList<>();
   private boolean enabled = true;

   public void cycleMode() {
      this.mode = DelayPacketsAction.DelayMode.values()[(this.mode.ordinal() + 1) % DelayPacketsAction.DelayMode.values().length];
   }

   public void cycleModeBackwards() {
      this.mode = DelayPacketsAction.DelayMode.values()[(this.mode.ordinal() - 1 + DelayPacketsAction.DelayMode.values().length)
         % DelayPacketsAction.DelayMode.values().length];
   }

   public void applyDefaultPreset() {
      this.c2sPacketNames.clear();
      this.s2cPacketNames.clear();
      String[] defaultNames = new String[]{
         "ServerboundContainerClickPacket",
         "ServerboundContainerButtonClickPacket",
         "ServerboundSetCreativeModeSlotPacket",
         "ServerboundPlayerActionPacket",
         "ServerboundUseItemPacket"
      };

      for (String target : defaultNames) {
         String targetNoSuffix = target.endsWith("Packet") ? target.substring(0, target.length() - 6) : target;

         for (Class<?> clz : RiptidePacketRegistry.getC2SPackets()) {
            String registryName = RiptidePacketRegistry.getName((Class<? extends Packet<?>>)clz);
            if (registryName != null && (registryName.equals(target) || registryName.equals(targetNoSuffix))) {
               this.c2sPacketNames.add(RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)clz));
               break;
            }
         }
      }
   }

   public void applyModulePreset() {
      this.c2sPacketNames.clear();
      this.s2cPacketNames.clear();
      RiptideSharedState shared = RiptideSharedState.get();

      for (Class<?> clz : shared.getC2SPackets()) {
         this.c2sPacketNames.add(RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)clz));
      }

      for (Class<?> clz : shared.getS2CPackets()) {
         this.s2cPacketNames.add(RiptidePacketNamer.getFriendlyName((Class<? extends Packet<?>>)clz));
      }
   }

   @Override
   public void execute(Minecraft mc) {
      RiptideSharedState shared = RiptideSharedState.get();
      RiptideModule module = RiptideModule.get();
      boolean shouldEnable = this.mode == DelayPacketsAction.DelayMode.ENABLE;
      int flushed = 0;
      if (!shouldEnable && this.flushOnDisable && mc.getConnection() != null) {
         flushed = shared.flushDelayedPackets(mc.getConnection());
      }

      if (this.c2sPacketNames.isEmpty() && this.s2cPacketNames.isEmpty()) {
         shared.setC2SPackets(Set.of());
         shared.setS2CPackets(Set.of());
         shared.setUseCustomPackets(false);
         shared.setDelayGuiPackets(false);
         module.notifyDelayPacketsUiResult(false, flushed);
      } else {
         Set<Class<? extends Packet<?>>> resolvedC2S = this.resolvePackets(this.c2sPacketNames, true);
         Set<Class<? extends Packet<?>>> resolvedS2C = this.resolvePackets(this.s2cPacketNames, false);
         shared.setC2SPackets(resolvedC2S);
         shared.setS2CPackets(resolvedS2C);
         shared.setUseCustomPackets(true);
         shared.setDelayGuiPackets(shouldEnable);
         module.notifyDelayPacketsUiResult(shouldEnable, flushed);
      }
   }

   private Set<Class<? extends Packet<?>>> resolvePackets(List<String> names, boolean isC2S) {
      Set<Class<? extends Packet<?>>> result = new HashSet<>();
      Set<Class<? extends Packet<?>>> pool = isC2S ? RiptidePacketRegistry.getC2SPackets() : RiptidePacketRegistry.getS2CPackets();

      for (String name : names) {
         String trimmed = name.trim();
         if (!trimmed.isEmpty()) {
            String withSuffix = trimmed.endsWith("Packet") ? trimmed : trimmed + "Packet";

            for (Class<? extends Packet<?>> clazz : pool) {
               String friendlyName = RiptidePacketNamer.getFriendlyName(clazz);
               if (friendlyName.equalsIgnoreCase(trimmed)
                  || friendlyName.equalsIgnoreCase(withSuffix)
                  || clazz.getSimpleName().equalsIgnoreCase(trimmed)
                  || clazz.getSimpleName().equalsIgnoreCase(withSuffix)) {
                  result.add(clazz);
                  break;
               }
            }
         }
      }

      return result;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.DELAY_PACKETS;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "DELAY_PACKETS");
      tag.putString("mode", this.mode.name());
      tag.putBoolean("flushOnDisable", this.flushOnDisable);
      ListTag c2sList = new ListTag();

      for (String n : this.c2sPacketNames) {
         c2sList.add(StringTag.valueOf(n));
      }

      tag.put("c2sPackets", c2sList);
      ListTag s2cList = new ListTag();

      for (String n : this.s2cPacketNames) {
         s2cList.add(StringTag.valueOf(n));
      }

      tag.put("s2cPackets", s2cList);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("mode")) {
         try {
            this.mode = DelayPacketsAction.DelayMode.valueOf(tag.getStringOr("mode", "ENABLE"));
         } catch (IllegalArgumentException var4) {
            this.mode = DelayPacketsAction.DelayMode.ENABLE;
         }
      }

      this.flushOnDisable = tag.getBooleanOr("flushOnDisable", true);
      this.c2sPacketNames.clear();
      if (tag.contains("c2sPackets")) {
         ListTag c2sList = (ListTag)tag.get("c2sPackets");
         if (c2sList != null) {
            for (int i = 0; i < c2sList.size(); i++) {
               this.c2sPacketNames.add(c2sList.getString(i).orElse(""));
            }
         }
      }

      this.s2cPacketNames.clear();
      if (tag.contains("s2cPackets")) {
         ListTag s2cList = (ListTag)tag.get("s2cPackets");
         if (s2cList != null) {
            for (int i = 0; i < s2cList.size(); i++) {
               this.s2cPacketNames.add(s2cList.getString(i).orElse(""));
            }
         }
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String base = this.mode == DelayPacketsAction.DelayMode.ENABLE ? "Delay Pkts: ON" : "Delay Pkts: OFF";
      int total = this.c2sPacketNames.size() + this.s2cPacketNames.size();
      if (this.mode == DelayPacketsAction.DelayMode.ENABLE && total > 0) {
         base = base + " (" + total + " pkts)";
      }

      return base;
   }

   @Override
   public String getIcon() {
      return "D";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum DelayMode {
      ENABLE,
      DISABLE;
   }
}
