package riptide.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class RiptidePlayerProbe {
   private static final int PROBE_MAX_QUERIES = 5000;

   private RiptidePlayerProbe() {
   }

   public static List<String> everyone(Minecraft mc, boolean probeHidden) {
      return everyone(mc, probeHidden, () -> false);
   }

   public static List<String> everyone(Minecraft mc, boolean probeHidden, final BooleanSupplier cancelled) {
      String self = selfName(mc);
      LinkedHashMap<String, String> names = RiptideNameHarvest.instantNames(mc, self);
      final ClientPacketListener connection = mc == null ? null : mc.getConnection();
      if (probeHidden && connection != null) {
         List<RiptideNameHarvest.Vector> vectors = RiptideNameHarvest.discoverVectors(connection);
         RiptideNameHarvest.sweep(connection, names, self, vectors, new RiptideNameHarvest.Control() {
            @Override
            public boolean cancelled() {
               Minecraft m = Minecraft.getInstance();
               return cancelled != null && cancelled.getAsBoolean() || m == null || m.getConnection() != connection;
            }

            @Override
            public boolean paused() {
               return false;
            }

            @Override
            public int limit() {
               return Integer.MAX_VALUE;
            }

            @Override
            public int maxQueries() {
               return 5000;
            }
         });
      }

      return new ArrayList<>(names.values());
   }

   private static String selfName(Minecraft mc) {
      return mc != null && mc.player != null ? mc.player.getName().getString() : "";
   }
}
