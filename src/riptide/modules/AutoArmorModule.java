package riptide.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RangeSetting;
import riptide.api.module.ValueRange;
import riptide.util.RiptideConfig;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryClickHelper;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideSharedState;

public final class AutoArmorModule extends Module {
   private static final EquipmentSlot[] ARMOR_ORDER = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final Map<EquipmentSlot, Integer> ARMOR_MENU_SLOT = new EnumMap<>(EquipmentSlot.class);
   private static final float EXPECTED_DAMAGE = 6.0F;
   private static final ResourceKey<Enchantment>[] DR_ENCHANTS = keys(
      Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION
   );
   private static final float[] DR_ENCHANT_FACTOR = new float[]{1.2F, 0.4F, 0.39F, 0.38F};
   private static final float[] DR_ENCHANT_REDUCTION = new float[]{0.04F, 0.08F, 0.15F, 0.08F};
   private static final ResourceKey<Enchantment>[] OTHER_ENCHANTS = keys(
      Enchantments.FEATHER_FALLING, Enchantments.THORNS, Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.UNBREAKING
   );
   private static final float[] OTHER_ENCHANT_PER_LEVEL = new float[]{3.0F, 1.0F, 0.1F, 0.05F, 0.01F};
   private int cooldown;
   private int prevArmorValue = -1;
   private boolean openedInventory;
   private static final int IDLE_SCAN_INTERVAL_TICKS = 4;
   private int idleScanLastTick = -4;
   private int idleScanInventoryStamp = -1;
   private final Map<String, String> bandRaws = new HashMap<>();
   private final Map<String, ValueRange> bandParsed = new HashMap<>();
   private boolean sessionLive;
   private boolean sessionClosing;
   private int clicksThisSession;
   private int nextStepTick;
   private int nextSessionTick;
   private int reactionHoldUntilTick = -1;
   private static volatile int operationActiveUntilTick = Integer.MIN_VALUE;
   private static volatile int pauseMovementUntilTick = Integer.MIN_VALUE;
   private static final Map<ResourceKey<Enchantment>, Holder<Enchantment>> ENCHANT_HOLDERS = new HashMap<>();
   private static Level enchantHolderLevel;

   private ValueRange band(String settingId, int fallbackMin, int fallbackMax) {
      String raw = this.value(settingId);
      String previous = this.bandRaws.put(settingId, raw);
      if (previous == null || !previous.equals(raw)) {
         this.bandParsed.put(settingId, ValueRange.parse(raw, new ValueRange(fallbackMin, fallbackMax)).clamp(0.0, 10.0));
      }

      return this.bandParsed.get(settingId);
   }

   AutoArmorModule() {
      super("auto-armor", "AutoArmor", ModuleCategory.COMBAT, "Equips your best armor.");
      this.add(new BoolSetting("prefer-elytra", "Prefer Elytra", false).description("Wear elytra over chestplate.").build());
      this.add(new BoolSetting("allow-cursed", "Allow Cursed", false).description("Equip curse-of-binding armor.").build());
      this.add(
         new RangeSetting("click-delay", "Click Delay", new ValueRange(3, 5), 0.0, 10.0, 1.0).group("Timing").unit("ticks").description("Ticks between moves")
      );
      this.add(
         new RangeSetting("close-delay", "Close Delay", new ValueRange(3, 5), 0.0, 10.0, 1.0)
            .group("Timing")
            .unit("ticks")
            .description("Ticks before session close")
      );
      this.add(
         new RangeSetting("operation-delay", "Spacing", new ValueRange(3, 5), 0.0, 10.0, 1.0)
            .group("Timing")
            .unit("ticks")
            .description("Ticks between sessions")
      );
      this.add(
         new RangeSetting("reaction", "Reaction", new ValueRange(3, 7), 0.0, 10.0, 1.0).group("Timing").unit("ticks").description("Ticks before first move")
      );
      this.add(new BoolSetting("no-movement", "No Movement", true).group("Constraints").description("Only swap while still.").build());
      this.add(new BoolSetting("not-using-item", "Not Using Item", true).group("Constraints").description("Not while using item.").build());
      this.add(new BoolSetting("hotbar", "Hotbar", true).group("Hotbar").description("Equip via hotbar swap.").build());
      this.add(
         new BoolSetting("can-swap-armor", "Can Swap Armor", false)
            .group("Hotbar")
            .visibleWhen(() -> this.bool("hotbar"))
            .description("Direct armor swap.")
            .build()
      );
      this.add(new BoolSetting("save-armor", "Save Armor", false).group("Save Armor").description("Swap before armor breaks.").build());
      this.add(
         new IntSetting("durability-threshold", "Durability Threshold", 24, 0, 100, 1)
            .group("Save Armor")
            .visibleWhen(() -> this.bool("save-armor"))
            .description("Save below this durability.")
            .build()
      );
      this.add(
         new BoolSetting("auto-open-inventory", "Auto Open Inventory", true)
            .group("Save Armor")
            .visibleWhen(() -> this.bool("save-armor"))
            .description("Open inventory to save.")
            .build()
      );
      this.add(new BoolSetting("pause-movement", "Pause Movement", true));
      boolean firstInstall = !RiptideConfig.getGlobal().modules.containsKey("auto-armor");
      if (firstInstall) {
         this.setEnabledSilently(true);
      }
   }

