package riptide.gui.screen;

import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.realmsclient.RealmsMainScreen;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.modules.Module;
import riptide.modules.ModuleCategory;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideColors;
import riptide.util.RiptideDiscordLogin;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideLinks;
import riptide.util.RiptideMarquee;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePerf;
import riptide.util.RiptideProfilesOverlay;
import riptide.util.RiptideSpotify;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;
import riptide.util.RiptideUiScale;

public class RiptideTitleScreen extends Screen {
   private static final Identifier LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/riptide_client_logo.png");
   private static final Identifier BUTTON_CLICK_SOUND_ID = Identifier.fromNamespaceAndPath("riptide", "gui.main_menu_click");
   private static final SoundEvent BUTTON_CLICK_SOUND = SoundEvent.createVariableRangeEvent(BUTTON_CLICK_SOUND_ID);
   private static final int LOGO_TEXTURE_WIDTH = 516;
   private static final int LOGO_TEXTURE_HEIGHT = 144;
   private static final Identifier TEXT_SINGLEPLAYER = buttonText("singleplayer");
   private static final Identifier TEXT_MULTIPLAYER = buttonText("multiplayer");
   private static final Identifier TEXT_REALMS = buttonText("minecraft_realms");
   private static final Identifier TEXT_OPTIONS = buttonText("options");
   private static final Identifier TEXT_QUIT = buttonText("quit_game");
   private static final Identifier ESSENTIAL_ICON = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/icons/essential.png");
   private static final Identifier MODMENU_ICON = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/icons/modmenu.png");
   private static final Identifier REPLAYMOD_ICON = Identifier.fromNamespaceAndPath("replaymod", "logo_button.png");
   private static final Identifier FLASHBACK_ICON = Identifier.fromNamespaceAndPath("flashback", "icon.png");
   private static final Identifier DISCORD_SUPPORT_ICON = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/icons/discord.png");
   private static final Identifier DONATE_SUPPORT_ICON = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/icons/donate.png");
   private static final int SUPPORT_ICON_WIDTH = 32;
   private static final int SUPPORT_ICON_HEIGHT = 32;
   private static final Identifier LANGUAGE_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/language");
   private static final Identifier ACCESSIBILITY_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/accessibility");
   private static final int VANILLA_SPRITE_SIZE = 15;
   private static final int ICON_TEXTURE_SIZE = 32;
   private static final int REPLAYMOD_ICON_TEXTURE_SIZE = 164;
   private static final int FLASHBACK_ICON_TEXTURE_SIZE = 128;
   private static final Component TITLE = Component.translatable("narrator.screen.title");
   private static final int PANEL_PAD = 5;
   private static final int STATUS_ROW_H = 11;
   private static final int BIG_LINE_H = 15;
   private static final int CATEGORY_ROW_H = 12;
   private static final int MANAGER_BUTTON_H = 16;
   private final CompactTheme theme = new CompactTheme();
   private final List<RiptideTitleScreen.MenuButton> buttons = new ArrayList<>();
   private final String modCountText = createModCountText();
   private final boolean modMenuLoaded = FabricLoader.getInstance().isModLoaded("modmenu");
   private final boolean essentialLoaded = FabricLoader.getInstance().isModLoaded("essential");
   private final boolean replayModLoaded = FabricLoader.getInstance().isModLoaded("replaymod");
   private final boolean flashbackLoaded = FabricLoader.getInstance().isModLoaded("flashback");
   private List<RiptideTitleScreen.MeteorCreditLine> meteorCredits = List.of();
   private boolean meteorCreditsLoadFailed;
   private int cachedServerCount = -1;
   private long serverCountCheckedAt;
   private boolean layoutDirty = true;
   private int layoutScreenW = -1;
   private int layoutScreenH = -1;
   private final long openedAtNanos = System.nanoTime();
   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private int supportX;
   private int supportY;
   private int supportW;
   private int supportH;
   private int statusX;
   private int statusY;
   private int statusW;
   private int statusH;
   private int statusLabelW;
   private int modulesX;
   private int modulesY;
   private int modulesW;
   private int modulesH;
   private boolean sidePanelsVisible;
   private int utilityRowW;
   private int utilityRowX;
   private int centerStackBottom;
   private final List<RiptideTitleScreen.StatusRow> statusRows = new ArrayList<>();
   private final List<RiptideTitleScreen.CategoryRow> categoryRows = new ArrayList<>();
   private int moduleTotalCount;
   private int moduleActiveCount;
   private static final Component COPYRIGHT_TEXT = Component.translatable("title.credits");
   private static final Random SPLASH_RANDOM = new Random();
   private static List<String> vanillaSplashPool;
   private final LogoRenderer vanillaLogo = new LogoRenderer(false);
   private SplashRenderer vanillaSplash;
   private boolean vanillaSplashChosen;
   private static final int MENU_ART_SIZE = 16;

   public RiptideTitleScreen() {
      super(TITLE);
   }

   protected void init() {
      if (!RiptideMenuPrefs.customMainMenuEnabled() && this.minecraft != null) {
         this.minecraft.gui.setScreen(new TitleScreen());
      } else {
         this.layoutDirty = true;
         this.cachedServerCount = -1;
      }
   }

