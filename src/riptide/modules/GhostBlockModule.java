package riptide.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideFakeGamemode;
import riptide.util.multi.MultiPilot;

public final class GhostBlockModule extends Module {
   static final String MODULE_DESCRIPTION = "Places blocks that only exist for you. Creative mode fakes a creative gamemode so anything you place becomes a ghost; right-click places, left-click removes, sneak keeps it real.";
   static final String MODE_TIP = "Creative fakes your gamemode";
   static final String DELAY_TIP = "Ticks between placements";
   static final String BREAK_TIP = "Click ghosts to remove";
   static final String SNEAK_TIP = "Sneak places real blocks";
   static final String CLEAR_DISABLE_TIP = "Clear ghosts on disable";
   static final String CLEAR_KEY_TIP = "Key clears all ghosts";
   static final String HIGHLIGHT_TIP = "Outline ghost blocks";
   static final String HIGHLIGHT_FILL_TIP = "Translucent ghost fill";
   static final String HIGHLIGHT_COLOR_TIP = "Ghost highlight color";
   private static final int MAX_GHOSTS = 4096;
   private static final int BLOCK_BREAK_EFFECT = 2001;
   private static final int GAMEMODE_SYNC_INTERVAL_TICKS = 20;
   private static final int DEFAULT_HIGHLIGHT_COLOR = -6381922;
   private final Map<BlockPos, BlockState> ghosts = new LinkedHashMap<>();
   private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
   private Block armedBlock;
   private ClientLevel lastLevel;
   private boolean clearKeyWasDown;
   private boolean fakeGamemodeApplied;
   private int gamemodeSyncTicks;
   private volatile List<AABB> cachedHighlightBoxes = List.of();
   private volatile boolean highlightBoxesDirty;

   GhostBlockModule() {
      super(
         "ghostblock",
         "GhostBlock",
         ModuleCategory.PLAYER,
         "Places blocks that only exist for you. Creative mode fakes a creative gamemode so anything you place becomes a ghost; right-click places, left-click removes, sneak keeps it real."
      );
      this.add(new ChoiceSetting("mode", "Mode", "Creative", "Creative", "Survival").description("Creative fakes your gamemode").build());
      this.add(new IntSetting("place-delay", "Place Delay", 4, 1, 20, 1).description("Ticks between placements").build());
      this.add(new BoolSetting("break-ghosts", "Click Removes", true).description("Click ghosts to remove").build());
      this.add(new BoolSetting("sneak-bypass", "Sneak Bypass", true).description("Sneak places real blocks").build());
      this.add(new BoolSetting("clear-on-disable", "Clear On Disable", false).description("Clear ghosts on disable").build());
      this.add(new KeybindSetting("clear-key", "Clear Key", -1).description("Key clears all ghosts").build());
      this.add(new BoolSetting("highlight", "Highlight", true).description("Outline ghost blocks").build());
      this.add(new BoolSetting("highlight-fill", "Highlight Fill", true).description("Translucent ghost fill").build());
      this.add(new ColorSetting("highlight-color", "Highlight Color", -6381922).description("Ghost highlight color").build());
   }

   @Override
   public void onEnable() {
      this.syncFakeGamemode();
   }

   @Override
   public void onDisable() {
      if (this.bool("clear-on-disable")) {
         this.restoreAll();
      }

      this.releaseFakeGamemode();
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return this.ghostCount() > 0;
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if ("mode".equals(settingId)) {
         this.syncFakeGamemode();
      }
   }

   private void syncFakeGamemode() {
      if (this.isEnabled() && MC != null && MC.player != null && MC.gameMode != null) {
         if (!this.isCreative()) {
            this.releaseFakeGamemode();
         } else if (!this.fakeGamemodeApplied || RiptideFakeGamemode.snapshot().displayedMode() != GameType.CREATIVE) {
            this.fakeGamemodeApplied = RiptideFakeGamemode.apply(GameType.CREATIVE).success();
         }
      }
   }

   private void releaseFakeGamemode() {
      if (this.fakeGamemodeApplied) {
         this.fakeGamemodeApplied = false;
         RiptideFakeGamemode.reset();
      }
   }

   @Override
   public void onGameJoin() {
      this.syncFakeGamemode();
   }

