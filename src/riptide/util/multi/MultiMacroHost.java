package riptide.util.multi;

import java.util.List;
import java.util.Map;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.api.custommenu.CustomMenuSubmission;
import riptide.api.custommenu.CustomMenuSubmitResult;
import riptide.util.macro.CaptureValueAction;
import riptide.util.macro.ContainerClickSequenceAction;
import riptide.util.macro.ItemAction;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.PacketBurstAction;
import riptide.util.macro.PickUpAllAction;
import riptide.util.macro.SignEditAction;
import riptide.util.macro.StoreItemAction;
import riptide.util.macro.SwapSlotsAction;
import riptide.util.macro.UseItemPhaseAction;
import riptide.util.macro.WaitForMacroStepAction;
import riptide.util.macro.WaitForSlotChangeAction;
import riptide.util.macro.WaitPacketMatchAction;
import riptide.util.macro.XCarryAction;

public interface MultiMacroHost {
   boolean macroReady();

   default boolean customMenuPhaseActive() {
      return this.macroReady();
   }

   boolean fullMode();

   default void macroNote(String note) {
   }

   default CustomMenuSnapshot customMenu() {
      return null;
   }

   default CustomMenuSubmitResult submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
      return CustomMenuSubmitResult.failure("Custom menus are unavailable");
   }

   default String resolveCustomMenuValue(String template, Map<String, String> macroVariables) {
      return template == null ? "" : template;
   }

   default String botUsername() {
      return "";
   }

   default String botUuid() {
      return "";
   }

   default String serverAddress() {
      return "";
   }

   default String macroPassword() {
      return "";
   }

   float health();

   float maxHealth();

   int food();

   boolean hasPosition();

   double posX();

   double posY();

   double posZ();

   String dimension();

   String heldItemName();

   int selectedHotbar();

   String openScreenTitle();

   boolean containerOpen();

   long guiOpenSeq();

   int countItem(String var1);

   int countItemTarget(ItemTarget var1);

   int freeSlots();

   boolean slotFilled(int var1);

   boolean cursorEmpty();

   String cursorName();

   boolean cursorMatches(ItemTarget var1);

   float currentPitch();

   int[] heldDurability();

   int[] durabilityAtInv(int var1);

   int[] itemDurability(ItemTarget var1);

   long teleportSeq();

   int gameMode();

   long chatSeq();

   List<String> chatSince(long var1);

   boolean entityWithin(List<String> var1, boolean var2, boolean var3, double var4, double var6, double var8, double var10);

   void setPacketCapture(boolean var1);

   long packetSeq();

   boolean packetSeen(long var1, List<String> var3);

   boolean editSign(SignEditAction var1, String var2, String var3, String var4, String var5);

   void setSoundCapture(boolean var1);

   long soundSeq();

   boolean soundMatched(long var1, List<String> var3, boolean var4, double var5);

   boolean packetMatched(long var1, WaitPacketMatchAction var3);

   boolean itemOnCooldown(ItemTarget var1, boolean var2);

   String captureItemText(CaptureValueAction var1, ItemTarget var2);

   List<String> tablistNames(boolean var1);

   int requestCommandSuggestions(String var1);

   List<String> commandSuggestions(int var1);

   List<CaptureValueAction.ScoreboardLine> scoreboardLines();

   long containerRevision();

   default MultiMacroHost.InventorySyncState inventorySyncState() {
      return MultiMacroHost.InventorySyncState.READY;
   }

   void sendRawPayload(String var1, String var2);

   boolean blockAt(int var1, int var2, int var3, List<String> var4, boolean var5, boolean var6);

   String[] slotChangeBaseline(WaitForSlotChangeAction var1);

   boolean slotChangeMet(WaitForSlotChangeAction var1, String[] var2);

   List<int[]> resolveItemClicks(ItemAction var1);

   List<int[]> resolveStoreClicks(StoreItemAction var1);

   List<int[]> resolveSwapClicks(SwapSlotsAction var1);

   List<int[]> resolvePickupAllClicks(PickUpAllAction var1);

   List<int[]> resolveSequenceClicks(ContainerClickSequenceAction var1);

   void clickResolved(int var1, int var2, int var3);

   boolean sendPacketBurst(PacketBurstAction var1);

   int writeBook(List<String> var1, String var2, boolean var3, boolean var4, int var5);

   boolean macroStepMet(WaitForMacroStepAction var1);

   boolean saveGui(boolean var1, boolean var2);

   boolean desyncGui();

   boolean restoreGui();

   int runXCarry(XCarryAction var1, long var2);

   void cancelXCarry();

   int nearestEntity(String var1);

   double[] entityPos(int var1);

   String runClient(String var1, String var2);

   void useItemPhase(UseItemPhaseAction.Phase var1, boolean var2);

   String chat(String var1);

   boolean startSelfMacro(String var1);

   void stopSelfMacro();

   void disconnectBot(String var1);

   float currentYaw();

   void look(float var1, float var2);

   void move(double var1, double var3, long var5);

   int clip(double var1, double var3, double var5, int var7, boolean var8);

   boolean clipBusy();

   long clipDrainMillis();

   void setSneak(boolean var1);

   void setSprint(boolean var1);

   boolean sprinting();

   void jump();

   String interactEntity(int var1, boolean var2);

   String useOnBlock(int var1, int var2, int var3, String var4);

   String breakBlock(int var1, int var2, int var3, String var4);

   public static enum InventorySyncState {
      READY,
      WAITING,
      BLOCKED;
   }
}
