package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class PlayerArmorEspModule extends Module {
   private static volatile boolean hook;
   private static PlayerArmorEspModule cached;

   public PlayerArmorEspModule() {
      super("player-esp-plus", "PlayerESP+", ModuleCategory.RENDER, "Shows each player's name, armour, held item and stats above their head.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("range", "Range", 64, 8, 256, 4).description("How far away a player still shows a panel, in blocks.").build());
      this.add(new BoolSetting("self", "Show Self", false).description("Also show your own panel (visible in F5).").build());
      this.add(new BoolSetting("name", "Show Name", true).description("Draw the player's name as the header line.").build());
      this.add(new BoolSetting("health", "Show Health", true).description("Add a hearts/health line.").build());
      this.add(new BoolSetting("distance", "Show Distance", false).description("Add how far away they are.").build());
      this.add(new BoolSetting("ping", "Show Ping", false).description("Add their tab-list latency.").build());
      this.add(new BoolSetting("held", "Show Held", true).description("Add the item they're holding.").build());
      this.add(new BoolSetting("empty", "Show Empty Slots", true).description("Draw a dash for missing armour pieces instead of skipping them.").build());
      this.add(new BoolSetting("compact", "Compact Layout", true).description("Armour on top, then name, health and ping on one line. Off stacks one stat per line.").build());
      this.add(new BoolSetting("names", "Full Names", false).description("Off uses short tags (NTH, DIA). On spells the material out.").build());
      this.add(new IntSetting("scale", "Scale", 100, 40, 300, 5).description("Size of the panel, as a percent.").build());
      this.add(new IntSetting("height", "Height Above", 55, 0, 300, 5).description("How far above the head it sits, in hundredths of a block.").build());
      this.add(new IntSetting("bg-alpha", "Background", 64, 0, 255, 8).description("Darkness of the panel backing. 0 removes it.").build());
      this.add(new ColorSetting("c-netherite", "Netherite", -5005861).group("Colors").build());
      this.add(new ColorSetting("c-diamond", "Diamond", -13374465).group("Colors").build());
      this.add(new ColorSetting("c-iron", "Iron", -1644826).group("Colors").build());
      this.add(new ColorSetting("c-gold", "Gold", -11702).group("Colors").build());
      this.add(new ColorSetting("c-chain", "Chainmail", -6643542).group("Colors").build());
      this.add(new ColorSetting("c-leather", "Leather", -5215685).group("Colors").build());
      this.add(new ColorSetting("c-other", "Other", -4800308).group("Colors").build());
      this.add(new ColorSetting("c-empty", "Empty", -11775398).group("Colors").build());
      this.add(new ColorSetting("c-held", "Held Item", -1).group("Colors").build());
      this.add(new ColorSetting("c-name", "Name", -1).group("Colors").build());
      this.add(new ColorSetting("c-stats", "Stats", -4602154).group("Colors").build());
   }

   private static MutableComponent healthLine(Player var0, int var1) {
      int var2 = (int)Math.ceil(var0.getHealth());
      int var3 = (int)Math.ceil(var0.getMaxHealth());
      int var4 = (int)Math.ceil(var0.getAbsorptionAmount());
      String var5 = var4 > 0 ? var2 + "+" + var4 + " ♥" : var2 + "/" + var3 + " ♥";
      int var6 = var2 > var3 * 0.6 ? -11149995 : (var2 > var3 * 0.3 ? -11702 : -43691);
      return Component.literal(var5).withColor(var6);
   }

   private String ping(Player var1) {
      try {
         if (MC.getConnection() != null) {
            PlayerInfo var2 = MC.getConnection().getPlayerInfo(var1.getUUID());
            if (var2 != null) {
               return var2.getLatency() + "ms";
            }
         }
      } catch (Throwable var3) {
      }

      return null;
   }

   @Override
   public void onEnable() {
      installHook();
   }

   private static PlayerArmorEspModule instance() {
      PlayerArmorEspModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("player-esp-plus") instanceof PlayerArmorEspModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static PlayerArmorEspModule.Material materialOf(ItemStack var0) {
      if (var0.isEmpty()) {
         return PlayerArmorEspModule.Material.EMPTY;
      } else {
         String var1 = var0.getItem().toString().toLowerCase(Locale.ROOT);
         if (var1.contains("netherite")) {
            return PlayerArmorEspModule.Material.NETHERITE;
         } else if (var1.contains("diamond")) {
            return PlayerArmorEspModule.Material.DIAMOND;
         } else if (var1.contains("golden") || var1.contains("gold")) {
            return PlayerArmorEspModule.Material.GOLD;
         } else if (var1.contains("chainmail")) {
            return PlayerArmorEspModule.Material.CHAIN;
         } else if (var1.contains("iron")) {
            return PlayerArmorEspModule.Material.IRON;
         } else if (var1.contains("leather")) {
            return PlayerArmorEspModule.Material.LEATHER;
         } else if (var1.contains("turtle")) {
            return PlayerArmorEspModule.Material.TURTLE;
         } else {
            return var1.contains("elytra") ? PlayerArmorEspModule.Material.ELYTRA : PlayerArmorEspModule.Material.OTHER;
         }
      }
   }

   private Component armorLine(Player var1) {
      boolean var2 = this.bool("names");
      boolean var3 = this.bool("empty");
      MutableComponent var4 = Component.empty();
      boolean var5 = false;
      boolean var6 = true;

      for (EquipmentSlot var10 : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         PlayerArmorEspModule.Material var11 = materialOf(var1.getItemBySlot(var10));
         if (var11 != PlayerArmorEspModule.Material.EMPTY || var3) {
            if (var11 != PlayerArmorEspModule.Material.EMPTY) {
               var5 = true;
            }

            if (!var6) {
               var4.append(Component.literal(" ").withColor(-7829368));
            }

            var6 = false;
            int var12 = ModuleRenderUtil.color(this, var11.colorId, -1) & 16777215;
            var4.append(Component.literal(var2 ? var11.full : var11.tag).withColor(var12));
         }
      }

      return !var5 && !var3 ? null : var4;
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)var0 -> {
            try {
               PlayerArmorEspModule var1 = instance();
               if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                  return;
               }

               if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                  return;
               }

               Vec3 var2 = var0.levelState().cameraRenderState.pos;
               double var3 = var1.integer("range");
               double var5 = var3 * var3;
               boolean var7 = var1.bool("self");
               boolean var8 = var1.bool("held");
               float var9 = var1.integer("scale") / 100.0F;
               double var10 = var1.integer("height") / 100.0;
               double var12 = 0.32 * var9;
               int var14 = var1.integer("bg-alpha") << 24;
               int var15 = ModuleRenderUtil.color(var1, "c-held", -1) & 16777215;
               int var16 = ModuleRenderUtil.color(var1, "c-name", -1) & 16777215;
               int var17 = ModuleRenderUtil.color(var1, "c-stats", -4602154) & 16777215;
               PoseStack var18 = var0.poseStack();
               float var30 = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);

               for (Player var20 : MC.level.players()) {
                  if (var20 != null && var20.isAlive() && (var20 != MC.player || var7) && !(var20.distanceToSqr(MC.player) > var5)) {
                     ArrayList var21 = new ArrayList();
                     if (var1.bool("compact")) {
                        // Classic PvP tag: armour row on top, then name / health / ping on one line.
                        Component var40 = var1.armorLine(var20);
                        if (var40 != null) {
                           var21.add(var40);
                        }

                        MutableComponent var41 = Component.empty();
                        boolean var42 = false;
                        if (var1.bool("name")) {
                           var41.append(Component.literal(var20.getGameProfile().name()).withColor(var16));
                           var42 = true;
                        }

                        if (var1.bool("health")) {
                           var41.append(Component.literal(var42 ? " " : "")).append(healthLine(var20, var17));
                           var42 = true;
                        }

                        String var43 = var1.bool("ping") ? var1.ping(var20) : null;
                        if (var43 != null) {
                           var41.append(Component.literal((var42 ? " " : "") + var43).withColor(var17));
                           var42 = true;
                        }

                        if (var1.bool("distance")) {
                           var41.append(Component.literal((var42 ? " " : "") + (int)Math.sqrt(var20.distanceToSqr(MC.player)) + "m").withColor(var17));
                           var42 = true;
                        }

                        if (var42) {
                           var21.add(var41);
                        }

                        ItemStack var44 = var20.getMainHandItem();
                        if (var8 && !var44.isEmpty()) {
                           var21.add(Component.literal(var44.getHoverName().getString()).withColor(var15));
                        }
                     } else {
                        // Stacked layout: one line per stat, armour under them.
                        if (var1.bool("name")) {
                           var21.add(Component.literal(var20.getGameProfile().name()).withColor(var16));
                        }

                        if (var1.bool("health")) {
                           var21.add(healthLine(var20, var17));
                        }

                        if (var1.bool("distance") || var1.bool("ping")) {
                           StringBuilder var22 = new StringBuilder();
                           if (var1.bool("distance")) {
                              var22.append((int)Math.sqrt(var20.distanceToSqr(MC.player))).append('m');
                           }

                           String var23 = var1.bool("ping") ? var1.ping(var20) : null;
                           if (var23 != null) {
                              if (var22.length() > 0) {
                                 var22.append("  ");
                              }

                              var22.append(var23);
                           }

                           if (var22.length() > 0) {
                              var21.add(Component.literal(var22.toString()).withColor(var17));
                           }
                        }

                        Component var28 = var1.armorLine(var20);
                        if (var28 != null) {
                           var21.add(var28);
                        }

                        ItemStack var29 = var20.getMainHandItem();
                        if (var8 && !var29.isEmpty()) {
                           var21.add(Component.literal(var29.getHoverName().getString()).withColor(var15));
                        }
                     }

                     if (!var21.isEmpty()) {
                        // Interpolate like the other ESP renderers so the panel doesn't trail moving players.
                        double var31 = Mth.lerp(var30, var20.xOld, var20.getX());
                        double var33 = Mth.lerp(var30, var20.zOld, var20.getZ());
                        double var24 = Mth.lerp(var30, var20.yOld, var20.getY()) + var20.getBbHeight() + var10 + var12 * (var21.size() - 1);

                        for (int var26 = 0; var26 < var21.size(); var26++) {
                           drawLine(var0, var18, var31, var33, var2, var24 - var12 * var26, var9, var14, (Component)var21.get(var26));
                        }
                     }
                  }
               }
            } catch (Throwable var27) {
            }
         });
      }
   }

   private static void drawLine(LevelRenderContext var0, PoseStack var1, double var2, double var9, Vec3 var3, double var4, float var6, int var7, Component var8) {
      var1.pushPose();
      var1.translate(var2 - var3.x, var4 - var3.y, var9 - var3.z);
      if (var6 != 1.0F) {
         var1.scale(var6, var6, var6);
      }

      var0.submitNodeCollector().submitNameTag(var1, Vec3.ZERO, var7, var8, false, 15728880, var0.levelState().cameraRenderState);
      var1.popPose();
   }

   private static enum Material {
      NETHERITE("NTH", "Netherite", "c-netherite"),
      DIAMOND("DIA", "Diamond", "c-diamond"),
      IRON("IRN", "Iron", "c-iron"),
      GOLD("GLD", "Gold", "c-gold"),
      CHAIN("CHN", "Chain", "c-chain"),
      LEATHER("LTR", "Leather", "c-leather"),
      TURTLE("TRT", "Turtle", "c-other"),
      ELYTRA("ELY", "Elytra", "c-other"),
      OTHER("???", "Other", "c-other"),
      EMPTY("—", "-", "c-empty");

      final String tag;
      final String full;
      final String colorId;

      private Material(String nullxx, String nullxxx, String nullxxxx) {
         this.tag = nullxx;
         this.full = nullxxx;
         this.colorId = nullxxxx;
      }
   }
}