   @Override
   public void onGameLeft() {
      this.clearGhosts();
      this.releaseFakeGamemode();
      this.lastLevel = null;
      this.clearKeyWasDown = false;
   }

   @Override
   public void tick() {
      if (MC != null && MC.level != null && MC.player != null) {
         if (MC.level != this.lastLevel) {
            this.clearGhosts();
            this.lastLevel = MC.level;
         }

         this.handleClearKey();
         if (!this.isEnabled()) {
            this.pruneLostGhosts();
         } else {
            this.sweepGhosts();
            if (++this.gamemodeSyncTicks >= 20) {
               this.gamemodeSyncTicks = 0;
               this.syncFakeGamemode();
            }
         }
      }
   }

   private void handleClearKey() {
      int bind = this.clearKeyCode();
      boolean down = bind != -1 && RiptideBindUtil.isBindPressed(MC, bind);
      if (down && !this.clearKeyWasDown && MC.gui.screen() == null) {
         int removed = this.restoreAll();
         if (removed > 0) {
            RiptideClientMessaging.sendPrefixed("§aCleared §f" + removed + "§a ghost block" + (removed == 1 ? "" : "s") + ".");
         }
      }

      this.clearKeyWasDown = down;
   }

   private int clearKeyCode() {
      try {
         return Integer.parseInt(this.value("clear-key"));
      } catch (NumberFormatException var2) {
         return -1;
      }
   }

   private void sweepGhosts() {
      List<Entry<BlockPos, BlockState>> snapshot;
      synchronized (this.ghosts) {
         if (this.ghosts.isEmpty()) {
            return;
         }

         snapshot = new ArrayList<>(this.ghosts.entrySet());
      }

      for (Entry<BlockPos, BlockState> entry : snapshot) {
         BlockPos pos = entry.getKey();
         if (this.hasChunk(pos) && MC.level.getBlockState(pos) != entry.getValue()) {
            MC.level.setBlock(pos, entry.getValue(), 3);
         }
      }
   }

