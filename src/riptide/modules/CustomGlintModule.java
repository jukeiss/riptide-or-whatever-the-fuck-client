package riptide.modules;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

/**
 * Recolours the enchantment glint by re-registering the vanilla glint textures with tinted copies.
 * The glint render types look their texture up by id every draw, so no shader or render-type changes
 * are needed; disabling releases the ids and vanilla reloads the originals on next use.
 */
public final class CustomGlintModule extends Module {
   private static final Identifier ITEM_GLINT = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
   private static final Identifier ARMOR_GLINT = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
   private int appliedSignature;
   private boolean applied;
   private boolean warned;

   public CustomGlintModule() {
      super("custom-glint", "Custom Glint", ModuleCategory.RENDER, "Recolours the enchantment glint on items and armour.");
      this.add(new ColorSetting("color", "Color", -43521).description("Glint colour. Alpha is ignored; use Brightness instead.").build());
      this.add(new IntSetting("brightness", "Brightness", 100, 10, 300, 5).unit("%").description("How strong the glint shimmer is.").build());
      this.add(new BoolSetting("armor", "Armour Too", true).description("Also recolour the glint on worn armour.").build());
   }

   @Override
   public void onEnable() {
      this.applied = false;
      this.warned = false;
   }

   @Override
   public void onDisable() {
      this.restore();
   }

   @Override
   public void onGameJoin() {
      // A resource reload on join can put the vanilla textures back.
      this.applied = false;
   }

   @Override
   public void tick() {
      int signature = ModuleRenderUtil.color(this, "color", -43521) * 31 + this.integer("brightness") * 7 + (this.bool("armor") ? 1 : 0);
      if (!this.applied || signature != this.appliedSignature) {
         this.appliedSignature = signature;
         this.applied = true;
         this.apply();
      }
   }

   private void apply() {
      int color = ModuleRenderUtil.color(this, "color", -43521);
      float gain = this.integer("brightness") / 100.0F;
      boolean ok = this.tint(ITEM_GLINT, color, gain);
      if (this.bool("armor")) {
         ok &= this.tint(ARMOR_GLINT, color, gain);
      } else {
         release(ARMOR_GLINT);
      }

      if (!ok && !this.warned) {
         this.warned = true;
         RiptideClientMessaging.sendPrefixed("§cCustom Glint couldn't load the vanilla glint texture; is a resource pack replacing it?");
      }
   }

   private boolean tint(Identifier id, int color, float gain) {
      try {
         // Always tint from the pack's original, not from a previously tinted copy.
         release(id);
         TextureContents contents = TextureContents.load(MC.getResourceManager(), id);
         NativeImage source = contents.image();
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         NativeImage tinted = source.mappedCopy(argb -> {
            // The vanilla glint is a purple noise pattern; its brightest channel carries the shimmer.
            int luma = Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF));
            float k = Math.min(1.0F, luma / 255.0F * gain);
            return argb & 0xFF000000 | Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
         });

         source.close();

         MC.getTextureManager().register(id, new CustomGlintModule.GlintTexture(id.toString(), tinted));
         return true;
      } catch (Throwable var13) {
         return false;
      }
   }

   private void restore() {
      release(ITEM_GLINT);
      release(ARMOR_GLINT);
      this.applied = false;
   }

   private static void release(Identifier id) {
      try {
         if (MC.getTextureManager().getTexture(id) instanceof CustomGlintModule.GlintTexture) {
            MC.getTextureManager().release(id);
         }
      } catch (Throwable var2) {
      }
   }

   private static final class GlintTexture extends AbstractTexture {
      private final NativeImage pixels;

      private GlintTexture(String label, NativeImage pixels) {
         this.pixels = pixels;
         this.texture = RenderSystem.getDevice().createTexture(label, 5, GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
         // The glint scrolls, so it must wrap.
         this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
      }

      public void close() {
         try {
            this.pixels.close();
         } catch (Throwable var2) {
         }

         super.close();
      }
   }
}
