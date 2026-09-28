package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.HitResult.Type;
import riptide.util.RiptideClientMessaging;

public class AssertAction implements MacroAction {
   public AssertAction.CheckType check = AssertAction.CheckType.CONNECTION;
   public AssertAction.FailureBehavior failureBehavior = AssertAction.FailureBehavior.STOP_MACRO;
   public String itemName = "";
   public String guiType = "ANY";
   public String entityId = "";
   public String message = "";

   @Override
   public void execute(Minecraft mc) {
      if (!this.passes(mc)) {
         String out;
         if (this.message != null && !this.message.isBlank()) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(this.message, mc);
            if (!resolved.success()) {
               return;
            }

            out = resolved.value();
         } else {
            out = "Macro assert failed: " + this.check;
         }

         RiptideClientMessaging.sendPrefixed("§c" + out);
         if (this.failureBehavior == AssertAction.FailureBehavior.STOP_MACRO) {
            MacroExecutor.stopCurrentActionRun();
         }
      }
   }

   public boolean passes(Minecraft mc) {
      if (mc == null) {
         return false;
      } else {
         MacroTemplate.Resolution itemResolution = MacroVariables.resolve(this.itemName, mc);
         MacroTemplate.Resolution entityResolution = MacroVariables.resolve(this.entityId, mc);
         MacroTemplate.Resolution guiResolution = MacroVariables.resolve(this.guiType, mc);
         if (itemResolution.success() && entityResolution.success() && guiResolution.success()) {
            return switch (this.check) {
               case HELD_ITEM -> mc.player != null
                  && ItemTarget.fromLegacyEntry(itemResolution.value()).score(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot()) >= 0;
               case INVENTORY_ITEM -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry(itemResolution.value()));
               case GUI_TYPE -> MacroGuiMatcher.matches(mc.gui.screen(), guiResolution.value(), "");
               case LOOKING_AT_ENTITY -> mc.crosshairPickEntity != null && this.matchesEntity(mc.crosshairPickEntity, entityResolution.value());
               case LOOKING_AT_CONTAINER_ENTITY -> mc.crosshairPickEntity != null
                  && this.matchesEntity(mc.crosshairPickEntity, entityResolution.value())
                  && this.isContainerEntity(mc.crosshairPickEntity);
               case MOUNTED_ENTITY -> mc.player != null
                  && mc.player.getVehicle() != null
                  && this.matchesEntity(mc.player.getVehicle(), entityResolution.value());
               case HAS_BUNDLE -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry("minecraft:bundle"));
               case BUNDLE_V2_READY -> this.isBundleV2Ready(mc);
               case HAS_WRITABLE_BOOK -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry("minecraft:writable_book"));
               case CONNECTION -> mc.getConnection() != null;
               case LOOKING_AT_BLOCK -> mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK;
            };
         } else {
            return false;
         }
      }
   }

   private boolean isBundleV2Ready(Minecraft mc) {
      if (mc != null && mc.player != null && mc.player.containerMenu instanceof InventoryMenu) {
         ItemStack stack = mc.player.getInventory().getItem(0);
         if (stack != null && !stack.isEmpty() && stack.is(Items.BUNDLE)) {
            BundleContents contents = (BundleContents)stack.get(DataComponents.BUNDLE_CONTENTS);
            return contents != null && contents.size() == 1;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean matchesEntity(Entity entity, String expected) {
      if (entity == null) {
         return false;
      } else if (expected != null && !expected.isBlank()) {
         String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         return type.equalsIgnoreCase(expected) || entity.getStringUUID().equalsIgnoreCase(expected) || entity.getName().getString().equalsIgnoreCase(expected);
      } else {
         return true;
      }
   }

   private boolean isContainerEntity(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
         return type.contains("chest_boat")
            || type.contains("chest_minecart")
            || type.contains("hopper_minecart")
            || type.contains("furnace_minecart")
            || type.contains("llama")
            || type.contains("donkey")
            || type.contains("mule");
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.ASSERT;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "ASSERT");
      tag.putString("check", this.check.name());
      tag.putString("failureBehavior", this.failureBehavior.name());
      tag.putString("itemName", this.itemName);
      tag.putString("guiType", this.guiType);
      tag.putString("entityId", this.entityId);
      tag.putString("message", this.message);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.check = MacroStringList.enumValue(AssertAction.CheckType.class, tag.getStringOr("check", "CONNECTION"), AssertAction.CheckType.CONNECTION);
      this.failureBehavior = MacroStringList.enumValue(
         AssertAction.FailureBehavior.class, tag.getStringOr("failureBehavior", "STOP_MACRO"), AssertAction.FailureBehavior.STOP_MACRO
      );
      this.itemName = tag.getStringOr("itemName", "");
      this.guiType = tag.getStringOr("guiType", "ANY");
      this.entityId = tag.getStringOr("entityId", "");
      this.message = tag.getStringOr("message", "");
   }

   @Override
   public String getDisplayName() {
      return "Assert " + this.check;
   }

   @Override
   public String getIcon() {
      return "!";
   }

   public static enum CheckType {
      HELD_ITEM,
      INVENTORY_ITEM,
      GUI_TYPE,
      LOOKING_AT_ENTITY,
      LOOKING_AT_CONTAINER_ENTITY,
      MOUNTED_ENTITY,
      HAS_BUNDLE,
      BUNDLE_V2_READY,
      HAS_WRITABLE_BOOK,
      CONNECTION,
      LOOKING_AT_BLOCK;
   }

   public static enum FailureBehavior {
      STOP_MACRO,
      WARN_ONLY;
   }
}
