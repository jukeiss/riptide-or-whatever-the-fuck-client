package riptide.util.macro;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.macro.AddonCondition;
import riptide.util.RiptideFakeGamemode;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideLANSync;

public class MacroConditionRegistry {
   private static final List<MacroConditionRegistry.PendingCondition> pendingConditions = Collections.synchronizedList(new ArrayList<>());
   private static final AtomicLong inventorySyncRevision = new AtomicLong();
   private static volatile long lastRespawnNanos = 0L;
   private static volatile long lastTeleportNanos = 0L;

   public static long inventorySyncRevision() {
      return inventorySyncRevision.get();
   }

   public static int pendingConditionCount() {
      return pendingConditions.size();
   }

   public static void recordInventorySync() {
      inventorySyncRevision.incrementAndGet();
   }

   public static CompletableFuture<Void> waitForGui(String guiTitle) {
      Minecraft mc = Minecraft.getInstance();
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.GuiCondition condition = new MacroConditionRegistry.GuiCondition(guiTitle, mc.gui.screen(), future);
      pendingConditions.add(condition);
      return future;
   }

   public static CompletableFuture<Void> waitForGuiClose(String guiTitle) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      pendingConditions.add(new MacroConditionRegistry.GuiCloseCondition(guiTitle, future));
      return future;
   }

   public static CompletableFuture<Void> waitForGuiChange(Screen prevScreen) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      pendingConditions.add(new MacroConditionRegistry.GuiChangeCondition(prevScreen, future));
      return future;
   }

   public static CompletableFuture<Void> waitForItem(String itemName) {
      return waitForItem(ItemTarget.fromLegacyEntry(itemName));
   }

   public static CompletableFuture<Void> waitForItem(ItemTarget target) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.ItemCondition condition = new MacroConditionRegistry.ItemCondition(target, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForHealth(float threshold, boolean below) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.HealthCondition condition = new MacroConditionRegistry.HealthCondition(threshold, below, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForBlock(WaitForBlockAction action) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.BlockCondition condition = new MacroConditionRegistry.BlockCondition(action, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForCooldown(String itemName, boolean checkMainHand) {
      return waitForCooldown(ItemTarget.fromLegacyEntry(itemName), checkMainHand);
   }

   public static CompletableFuture<Void> waitForCooldown(ItemTarget target, boolean checkMainHand) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.CooldownCondition condition = new MacroConditionRegistry.CooldownCondition(target, checkMainHand, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForPos(double x, double y, double z, double leeway, boolean checkRotation, float yaw, float pitch, float rotLeeway) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.WaitPosCondition condition = new MacroConditionRegistry.WaitPosCondition(
         x, y, z, leeway, checkRotation, yaw, pitch, rotLeeway, future
      );
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForWorldChange() {
      return waitForWorldChange(new WaitForWorldChangeAction());
   }

   public static CompletableFuture<Void> waitForWorldChange(WaitForWorldChangeAction action) {
      Minecraft mc = Minecraft.getInstance();
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.WorldChangeCondition condition = new MacroConditionRegistry.WorldChangeCondition(
         currentDimensionId(mc), action == null ? "" : action.targetDimension, future
      );
      pendingConditions.add(condition);
      return future;
   }

   public static CompletableFuture<Void> waitForGamemodeChange(WaitGamemodeChangeAction action) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.GamemodeChangeCondition condition = new MacroConditionRegistry.GamemodeChangeCondition(
         action, RiptideFakeGamemode.snapshot(), future
      );
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForPositionDelta(double distance, boolean horizontalOnly) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 origin = mc.player == null ? Vec3.ZERO : mc.player.position();
      return waitForPositionDeltaFrom(origin, distance, horizontalOnly);
   }

   public static CompletableFuture<Void> waitForPositionDeltaFrom(Vec3 origin, double distance, boolean horizontalOnly) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.PositionDeltaCondition condition = new MacroConditionRegistry.PositionDeltaCondition(origin, distance, horizontalOnly, future);
      pendingConditions.add(condition);
      return future;
   }

   public static CompletableFuture<Void> waitForNextTick() {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.TickSyncCondition condition = new MacroConditionRegistry.TickSyncCondition(future);
      pendingConditions.add(condition);
      return future;
   }

   public static void onInventorySync(Minecraft mc) {
      if (!pendingConditions.isEmpty()) {
         Screen current = mc.gui.screen();
         List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond.isCancelled()) {
                  toRemove.add(cond);
               } else if (cond instanceof MacroConditionRegistry.GuiCondition gc) {
                  if (current != null && gc.checkScreen(current)) {
                     gc.complete();
                     toRemove.add(gc);
                  }
               } else if (cond instanceof MacroConditionRegistry.HandlerItemCondition hic) {
                  if (hic.check(mc)) {
                     hic.complete();
                     toRemove.add(hic);
                  }
               } else if (cond instanceof MacroConditionRegistry.ItemCondition ic) {
                  if (ic.check(mc)) {
                     ic.complete();
                     toRemove.add(ic);
                  }
               } else if (cond instanceof MacroConditionRegistry.SlotChangeCondition scc && scc.check(mc)) {
                  scc.complete();
                  toRemove.add(scc);
               }
            }

            pendingConditions.removeAll(toRemove);
         }
      }
   }

   public static void onSlotUpdate(int slot) {
      if (!pendingConditions.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
            synchronized (pendingConditions) {
               for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
                  if (cond.isCancelled()) {
                     toRemove.add(cond);
                  } else if (cond instanceof MacroConditionRegistry.SlotChangeCondition scc) {
                     if (scc.check(mc)) {
                        scc.complete();
                        toRemove.add(scc);
                     }
                  } else if (cond instanceof MacroConditionRegistry.HandlerItemCondition hic) {
                     if (hic.check(mc)) {
                        hic.complete();
                        toRemove.add(hic);
                     }
                  } else if (cond instanceof MacroConditionRegistry.ItemCondition ic && ic.check(mc)) {
                     ic.complete();
                     toRemove.add(ic);
                  }
               }

               pendingConditions.removeAll(toRemove);
            }
         }
      }
   }

   public static void onTick(Minecraft mc) {
      if (!pendingConditions.isEmpty()) {
         List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond.isCancelled()) {
                  toRemove.add(cond);
               } else {
                  try {
                     if (cond.check(mc)) {
                        cond.complete();
                        toRemove.add(cond);
                     }
                  } catch (Throwable var7) {
                     cond.cancel();
                     toRemove.add(cond);
                  }
               }
            }

            pendingConditions.removeAll(toRemove);
         }
      }
   }

   public static void onScreenChange(Screen screen) {
      if (!pendingConditions.isEmpty()) {
         List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond instanceof MacroConditionRegistry.GuiCondition gc) {
                  if (gc.checkScreen(screen)) {
                     gc.complete();
                     toRemove.add(gc);
                  }
               } else if (cond instanceof MacroConditionRegistry.HandlerItemCondition hic) {
                  Minecraft mc = Minecraft.getInstance();
                  if (hic.check(mc)) {
                     hic.complete();
                     toRemove.add(hic);
                  }
               } else if (cond instanceof MacroConditionRegistry.SlotChangeCondition scc) {
                  Minecraft mc = Minecraft.getInstance();
                  if (scc.check(mc)) {
                     scc.complete();
                     toRemove.add(scc);
                  }
               }
            }

            pendingConditions.removeAll(toRemove);
         }
      }
   }

   private static boolean checkOnClientThread(Minecraft mc, MacroConditionRegistry.PendingCondition condition) {
      if (mc != null && condition != null) {
         try {
            if (mc.isSameThread()) {
               return condition.check(mc);
            } else {
               CompletableFuture<Boolean> result = new CompletableFuture<>();
               mc.execute(() -> {
                  try {
                     result.complete(condition.check(mc));
                  } catch (Throwable var4) {
                     result.complete(false);
                  }
               });
               return Boolean.TRUE.equals(result.get(250L, TimeUnit.MILLISECONDS));
            }
         } catch (Exception var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void runOnClientThread(Minecraft mc, Runnable task) {
      if (mc != null && task != null) {
         try {
            if (mc.isSameThread()) {
               task.run();
               return;
            }

            CompletableFuture<Void> result = new CompletableFuture<>();
            mc.execute(() -> {
               try {
                  task.run();
               } finally {
                  result.complete(null);
               }
            });
            result.get(250L, TimeUnit.MILLISECONDS);
         } catch (Exception var3) {
         }
      }
   }

   public static void cancelAll() {
      synchronized (pendingConditions) {
         for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
            cond.cancel();
         }

         pendingConditions.clear();
      }
   }

   public static boolean hasPendingConditions() {
      return !pendingConditions.isEmpty();
   }

   public static boolean hasPendingInventoryConditions() {
      if (pendingConditions.isEmpty()) {
         return false;
      } else {
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond instanceof MacroConditionRegistry.GuiCondition
                  || cond instanceof MacroConditionRegistry.HandlerItemCondition
                  || cond instanceof MacroConditionRegistry.ItemCondition
                  || cond instanceof MacroConditionRegistry.SlotChangeCondition) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public static boolean hasPendingSoundConditions() {
      if (pendingConditions.isEmpty()) {
         return false;
      } else {
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond instanceof MacroConditionRegistry.SoundCondition) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public static CompletableFuture<Void> await(AddonCondition condition) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      if (condition == null) {
         future.complete(null);
         return future;
      } else {
         MacroConditionRegistry.AddonConditionAdapter adapter = new MacroConditionRegistry.AddonConditionAdapter(condition, future);
         pendingConditions.add(adapter);
         Minecraft mc = Minecraft.getInstance();
         if (checkOnClientThread(mc, adapter)) {
            adapter.complete();
            pendingConditions.remove(adapter);
         }

         return future;
      }
   }

   public static void onRespawnPacket() {
      lastRespawnNanos = System.nanoTime();
   }

   public static void onTeleportPacket() {
      lastTeleportNanos = System.nanoTime();
   }

   private static String currentDimensionId(Minecraft mc) {
      try {
         return mc != null && mc.level != null && mc.level.dimension() != null ? mc.level.dimension().identifier().toString() : "";
      } catch (Throwable var2) {
         return "";
      }
   }

   private static boolean dimensionMatches(String current, String target) {
      return target != null && !target.isBlank() ? normalizeDimensionId(current).equals(normalizeDimensionId(target)) : true;
   }

   private static String normalizeDimensionId(String raw) {
      if (raw == null) {
         return "";
      } else {
         String value = raw.trim().toLowerCase(Locale.ROOT);
         if (value.isEmpty()) {
            return "";
         } else if (value.equals("nether") || value.equals("the_nether")) {
            return "minecraft:the_nether";
         } else if (value.equals("overworld") || value.equals("world")) {
            return "minecraft:overworld";
         } else if (value.equals("end") || value.equals("the_end")) {
            return "minecraft:the_end";
         } else {
            return !value.contains(":") ? "minecraft:" + value : value;
         }
      }
   }

   public static CompletableFuture<Void> waitForServerTick(int bufferMs, int maxWaitMs, boolean ignorePing) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.ServerTickSyncCondition condition = new MacroConditionRegistry.ServerTickSyncCondition(future, bufferMs, maxWaitMs, ignorePing);
      pendingConditions.add(condition);
      return future;
   }

   public static CompletableFuture<Void> waitForEntity(WaitForEntityAction action) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.EntityCondition condition = new MacroConditionRegistry.EntityCondition(action, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (condition.check(mc)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForItemInHandler(String itemName) {
      return waitForItemInHandler(ItemTarget.fromLegacyEntry(itemName));
   }

   public static CompletableFuture<Void> waitForItemInHandler(ItemTarget target) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.HandlerItemCondition condition = new MacroConditionRegistry.HandlerItemCondition(target, future);
      pendingConditions.add(condition);
      Minecraft mc = Minecraft.getInstance();
      if (checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   public static CompletableFuture<Void> waitForSlotChange(int slotNumber) {
      return waitForSlotChange(new WaitForSlotChangeAction(slotNumber));
   }

   public static CompletableFuture<Void> waitForSound(WaitForSoundAction action) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      pendingConditions.add(new MacroConditionRegistry.SoundCondition(action, future));
      return future;
   }

   public static void onSoundPacket(String soundId, double x, double y, double z) {
      if (!pendingConditions.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond.isCancelled()) {
                  toRemove.add(cond);
               } else if (cond instanceof MacroConditionRegistry.SoundCondition sc && sc.matches(mc, soundId, x, y, z)) {
                  sc.action.recordMatch(soundId, x, y, z);
                  sc.complete();
                  toRemove.add(sc);
               }
            }

            pendingConditions.removeAll(toRemove);
         }
      }
   }

   public static CompletableFuture<Void> waitForSlotChange(WaitForSlotChangeAction action) {
      Minecraft mc = Minecraft.getInstance();
      Map<Integer, String> initialNames = new HashMap<>();
      Map<Integer, Integer> initialCounts = new HashMap<>();
      runOnClientThread(mc, () -> {
         if (mc.player != null) {
            AbstractContainerMenu handler = mc.player.containerMenu;

            for (int i = 0; i < action.entries.size(); i++) {
               WaitForSlotChangeAction.WaitEntry e = action.entries.get(i);
               ItemTarget entryTarget = e.resolvedTarget();
               if (e.waitMode == WaitForSlotChangeAction.WaitMode.ANY_CHANGE && entryTarget != null && entryTarget.hasSlot()) {
                  int slotNum = entryTarget.slot;
                  int handlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, slotNum);
                  if (handler != null && handlerSlot >= 0 && handlerSlot < handler.slots.size()) {
                     ItemStack s = ((Slot)handler.slots.get(handlerSlot)).getItem();
                     initialNames.put(i, snapshotStackIdentity(s));
                     initialCounts.put(i, s.isEmpty() ? 0 : s.getCount());
                  }
               }
            }
         }
      });
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.SlotChangeCondition condition = new MacroConditionRegistry.SlotChangeCondition(action, initialNames, initialCounts, future);
      pendingConditions.add(condition);
      boolean hasAnyChange = action.entries.stream().anyMatch(e -> e.waitMode == WaitForSlotChangeAction.WaitMode.ANY_CHANGE);
      if (!hasAnyChange && checkOnClientThread(mc, condition)) {
         condition.complete();
         pendingConditions.remove(condition);
      }

      return future;
   }

   private static String snapshotStackIdentity(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
         String rich = MacroExecutor.serializeTextComponent(stack.getHoverName());
         String components = String.valueOf(stack.getComponents());
         return id + "|" + stack.getCount() + "|" + (rich == null ? "" : rich) + "|" + components.hashCode();
      } else {
         return "";
      }
   }

   public static CompletableFuture<Void> waitForLanStep(WaitForLanStepAction action) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroConditionRegistry.LanStepCondition cond = new MacroConditionRegistry.LanStepCondition(action, future);
      if (cond.checkNow()) {
         future.complete(null);
      } else {
         pendingConditions.add(cond);
      }

      return future;
   }

   public static void onLanStepProgress() {
      if (!pendingConditions.isEmpty()) {
         List<MacroConditionRegistry.PendingCondition> toRemove = new ArrayList<>();
         synchronized (pendingConditions) {
            for (MacroConditionRegistry.PendingCondition cond : pendingConditions) {
               if (cond.isCancelled()) {
                  toRemove.add(cond);
               } else if (cond instanceof MacroConditionRegistry.LanStepCondition lsc && lsc.checkNow()) {
                  lsc.complete();
                  toRemove.add(lsc);
               }
            }

            pendingConditions.removeAll(toRemove);
         }
      }
   }

   private static boolean matchesLower(String searchLower, String[] searchWords, String target) {
      String normalizedTarget = MacroExecutor.normalizeManualText(target);
      if (!searchLower.isEmpty() && !normalizedTarget.isEmpty()) {
         String tl = normalizedTarget.toLowerCase();
         if (searchLower.equals(tl)) {
            return true;
         } else if (tl.contains(searchLower)) {
            return true;
         } else if (searchWords.length == 0) {
            return false;
         } else {
            String[] targetWords = tl.split("\\s+");

            for (String word : searchWords) {
               boolean found = false;

               for (String tWord : targetWords) {
                  if (tWord.contains(word) || word.contains(tWord)) {
                     found = true;
                     break;
                  }
               }

               if (!found) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean matchesText(String search, String target) {
      if (!search.isEmpty() && !target.isEmpty()) {
         if (search.equals(target)) {
            return true;
         } else if (target.toLowerCase().contains(search.toLowerCase())) {
            return true;
         } else {
            String[] searchWords = search.toLowerCase().split("\\s+");
            String[] targetWords = target.toLowerCase().split("\\s+");

            for (String word : searchWords) {
               boolean found = false;

               for (String tWord : targetWords) {
                  if (tWord.contains(word) || word.contains(tWord)) {
                     found = true;
                     break;
                  }
               }

               if (!found) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   static final class AddonConditionAdapter implements MacroConditionRegistry.PendingCondition {
      private final AddonCondition condition;
      private final CompletableFuture<Void> future;

      AddonConditionAdapter(AddonCondition condition, CompletableFuture<Void> future) {
         this.condition = condition;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         return this.condition.check(mc);
      }

      @Override
      public void complete() {
         try {
            this.condition.onComplete();
         } catch (Throwable var2) {
         }

         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class BlockCondition implements MacroConditionRegistry.PendingCondition {
      final WaitForBlockAction action;
      final Set<String> blockIdSet;
      final CompletableFuture<Void> future;

      BlockCondition(WaitForBlockAction action, CompletableFuture<Void> future) {
         this.action = action;
         this.blockIdSet = new HashSet<>(action.blockIds);
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.level != null && mc.player != null) {
            boolean destroyed = this.action.waitBehavior == WaitForBlockAction.WaitBehavior.DESTROYED;
            boolean anyBlock = this.action.anyBlock;
            switch (this.action.checkMode) {
               case AT_POSITION:
                  Block b = mc.level.getBlockState(this.action.blockPos).getBlock();
                  boolean isAir = b == Blocks.AIR;
                  if (destroyed) {
                     if (anyBlock) {
                        return isAir;
                     } else {
                        if (isAir) {
                           return true;
                        }

                        return !this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                     }
                  } else if (isAir) {
                     return false;
                  } else if (!anyBlock && !this.blockIdSet.isEmpty() && !this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString())) {
                     return false;
                  } else {
                     if (this.action.mustBeInReach) {
                        return this.isAtPosReachable(mc);
                     }

                     return true;
                  }
               case IN_REACH:
                  int r = (int)Math.ceil(this.action.searchRadius);
                  int cx = (int)mc.player.getX();
                  int cy = (int)mc.player.getY();
                  int cz = (int)mc.player.getZ();
                  MutableBlockPos mut = new MutableBlockPos();
                  if (destroyed) {
                     if (anyBlock) {
                        return false;
                     } else {
                        for (int dx = -r; dx <= r; dx++) {
                           for (int dy = -r; dy <= r; dy++) {
                              for (int dz = -r; dz <= r; dz++) {
                                 mut.set(cx + dx, cy + dy, cz + dz);
                                 Block bx = mc.level.getBlockState(mut).getBlock();
                                 if (bx != Blocks.AIR && this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(bx).toString())) {
                                    return false;
                                 }
                              }
                           }
                        }

                        return true;
                     }
                  } else {
                     for (int dx = -r; dx <= r; dx++) {
                        for (int dy = -r; dy <= r; dy++) {
                           for (int dzx = -r; dzx <= r; dzx++) {
                              mut.set(cx + dx, cy + dy, cz + dzx);
                              Block bx = mc.level.getBlockState(mut).getBlock();
                              if (bx != Blocks.AIR) {
                                 if (anyBlock || this.blockIdSet.isEmpty()) {
                                    return true;
                                 }

                                 if (this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(bx).toString())) {
                                    return true;
                                 }
                              }
                           }
                        }
                     }

                     return false;
                  }
               case LOOKING_AT:
                  if (destroyed) {
                     if (mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK) {
                        if (!anyBlock && !this.blockIdSet.isEmpty()) {
                           BlockHitResult bhr = (BlockHitResult)mc.hitResult;
                           Block b = mc.level.getBlockState(bhr.getBlockPos()).getBlock();
                           return !this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                        }

                        return false;
                     }

                     return anyBlock;
                  } else {
                     if (mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK) {
                        BlockHitResult bhr = (BlockHitResult)mc.hitResult;
                        if (!anyBlock && !this.blockIdSet.isEmpty()) {
                           Block b = mc.level.getBlockState(bhr.getBlockPos()).getBlock();
                           return this.blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                        }

                        return true;
                     }

                     return false;
                  }
               default:
                  return false;
            }
         } else {
            return false;
         }
      }

      private boolean isAtPosReachable(Minecraft mc) {
         Vec3 center = Vec3.atCenterOf(this.action.blockPos);
         if (mc.player.distanceToSqr(center) > 36.0) {
            return false;
         } else {
            ClipContext ctx = new ClipContext(mc.player.getEyePosition(), center, net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.NONE, mc.player);
            BlockHitResult result = mc.level.clip(ctx);
            return result.getType() == Type.BLOCK && result.getBlockPos().equals(this.action.blockPos);
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class CooldownCondition implements MacroConditionRegistry.PendingCondition {
      final ItemTarget target;
      final boolean checkMainInteractionHand;
      final CompletableFuture<Void> future;

      CooldownCondition(ItemTarget target, boolean checkMainHand, CompletableFuture<Void> future) {
         this.target = target == null ? new ItemTarget() : target.copy();
         this.checkMainInteractionHand = checkMainHand;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player == null) {
            return false;
         } else if (this.target.hasIdentity()) {
            boolean foundAny = false;

            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               if (!stack.isEmpty() && this.target.matches(stack, i)) {
                  foundAny = true;
                  if (mc.player.getCooldowns().isOnCooldown(stack)) {
                     return false;
                  }
               }
            }

            return foundAny;
         } else {
            ItemStack stack = this.checkMainInteractionHand ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            return stack.isEmpty() ? true : !mc.player.getCooldowns().isOnCooldown(stack);
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class EntityCondition implements MacroConditionRegistry.PendingCondition {
      final WaitForEntityAction action;
      final Set<String> entityIdSet;
      final Set<String> specificUuids;
      final double radiusSq;
      final CompletableFuture<Void> future;

      EntityCondition(WaitForEntityAction action, CompletableFuture<Void> future) {
         this.action = action;
         this.entityIdSet = new HashSet<>();
         this.specificUuids = new HashSet<>();

         for (String entry : action.entityIds) {
            if (entry.startsWith("~")) {
               String[] p = entry.split("~", 4);
               if (p.length >= 2 && !p[1].isEmpty()) {
                  this.specificUuids.add(p[1]);
               }
            } else {
               this.entityIdSet.add(entry);
            }
         }

         this.radiusSq = action.radius * action.radius;
         this.future = future;
      }

      private boolean entityMatches(Entity entity) {
         if (this.action.containerEntitiesOnly) {
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!typeId.contains("boat") && !typeId.contains("minecart") && !typeId.contains("llama") && !typeId.contains("chest")) {
               return false;
            }
         }

         if (this.entityIdSet.isEmpty() && this.specificUuids.isEmpty()) {
            return true;
         } else {
            if (!this.entityIdSet.isEmpty()) {
               String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
               if (this.entityIdSet.contains(typeId)) {
                  return true;
               }
            }

            return !this.specificUuids.isEmpty() && this.specificUuids.contains(entity.getStringUUID());
         }
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.level != null && mc.player != null) {
            switch (this.action.checkMode) {
               case LOOKING_AT:
                  Entity targeted = mc.crosshairPickEntity;
                  if (targeted != null && targeted != mc.player) {
                     return this.entityMatches(targeted);
                  }

                  return false;
               case MOUNTED_IN:
                  Entity vehicle = mc.player.getVehicle();
                  return vehicle != null && this.entityMatches(vehicle);
               case WITHIN_REACH:
                  double reachSq = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();

                  for (Entity entity : mc.level.entitiesForRendering()) {
                     if (entity != mc.player && !(entity.distanceToSqr(mc.player) > reachSq) && this.entityMatches(entity)) {
                        return true;
                     }
                  }

                  return false;
               case NEARBY:
               default:
                  Vec3 center = this.action.centerOnPlayer
                     ? new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                     : new Vec3(this.action.x, this.action.y, this.action.z);

                  for (Entity entityx : mc.level.entitiesForRendering()) {
                     if (entityx != mc.player && !(entityx.distanceToSqr(center) > this.radiusSq) && this.entityMatches(entityx)) {
                        return true;
                     }
                  }

                  return false;
            }
         } else {
            return false;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class GamemodeChangeCondition implements MacroConditionRegistry.PendingCondition {
      final WaitGamemodeChangeAction action;
      final RiptideFakeGamemode.Snapshot baseline;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      GamemodeChangeCondition(WaitGamemodeChangeAction action, RiptideFakeGamemode.Snapshot baseline, CompletableFuture<Void> future) {
         this.action = action == null ? new WaitGamemodeChangeAction() : action;
         this.baseline = baseline == null ? RiptideFakeGamemode.snapshot() : baseline;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         RiptideFakeGamemode.Snapshot now = RiptideFakeGamemode.snapshot();
         return now.realRevision() > this.baseline.realRevision() && this.action.accepts(this.safeMode(now.realMode()))
            ? true
            : this.action.detectFake && now.fakeRevision() > this.baseline.fakeRevision() && this.action.accepts(this.safeMode(now.displayedMode()));
      }

      private GameType safeMode(GameType mode) {
         return mode == null ? GameType.DEFAULT_MODE : mode;
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class GuiChangeCondition implements MacroConditionRegistry.PendingCondition {
      final WeakReference<Screen> prevScreen;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      GuiChangeCondition(Screen prevScreen, CompletableFuture<Void> future) {
         this.prevScreen = new WeakReference<>(prevScreen);
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         return mc.gui.screen() != this.prevScreen.get();
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class GuiCloseCondition implements MacroConditionRegistry.PendingCondition {
      final String titleLower;
      final String[] titleWords;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;
      private boolean sawMatchingGui = false;

      GuiCloseCondition(String title, CompletableFuture<Void> future) {
         this.titleLower = MacroExecutor.normalizeManualText(title).toLowerCase();
         this.titleWords = this.titleLower.isEmpty() ? new String[0] : this.titleLower.split("\\s+");
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         Screen screen = mc.gui.screen();
         if (screen != null && !this.titleLower.isEmpty() && MacroGuiMatcher.matches(screen, this.titleLower)) {
            this.sawMatchingGui = true;
            return false;
         } else if (this.titleLower.isEmpty()) {
            if (screen != null) {
               this.sawMatchingGui = true;
               return false;
            } else {
               return this.sawMatchingGui;
            }
         } else {
            return this.sawMatchingGui || screen == null;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class GuiCondition implements MacroConditionRegistry.PendingCondition {
      final String title;
      final String titleLower;
      final String[] titleWords;
      final WeakReference<Screen> prevScreen;
      final CompletableFuture<Void> future;

      GuiCondition(String title, Screen prevScreen, CompletableFuture<Void> future) {
         this.title = MacroExecutor.normalizeManualText(title);
         this.titleLower = this.title.toLowerCase();
         this.titleWords = this.titleLower.isEmpty() ? new String[0] : this.titleLower.split("\\s+");
         this.prevScreen = new WeakReference<>(prevScreen);
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         return this.checkScreen(mc.gui.screen());
      }

      public boolean checkScreen(Screen screen) {
         if (screen == null) {
            return false;
         } else if (MacroGuiMatcher.isOwnScreen(screen)) {
            return false;
         } else if (screen == this.prevScreen.get()) {
            return false;
         } else if (!this.titleLower.isEmpty() && !MacroGuiMatcher.matches(screen, this.title)) {
            return false;
         } else {
            if (!this.titleLower.isEmpty()) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null
                  && mc.player.containerMenu != null
                  && mc.player.containerMenu != mc.player.inventoryMenu
                  && screen instanceof AbstractContainerScreen) {
                  return mc.player.containerMenu.getStateId() > 0;
               }
            }

            return true;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class HandlerItemCondition implements MacroConditionRegistry.PendingCondition {
      final ItemTarget target;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      HandlerItemCondition(ItemTarget target, CompletableFuture<Void> future) {
         this.target = target == null ? new ItemTarget() : target.copy();
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player != null && this.target.hasIdentity()) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) {
               return false;
            } else {
               for (Slot slot : handler.slots) {
                  if (slot != null && !slot.getItem().isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.target.matches(slot.getItem(), visibleSlot)) {
                        return true;
                     }
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class HealthCondition implements MacroConditionRegistry.PendingCondition {
      final float threshold;
      final boolean below;
      final CompletableFuture<Void> future;

      HealthCondition(float threshold, boolean below, CompletableFuture<Void> future) {
         this.threshold = threshold;
         this.below = below;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player == null) {
            return false;
         } else {
            float health = mc.player.getHealth();
            return this.below ? health < this.threshold : health > this.threshold;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class ItemCondition implements MacroConditionRegistry.PendingCondition {
      final ItemTarget target;
      final CompletableFuture<Void> future;

      ItemCondition(ItemTarget target, CompletableFuture<Void> future) {
         this.target = target == null ? new ItemTarget() : target.copy();
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player != null && this.target.hasIdentity()) {
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               if (!stack.isEmpty() && this.target.matches(stack, i)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class LanStepCondition implements MacroConditionRegistry.PendingCondition {
      final WaitForLanStepAction action;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      LanStepCondition(WaitForLanStepAction action, CompletableFuture<Void> future) {
         this.action = action;
         this.future = future;
      }

      boolean checkNow() {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         if (!sync.isInSession()) {
            return true;
         } else {
            String myUsername = sync.getMyUsername();
            Map<String, Integer> steps = sync.getAllPeerSteps();
            if (!this.action.filterByUser) {
               return this.anyOtherPeerReached(steps, myUsername, this.action.defaultStep);
            } else if (this.action.entries.isEmpty()) {
               return this.anyOtherPeerReached(steps, myUsername, this.action.defaultStep);
            } else {
               int peerCount = sync.getConnectedCount();

               for (WaitForLanStepAction.LanStepEntry req : this.action.entries) {
                  String targetUser = req.username;
                  if (peerCount <= 2 && !targetUser.isEmpty()) {
                     boolean nameExists = false;

                     for (String name : steps.keySet()) {
                        if (name.equals(targetUser) && !name.equals(myUsername)) {
                           nameExists = true;
                           break;
                        }
                     }

                     if (!nameExists) {
                        targetUser = "";
                     }
                  }

                  if (targetUser.isEmpty()) {
                     boolean anyReached = false;

                     for (Entry<String, Integer> entry : steps.entrySet()) {
                        if (!entry.getKey().equals(myUsername) && entry.getValue() >= req.step) {
                           anyReached = true;
                           break;
                        }
                     }

                     if (!anyReached) {
                        return false;
                     }
                  } else {
                     Map<String, ?> clients = sync.getConnectedClients();
                     if (clients.containsKey(targetUser)) {
                        int peerStep = steps.getOrDefault(targetUser, 0);
                        if (peerStep < req.step) {
                           return false;
                        }
                     }
                  }
               }

               return true;
            }
         }
      }

      private boolean anyOtherPeerReached(Map<String, Integer> steps, String myUsername, int targetStep) {
         for (Entry<String, Integer> entry : steps.entrySet()) {
            if (!entry.getKey().equals(myUsername) && entry.getValue() >= targetStep) {
               return true;
            }
         }

         return false;
      }

      @Override
      public boolean check(Minecraft mc) {
         return this.checkNow();
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   interface PendingCondition {
      boolean check(Minecraft var1);

      void complete();

      void cancel();

      boolean isCancelled();
   }

   static class PositionDeltaCondition implements MacroConditionRegistry.PendingCondition {
      final Vec3 origin;
      final double distanceSquared;
      final boolean horizontalOnly;
      final boolean anyMovement;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      PositionDeltaCondition(Vec3 origin, double distance, boolean horizontalOnly, CompletableFuture<Void> future) {
         this.origin = origin == null ? Vec3.ZERO : origin;
         double d = Math.max(0.0, distance);
         this.anyMovement = d <= 0.0;
         this.distanceSquared = d * d;
         this.horizontalOnly = horizontalOnly;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player == null) {
            return false;
         } else {
            Vec3 now = mc.player.position();
            double dx = now.x - this.origin.x;
            double dy = this.horizontalOnly ? 0.0 : now.y - this.origin.y;
            double dz = now.z - this.origin.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            return this.anyMovement ? d2 > 1.0E-4 : d2 >= this.distanceSquared;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class ServerTickSyncCondition implements MacroConditionRegistry.PendingCondition {
      final CompletableFuture<Void> future;
      final int bufferMs;
      final int maxWaitMs;
      final boolean ignorePing;
      final long startTimeNanos;

      ServerTickSyncCondition(CompletableFuture<Void> future, int bufferMs, int maxWaitMs, boolean ignorePing) {
         this.future = future;
         this.bufferMs = bufferMs;
         this.maxWaitMs = maxWaitMs;
         this.ignorePing = ignorePing;
         this.startTimeNanos = System.nanoTime();
      }

      @Override
      public boolean check(Minecraft mc) {
         long now = System.nanoTime();
         long elapsedMs = (now - this.startTimeNanos) / 1000000L;
         if (elapsedMs >= this.maxWaitMs) {
            return true;
         } else if (!ServerTickTracker.isReady()) {
            return false;
         } else {
            long optimalTime = ServerTickTracker.getOptimalSendTime(this.bufferMs, this.ignorePing);
            return now >= optimalTime;
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class SlotChangeCondition implements MacroConditionRegistry.PendingCondition {
      final WaitForSlotChangeAction action;
      final Map<Integer, String> initialNames;
      final Map<Integer, Integer> initialCounts;
      final CompletableFuture<Void> future;

      SlotChangeCondition(
         WaitForSlotChangeAction action, Map<Integer, String> initialNames, Map<Integer, Integer> initialCounts, CompletableFuture<Void> future
      ) {
         this.action = action;
         this.initialNames = initialNames;
         this.initialCounts = initialCounts;
         this.future = future;
      }

      SlotChangeCondition(int slotNumber, String initialItemName, int initialCount, CompletableFuture<Void> future) {
         WaitForSlotChangeAction a = new WaitForSlotChangeAction(slotNumber);
         this.action = a;
         this.initialNames = new HashMap<>();
         this.initialCounts = new HashMap<>();
         if (slotNumber >= 0) {
            this.initialNames.put(0, initialItemName);
            this.initialCounts.put(0, initialCount);
         }

         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player == null) {
            return false;
         } else if (this.action.entries.isEmpty()) {
            return true;
         } else {
            AbstractContainerMenu handler = mc.player.containerMenu;

            for (int i = 0; i < this.action.entries.size(); i++) {
               if (!this.checkEntry(mc, handler, this.action.entries.get(i), i)) {
                  return false;
               }
            }

            return true;
         }
      }

      private boolean checkEntry(Minecraft mc, AbstractContainerMenu handler, WaitForSlotChangeAction.WaitEntry e, int entryIdx) {
         ItemTarget entryTarget = e.resolvedTarget();
         if (entryTarget == null) {
            entryTarget = new ItemTarget();
         }

         if (entryTarget.hasSlot()) {
            int slotNum = entryTarget.slot;
            if (handler == null) {
               return e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
            } else {
               int handlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, slotNum);
               if (handlerSlot >= 0 && handlerSlot < handler.slots.size()) {
                  ItemStack stack = ((Slot)handler.slots.get(handlerSlot)).getItem();
                  return this.checkStack(stack, entryTarget, slotNum, e, entryIdx);
               } else {
                  return e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
               }
            }
         } else if (e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY) {
            if (handler != null) {
               for (Slot s : handler.slots) {
                  if (s != null && !s.getItem().isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, s.index);
                     if (!entryTarget.hasIdentity() || entryTarget.matches(s.getItem(), visibleSlot)) {
                        return false;
                     }
                  }
               }
            } else {
               for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                  ItemStack stack = mc.player.getInventory().getItem(i);
                  if (!stack.isEmpty() && (!entryTarget.hasIdentity() || entryTarget.matches(stack, i))) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            if (handler != null) {
               for (Slot sx : handler.slots) {
                  if (sx != null && !sx.getItem().isEmpty()) {
                     ItemStack stack = sx.getItem();
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, sx.index);
                     if ((!entryTarget.hasIdentity() || entryTarget.matches(stack, visibleSlot))
                        && this.checkStack(stack, entryTarget, visibleSlot, e, entryIdx)) {
                        return true;
                     }
                  }
               }
            } else {
               for (int ix = 0; ix < mc.player.getInventory().getContainerSize(); ix++) {
                  ItemStack stack = mc.player.getInventory().getItem(ix);
                  if (!stack.isEmpty()
                     && (!entryTarget.hasIdentity() || entryTarget.matches(stack, ix))
                     && this.checkStack(stack, entryTarget, ix, e, entryIdx)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      private boolean checkStack(ItemStack stack, ItemTarget target, int visibleSlot, WaitForSlotChangeAction.WaitEntry e, int entryIdx) {
         boolean nameMatches = target == null || !target.hasIdentity() || !stack.isEmpty() && target.matches(stack, visibleSlot);

         return switch (e.waitMode) {
            case NOT_EMPTY -> !stack.isEmpty() && nameMatches;
            case IS_EMPTY -> stack.isEmpty();
            case COUNT_AT_LEAST -> !stack.isEmpty() && nameMatches && stack.getCount() >= e.targetCount;
            case COUNT_BELOW -> stack.isEmpty() || nameMatches && stack.getCount() < e.targetCount;
            case ANY_CHANGE -> {
               String initName = this.initialNames.getOrDefault(entryIdx, "");
               int initCount = this.initialCounts.getOrDefault(entryIdx, 0);
               String curName = MacroConditionRegistry.snapshotStackIdentity(stack);
               int curCount = stack.isEmpty() ? 0 : stack.getCount();
               yield !curName.equals(initName) || curCount != initCount;
            }
         };
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class SoundCondition implements MacroConditionRegistry.PendingCondition {
      final WaitForSoundAction action;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      SoundCondition(WaitForSoundAction action, CompletableFuture<Void> future) {
         this.action = action;
         this.future = future;
      }

      public boolean matches(Minecraft mc, String soundId, double x, double y, double z) {
         if (!this.action.soundIds.isEmpty()) {
            boolean found = false;
            String played = normalizeSoundId(soundId);
            String playedPath = soundPath(played);

            for (String id : this.action.soundIds) {
               String expected = normalizeSoundId(id);
               if (expected.equals(played) || expected.equals(playedPath) || soundPath(expected).equals(played) || soundPath(expected).equals(playedPath)) {
                  found = true;
                  break;
               }
            }

            if (!found) {
               return false;
            }
         }

         if (this.action.checkDistance && mc != null && mc.player != null) {
            double dx = mc.player.getX() - x;
            double dy = mc.player.getY() - y;
            double dz = mc.player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > this.action.maxDistance * this.action.maxDistance) {
               return false;
            }
         }

         return true;
      }

      private static String normalizeSoundId(String value) {
         return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      }

      private static String soundPath(String value) {
         String normalized = normalizeSoundId(value);
         int split = normalized.indexOf(58);
         return split >= 0 && split + 1 < normalized.length() ? normalized.substring(split + 1) : normalized;
      }

      @Override
      public boolean check(Minecraft mc) {
         return false;
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }

   static class TickSyncCondition implements MacroConditionRegistry.PendingCondition {
      final CompletableFuture<Void> future;

      TickSyncCondition(CompletableFuture<Void> future) {
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         return true;
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class WaitPosCondition implements MacroConditionRegistry.PendingCondition {
      final double x;
      final double y;
      final double z;
      final double leewaySquared;
      final boolean checkRotation;
      final float yaw;
      final float pitch;
      final float rotLeeway;
      final CompletableFuture<Void> future;

      WaitPosCondition(
         double x, double y, double z, double leeway, boolean checkRotation, float yaw, float pitch, float rotLeeway, CompletableFuture<Void> future
      ) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.leewaySquared = leeway * leeway;
         this.checkRotation = checkRotation;
         this.yaw = yaw;
         this.pitch = pitch;
         this.rotLeeway = rotLeeway;
         this.future = future;
      }

      @Override
      public boolean check(Minecraft mc) {
         if (mc.player == null) {
            return false;
         } else {
            double dx = mc.player.getX() - this.x;
            double dy = mc.player.getY() - this.y;
            double dz = mc.player.getZ() - this.z;
            if (dx * dx + dy * dy + dz * dz > this.leewaySquared) {
               return false;
            } else {
               if (this.checkRotation) {
                  float currentYaw = Mth.wrapDegrees(mc.player.getYRot());
                  float currentPitch = Mth.wrapDegrees(mc.player.getXRot());
                  float targetYaw = Mth.wrapDegrees(this.yaw);
                  float targetPitch = Mth.wrapDegrees(this.pitch);
                  if (Math.abs(currentYaw - targetYaw) > this.rotLeeway) {
                     return false;
                  }

                  if (Math.abs(currentPitch - targetPitch) > this.rotLeeway) {
                     return false;
                  }
               }

               return true;
            }
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }
   }

   static class WorldChangeCondition implements MacroConditionRegistry.PendingCondition {
      final String initialDimension;
      final String targetDimension;
      final long createdAtNanos;
      final CompletableFuture<Void> future;
      private volatile boolean cancelled = false;

      WorldChangeCondition(String initialDimension, String targetDimension, CompletableFuture<Void> future) {
         this.initialDimension = initialDimension == null ? "" : initialDimension;
         this.targetDimension = MacroConditionRegistry.normalizeDimensionId(targetDimension);
         this.future = future;
         this.createdAtNanos = System.nanoTime();
      }

      @Override
      public boolean check(Minecraft mc) {
         if (MacroConditionRegistry.lastRespawnNanos > this.createdAtNanos && this.targetDimension.isEmpty()) {
            return true;
         } else {
            String current = MacroConditionRegistry.currentDimensionId(mc);
            return !current.isEmpty() && !current.equals(this.initialDimension)
               ? this.targetDimension.isEmpty() || MacroConditionRegistry.dimensionMatches(current, this.targetDimension)
               : MacroConditionRegistry.lastRespawnNanos > this.createdAtNanos
                  && !this.targetDimension.isEmpty()
                  && !current.isEmpty()
                  && MacroConditionRegistry.dimensionMatches(current, this.targetDimension);
         }
      }

      @Override
      public void complete() {
         this.future.complete(null);
      }

      @Override
      public void cancel() {
         this.cancelled = true;
         this.future.cancel(true);
      }

      @Override
      public boolean isCancelled() {
         return this.cancelled || this.future.isCancelled();
      }
   }
}
