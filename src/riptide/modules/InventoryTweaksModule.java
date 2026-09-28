package riptide.modules;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.RegistryListSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideInventoryClickHelper;
import riptide.util.RiptideInventoryHelper;

public final class InventoryTweaksModule extends Module {
   private static final Minecraft MC = Minecraft.getInstance();
   private InventoryTweaksModule.SortingOperation sortingOperation;
   private InventoryTweaksModule.TimedOperation timedOperation;
   private boolean warnedAutoConflict;
   private final Map<String, InventoryTweaksModule.NormalizedListCache> normalizedListCaches = new HashMap<>();
   private static int lastShowRevision = Integer.MIN_VALUE;
   private static WeakReference<AbstractContainerMenu> lastShowMenu = new WeakReference<>(null);
   private static boolean lastShowResult;
   private static final Comparator<ItemStack> STACK_COMPARATOR = Comparator.<ItemStack, String>comparing(
         stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
      )
      .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
      .thenComparing(Comparator.comparingInt(ItemStack::getDamageValue).reversed());

   InventoryTweaksModule() {
      super("inventory-tweaks", "InventoryTweaks", ModuleCategory.PLAYER, "Inventory sorting, protection, auto-drop, and container steal/dump tools.");
      this.add(new BoolSetting("mouse-drag-item-move", "Shift Drag Move", true).description("Shift-drag transfers hovered stacks.").build());
      this.add(new BoolSetting("uncap-bundle-scrolling", "Uncap Bundle Scroll", true).description("Allow unrestricted bundle scrolling.").build());
      this.add(new BoolSetting("shulker-preview", "Shulker Preview", true).description("Preview container on hover").build());
      this.add(new BoolSetting("sorting-enabled", "Sorting", true).group("Sorting").description("Enable inventory sorting.").build());
      this.add(
         new KeybindSetting("sorting-key", "Sort Key", RiptideBindUtil.encodeMouseButton(2))
            .group("Sorting")
            .visibleWhen(() -> this.bool("sorting-enabled"))
            .description("Sort key")
            .build()
      );
      this.add(
         new IntSetting("sorting-delay", "Sort Delay", 1, 0, 20, 1)
            .group("Sorting")
            .visibleWhen(() -> this.bool("sorting-enabled"))
            .description("Ticks between sorting moves.")
            .build()
      );
      this.add(
         new BoolSetting("disable-in-creative", "Disable In Creative", true)
            .group("Sorting")
            .visibleWhen(() -> this.bool("sorting-enabled"))
            .description("Skip creative screens")
            .build()
      );
      this.add(RegistryListSetting.items("anti-drop-items", "Anti Drop Items", "").group("Anti Drop").description("Undroppable items").build());
      this.add(new BoolSetting("item-frames", "Frames / Pots", true).group("Anti Drop").description("Block frame/pot placing").build());
      this.add(new KeybindSetting("override-bind", "Override Key", -1).group("Anti Drop").description("Hold to bypass anti-drop.").build());
      this.add(RegistryListSetting.items("auto-drop-items", "Auto Drop Items", "").group("Auto Drop").description("Items dropped automatically.").build());
      this.add(new BoolSetting("exclude-equipped", "Exclude Equipped", true).group("Auto Drop").description("Keep armor/offhand items.").build());
      this.add(new BoolSetting("exclude-hotbar", "Exclude Hotbar", false).group("Auto Drop").description("Keep hotbar stacks").build());
      this.add(new BoolSetting("only-full-stacks", "Only Full Stacks", false).group("Auto Drop").description("Only drop complete stacks.").build());
      this.add(
         new StringListSetting("steal-screens", "Steal Screens", "minecraft:generic_9x3|minecraft:generic_9x6")
            .group("Steal / Dump")
            .description("Menu ids")
            .build()
      );
      this.add(new BoolSetting("inventory-buttons", "Inventory Buttons", true).group("Steal / Dump").description("Show steal/dump buttons").build());
      this.add(new BoolSetting("steal-drop", "Steal Drop", false).group("Steal / Dump").description("Drop instead of move").build());
      this.add(
         new BoolSetting("drop-backwards", "Drop Backwards", false)
            .group("Steal / Dump")
            .visibleWhen(() -> this.bool("steal-drop"))
            .description("Turn while dropping")
            .build()
      );
      this.add(
         new ChoiceSetting("dump-filter", "Dump Filter", "None", "None", "Whitelist", "Blacklist")
            .group("Steal / Dump")
            .description("Filter dumped player items.")
            .build()
      );
      this.add(
         RegistryListSetting.items("dump-items", "Dump Items", "")
            .group("Steal / Dump")
            .visibleWhen(() -> !"None".equals(this.value("dump-filter")))
            .description("Dump filter items")
            .build()
      );
      this.add(
         new ChoiceSetting("steal-filter", "Steal Filter", "None", "None", "Whitelist", "Blacklist")
            .group("Steal / Dump")
            .description("Filter stolen container items.")
            .build()
      );
      this.add(
         RegistryListSetting.items("steal-items", "Steal Items", "")
            .group("Steal / Dump")
            .visibleWhen(() -> !"None".equals(this.value("steal-filter")))
            .description("Steal filter items")
            .build()
      );
      this.add(new BoolSetting("auto-steal", "Auto Steal", false).group("Auto Steal").description("Automatically steal supported containers.").build());
      this.add(new BoolSetting("auto-dump", "Auto Dump", false).group("Auto Steal").description("Auto-dump supported containers.").build());
      this.add(new IntSetting("delay-ms", "Delay", 0, 0, 1000, 5).group("Auto Steal").description("Move delay (ms)").build());
      this.add(new IntSetting("initial-delay-ms", "Initial Delay", 0, 0, 1000, 5).group("Auto Steal").description("First-move delay (ms)").build());
      this.add(new IntSetting("random-ms", "Random", 0, 0, 1000, 5).group("Auto Steal").description("Random extra delay").build());
      boolean firstInstall = !RiptideConfig.getGlobal().modules.containsKey("inventory-tweaks");
      if (firstInstall) {
         this.setEnabledSilently(true);
      }
   }