   private void pruneLostGhosts() {
      List<Entry<BlockPos, BlockState>> snapshot;
      synchronized (this.ghosts) {
         if (this.ghosts.isEmpty()) {
            return;
         }

         snapshot = new ArrayList<>(this.ghosts.entrySet());
      }

      List<BlockPos> lost = null;

      for (Entry<BlockPos, BlockState> entry : snapshot) {
         BlockPos pos = entry.getKey();
         if (this.hasChunk(pos) && MC.level.getBlockState(pos) != entry.getValue()) {
            if (lost == null) {
               lost = new ArrayList<>();
            }

            lost.add(pos);
         }
      }

      if (lost != null) {
         synchronized (this.ghosts) {
            for (BlockPos pos : lost) {
               this.ghosts.remove(pos);
               this.originals.remove(pos);
            }

            this.highlightBoxesDirty = true;
         }
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand ignoredHand) {
      if (MC == null || MC.level == null || MC.player == null || MultiPilot.isActive()) {
         return false;
      } else if (AutoTotemModule.operationActive()) {
         return false;
      } else if (hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         boolean sneaking = MC.player.isShiftKeyDown();
         if (sneaking && this.bool("sneak-bypass")) {
            return false;
         } else if (!sneaking && this.hasUseAction(MC.level.getBlockState(hit.getBlockPos()), hit.getBlockPos())) {
            return false;
         } else {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ItemStack stack = MC.player.getMainHandItem();
            if (!(stack.getItem() instanceof BlockItem)) {
               stack = MC.player.getOffhandItem();
               hand = InteractionHand.OFF_HAND;
            }

            boolean fromHand = stack.getItem() instanceof BlockItem;
            if (!fromHand) {
               if (!this.isCreative() || this.armedBlock == null) {
                  return false;
               }

               hand = InteractionHand.MAIN_HAND;
               stack = new ItemStack(this.armedBlock);
            }

            if (stack.getItem() instanceof BlockItem blockItem) {
               ((RiptideMinecraftAccessor)MC).riptide$setRightClickDelay(Math.max(1, this.integer("place-delay")));
               this.placeGhost(blockItem, hand, stack, hit, fromHand);
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void placeGhost(BlockItem blockItem, InteractionHand hand, ItemStack stack, BlockHitResult hit, boolean fromHand) {
      BlockPlaceContext context = new BlockPlaceContext(MC.player, hand, stack, hit);
      if (context.canPlace()) {
         BlockPos clicked = context.getClickedPos();
         if (!MC.level.isOutsideBuildHeight(clicked)) {
            Map<BlockPos, BlockState> before = this.snapshotAround(clicked);
            ItemStack held = fromHand ? stack.copy() : null;
            boolean placed = blockItem.place(context).consumesAction();
            if (held != null) {
               MC.player.setItemInHand(hand, held);
            }

            if (placed) {
               for (Entry<BlockPos, BlockState> entry : before.entrySet()) {
                  BlockState current = MC.level.getBlockState(entry.getKey());
                  if (current != entry.getValue()) {
                     this.rememberOriginal(entry.getKey(), entry.getValue());
                     this.markGhost(entry.getKey(), current);
                  }
               }

               MC.player.swing(hand);
            }
         }
      }
   }

   private Map<BlockPos, BlockState> snapshotAround(BlockPos pos) {
      Map<BlockPos, BlockState> states = new LinkedHashMap<>();
      states.put(pos.immutable(), MC.level.getBlockState(pos));

      for (Direction direction : Direction.values()) {
         BlockPos neighbour = pos.relative(direction).immutable();
         states.put(neighbour, MC.level.getBlockState(neighbour));
      }

      return states;
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK ? this.attackBlock(hit.getBlockPos()) : false;
   }

   @Override
   public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
      return this.attackBlock(pos);
   }

   @Override
   public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
      return this.attackBlock(pos);
   }

   private boolean attackBlock(BlockPos pos) {
      if (MC == null || MC.level == null || MC.player == null) {
         return false;
      } else if (AutoTotemModule.operationActive()) {
         return false;
      } else if (this.isGhost(pos)) {
         if (this.bool("break-ghosts")) {
            this.restoreGhost(pos, true);
         }

         return true;
      } else if (!this.isCreative()) {
         return false;
      } else {
         BlockState state = MC.level.getBlockState(pos);
         if (!state.isAir() && !MC.level.isOutsideBuildHeight(pos)) {
            this.rememberOriginal(pos, state);
            MC.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            this.markGhost(pos, MC.level.getBlockState(pos));
            MC.level.levelEvent(null, 2001, pos, Block.getId(state));
            return true;
         } else {
            return true;
         }
      }
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      if (!this.isCreative()) {
         return false;
      } else if (packet instanceof ServerboundSetCreativeModeSlotPacket) {
         return true;
      } else if (packet instanceof ServerboundPickItemFromBlockPacket pick) {
         if (!AutoTotemModule.operationActive()) {
            this.pickBlockLocally(pick.pos(), pick.includeData());
         }

         return true;
      } else if (!(packet instanceof ServerboundPlayerActionPacket action)) {
         return false;
      } else {
         Action kind = action.getAction();
         return kind == Action.DROP_ITEM || kind == Action.DROP_ALL_ITEMS;
      }
   }

   private void pickBlockLocally(BlockPos pos, boolean includeData) {
      if (MC != null && MC.level != null && MC.player != null) {
         BlockState state = MC.level.getBlockState(pos);
         if (!state.isAir()) {
            ItemStack picked = state.getCloneItemStack(MC.level, pos, includeData);
            if (!picked.isEmpty()) {
               Inventory inventory = MC.player.getInventory();
               int existing = inventory.findSlotMatchingItem(picked);
               if (Inventory.isHotbarSlot(existing)) {
                  inventory.setSelectedSlot(existing);
               } else {
                  inventory.setSelectedItem(picked);
               }
            }
         }
      }
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      if (!this.isEnabled()) {
         return false;
      } else if (packet instanceof ClientboundBlockUpdatePacket update) {
         synchronized (this.ghosts) {
            if (!this.ghosts.containsKey(update.getPos())) {
               return false;
            } else {
               this.originals.put(update.getPos(), update.getBlockState());
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean hasChunk(BlockPos pos) {
      return MC.level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
   }

   private void rememberOriginal(BlockPos pos, BlockState original) {
      synchronized (this.ghosts) {
         this.originals.putIfAbsent(pos.immutable(), original);
      }
   }

   private void markGhost(BlockPos pos, BlockState state) {
      synchronized (this.ghosts) {
         this.ghosts.put(pos.immutable(), state);

         while (this.ghosts.size() > 4096) {
            BlockPos eldest = this.ghosts.keySet().iterator().next();
            this.ghosts.remove(eldest);
            BlockState original = this.originals.remove(eldest);
            if (original != null && this.hasChunk(eldest)) {
               MC.level.setBlock(eldest, original, 3);
            }
         }

         this.highlightBoxesDirty = true;
      }
   }

   private void clearGhosts() {
      synchronized (this.ghosts) {
         this.ghosts.clear();
         this.originals.clear();
         this.highlightBoxesDirty = true;
      }
   }

   private void restoreGhost(BlockPos pos, boolean effects) {
      BlockState ghost;
      BlockState original;
      synchronized (this.ghosts) {
         ghost = this.ghosts.remove(pos);
         original = this.originals.remove(pos);
         if (ghost != null) {
            this.highlightBoxesDirty = true;
         }
      }

      if (ghost != null && MC != null && MC.level != null) {
         if (this.hasChunk(pos)) {
            if (original != null) {
               MC.level.setBlock(pos, original, 3);
            }

            if (effects) {
               MC.level.levelEvent(null, 2001, pos, Block.getId(ghost));
            }
         }
      }
   }

   public int restoreAll() {
      List<BlockPos> positions;
      synchronized (this.ghosts) {
         positions = new ArrayList<>(this.ghosts.keySet());
      }

      for (BlockPos pos : positions) {
         this.restoreGhost(pos, false);
      }

      return positions.size();
   }

   public boolean isGhost(BlockPos pos) {
      synchronized (this.ghosts) {
         return this.ghosts.containsKey(pos);
      }
   }

   public int ghostCount() {
      synchronized (this.ghosts) {
         return this.ghosts.size();
      }
   }

   public boolean isCreative() {
      return "Creative".equals(this.choice("mode"));
   }

   public void setCreative(boolean creative) {
      this.setValue("mode", creative ? "Creative" : "Survival");
   }

   public Block armedBlock() {
      return this.armedBlock;
   }

   public boolean highlightEnabled() {
      return this.bool("highlight");
   }

   public boolean highlightFill() {
      return this.bool("highlight-fill");
   }

   public int highlightColor() {
      try {
         String value = this.value("highlight-color").replace("#", "");
         if (value.length() == 6) {
            value = "FF" + value;
         }

         return (int)Long.parseLong(value, 16);
      } catch (RuntimeException var2) {
         return -6381922;
      }
   }

   public List<AABB> highlightBoxes() {
      if (this.highlightBoxesDirty) {
         synchronized (this.ghosts) {
            List<AABB> boxes = new ArrayList<>(this.ghosts.size());

            for (BlockPos pos : this.ghosts.keySet()) {
               boxes.add(new AABB(pos).inflate(0.002));
            }

            this.cachedHighlightBoxes = Collections.unmodifiableList(boxes);
            this.highlightBoxesDirty = false;
         }
      }

      return this.cachedHighlightBoxes;
   }

   public void setArmedBlock(Block block, boolean announce) {
      this.armedBlock = block;
      if (announce) {
         RiptideClientMessaging.sendPrefixed(block == null ? "§7Ghost block disarmed." : "§aGhost block: §f" + BuiltInRegistries.BLOCK.getKey(block));
      }
   }

   private boolean hasUseAction(BlockState state, BlockPos pos) {
      if (state.getMenuProvider(MC.level, pos) != null) {
         return true;
      } else {
         Block block = state.getBlock();
         return block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof NoteBlock
            || block instanceof BedBlock
            || block instanceof BellBlock
            || block instanceof CakeBlock
            || block instanceof RespawnAnchorBlock
            || block instanceof DiodeBlock
            || block instanceof DaylightDetectorBlock
            || block instanceof DragonEggBlock
            || block instanceof RedStoneWireBlock
            || block instanceof JukeboxBlock
            || block instanceof FlowerPotBlock
            || block instanceof BeehiveBlock
            || block instanceof ChiseledBookShelfBlock
            || block instanceof CandleCakeBlock;
      }
   }

   @Override
   public String info() {
      int count = this.ghostCount();
      if (count > 0) {
         return Integer.toString(count);
      } else {
         return this.armedBlock == null ? "" : BuiltInRegistries.BLOCK.getKey(this.armedBlock).getPath();
      }
   }
}
