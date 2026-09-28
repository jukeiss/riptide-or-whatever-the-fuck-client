package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.modules.Module;
import riptide.modules.ModuleOreSim;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWaypoints;
import riptide.util.oresim.RiptideOreSimEngine;
import riptide.util.oresim.RiptideOreSimSeedInput;

public class OreSimCommand extends Command {
   private static final String G = "§7";
   private static final String W = "§f";
   private static final String R = "§c";
   private static final String Y = "§e";
   private static final String A = "§a";

   public OreSimCommand() {
      super("oresim", "Report why OreSim is or is not predicting.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         report();
         return 1;
      });
   }

   private static void report() {
      Module xray = ModuleRegistry.get("xray");
      if (xray == null) {
         RiptideClientMessaging.sendPrefixed("§cXray module not registered.");
      } else {
         RiptideClientMessaging.sendPrefixed("§e--- OreSim ---");
         RiptideClientMessaging.sendPrefixed(
            "§7enabled: §f" + xray.isEnabled() + "§7  mode: §f" + xray.value("mode") + "§7  style: §f" + xray.value("render-style")
         );
         Long seed = ModuleOreSim.debugSeed(xray);
         RiptideOreSimSeedInput.Status seedStatus = ModuleOreSim.seedInputStatus(xray);
         RiptideClientMessaging.sendPrefixed(
            "§7seed: "
               + (seedStatus == RiptideOreSimSeedInput.Status.INVALID ? "§cINVALID" : (seed == null ? "§cNONE - enter a signed 64-bit value" : "§f" + seed))
               + "§7  from: §fthe World Seed text field"
         );
         RiptideClientMessaging.sendPrefixed(
            "§7saved scope: §f"
               + RiptideWaypoints.scopeKey(Minecraft.getInstance())
               + "§7; the same world/server scoping as waypoints. No world or server seed is read."
         );
         RiptideClientMessaging.sendPrefixed(
            "§7ore list entries: §f" + ModuleOreSim.debugSelectionSize(xray) + "§7  families: §f" + Integer.bitCount(ModuleOreSim.debugEnabledMask(xray))
         );
         String worldgen = RiptideOreSimEngine.failed()
            ? "§cFAILED"
            : (
               RiptideOreSimEngine.loading()
                  ? "§e" + RiptideOreSimEngine.status().name().toLowerCase(Locale.ROOT)
                  : (RiptideOreSimEngine.ready() ? "§aready" : "§c" + RiptideOreSimEngine.status().name().toLowerCase(Locale.ROOT))
            );
         RiptideClientMessaging.sendPrefixed("§7local Minecraft 26.2 worldgen: " + worldgen);
         if (RiptideOreSimEngine.failed() || RiptideOreSimEngine.status() == RiptideOreSimEngine.Status.UNVERIFIED_WORLDGEN) {
            RiptideClientMessaging.sendPrefixed("§c" + RiptideOreSimEngine.failureMessage());
         }

         RiptideClientMessaging.sendPrefixed(
            "§7chunks simulated: §f" + RiptideOreSimEngine.chunkCount() + "§7  positions: §f" + RiptideOreSimEngine.storedPositions()
         );
         RiptideClientMessaging.sendPrefixed(
            "§7generation source: §flocal vanilla registries + terrain + carvers + biome decoration§7; received server chunks are not inputs."
         );
         RiptideClientMessaging.sendPrefixed("§7nearby real ore: §f" + ModuleOreSim.nearDiagnostics());
         RiptideClientMessaging.sendPrefixed("§7matched = real ore within 5 blocks; touchingAir = of those, exposed; drawn = also in line of sight.");
      }
   }
}
