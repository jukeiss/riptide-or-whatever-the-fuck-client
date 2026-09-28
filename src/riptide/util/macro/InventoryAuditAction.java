package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.modules.PackHideState;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideSharedState;

public class InventoryAuditAction implements MacroAction {
   private static final int MAX_DELAY_MS = 10000;
   private static final int MAX_ITERATIONS = 100;
   private static final int AUTO_GUI_TIMEOUT_MS = 12000;
   private static final int GUI_POLL_MS = 50;
   private static final int GUI_STABLE_POLLS_REQUIRED = 3;
   private static final int ACTION_WAIT_POLL_MS = 25;
   public InventoryAuditAction.Mode mode = InventoryAuditAction.Mode.DUPE;
   public List<String> targetItems = new ArrayList<>();
   public List<ItemTarget> itemTargets = new ArrayList<>();
   public String openCommand = "/ec";
   public InventoryAuditAction.OpenMode openMode = InventoryAuditAction.OpenMode.COMMAND;
   public BlockPos containerPos = BlockPos.ZERO;
   public InventoryAuditAction.DupeVector dupeVector = InventoryAuditAction.DupeVector.DESYNC_REOPEN;
   public int delayBeforeReopen = 200;
   public int delayAfterReopen = 500;
   public int iterations = 1;
   public int maxTransferAttempts = 5;
   public int transferRetryDelayMs = 50;
   public boolean multipleStacks = false;
   public int spamCount = 3;
   public int spamDelayMs = 50;
   private boolean enabled = true;

   public boolean hasValidTargetSelection() {
      return this.hasConfiguredTargets();
   }

   public boolean hasConfiguredTargets() {
      for (ItemTarget target : this.resolvedTargets()) {
         if (target != null && (target.hasSlot() || target.hasIdentity())) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc != null && mc.player != null) {
         if (this.mode == InventoryAuditAction.Mode.DUPE || this.mode == InventoryAuditAction.Mode.DUPE_SPAM) {
            ;
         }
      }
   }

   public void executeDupe(Minecraft mc) throws InterruptedException {
      if (mc != null && mc.player != null) {
         MacroTemplate.Resolution commandResolution = MacroVariables.resolve(this.normalizedOpenCommand(), mc);
         if (commandResolution.success()) {
            String cmd = commandResolution.value().trim();
            if (!cmd.isBlank()) {
               int runIterations = this.clampIterations(this.iterations);
               int maxAttempts = Math.max(1, Math.min(this.maxTransferAttempts, 20));
               int retryDelay = Math.max(10, Math.min(this.transferRetryDelayMs, 500));

               for (int iter = 0; iter < runIterations; iter++) {
                  this.ensureMacroRunning();
                  if (!this.openTargetAndWaitForGuiStable(mc, cmd, 12000L)) {
                     return;
                  }

                  InventoryAuditAction.InventorySnapshot before = this.captureOnGameThread(mc);
                  if (before == null || before.slots().isEmpty()) {
                     return;
                  }

                  Map<String, Integer> targetContainerItems = this.collectTargetContainerTotals(before);
                  if (targetContainerItems.isEmpty()) {
                     InventoryAuditAction.InventorySnapshot playerSnapshot = this.captureInventoryOnly(mc);
                     Map<String, Integer> playerTargetItems = this.collectTargetPlayerTotals(playerSnapshot);
                     if (!playerTargetItems.isEmpty()) {
                        boolean transferred = this.transferItemsToContainer(mc, playerTargetItems);
                        if (transferred) {
                           this.closeGuiOnGameThread(mc, true);
                           if (!this.waitForGuiClosed(mc, 12000L)) {
                              return;
                           }

                           if (!this.openTargetAndWaitForGuiStable(mc, cmd, 12000L)) {
                              return;
                           }

                           before = this.captureOnGameThread(mc);
                           if (before == null || before.slots().isEmpty()) {
                              return;
                           }

                           targetContainerItems = this.collectTargetContainerTotals(before);
                        }
                     }

                     if (targetContainerItems.isEmpty()) {
                        this.closeGuiOnGameThread(mc, true);
                        continue;
                     }
                  }

                  if (this.mode == InventoryAuditAction.Mode.DUPE_SPAM) {
                     int spamOpens = Math.max(1, Math.min(this.spamCount, 20));
                     int spamDelay = Math.max(10, Math.min(this.spamDelayMs, 1000));
                     this.spamOpenTarget(mc, cmd, spamOpens, spamDelay);
                     this.sleepInterruptibly(100L);
                  }

                  this.executeDupeVector(mc);
                  int totalTransferAttempts = 0;
                  int emptyVisibleTransferPasses = 0;
                  Set<Integer> targetSlots = this.getTargetSlots(mc);

                  while (totalTransferAttempts < maxAttempts) {
                     this.ensureMacroRunning();
                     if (!this.openTargetAndWaitForGuiStable(mc, cmd, 12000L)) {
                        break;
                     }

                     this.sleepInterruptibly(retryDelay);
                     InventoryAuditAction.TransferAttempt attempt = this.transferTargetsAggressive(mc, targetSlots);
                     totalTransferAttempts++;
                     if (attempt.slotCount() <= 0 && targetSlots.isEmpty()) {
                        if (++emptyVisibleTransferPasses >= 2) {
                           break;
                        }
                     } else {
                        emptyVisibleTransferPasses = 0;
                     }
                  }

                  if (!this.prepareForReopen(mc, 12000L)) {
                     return;
                  }

                  if (!this.openTargetAndWaitForGuiStable(mc, cmd, 12000L)) {
                     return;
                  }

                  this.closeGuiOnGameThread(mc, true);
                  if (iter < runIterations - 1) {
                     this.sleepInterruptibly(500L);
                  }
               }
            }
         }
      }
   }

