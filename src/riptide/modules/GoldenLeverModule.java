package riptide.modules;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import riptide.mixin.accessor.RiptideItemStackRenderStateAccessor;

public final class GoldenLeverModule extends Module {
   public static final int GOLD_TINT = -11702;
   private static volatile boolean active;
   private static volatile boolean femaleBody;
   private static volatile boolean femaleBodySelfOnly;
   private static volatile boolean femaleBodyOnlyOthers;
   private static volatile boolean femaleBodyCustomPlayers;
   private static volatile boolean femaleBodyCustomIncludeSelf;
   private static volatile Set<String> femaleBodyPlayerNames = Set.of();

   public GoldenLeverModule() {
      super("golden-lever", "GoldenLever", ModuleCategory.MISC, "Renames and recolors vanilla levers.");
      active = this.isEnabled();
      this.refreshFemaleBodySettings();
   }

   @Override
   public void onEnable() {
      active = true;
      ModuleRenderUtil.refreshWorldRenderer();
   }

   @Override
   public void onDisable() {
      active = false;
      ModuleRenderUtil.refreshWorldRenderer();
   }

   @Override
   protected void onOptionValueChanged(String optionId) {
      if (optionId != null && optionId.startsWith("female-body")) {
         this.refreshFemaleBodySettings();
      }
   }

   @Override
   protected void onSettingsReset() {
      femaleBody = false;
      femaleBodySelfOnly = true;
      femaleBodyOnlyOthers = false;
      femaleBodyCustomPlayers = false;
      femaleBodyCustomIncludeSelf = true;
      femaleBodyPlayerNames = Set.of();
   }

   public static boolean isStylingActive() {
      return active;
   }

   public static boolean isFemaleBodyActive() {
      return false;
   }

   public static boolean shouldApplyFemaleBody(int var0) {
      return false;
   }

   private void refreshFemaleBodySettings() {
      femaleBody = false;
      femaleBodySelfOnly = false;
      femaleBodyOnlyOthers = false;
      femaleBodyCustomPlayers = false;
      femaleBodyCustomIncludeSelf = false;
      femaleBodyPlayerNames = Set.of();
   }

   private static String normalizePlayerName(String name) {
      return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
   }

   public static boolean shouldStyle(ItemStack stack) {
      return isActive() && stack != null && stack.is(Items.LEVER);
   }

   public static boolean shouldStyle(BlockState state) {
      return isActive() && state != null && state.is(Blocks.LEVER);
   }

   public static Component leverName() {
      return Component.literal("Golden Lever").withStyle(ChatFormatting.GOLD);
   }

   public static void tintItemStackRenderState(ItemStackRenderState output) {
      if (output != null) {
         RiptideItemStackRenderStateAccessor accessor = (RiptideItemStackRenderStateAccessor)output;
         LayerRenderState[] layers = accessor.riptide$getLayers();
         int count = Math.min(accessor.riptide$getActiveLayerCount(), layers.length);

         for (int i = 0; i < count; i++) {
            tintLayer(layers[i]);
         }
      }
   }

   private static void tintLayer(LayerRenderState layer) {
      if (layer != null) {
         List<BakedQuad> quads = layer.prepareQuadList();

         for (int i = 0; i < quads.size(); i++) {
            quads.set(i, withTintIndex(quads.get(i)));
         }

         IntList tints = layer.tintLayers();
         tints.clear();
         tints.add(-11702);
      }
   }

   private static BakedQuad withTintIndex(BakedQuad quad) {
      MaterialInfo materialInfo = quad.materialInfo();
      if (materialInfo.tintIndex() == 0) {
         return quad;
      } else {
         MaterialInfo tintedInfo = new MaterialInfo(
            materialInfo.sprite(), materialInfo.layer(), materialInfo.itemRenderType(), 0, materialInfo.shade(), materialInfo.lightEmission()
         );
         return new BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            quad.packedUV0(),
            quad.packedUV1(),
            quad.packedUV2(),
            quad.packedUV3(),
            quad.direction(),
            tintedInfo
         );
      }
   }

   private static boolean isActive() {
      return active;
   }
}
