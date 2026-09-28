package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class MissingAddonAction implements MacroAction {
   private String typeId;
   private CompoundTag raw;

   public MissingAddonAction() {
      this.typeId = "";
      this.raw = new CompoundTag();
   }

   public MissingAddonAction(String typeId, CompoundTag raw) {
      this.typeId = typeId == null ? "" : typeId;
      this.raw = raw == null ? new CompoundTag() : raw;
   }

   public String missingTypeId() {
      return this.typeId;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      return this.raw.copy();
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag != null) {
         this.raw = tag.copy();
         this.typeId = tag.getStringOr("type", this.typeId);
      }
   }

   @Override
   public String getTypeId() {
      return this.typeId;
   }

   @Override
   public String getDisplayName() {
      return "! Missing: " + this.typeId;
   }

   @Override
   public String getIcon() {
      return "!";
   }

   @Override
   public void sanitizeForSharing() {
      if (this.raw != null) {
         this.raw.remove("customFilePath");
         this.raw.remove("javaSource");
         this.raw.remove("payloadScript");
         this.raw.remove("payloadScriptEnabled");
         this.raw.remove("commandAfter");
      }
   }
}