   public static InventoryTweaksModule get() {
      return ModuleRegistry.get("inventory-tweaks") instanceof InventoryTweaksModule tweaks ? tweaks : null;
   }

   public static boolean shouldShowButtons(AbstractContainerMenu handler) {
      int revision = ModuleRegistry.revision();
      if (revision == lastShowRevision && lastShowMenu.get() == handler) {
         return lastShowResult;
      } else {
         InventoryTweaksModule module = get();
         boolean result = module != null && module.isEnabled() && module.bool("inventory-buttons") && module.canSteal(handler);
         lastShowRevision = revision;
         lastShowMenu = new WeakReference<>(handler);
         lastShowResult = result;
         return result;
      }
   }

   public static void stealFromButton() {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled()) {
         module.startTimedOperation(true, false);
      }
   }

   public static void dumpFromButton() {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled()) {
         module.startTimedOperation(false, false);
      }
   }

   public static boolean handleSortMouse(int button, Slot hoveredSlot) {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled() && module.bool("sorting-enabled")) {
         int bind = module.integer("sorting-key");
         return RiptideBindUtil.isMouseBind(bind) && RiptideBindUtil.decodeMouseButton(bind) == button ? module.startSorting(hoveredSlot) : false;
      } else {
         return false;
      }
   }

   public static boolean handleSortKey(int keyCode, Slot hoveredSlot) {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled() && module.bool("sorting-enabled")) {
         int bind = module.integer("sorting-key");
         return !RiptideBindUtil.isMouseBind(bind) && bind == keyCode ? module.startSorting(hoveredSlot) : false;
      } else {
         return false;
      }
   }

   public static boolean shouldShiftDragMove() {
      InventoryTweaksModule module = get();
      return module != null && module.isEnabled() && module.bool("mouse-drag-item-move");
   }

   public static boolean shulkerPreviewEnabled() {
      InventoryTweaksModule module = get();
      return module != null && module.isEnabled() && module.bool("shulker-preview");
   }

   public static boolean hasContainerSyncWork() {
      InventoryTweaksModule module = get();
      return module != null && module.isEnabled() && (module.bool("auto-steal") || module.bool("auto-dump"));
   }

   public static int bundleScrollLimit(ItemStack stack, int vanillaLimit) {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled() && module.bool("uncap-bundle-scrolling")) {
         BundleContents contents = stack == null ? null : (BundleContents)stack.get(DataComponents.BUNDLE_CONTENTS);
         return contents == null ? vanillaLimit : Math.max(vanillaLimit, contents.size());
      } else {
         return vanillaLimit;
      }
   }

   public static void onContainerSynced(int containerId) {
      InventoryTweaksModule module = get();
      if (module != null && module.isEnabled()) {
         module.handleContainerSynced(containerId);
      }
   }

   @Override
   public void onDisable() {
      this.sortingOperation = null;
      this.timedOperation = null;
      this.warnedAutoConflict = false;
      this.normalizedListCaches.clear();
   }

   @Override
   public void tick() {
      this.tickSorting();
      this.tickTimedOperation();
      this.tickAutoDrop();
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      return this.shouldCancelProtectedDrop(packet);
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      if (packet instanceof ClientboundContainerSetContentPacket content) {
         this.handleContainerSynced(content.containerId());
      }

      if (packet instanceof ClientboundContainerSetSlotPacket slot) {
         this.handleContainerSynced(slot.getContainerId());
      }

      return false;
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      if (hitResult == null || hand == null || MC.player == null) {
         return false;
      } else if (this.bool("item-frames") && !this.isOverrideHeld()) {
         ItemStack held = MC.player.getItemInHand(hand);
         if (!this.isInItemList(held, "anti-drop-items")) {
            return false;
         } else if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemFrame) {
            return true;
         } else {
            return hitResult instanceof BlockHitResult blockHit && MC.level != null
               ? MC.level.getBlockState(blockHit.getBlockPos()).is(Blocks.DECORATED_POT)
               : false;
         }
      } else {
         return false;
      }
   }

   private boolean startSorting(Slot focusedSlot) {
      if (focusedSlot == null || MC.player == null || MC.gameMode == null) {
         return false;
      } else if (this.bool("disable-in-creative") && MC.player.hasInfiniteMaterials()) {
         return false;
      } else {
         AbstractContainerMenu handler = MC.player.containerMenu;
         if (handler != null && handler.getCarried().isEmpty()) {
            List<Slot> slots = this.sortableSlotsFor(focusedSlot, handler);
            if (slots.size() < 2) {
               return true;
            } else {
               List<InventoryTweaksModule.SwapAction> actions = this.buildSortActions(slots);
               if (actions.isEmpty()) {
                  RiptideClientMessaging.sendPrefixed("Inventory Tweaks: already sorted.");
                  return true;
               } else {
                  this.sortingOperation = new InventoryTweaksModule.SortingOperation(handler.containerId, actions, Math.max(0, this.integer("sorting-delay")));
                  RiptideClientMessaging.sendPrefixed("Inventory Tweaks: sorting " + slots.size() + " slots.");
                  return true;
               }
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Inventory Tweaks: clear your cursor before sorting.");
            return true;
         }
      }
   }

   private void tickSorting() {
      if (this.sortingOperation != null) {
         if (!this.sortingOperation.tick()) {
            this.sortingOperation = null;
         }
      }
   }

   private List<Slot> sortableSlotsFor(Slot focusedSlot, AbstractContainerMenu handler) {
      boolean focusedPlayer = RiptideInventoryHelper.isInventorySlot(MC, focusedSlot);
      List<Slot> slots = new ArrayList<>();

      for (Slot slot : handler.slots) {
         if (slot != null && slot.mayPickup(MC.player)) {
            int invSlot = inventorySlot(slot);
            if (focusedPlayer) {
               if (invSlot >= 9 && invSlot < 36) {
                  slots.add(slot);
               }
            } else if (invSlot < 0 && slot.container == focusedSlot.container) {
               slots.add(slot);
            }
         }
      }

      return slots;
   }

   private List<InventoryTweaksModule.SwapAction> buildSortActions(List<Slot> slots) {
      List<ItemStack> simulated = new ArrayList<>();

      for (Slot slot : slots) {
         simulated.add(slot.getItem().copy());
      }

      List<ItemStack> desired = new ArrayList<>();

      for (ItemStack stack : simulated) {
         if (!stack.isEmpty()) {
            desired.add(stack.copy());
         }
      }

      desired.sort(STACK_COMPARATOR);

      while (desired.size() < simulated.size()) {
         desired.add(ItemStack.EMPTY);
      }

      List<InventoryTweaksModule.SwapAction> actions = new ArrayList<>();

      for (int i = 0; i < simulated.size(); i++) {
         if (!sameStack(simulated.get(i), desired.get(i))) {
            int match = -1;

            for (int j = i + 1; j < simulated.size(); j++) {
               if (sameStack(simulated.get(j), desired.get(i))) {
                  match = j;
                  break;
               }
            }

            if (match >= 0) {
               actions.add(new InventoryTweaksModule.SwapAction(slots.get(i).index, slots.get(match).index));
               ItemStack tmp = simulated.get(i);
               simulated.set(i, simulated.get(match));
               simulated.set(match, tmp);
            }
         }
      }

      return actions;
   }

   private void handleContainerSynced(int containerId) {
      if (MC.player != null && MC.player.containerMenu != null) {
         AbstractContainerMenu handler = MC.player.containerMenu;
         if (handler.containerId == containerId && this.canSteal(handler) && this.timedOperation == null) {
            if (this.bool("auto-steal") && this.bool("auto-dump") && !this.warnedAutoConflict) {
               this.warnedAutoConflict = true;
               this.setValue("auto-dump", "false");
               RiptideClientMessaging.sendPrefixed("Inventory Tweaks: Auto Dump disabled because Auto Steal is enabled.");
            }

            if (this.bool("auto-steal")) {
               this.startTimedOperation(true, true);
            } else if (this.bool("auto-dump")) {
               this.startTimedOperation(false, true);
            }
         }
      }
   }

   private void startTimedOperation(boolean steal, boolean automatic) {
      if (MC.player != null && MC.gameMode != null) {
         AbstractContainerMenu handler = MC.player.containerMenu;
         if (!this.canSteal(handler)) {
            if (!automatic) {
               RiptideClientMessaging.sendPrefixed("Inventory Tweaks: this menu is not enabled for Steal/Dump.");
            }
         } else {
            List<InventoryTweaksModule.TimedClick> clicks = this.buildTimedClicks(handler, steal);
            if (clicks.isEmpty()) {
               if (!automatic) {
                  RiptideClientMessaging.sendPrefixed("Inventory Tweaks: nothing to " + (steal ? "steal." : "dump."));
               }
            } else {
               this.timedOperation = new InventoryTweaksModule.TimedOperation(
                  handler.containerId,
                  clicks,
                  steal,
                  this.bool("drop-backwards"),
                  Math.max(0, this.integer("delay-ms")),
                  Math.max(0, this.integer("initial-delay-ms")),
                  Math.max(0, this.integer("random-ms"))
               );
               if (!automatic) {
                  RiptideClientMessaging.sendPrefixed("Inventory Tweaks: " + (steal ? "stealing " : "dumping ") + clicks.size() + " stacks.");
               }
            }
         }
      }
   }

   private List<InventoryTweaksModule.TimedClick> buildTimedClicks(AbstractContainerMenu handler, boolean steal) {
      List<InventoryTweaksModule.TimedClick> clicks = new ArrayList<>();

      for (Slot slot : handler.slots) {
         if (slot != null && !slot.getItem().isEmpty()) {
            int invSlot = inventorySlot(slot);
            if (steal) {
               if (invSlot < 0 && this.passesFilter(slot.getItem(), "steal-filter", "steal-items")) {
                  clicks.add(new InventoryTweaksModule.TimedClick(slot.index, this.bool("steal-drop")));
               }
            } else if (invSlot >= 0 && invSlot < 36 && this.passesFilter(slot.getItem(), "dump-filter", "dump-items")) {
               clicks.add(new InventoryTweaksModule.TimedClick(slot.index, false));
            }
         }
      }

      return clicks;
   }

   private void tickTimedOperation() {
      if (this.timedOperation != null) {
         if (!this.timedOperation.tick()) {
            this.timedOperation = null;
         }
      }
   }

   private void tickAutoDrop() {
      if (MC.player != null && MC.gameMode != null && MC.gui.screen() == null) {
         if (!this.list("auto-drop-items").isEmpty()) {
            int start = this.bool("exclude-hotbar") ? 9 : 0;

            for (int slot = start; slot < 36; slot++) {
               ItemStack stack = MC.player.getInventory().getItem(slot);
               if (!stack.isEmpty()
                  && this.isInItemList(stack, "auto-drop-items")
                  && (!this.bool("only-full-stacks") || stack.getCount() >= stack.getMaxStackSize())) {
                  RiptideDropHelper.dropFromInventorySlot(MC, slot, 0);
                  return;
               }
            }
         }
      }
   }

   private boolean canSteal(AbstractContainerMenu handler) {
      if (handler != null && MC.player != null && handler != MC.player.inventoryMenu) {
         Identifier id = this.menuId(handler);
         if (id == null) {
            return false;
         } else {
            Set<String> allowed = this.normalizedList("steal-screens");
            return allowed.contains(id.toString()) || allowed.contains(id.getPath());
         }
      } else {
         return false;
      }
   }

   private Identifier menuId(AbstractContainerMenu handler) {
      try {
         MenuType<?> type = handler.getType();
         return type == null ? null : BuiltInRegistries.MENU.getKey(type);
      } catch (Throwable var3) {
         return null;
      }
   }

   private boolean passesFilter(ItemStack stack, String modeOption, String listOption) {
      String mode = this.choice(modeOption);
      if ("None".equals(mode)) {
         return true;
      } else {
         boolean listed = this.isInItemList(stack, listOption);
         return "Whitelist".equals(mode) ? listed : !listed;
      }
   }

   private boolean isInItemList(ItemStack stack, String optionId) {
      if (stack != null && !stack.isEmpty()) {
         String full = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
         String path = full.startsWith("minecraft:") ? full.substring("minecraft:".length()) : full;
         Set<String> ids = this.normalizedList(optionId);
         return ids.contains(full) || ids.contains(path);
      } else {
         return false;
      }
   }

   private Set<String> normalizedList(String optionId) {
      List<String> raw = this.list(optionId);
      String source = String.join("|", raw);
      InventoryTweaksModule.NormalizedListCache cached = this.normalizedListCaches.get(optionId);
      if (cached != null && cached.source().equals(source)) {
         return cached.values();
      } else {
         Set<String> values = new HashSet<>();

         for (String entry : raw) {
            String id = normalizeId(entry);
            if (!id.isBlank()) {
               values.add(id);
               if (id.startsWith("minecraft:")) {
                  values.add(id.substring("minecraft:".length()));
               } else {
                  values.add("minecraft:" + id);
               }
            }
         }

         Set<String> immutable = Set.copyOf(values);
         this.normalizedListCaches.put(optionId, new InventoryTweaksModule.NormalizedListCache(source, immutable));
         return immutable;
      }
   }

   private boolean isOverrideHeld() {
      int bind = this.integer("override-bind");
      return bind != -1 && RiptideBindUtil.isBindPressed(MC, bind);
   }

   private boolean shouldCancelProtectedDrop(Packet<?> packet) {
      if (MC.player != null && !this.isOverrideHeld() && !this.list("anti-drop-items").isEmpty()) {
         if (packet instanceof ServerboundPlayerActionPacket action && (action.getAction() == Action.DROP_ITEM || action.getAction() == Action.DROP_ALL_ITEMS)) {
            return this.isInItemList(MC.player.getMainHandItem(), "anti-drop-items");
         } else if (packet instanceof ServerboundContainerClickPacket click) {
            ContainerInput input = packetEnum(click, ContainerInput.class);
            if (input != ContainerInput.THROW) {
               return false;
            } else {
               int slotId = packetInt(click, "getSlot", "slot", "slotNum");
               AbstractContainerMenu handler = MC.player.containerMenu;
               return handler != null && slotId >= 0 && slotId < handler.slots.size()
                  ? this.isInItemList(((Slot)handler.slots.get(slotId)).getItem(), "anti-drop-items")
                  : false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static int inventorySlot(Slot slot) {
      if (slot != null && MC.player != null && slot.container == MC.player.getInventory()) {
         int index = slot.getContainerSlot();
         return index >= 0 && index < MC.player.getInventory().getContainerSize() ? index : -1;
      } else {
         return -1;
      }
   }

   private static boolean sameStack(ItemStack a, ItemStack b) {
      if (a != null && !a.isEmpty() || b != null && !b.isEmpty()) {
         return a != null && b != null && !a.isEmpty() && !b.isEmpty()
            ? a.getCount() == b.getCount() && a.getDamageValue() == b.getDamageValue() && ItemStack.isSameItemSameComponents(a, b)
            : false;
      } else {
         return true;
      }
   }

   private static String normalizeId(String raw) {
      return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
   }

   private static <T extends Enum<T>> T packetEnum(Object packet, Class<T> enumClass) {
      for (Method method : packet.getClass().getDeclaredMethods()) {
         if (method.getParameterCount() == 0 && enumClass.isAssignableFrom(method.getReturnType())) {
            try {
               method.setAccessible(true);
               return enumClass.cast(method.invoke(packet));
            } catch (Throwable var8) {
            }
         }
      }

      for (Field field : packet.getClass().getDeclaredFields()) {
         if (enumClass.isAssignableFrom(field.getType())) {
            try {
               field.setAccessible(true);
               return enumClass.cast(field.get(packet));
            } catch (Throwable var7) {
            }
         }
      }

      return null;
   }

   private static int packetInt(Object packet, String... names) {
      for (String name : names) {
         try {
            Method method = packet.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            if (method.invoke(packet) instanceof Number number) {
               return number.intValue();
            }
         } catch (Throwable var10) {
         }

         try {
            Field field = packet.getClass().getDeclaredField(name);
            field.setAccessible(true);
            if (field.get(packet) instanceof Number number) {
               return number.intValue();
            }
         } catch (Throwable var9) {
         }
      }

      return -1;
   }

   private record NormalizedListCache(String source, Set<String> values) {
   }

   private final class SortingOperation {
      private final int containerId;
      private final List<InventoryTweaksModule.SwapAction> actions;
      private final int delayTicks;
      private int index;
      private int delayLeft;

      private SortingOperation(int containerId, List<InventoryTweaksModule.SwapAction> actions, int delayTicks) {
         Objects.requireNonNull(InventoryTweaksModule.this);
         super();
         this.containerId = containerId;
         this.actions = actions;
         this.delayTicks = delayTicks;
      }

      private boolean tick() {
         if (InventoryTweaksModule.MC.player == null
            || InventoryTweaksModule.MC.player.containerMenu == null
            || InventoryTweaksModule.MC.player.containerMenu.containerId != this.containerId) {
            return false;
         } else if (this.delayLeft > 0) {
            this.delayLeft--;
            return true;
         } else if (this.index >= this.actions.size()) {
            return false;
         } else {
            InventoryTweaksModule.SwapAction action = this.actions.get(this.index++);
            RiptideInventoryHelper.swapHandlerSlots(InventoryTweaksModule.MC, action.fromSlot(), action.toSlot());
            this.delayLeft = this.delayTicks;
            return this.index < this.actions.size() || this.delayLeft > 0;
         }
      }
   }

   private record SwapAction(int fromSlot, int toSlot) {
   }

   private record TimedClick(int slot, boolean drop) {
   }

   private final class TimedOperation {
      private final int containerId;
      private final List<InventoryTweaksModule.TimedClick> clicks;
      private final boolean steal;
      private final boolean dropBackwards;
      private final int delayMs;
      private final int randomMs;
      private int index;
      private long nextClickAt;

      private TimedOperation(
         int containerId, List<InventoryTweaksModule.TimedClick> clicks, boolean steal, boolean dropBackwards, int delayMs, int initialDelayMs, int randomMs
      ) {
         Objects.requireNonNull(InventoryTweaksModule.this);
         super();
         this.containerId = containerId;
         this.clicks = clicks;
         this.steal = steal;
         this.dropBackwards = dropBackwards;
         this.delayMs = delayMs;
         this.randomMs = randomMs;
         this.nextClickAt = System.currentTimeMillis() + initialDelayMs;
      }

      private boolean tick() {
         if (InventoryTweaksModule.MC.player == null || InventoryTweaksModule.MC.player.containerMenu == null || InventoryTweaksModule.MC.gameMode == null) {
            return false;
         } else if (InventoryTweaksModule.MC.player.containerMenu.containerId != this.containerId) {
            return false;
         } else if (System.currentTimeMillis() < this.nextClickAt) {
            return true;
         } else {
            while (this.index < this.clicks.size()) {
               InventoryTweaksModule.TimedClick click = this.clicks.get(this.index++);
               if (click.slot() >= 0 && click.slot() < InventoryTweaksModule.MC.player.containerMenu.slots.size()) {
                  Slot slot = (Slot)InventoryTweaksModule.MC.player.containerMenu.slots.get(click.slot());
                  if (slot != null
                     && !slot.getItem().isEmpty()
                     && (!this.steal || InventoryTweaksModule.this.passesFilter(slot.getItem(), "steal-filter", "steal-items"))
                     && (this.steal || InventoryTweaksModule.this.passesFilter(slot.getItem(), "dump-filter", "dump-items"))) {
                     if (click.drop()) {
                        float oldYaw = InventoryTweaksModule.MC.player.getYRot();
                        if (this.dropBackwards) {
                           InventoryTweaksModule.MC.player.setYRot(oldYaw + 180.0F);
                        }

                        try {
                           RiptideInventoryClickHelper.click(InventoryTweaksModule.MC, click.slot(), 1, ContainerInput.THROW);
                        } finally {
                           if (this.dropBackwards) {
                              InventoryTweaksModule.MC.player.setYRot(oldYaw);
                           }
                        }
                     } else {
                        RiptideInventoryClickHelper.click(InventoryTweaksModule.MC, click.slot(), 0, ContainerInput.QUICK_MOVE);
                     }

                     if (this.delayMs > 0 || this.randomMs > 0) {
                        this.nextClickAt = System.currentTimeMillis() + this.delayMs + ThreadLocalRandom.current().nextInt(this.randomMs + 1);
                        return true;
                     }
                  }
               }
            }

            return false;
         }
      }
   }
}
