package riptide.gui.multi;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.multi.MultiSession;

public final class MultiMenuRenderer {
   private static final FontDescription ALT_FONT = new Resource(Identifier.withDefaultNamespace("alt"));
   private static final int TEXT = -1184275;
   private static final int DIM = -6645094;
   private static final int GREEN = -11207852;
   private static final int RED = -42149;
   private static final int GOLD = -1513280;
   private static final int OUTLINE = -10790053;
   private static final int OUTLINE_OFF = -12961222;
   private static final int TRACK = -14671318;
   private static final int COOK = -2060246;
   private static final int FLAME = -34274;

   private MultiMenuRenderer() {
   }

   public static void render(
      GuiGraphicsExtractor g,
      Font font,
      MultiSession.MenuView view,
      int originX,
      int originY,
      int scrollY,
      int mouseX,
      int mouseY,
      List<MultiMenuRenderer.MenuHit> hitsOut,
      MultiMenuInput in
   ) {
      if (view != null && view.extras() != null) {
         MultiMenuRenderer.Ctx c = new MultiMenuRenderer.Ctx(g, font, originX, originY, scrollY, mouseX, mouseY, hitsOut);
         MultiSession.MenuExtras ex = view.extras();
         String var12 = strip(ex.typeId());
         switch (var12) {
            case "enchantment":
               enchantment(c, ex);
               break;
            case "furnace":
            case "blast_furnace":
            case "smoker":
               furnace(c, ex);
               break;
            case "brewing_stand":
               brewing(c, ex);
               break;
            case "anvil":
               anvil(c, ex, view, in.rename.focused() ? in.rename.text() : null);
               break;
            case "beacon":
               beacon(c, ex, in);
               break;
            case "merchant":
               merchant(c, ex);
               break;
            case "lectern":
               lectern(c, ex);
               break;
            case "stonecutter":
            case "loom":
               recipeStepper(c, ex);
         }
      }
   }

   public static int[] contentSize(MultiSession.MenuView view) {
      int w = 18;
      int h = 18;
      if (view != null) {
         for (MultiSession.ViewSlot s : view.slots()) {
            w = Math.max(w, s.x() + 18);
            h = Math.max(h, s.y() + 18);
         }

         int[] ext = widgetExtent(view);
         w = Math.max(w, ext[0]);
         h = Math.max(h, ext[1]);
      }

      return new int[]{w, h};
   }

   private static int[] widgetExtent(MultiSession.MenuView view) {
      MultiSession.MenuExtras ex = view.extras();
      if (ex == null) {
         return new int[]{0, 0};
      } else {
         String var2 = strip(ex.typeId());

         return switch (var2) {
            case "enchantment" -> new int[]{174, 66};
            case "merchant" -> new int[]{144, 8 + Math.max(1, ex.trades().size()) * 20};
            case "beacon" -> new int[]{232, 108};
            case "lectern" -> new int[]{120, 44};
            case "anvil" -> new int[]{132, 50};
            case "stonecutter" -> new int[]{130, 54};
            case "loom" -> new int[]{130, 70};
            default -> new int[]{0, 0};
         };
      }
   }

   private static void enchantment(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      int[] d = ex.data();
      EnchantmentNames names = EnchantmentNames.getInstance();
      names.initSeed(idx(d, 3, 0));
      Registry<Enchantment> reg = enchantRegistry();
      int rw = 120;
      int rh = 20;

      for (int i = 0; i < 3; i++) {
         int cost = idx(d, i, 0);
         boolean enabled = cost > 0;
         int sx = c.sx(54);
         int sy = c.sy(i * 22);
         boolean hover = enabled && c.hover(sx, sy, rw, rh);
         c.frame(sx, sy, rw, rh, hover ? 872415231 : (enabled ? 452984831 : 234881023), enabled ? -10790053 : -12961222);
         String sga = names.getRandomName(c.font, rw - 22).getString();
         c.text(Component.literal(sga).setStyle(Style.EMPTY.withFont(ALT_FONT)), sx + 4, sy + 3, enabled ? -9803158 : -12632257);
         int clueId = idx(d, 4 + i, -1);
         int clueLvl = idx(d, 7 + i, 1);
         if (enabled && clueId >= 0 && reg != null) {
            try {
               reg.get(clueId).ifPresent(h -> c.text(Enchantment.getFullname(h, clueLvl), sx + 4, sy + 12, -1513280));
            } catch (Throwable var17) {
            }
         }

         if (enabled) {
            String cs = Integer.toString(cost);
            c.text(cs, sx + rw - 4 - c.font.width(cs), sy + 3, -11207852);
            c.hits.add(new MultiMenuRenderer.MenuHit(sx, sy, rw, rh, new MultiMenuRenderer.ButtonAct(i)));
         }
      }
   }

