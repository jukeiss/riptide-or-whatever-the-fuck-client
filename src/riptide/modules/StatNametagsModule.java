package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptidePlayerStats;

public final class StatNametagsModule extends Module {
   private static final int ROWS = 5;
   private static volatile boolean hook;
   private static StatNametagsModule cached;

   public StatNametagsModule() {
      super("stat-nametags", "Stat Nametags", ModuleCategory.RENDER, "Shows a configurable stat panel above players.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("range", "Range", 64, 8, 256, 4).description("How far away a player still gets a tag, in blocks.").build());
      this.add(new BoolSetting("self", "Show Self", true).description("Also draw the tag above your own head.").build());
      this.add(
         new ChoiceSetting("self-view", "Show Self In", "Third Person", "Third Person", "Always")
            .description("Third Person only draws your own tag in F5, where you can actually see it. Always draws it in first person too.")
            .build()
      );
      this.add(new IntSetting("scale", "Scale", 100, 40, 300, 5).description("Size of the whole panel, as a percent.").build());
      this.add(new IntSetting("spacing", "Line Spacing", 32, 10, 100, 2).description("Gap between rows, in hundredths of a block.").build());
      this.add(new IntSetting("bg-alpha", "Background", 64, 0, 255, 8).description("How dark the panel backing is. 0 removes it.").build());
      this.add(
         new IntSetting("height", "Height Above", 60, 0, 300, 5)
            .description("How far above the player's head the panel sits, in hundredths of a block.")
            .build()
      );
      this.add(
         new BoolSetting("hide-unknown", "Hide Empty Rows", true)
            .description("Skip rows whose stat hasn't been looked up yet, instead of showing the fallback.")
            .build()
      );
      this.add(new StringSetting("unknown", "Unknown Text", "?").description("Shown for a stat that hasn't been looked up.").build());
      this.add(
         new BoolSetting("fake", "Use Fake Values", true)
            .group("Fake Values")
            .description("Fill the stat placeholders with the values below instead of looked-up ones.")
            .build()
      );
      this.add(
         new BoolSetting("fake-self-only", "Only On Me", true)
            .group("Fake Values")
            .description("Apply the fake values to your own tag only. Off means every player shows them.")
            .build()
      );
      this.add(new StringSetting("fake-balance", "Balance", "926K").group("Fake Values").build());
      this.add(new StringSetting("fake-shards", "Shards", "117").group("Fake Values").build());
      this.add(new StringSetting("fake-kills", "Kills", "50").group("Fake Values").build());
      this.add(new StringSetting("fake-deaths", "Deaths", "69").group("Fake Values").build());
      this.add(new StringSetting("fake-playtime", "Playtime", "1d 32m").group("Fake Values").build());
      this.add(new BoolSetting("name-line", "Show Name", true).group("Name").description("Draw the player's name as the header.").build());
      this.add(
         new StringSetting("name-text", "Name Text", "{name}")
            .group("Name")
            .description("Header template. Placeholders: {name} {rank} {distance} {health} {armor} {ping}")
            .build()
      );
      this.add(new ColorSetting("name-color", "Name Color", -1).group("Name").build());
      this.row(1, "$", "{balance}", -11141291);
      this.row(2, "✦", "{shards}", -5617921);
      this.row(3, "⚔", "{kills}", -43691);
      this.row(4, "☠", "{deaths}", -24528);
      this.row(5, "⏱", "{playtime}", -8118);
   }

   private void row(int var1, String var2, String var3, int var4) {
      String var5 = "Row " + var1;
      this.add(new BoolSetting("row" + var1, "Enabled", true).group(var5).description("Draw this row.").build());
      this.add(new StringSetting("row" + var1 + "-icon", "Icon", var2).group(var5).description("Symbol shown before the value. Any text works.").build());
      this.add(
         new StringSetting("row" + var1 + "-text", "Text", var3)
            .group(var5)
            .description("Placeholders: {balance} {shards} {kills} {deaths} {playtime} {name} {rank} {distance} {health} {armor} {ping}")
            .build()
      );
      this.add(new ColorSetting("row" + var1 + "-color", "Color", var4).group(var5).build());
   }

   @Override
   public String info() {
      return Integer.toString(RiptidePlayerStats.size());
   }

   @Override
   public void onEnable() {
      installHook();
   }

   private static StatNametagsModule instance() {
      StatNametagsModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("stat-nametags") instanceof StatNametagsModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private String expand(String var1, Player var2, String var3) {
      if (var1.indexOf(123) < 0) {
         return var1;
      } else {
         String var4 = this.text("unknown");
         String var5 = var1;
         if (var1.contains("{name}")) {
            var5 = var1.replace("{name}", var3);
         }

         if (var5.contains("{rank}")) {
            var5 = var5.replace("{rank}", rank(var2, var3));
         }

         if (var5.contains("{distance}")) {
            double var6 = MC.player == null ? 0.0 : Math.sqrt(var2.distanceToSqr(MC.player));
            var5 = var5.replace("{distance}", (int)var6 + "m");
         }

         if (var5.contains("{health}")) {
            var5 = var5.replace("{health}", Integer.toString((int)Math.ceil(var2.getHealth())));
         }

         if (var5.contains("{armor}")) {
            var5 = var5.replace("{armor}", armorSet(var2));
         }

         if (var5.contains("{ping}")) {
            var5 = var5.replace("{ping}", ping(var3));
         }

         boolean var13 = this.fakeAppliesTo(var2);

         for (String var10 : new String[]{"balance", "shards", "kills", "deaths", "playtime"}) {
            String var11 = "{" + var10 + "}";
            if (var5.contains(var11)) {
               String var12 = var13 ? this.text("fake-" + var10) : RiptidePlayerStats.get(var3, var10);
               var5 = var5.replace(var11, var12 != null && !var12.isBlank() ? var12 : var4);
            }
         }

         return var5;
      }
   }

   private boolean fakeAppliesTo(Player var1) {
      return !this.bool("fake") ? false : !this.bool("fake-self-only") || var1 == MC.player;
   }

   private boolean isUnknown(String var1, Player var2, String var3) {
      if (this.fakeAppliesTo(var2)) {
         return false;
      } else {
         for (String var7 : new String[]{"balance", "shards", "kills", "deaths", "playtime"}) {
            if (var1.contains("{" + var7 + "}") && !RiptidePlayerStats.has(var3, var7)) {
               return true;
            }
         }

         return false;
      }
   }

   private static String rank(Player var0, String var1) {
      try {
         Component var2 = var0.getDisplayName();
         if (var2 != null) {
            String var3 = var2.getString().replaceAll("§.", "").trim();
            int var4 = var3.toLowerCase(Locale.ROOT).indexOf(var1.toLowerCase(Locale.ROOT));
            if (var4 > 0) {
               return var3.substring(0, var4).trim();
            }
         }
      } catch (Throwable var5) {
      }

      return "";
   }

   private static String ping(String var0) {
      try {
         if (MC.getConnection() != null) {
            PlayerInfo var1 = MC.getConnection().getPlayerInfoIgnoreCase(var0);
            if (var1 != null) {
               return var1.getLatency() + "ms";
            }
         }
      } catch (Throwable var2) {
      }

      return "";
   }

   private static String armorSet(Player var0) {
      int var1 = 0;
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;

      for (EquipmentSlot var10 : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack var11 = var0.getItemBySlot(var10);
         if (!var11.isEmpty()) {
            var6++;
            String var12 = var11.getItem().toString().toLowerCase(Locale.ROOT);
            if (var12.contains("netherite")) {
               var1++;
            } else if (var12.contains("diamond")) {
               var2++;
            } else if (var12.contains("iron")) {
               var3++;
            } else if (var12.contains("gold")) {
               var4++;
            } else {
               var5++;
            }
         }
      }

      if (var6 == 0) {
         return "none";
      } else {
         int var13 = Math.max(Math.max(var1, var2), Math.max(Math.max(var3, var4), var5));
         if (var13 == var1) {
            return "neth";
         } else if (var13 == var2) {
            return "dia";
         } else if (var13 == var3) {
            return "iron";
         } else {
            return var13 == var4 ? "gold" : "other";
         }
      }
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     StatNametagsModule var1 = instance();
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
                     boolean var8 = var1.bool("hide-unknown");
                     float var9 = var1.integer("scale") / 100.0F;
                     double var10 = var1.integer("spacing") / 100.0;
                     double var12 = var1.integer("height") / 100.0;
                     int var14 = var1.integer("bg-alpha") << 24;
                     PoseStack var15 = var0.poseStack();

                     for (Player var17 : MC.level.players()) {
                        if (var17 != null
                           && var17.isAlive()
                           && (
                              var17 != MC.player
                                 || var7
                                    && (!"Third Person".equals(var1.choice("self-view")) || MC.options != null && !MC.options.getCameraType().isFirstPerson())
                           )
                           && !(var17.distanceToSqr(MC.player) > var5)) {
                           String var18 = var17.getGameProfile().name();
                           if (var18 != null && !var18.isEmpty()) {
                              ArrayList<Component> var19 = new ArrayList<>();
                              ArrayList<Integer> var20 = new ArrayList<>();
                              if (var1.bool("name-line")) {
                                 String var21 = var1.expand(var1.text("name-text"), var17, var18);
                                 if (!var21.isBlank()) {
                                    var19.add(Component.literal(var21));
                                    var20.add(ModuleRenderUtil.color(var1, "name-color", -1));
                                 }
                              }

                              for (int var27 = 1; var27 <= 5; var27++) {
                                 if (var1.bool("row" + var27)) {
                                    String var22 = var1.text("row" + var27 + "-text");
                                    if (!var8 || !var1.isUnknown(var22, var17, var18)) {
                                       String var23 = var1.expand(var22, var17, var18);
                                       if (!var23.isBlank()) {
                                          String var24 = var1.text("row" + var27 + "-icon");
                                          String var25 = var24.isBlank() ? var23 : var24 + " " + var23;
                                          var19.add(Component.literal(var25));
                                          var20.add(ModuleRenderUtil.color(var1, "row" + var27 + "-color", -1));
                                       }
                                    }
                                 }
                              }

                              if (!var19.isEmpty()) {
                                 double var28 = var17.getBoundingBox().maxY + var12 + var10 * (var19.size() - 1);

                                 for (int var29 = 0; var29 < var19.size(); var29++) {
                                    var15.pushPose();
                                    var15.translate(var17.getX() - var2.x, var28 - var10 * var29 - var2.y, var17.getZ() - var2.z);
                                    if (var9 != 1.0F) {
                                       var15.scale(var9, var9, var9);
                                    }

                                    var0.submitNodeCollector()
                                       .submitNameTag(
                                          var15,
                                          Vec3.ZERO,
                                          var14,
                                          colored((Component)var19.get(var29), (Integer)var20.get(var29)),
                                          false,
                                          15728880,
                                          var0.levelState().cameraRenderState
                                       );
                                    var15.popPose();
                                 }
                              }
                           }
                        }
                     }
                  } catch (Throwable var26) {
                  }
               }
            );
      }
   }

   private static Component colored(Component var0, int var1) {
      return Component.literal(var0.getString()).withStyle(var1x -> var1x.withColor(var1 & 16777215));
   }
}
