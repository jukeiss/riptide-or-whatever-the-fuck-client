package riptide.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class RiptideChamsHit {
   private static final long FLASH_MS = 500L;
   private static final int PENDING_TICKS = 6;
   private static final int HURT_TIME = 10;
   private static final Map<Integer, Long> FLASH = new ConcurrentHashMap<>();
   private static final Map<Integer, RiptideChamsHit.Pending> PENDING = new ConcurrentHashMap<>();

   private RiptideChamsHit() {
   }

   public static void onAttack(Entity entity) {
      if (entity instanceof LivingEntity living) {
         PENDING.put(living.getId(), new RiptideChamsHit.Pending(living.hurtTime, 6));
      }
   }

   public static void mark(Entity entity) {
      if (entity != null) {
         FLASH.put(entity.getId(), System.currentTimeMillis());
      }
   }

   public static void tick() {
      if (!PENDING.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.level != null) {
            Iterator<Entry<Integer, RiptideChamsHit.Pending>> it = PENDING.entrySet().iterator();

            while (it.hasNext()) {
               Entry<Integer, RiptideChamsHit.Pending> e = it.next();
               Entity entity = mc.level.getEntity(e.getKey());
               RiptideChamsHit.Pending p = e.getValue();
               boolean landed = entity instanceof LivingEntity living
                  && (living.hurtTime > p.prevHurtTime() || p.prevHurtTime() >= 10 && living.hurtTime >= 10);
               if (landed) {
                  FLASH.put(e.getKey(), System.currentTimeMillis());
                  it.remove();
               } else if (entity != null && p.ticksLeft() > 1) {
                  e.setValue(new RiptideChamsHit.Pending(p.prevHurtTime(), p.ticksLeft() - 1));
               } else {
                  it.remove();
               }
            }
         } else {
            PENDING.clear();
         }
      }
   }

   public static boolean isFlashing(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         Long at = FLASH.get(entity.getId());
         if (at == null) {
            return false;
         } else if (System.currentTimeMillis() - at < 500L) {
            return true;
         } else {
            FLASH.remove(entity.getId());
            return false;
         }
      }
   }

   private record Pending(int prevHurtTime, int ticksLeft) {
   }
}