   private static void furnace(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      int[] d = ex.data();
      int litTime = idx(d, 0, 0);
      int litDur = idx(d, 1, 0);
      int cook = idx(d, 2, 0);
      int cookTotal = idx(d, 3, 0);
      double burn = litDur > 0 ? clamp01((double)litTime / litDur) : 0.0;
      double prog = cookTotal > 0 ? clamp01((double)cook / cookTotal) : 0.0;
      int ax = c.sx(20);
      int ay = c.sy(23);
      c.rect(ax, ay, 46, 6, -14671318);
      c.rect(ax, ay, (int)(46.0 * prog), 6, -2060246);
      int fx = c.sx(6);
      int fyTop = c.sy(20);
      int fh = 14;
      c.rect(fx, fyTop, 6, fh, -14671318);
      int lit = (int)(fh * burn);
      c.rect(fx, fyTop + (fh - lit), 6, lit, -34274);
   }

   private static void brewing(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      int[] d = ex.data();
      int brewTime = idx(d, 0, 0);
      int fuel = idx(d, 1, 0);
      double brew = brewTime > 0 ? clamp01((400 - brewTime) / 400.0) : 0.0;
      int bx = c.sx(50);
      int byTop = c.sy(18);
      c.rect(bx, byTop, 6, 30, -14671318);
      c.rect(bx, byTop, 6, (int)(30.0 * brew), -3116832);
      int gx = c.sx(0);
      int gy = c.sy(50);
      c.rect(gx, gy, 18, 4, -14671318);
      c.rect(gx, gy, (int)(18.0 * clamp01(fuel / 20.0)), 4, -1003472);
   }

   private static void anvil(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex, MultiSession.MenuView view, String renameText) {
      int cost = idx(ex.data(), 0, 0);
      int bx = c.sx(0);
      int by = c.sy(0);
      int bw = 132;
      int bh = 14;
      boolean focused = renameText != null;
      c.frame(bx, by, bw, bh, 570425344, focused ? -1513280 : -10790053);
      ItemStack result = slot(view, 2);
      String label;
      int color;
      if (focused) {
         label = renameText + "_";
         color = -1184275;
      } else {
         boolean has = !result.isEmpty();
         label = has ? result.getHoverName().getString() : "Rename...";
         color = has ? -1184275 : -6645094;
      }

      c.text(trim(c.font, label, bw - 6), bx + 3, by + 3, color);
      c.hits.add(new MultiMenuRenderer.MenuHit(bx, by, bw, bh, new MultiMenuRenderer.RenameFocusAct()));
      if (cost > 0) {
         c.text("Cost: " + cost, c.sx(0), c.sy(38), cost >= 40 ? -42149 : -11207852);
      }
   }

   private static void beacon(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex, MultiMenuInput in) {
      int[] d = ex.data();
      int tier = idx(d, 0, 0);
      int effPrimary = in.beaconPrimary >= 0 ? in.beaconPrimary : idx(d, 1, -1);
      int effSecondary = in.beaconSecondary >= 0 ? in.beaconSecondary : idx(d, 2, -1);
      c.text("Beacon tier " + tier, c.sx(24), c.sy(2), tier > 0 ? -1184275 : -6645094);
      List<List<Holder<MobEffect>>> effects = BeaconBlockEntity.BEACON_EFFECTS;
      int y = 14;
      c.text("Power", c.sx(24), c.sy(y), -6645094);
      y += 10;
      int primaryBottom = y;

      for (int row = 0; row < 3 && row < effects.size() && row < tier; row++) {
         for (Holder<MobEffect> h : effects.get(row)) {
            int id = effectId(h);
            effectButton(c, c.sx(24), c.sy(primaryBottom), 100, 11, effectName(id), id == effPrimary, new MultiMenuRenderer.BeaconPick(false, id));
            primaryBottom += 12;
         }
      }

      if (tier >= 4) {
         int sy = 14;
         c.text("Secondary", c.sx(132), c.sy(sy), -6645094);
         sy += 10;
         if (effPrimary >= 0) {
            effectButton(
               c, c.sx(132), c.sy(sy), 100, 11, effectName(effPrimary) + " II", effSecondary == effPrimary, new MultiMenuRenderer.BeaconPick(true, effPrimary)
            );
            sy += 12;
         }

         if (effects.size() >= 4) {
            for (Holder<MobEffect> h : effects.get(3)) {
               int id = effectId(h);
               effectButton(c, c.sx(132), c.sy(sy), 100, 11, effectName(id), effSecondary == id, new MultiMenuRenderer.BeaconPick(true, id));
               sy += 12;
            }
         }
      }

      button(c, c.sx(24), c.sy(primaryBottom + 6), 80, 12, "Confirm", new MultiMenuRenderer.BeaconAct(effPrimary, effSecondary));
   }

