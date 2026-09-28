package riptide.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public final class SodiumTerrainPassGuard {
   private static final int TRANSITION_PASS_BUDGET = 600;
   private static final AtomicInteger ARM_GENERATION = new AtomicInteger(1);
   private static int seenArmGeneration;
   private static int remainingTransitionPasses;
   private static volatile boolean xrayActive;
   private static volatile boolean accessUnavailable;
   private static final ClassValue<SodiumTerrainPassGuard.PassAccess> PASS_ACCESS = new ClassValue<SodiumTerrainPassGuard.PassAccess>() {
      protected SodiumTerrainPassGuard.PassAccess computeValue(Class<?> type) {
         return new SodiumTerrainPassGuard.PassAccess(SodiumTerrainPassGuard.findHandle(type, "isTranslucent", 0));
      }
   };
   private static final ClassValue<SodiumTerrainPassGuard.ListsAccess> LISTS_ACCESS = new ClassValue<SodiumTerrainPassGuard.ListsAccess>() {
      protected SodiumTerrainPassGuard.ListsAccess computeValue(Class<?> type) {
         return new SodiumTerrainPassGuard.ListsAccess(SodiumTerrainPassGuard.findHandle(type, "iterator", 1));
      }
   };
   private static final ClassValue<SodiumTerrainPassGuard.RenderListAccess> RENDER_LIST_ACCESS = new ClassValue<SodiumTerrainPassGuard.RenderListAccess>() {
      protected SodiumTerrainPassGuard.RenderListAccess computeValue(Class<?> type) {
         return new SodiumTerrainPassGuard.RenderListAccess(
            SodiumTerrainPassGuard.findHandle(type, "getRegion", 0), SodiumTerrainPassGuard.findHandle(type, "sectionsWithGeometryIterator", 1)
         );
      }
   };
   private static final ClassValue<SodiumTerrainPassGuard.RegionAccess> REGION_ACCESS = new ClassValue<SodiumTerrainPassGuard.RegionAccess>() {
      protected SodiumTerrainPassGuard.RegionAccess computeValue(Class<?> type) {
         return new SodiumTerrainPassGuard.RegionAccess(
            SodiumTerrainPassGuard.findHandle(type, "getStorage", 1),
            SodiumTerrainPassGuard.findHandle(type, "getCachedBatch", 1),
            SodiumTerrainPassGuard.findHandle(type, "getResources", 0)
         );
      }
   };
   private static final ClassValue<SodiumTerrainPassGuard.BatchAccess> BATCH_ACCESS = new ClassValue<SodiumTerrainPassGuard.BatchAccess>() {
      protected SodiumTerrainPassGuard.BatchAccess computeValue(Class<?> type) {
         return new SodiumTerrainPassGuard.BatchAccess(SodiumTerrainPassGuard.findHandle(type, "isEmpty", 0));
      }
   };

   private SodiumTerrainPassGuard() {
   }

   public static void armForTransition() {
      ARM_GENERATION.incrementAndGet();
   }

   public static void armForPositionCorrection() {
      if (!hasInspectionWork()) {
         armForTransition();
      }
   }

   public static void setXrayActive(boolean active) {
      if (xrayActive != active) {
         armForTransition();
      }

      xrayActive = active;
   }

   static boolean hasInspectionWork() {
      return !accessUnavailable && (xrayActive || ARM_GENERATION.get() != seenArmGeneration || remainingTransitionPasses > 0);
   }

   static void resetForTests() {
      xrayActive = false;
      accessUnavailable = false;
      seenArmGeneration = ARM_GENERATION.get();
      remainingTransitionPasses = 0;
   }

   public static boolean shouldSkip(Object lists, Object pass) {
      if (accessUnavailable) {
         return false;
      } else {
         int generation = ARM_GENERATION.get();
         if (generation != seenArmGeneration) {
            seenArmGeneration = generation;
            remainingTransitionPasses = 600;
         } else if (!xrayActive && remainingTransitionPasses <= 0) {
            return false;
         }

         if (!xrayActive) {
            remainingTransitionPasses--;
         }

         if (lists != null && pass != null) {
            try {
               SodiumTerrainPassGuard.PassAccess passAccess = PASS_ACCESS.get(pass.getClass());
               SodiumTerrainPassGuard.ListsAccess listsAccess = LISTS_ACCESS.get(lists.getClass());
               if (passAccess.isTranslucent() == null || listsAccess.iterator() == null) {
                  return disableCompatibilityGuard();
               }

               boolean translucent = (boolean)passAccess.isTranslucent().invoke((Object)pass);
               if (!((Object)listsAccess.iterator().invoke((Object)lists, (boolean)translucent) instanceof Iterator<?> iterator)) {
                  return false;
               }

               while (iterator.hasNext()) {
                  Object renderList = iterator.next();
                  if (renderList != null) {
                     SodiumTerrainPassGuard.RenderListAccess renderAccess = RENDER_LIST_ACCESS.get(renderList.getClass());
                     if (renderAccess.getRegion() == null || renderAccess.sectionsWithGeometryIterator() == null) {
                        return disableCompatibilityGuard();
                     }

                     Object region = (Object)renderAccess.getRegion().invoke((Object)renderList);
                     if (region != null) {
                        SodiumTerrainPassGuard.RegionAccess regionAccess = REGION_ACCESS.get(region.getClass());
                        if (regionAccess.getStorage() == null || regionAccess.getCachedBatch() == null || regionAccess.getResources() == null) {
                           return disableCompatibilityGuard();
                        }

                        if ((Object)regionAccess.getStorage().invoke((Object)region, (Object)pass) != null) {
                           Object batch = (Object)regionAccess.getCachedBatch().invoke((Object)region, (Object)pass);
                           SodiumTerrainPassGuard.BatchAccess batchAccess = batch == null ? null : BATCH_ACCESS.get(batch.getClass());
                           if (batchAccess != null && batchAccess.isEmpty() == null) {
                              return disableCompatibilityGuard();
                           }

                           boolean cachedDraws = batchAccess != null && !(boolean)batchAccess.isEmpty().invoke((Object)batch);
                           boolean listedGeometry = (Object)renderAccess.sectionsWithGeometryIterator().invoke((Object)renderList, (boolean)translucent)
                              != null;
                           if ((cachedDraws || listedGeometry) && (Object)regionAccess.getResources().invoke((Object)region) == null) {
                              remainingTransitionPasses = Math.max(remainingTransitionPasses, 600);
                              return true;
                           }
                        }
                     }
                  }
               }
            } catch (Throwable var16) {
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private static boolean disableCompatibilityGuard() {
      accessUnavailable = true;
      remainingTransitionPasses = 0;
      return false;
   }

   private static MethodHandle findHandle(Class<?> owner, String name, int parameters) {
      for (Method candidate : owner.getMethods()) {
         if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) {
            try {
               return MethodHandles.publicLookup().unreflect(candidate);
            } catch (IllegalAccessException var8) {
               return null;
            }
         }
      }

      return null;
   }

   private record BatchAccess(MethodHandle isEmpty) {
   }

   private record ListsAccess(MethodHandle iterator) {
   }

   private record PassAccess(MethodHandle isTranslucent) {
   }

   private record RegionAccess(MethodHandle getStorage, MethodHandle getCachedBatch, MethodHandle getResources) {
   }

   private record RenderListAccess(MethodHandle getRegion, MethodHandle sectionsWithGeometryIterator) {
   }
}