   private Set<Integer> getTargetSlots(Minecraft mc) {
      Set<Integer> slots = new HashSet<>();
      List<ItemTarget> targets = this.runtimeTargets();
      if (!targets.isEmpty()) {
         for (ItemTarget target : targets) {
            if (target != null && target.hasSlot()) {
               slots.add(target.slot);
            }
         }
      }

      return slots;
   }

   private InventoryAuditAction.TransferAttempt transferTargetsAggressive(Minecraft mc, Set<Integer> targetSlots) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;
            Set<Integer> firedSlots = new HashSet<>();

            for (int visibleSlot : targetSlots) {
               int handlerSlot = RiptideInventoryHelper.toHandlerSlot(mc, visibleSlot);
               if (handlerSlot >= 0 && handlerSlot < handler.slots.size() && firedSlots.add(handlerSlot)) {
                  Slot slot = (Slot)handler.slots.get(handlerSlot);
                  if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                     ItemStack stack = slot.getItem();
                     if (stack != null && !stack.isEmpty()) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }

                     mc.gameMode.handleContainerInput(handler.containerId, handlerSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                  }
               }
            }

            for (int i = 0; i < handler.slots.size(); i++) {
               if (!firedSlots.contains(i)) {
                  Slot slot = (Slot)handler.slots.get(i);
                  if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                     ItemStack stack = slot.getItem();
                     if (stack != null && !stack.isEmpty()) {
                        int visibleSlotx = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                        if (this.shouldTransferSlot(stack, visibleSlotx)) {
                           firedSlots.add(i);
                           slotCount++;
                           items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                           mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                        }
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 2000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private void verifyActualDuplication(
      InventoryAuditAction.InventorySnapshot before,
      InventoryAuditAction.InventorySnapshot after,
      InventoryAuditAction.TransferAttempt transfer,
      String iterLabel,
      int transferAttempts
   ) {
   }

   private InventoryAuditAction.TransferAttempt executeDupeVector(Minecraft mc) throws InterruptedException {
      return switch (this.dupeVector) {
         case DESYNC_REOPEN -> this.executeDesyncReopen(mc);
         case CLOSE_NO_PACKET -> this.executeCloseNoPacket(mc);
         case SHIFT_CLICK_REOPEN -> this.executeShiftClickReopen(mc);
         case DELAYED_PACKETS -> this.executeDelayedPackets(mc);
         case SWAP_HOTBAR -> this.executeSwapHotbar(mc);
         case DROP_EXPLOIT -> this.executeDropExploit(mc);
         case DELAYED_DESYNC_REOPEN -> this.executeDelayedDesyncReopen(mc);
         case SWAP_DESYNC_REOPEN -> this.executeSwapDesyncReopen(mc);
         case DROP_DELAYED_PACKETS -> this.executeDropDelayedPackets(mc);
      };
   }

   private InventoryAuditAction.TransferAttempt executeDesyncReopen(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt transfer = this.shiftClickTargets(mc);
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player != null && mc.getConnection() != null && mc.player.containerMenu != null) {
               mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 2000L);
      this.sleepInterruptibly(100L);
      this.closeGuiOnGameThread(mc, false);
      return transfer;
   }

   private InventoryAuditAction.TransferAttempt executeCloseNoPacket(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt transfer = this.shiftClickTargets(mc);
      this.closeGuiOnGameThread(mc, false);
      return transfer;
   }

   private InventoryAuditAction.TransferAttempt executeShiftClickReopen(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;

            for (int i = 0; i < handler.slots.size(); i++) {
               Slot slot = (Slot)handler.slots.get(i);
               if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                  ItemStack stack = slot.getItem();
                  if (stack != null && !stack.isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.shouldTransferSlot(stack, visibleSlot) && this.clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
            mc.player.closeContainer();
            if (mc.gui.screen() != null) {
               mc.gui.setScreen(null);
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 3000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private InventoryAuditAction.TransferAttempt executeSwapDesyncReopen(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt transfer = this.swapTargetsToHotbar(mc);
      this.sendClosePacketOnGameThread(mc);
      this.sleepInterruptibly(100L);
      this.closeGuiOnGameThread(mc, false);
      return transfer;
   }

   private InventoryAuditAction.TransferAttempt executeDelayedPackets(Minecraft mc) throws InterruptedException {
      RiptideSharedState shared = RiptideSharedState.get();
      boolean previousDelayPackets = shared.shouldDelayGuiPackets();
      boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
      Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
      Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());
      shared.setUseCustomPackets(true);
      shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
      shared.setS2CPackets(Set.of());
      shared.setDelayGuiPackets(true);

      InventoryAuditAction.TransferAttempt var10;
      try {
         InventoryAuditAction.TransferAttempt transfer = this.shiftClickTargets(mc);
         int[] flushed = new int[]{0};
         CountDownLatch flushLatch = new CountDownLatch(1);
         mc.execute(() -> {
            try {
               if (mc.getConnection() != null && mc.player != null) {
                  flushed[0] = shared.flushDelayedPackets(mc.getConnection());
                  if (mc.player.containerMenu != null) {
                     mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                  }
               }
            } finally {
               flushLatch.countDown();
            }
         });
         this.awaitLatch(flushLatch, 3000L);
         this.closeGuiOnGameThread(mc, false);
         var10 = transfer;
      } finally {
         shared.setDelayGuiPackets(previousDelayPackets);
         shared.setUseCustomPackets(previousUseCustomPackets);
         shared.setC2SPackets(previousC2SPackets);
         shared.setS2CPackets(previousS2CPackets);
      }

      return var10;
   }

   private InventoryAuditAction.TransferAttempt executeDelayedDesyncReopen(Minecraft mc) throws InterruptedException {
      RiptideSharedState shared = RiptideSharedState.get();
      boolean previousDelayPackets = shared.shouldDelayGuiPackets();
      boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
      Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
      Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());
      shared.setUseCustomPackets(true);
      shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
      shared.setS2CPackets(Set.of());
      shared.setDelayGuiPackets(true);

      InventoryAuditAction.TransferAttempt var9;
      try {
         InventoryAuditAction.TransferAttempt transfer = this.shiftClickTargets(mc);
         CountDownLatch flushLatch = new CountDownLatch(1);
         mc.execute(() -> {
            try {
               if (mc.getConnection() != null && mc.player != null) {
                  shared.flushDelayedPackets(mc.getConnection());
                  if (mc.player.containerMenu != null) {
                     mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                  }
               }
            } finally {
               flushLatch.countDown();
            }
         });
         this.awaitLatch(flushLatch, 3000L);
         this.sleepInterruptibly(100L);
         this.closeGuiOnGameThread(mc, false);
         var9 = transfer;
      } finally {
         shared.setDelayGuiPackets(previousDelayPackets);
         shared.setUseCustomPackets(previousUseCustomPackets);
         shared.setC2SPackets(previousC2SPackets);
         shared.setS2CPackets(previousS2CPackets);
      }

      return var9;
   }

   private InventoryAuditAction.TransferAttempt executeSwapHotbar(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;
            int hotbarSlot = 0;

            for (int i = 0; i < handler.slots.size() && hotbarSlot <= 8; i++) {
               Slot slot = (Slot)handler.slots.get(i);
               if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                  ItemStack stack = slot.getItem();
                  if (stack != null && !stack.isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.shouldTransferSlot(stack, visibleSlot) && this.clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
            mc.player.closeContainer();
            if (mc.gui.screen() != null) {
               mc.gui.setScreen(null);
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 3000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private InventoryAuditAction.TransferAttempt executeDropExploit(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt transfer = this.throwTargets(mc);
      this.closeGuiOnGameThread(mc, false);
      return transfer;
   }

   private InventoryAuditAction.TransferAttempt executeDropDelayedPackets(Minecraft mc) throws InterruptedException {
      RiptideSharedState shared = RiptideSharedState.get();
      boolean previousDelayPackets = shared.shouldDelayGuiPackets();
      boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
      Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
      Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());
      shared.setUseCustomPackets(true);
      shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
      shared.setS2CPackets(Set.of());
      shared.setDelayGuiPackets(true);

      InventoryAuditAction.TransferAttempt var9;
      try {
         InventoryAuditAction.TransferAttempt transfer = this.throwTargets(mc);
         CountDownLatch flushLatch = new CountDownLatch(1);
         mc.execute(() -> {
            try {
               if (mc.getConnection() != null && mc.player != null) {
                  shared.flushDelayedPackets(mc.getConnection());
                  if (mc.player.containerMenu != null) {
                     mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                  }
               }
            } finally {
               flushLatch.countDown();
            }
         });
         this.awaitLatch(flushLatch, 3000L);
         this.closeGuiOnGameThread(mc, false);
         var9 = transfer;
      } finally {
         shared.setDelayGuiPackets(previousDelayPackets);
         shared.setUseCustomPackets(previousUseCustomPackets);
         shared.setC2SPackets(previousC2SPackets);
         shared.setS2CPackets(previousS2CPackets);
      }

      return var9;
   }

   private void sendCommand(Minecraft mc, String cmd) {
      mc.execute(() -> {
         if (!PackHideState.isHardLocked() && MacroExecutor.isCurrentActionRunActive() && mc.getConnection() != null) {
            String clean = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            mc.getConnection().sendCommand(clean);
         }
      });
   }

   private void sendContainerOpen(Minecraft mc, BlockPos pos) {
      mc.execute(() -> {
         if (!PackHideState.isHardLocked() && MacroExecutor.isCurrentActionRunActive() && mc.player != null && mc.getConnection() != null && pos != null) {
            RiptideContainerTarget target = RiptideContainerTarget.forBlock(pos);
            if (target != null) {
               target.interact(mc);
            }
         }
      });
   }

   private void sendClosePacketOnGameThread(Minecraft mc) throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player != null && mc.getConnection() != null && mc.player.containerMenu != null) {
               mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 2000L);
   }

   private boolean waitForGui(Minecraft mc, long timeoutMs) throws InterruptedException {
      CompletableFuture<Void> future = MacroConditionRegistry.waitForGui("");

      boolean var5;
      try {
         var5 = this.awaitFutureCompletion(future, timeoutMs);
      } finally {
         future.cancel(true);
      }

      return var5;
   }

   private boolean waitForGuiStable(Minecraft mc, long timeoutMs) throws InterruptedException {
      if (!this.readGuiState(mc).open() && !this.waitForGui(mc, timeoutMs)) {
         return false;
      } else {
         long deadline = System.currentTimeMillis() + timeoutMs;
         InventoryAuditAction.GuiState previous = null;
         int stablePolls = 0;

         while (System.currentTimeMillis() <= deadline) {
            this.ensureMacroRunning();
            InventoryAuditAction.GuiState current = this.readGuiState(mc);
            if (current.open()) {
               if (current.equals(previous)) {
                  stablePolls++;
               } else {
                  previous = current;
                  stablePolls = 1;
               }

               if (stablePolls >= 3) {
                  return true;
               }
            } else {
               previous = null;
               stablePolls = 0;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
               break;
            }

            this.sleepInterruptibly(Math.min(50L, remaining));
         }

         return false;
      }
   }

   private boolean waitForGuiClosed(Minecraft mc, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;

      while (System.currentTimeMillis() <= deadline) {
         this.ensureMacroRunning();
         if (!this.readGuiState(mc).open()) {
            return true;
         }

         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            break;
         }

         this.sleepInterruptibly(Math.min(50L, remaining));
      }

      return !this.readGuiState(mc).open();
   }

   private boolean prepareForReopen(Minecraft mc, long timeoutMs) throws InterruptedException {
      if (this.readGuiState(mc).open()) {
         this.closeGuiOnGameThread(mc, true);
      }

      return this.waitForGuiClosed(mc, timeoutMs);
   }

   private boolean openTargetAndWaitForGuiStable(Minecraft mc, String cmd, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      long retryIntervalMs = this.resolveOpenRetryIntervalMs();
      long nextAttemptAt = 0L;
      int attempts = 0;
      int maxAttempts = this.resolveOpenAttemptBudget();
      InventoryAuditAction.GuiState previous = null;
      int stablePolls = 0;

      while (System.currentTimeMillis() <= deadline) {
         this.ensureMacroRunning();
         long now = System.currentTimeMillis();
         InventoryAuditAction.GuiState current = this.readGuiState(mc);
         if (current.open()) {
            if (current.equals(previous)) {
               stablePolls++;
            } else {
               previous = current;
               stablePolls = 1;
            }

            if (stablePolls >= 3) {
               return true;
            }
         } else {
            previous = null;
            stablePolls = 0;
            if (attempts < maxAttempts && now >= nextAttemptAt) {
               if (this.openMode == InventoryAuditAction.OpenMode.CONTAINER) {
                  this.sendContainerOpen(mc, this.containerPos);
               } else {
                  this.sendCommand(mc, cmd);
               }

               attempts++;
               nextAttemptAt = now + retryIntervalMs;
            }
         }

         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            break;
         }

         this.sleepInterruptibly(Math.min(50L, remaining));
      }

      return false;
   }

   private void spamOpenTarget(Minecraft mc, String cmd, int totalOpens, int delayMs) throws InterruptedException {
      for (int spam = 1; spam < totalOpens; spam++) {
         this.sleepInterruptibly(delayMs);
         if (this.openMode == InventoryAuditAction.OpenMode.CONTAINER) {
            this.sendContainerOpen(mc, this.containerPos);
         } else {
            this.sendCommand(mc, cmd);
         }
      }
   }

   private int resolveOpenAttemptBudget() {
      if (this.mode == InventoryAuditAction.Mode.DUPE_SPAM) {
         return Math.max(2, Math.min(this.spamCount, this.openMode == InventoryAuditAction.OpenMode.CONTAINER ? 8 : 4));
      } else {
         return this.openMode == InventoryAuditAction.OpenMode.CONTAINER ? 4 : 2;
      }
   }

   private long resolveOpenRetryIntervalMs() {
      int configuredDelay = this.mode == InventoryAuditAction.Mode.DUPE_SPAM
         ? Math.max(10, Math.min(this.spamDelayMs, 1000))
         : Math.max(10, Math.min(this.transferRetryDelayMs, 500));
      long fallback = this.openMode == InventoryAuditAction.OpenMode.CONTAINER ? 175L : 250L;
      return Math.max(50L, Math.min(configuredDelay > 0 ? configuredDelay : fallback, 400L));
   }

   private InventoryAuditAction.GuiState readGuiState(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.GuiState[] state = new InventoryAuditAction.GuiState[]{new InventoryAuditAction.GuiState(false, -1, -1, 0, 0)};
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
               AbstractContainerMenu handler = mc.player.containerMenu;
               int filledSlots = 0;
               int totalItems = 0;

               for (Slot slot : handler.slots) {
                  if (slot != null) {
                     ItemStack stack = slot.getItem();
                     if (stack != null && !stack.isEmpty()) {
                        filledSlots++;
                        totalItems += stack.getCount();
                     }
                  }
               }

               state[0] = new InventoryAuditAction.GuiState(true, handler.containerId, handler.slots.size(), filledSlots, totalItems);
               return;
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 2000L);
      return state[0];
   }

   private void closeGuiOnGameThread(Minecraft mc, boolean sendPacket) throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player != null) {
               if (!sendPacket) {
                  RiptideSharedState.get().setSuppressNextClosePacket(true);
               }

               if (mc.player.containerMenu != mc.player.inventoryMenu) {
                  mc.player.closeContainer();
               }

               if (mc.gui.screen() != null) {
                  mc.gui.setScreen(null);
               }
            }
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 2000L);
   }

   private InventoryAuditAction.TransferAttempt shiftClickTargets(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;

            for (int i = 0; i < handler.slots.size(); i++) {
               Slot slot = (Slot)handler.slots.get(i);
               if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                  ItemStack stack = slot.getItem();
                  if (stack != null && !stack.isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.shouldTransferSlot(stack, visibleSlot) && this.clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 3000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private InventoryAuditAction.TransferAttempt swapTargetsToHotbar(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;
            int hotbarSlot = 0;

            for (int i = 0; i < handler.slots.size() && hotbarSlot <= 8; i++) {
               Slot slot = (Slot)handler.slots.get(i);
               if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                  ItemStack stack = slot.getItem();
                  if (stack != null && !stack.isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.shouldTransferSlot(stack, visibleSlot) && this.clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 3000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private InventoryAuditAction.TransferAttempt throwTargets(Minecraft mc) throws InterruptedException {
      InventoryAuditAction.TransferAttempt[] result = new InventoryAuditAction.TransferAttempt[1];
      CountDownLatch latch = new CountDownLatch(1);
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.gameMode == null) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null || handler == mc.player.inventoryMenu) {
               result[0] = new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
               return;
            }

            Map<String, Integer> items = new LinkedHashMap<>();
            int slotCount = 0;

            for (int i = 0; i < handler.slots.size(); i++) {
               Slot slot = (Slot)handler.slots.get(i);
               if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                  ItemStack stack = slot.getItem();
                  if (stack != null && !stack.isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.shouldTransferSlot(stack, visibleSlot) && this.clickContainerSlotExtracted(mc, handler, i, 1, ContainerInput.THROW)) {
                        slotCount++;
                        items.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
                     }
                  }
               }
            }

            result[0] = new InventoryAuditAction.TransferAttempt(slotCount, items);
         } finally {
            latch.countDown();
         }
      });
      this.awaitLatch(latch, 3000L);
      return result[0] != null ? result[0] : new InventoryAuditAction.TransferAttempt(0, new LinkedHashMap<>());
   }

   private boolean clickContainerSlotExtracted(Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input) {
      if (mc == null || mc.player == null || mc.gameMode == null || handler == null) {
         return false;
      } else if (mc.player.containerMenu != handler) {
         return false;
      } else if (slotId >= 0 && slotId < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(slotId);
         if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
            ItemStack beforeSlot = slot.getItem().copy();
            if (beforeSlot.isEmpty()) {
               return false;
            } else {
               mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
               if (slotId >= handler.slots.size()) {
                  return true;
               } else {
                  Slot afterSlot = (Slot)handler.slots.get(slotId);
                  if (afterSlot == null) {
                     return true;
                  } else {
                     ItemStack afterStack = afterSlot.getItem();
                     return afterStack.isEmpty()
                        ? true
                        : ItemStack.isSameItemSameComponents(beforeSlot, afterStack) && afterStack.getCount() < beforeSlot.getCount();
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean clickPlayerSlotDeposited(Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input) {
      if (mc == null || mc.player == null || mc.gameMode == null || handler == null) {
         return false;
      } else if (mc.player.containerMenu != handler) {
         return false;
      } else if (slotId >= 0 && slotId < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(slotId);
         if (slot != null && RiptideInventoryHelper.isInventorySlot(mc, slot)) {
            ItemStack beforeSlot = slot.getItem().copy();
            if (beforeSlot.isEmpty()) {
               return false;
            } else {
               mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
               if (slotId >= handler.slots.size()) {
                  return true;
               } else {
                  Slot afterSlot = (Slot)handler.slots.get(slotId);
                  if (afterSlot == null) {
                     return true;
                  } else {
                     ItemStack afterStack = afterSlot.getItem();
                     return afterStack.isEmpty()
                        ? true
                        : ItemStack.isSameItemSameComponents(beforeSlot, afterStack) && afterStack.getCount() < beforeSlot.getCount();
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean shouldTransferSlot(ItemStack stack, int visibleSlot) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else {
         return this.targetItems.isEmpty() ? false : this.matchesTargetEntry(stack, visibleSlot);
      }
   }

   private boolean matchesTargetEntry(ItemStack stack, int visibleSlot) {
      List<ItemTarget> targets = this.runtimeTargets();
      if (targets.isEmpty()) {
         return false;
      } else {
         for (ItemTarget itemTarget : targets) {
            if (!itemTarget.hasSlot() || itemTarget.slot == visibleSlot) {
               if (!itemTarget.hasIdentity() || itemTarget.matches(stack, visibleSlot)) {
                  return true;
               }
            } else if (itemTarget.hasIdentity()) {
               ItemTarget identityTarget = itemTarget.copy();
               identityTarget.slot = -1;
               if (identityTarget.matches(stack, visibleSlot)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private Map<String, Integer> collectTargetContainerTotals(InventoryAuditAction.InventorySnapshot snapshot) {
      Map<String, Integer> totals = new LinkedHashMap<>();
      if (snapshot == null) {
         return totals;
      } else {
         for (InventoryAuditAction.SlotSnapshot slot : snapshot.slots().values()) {
            ItemStack stack = slot.stack();
            if (stack != null && !stack.isEmpty() && !this.isPlayerVisibleSlot(slot.visibleSlot()) && this.shouldTransferSlot(stack, slot.visibleSlot())) {
               totals.merge(this.shortItemId(stack), stack.getCount(), Integer::sum);
            }
         }

         return totals;
      }
   }

   private int sumTotals(Map<String, Integer> totals, Set<String> items) {
      int sum = 0;

      for (String item : items) {
         sum += totals.getOrDefault(item, 0);
      }

      return sum;
   }

   private boolean isPlayerVisibleSlot(int visibleSlot) {
      return visibleSlot >= 0 && visibleSlot < 41;
   }

   private InventoryAuditAction.InventorySnapshot captureInventoryOnly(Minecraft mc) throws InterruptedException {
      CompletableFuture<InventoryAuditAction.InventorySnapshot> future = new CompletableFuture<>();
      mc.execute(
         () -> {
            try {
               if (mc.player == null || mc.player.containerMenu == null) {
                  future.complete(null);
                  return;
               }

               AbstractContainerMenu handler = mc.player.containerMenu;
               String title = mc.gui.screen() != null && mc.gui.screen().getTitle() != null ? mc.gui.screen().getTitle().getString() : "";
               Map<Integer, InventoryAuditAction.SlotSnapshot> slots = new LinkedHashMap<>();

               for (Slot slot : handler.slots) {
                  if (slot != null) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                     if (this.isPlayerVisibleSlot(visibleSlot)) {
                        slots.put(
                           visibleSlot, new InventoryAuditAction.SlotSnapshot(visibleSlot, this.formatSlotLabel(visibleSlot), this.copyStack(slot.getItem()))
                        );
                     }
                  }
               }

               future.complete(new InventoryAuditAction.InventorySnapshot(title, System.currentTimeMillis(), slots));
            } catch (Exception var9) {
               future.complete(null);
            }
         }
      );
      return this.awaitFutureValue(future, 3000L);
   }

   private Map<String, Integer> collectTargetPlayerTotals(InventoryAuditAction.InventorySnapshot snapshot) {
      Map<String, Integer> totals = new LinkedHashMap<>();
      List<ItemTarget> targets = this.runtimeTargets();
      if (snapshot != null && !targets.isEmpty()) {
         for (InventoryAuditAction.SlotSnapshot slot : snapshot.slots().values()) {
            ItemStack stack = slot.stack();
            if (stack != null && !stack.isEmpty()) {
               String itemId = this.shortItemId(stack);

               for (ItemTarget itemTarget : targets) {
                  if (this.matchesPlayerInventoryTarget(itemTarget, stack, slot.visibleSlot())) {
                     totals.merge(itemId, stack.getCount(), Integer::sum);
                     break;
                  }
               }
            }
         }

         return totals;
      } else {
         return totals;
      }
   }

   private boolean matchesPlayerInventoryTarget(ItemTarget itemTarget, ItemStack stack, int visibleSlot) {
      if (itemTarget == null) {
         return false;
      } else if (itemTarget.hasIdentity()) {
         ItemTarget identityTarget = itemTarget.copy();
         identityTarget.slot = -1;
         return identityTarget.matches(stack, visibleSlot);
      } else {
         return itemTarget.matches(stack, visibleSlot);
      }
   }

   private boolean transferItemsToContainer(Minecraft mc, Map<String, Integer> targetItemsToTransfer) throws InterruptedException {
      if (targetItemsToTransfer != null && !targetItemsToTransfer.isEmpty()) {
         boolean[] result = new boolean[]{false};
         CountDownLatch latch = new CountDownLatch(1);
         mc.execute(
            () -> {
               try {
                  if (mc.player != null && mc.player.containerMenu != null && mc.gameMode != null) {
                     AbstractContainerMenu handler = mc.player.containerMenu;
                     if (handler != mc.player.inventoryMenu) {
                        for (int i = 0; i < handler.slots.size(); i++) {
                           if (!MacroExecutor.isCurrentActionRunActive()) {
                              return;
                           }

                           Slot slot = (Slot)handler.slots.get(i);
                           if (slot != null && RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                              ItemStack stack = slot.getItem();
                              if (stack != null && !stack.isEmpty()) {
                                 int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                                 if (this.isPlayerVisibleSlot(visibleSlot)
                                    && targetItemsToTransfer.containsKey(this.shortItemId(stack))
                                    && this.clickPlayerSlotDeposited(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                                    result[0] = true;
                                    if (!this.multipleStacks) {
                                       return;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               } finally {
                  latch.countDown();
               }
            }
         );
         this.awaitLatch(latch, 3000L);
         return result[0];
      } else {
         return false;
      }
   }

   private boolean matchesPlayerInventoryStack(ItemStack stack, int visibleSlot) {
      List<ItemTarget> targets = this.runtimeTargets();
      if (targets.isEmpty()) {
         return false;
      } else {
         for (ItemTarget itemTarget : targets) {
            if (this.matchesPlayerInventoryTarget(itemTarget, stack, visibleSlot)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean extractTargetsFromContainerToInventory(Minecraft mc, Map<String, Integer> targetItemsToExtract) throws InterruptedException {
      if (targetItemsToExtract != null && !targetItemsToExtract.isEmpty()) {
         boolean[] result = new boolean[]{false};
         CountDownLatch latch = new CountDownLatch(1);
         mc.execute(
            () -> {
               try {
                  if (mc.player != null && mc.player.containerMenu != null && mc.gameMode != null) {
                     AbstractContainerMenu handler = mc.player.containerMenu;

                     for (int i = 0; i < handler.slots.size(); i++) {
                        if (!MacroExecutor.isCurrentActionRunActive()) {
                           return;
                        }

                        Slot slot = (Slot)handler.slots.get(i);
                        if (slot != null && !RiptideInventoryHelper.isInventorySlot(mc, slot)) {
                           ItemStack stack = slot.getItem();
                           if (stack != null && !stack.isEmpty()) {
                              int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                              if (this.shouldTransferSlot(stack, visibleSlot)
                                 && targetItemsToExtract.containsKey(this.shortItemId(stack))
                                 && this.clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                                 result[0] = true;
                                 if (!this.multipleStacks) {
                                    return;
                                 }
                              }
                           }
                        }
                     }
                  }
               } finally {
                  latch.countDown();
               }
            }
         );
         this.awaitLatch(latch, 3000L);
         return result[0];
      } else {
         return false;
      }
   }

   private InventoryAuditAction.InventorySnapshot captureOnGameThread(Minecraft mc) throws InterruptedException {
      CompletableFuture<InventoryAuditAction.InventorySnapshot> future = new CompletableFuture<>();
      mc.execute(() -> {
         try {
            if (mc.player == null || mc.player.containerMenu == null) {
               future.complete(null);
               return;
            }

            AbstractContainerMenu handler = mc.player.containerMenu;
            future.complete(this.captureAutoTestSnapshot(mc, handler));
         } catch (Exception var4) {
            future.complete(null);
         }
      });
      return this.awaitFutureValue(future, 3000L);
   }

   private InventoryAuditAction.InventorySnapshot captureAutoTestSnapshot(Minecraft mc, AbstractContainerMenu handler) {
      String title = mc.gui.screen() != null && mc.gui.screen().getTitle() != null ? mc.gui.screen().getTitle().getString() : "";
      Map<Integer, InventoryAuditAction.SlotSnapshot> slots = this.captureAllSlots(mc, handler);
      return new InventoryAuditAction.InventorySnapshot(title, System.currentTimeMillis(), slots);
   }

   private Map<Integer, InventoryAuditAction.SlotSnapshot> captureAllSlots(Minecraft mc, AbstractContainerMenu handler) {
      Map<Integer, InventoryAuditAction.SlotSnapshot> slots = new LinkedHashMap<>();

      for (Slot slot : handler.slots) {
         if (slot != null) {
            int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
            slots.putIfAbsent(
               visibleSlot, new InventoryAuditAction.SlotSnapshot(visibleSlot, this.formatSlotLabel(visibleSlot), this.copyStack(slot.getItem()))
            );
         }
      }

      return slots;
   }

   private ItemStack copyStack(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? stack.copy() : ItemStack.EMPTY;
   }

   private String formatSlotLabel(int visibleSlot) {
      return "Slot " + visibleSlot;
   }

   private String shortItemId(ItemStack stack) {
      String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      return itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
   }

   private void ensureMacroRunning() throws InterruptedException {
      if (Thread.currentThread().isInterrupted() || !MacroExecutor.isCurrentActionRunActive()) {
         throw new InterruptedException("Macro stopped");
      }
   }

   private void sleepInterruptibly(long millis) throws InterruptedException {
      long remaining = Math.max(0L, millis);

      while (remaining > 0L) {
         this.ensureMacroRunning();
         long slice = Math.min(remaining, 25L);
         Thread.sleep(slice);
         remaining -= slice;
      }
   }

   private boolean awaitLatch(CountDownLatch latch, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);

      while (latch.getCount() > 0L) {
         this.ensureMacroRunning();
         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            return false;
         }

         if (latch.await(Math.min(remaining, 25L), TimeUnit.MILLISECONDS)) {
            return true;
         }
      }

      return true;
   }

   private boolean awaitFutureCompletion(CompletableFuture<?> future, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);

      while (true) {
         this.ensureMacroRunning();
         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            future.cancel(true);
            return false;
         }

         try {
            future.get(Math.min(remaining, 25L), TimeUnit.MILLISECONDS);
            return true;
         } catch (TimeoutException var9) {
         } catch (ExecutionException | CancellationException var10) {
            return false;
         }
      }
   }

   private <T> T awaitFutureValue(CompletableFuture<T> future, long timeoutMs) throws InterruptedException {
      long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);

      while (true) {
         this.ensureMacroRunning();
         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            future.cancel(true);
            return null;
         }

         try {
            return future.get(Math.min(remaining, 25L), TimeUnit.MILLISECONDS);
         } catch (TimeoutException var9) {
         } catch (ExecutionException | CancellationException var10) {
            return null;
         }
      }
   }

   private int clampDelayMs(int value) {
      return Math.max(0, Math.min(value, 10000));
   }

   private int clampIterations(int value) {
      return Math.max(1, Math.min(value, 100));
   }

   private String normalizedOpenCommand() {
      String raw = this.openCommand == null ? "" : this.openCommand.trim();
      return raw.isEmpty() ? "/ec" : raw;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("mode", this.mode.name());
      tag.put("targetItems", ItemTarget.toTagList(this.resolvedTargets()));
      tag.putString("openCommand", this.normalizedOpenCommand());
      tag.putString("openMode", this.openMode.name());
      tag.putInt("containerX", this.containerPos.getX());
      tag.putInt("containerY", this.containerPos.getY());
      tag.putInt("containerZ", this.containerPos.getZ());
      tag.putString("dupeVector", this.dupeVector.name());
      tag.putInt("delayBeforeReopen", this.clampDelayMs(this.delayBeforeReopen));
      tag.putInt("delayAfterReopen", this.clampDelayMs(this.delayAfterReopen));
      tag.putInt("iterations", this.clampIterations(this.iterations));
      tag.putInt("maxTransferAttempts", Math.max(1, Math.min(this.maxTransferAttempts, 20)));
      tag.putInt("transferRetryDelayMs", Math.max(10, Math.min(this.transferRetryDelayMs, 500)));
      tag.putBoolean("multipleStacks", this.multipleStacks);
      tag.putInt("spamCount", Math.max(1, Math.min(this.spamCount, 20)));
      tag.putInt("spamDelayMs", Math.max(10, Math.min(this.spamDelayMs, 1000)));
      tag.putInt("grabPreDelayMs", 0);
      tag.putBoolean("closeAfterGrab", true);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("mode")) {
         try {
            String modeStr = tag.getStringOr("mode", "DUPE");
            if (!"AUTO_TEST".equals(modeStr) && !"GRAB".equals(modeStr) && !"SAVE".equals(modeStr) && !"COMPARE".equals(modeStr) && !"CLEAR".equals(modeStr)) {
               this.mode = InventoryAuditAction.Mode.valueOf(modeStr);
            } else {
               this.mode = InventoryAuditAction.Mode.DUPE;
            }
         } catch (IllegalArgumentException var5) {
            this.mode = InventoryAuditAction.Mode.DUPE;
         }
      } else {
         this.mode = InventoryAuditAction.Mode.DUPE;
      }

      this.itemTargets.clear();
      this.targetItems.clear();
      if (tag.contains("targetItems")) {
         ListTag list = tag.getList("targetItems").orElse(new ListTag());
         this.itemTargets.addAll(ItemTarget.fromElementList(list));
      }

      this.syncLegacyTargetItems();
      if (tag.contains("openCommand")) {
         this.openCommand = tag.getStringOr("openCommand", "/ec");
      }

      if (tag.contains("openMode")) {
         try {
            this.openMode = InventoryAuditAction.OpenMode.valueOf(tag.getStringOr("openMode", "COMMAND"));
         } catch (IllegalArgumentException var4) {
            this.openMode = InventoryAuditAction.OpenMode.COMMAND;
         }
      }

      if (tag.contains("containerX") || tag.contains("containerY") || tag.contains("containerZ")) {
         this.containerPos = new BlockPos(tag.getIntOr("containerX", 0), tag.getIntOr("containerY", 0), tag.getIntOr("containerZ", 0));
      }

      if (tag.contains("dupeVector")) {
         try {
            this.dupeVector = InventoryAuditAction.DupeVector.valueOf(tag.getStringOr("dupeVector", "DESYNC_REOPEN"));
         } catch (IllegalArgumentException var3) {
            this.dupeVector = InventoryAuditAction.DupeVector.DESYNC_REOPEN;
         }
      }

      if (tag.contains("delayBeforeReopen")) {
         this.delayBeforeReopen = tag.getIntOr("delayBeforeReopen", 200);
      }

      if (tag.contains("delayAfterReopen")) {
         this.delayAfterReopen = tag.getIntOr("delayAfterReopen", 500);
      }

      if (tag.contains("iterations")) {
         this.iterations = tag.getIntOr("iterations", 1);
      }

      if (tag.contains("maxTransferAttempts")) {
         this.maxTransferAttempts = tag.getIntOr("maxTransferAttempts", 5);
      }

      if (tag.contains("transferRetryDelayMs")) {
         this.transferRetryDelayMs = tag.getIntOr("transferRetryDelayMs", 50);
      }

      if (tag.contains("multipleStacks")) {
         this.multipleStacks = tag.getBooleanOr("multipleStacks", false);
      }

      if (tag.contains("spamCount")) {
         this.spamCount = tag.getIntOr("spamCount", 3);
      }

      if (tag.contains("spamDelayMs")) {
         this.spamDelayMs = tag.getIntOr("spamDelayMs", 50);
      }

      this.delayBeforeReopen = this.clampDelayMs(this.delayBeforeReopen);
      this.delayAfterReopen = this.clampDelayMs(this.delayAfterReopen);
      this.iterations = this.clampIterations(this.iterations);
      this.openCommand = this.normalizedOpenCommand();
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.INVENTORY_AUDIT;
   }

   @Override
   public String getDisplayName() {
      String src = this.openMode == InventoryAuditAction.OpenMode.CONTAINER
         ? "(" + this.containerPos.getX() + "," + this.containerPos.getY() + "," + this.containerPos.getZ() + ")"
         : this.normalizedOpenCommand();

      return switch (this.mode) {
         case DUPE -> "Dupe " + this.dupeVector.name() + " " + src;
         case DUPE_SPAM -> "DupeSpam " + this.dupeVector.name() + " " + src + " (x" + this.spamCount + ")";
      };
   }

   @Override
   public String getIcon() {
      return this.mode == InventoryAuditAction.Mode.DUPE_SPAM ? "DSP" : "DUP";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   private List<ItemTarget> resolvedTargets() {
      if (!this.itemTargets.isEmpty()) {
         return this.itemTargets;
      } else {
         for (String target : this.targetItems) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(target);
            if (parsed.hasSlot() || parsed.hasIdentity()) {
               this.itemTargets.add(parsed);
            }
         }

         return this.itemTargets;
      }
   }

   private List<ItemTarget> runtimeTargets() {
      List<ItemTarget> runtime = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();

      for (ItemTarget target : this.resolvedTargets()) {
         if (target != null) {
            ItemTarget resolved = target.resolveTemplate(mc);
            if (resolved != null && (resolved.hasSlot() || resolved.hasIdentity())) {
               runtime.add(resolved);
            }
         }
      }

      return runtime;
   }

   private void syncLegacyTargetItems() {
      this.targetItems.clear();

      for (ItemTarget target : this.itemTargets) {
         if (target != null) {
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) {
               this.targetItems.add(entry);
            }
         }
      }
   }

   public static enum DupeVector {
      DESYNC_REOPEN,
      CLOSE_NO_PACKET,
      SHIFT_CLICK_REOPEN,
      DELAYED_PACKETS,
      SWAP_HOTBAR,
      DROP_EXPLOIT,
      DELAYED_DESYNC_REOPEN,
      SWAP_DESYNC_REOPEN,
      DROP_DELAYED_PACKETS;
   }

   private record GuiState(boolean open, int syncId, int slotCount, int filledSlots, int totalItems) {
   }

   private record InventorySnapshot(String title, long capturedAtMs, Map<Integer, InventoryAuditAction.SlotSnapshot> slots) {
   }

   public static enum Mode {
      DUPE,
      DUPE_SPAM;
   }

   public static enum OpenMode {
      COMMAND,
      CONTAINER;
   }

   private record SlotSnapshot(int visibleSlot, String label, ItemStack stack) {
   }

   private record TransferAttempt(int slotCount, Map<String, Integer> itemCounts) {
   }
}
