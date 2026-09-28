package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

public final class RiptideAutoTool {
   private RiptideAutoTool() {
   }

   public static float destroySpeed(Minecraft mc, ItemStack stack, BlockState state) {
      if (stack != null && !stack.isEmpty() && state != null) {
         float speed;
         try {
            speed = stack.getDestroySpeed(state);
         } catch (Throwable var6) {
            return 0.0F;
         }

         if (speed <= 1.0F && stack.getItem() == Items.SHEARS) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if ("cobweb".equals(path) || path.endsWith("_leaves")) {
               speed = 15.0F;
            } else if (path.endsWith("_wool")) {
               speed = 5.0F;
            }
         }

         if (speed > 1.0F) {
            int efficiency = efficiencyLevel(mc, stack);
            if (efficiency > 0) {
               float addition = (float)efficiency * efficiency + 1.0F;
               speed += Math.max(0.0F, Math.min(1024.0F, addition));
            }
         }

         return speed;
      } else {
         return 0.0F;
      }
   }

   private static int efficiencyLevel(Minecraft mc, ItemStack stack) {
      return enchantLevel(mc, stack, Enchantments.EFFICIENCY);
   }

   public static boolean hasSilkTouch(Minecraft mc, ItemStack stack) {
      return stack != null && !stack.isEmpty() ? enchantLevel(mc, stack, Enchantments.SILK_TOUCH) > 0 : false;
   }

   private static int enchantLevel(Minecraft mc, ItemStack stack, ResourceKey<Enchantment> key) {
      try {
         if (mc != null && mc.level != null) {
            Reference<Enchantment> holder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
         } else {
            return 0;
         }
      } catch (Throwable var4) {
         return 0;
      }
   }

   public static float heldDestroySpeed(Minecraft mc, BlockState state) {
      return mc != null && mc.player != null ? destroySpeed(mc, mc.player.getMainHandItem(), state) : 0.0F;
   }

   public static int bestToolSlot(Minecraft mc, BlockState state, int limit, boolean ignoreDurability) {
      return bestToolSlot(mc, state, limit, ignoreDurability, false);
   }

   public static int bestToolSlot(Minecraft mc, BlockState state, int limit, boolean ignoreDurability, boolean requireSilkTouch) {
      if (mc != null && mc.player != null && state != null) {
         int best = -1;
         float bestSpeed = -1.0F;
         int selected = mc.player.getInventory().getSelectedSlot();

         for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && (ignoreDurability || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 2)) {
               float speed = destroySpeed(mc, stack, state);
               if (!(speed <= 1.0F) && (!requireSilkTouch || hasSilkTouch(mc, stack))) {
                  boolean better = speed > bestSpeed || speed == bestSpeed && best >= 0 && nearness(slot, selected) < nearness(best, selected);
                  if (better) {
                     bestSpeed = speed;
                     best = slot;
                  }
               }
            }
         }

         return best;
      } else {
         return -1;
      }
   }

   private static int nearness(int slot, int selected) {
      return slot < 9 ? Math.abs(slot - selected) : 100 + slot;
   }

   public static boolean equipBestTool(Minecraft mc, BlockState state, boolean considerInventory, boolean ignoreDurability) {
      return equipBestTool(mc, state, considerInventory, ignoreDurability, false);
   }

   public static boolean equipBestTool(Minecraft mc, BlockState state, boolean considerInventory, boolean ignoreDurability, boolean preferSilkTouch) {
      if (mc == null || mc.player == null || state == null) {
         return false;
      } else if (mc.player.hasInfiniteMaterials()) {
         return true;
      } else {
         int limit = considerInventory ? 36 : 9;
         int best = -1;
         if (preferSilkTouch) {
            best = bestToolSlot(mc, state, limit, ignoreDurability, true);
         }

         if (best < 0) {
            best = bestToolSlot(mc, state, limit, ignoreDurability, false);
         }

         if (best < 0) {
            return false;
         } else {
            int selected = mc.player.getInventory().getSelectedSlot();
            ItemStack bestStack = mc.player.getInventory().getItem(best);
            ItemStack heldStack = mc.player.getInventory().getItem(selected);
            float bestSpeed = destroySpeed(mc, bestStack, state);
            float heldSpeed = destroySpeed(mc, heldStack, state);
            boolean silkChosen = preferSilkTouch && bestSpeed > 1.0F && hasSilkTouch(mc, bestStack);
            if (silkChosen) {
               if (heldSpeed > 1.0F && hasSilkTouch(mc, heldStack)) {
                  return true;
               }
            } else if (bestSpeed <= heldSpeed) {
               return true;
            }

            if (best < 9) {
               RiptideInventoryHelper.selectHotbarSlot(mc, best);
               return true;
            } else if (!RiptideInventoryHelper.swapInventorySlots(mc, best, selected)) {
               return false;
            } else {
               RiptideInventoryHelper.selectHotbarSlot(mc, selected);
               return true;
            }
         }
      }
   }
}