   @Override
   public void onDisable() {
      this.reset();
      this.closeOpenedInventory();
   }

   @Override
   public void onGameLeft() {
      this.reset();
      this.openedInventory = false;
   }

   private void reset() {
      if (this.sessionLive && this.clicksThisSession > 0) {
         this.sendSessionClose();
      }

      this.cooldown = 0;
      this.prevArmorValue = -1;
      this.sessionLive = false;
      this.sessionClosing = false;
      this.clicksThisSession = 0;
      this.nextStepTick = 0;
      this.nextSessionTick = 0;
      this.reactionHoldUntilTick = -1;
      operationActiveUntilTick = Integer.MIN_VALUE;
   }

   @Override
   public void preMovementTick() {
      if (MC != null && MC.player != null && MC.level != null) {
         if (!PackHideState.isHardLocked() && !MC.player.isSpectator()) {
            this.trackArmorBreak();
            if (this.sessionLive) {
               if (AutoTotemModule.operationActive()) {
                  this.endSession(true);
               } else {
                  this.tickSession();
               }
            } else if (!AutoTotemModule.operationActive()) {
               if (!this.timed()) {
                  this.legacyTick();
               } else if (MC.player.containerMenu != MC.player.inventoryMenu) {
                  this.handleForeignScreen();
               } else if (!this.bool("not-using-item") || !MC.player.isUsingItem()) {
                  if (this.bool("no-movement") && isMoving()) {
                     if (this.shouldOpenInventoryToSave()) {
                        MC.gui.setScreen(new InventoryScreen(MC.player));
                        this.openedInventory = true;
                     }
                  } else if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
                     int now = RiptideSharedState.get().getClientTickCounter();
                     if (now >= this.nextSessionTick) {
                        int timesChanged = MC.player.getInventory().getTimesChanged();
                        if (timesChanged != this.idleScanInventoryStamp || now - this.idleScanLastTick >= 4) {
                           this.idleScanInventoryStamp = timesChanged;
                           this.idleScanLastTick = now;
                           if (this.findNextMove() == null) {
                              if (this.openedInventory) {
                                 this.closeOpenedInventory();
                              }

                              this.reactionHoldUntilTick = -1;
                           } else {
                              if (this.reactionHoldUntilTick < 0) {
                                 this.reactionHoldUntilTick = now + this.drawTicks("reaction", 3, 7);
                              }

                              if (now >= this.reactionHoldUntilTick) {
                                 this.reactionHoldUntilTick = -1;
                                 this.sessionLive = true;
                                 this.sessionClosing = false;
                                 this.clicksThisSession = 0;
                                 this.tickSession();
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void legacyTick() {
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (MC.player.containerMenu != MC.player.inventoryMenu) {
         this.handleForeignScreen();
      } else if (!this.bool("not-using-item") || !MC.player.isUsingItem()) {
         if (this.bool("no-movement") && isMoving()) {
            if (this.shouldOpenInventoryToSave()) {
               MC.gui.setScreen(new InventoryScreen(MC.player));
               this.openedInventory = true;
            }
         } else if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
            this.legacyRunOnce();
         }
      }
   }

   private void tickSession() {
      int now = RiptideSharedState.get().getClientTickCounter();
      operationActiveUntilTick = now + this.windowTail();
      if (now >= this.nextStepTick) {
         if (this.isEnabled()
            && !PackHideState.isHardLocked()
            && MC.player.containerMenu == MC.player.inventoryMenu
            && !RiptideBlinkManager.holdsActionsWithoutMovement()
            && (!this.bool("not-using-item") || !MC.player.isUsingItem())) {
            if (this.sessionClosing) {
               AutoArmorModule.Move rest = this.findNextMove();
               if (rest == null) {
                  if (this.clicksThisSession > 0) {
                     this.sendSessionClose();
                  }

                  this.endSession(false);
                  return;
               }

               this.sessionClosing = false;
            }

            AutoArmorModule.Move move = this.findNextMove();
            if (move == null) {
               this.sessionClosing = true;
               this.nextStepTick = now + this.drawTicks("close-delay", 3, 5);
            } else if (!this.equip(move.slot(), move.candidate(), move.worn())) {
               this.nextStepTick = now + 1;
            } else {
               this.clicksThisSession++;
               if (this.findNextMove() != null) {
                  this.nextStepTick = now + this.drawTicks("click-delay", 3, 5);
               } else {
                  this.sessionClosing = true;
                  this.nextStepTick = now + this.drawTicks("close-delay", 3, 5);
               }
            }
         } else {
            this.endSession(true);
         }
      }
   }

   private void endSession(boolean aborted) {
      if (aborted && this.clicksThisSession > 0) {
         this.sendSessionClose();
      }

      this.sessionLive = false;
      this.sessionClosing = false;
      this.clicksThisSession = 0;
      this.nextSessionTick = RiptideSharedState.get().getClientTickCounter() + this.drawTicks("operation-delay", 3, 5);
   }

   private AutoArmorModule.Move findNextMove() {
      int threshold = this.bool("save-armor") ? this.integer("durability-threshold") : Integer.MIN_VALUE;
      Map<EquipmentSlot, AutoArmorModule.Candidate> best = this.findBestArmor(threshold);
      if (this.bool("prefer-elytra")) {
         AutoArmorModule.Candidate elytra = this.preferredElytra();
         if (elytra != null) {
            best.put(EquipmentSlot.CHEST, elytra);
         }
      }

      for (EquipmentSlot slot : ARMOR_ORDER) {
         AutoArmorModule.Candidate candidate = best.get(slot);
         if (candidate != null
            && candidate.kind != AutoArmorModule.Kind.ARMOR
            && (candidate.invIndex < 0 || !RiptideHandArbiter.slotReserved(candidate.invIndex, this.id()))) {
            ItemStack worn = MC.player.getItemBySlot(slot);
            if (worn.isEmpty() || !worn.is(Items.ELYTRA) && !hasCurseOfBinding(worn)) {
               return new AutoArmorModule.Move(slot, candidate, worn);
            }
         }
      }

      return null;
   }

   private boolean shouldOpenInventoryToSave() {
      return this.bool("save-armor") && this.bool("auto-open-inventory") && MC.gui.screen() == null && !isInvMoveActive() && this.hasLowArmorWithReplacement();
   }

   private static boolean isInvMoveActive() {
      Module module = ModuleRegistry.get("inv-move");
      return module != null && module.isEnabled();
   }

   private void legacyRunOnce() {
      AutoArmorModule.Move move = this.findNextMove();
      if (move != null && this.equip(move.slot(), move.candidate(), move.worn())) {
         this.cooldown = 1;
      } else {
         if (this.openedInventory) {
            this.closeOpenedInventory();
         }

         this.cooldown = 1;
      }
   }

   private boolean equip(EquipmentSlot slot, AutoArmorModule.Candidate candidate, ItemStack worn) {
      boolean occupied = !worn.isEmpty();
      int armorMenuSlot = ARMOR_MENU_SLOT.get(slot);
      boolean hotbarFast = candidate.kind == AutoArmorModule.Kind.HOTBAR && this.bool("hotbar");
      if (!occupied) {
         return hotbarFast
            ? this.click(armorMenuSlot, candidate.invIndex, ContainerInput.SWAP)
            : this.click(this.sourceMenuSlot(candidate), 0, ContainerInput.QUICK_MOVE);
      } else if (hotbarFast && this.bool("can-swap-armor")) {
         return this.click(armorMenuSlot, candidate.invIndex, ContainerInput.SWAP);
      } else {
         return MC.player.getInventory().getFreeSlot() >= 0
            ? this.click(armorMenuSlot, 0, ContainerInput.QUICK_MOVE)
            : this.click(armorMenuSlot, 1, ContainerInput.THROW);
      }
   }

   private boolean click(int menuSlot, int button, ContainerInput input) {
      if (menuSlot < 0) {
         return false;
      } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         return false;
      } else {
         boolean var5;
         try {
            boolean clicked = RiptideInventoryClickHelper.click(MC, menuSlot, button, input);
            if (clicked && this.bool("pause-movement")) {
               pauseMovementUntilTick = RiptideSharedState.get().getClientTickCounter() + 1;
            }

            var5 = clicked;
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }

         return var5;
      }
   }

   private int drawTicks(String settingId, int fallbackMin, int fallbackMax) {
      return (int)Math.round(this.band(settingId, fallbackMin, fallbackMax).random(ThreadLocalRandom.current()));
   }

   private double bandMax(String settingId, int fallbackMax) {
      return this.band(settingId, 0, fallbackMax).max();
   }

   private boolean timed() {
      return this.bandMax("click-delay", 5) > 0.0
         || this.bandMax("close-delay", 5) > 0.0
         || this.bandMax("operation-delay", 5) > 0.0
         || this.bandMax("reaction", 7) > 0.0;
   }

   private int windowTail() {
      return Math.max(1, this.drawTicks("click-delay", 3, 5));
   }

   private void sendSessionClose() {
      if (MC.getConnection() != null && MC.gui.screen() == null) {
         if (MC.player.containerMenu == MC.player.inventoryMenu) {
            MC.getConnection().send(new ServerboundContainerClosePacket(MC.player.containerMenu.containerId));
         }
      }
   }

   public static boolean operationActive() {
      int until = operationActiveUntilTick;
      if (until != Integer.MIN_VALUE && MC != null && MC.player != null) {
         Module module = ModuleRegistry.get("auto-armor");
         return module != null && module.isEnabled() ? RiptideSharedState.get().getClientTickCounter() <= until : false;
      } else {
         return false;
      }
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return operationActive();
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return operationActive();
   }

   public static boolean movementInputPaused() {
      if (operationActive()) {
         return true;
      } else {
         int until = pauseMovementUntilTick;
         if (until == Integer.MIN_VALUE) {
            return false;
         } else {
            int age = RiptideSharedState.get().getClientTickCounter() - until;
            return age <= 0 && age > -2;
         }
      }
   }

   public static Input modifyMovementInput(ClientInput source, Input original) {
      if (original == null || MC == null || MC.player == null || MC.player.input != source) {
         return original;
      } else if (operationActive()) {
         return new Input(false, false, false, false, false, false, false);
      } else {
         return RiptideSharedState.get().getClientTickCounter() > pauseMovementUntilTick
            ? original
            : new Input(false, false, false, false, original.jump(), original.shift(), false);
      }
   }

   private int sourceMenuSlot(AutoArmorModule.Candidate candidate) {
      return RiptideInventoryHelper.toHandlerSlot(MC, candidate.invIndex);
   }

   private AutoArmorModule.Candidate preferredElytra() {
      ItemStack worn = MC.player.getItemBySlot(EquipmentSlot.CHEST);
      if (worn.is(Items.ELYTRA)) {
         return new AutoArmorModule.Candidate(worn, EquipmentSlot.CHEST, AutoArmorModule.Kind.ARMOR, -1);
      } else {
         for (int inv = 0; inv < 36; inv++) {
            ItemStack stack = MC.player.getInventory().getItem(inv);
            if (stack.is(Items.ELYTRA)) {
               return new AutoArmorModule.Candidate(stack, EquipmentSlot.CHEST, inv < 9 ? AutoArmorModule.Kind.HOTBAR : AutoArmorModule.Kind.INVENTORY, inv);
            }
         }

         if (!RiptideHandArbiter.offhandClaimedByOther(this.id())) {
            ItemStack offhand = MC.player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.is(Items.ELYTRA)) {
               return new AutoArmorModule.Candidate(offhand, EquipmentSlot.CHEST, AutoArmorModule.Kind.INVENTORY, 40);
            }
         }

         return null;
      }
   }

   private void trackArmorBreak() {
      if (!this.bool("save-armor")) {
         this.prevArmorValue = -1;
      } else {
         int current = MC.player.getArmorValue();
         boolean handledScreen = MC.gui.screen() instanceof AbstractContainerScreen && !(MC.gui.screen() instanceof InventoryScreen);
         if (handledScreen && this.prevArmorValue >= 0 && current < this.prevArmorValue && this.bool("auto-open-inventory")) {
            Screen screen = MC.gui.screen();
            if (screen != null) {
               screen.onClose();
            }
         }

         this.prevArmorValue = current;
      }
   }

   private void handleForeignScreen() {
      if (this.bool("save-armor") && this.bool("auto-open-inventory")) {
         if (this.hasLowArmorWithReplacement()) {
            Screen screen = MC.gui.screen();
            if (screen instanceof AbstractContainerScreen && !(screen instanceof InventoryScreen)) {
               screen.onClose();
               this.cooldown = Math.max(1, this.drawTicks("click-delay", 3, 5));
            }
         }
      }
   }

   private boolean hasLowArmorWithReplacement() {
      int threshold = this.integer("durability-threshold");

      for (EquipmentSlot slot : ARMOR_ORDER) {
         ItemStack worn = MC.player.getItemBySlot(slot);
         if (!worn.isEmpty() && isPlayerArmor(worn) && durability(worn) <= threshold && !hasCurseOfBinding(worn)) {
            for (int inv = 0; inv < 36; inv++) {
               ItemStack stack = MC.player.getInventory().getItem(inv);
               if (!stack.isEmpty()
                  && isPlayerArmor(stack)
                  && (this.bool("allow-cursed") || !hasCurseOfBinding(stack))
                  && slot.equals(equipmentSlotOf(stack))
                  && durability(stack) > threshold) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void closeOpenedInventory() {
      if (this.openedInventory) {
         this.openedInventory = false;
         if (MC != null && MC.gui.screen() instanceof InventoryScreen screen) {
            screen.onClose();
         }
      }
   }

   private Map<EquipmentSlot, AutoArmorModule.Candidate> findBestArmor(int threshold) {
      Map<EquipmentSlot, List<AutoArmorModule.Candidate>> byType = this.gatherCandidates();
      Map<EquipmentSlot, AutoArmorModule.Candidate> current = new EnumMap<>(EquipmentSlot.class);

      for (Entry<EquipmentSlot, List<AutoArmorModule.Candidate>> entry : byType.entrySet()) {
         current.put(entry.getKey(), maxBy(entry.getValue(), Comparator.comparingDouble(c -> armorToughness(c.stack, entry.getKey()))));
      }

      for (int pass = 0; pass < 2; pass++) {
         Map<EquipmentSlot, AutoArmorModule.KitParam> kit = kitParamsExcludingSelf(current);
         Comparator<AutoArmorModule.Candidate> comparator = armorComparator(kit, threshold);
         Map<EquipmentSlot, AutoArmorModule.Candidate> next = new EnumMap<>(EquipmentSlot.class);

         for (Entry<EquipmentSlot, List<AutoArmorModule.Candidate>> entry : byType.entrySet()) {
            next.put(entry.getKey(), maxBy(entry.getValue(), comparator));
         }

         current = next;
      }

      return current;
   }

   private Map<EquipmentSlot, List<AutoArmorModule.Candidate>> gatherCandidates() {
      Map<EquipmentSlot, List<AutoArmorModule.Candidate>> byType = new EnumMap<>(EquipmentSlot.class);
      this.addCandidateRange(byType, 0, 9, AutoArmorModule.Kind.HOTBAR);
      this.addCandidateRange(byType, 9, 36, AutoArmorModule.Kind.INVENTORY);
      if (!RiptideHandArbiter.offhandClaimedByOther(this.id())) {
         this.addCandidate(byType, MC.player.getItemBySlot(EquipmentSlot.OFFHAND), 40, AutoArmorModule.Kind.INVENTORY);
      }

      for (EquipmentSlot slot : ARMOR_ORDER) {
         this.addCandidate(byType, MC.player.getItemBySlot(slot), -1, AutoArmorModule.Kind.ARMOR);
      }

      return byType;
   }

   private void addCandidateRange(Map<EquipmentSlot, List<AutoArmorModule.Candidate>> byType, int from, int to, AutoArmorModule.Kind kind) {
      for (int inv = from; inv < to; inv++) {
         this.addCandidate(byType, MC.player.getInventory().getItem(inv), inv, kind);
      }
   }

   private void addCandidate(Map<EquipmentSlot, List<AutoArmorModule.Candidate>> byType, ItemStack stack, int invIndex, AutoArmorModule.Kind kind) {
      if (stack != null && !stack.isEmpty() && isPlayerArmor(stack)) {
         if (kind == AutoArmorModule.Kind.ARMOR || this.bool("allow-cursed") || !hasCurseOfBinding(stack)) {
            EquipmentSlot slot = equipmentSlotOf(stack);
            if (slot != null && ARMOR_MENU_SLOT.get(slot) != null) {
               byType.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(new AutoArmorModule.Candidate(stack, slot, kind, invIndex));
            }
         }
      }
   }

   private static Map<EquipmentSlot, AutoArmorModule.KitParam> kitParamsExcludingSelf(Map<EquipmentSlot, AutoArmorModule.Candidate> current) {
      double totalDefense = 0.0;
      double totalToughness = 0.0;

      for (Entry<EquipmentSlot, AutoArmorModule.Candidate> entry : current.entrySet()) {
         AutoArmorModule.Candidate candidate = entry.getValue();
         if (candidate != null) {
            totalDefense += armorValue(candidate.stack, entry.getKey());
            totalToughness += armorToughness(candidate.stack, entry.getKey());
         }
      }

      Map<EquipmentSlot, AutoArmorModule.KitParam> kit = new EnumMap<>(EquipmentSlot.class);

      for (Entry<EquipmentSlot, AutoArmorModule.Candidate> entryx : current.entrySet()) {
         AutoArmorModule.Candidate candidate = entryx.getValue();
         double defense = candidate == null ? 0.0 : armorValue(candidate.stack, entryx.getKey());
         double toughness = candidate == null ? 0.0 : armorToughness(candidate.stack, entryx.getKey());
         kit.put(entryx.getKey(), new AutoArmorModule.KitParam(totalDefense - defense, totalToughness - toughness));
      }

      return kit;
   }

   private static Comparator<AutoArmorModule.Candidate> armorComparator(Map<EquipmentSlot, AutoArmorModule.KitParam> kit, int threshold) {
      Comparator<AutoArmorModule.Candidate> byReduction = Comparator.comparingDouble(c -> round3(thresholdedDamageReduction(c, kit)));
      return Comparator.<AutoArmorModule.Candidate, Boolean>comparing(c -> durability(c.stack) > threshold)
         .thenComparing(byReduction.reversed())
         .thenComparingDouble(c -> round3(enchantmentScore(c.stack)))
         .thenComparingInt(c -> enchantmentCount(c.stack))
         .thenComparingInt(c -> enchantability(c.stack))
         .thenComparing(c -> c.kind == AutoArmorModule.Kind.ARMOR)
         .thenComparing(c -> c.kind == AutoArmorModule.Kind.HOTBAR);
   }

   private static double thresholdedDamageReduction(AutoArmorModule.Candidate candidate, Map<EquipmentSlot, AutoArmorModule.KitParam> kit) {
      AutoArmorModule.KitParam param = kit.getOrDefault(candidate.slot, AutoArmorModule.KitParam.ZERO);
      double defense = param.defense + armorValue(candidate.stack, candidate.slot);
      double toughness = param.toughness + armorToughness(candidate.stack, candidate.slot);
      return damageFactor(6.0, defense, toughness) * (1.0 - enchantmentDamageReduction(candidate.stack));
   }

   private static double damageFactor(double damage, double defense, double toughness) {
      double f = 2.0 + toughness / 4.0;
      double g = clamp(defense - damage / f, defense * 0.2, 20.0);
      return 1.0 - g / 25.0;
   }

   private static double enchantmentDamageReduction(ItemStack stack) {
      double sum = 0.0;

      for (int i = 0; i < DR_ENCHANTS.length; i++) {
         sum += enchantLevel(stack, DR_ENCHANTS[i]) * DR_ENCHANT_FACTOR[i] * DR_ENCHANT_REDUCTION[i];
      }

      return sum;
   }

   private static double enchantmentScore(ItemStack stack) {
      double sum = 0.0;

      for (int i = 0; i < OTHER_ENCHANTS.length; i++) {
         sum += enchantLevel(stack, OTHER_ENCHANTS[i]) * OTHER_ENCHANT_PER_LEVEL[i];
      }

      return sum;
   }

   private static boolean isPlayerArmor(ItemStack stack) {
      Holder<Item> holder = stack.typeHolder();
      return holder.is(ItemTags.HEAD_ARMOR) || holder.is(ItemTags.CHEST_ARMOR) || holder.is(ItemTags.LEG_ARMOR) || holder.is(ItemTags.FOOT_ARMOR);
   }

   private static EquipmentSlot equipmentSlotOf(ItemStack stack) {
      Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
      return equippable == null ? null : equippable.slot();
   }

   private static double armorValue(ItemStack stack, EquipmentSlot slot) {
      return attributeValue(stack, Attributes.ARMOR, slot);
   }

   private static double armorToughness(ItemStack stack, EquipmentSlot slot) {
      return attributeValue(stack, Attributes.ARMOR_TOUGHNESS, slot);
   }

   private static double attributeValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
      Attribute value = (Attribute)attribute.value();
      double base = value.getDefaultValue();
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
      return modifiers == null ? value.sanitizeValue(base) : value.sanitizeValue(modifiers.compute(attribute, base, slot));
   }

   private static int durability(ItemStack stack) {
      return stack.getMaxDamage() - stack.getDamageValue();
   }

   private static int enchantmentCount(ItemStack stack) {
      ItemEnchantments enchantments = (ItemEnchantments)stack.get(DataComponents.ENCHANTMENTS);
      return enchantments == null ? 0 : enchantments.size();
   }

   private static int enchantability(ItemStack stack) {
      Enchantable enchantable = (Enchantable)stack.get(DataComponents.ENCHANTABLE);
      return enchantable == null ? 0 : enchantable.value();
   }

   private static boolean hasCurseOfBinding(ItemStack stack) {
      return enchantLevel(stack, Enchantments.BINDING_CURSE) > 0;
   }

   private static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
      try {
         if (enchantHolderLevel != MC.level) {
            enchantHolderLevel = MC.level;
            ENCHANT_HOLDERS.clear();
         }

         Holder<Enchantment> holder = ENCHANT_HOLDERS.computeIfAbsent(key, k -> MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(k));
         return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
      } catch (Throwable var3) {
         return 0;
      }
   }

   private static boolean isMoving() {
      Input input = MC.player.input.keyPresses;
      return input.forward() || input.backward() || input.left() || input.right() || input.jump();
   }

   private static AutoArmorModule.Candidate maxBy(List<AutoArmorModule.Candidate> candidates, Comparator<AutoArmorModule.Candidate> comparator) {
      AutoArmorModule.Candidate best = null;

      for (AutoArmorModule.Candidate candidate : candidates) {
         if (best == null || comparator.compare(candidate, best) > 0) {
            best = candidate;
         }
      }

      return best;
   }

   private static double round3(double value) {
      return Math.round(value * 1000.0) / 1000.0;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   @SafeVarargs
   private static ResourceKey<Enchantment>[] keys(ResourceKey<Enchantment>... keys) {
      return keys;
   }

   static {
      ARMOR_MENU_SLOT.put(EquipmentSlot.HEAD, 5);
      ARMOR_MENU_SLOT.put(EquipmentSlot.CHEST, 6);
      ARMOR_MENU_SLOT.put(EquipmentSlot.LEGS, 7);
      ARMOR_MENU_SLOT.put(EquipmentSlot.FEET, 8);
   }

   private record Candidate(ItemStack stack, EquipmentSlot slot, AutoArmorModule.Kind kind, int invIndex) {
   }

   private static enum Kind {
      HOTBAR,
      INVENTORY,
      ARMOR;
   }

   private record KitParam(double defense, double toughness) {
      private static final AutoArmorModule.KitParam ZERO = new AutoArmorModule.KitParam(0.0, 0.0);
   }

   private record Move(EquipmentSlot slot, AutoArmorModule.Candidate candidate, ItemStack worn) {
   }
}