   private static void effectButton(MultiMenuRenderer.Ctx c, int sx, int sy, int w, int h, String label, boolean selected, MultiMenuRenderer.MenuAction act) {
      boolean hover = c.hover(sx, sy, w, h);
      c.frame(sx, sy, w, h, selected ? 861207380 : (hover ? 872415231 : 452984831), selected ? -11207852 : -10790053);
      c.text(trim(c.font, label, w - 6), sx + 3, sy + 2, selected ? -11207852 : -1184275);
      c.hits.add(new MultiMenuRenderer.MenuHit(sx, sy, w, h, act));
   }

   private static void recipeStepper(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      boolean loom = strip(ex.typeId()).equals("loom");
      int sy = loom ? 56 : 40;
      button(c, c.sx(36), c.sy(sy), 40, 12, "< Prev", new MultiMenuRenderer.RecipeStep(-1));
      button(c, c.sx(80), c.sy(sy), 40, 12, "Next >", new MultiMenuRenderer.RecipeStep(1));
   }

   private static void merchant(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      List<MultiSession.TradeView> trades = ex.trades();
      int rw = 86;
      int rh = 18;

      for (int i = 0; i < trades.size(); i++) {
         MultiSession.TradeView t = trades.get(i);
         int sx = c.sx(0);
         int sy = c.sy(8 + i * 20);
         boolean hover = c.hover(sx, sy, rw, rh);
         c.frame(sx, sy, rw, rh, hover ? 872415231 : 352321535, t.outOfStock() ? -8372160 : -10790053);
         c.item(t.costA(), sx + 2, sy + 1);
         if (!t.costB().isEmpty()) {
            c.item(t.costB(), sx + 20, sy + 1);
         }

         c.text(">", sx + 40, sy + 5, -4210753);
         c.item(t.result(), sx + 50, sy + 1);
         if (t.outOfStock()) {
            c.rect(sx, sy + rh / 2, rw, 1, -1057013696);
         }

         c.hits.add(new MultiMenuRenderer.MenuHit(sx, sy, rw, rh, new MultiMenuRenderer.TradeAct(i)));
      }
   }

   private static void lectern(MultiMenuRenderer.Ctx c, MultiSession.MenuExtras ex) {
      c.text("Page " + (idx(ex.data(), 0, 0) + 1), c.sx(24), c.sy(2), -1184275);
      button(c, c.sx(24), c.sy(14), 40, 12, "< Prev", new MultiMenuRenderer.ButtonAct(1));
      button(c, c.sx(70), c.sy(14), 40, 12, "Next >", new MultiMenuRenderer.ButtonAct(2));
      button(c, c.sx(24), c.sy(30), 86, 12, "Take Book", new MultiMenuRenderer.ButtonAct(3));
   }

   private static void button(MultiMenuRenderer.Ctx c, int sx, int sy, int w, int h, String label, MultiMenuRenderer.MenuAction act) {
      boolean hover = c.hover(sx, sy, w, h);
      c.frame(sx, sy, w, h, hover ? 872415231 : 452984831, -10790053);
      c.text(label, sx + Math.max(2, (w - c.font.width(label)) / 2), sy + 2, -1184275);
      c.hits.add(new MultiMenuRenderer.MenuHit(sx, sy, w, h, act));
   }