   private void initVanillaSkin() {
      byte var1 = 24;
      int var2 = this.height / 4 + 48;
      this.addRenderableWidget(
         Button.builder(Component.translatable("menu.singleplayer"), var1x -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)))
            .bounds(this.width / 2 - 100, var2, 200, 20)
            .build()
      );
      Component var3 = this.multiplayerDisabledReason();
      boolean var4 = var3 == null;
      Tooltip var5 = var3 != null ? Tooltip.create(var3) : null;
      int var6 = var2 + var1;
      ((Button)this.addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"), var1x -> {
         Object var2x = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
         this.minecraft.gui.setScreen((Screen)var2x);
      }).bounds(this.width / 2 - 100, var6, 200, 20).tooltip(var5).build())).active = var4;
      int var7 = var6 + var1;
      ((Button)this.addRenderableWidget(
            Button.builder(Component.translatable("menu.online"), var1x -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)))
               .bounds(this.width / 2 - 100, var7, 200, 20)
               .tooltip(var5)
               .build()
         ))
         .active = var4;
      int var8 = var7 + 36;
      SpriteIconButton var9 = (SpriteIconButton)this.addRenderableWidget(
         CommonButtons.language(
            20, var1x -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true
         )
      );
      var9.setPosition(this.width / 2 - 124, var8);
      this.addRenderableWidget(
         Button.builder(Component.translatable("menu.options"), var1x -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)))
            .bounds(this.width / 2 - 100, var8, 98, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("menu.quit"), var1x -> this.minecraft.stop()).bounds(this.width / 2 + 2, var8, 98, 20).build()
      );
      SpriteIconButton var10 = (SpriteIconButton)this.addRenderableWidget(
         CommonButtons.accessibility(20, var1x -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)), true)
      );
      var10.setPosition(this.width / 2 + 104, var8);
      int var11 = this.font.width(COPYRIGHT_TEXT);
      this.addRenderableWidget(
         new PlainTextButton(
            this.width - var11 - 2,
            this.height - 10,
            var11,
            10,
            COPYRIGHT_TEXT,
            var1x -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)),
            this.font
         )
      );
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      long var5 = RiptidePerf.begin();
      this.minecraft.gameRenderer.panorama().extractRenderState(var1, this.width, this.height);
      float var7 = (float)RiptideUiScale.toVirtual(var2);
      float var8 = (float)RiptideUiScale.toVirtual(var3);
      this.layout();
      RiptideUiScale.pushOverlayScale(var1);

      try {
         if (this.sidePanelsVisible) {
            this.renderSupportPanel(var1);
            this.renderStatusPanel(var1);
            this.renderModulesPanel(var1);
         }

         this.renderTitleCard(var1);
         Component var9 = null;

         for (RiptideTitleScreen.MenuButton var11 : this.buttons) {
            var11.render(var1, var7, var8, var4);
            if (var11.contains(var7, var8)) {
               var1.requestCursor(var11.enabled ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
               if (var11.tooltip != null) {
                  var9 = var11.tooltip;
               }
            }
         }

         if (var9 != null) {
            this.renderCustomTooltip(var1, var9, var7, var8);
         }

         this.renderMeteorCredits(var1);
         this.renderModCount(var1);
         this.renderSpotifyStrip(var1);
      } finally {
         RiptideUiScale.popOverlayScale(var1);
         RiptidePerf.end("title.render", var5);
      }
   }

   private void renderVanillaSkin(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      this.minecraft.gameRenderer.panorama().extractRenderState(var1, this.width, this.height);
      super.extractRenderState(var1, var2, var3, var4);
      this.vanillaLogo.extractRenderState(var1, this.width, 1.0F);
      if (!this.vanillaSplashChosen) {
         this.vanillaSplash = this.pickVanillaSplash();
         this.vanillaSplashChosen = true;
      }

      if (this.vanillaSplash != null && !(Boolean)this.minecraft.options.hideSplashTexts().get()) {
         this.vanillaSplash.extractRenderState(var1, this.width, this.font, 1.0F);
      }

      String var5 = "Minecraft " + SharedConstants.getCurrentVersion().name();
      var1.text(this.font, var5, 2, this.height - 10, RiptideColors.accent());
   }

   private SplashRenderer pickVanillaSplash() {
      List var1 = this.vanillaSplashPool();
      if (var1.isEmpty()) {
         return null;
      } else {
         String var2 = (String)var1.get(SPLASH_RANDOM.nextInt(var1.size()));
         return new SplashRenderer(Component.literal(var2).setStyle(Style.EMPTY.withColor(-256)));
      }
   }

   private List<String> vanillaSplashPool() {
      if (vanillaSplashPool != null) {
         return vanillaSplashPool;
      } else {
         ArrayList var1 = new ArrayList();

         try {
            IoSupplier var2 = this.minecraft
               .getVanillaPackResources()
               .getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace("texts/splashes.txt"));
            if (var2 != null) {
               try (BufferedReader var3 = new BufferedReader(new InputStreamReader((InputStream)var2.get(), StandardCharsets.UTF_8))) {
                  var3.lines().map(String::trim).filter(var0 -> !var0.isEmpty() && var0.hashCode() != 125780783).forEach(var1::add);
               }
            }
         } catch (Exception var8) {
         }

         vanillaSplashPool = List.copyOf(var1);
         return vanillaSplashPool;
      }
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      if (var1.button() != 0) {
         return false;
      } else {
         float var3 = (float)RiptideUiScale.toVirtual(var1.x());
         float var4 = (float)RiptideUiScale.toVirtual(var1.y());
         this.layout();

         for (RiptideTitleScreen.MenuButton var6 : this.buttons) {
            if (var6.click(var3, var4)) {
               return true;
            }
         }

         return false;
      }
   }

   public void removed() {
   }

   private void layout() {
      int var1 = RiptideUiScale.getVirtualScreenWidth();
      int var2 = RiptideUiScale.getVirtualScreenHeight();
      if (this.layoutDirty || var1 != this.layoutScreenW || var2 != this.layoutScreenH) {
         this.layoutDirty = false;
         this.layoutScreenW = var1;
         this.layoutScreenH = var2;
         boolean var3 = var2 < 320;
         byte var4 = 8;
         this.cardW = Math.min(296, Math.max(180, var1 - var4 * 2));
         this.cardH = Math.max(20, Math.round(144.0F * Math.min(1.0F, (this.cardW - 16) / 516.0F)));
         int var5 = Math.min(this.cardW - 28, var3 ? 220 : 240);
         int var6 = var3 ? 18 : 22;
         int var7 = var3 ? 3 : 5;
         int var8 = var3 ? 6 : 10;
         int var9 = this.cardH + var8 + var6 * 5 + var7 * 4;
         int var10 = var3 ? 16 : 20;
         int var11 = var10 + var4 + 14;
         this.cardX = (var1 - this.cardW) / 2;
         this.cardY = Math.max(var4, (var2 - var9 - var11) / 2);
         this.centerStackBottom = this.cardY + var9;
         int var12 = this.cardY + this.cardH + var8;
         int var13 = this.cardX + (this.cardW - var5) / 2;
         int var14 = Math.min(172, (var1 - this.cardW) / 2 - var4 * 2);
         this.sidePanelsVisible = var14 >= 104 && var2 >= 250;
         Component var15 = this.multiplayerDisabledReason();
         boolean var16 = var15 == null;
         this.buttons.clear();
         RiptideTitleScreen.MenuButton var17 = new RiptideTitleScreen.MenuButton(
               var13, var12, var5, var6, Component.translatable("menu.singleplayer"), true, () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))
            )
            .asMainRow(1);
         var17.withLabelTexture(TEXT_SINGLEPLAYER, 248, 24, 124, 12);
         this.buttons.add(var17);
         RiptideTitleScreen.MenuButton var18 = new RiptideTitleScreen.MenuButton(
               var13, var12 + var6 + var7, var5, var6, Component.translatable("menu.multiplayer"), var16, () -> {
                  Object var1x = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
                  this.minecraft.gui.setScreen((Screen)var1x);
               }
            )
            .withTooltip(var15)
            .withRightBadge(this.serverCountBadge())
            .asMainRow(2);
         var18.withLabelTexture(TEXT_MULTIPLAYER, 222, 24, 111, 12);
         this.buttons.add(var18);
         RiptideTitleScreen.MenuButton var19 = new RiptideTitleScreen.MenuButton(
               var13,
               var12 + (var6 + var7) * 2,
               var5,
               var6,
               Component.translatable("menu.online"),
               var16,
               () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))
            )
            .withTooltip(var15)
            .asMainRow(3);
         var19.withLabelTexture(TEXT_REALMS, 136, 24, 68, 12);
         this.buttons.add(var19);
         RiptideTitleScreen.MenuButton var20 = new RiptideTitleScreen.MenuButton(
               var13,
               var12 + (var6 + var7) * 3,
               var5,
               var6,
               Component.translatable("menu.options"),
               true,
               () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false))
            )
            .asMainRow(4);
         var20.withLabelTexture(TEXT_OPTIONS, 138, 24, 69, 12);
         this.buttons.add(var20);
         RiptideTitleScreen.MenuButton var21 = new RiptideTitleScreen.MenuButton(
               var13, var12 + (var6 + var7) * 4, var5, var6, Component.translatable("menu.quit"), true, () -> this.minecraft.stop()
            )
            .asMainRow(5);
         var21.withLabelTexture(TEXT_QUIT, 74, 24, 37, 12);
         this.buttons.add(var21);
         this.layoutUtilityButtons(var1, var2, var4, var10);
         if (!this.sidePanelsVisible) {
            this.statusRows.clear();
            this.categoryRows.clear();
         } else {
            int var22 = var2 - var11 - this.cardY;
            byte var23 = 44;
            int var24 = Math.max(1, (var22 - var23 - 3) / 12);
            this.buildCategoryRows(var24);
            this.buildStatusRows();
            this.statusLabelW = 0;
            int var25 = 0;

            for (RiptideTitleScreen.StatusRow var27 : this.statusRows) {
               this.statusLabelW = Math.max(this.statusLabelW, UiText.width(this.font, var27.label(), UiAssets.FONT_LABEL, 0));
               var25 = Math.max(var25, UiText.width(this.font, var27.value().get(), UiAssets.FONT_BODY, 0));
            }

            this.statusW = this.statusLabelW + var25 + 24;
            this.statusH = 5 + this.statusRows.size() * 11 + 5 - 1;
            this.statusX = var4;
            this.statusY = this.cardY;
            int var38 = Math.max(var3 ? 16 : 20, UiText.fontHeight(UiAssets.FONT_LABEL) + 8);
            byte var39 = 3;
            int var28 = Math.min(14, var38 - 6);
            String[] var29 = new String[]{"RIPTIDE", "RIPTIDE CLIENT"};
            int var30 = 0;

            for (String var34 : var29) {
               var30 = Math.max(var30, UiText.width(this.font, var34, UiAssets.FONT_LABEL, 0));
            }

            this.supportW = var30 + var28 + 22;
            this.supportH = 5 + 2 * var38 + var39 + 5;
            this.supportX = var4;
            this.supportY = this.statusY + this.statusH + 6;
            int var40 = this.supportY + 5;
            this.addSupportRow(this.supportX + 4, var40, this.supportW - 8, var38, var29[0], DISCORD_SUPPORT_ICON, () -> RiptideLinks.open(""), "");
            this.addSupportRow(
               this.supportX + 4, var40 + var38 + var39, this.supportW - 8, var38, var29[1], DISCORD_SUPPORT_ICON, () -> RiptideLinks.open(""), ""
            );
            String var41 = Integer.toString(this.moduleActiveCount);
            String var42 = " / " + this.moduleTotalCount + " ACTIVE";
            int var43 = 7 + UiText.width(this.font, var41, UiAssets.FONT_TITLE, 0) + 3 + UiText.width(this.font, var42, UiAssets.FONT_BODY, 0) + 7;

            for (RiptideTitleScreen.CategoryRow var36 : this.categoryRows) {
               if (var36.total() < 0) {
                  var43 = Math.max(var43, 12 + UiText.width(this.font, var36.label(), UiAssets.FONT_BODY, 0));
               } else {
                  String var37 = var36.active() + "/" + var36.total();
                  var43 = Math.max(
                     var43, 6 + UiText.width(this.font, var36.label(), UiAssets.FONT_BODY, 0) + 8 + UiText.width(this.font, var37, UiAssets.FONT_BODY, 0) + 6
                  );
               }
            }

            var43 = Math.max(var43, 10 + UiText.width(this.font, "OPEN MANAGER", UiAssets.FONT_LABEL, 0) + 20);
            this.modulesW = Math.min(var14, var43);
            this.modulesH = var23 + 3 + this.categoryRows.size() * 12;
            this.modulesX = var1 - var4 - this.modulesW;
            this.modulesY = this.cardY;
            RiptideTitleScreen.MenuButton var45 = new RiptideTitleScreen.MenuButton(
                  this.modulesX + 5, this.modulesY + this.modulesH - 5 - 16, this.modulesW - 10, 16, Component.literal("OPEN MANAGER"), true, () -> {
                     if (!PackHideState.isHardLocked()) {
                        this.minecraft.gui.setScreen(new RiptideModuleScreen(this, RiptideModuleScreen.Mode.TITLE_SETUP));
                     }
                  }
               )
               .asMainRow(0)
               .withIntro(260, true);
            this.buttons.add(var45);
         }
      }
   }

   private void addSupportRow(int var1, int var2, int var3, int var4, String var5, Identifier var6, Runnable var7, String var8) {
   }

   private void layoutUtilityButtons(int var1, int var2, int var3, int var4) {
      ArrayList var5 = new ArrayList();
      if (this.modMenuLoaded) {
         var5.add(new RiptideTitleScreen.UtilityButtonSpec("MODS", "", MODMENU_ICON, null, 32, () -> this.openModMenu(), Component.literal("Mod Menu")));
      }

      var5.add(
         new RiptideTitleScreen.UtilityButtonSpec(
            "LANGUAGE",
            "",
            null,
            LANGUAGE_SPRITE,
            32,
            () -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())),
            Component.literal("Language")
         )
      );
      var5.add(new RiptideTitleScreen.UtilityButtonSpec("MODULES", "", UiAssets.ICON_MAIN_MENU_CATEGORY, null, 32, () -> {
         if (!PackHideState.isHardLocked()) {
            this.minecraft.gui.setScreen(new RiptideModuleScreen(this, RiptideModuleScreen.Mode.TITLE_SETUP));
         }
      }, Component.literal("Modules & Macros")));
      var5.add(new RiptideTitleScreen.UtilityButtonSpec("PROFILES", "", UiAssets.ICON_PROFILES, null, 32, () -> {
         if (!PackHideState.isHardLocked()) {
            RiptideModule var1x = RiptideModule.get();
            IRiptideOverlay var2x = var1x == null ? null : var1x.getProfilesOverlay();
            if (var2x != null) {
               RiptideOverlayManager.get().register(var2x);
               ((RiptideProfilesOverlay)var2x).setMainMenuMode(true);
               var2x.setVisible(true);
               this.minecraft.gui.setScreen(new RiptideOverlayHostScreen(var2x, this, true));
            }
         }
      }, Component.literal("Profiles")));
      var5.add(
         new RiptideTitleScreen.UtilityButtonSpec(
            "ACCESSIBILITY",
            "",
            null,
            ACCESSIBILITY_SPRITE,
            32,
            () -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)),
            Component.literal("Accessibility")
         )
      );
      if (this.replayModLoaded) {
         var5.add(
            new RiptideTitleScreen.UtilityButtonSpec(
               "REPLAYS", "", REPLAYMOD_ICON, null, 164, () -> this.openReplayViewer(), Component.literal("Replay Viewer")
            )
         );
      }

      if (this.flashbackLoaded) {
         var5.add(
            new RiptideTitleScreen.UtilityButtonSpec(
               "FLASHBACK", "", FLASHBACK_ICON, null, 128, () -> this.openFlashback(), Component.literal("Flashback Replays")
            )
         );
      }

      if (this.essentialLoaded) {
         var5.add(
            new RiptideTitleScreen.UtilityButtonSpec("ESSENTIAL", "", ESSENTIAL_ICON, null, 32, () -> this.openEssential(), Component.literal("Essential"))
         );
      }

      byte var6 = 4;
      this.utilityRowW = var5.isEmpty() ? 0 : var5.size() * var4 + (var5.size() - 1) * var6;
      int var7 = var1 - var3 - this.utilityRowW;
      this.utilityRowX = var5.isEmpty() ? var1 : var7;
      int var8 = var2 - var3 - var4;

      for (int var9 = 0; var9 < var5.size(); var9++) {
         this.addUtilityButton((RiptideTitleScreen.UtilityButtonSpec)var5.get(var9), var7 + var9 * (var4 + var6), var8, var4);
      }
   }

   private void addUtilityButton(RiptideTitleScreen.UtilityButtonSpec var1, int var2, int var3, int var4) {
      RiptideTitleScreen.MenuButton var5 = new RiptideTitleScreen.MenuButton(
            var2, var3, var4, var4, Component.literal(var1.title()), var1.onPress() != null, var1.onPress()
         )
         .asUtility(var1.subtitle())
         .withTooltip(var1.tooltip());
      if (var1.sprite() != null) {
         var5.withSprite(var1.sprite());
      } else if (var1.icon() != null) {
         var5.withIcon(var1.icon(), var1.iconTextureSize());
      }

      this.buttons.add(var5);
   }

   private Component multiplayerDisabledReason() {
      if (this.minecraft.allowsMultiplayer()) {
         return null;
      } else if (this.minecraft.isNameBanned()) {
         return Component.translatable("title.multiplayer.disabled.banned.name");
      } else {
         BanDetails var1 = this.minecraft.multiplayerBan();
         if (var1 != null) {
            return var1.expires() != null
               ? Component.translatable("title.multiplayer.disabled.banned.temporary")
               : Component.translatable("title.multiplayer.disabled.banned.permanent");
         } else {
            return Component.translatable("title.multiplayer.disabled");
         }
      }
   }

   private void buildStatusRows() {
      this.statusRows.clear();
      this.statusRows.add(new RiptideTitleScreen.StatusRow("USER:", () -> this.minecraft.getUser().getName()));
      this.statusRows.add(new RiptideTitleScreen.StatusRow("BUILD:", RiptideTitleScreen::modVersion));
   }

   private void buildCategoryRows(int var1) {
      this.categoryRows.clear();
      int var2 = 0;
      int var3 = 0;
      ArrayList var4 = new ArrayList();

      for (ModuleCategory var6 : ModuleCategory.values()) {
         List var7 = ModuleRegistry.byCategory(var6);
         if (!var7.isEmpty()) {
            int var8 = 0;

            for (Module var10 : var7) {
               if (var10.isEnabled()) {
                  var8++;
               }
            }

            var4.add(new RiptideTitleScreen.CategoryRow(var6.label().toUpperCase(Locale.ROOT), var8, var7.size()));
            var2 += var7.size();
            var3 += var8;
         }
      }

      this.moduleTotalCount = var2;
      this.moduleActiveCount = var3;
      if (var4.size() <= var1) {
         this.categoryRows.addAll(var4);
      } else {
         int var11 = Math.max(0, var1 - 1);

         for (int var12 = 0; var12 < var11; var12++) {
            this.categoryRows.add((RiptideTitleScreen.CategoryRow)var4.get(var12));
         }

         this.categoryRows.add(new RiptideTitleScreen.CategoryRow("+" + (var4.size() - var11) + " MORE", -1, -1));
      }
   }

   private static String modVersion() {
      String var0 = RiptideDiscordLogin.modVersionString();
      return var0 != null && !var0.isBlank() ? var0 : "unknown";
   }

   private float introProgress(int var1) {
      float var2 = (float)(System.nanoTime() - this.openedAtNanos) / 1000000.0F - var1;
      if (var2 <= 0.0F) {
         return 0.0F;
      } else {
         float var3 = Math.min(1.0F, var2 / 340.0F);
         return 1.0F - (1.0F - var3) * (1.0F - var3) * (1.0F - var3);
      }
   }

   private static int introOffset(float var0) {
      return Math.round((1.0F - var0) * 5.0F);
   }

   private static int fade(int var0, float var1) {
      return UiRenderer.applyAlpha(var0, var1);
   }

   private void renderTitleCard(GuiGraphicsExtractor var1) {
      float var2 = this.introProgress(0);
      if (!(var2 <= 0.0F)) {
         float var3 = Math.min(1.0F, (this.cardW - 16) / 516.0F);
         int var4 = Math.max(1, Math.round(516.0F * var3));
         int var5 = Math.max(1, Math.round(144.0F * var3));
         int var6 = UiSizing.centerInside(this.cardX, this.cardW, var4);
         int var7 = this.cardY + introOffset(var2) + Math.max(0, (this.cardH - var5) / 2);
         var1.blit(
            RenderPipelines.GUI_TEXTURED,
            RiptideThemeTextures.recolored(LOGO, RiptideTheme.Channel.ACCENT),
            var6,
            var7,
            0.0F,
            0.0F,
            var4,
            var5,
            516,
            144,
            516,
            144,
            ARGB.white(var2)
         );
      }
   }

   private void renderSupportPanel(GuiGraphicsExtractor var1) {
   }

   private void renderStatusPanel(GuiGraphicsExtractor var1) {
      float var2 = this.introProgress(200);
      if (!(var2 <= 0.0F)) {
         int var3 = this.statusX;
         int var4 = this.statusY + introOffset(var2);
         this.renderPanelChrome(var1, var3, var4, this.statusW, this.statusH, null, var2);
         int var5 = var4 + 5;
         int var6 = fade(this.theme.color(UiTone.ACCENT), var2 * 0.85F);
         int var7 = fade(this.theme.color(UiTone.BODY), var2);

         for (RiptideTitleScreen.StatusRow var9 : this.statusRows) {
            UiText.draw(var1, this.font, var9.label(), UiAssets.FONT_LABEL, var6, var3 + 6, var5, false);
            int var10 = var3 + 12 + this.statusLabelW;
            String var11 = this.fitText(var9.value().get(), var3 + this.statusW - 6 - var10, UiAssets.FONT_BODY, var7);
            UiText.draw(var1, this.font, var11, UiAssets.FONT_BODY, var7, var10, var5 + 1, false);
            var5 += 11;
         }
      }
   }

   private void renderModulesPanel(GuiGraphicsExtractor var1) {
      float var2 = this.introProgress(260);
      if (!(var2 <= 0.0F)) {
         int var3 = this.modulesX;
         int var4 = this.modulesY + introOffset(var2);
         this.renderPanelChrome(var1, var3, var4, this.modulesW, this.modulesH, null, var2);
         int var5 = fade(this.theme.color(UiTone.ACCENT), var2);
         int var6 = fade(this.theme.color(UiTone.MUTED), var2);
         int var7 = fade(this.theme.color(UiTone.BODY), var2);
         int var8 = var4 + 5;
         String var9 = Integer.toString(this.moduleActiveCount);
         UiText.draw(var1, this.font, var9, UiAssets.FONT_TITLE, var5, var3 + 7, var8, false);
         int var10 = UiText.width(this.font, var9, UiAssets.FONT_TITLE, var5);
         UiText.draw(
            var1,
            this.font,
            " / " + this.moduleTotalCount + " ACTIVE",
            UiAssets.FONT_BODY,
            var6,
            var3 + 7 + var10 + 3,
            var8 + (UiText.fontHeight(UiAssets.FONT_TITLE) - UiText.fontHeight(UiAssets.FONT_BODY)),
            false
         );
         UiRenderer.horizontalEdge(var1, var3 + 5, var8 + 15, this.modulesW - 10, fade(this.theme.borderSoft(), var2));
         int var11 = var8 + 15 + 3;

         for (RiptideTitleScreen.CategoryRow var13 : this.categoryRows) {
            if (var13.total() < 0) {
               UiText.draw(var1, this.font, var13.label(), UiAssets.FONT_BODY, var6, var3 + 6, var11 + 2, false);
            } else {
               String var14 = var13.active() + "/" + var13.total();
               int var15 = var13.active() > 0 ? var5 : var6;
               int var16 = UiText.width(this.font, var14, UiAssets.FONT_BODY, var15);
               String var17 = this.fitText(var13.label(), this.modulesW - var16 - 20, UiAssets.FONT_BODY, var7);
               UiText.draw(var1, this.font, var17, UiAssets.FONT_BODY, var7, var3 + 6, var11 + 2, false);
               UiText.draw(var1, this.font, var14, UiAssets.FONT_BODY, var15, var3 + this.modulesW - 6 - var16, var11 + 2, false);
            }

            var11 += 12;
         }
      }
   }

   private void renderPanelChrome(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, String var6, float var7) {
      UiRenderer.frame(var1, UiBounds.of(var2, var3, var4, var5), fade(this.theme.windowFill(), var7), fade(this.theme.borderSoft(), var7));
      int var8 = fade(this.theme.color(UiTone.ACCENT), var7);
      UiRenderer.horizontalEdge(var1, var2 + 1, var3 + 1, 5, var8);
      UiRenderer.verticalEdge(var1, var2 + 1, var3 + 1, 5, var8);
      UiRenderer.horizontalEdge(var1, var2 + var4 - 6, var3 + 1, 5, var8);
      UiRenderer.verticalEdge(var1, var2 + var4 - 2, var3 + 1, 5, var8);
      UiRenderer.horizontalEdge(var1, var2 + 1, var3 + var5 - 2, 5, var8);
      UiRenderer.verticalEdge(var1, var2 + 1, var3 + var5 - 6, 5, var8);
      UiRenderer.horizontalEdge(var1, var2 + var4 - 6, var3 + var5 - 2, 5, var8);
      UiRenderer.verticalEdge(var1, var2 + var4 - 2, var3 + var5 - 6, 5, var8);
      if (var6 != null && !var6.isBlank()) {
         String var9 = "/// " + var6 + " ///";
         int var10 = UiText.width(this.font, var9, UiAssets.FONT_LABEL, var8);
         UiText.draw(var1, this.font, var9, UiAssets.FONT_LABEL, var8, var2 + (var4 - var10) / 2, var3 + 4, false);
      }
   }

   private void drawHudText(GuiGraphicsExtractor var1, String var2, int var3, int var4, int var5, Identifier var6) {
      UiText.draw(var1, this.font, var2 == null ? "" : var2, var6, var5, var3, var4, false);
   }

   private void renderModCount(GuiGraphicsExtractor var1) {
      float var2 = this.introProgress(320);
      if (!(var2 <= 0.0F)) {
         Identifier var3 = UiAssets.FONT_BODY;
         int var4 = fade(this.theme.color(UiTone.MUTED), var2);
         int var5 = RiptideUiScale.getVirtualScreenHeight() - UiText.fontHeight(var3) - 3;
         UiText.draw(var1, this.font, this.modCountText, var3, var4, 4, var5, false);
      }
   }

   private void renderSpotifyStrip(GuiGraphicsExtractor var1) {
      if (RiptideHudManager.spotifyMenuStrip("spotify")) {
         int var2 = RiptideUiScale.getVirtualScreenWidth();
         int var3 = RiptideUiScale.getVirtualScreenHeight();
         if (var3 >= 200) {
            RiptideSpotify.setWanted();
            RiptideSpotify.Snapshot var4 = RiptideSpotify.snapshot();
            boolean var5 = var4 != null && var4.status() == RiptideSpotify.Status.PLAYING;
            boolean var6 = var4 != null && var4.status() == RiptideSpotify.Status.PAUSED;
            if (var5 || var6) {
               String var7 = RiptideMarquee.trackText(var4);
               if (!var7.isEmpty()) {
                  float var8 = this.introProgress(320);
                  if (!(var8 <= 0.0F)) {
                     Identifier var9 = UiAssets.FONT_BODY;
                     int var10 = fade(this.theme.color(UiTone.MUTED), var8);
                     int var11 = UiText.width(this.font, var7, var9, var10);
                     RiptideHudManager.SpotifyArt var12 = RiptideHudManager.spotifyArt(var4);
                     int var13 = var12 != null ? 16 : 0;
                     int var14 = var12 != null ? 4 : 0;
                     int var15 = 4 + UiText.width(this.font, this.modCountText, var9, 0);
                     int var16 = Math.min(var2 / 2 - var15, this.utilityRowX - var2 / 2) - 4;
                     int var17 = var13 + var14 + var11;
                     int var18 = Math.min(var17, 2 * Math.max(0, var16));
                     if (var18 >= 24) {
                        int var19 = var18 - var13 - var14;
                        if (var19 < 16) {
                           var12 = null;
                           var13 = 0;
                           var14 = 0;
                           var19 = var18;
                        }

                        int var20 = var13 + var14 + Math.min(var11, var19);
                        int var21 = var2 / 2 - var20 / 2;
                        int var22 = UiText.fontHeight(var9);
                        int var23 = var3 - 4;
                        int var24 = var23 - 2 - var22;
                        if (var24 >= this.centerStackBottom + 4) {
                           long var25 = var4.updatedAtMs() + 1200L;
                           int var27 = RiptideHudManager.spotifyScrollSpeed("spotify");
                           long var28 = System.currentTimeMillis();
                           if (var12 != null) {
                              int var30 = var24 + (var22 - 16) / 2;
                              var1.blit(
                                 RenderPipelines.GUI_TEXTURED,
                                 var12.id(),
                                 var21,
                                 var30,
                                 0.0F,
                                 0.0F,
                                 16,
                                 16,
                                 var12.width(),
                                 var12.height(),
                                 var12.width(),
                                 var12.height(),
                                 fade(-1, var8)
                              );
                           }

                           RiptideMarquee.drawMarquee(var1, this.font, var7, var9, var10, var21 + var13 + var14, var24, var19, false, var28, var25, var27);
                           double var33 = RiptideHudManager.spotifyProgressFor(var4);
                           UiText.fill(var1, var21, var23, var21 + var20, var23 + 2, fade(this.theme.color(UiTone.MUTED), var8 * 0.22F));
                           int var32 = (int)Math.round(var20 * var33);
                           if (var32 > 0) {
                              UiText.fill(var1, var21, var23, var21 + var32, var23 + 2, fade(this.theme.color(UiTone.ACCENT), var8 * 0.9F));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private String fitText(String var1, int var2, Identifier var3, int var4) {
      if (var1 == null) {
         return "";
      } else if (UiText.width(this.font, var1, var3, var4) <= var2) {
         return var1;
      } else {
         String var5 = var1;

         while (var5.length() > 1 && UiText.width(this.font, var5 + ".", var3, var4) > var2) {
            var5 = var5.substring(0, var5.length() - 1);
         }

         return var5 + ".";
      }
   }

   private void renderMeteorCredits(GuiGraphicsExtractor var1) {
      List var2 = this.getMeteorCredits();
      if (!var2.isEmpty()) {
         float var3 = this.introProgress(320);
         if (!(var3 <= 0.0F)) {
            int var4 = UiText.fontHeight(UiAssets.FONT_BODY) + 2;
            int var5 = RiptideUiScale.getVirtualScreenHeight() - UiText.fontHeight(UiAssets.FONT_BODY) - 5 - var2.size() * var4;

            for (RiptideTitleScreen.MeteorCreditLine var7 : var2) {
               int var8 = 4;

               for (RiptideTitleScreen.MeteorCreditSegment var10 : var7.segments()) {
                  if (!var10.text().isEmpty()) {
                     int var11 = fade(var10.color(), var3);
                     UiText.draw(var1, this.font, var10.text(), UiAssets.FONT_BODY, var11, var8, var5, false);
                     var8 += UiText.width(this.font, var10.text(), UiAssets.FONT_BODY, var11);
                  }
               }

               var5 += var4;
            }
         }
      }
   }

   private List<RiptideTitleScreen.MeteorCreditLine> getMeteorCredits() {
      if (this.meteorCredits.isEmpty() && !this.meteorCreditsLoadFailed) {
         if (!FabricLoader.getInstance().isModLoaded("meteor-client")) {
            return this.meteorCredits;
         } else {
            try {
               Class var1 = Class.forName("meteordevelopment.meteorclient.addons.AddonManager");
               Field var2 = var1.getField("ADDONS");
               if (!(var2.get(null) instanceof Iterable var3)) {
                  return this.meteorCredits;
               }

               ArrayList var9 = new ArrayList();

               for (Object var6 : var3) {
                  RiptideTitleScreen.MeteorCreditLine var7 = meteorCreditLine(var6);
                  if (var7 != null) {
                     var9.add(var7);
                  }
               }

               this.meteorCredits = List.copyOf(var9);
            } catch (RuntimeException | ReflectiveOperationException var8) {
               this.meteorCreditsLoadFailed = true;
            }

            return this.meteorCredits;
         }
      } else {
         return this.meteorCredits;
      }
   }

   private static RiptideTitleScreen.MeteorCreditLine meteorCreditLine(Object var0) throws ReflectiveOperationException {
      if (var0 == null) {
         return null;
      } else {
         Class var1 = var0.getClass();
         String var2 = stringField(var1, var0, "name");
         String[] var3 = authorsField(var1, var0);
         if (var2 != null && !var2.isBlank() && var3.length != 0) {
            int var4 = addonColor(var1, var0);
            ArrayList var5 = new ArrayList();
            var5.add(new RiptideTitleScreen.MeteorCreditSegment(var2, var4));
            var5.add(new RiptideTitleScreen.MeteorCreditSegment(" by ", -5592406));

            for (int var6 = 0; var6 < var3.length; var6++) {
               if (var6 > 0) {
                  var5.add(new RiptideTitleScreen.MeteorCreditSegment(var6 == var3.length - 1 ? " & " : ", ", -5592406));
               }

               var5.add(new RiptideTitleScreen.MeteorCreditSegment(var3[var6], -1));
            }

            return new RiptideTitleScreen.MeteorCreditLine(List.copyOf(var5));
         } else {
            return null;
         }
      }
   }

   private static String stringField(Class<?> var0, Object var1, String var2) throws ReflectiveOperationException {
      return var0.getField(var2).get(var1) instanceof String var3 ? var3 : null;
   }

   private static String[] authorsField(Class<?> var0, Object var1) throws ReflectiveOperationException {
      return var0.getField("authors").get(var1) instanceof String[] var2 ? var2 : new String[0];
   }

   private static int addonColor(Class<?> var0, Object var1) throws ReflectiveOperationException {
      Object var2 = var0.getField("color").get(var1);
      if (var2 == null) {
         return -1;
      } else {
         Method var3 = var2.getClass().getMethod("getPacked");
         return var3.invoke(var2) instanceof Integer var4 ? var4 : -1;
      }
   }

   private void openModMenu() {
      try {
         Class var1 = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
         Method var2 = var1.getMethod("createModsScreen", Screen.class);
         Screen var3 = (Screen)var2.invoke(null, this);
         this.minecraft.gui.setScreen(var3);
      } catch (Exception var4) {
      }
   }

   private static Identifier buttonText(String var0) {
      return Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/button_text/" + var0 + ".png");
   }

   private static String createModCountText() {
      int var0 = FabricLoader.getInstance().getAllMods().size();
      return var0 + (var0 == 1 ? " Mod" : " Mods");
   }

   private void openEssential() {
      try {
         Class var1 = Class.forName("gg.essential.gui.modals.QuickAccessModal");
         Object var2 = var1.getDeclaredField("Companion").get(null);
         Method var3 = var2.getClass().getDeclaredMethod("open");
         var3.setAccessible(true);
         var3.invoke(var2);
      } catch (Exception var4) {
      }
   }

   private void openReplayViewer() {
      try {
         Class var1 = Class.forName("com.replaymod.replay.ReplayModReplay");
         Field var2 = var1.getField("instance");
         Object var3 = var2.get(null);
         if (var3 == null) {
            return;
         }

         Class var4 = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer");
         Constructor var5 = var4.getConstructor(var1);
         Object var6 = var5.newInstance(var3);
         Method var7 = findNoArgMethod(var4, "display");
         if (var7 == null) {
            return;
         }

         var7.setAccessible(true);
         var7.invoke(var6);
      } catch (Exception var8) {
      }
   }

   private void openFlashback() {
      try {
         Class var1 = Class.forName("com.moulberry.flashback.screen.select_replay.SelectReplayScreen");
         Constructor var2 = var1.getConstructor(Screen.class);
         if (var2.newInstance(this) instanceof Screen var3) {
            this.minecraft.gui.setScreen(var3);
         }
      } catch (Exception var5) {
      }
   }

   private static Method findNoArgMethod(Class<?> var0, String var1) {
      for (Class var2 = var0; var2 != null; var2 = var2.getSuperclass()) {
         for (Method var6 : var2.getDeclaredMethods()) {
            if (var6.getName().equals(var1) && var6.getParameterCount() == 0) {
               return var6;
            }
         }
      }

      return null;
   }

   private void renderCustomTooltip(GuiGraphicsExtractor var1, Component var2, float var3, float var4) {
      riptide.gui.vanillaui.components.Tooltip.render(
         UiContexts.overlay(var1, this.font, Math.round(var3), Math.round(var4)), var2.getString(), Math.round(var3), Math.round(var4), 220
      );
   }

   private String serverCountBadge() {
      int var1 = this.savedServerCount();
      return var1 < 0 ? null : Integer.toString(var1);
   }

   private int savedServerCount() {
      long var1 = System.currentTimeMillis();
      if (this.cachedServerCount >= 0 && var1 - this.serverCountCheckedAt < 2000L) {
         return this.cachedServerCount;
      } else {
         this.serverCountCheckedAt = var1;

         try {
            ServerList var3 = new ServerList(this.minecraft);
            var3.load();
            this.cachedServerCount = var3.size();
         } catch (RuntimeException var4) {
            this.cachedServerCount = -1;
         }

         return this.cachedServerCount;
      }
   }

   private record CategoryRow(String label, int active, int total) {
   }

   private final class MenuButton {
      private final int x;
      private final int y;
      private final int width;
      private final int height;
      private final Component label;
      private final boolean enabled;
      private final Runnable onPress;
      private Identifier icon;
      private int iconTextureSize;
      private Identifier iconSprite;
      private Identifier leftIcon;
      private int leftIconTextureWidth;
      private int leftIconTextureHeight;
      private int leftIconDrawSize;
      private Identifier labelTexture;
      private int labelTextureWidth;
      private int labelTextureHeight;
      private int labelDrawWidth;
      private int labelDrawHeight;
      private Component tooltip;
      private String rightBadge;
      private int rowIndex;
      private boolean utilityCell;
      private String utilitySubtitle;
      private boolean supportRow;
      private int introDelayMs;
      private boolean introSlide;

      private MenuButton(int nullx, int nullxx, int nullxxx, int nullxxxx, Component nullxxxxx, boolean nullxxxxxx, Runnable nullxxxxxxx) {
         Objects.requireNonNull(RiptideTitleScreen.this);
         super();
         this.iconTextureSize = 32;
         this.leftIconTextureWidth = 32;
         this.leftIconTextureHeight = 32;
         this.leftIconDrawSize = 14;
         this.rowIndex = -1;
         this.utilitySubtitle = "";
         this.introDelayMs = 320;
         this.x = nullx;
         this.y = nullxx;
         this.width = nullxxx;
         this.height = nullxxxx;
         this.label = nullxxxxx;
         this.enabled = nullxxxxxx;
         this.onPress = nullxxxxxxx;
      }

      private RiptideTitleScreen.MenuButton withTooltip(Component var1) {
         this.tooltip = var1;
         return this;
      }

      private RiptideTitleScreen.MenuButton withRightBadge(String var1) {
         this.rightBadge = var1;
         return this;
      }

      private RiptideTitleScreen.MenuButton withIcon(Identifier var1) {
         return this.withIcon(var1, 32);
      }

      private RiptideTitleScreen.MenuButton withIcon(Identifier var1, int var2) {
         this.icon = var1;
         this.iconTextureSize = Math.max(1, var2);
         return this;
      }

      private RiptideTitleScreen.MenuButton withSprite(Identifier var1) {
         this.iconSprite = var1;
         return this;
      }

      private RiptideTitleScreen.MenuButton withLeftIcon(Identifier var1, int var2, int var3, int var4) {
         this.leftIcon = var1;
         this.leftIconTextureWidth = Math.max(1, var2);
         this.leftIconTextureHeight = Math.max(1, var3);
         this.leftIconDrawSize = Math.max(1, var4);
         return this;
      }

      private RiptideTitleScreen.MenuButton withLabelTexture(Identifier var1, int var2, int var3, int var4, int var5) {
         this.labelTexture = var1;
         this.labelTextureWidth = var2;
         this.labelTextureHeight = var3;
         this.labelDrawWidth = var4;
         this.labelDrawHeight = var5;
         return this;
      }

      private RiptideTitleScreen.MenuButton asMainRow(int var1) {
         this.rowIndex = var1;
         if (var1 > 0) {
            this.introDelayMs = 60 + var1 * 50;
         }

         return this;
      }

      private RiptideTitleScreen.MenuButton asSupportRow() {
         this.supportRow = true;
         this.introDelayMs = 140;
         this.introSlide = true;
         return this;
      }

      private RiptideTitleScreen.MenuButton withIntro(int var1, boolean var2) {
         this.introDelayMs = var1;
         this.introSlide = var2;
         return this;
      }

      private RiptideTitleScreen.MenuButton asUtility(String var1) {
         this.utilityCell = true;
         this.utilitySubtitle = var1 == null ? "" : var1;
         return this;
      }

      private boolean contains(float var1, float var2) {
         return var1 >= this.x && var2 >= this.y && var1 < this.x + this.width && var2 < this.y + this.height;
      }

      private boolean click(float var1, float var2) {
         if (!this.contains(var1, var2)) {
            return false;
         } else if (RiptideTitleScreen.this.introProgress(this.introDelayMs) < 1.0F) {
            return false;
         } else if (!this.enabled) {
            return true;
         } else {
            RiptideTitleScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(RiptideTitleScreen.BUTTON_CLICK_SOUND, 1.0F, 0.7F));
            if (this.onPress != null) {
               this.onPress.run();
            }

            return true;
         }
      }

      private void render(GuiGraphicsExtractor var1, float var2, float var3, float var4) {
         float var5 = RiptideTitleScreen.this.introProgress(this.introDelayMs);
         if (!(var5 <= 0.0F)) {
            if (this.utilityCell) {
               this.renderUtility(var1, var2, var3, var5);
            } else if (this.supportRow) {
               this.renderSupportRow(var1, var2, var3, var5);
            } else {
               this.renderMainRow(var1, var2, var3, var5);
            }
         }
      }

      private void renderMainRow(GuiGraphicsExtractor var1, float var2, float var3, float var4) {
         boolean var5 = this.enabled && this.contains(var2, var3);
         int var6 = this.y + (this.introSlide ? RiptideTitleScreen.introOffset(var4) : 0);
         int var7 = RiptideTheme.recolor(this.enabled ? -1721357268 : 1430596138, RiptideTheme.Channel.OUTLINE);
         UiRenderer.frame(
            var1,
            UiBounds.of(this.x, var6, this.width, this.height),
            RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -1207302650 : -1873801200, RiptideTheme.Channel.BUTTON), var4),
            RiptideTitleScreen.fade(var7, var4)
         );
         float var8 = HoverFades.get(HoverFades.key(UiBounds.of(this.x, this.y, this.width, this.height)), var5);
         if (var8 > 0.001F) {
            UiRenderer.rect(var1, UiBounds.of(this.x + 1, var6 + 1, this.width - 2, this.height - 2), Math.round(20.0F * var8 * var4) << 24 | 16777215);
            int var9 = RiptideTitleScreen.this.theme.color(UiTone.ACCENT);
            UiRenderer.outline(var1, UiBounds.of(this.x, var6, this.width, this.height), RiptideTitleScreen.fade(var9, var8 * 0.8F * var4));
            UiRenderer.rect(var1, UiBounds.of(this.x, var6, 2, this.height), RiptideTitleScreen.fade(var9, var8 * var4));
            int var10 = Math.round(var8 * 3.0F);
            UiRenderer.chevron(var1, UiBounds.of(this.x + 4 + var10, var6 + (this.height - 8) / 2, 7, 8), false, RiptideTitleScreen.fade(var9, var8 * var4));
         }

         String var20 = this.rightBadge != null && !this.rightBadge.isBlank() ? this.rightBadge : null;
         if (this.labelTexture != null && this.labelTextureWidth > 0 && this.labelTextureHeight > 0 && this.labelDrawWidth > 0 && this.labelDrawHeight > 0) {
            int var22 = Math.max(1, this.width - 8);
            int var24 = Math.max(1, this.height <= 18 ? this.height - 4 : this.height - 8);
            float var26 = Math.min(1.0F, Math.min((float)var22 / this.labelDrawWidth, (float)var24 / this.labelDrawHeight));
            int var28 = Math.max(1, Math.round(this.labelDrawWidth * var26));
            int var30 = Math.max(1, Math.round(this.labelDrawHeight * var26));
            int var32 = this.x + (this.width - var28) / 2;
            int var34 = var6 + (this.height - var30) / 2;
            int var35 = RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -528658 : -8362651, RiptideTheme.Channel.TEXT), var4);
            var1.blit(
               RenderPipelines.GUI_TEXTURED,
               this.labelTexture,
               var32,
               var34,
               0.0F,
               0.0F,
               var28,
               var30,
               this.labelTextureWidth,
               this.labelTextureHeight,
               this.labelTextureWidth,
               this.labelTextureHeight,
               var35
            );
         } else if (this.label != null) {
            String var21 = this.label.getString();
            int var11 = RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -528658 : -8362651, RiptideTheme.Channel.TEXT), var4);
            Identifier var12 = UiAssets.FONT_LABEL;
            int var13 = UiSizing.alignTextY(var6, this.height, UiText.fontHeight(var12), 1);
            String var14 = var21;
            int var15;
            if (this.leftIcon != null) {
               int var16 = Math.min(this.leftIconDrawSize, Math.max(1, Math.min(this.height - 2, this.width - 12)));
               int var17 = this.x + 3;
               int var18 = var6 + (this.height - var16) / 2;
               var1.blit(
                  RenderPipelines.GUI_TEXTURED,
                  RiptideThemeTextures.whitened(this.leftIcon),
                  var17,
                  var18,
                  0.0F,
                  0.0F,
                  var16,
                  var16,
                  this.leftIconTextureWidth,
                  this.leftIconTextureHeight,
                  this.leftIconTextureWidth,
                  this.leftIconTextureHeight,
                  RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -528658 : -8362651, RiptideTheme.Channel.TEXT), var4)
               );
               var15 = var17 + var16 + 4;
               int var19 = Math.max(1, this.x + this.width - 4 - var15);
               var14 = UiText.trimToWidth(RiptideTitleScreen.this.font, var21, var19, var12, var11);
            } else {
               int var33 = UiText.width(RiptideTitleScreen.this.font, var21, var12, var11);
               var15 = this.x + (this.width - var33) / 2;
            }

            UiText.draw(var1, RiptideTitleScreen.this.font, var14, var12, var11, var15, var13, false);
         }

         if (var20 != null && !var20.isBlank()) {
            Identifier var23 = UiAssets.FONT_LABEL;
            int var25 = RiptideTitleScreen.fade(this.enabled ? RiptideTheme.recolor(-11052, RiptideTheme.Channel.ACCENT) : -8362651, var4);
            int var27 = UiText.width(RiptideTitleScreen.this.font, var20, var23, var25);
            int var29 = this.x + this.width - var27 - 6;
            int var31 = UiSizing.alignTextY(var6, this.height, UiText.fontHeight(var23), 1);
            RiptideTitleScreen.this.drawHudText(var1, var20, var29, var31, var25, var23);
         }
      }

      private void renderSupportRow(GuiGraphicsExtractor var1, float var2, float var3, float var4) {
         boolean var5 = this.enabled && this.contains(var2, var3);
         int var6 = this.y + (this.introSlide ? RiptideTitleScreen.introOffset(var4) : 0);
         int var7 = RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE);
         UiRenderer.frame(
            var1,
            UiBounds.of(this.x, var6, this.width, this.height),
            RiptideTitleScreen.fade(RiptideTheme.recolor(-1878391290, RiptideTheme.Channel.BUTTON), var4),
            RiptideTitleScreen.fade(var7, var4)
         );
         float var8 = HoverFades.get(HoverFades.key(UiBounds.of(this.x, this.y, this.width, this.height)), var5);
         if (var8 > 0.001F) {
            UiRenderer.rect(var1, UiBounds.of(this.x + 1, var6 + 1, this.width - 2, this.height - 2), Math.round(20.0F * var8 * var4) << 24 | 16777215);
            UiRenderer.outline(
               var1,
               UiBounds.of(this.x, var6, this.width, this.height),
               RiptideTitleScreen.fade(RiptideTitleScreen.this.theme.color(UiTone.ACCENT), var8 * 0.8F * var4)
            );
         }

         if (this.leftIcon != null) {
            int var9 = Math.min(this.leftIconDrawSize, Math.max(1, this.height - 6));
            int var10 = this.x + 5;
            int var11 = var6 + (this.height - var9) / 2;
            int var12 = RiptideTitleScreen.fade(RiptideTheme.recolor(-528658, RiptideTheme.Channel.TEXT), var4);
            var1.blit(
               RenderPipelines.GUI_TEXTURED,
               RiptideThemeTextures.whitened(this.leftIcon),
               var10,
               var11,
               0.0F,
               0.0F,
               var9,
               var9,
               this.leftIconTextureWidth,
               this.leftIconTextureHeight,
               this.leftIconTextureWidth,
               this.leftIconTextureHeight,
               var12
            );
            if (var8 > 0.001F) {
               var1.blit(
                  RenderPipelines.GUI_TEXTURED,
                  RiptideThemeTextures.whitened(this.leftIcon),
                  var10,
                  var11,
                  0.0F,
                  0.0F,
                  var9,
                  var9,
                  this.leftIconTextureWidth,
                  this.leftIconTextureHeight,
                  this.leftIconTextureWidth,
                  this.leftIconTextureHeight,
                  RiptideTitleScreen.fade(RiptideTitleScreen.this.theme.color(UiTone.ACCENT), var8 * var4)
               );
            }
         }

         int var14 = this.x + 5 + Math.min(this.leftIconDrawSize, Math.max(1, this.height - 6)) + 5;
         int var15 = Math.max(1, this.x + this.width - 4 - var14);
         int var16 = RiptideTitleScreen.fade(RiptideTitleScreen.this.theme.color(UiTone.BODY), var4);
         String var17 = UiText.trimToWidth(RiptideTitleScreen.this.font, this.label.getString(), var15, UiAssets.FONT_LABEL, var16);
         int var13 = UiSizing.alignTextY(var6, this.height, UiText.fontHeight(UiAssets.FONT_LABEL), 1);
         UiText.draw(var1, RiptideTitleScreen.this.font, var17, UiAssets.FONT_LABEL, var16, var14, var13, false);
      }

      private void renderUtility(GuiGraphicsExtractor var1, float var2, float var3, float var4) {
         boolean var5 = this.enabled && this.contains(var2, var3);
         int var6 = RiptideTheme.recolor(this.enabled ? -1721357268 : 1430596138, RiptideTheme.Channel.OUTLINE);
         UiRenderer.frame(
            var1,
            UiBounds.of(this.x, this.y, this.width, this.height),
            RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -1727527675 : 1716523024, RiptideTheme.Channel.BUTTON), var4),
            RiptideTitleScreen.fade(var6, var4)
         );
         float var7 = HoverFades.get(HoverFades.key(UiBounds.of(this.x, this.y, this.width, this.height)), var5);
         if (var7 > 0.001F) {
            UiRenderer.rect(var1, UiBounds.of(this.x + 1, this.y + 1, this.width - 2, this.height - 2), Math.round(20.0F * var7 * var4) << 24 | 16777215);
         }

         if (this.iconSprite != null) {
            int var8 = Math.min(15, Math.max(12, this.height - 6));
            int var9 = this.x + (this.width - var8) / 2;
            int var10 = this.y + (this.height - var8) / 2;
            float var11 = (this.enabled ? 1.0F : 0.45F) * var4;
            var1.blitSprite(RenderPipelines.GUI_TEXTURED, this.iconSprite, var9, var10, var8, var8, var11);
         } else if (this.icon != null) {
            int var12 = Math.min(16, Math.max(12, this.height - 7));
            int var13 = this.x + (this.width - var12) / 2;
            int var14 = this.y + (this.height - var12) / 2;
            int var15 = RiptideTitleScreen.fade(RiptideTheme.recolor(this.enabled ? -528658 : -8362651, RiptideTheme.Channel.TEXT), var4);
            var1.blit(
               RenderPipelines.GUI_TEXTURED,
               RiptideThemeTextures.whitened(this.icon),
               var13,
               var14,
               0.0F,
               0.0F,
               var12,
               var12,
               this.iconTextureSize,
               this.iconTextureSize,
               this.iconTextureSize,
               this.iconTextureSize,
               var15
            );
         }
      }
   }

   private record MeteorCreditLine(List<RiptideTitleScreen.MeteorCreditSegment> segments) {
      private int width(Font var1) {
         int var2 = 0;

         for (RiptideTitleScreen.MeteorCreditSegment var4 : this.segments) {
            var2 += UiText.width(var1, var4.text(), UiAssets.FONT_BODY, var4.color());
         }

         return var2;
      }
   }

   private record MeteorCreditSegment(String text, int color) {
   }

   private record StatusRow(String label, Supplier<String> value) {
   }

   private record UtilityButtonSpec(String title, String subtitle, Identifier icon, Identifier sprite, int iconTextureSize, Runnable onPress, Component tooltip) {
   }
}