   private static Registry<Enchantment> enchantRegistry() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      } else {
         try {
            return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   private static String effectName(int id) {
      return id < 0 ? "-" : BuiltInRegistries.MOB_EFFECT.get(id).map(h -> ((MobEffect)h.value()).getDisplayName().getString()).orElse("?");
   }

   private static int effectId(Holder<MobEffect> h) {
      return h == null ? -1 : BuiltInRegistries.MOB_EFFECT.getId((MobEffect)h.value());
   }

   private static ItemStack slot(MultiSession.MenuView view, int handler) {
      for (MultiSession.ViewSlot s : view.slots()) {
         if (s.handler() == handler) {
            return s.item() == null ? ItemStack.EMPTY : s.item();
         }
      }

      return ItemStack.EMPTY;
   }

   private static int idx(int[] d, int i, int def) {
      return d != null && i >= 0 && i < d.length ? d[i] : def;
   }

   private static double clamp01(double v) {
      return v < 0.0 ? 0.0 : Math.min(1.0, v);
   }

   private static String strip(String typeId) {
      if (typeId == null) {
         return "";
      } else {
         int c = typeId.indexOf(58);
         return c >= 0 ? typeId.substring(c + 1) : typeId;
      }
   }

   private static String trim(Font font, String s, int maxW) {
      if (font.width(s) <= maxW) {
         return s;
      } else {
         StringBuilder b = new StringBuilder();

         for (int i = 0; i < s.length() && font.width(b.toString() + s.charAt(i) + "...") <= maxW; i++) {
            b.append(s.charAt(i));
         }

         return b + "...";
      }
   }

   public record BeaconAct(int primary, int secondary) implements MultiMenuRenderer.MenuAction {
   }

   public record BeaconPick(boolean secondary, int effectId) implements MultiMenuRenderer.MenuAction {
   }

   public record ButtonAct(int id) implements MultiMenuRenderer.MenuAction {
   }

   private static final class Ctx {
      private final GuiGraphicsExtractor g;
      private final Font font;
      private final int ox;
      private final int oy;
      private final int scrollY;
      private final int mx;
      private final int my;
      private final List<MultiMenuRenderer.MenuHit> hits;

      Ctx(GuiGraphicsExtractor g, Font font, int ox, int oy, int scrollY, int mx, int my, List<MultiMenuRenderer.MenuHit> hits) {
         this.g = g;
         this.font = font;
         this.ox = ox;
         this.oy = oy;
         this.scrollY = scrollY;
         this.mx = mx;
         this.my = my;
         this.hits = hits;
      }

      int sx(int wx) {
         return this.ox + wx;
      }

      int sy(int wy) {
         return this.oy + wy - this.scrollY;
      }

      boolean hover(int sx, int sy, int w, int h) {
         return this.mx >= sx && this.mx < sx + w && this.my >= sy && this.my < sy + h;
      }

      void rect(int sx, int sy, int w, int h, int color) {
         if (w > 0 && h > 0) {
            UiRenderer.rect(this.g, UiBounds.of(sx, sy, w, h), color);
         }
      }

      void frame(int sx, int sy, int w, int h, int fill, int outline) {
         UiRenderer.frame(this.g, UiBounds.of(sx, sy, w, h), fill, outline);
      }

      void text(String s, int sx, int sy, int color) {
         this.g.text(this.font, Component.literal(s), sx, sy, color, false);
      }

      void text(Component comp, int sx, int sy, int color) {
         this.g.text(this.font, comp, sx, sy, color, false);
      }

      void item(ItemStack stack, int sx, int sy) {
         if (stack != null && !stack.isEmpty()) {
            try {
               this.g.item(stack, sx, sy);
               this.g.itemDecorations(this.font, stack, sx, sy);
            } catch (Throwable var5) {
            }
         }
      }
   }

   public sealed interface MenuAction
      permits MultiMenuRenderer.ButtonAct,
      MultiMenuRenderer.TradeAct,
      MultiMenuRenderer.BeaconAct,
      MultiMenuRenderer.BeaconPick,
      MultiMenuRenderer.RenameFocusAct,
      MultiMenuRenderer.RecipeStep {
   }

   public record MenuHit(int x, int y, int w, int h, MultiMenuRenderer.MenuAction action) {
   }

   public record RecipeStep(int delta) implements MultiMenuRenderer.MenuAction {
   }

   public record RenameFocusAct() implements MultiMenuRenderer.MenuAction {
   }

   public record TradeAct(int index) implements MultiMenuRenderer.MenuAction {
   }
}
