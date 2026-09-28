package riptide.gui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.util.UndashedUuid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultiLineEditBox.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model.Simple;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.PlayerSkinRenderCache.RenderInfo;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountGenerator;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountSessionSwitcher;
import riptide.util.RiptideAccountType;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideConfig;
import riptide.util.RiptideHttp;
import riptide.util.RiptideMicrosoftLogin;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiIcons;
import riptide.util.RiptideUiScale;

public class RiptideAccountsScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int BORDER_DEFAULT = -10519297;
   private static final int DEFAULT_COLOR = -7429889;
   private static final int PANEL_WIDTH = 520;
   private static final int PANEL_MARGIN = 12;
   private static final int ROW_HEIGHT = 24;
   private static final int FORM_WIDTH = 292;
   private static final int PREVIEW_WIDTH = 200;
   private static final int PANEL_GAP = 8;
   private static final int PROVIDER_BUTTON_WIDTH = 66;
   private static final int TOP_PANEL_Y = 20;
   private static final int TOP_PANEL_HEIGHT = 178;
   private static final int LIST_TOP = 204;
   private static final int LIST_HEADER_HEIGHT = 22;
   private static final int LIST_BOTTOM_MARGIN = 12;
   private static final int FIELD_Y = 88;
   private static final int ACTION_Y = 114;
   private static final int SEARCH_Y = 140;
   private static final int FILTER_Y = 174;
   private static final int MAX_SKIN_LOOKUPS = 10;
   private static final int LIST_SCROLLBAR_WIDTH = 4;
   private static final int LIST_SCROLLBAR_GUTTER = 12;
   private static final int LIST_CHECK_BUTTON_WIDTH = 64;
   private static final int LIST_CLEAR_BUTTON_WIDTH = 96;
   private static final int LIST_HEADER_BUTTON_GAP = 4;
   private static final int LIST_HEADER_BUTTON_RESERVE = 328;
   private static final long CANCEL_BUTTON_DELAY_MS = 5000L;
   private static final String TEXTURES_PROPERTY = "textures";
   private static final int SHARE_BUTTON_OUTLINE = -65536;
   private static final int SHARE_GLYPH_PAGE = -15395557;
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<RiptideAccountsScreen.AccountRow> accountRows = new ArrayList<>();
   private final Map<String, RiptideAccountsScreen.SkinLookup> skinLookups = new LinkedHashMap<>(16, 0.75F, true);
   private final ScrollState savedListScroll = new ScrollState();
   private RiptideAccountType categoryFilter = null;
   private EditBox labelField;
   private EditBox tokenField;
   private EditBox searchField;
   private RiptideAccount renamingAccount;
   private Simple widePlayerModel;
   private Simple slimPlayerModel;
   private RiptideAccountType type = RiptideAccountType.Cracked;
   private RiptideAccount selectedAccount;
   private String searchQuery = "";
   private String pendingSearchQuery = "";
   private boolean searchDirty;
   private RiptideAccountsScreen.Operation operation = RiptideAccountsScreen.Operation.NONE;
   private int operationId;
   private long operationStartedAtMs;
   private Future<?> operationTask;
   private CompactOverlayButton cancelOperationButton;
   private CompactOverlayButton checkButton;
   private int savedListScrollOffset;
   private boolean accountScrollbarDragging;
   private int accountScrollbarGrabOffset;
   private boolean previewDragging;
   private float previewRotationX = -5.0F;
   private float previewRotationY = 30.0F;
   private double previewAutoRotationStartTime = Blaze3D.getTime();
   private double lastPreviewMouseX;
   private double lastPreviewMouseY;
   private boolean accountSnapshotDirty = true;
   private long accountSnapshotRevision;
   private List<RiptideAccount> cachedAccountSnapshot = List.of();
   private long cachedFilteredRevision = Long.MIN_VALUE;
   private String cachedAccountQuery = "";
   private int cachedAccountFilterMask = Integer.MIN_VALUE;
   private List<RiptideAccount> cachedFilteredAccounts = List.of();
   private List<RiptideAccount> cachedDisplaySource;
   private List<RiptideAccountsScreen.DisplayAccountRow> cachedDisplayAccounts = List.of();
   private static final int POPUP_NONE = 0;
   private static final int POPUP_GENERATOR = 1;
   private static final int POPUP_CLEAR = 2;
   private int activePopup = 0;
   private int generatorMode;
   private EditBox generatorCountField;
   private EditBox generatorPasswordField;
   private MultiLineEditBox generatorListField;
   private String generatorResult = "";
   private final List<CompactOverlayButton> popupButtons = new ArrayList<>();
   private final List<CompactDropdown> popupDropdowns = new ArrayList<>();
   private volatile boolean generating;

   public RiptideAccountsScreen(Screen parent) {
      super(Component.literal("Accounts"));
      this.parent = parent;
   }

   protected void init() {
      int panelX = this.panelX();
      int fieldY = 88;
      this.labelField = new EditBox(this.font, panelX + 18, fieldY, 256, 18, Component.literal("Username"));
      this.labelField.setHint(Component.literal("Username"));
      this.labelField.setMaxLength(256);
      this.addRenderableWidget(this.labelField);
      this.tokenField = new EditBox(this.font, panelX + 18, fieldY, 256, 18, Component.literal("Token"));
      this.tokenField.setHint(Component.literal("Token"));
      this.tokenField.setMaxLength(4096);
      this.addRenderableWidget(this.tokenField);
      this.searchField = new EditBox(this.font, panelX + 18, 140, 256, 18, Component.literal("Search accounts"));
      this.searchField.setHint(Component.literal("Search nickname..."));
      this.searchField.setMaxLength(64);
      this.searchField.setResponder(value -> {
         this.pendingSearchQuery = safeTrim(value);
         this.searchDirty = true;
      });
      this.addRenderableWidget(this.searchField);
      this.widePlayerModel = new Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RiptideRenderTypes::skinPreview);
      this.slimPlayerModel = new Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RiptideRenderTypes::skinPreview);
      this.generatorCountField = new EditBox(this.font, 0, 0, 58, 16, Component.literal("Count"));
      this.generatorCountField.setHint(Component.literal("Count"));
      this.generatorCountField.setMaxLength(4);
      this.generatorCountField.setValue("10");
      this.generatorPasswordField = new EditBox(this.font, 0, 0, 186, 16, Component.literal("Password"));
      this.generatorPasswordField.setHint(Component.literal("Password"));
      this.generatorPasswordField.setMaxLength(16);
      this.generatorPasswordField.setValue(RiptideConfig.getGlobal().accountGenSharedPassword);
      this.generatorListField = new Builder()
         .setX(0)
         .setY(0)
         .setPlaceholder(Component.literal("Paste names here, one per line or name:password"))
         .build(this.font, 240, 108, Component.literal("Account name list"));
      this.updateInputVisibility();
      this.rebuildButtons();
   }

   public void tick() {
      super.tick();
      if (this.searchDirty) {
         this.searchDirty = false;
         this.searchQuery = this.pendingSearchQuery;
         this.savedListScrollOffset = 0;
         this.savedListScroll.jumpTo(0, 0);
         this.rebuildButtons();
      }
   }

   private void rebuildButtons() {
      this.buttons.clear();
      this.accountRows.clear();
      this.checkButton = null;
      int panelX = this.panelX();
      int formX = panelX + 10;
      this.buttons
         .add(
            CompactOverlayButton.create(10, 10, 76, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(this.parent))
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      if (!this.narrowLayout()) {
         int y = 58;
         this.addProviderButton(formX + 8, y, RiptideAccountType.Cracked, "Cracked");
         this.addProviderButton(formX + 77, y, RiptideAccountType.TheAltening, "Altening");
         this.addProviderButton(formX + 146, y, RiptideAccountType.Session, "Session");
         this.addProviderButton(formX + 215, y, RiptideAccountType.Microsoft, "Microsoft");
         int actionY = this.type == RiptideAccountType.Microsoft ? 84 : 114;
         int addWidth = this.type == RiptideAccountType.Microsoft ? 276 : 116;
         int addHeight = this.type == RiptideAccountType.Microsoft ? 20 : 18;
         CompactOverlayButton add = CompactOverlayButton.create(
               formX + 8, actionY, addWidth, addHeight, Component.literal(this.addButtonLabel()), b -> this.addAccount()
            )
            .setVariant(CompactOverlayButton.Variant.PRIMARY);
         add.active = !this.isBusy();
         this.buttons.add(add);
         if (this.type != RiptideAccountType.Microsoft) {
            CompactOverlayButton clear = CompactOverlayButton.create(formX + 128, 114, 64, 18, Component.literal("Clear"), b -> this.clearFields())
               .setVariant(CompactOverlayButton.Variant.SECONDARY);
            clear.active = !this.isBusy();
            this.buttons.add(clear);
         }

         int cancelX = this.type == RiptideAccountType.Microsoft ? formX + 8 : formX + 196;
         int cancelY = this.type == RiptideAccountType.Microsoft ? 114 : 114;
         int cancelWidth = this.type == RiptideAccountType.Microsoft ? 276 : 88;
         CompactOverlayButton cancel = CompactOverlayButton.create(cancelX, cancelY, cancelWidth, 18, Component.literal("Cancel"), b -> this.cancelOperation())
            .setVariant(CompactOverlayButton.Variant.DANGER);
         cancel.visible = this.shouldShowCancelOperationButton();
         this.cancelOperationButton = cancel;
         this.buttons.add(cancel);
         RiptideAccountType[] filterTypes = new RiptideAccountType[]{
            RiptideAccountType.Cracked, RiptideAccountType.Microsoft, RiptideAccountType.Session, RiptideAccountType.TheAltening, RiptideAccountType.Generated
         };
         String[] filterLabels = new String[]{"Cracked", "Microsoft", "Session", "Altening", "Generated"};

         for (int i = 0; i < filterTypes.length; i++) {
            this.addFilterButton(formX + 8 + i * 56, 174, filterTypes[i], filterLabels[i]);
         }
      }

      if (!this.compactListLayout()) {
         boolean checking = this.operation == RiptideAccountsScreen.Operation.CHECK;
         int checkX = this.rowRight() - 8 - 64;
         CompactOverlayButton check = CompactOverlayButton.create(
            checkX, this.listTop() + 3, 64, 16, Component.literal(checking ? "Checking" : "Check"), b -> this.checkExpiredAccounts()
         );
         check.setVariant(CompactOverlayButton.Variant.SECONDARY);
         check.active = !this.isBusy() && !this.accountSnapshot().isEmpty();
         this.buttons.add(check);
         this.checkButton = check;
         CompactOverlayButton clearExpired = CompactOverlayButton.create(
            checkX - 4 - 96, this.listTop() + 3, 96, 16, Component.literal("Clear Expired"), b -> this.clearExpiredAccounts()
         );
         clearExpired.setVariant(CompactOverlayButton.Variant.DANGER);
         clearExpired.active = !this.isBusy() && this.expiredAccountCount() > 0;
         this.buttons.add(clearExpired);
         CompactOverlayButton generate = CompactOverlayButton.create(
            clearExpired.getX() - 4 - 60, this.listTop() + 3, 60, 16, Component.literal("Generate"), b -> this.openGenerator()
         );
         generate.setVariant(CompactOverlayButton.Variant.SECONDARY);
         this.buttons.add(generate);
         CompactOverlayButton clearList = CompactOverlayButton.create(
            generate.getX() - 4 - 64, this.listTop() + 3, 64, 16, Component.literal("Clear List"), b -> this.openClearConfirm()
         );
         clearList.setVariant(CompactOverlayButton.Variant.DANGER);
         clearList.active = !this.accountSnapshot().isEmpty();
         this.buttons.add(clearList);
      }

      List<RiptideAccountsScreen.DisplayAccountRow> displayAccounts = this.displayAccounts();
      if (!this.compactListLayout()) {
         int maxScroll = this.savedMaxScroll(displayAccounts.size());
         this.savedListScrollOffset = quantizeScrollOffset(this.savedListScrollOffset, 24, maxScroll);
         this.savedListScroll.jumpTo(this.savedListScrollOffset, maxScroll);
         int firstVisible = this.savedListScrollOffset / 24;
         int rowY = this.savedRowsTop() - this.savedListScrollOffset % 24;

         for (int i = firstVisible; i < displayAccounts.size() && rowY + 24 - 3 <= this.savedRowsBottom(); i++) {
            RiptideAccountsScreen.DisplayAccountRow displayRow = displayAccounts.get(i);
            RiptideAccount account = displayRow.account();
            if (rowY + 24 - 3 <= this.savedRowsTop()) {
               rowY += 24;
            } else {
               boolean current = displayRow.defaultAccount() ? this.isCurrentDefaultAccount() : this.isCurrentAccount(account);
               boolean renameable = !displayRow.defaultAccount() && account.type == RiptideAccountType.Cracked;
               CompactOverlayButton login = CompactOverlayButton.create(
                  this.rowRight() - (displayRow.defaultAccount() ? 62 : (renameable ? 114 : 88)),
                  this.rowButtonY(rowY, 16),
                  54,
                  16,
                  Component.literal(current ? "Active" : "Login"),
                  b -> {
                     if (displayRow.defaultAccount()) {
                        this.loginDefaultAccount();
                     } else {
                        this.loginAccount(account);
                     }
                  }
               );
               login.setVariant(current ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.PRIMARY);
               login.setSelected(current).setAnimationKey("acct-active:" + (displayRow.defaultAccount() ? "default" : account.stableId()));
               login.active = !this.isBusy() && !current;
               CompactOverlayButton delete = null;
               CompactOverlayButton share = null;
               CompactOverlayButton rename = null;
               if (!displayRow.defaultAccount()) {
                  if (renameable) {
                     rename = CompactOverlayButton.create(
                        this.rowRight() - 54, this.rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> this.startRename(account)
                     );
                     rename.setVariant(CompactOverlayButton.Variant.SECONDARY).setIcon(RiptideUiIcons.EDIT);
                     rename.active = !this.isBusy();
                  }

                  delete = CompactOverlayButton.create(
                     this.rowRight() - 28, this.rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> this.deleteAccount(account)
                  );
                  delete.setVariant(CompactOverlayButton.Variant.DANGER).setIcon(RiptideUiIcons.TRASH);
                  delete.active = !this.isBusy();
               }

               if (hasShareableSessionToken(account) && !shareableSessionToken(account).isBlank()) {
                  int shareX = displayRow.defaultAccount() ? this.rowRight() - 88 : this.rowRight() - (renameable ? 140 : 114);
                  share = CompactOverlayButton.create(shareX, this.rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> this.copySessionToken(account));
                  share.setVariant(CompactOverlayButton.Variant.SECONDARY);
                  share.active = !this.isBusy();
               }

               boolean loadRowSkin = this.accountRows.size() < 10;
               this.accountRows
                  .add(new RiptideAccountsScreen.AccountRow(account, rowY, login, delete, share, rename, displayRow.defaultAccount(), loadRowSkin));
               rowY += 24;
            }
         }
      }
   }

   private void addProviderButton(int x, int y, RiptideAccountType provider, String label) {
      CompactOverlayButton button = CompactOverlayButton.create(x, y, 66, 18, Component.literal(label), b -> {
         if (!this.isBusy()) {
            this.type = provider;
            this.clearInputFocus();
            this.updateInputVisibility();
            this.rebuildButtons();
         }
      });
      button.active = !this.isBusy();
      button.setVariant(this.type == provider ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY);
      this.buttons.add(button);
   }

   private void addFilterButton(int x, int y, RiptideAccountType provider, String label) {
      CompactOverlayButton button = CompactOverlayButton.create(x, y, 50, 16, Component.literal(label), b -> this.toggleFilter(provider));
      button.setVariant(this.categoryFilter == provider ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.FILTER_OFF);
      this.buttons.add(button);
   }

   private void addAccount() {
      if (!this.isBusy()) {
         if (this.renamingAccount != null) {
            String newName = safeTrim(this.labelField.getValue());
            if (newName.isBlank()) {
               this.toast("Enter a username first.", -14249);
            } else {
               boolean wasCurrent = this.isCurrentAccount(this.renamingAccount);
               if (RiptideAccountManager.get().rename(this.renamingAccount.stableId(), newName)) {
                  this.toast("Renamed to " + newName + ". Password and proxies carried over.", -13248397);
                  if (wasCurrent) {
                     RiptideAccount renamed = RiptideAccountManager.get().findById(this.renamingAccount.stableId());
                     if (renamed != null) {
                        RiptideAccountManager.get().login(renamed);
                     }
                  }

                  this.renamingAccount = null;
                  this.clearFields();
               } else {
                  this.toast("An account with that name already exists.", -14249);
               }

               this.invalidateAccountSnapshot();
               this.rebuildButtons();
            }
         } else {
            RiptideAccount account = new RiptideAccount();
            account.type = this.type;
            if (this.type == RiptideAccountType.Cracked) {
               account.label = safeTrim(this.labelField.getValue());
               if (account.label.isBlank()) {
                  this.toast("Enter a username first.", -14249);
                  return;
               }

               this.runAdd(account);
            } else if (this.type == RiptideAccountType.Session) {
               account.token = safeTrim(this.tokenField.getValue());
               if (account.token.isBlank()) {
                  this.toast("Paste a token first.", -14249);
                  return;
               }

               this.runAdd(account);
            } else if (this.type == RiptideAccountType.TheAltening) {
               account.token = safeTrim(this.tokenField.getValue());
               if (account.token.isBlank()) {
                  this.toast("Paste a token first.", -14249);
                  return;
               }

               this.runAdd(account);
            } else if (this.type == RiptideAccountType.Microsoft) {
               this.runMicrosoftAdd();
            }
         }
      }
   }

   private void runAdd(RiptideAccount account) {
      int id = this.beginOperation(RiptideAccountsScreen.Operation.ADD);
      this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Account-Add", () -> {
         boolean fetched = account.fetchInfo();
         if (!this.isCancelled(id)) {
            if (!fetched) {
               this.finishOperation(id, false, "Couldn't add account.", false);
            } else if (RiptideAccountManager.get().contains(account)) {
               this.finishOperation(id, false, "Account already exists.", false);
            } else {
               RiptideAccountManager.get().add(account);
               this.selectedAccount = account;
               boolean loggedIn = account.login();
               if (!this.isCancelled(id)) {
                  if (!loggedIn) {
                     this.finishOperation(id, true, "Added, but login failed.", true);
                  } else {
                     this.finishOperation(id, true, "Logged in as " + account.displayName() + ".", true);
                  }
               }
            }
         }
      });
   }

   private void runMicrosoftAdd() {
      int id = this.beginOperation(RiptideAccountsScreen.Operation.MICROSOFT);
      RiptideMicrosoftLogin.getRefreshToken(refreshToken -> {
         if (!this.isCancelled(id)) {
            if (refreshToken != null && !refreshToken.isBlank()) {
               RiptideAccount account = new RiptideAccount();
               account.type = RiptideAccountType.Microsoft;
               account.label = refreshToken;
               this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Microsoft-Add", () -> {
                  boolean fetched = account.fetchInfo();
                  if (!this.isCancelled(id)) {
                     if (!fetched) {
                        this.finishOperation(id, false, loginFailMessage(account), false);
                     } else if (RiptideAccountManager.get().contains(account)) {
                        this.finishOperation(id, false, "Account already exists.", false);
                     } else {
                        boolean loggedIn = account.login();
                        if (!this.isCancelled(id)) {
                           if (!loggedIn) {
                              this.finishOperation(id, false, loginFailMessage(account), false);
                           } else {
                              RiptideAccountManager.get().add(account);
                              this.selectedAccount = account;
                              this.finishOperation(id, true, "Logged in as " + account.displayName() + ".", true);
                           }
                        }
                     }
                  }
               });
            } else {
               this.finishOperation(id, false, "Login cancelled.", false);
            }
         }
      });
   }

   private void loginAccount(RiptideAccount account) {
      if (!this.isBusy() && account != null && !this.isCurrentAccount(account)) {
         this.selectedAccount = account;
         int id = this.beginOperation(RiptideAccountsScreen.Operation.LOGIN);
         this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Account-Login", () -> {
            boolean fetched = account.fetchInfo();
            if (!this.isCancelled(id)) {
               if (!fetched) {
                  if (account.type == RiptideAccountType.Microsoft && isExpiredMicrosoftToken(account)) {
                     this.reloginMicrosoft(account, id);
                  } else {
                     this.finishOperation(id, false, "Token expired.", false);
                  }
               } else {
                  boolean loggedIn = account.login();
                  if (!this.isCancelled(id)) {
                     if (loggedIn) {
                        RiptideAccountManager.get().save();
                        this.finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
                     } else {
                        this.finishOperation(id, false, loginFailMessage(account), false);
                     }
                  }
               }
            }
         });
      }
   }

   private static boolean isExpiredMicrosoftToken(RiptideAccount account) {
      String error = account == null ? null : account.lastError();
      if (error != null && !error.isBlank()) {
         String lower = error.toLowerCase(Locale.ROOT);
         return lower.contains("invalid_grant") || lower.contains("expired");
      } else {
         return false;
      }
   }

   private void reloginMicrosoft(RiptideAccount account, int id) {
      this.minecraft.execute(() -> {
         if (!this.isCancelled(id)) {
            RiptideMicrosoftLogin.getRefreshToken(refreshToken -> {
               if (!this.isCancelled(id)) {
                  if (refreshToken != null && !refreshToken.isBlank()) {
                     account.label = refreshToken;
                     this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Microsoft-Relogin", () -> {
                        boolean fetched = account.fetchInfo();
                        if (!this.isCancelled(id)) {
                           if (!fetched) {
                              this.finishOperation(id, false, loginFailMessage(account), false);
                           } else {
                              boolean loggedIn = account.login();
                              if (!this.isCancelled(id)) {
                                 if (loggedIn) {
                                    RiptideAccountManager.get().save();
                                    this.finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
                                 } else {
                                    this.finishOperation(id, false, loginFailMessage(account), false);
                                 }
                              }
                           }
                        }
                     });
                  } else {
                     this.finishOperation(id, false, "Token expired.", false);
                  }
               }
            });
         }
      });
   }

   private static String loginFailMessage(RiptideAccount account) {
      String reason = account == null ? "" : account.lastError();
      return reason != null && !reason.isBlank() ? "Login failed: " + reason : "Login failed.";
   }

   private void loginDefaultAccount() {
      if (!this.isBusy() && !this.isCurrentDefaultAccount()) {
         RiptideAccount account = this.defaultMinecraftAccount();
         if (account != null) {
            this.selectedAccount = account;
            int id = this.beginOperation(RiptideAccountsScreen.Operation.LOGIN);
            this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Default-Account-Login", () -> {
               boolean loggedIn = RiptideAccountSessionSwitcher.setSession(RiptideAccountSessionSwitcher.getOriginalUser());
               if (!this.isCancelled(id)) {
                  if (loggedIn) {
                     this.finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
                  } else {
                     this.finishOperation(id, false, "Login failed.", false);
                  }
               }
            });
         }
      }
   }

   private void deleteAccount(RiptideAccount account) {
      if (!this.isBusy() && account != null) {
         RiptideAccountManager.get().remove(account);
         this.invalidateAccountSnapshot();
         if (account.equals(this.selectedAccount)) {
            this.selectedAccount = null;
         }

         this.toast("Deleted " + account.displayName() + ".", -6645094);
         this.rebuildButtons();
      }
   }

   private void checkExpiredAccounts() {
      if (!this.isBusy()) {
         List<RiptideAccount> accounts = new ArrayList<>();

         for (RiptideAccount account : this.accountSnapshot()) {
            if (account.type == RiptideAccountType.Cracked) {
               account.checkStatus = RiptideAccount.CheckStatus.UNKNOWN;
            } else {
               accounts.add(account);
            }
         }

         if (accounts.isEmpty()) {
            this.toast("No accounts to check.", -6645094);
         } else {
            for (RiptideAccount accountx : accounts) {
               if (accountx.checkStatus == RiptideAccount.CheckStatus.UNKNOWN) {
                  accountx.checkStatus = RiptideAccount.CheckStatus.CHECKING;
               }
            }

            int id = this.beginOperation(RiptideAccountsScreen.Operation.CHECK);
            this.operationTask = RiptideBackgroundTasks.runTracked("Riptide-Account-Check-Coordinator", () -> {
               ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, Math.max(1, accounts.size())), runnable -> {
                  Thread worker = new Thread(runnable, "Riptide-Account-Check");
                  worker.setDaemon(true);
                  return worker;
               });
               AtomicInteger valid = new AtomicInteger();
               AtomicInteger expired = new AtomicInteger();

               try {
                  List<Future<?>> futures = new ArrayList<>();

                  for (RiptideAccount accountxx : accounts) {
                     futures.add(pool.submit(() -> {
                        if (!this.isCancelled(id)) {
                           boolean ok;
                           try {
                              ok = accountx.fetchInfo();
                           } catch (Throwable var7) {
                              ok = false;
                           }

                           if (!this.isCancelled(id)) {
                              accountx.checkStatus = ok ? RiptideAccount.CheckStatus.VALID : RiptideAccount.CheckStatus.EXPIRED;
                              (ok ? valid : expired).incrementAndGet();
                           }
                        }
                     }));
                  }

                  for (Future<?> future : futures) {
                     try {
                        future.get();
                     } catch (Exception var13) {
                     }
                  }
               } finally {
                  pool.shutdownNow();
               }

               if (!this.isCancelled(id)) {
                  RiptideAccountManager.get().save();
                  this.finishOperation(id, true, valid.get() + " valid, " + expired.get() + " expired.", false);
               }
            });
         }
      }
   }

   private int expiredAccountCount() {
      int count = 0;

      for (RiptideAccount account : this.accountSnapshot()) {
         if (account.checkStatus == RiptideAccount.CheckStatus.EXPIRED) {
            count++;
         }
      }

      return count;
   }

   private void clearExpiredAccounts() {
      if (!this.isBusy()) {
         if (this.selectedAccount != null && this.selectedAccount.checkStatus == RiptideAccount.CheckStatus.EXPIRED) {
            this.selectedAccount = null;
         }

         int removed = RiptideAccountManager.get().removeExpired();
         if (removed == 0) {
            this.toast("No expired accounts.", -6645094);
         } else {
            this.invalidateAccountSnapshot();
            this.toast("Cleared " + removed + " expired.", -6645094);
            this.rebuildButtons();
         }
      }
   }

   private static int nameColor(RiptideAccount account, boolean active, boolean defaultAccount) {
      if (active) {
         return -13248397;
      } else if (defaultAccount) {
         return -7429889;
      } else {
         if (account != null) {
            if (account.checkStatus == RiptideAccount.CheckStatus.EXPIRED) {
               return -42149;
            }

            if (account.checkStatus == RiptideAccount.CheckStatus.CHECKING) {
               return -14249;
            }
         }

         return -855310;
      }
   }

   private static String checkStatusLabel(RiptideAccount.CheckStatus status) {
      if (status == null) {
         return "";
      } else {
         return switch (status) {
            case CHECKING -> "CHECK…";
            case VALID -> "VALID";
            case EXPIRED -> "EXPIRED";
            case UNKNOWN -> "";
         };
      }
   }

   private static int checkStatusColor(RiptideAccount.CheckStatus status) {
      if (status == null) {
         return -6645094;
      } else {
         return switch (status) {
            case CHECKING -> -14249;
            case VALID -> -13248397;
            case EXPIRED -> -42149;
            case UNKNOWN -> -6645094;
         };
      }
   }

   private static boolean hasShareableSessionToken(RiptideAccount account) {
      return account != null
         && (account.type == RiptideAccountType.Microsoft || account.type == RiptideAccountType.Session || account.type == RiptideAccountType.TheAltening);
   }

   private static String shareableSessionToken(RiptideAccount account) {
      if (!hasShareableSessionToken(account)) {
         return "";
      } else {
         return account.type == RiptideAccountType.TheAltening ? safeTrim(account.sessionToken) : safeTrim(account.token);
      }
   }

   private void copySessionToken(RiptideAccount account) {
      if (hasShareableSessionToken(account)) {
         String sessionToken = shareableSessionToken(account);
         if (sessionToken.isBlank()) {
            this.toast("No session token.", -14249);
         } else if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
            try {
               this.minecraft.keyboardHandler.setClipboard(sessionToken);
            } catch (Exception var4) {
               this.toast("Failed to copy token.", -42149);
               return;
            }

            this.toast("Copied session token.", -13248397);
         } else {
            this.toast("Clipboard unavailable.", -42149);
         }
      }
   }

   private int beginOperation(RiptideAccountsScreen.Operation next) {
      this.operationId++;
      this.operation = next;
      this.operationStartedAtMs = System.currentTimeMillis();
      this.clearInputFocus();
      this.updateInputVisibility();
      this.rebuildButtons();
      return this.operationId;
   }

   private void finishOperation(int id, boolean success, String message, boolean clearOnSuccess) {
      this.minecraft.execute(() -> {
         if (id == this.operationId) {
            this.operation = RiptideAccountsScreen.Operation.NONE;
            this.operationStartedAtMs = 0L;
            this.operationTask = null;
            this.toast(message, success ? -13248397 : -42149);
            if (success && clearOnSuccess) {
               this.clearFields();
            }

            this.updateInputVisibility();
            this.clearInputFocus();
            this.invalidateAccountSnapshot();
            this.rebuildButtons();
         }
      });
   }

   private void cancelOperation() {
      if (this.isBusy()) {
         this.operationId++;
         Future<?> task = this.operationTask;
         if (task != null) {
            task.cancel(true);
         }

         if (this.operation == RiptideAccountsScreen.Operation.MICROSOFT) {
            RiptideMicrosoftLogin.stopServer();
         }

         if (this.operation == RiptideAccountsScreen.Operation.CHECK) {
            for (RiptideAccount account : this.accountSnapshot()) {
               if (account.checkStatus == RiptideAccount.CheckStatus.CHECKING) {
                  account.checkStatus = RiptideAccount.CheckStatus.UNKNOWN;
               }
            }
         }

         this.operation = RiptideAccountsScreen.Operation.NONE;
         this.operationStartedAtMs = 0L;
         this.operationTask = null;
         this.clearInputFocus();
         this.toast("Cancelled.", -14249);
         this.updateInputVisibility();
         this.rebuildButtons();
      }
   }

   private boolean isCancelled(int id) {
      return id != this.operationId || Thread.currentThread().isInterrupted();
   }

   private boolean isBusy() {
      return this.operation != RiptideAccountsScreen.Operation.NONE;
   }

   private boolean shouldShowCancelOperationButton() {
      return this.isBusy() && this.operationStartedAtMs > 0L && System.currentTimeMillis() - this.operationStartedAtMs >= 5000L;
   }

   private void refreshOperationControls() {
      if (this.cancelOperationButton != null) {
         this.cancelOperationButton.visible = this.shouldShowCancelOperationButton();
      }
   }

   private void clearFields() {
      this.renamingAccount = null;
      if (this.labelField != null) {
         this.labelField.setValue("");
      }

      if (this.tokenField != null) {
         this.tokenField.setValue("");
      }
   }

   private void startRename(RiptideAccount account) {
      if (account != null && account.type == RiptideAccountType.Cracked && !this.isBusy()) {
         this.renamingAccount = account;
         this.type = RiptideAccountType.Cracked;
         this.clearInputFocus();
         this.updateInputVisibility();
         this.rebuildButtons();
         if (this.labelField != null) {
            this.labelField.setValue(account.displayName());
            this.labelField.setFocused(true);
            this.setFocused(this.labelField);
            this.labelField.moveCursorToEnd(false);
         }
      }
   }

   private void updateInputVisibility() {
      if (this.labelField != null && this.tokenField != null) {
         boolean showForm = !this.narrowLayout();
         boolean labelVisible = showForm && this.type == RiptideAccountType.Cracked;
         boolean tokenVisible = showForm && (this.type == RiptideAccountType.Session || this.type == RiptideAccountType.TheAltening);
         this.labelField.setVisible(labelVisible);
         this.tokenField.setVisible(tokenVisible);
         this.labelField.active = labelVisible && !this.isBusy();
         this.tokenField.active = tokenVisible && !this.isBusy();
         if (!labelVisible || this.isBusy()) {
            this.labelField.setFocused(false);
         }

         if (!tokenVisible || this.isBusy()) {
            this.tokenField.setFocused(false);
         }

         if (this.searchField != null) {
            this.searchField.visible = showForm;
            this.searchField.active = showForm;
         }

         this.labelField.setHint(Component.literal("Username"));
         this.tokenField.setHint(Component.literal(this.type == RiptideAccountType.TheAltening ? "TheAltening token" : "Minecraft access token"));
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      this.refreshOperationControls();
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -15856112);
         int panelX = this.panelX();
         int formX = panelX + 10;
         int previewX = this.previewX();
         if (!this.narrowLayout()) {
            this.drawPanel(graphics, formX, 20, 292, 178, -401074149);
            this.drawPanel(graphics, previewX, 20, 200, 178, -1206643689);
         }

         int listHeight = this.listPanelHeight();
         this.drawPanel(graphics, this.listX(), this.listTop(), this.listWidth(), listHeight, -401074149);
         if (!this.narrowLayout()) {
            this.drawText(graphics, "Accounts", formX + 12, 31, -855310, false);
            this.renderPreview(graphics, previewX, virtualMouseX, virtualMouseY, delta);
         }

         List<RiptideAccountsScreen.DisplayAccountRow> displayAccounts = this.displayAccounts();
         if (this.compactListLayout()) {
            this.drawText(graphics, "Window too small.", this.listX() + 12, this.listTop() + 12, -6645094, false, Math.max(0, this.listWidth() - 24));
         }

         int firstVisibleRow = this.savedListScrollOffset / 24;
         String listTitle = displayAccounts.size() <= this.savedViewportRows()
            ? "Accounts"
            : "Accounts  showing "
               + (firstVisibleRow + 1)
               + "-"
               + Math.min(displayAccounts.size(), firstVisibleRow + this.savedViewportRows())
               + " / "
               + displayAccounts.size();
         int titleMaxWidth = this.compactListLayout() ? this.listWidth() - 24 : Math.max(20, this.listWidth() - 24 - 328);
         this.drawText(graphics, listTitle, this.listX() + 12, this.listTop() + 10, -855310, false, titleMaxWidth);
         if (!this.compactListLayout()) {
            for (RiptideAccountsScreen.AccountRow row : this.accountRows) {
               this.renderRow(graphics, row, virtualMouseX, virtualMouseY);
            }
         }

         if (this.accountSnapshot().isEmpty()) {
            this.drawText(graphics, "No extra accounts saved yet.", this.listX() + 12, this.listRowTop() + 24 + 8, -6645094, false, this.listWidth() - 24);
         } else if (this.filteredAccounts().isEmpty()) {
            this.drawText(
               graphics,
               "No accounts match the current search or filters.",
               this.listX() + 12,
               this.listRowTop() + 24 + 8,
               -6645094,
               false,
               this.listWidth() - 24
            );
         }

         for (CompactOverlayButton button : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
         }

         if (!this.compactListLayout()) {
            CompactScrollbar.Metrics scrollbar = this.accountScrollbarMetrics(displayAccounts.size());
            CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), this.accountScrollbarDragging);
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
         if (this.activePopup != 0) {
            this.renderPopup(graphics, virtualMouseX, virtualMouseY, delta);
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   public void removed() {
      super.removed();
   }

   private void renderRow(GuiGraphicsExtractor graphics, RiptideAccountsScreen.AccountRow row, int mouseX, int mouseY) {
      RiptideAccount account = row.account;
      boolean active = row.defaultAccount ? this.isCurrentDefaultAccount() : this.isCurrentAccount(account);
      boolean selected = account.equals(this.selectedAccount);
      int x = this.rowX();
      int y = row.y;
      int w = this.rowWidth();
      int fill = active ? successColor(858052714) : (row.defaultAccount ? 808137323 : (selected ? outlineColor(606804509) : 403771667));
      UiRenderer.rect(graphics, UiBounds.of(x, y, w, this.rowVisualHeight()), fill);
      if (active) {
         UiRenderer.rect(graphics, UiBounds.of(x, y, 2, this.rowVisualHeight()), successColor(-12588930));
      } else if (selected) {
         UiRenderer.rect(graphics, UiBounds.of(x, y, 2, this.rowVisualHeight()), outlineColor(-10866632));
      }

      PlayerSkin rowSkin = !row.loadSkin && !active && !selected ? this.fallbackSkin(account) : this.skinLookup(account).skin();
      this.drawHead(graphics, rowSkin, x + 7, y + 3, 16);
      String name = account.displayName().isBlank() ? "(unknown)" : account.displayName();
      String meta = row.defaultAccount ? "Default Minecraft" : account.type.name();
      int nameX = x + 31;
      if (this.narrowLayout()) {
         int textRight = Math.max(nameX + 1, row.loginButton.getX() - 8);
         this.drawText(graphics, name, nameX, y + 7, nameColor(account, active, row.defaultAccount), false, Math.max(1, textRight - nameX));
         CompactOverlayButton.renderStyled(graphics, this.font, row.loginButton, mouseX, mouseY);
         if (row.renameButton != null) {
            CompactOverlayButton.renderStyled(graphics, this.font, row.renameButton, mouseX, mouseY);
         }

         if (row.deleteButton != null) {
            CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
         }

         this.renderShareButton(graphics, row.shareButton, mouseX, mouseY);
      } else {
         int metaX = x + 228;
         int badgeX = row.defaultAccount ? row.loginButton.getX() - 70 : row.loginButton.getX() - 58;
         int badgeRight = row.shareButton == null ? row.loginButton.getX() : row.shareButton.getX();
         int nameMaxWidth = Math.max(20, metaX - nameX - 12);
         int metaMaxWidth = Math.max(20, badgeX - metaX - 10);
         this.drawText(graphics, name, nameX, y + 7, nameColor(account, active, row.defaultAccount), false, nameMaxWidth);
         this.drawText(graphics, meta, metaX, y + 7, row.defaultAccount ? -7429889 : -6645094, false, metaMaxWidth);
         if (active) {
            this.drawText(graphics, "CURRENT", badgeX, y + 8, -13248397, false, Math.max(1, badgeRight - badgeX - 4));
         } else if (!row.defaultAccount) {
            String statusLabel = checkStatusLabel(account.checkStatus);
            if (!statusLabel.isEmpty()) {
               this.drawText(graphics, statusLabel, badgeX, y + 8, checkStatusColor(account.checkStatus), false, Math.max(1, badgeRight - badgeX - 4));
            }
         }

         CompactOverlayButton.renderStyled(graphics, this.font, row.loginButton, mouseX, mouseY);
         if (row.renameButton != null) {
            CompactOverlayButton.renderStyled(graphics, this.font, row.renameButton, mouseX, mouseY);
         }

         if (row.deleteButton != null) {
            CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
         }

         this.renderShareButton(graphics, row.shareButton, mouseX, mouseY);
      }
   }

   private void renderShareButton(GuiGraphicsExtractor graphics, CompactOverlayButton button, int mouseX, int mouseY) {
      if (button != null) {
         CompactOverlayButton.renderStyled(graphics, this.font, button, mouseX, mouseY);
         UiBounds bounds = UiBounds.of(button.getX(), button.getY(), button.getWidth(), button.getHeight());
         this.drawCopyGlyph(graphics, bounds, button.active ? -855310 : -9016466);
      }
   }

   private void drawCopyGlyph(GuiGraphicsExtractor graphics, UiBounds button, int color) {
      int pageW = 7;
      int pageH = 7;
      int offset = 3;
      int ox = button.x() + (button.width() - (pageW + offset)) / 2;
      int oy = button.y() + (button.height() - (pageH + offset)) / 2;
      UiRenderer.outline(graphics, UiBounds.of(ox + offset, oy, pageW, pageH), color);
      UiBounds front = UiBounds.of(ox, oy + offset, pageW, pageH);
      UiRenderer.rect(graphics, front, -15395557);
      UiRenderer.outline(graphics, front, color);
   }

   private void renderPreview(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY, float delta) {
      RiptideAccount account = this.previewAccount();
      this.drawText(graphics, "Skin preview", x + 12, 31, -855310, false);
      if (account == null) {
         this.drawText(graphics, "No account selected", x + 16, 78, -6645094, false, 168);
         this.drawText(graphics, "Click a row to preview it.", x + 16, 94, -6645094, false, 168);
      } else {
         RiptideAccountsScreen.SkinLookup lookup = this.skinLookup(account);
         PlayerSkin skin = lookup.skin();
         int modelCenterX = x + 100;
         int modelX0 = modelCenterX - 111;
         int modelY0 = 54;
         int modelX1 = modelCenterX + 111;
         int modelY1 = 190;
         this.render3dSkin(graphics, skin, modelX0, modelY0, modelX1, modelY1);
         if (lookup.loading()) {
            this.drawText(graphics, "Loading skin...", x + 14, 178, -14249, false, 172);
         }

         String display = account.displayName().isBlank() ? "(unknown)" : account.displayName();
         boolean defaultAccount = this.isDefaultAccount(account);
         boolean active = defaultAccount ? this.isCurrentDefaultAccount() : this.isCurrentAccount(account);
         this.drawText(graphics, display, x + 12, 164, active ? -13248397 : -855310, false, 176);
         this.drawText(graphics, defaultAccount ? "Default Minecraft" : account.type.name(), x + 12, 150, defaultAccount ? -7429889 : -6645094, false, 176);
         if (active) {
            this.drawText(graphics, "CURRENT", x + 200 - 66, 31, -13248397, false, 54);
         }
      }
   }

   private void render3dSkin(GuiGraphicsExtractor graphics, PlayerSkin skin, int x0, int y0, int x1, int y1) {
      if (this.widePlayerModel != null && this.slimPlayerModel != null) {
         Simple model = skin.model().name().equalsIgnoreCase("slim") ? this.slimPlayerModel : this.widePlayerModel;
         float drawScale = RiptideUiScale.getOverlayDrawScale();
         int scaledX0 = Math.round(x0 * drawScale);
         int scaledY0 = Math.round(y0 * drawScale);
         int scaledX1 = Math.round(x1 * drawScale);
         int scaledY1 = Math.round(y1 * drawScale);
         float scale = 0.97F * Math.max(1, scaledY1 - scaledY0) / 2.125F;
         graphics.skin(
            model, skin.body().texturePath(), scale, this.previewRotationX, this.currentPreviewRotationY(), -1.0625F, scaledX0, scaledY0, scaledX1, scaledY1
         );
      }
   }

   private float currentPreviewRotationY() {
      return this.previewDragging ? this.previewRotationY : this.previewRotationY + (float)((Blaze3D.getTime() - this.previewAutoRotationStartTime) * 18.0);
   }

   private void drawHead(GuiGraphicsExtractor graphics, PlayerSkin skin, int x, int y, int size) {
      Identifier texture = skin.body().texturePath();
      graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
      graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() != 0) {
         return super.mouseClicked(virtualEvent, doubleClick);
      } else if (this.activePopup != 0) {
         this.handlePopupMouse(virtualEvent, doubleClick);
         return true;
      } else if (this.compactListLayout()) {
         return super.mouseClicked(virtualEvent, doubleClick);
      } else {
         CompactScrollbar.Metrics scrollbar = this.accountScrollbarMetrics(this.displayAccounts().size());
         if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            this.accountScrollbarDragging = true;
            this.accountScrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
               ? Math.max(0, (int)Math.round(virtualEvent.y()) - scrollbar.thumbY())
               : scrollbar.thumbHeight() / 2;
            this.savedListScrollOffset = quantizeScrollOffset(
               CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.accountScrollbarGrabOffset), 24, scrollbar.maxScroll()
            );
            this.savedListScroll.jumpTo(this.savedListScrollOffset, scrollbar.maxScroll());
            this.rebuildButtons();
            this.clearInputFocus();
            return true;
         } else if (this.isInPreview(virtualEvent.x(), virtualEvent.y())) {
            this.previewRotationY = this.currentPreviewRotationY();
            this.previewAutoRotationStartTime = Blaze3D.getTime();
            this.previewDragging = true;
            this.lastPreviewMouseX = virtualEvent.x();
            this.lastPreviewMouseY = virtualEvent.y();
            this.clearInputFocus();
            return true;
         } else {
            for (CompactOverlayButton button : this.buttons) {
               if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }
            }

            for (RiptideAccountsScreen.AccountRow row : this.accountRows) {
               if (row.renameButton != null && CompactOverlayButton.fireIfHit(row.renameButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }

               if (CompactOverlayButton.fireIfHit(row.deleteButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }

               if (CompactOverlayButton.fireIfHit(row.shareButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }

               if (CompactOverlayButton.fireIfHit(row.loginButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                  return true;
               }

               if (virtualEvent.x() >= this.rowX() && virtualEvent.x() < this.rowRight() && virtualEvent.y() >= row.y && virtualEvent.y() < row.y + 24 - 3) {
                  this.selectedAccount = row.account();
                  return true;
               }
            }

            this.clearInputFocus();
            return super.mouseClicked(virtualEvent, doubleClick);
         }
      }
   }

   private void toggleFilter(RiptideAccountType filterType) {
      this.categoryFilter = this.categoryFilter == filterType ? null : filterType;
      this.savedListScrollOffset = 0;
      this.savedListScroll.jumpTo(0, 0);
      this.rebuildButtons();
   }

   private void openGenerator() {
      this.activePopup = 1;
      this.generatorResult = "";
      this.clearInputFocus();
      this.rebuildPopupButtons();
   }

   private void openClearConfirm() {
      this.activePopup = 2;
      this.clearInputFocus();
      this.rebuildPopupButtons();
   }

   private void closePopup() {
      this.syncGeneratorPasswordToConfig();
      this.activePopup = 0;
      this.generatorResult = "";
      this.popupDropdowns.clear();
      if (this.generatorCountField != null) {
         this.generatorCountField.setFocused(false);
      }

      if (this.generatorPasswordField != null) {
         this.generatorPasswordField.setFocused(false);
      }

      if (this.generatorListField != null) {
         this.generatorListField.setFocused(false);
      }
   }

   private void syncGeneratorPasswordToConfig() {
      if (this.generatorPasswordField != null) {
         RiptideConfig config = RiptideConfig.getGlobal();
         String value = this.generatorPasswordField.getValue();
         if (!Objects.equals(config.accountGenSharedPassword, value)) {
            config.accountGenSharedPassword = value;
            config.save();
         }
      }
   }

   private int popupX() {
      return Math.max(4, (this.screenWidth() - 280) / 2);
   }

   private int popupY() {
      return Math.max(4, (this.screenHeight() - 210) / 2);
   }

   private void rebuildPopupButtons() {
      this.popupButtons.clear();
      this.popupDropdowns.clear();
      int px = this.popupX();
      int py = this.popupY();
      if (this.activePopup == 1) {
         CompactOverlayButton random = CompactOverlayButton.create(px + 12, py + 28, 80, 16, Component.literal("Random"), b -> {
            this.generatorMode = 0;
            this.generatorResult = "";
            this.rebuildPopupButtons();
         });
         random.setVariant(this.generatorMode == 0 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
         this.popupButtons.add(random);
         CompactOverlayButton fromList = CompactOverlayButton.create(px + 96, py + 28, 80, 16, Component.literal("From List"), b -> {
            this.generatorMode = 1;
            this.generatorResult = "";
            this.rebuildPopupButtons();
         });
         fromList.setVariant(this.generatorMode == 1 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
         this.popupButtons.add(fromList);
         if (this.generatorMode == 0) {
            RiptideConfig config = RiptideConfig.getGlobal();
            this.popupButtons.add(CompactOverlayButton.create(px + 12, py + 72, 256, 16, Component.literal("Set Password"), b -> {
               RiptideConfig cfg = RiptideConfig.getGlobal();
               cfg.accountGenSetPassword = !cfg.accountGenSetPassword;
               cfg.save();
               this.rebuildPopupButtons();
            }).setToggleState(config.accountGenSetPassword));
            if (config.accountGenSetPassword) {
               boolean setForAll = "Set For All".equalsIgnoreCase(config.accountGenPasswordMode);
               this.popupDropdowns.add(new CompactDropdown(px + 12, py + 94, 256, 16, List.of("Generate", "Set For All"), setForAll ? 1 : 0, index -> {
                  RiptideConfig cfg = RiptideConfig.getGlobal();
                  cfg.accountGenPasswordMode = index == 1 ? "Set For All" : "Generate";
                  cfg.save();
                  this.rebuildPopupButtons();
               }));
            }

            CompactOverlayButton generate = CompactOverlayButton.create(
                  px + 12, py + 150, 90, 18, Component.literal(this.generating ? "Working..." : "Generate"), b -> this.runRandomGeneration()
               )
               .setVariant(CompactOverlayButton.Variant.PRIMARY);
            generate.active = !this.generating;
            this.popupButtons.add(generate);
         } else {
            this.popupButtons
               .add(
                  CompactOverlayButton.create(px + 12, py + 160, 90, 16, Component.literal("Paste"), b -> this.pasteListFromClipboard())
                     .setVariant(CompactOverlayButton.Variant.SECONDARY)
               );
            CompactOverlayButton addAccounts = CompactOverlayButton.create(
                  px + 106, py + 160, 90, 16, Component.literal(this.generating ? "Working..." : "Add Accounts"), b -> this.runListGeneration()
               )
               .setVariant(CompactOverlayButton.Variant.PRIMARY);
            addAccounts.active = !this.generating;
            this.popupButtons.add(addAccounts);
         }

         this.popupButtons
            .add(
               CompactOverlayButton.create(px + 198, py + 184, 70, 16, Component.literal("Close"), b -> this.closePopup())
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
      } else if (this.activePopup == 2) {
         CompactOverlayButton clear = CompactOverlayButton.create(px + 12, py + 74, 90, 18, Component.literal("Clear"), b -> this.confirmClear())
            .setVariant(CompactOverlayButton.Variant.DANGER);
         clear.active = this.clearTargetCount() > 0;
         this.popupButtons.add(clear);
         this.popupButtons
            .add(
               CompactOverlayButton.create(px + 198, py + 74, 70, 18, Component.literal("Cancel"), b -> this.closePopup())
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
      }
   }

   private void runRandomGeneration() {
      if (!this.generating) {
         this.syncGeneratorPasswordToConfig();
         int count = 10;

         try {
            count = Integer.parseInt(safeTrim(this.generatorCountField.getValue()));
         } catch (NumberFormatException var3) {
         }

         int wanted = count;
         this.beginGeneration();
         this.runGenerationAsync(
            () -> {
               List<String> names = RiptideAccountGenerator.randomNames(wanted);
               int added = RiptideAccountGenerator.addGeneratedAccounts(names);
               return names.isEmpty()
                  ? "Nothing to add."
                  : (added >= names.size() ? added + " accounts added." : added + " added, " + (names.size() - added) + " already existed.");
            }
         );
      }
   }

   private void runListGeneration() {
      if (!this.generating) {
         this.syncGeneratorPasswordToConfig();
         String raw = this.generatorListField == null ? "" : this.generatorListField.getValue();
         if (raw != null && !raw.isBlank()) {
            this.beginGeneration();
            this.runGenerationAsync(() -> {
               RiptideAccountGenerator.ParseResult result = RiptideAccountGenerator.parseNameList(raw);
               int added = RiptideAccountGenerator.addGeneratedAccounts(result.names());
               StringBuilder message = new StringBuilder("Added ").append(added).append(" accounts");
               int existed = result.names().size() - added;
               if (existed > 0) {
                  message.append(", ").append(existed).append(" existed");
               }

               if (result.skipped() > 0) {
                  message.append(", ").append(result.skipped()).append(" invalid");
               }

               if (result.duplicates() > 0) {
                  message.append(", ").append(result.duplicates()).append(" dupes");
               }

               return message.toString();
            });
         } else {
            this.generatorResult = "Paste a list first.";
         }
      }
   }

   private void beginGeneration() {
      this.generating = true;
      this.generatorResult = "Generating...";
      this.rebuildPopupButtons();
   }

   private void runGenerationAsync(Supplier<String> work) {
      Thread thread = new Thread(() -> {
         String result;
         try {
            result = work.get();
         } catch (Throwable var5) {
            result = "Generation failed.";
         }

         String message = result;
         Runnable done = () -> {
            this.generating = false;
            this.generatorResult = message;
            this.invalidateAccountSnapshot();
            this.rebuildButtons();
            this.rebuildPopupButtons();
         };
         if (this.minecraft != null) {
            this.minecraft.execute(done);
         } else {
            done.run();
         }
      }, "riptide-account-gen");
      thread.setDaemon(true);
      thread.start();
   }

   private void pasteListFromClipboard() {
      if (this.minecraft != null && this.generatorListField != null) {
         this.generatorListField.setValue(this.minecraft.keyboardHandler.getClipboard());
      }
   }

   private void confirmClear() {
      int removed = 0;

      for (RiptideAccount account : new ArrayList<>(RiptideAccountManager.get().all())) {
         if (this.categoryFilter == null || account.type == this.categoryFilter) {
            RiptideAccountManager.get().remove(account);
            removed++;
         }
      }

      this.closePopup();
      this.toast("Cleared " + removed + " accounts.", -6645094);
      this.invalidateAccountSnapshot();
      this.rebuildButtons();
   }

   private int clearTargetCount() {
      int count = 0;

      for (RiptideAccount account : RiptideAccountManager.get().all()) {
         if (this.categoryFilter == null || account.type == this.categoryFilter) {
            count++;
         }
      }

      return count;
   }

   private String clearTargetNames() {
      if (this.categoryFilter != null) {
         return this.categoryFilter.name();
      } else {
         StringBuilder names = new StringBuilder();

         for (RiptideAccountType type : RiptideAccountType.values()) {
            if (names.length() > 0) {
               names.append(", ");
            }

            names.append(type.name());
         }

         return names.toString();
      }
   }

   private void renderPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int rawMouseX = mouseX;
      int rawMouseY = mouseY;
      if (CompactDropdown.isMenuOpen(this.popupDropdowns)) {
         mouseX = Integer.MIN_VALUE;
         mouseY = Integer.MIN_VALUE;
      }

      UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -1610612736);
      int px = this.popupX();
      int py = this.popupY();
      if (this.activePopup == 2) {
         this.drawPanel(graphics, px, py, 280, 100, -401074149);
         this.drawText(graphics, "Clear Accounts?", px + 12, py + 10, -855310, false);
         this.drawText(graphics, this.clearTargetCount() + " accounts will be deleted in:", px + 12, py + 28, -6645094, false, 256);
         this.drawText(graphics, this.clearTargetNames(), px + 12, py + 42, -14249, false, 256);
         this.drawText(graphics, "This cannot be undone.", px + 12, py + 56, -6645094, false, 256);
      } else {
         this.drawPanel(graphics, px, py, 280, 210, -401074149);
         this.drawText(graphics, "Generate Accounts", px + 12, py + 10, -855310, false);
         if (this.generatorMode == 0) {
            this.drawText(graphics, "Count:", px + 12, py + 54, -6645094, false);
            this.generatorCountField.setX(px + 50);
            this.generatorCountField.setY(py + 50);
            this.generatorCountField.extractRenderState(graphics, mouseX, mouseY, delta);
            if (this.passwordFieldVisible()) {
               this.generatorPasswordField.setX(px + 12);
               this.generatorPasswordField.setY(py + 116);
               this.generatorPasswordField.extractRenderState(graphics, mouseX, mouseY, delta);
            }

            if (!this.generatorResult.isBlank()) {
               this.drawText(graphics, this.generatorResult, px + 12, py + 172, -13248397, false, 180);
            }
         } else {
            this.generatorListField.setX(px + 12);
            this.generatorListField.setY(py + 48);
            this.generatorListField.setWidth(256);
            this.generatorListField.setHeight(108);
            this.generatorListField.extractRenderState(graphics, mouseX, mouseY, delta);
            if (!this.generatorResult.isBlank()) {
               this.drawText(graphics, this.generatorResult, px + 12, py + 182, -13248397, false, 180);
            }
         }
      }

      for (CompactOverlayButton button : this.popupButtons) {
         CompactOverlayButton.renderStyled(graphics, this.font, button, mouseX, mouseY);
      }

      CompactDropdown.renderButtons(graphics, this.font, this.popupDropdowns, rawMouseX, rawMouseY);
      CompactDropdown.renderOpenMenu(graphics, this.font, this.popupDropdowns, rawMouseX, rawMouseY);
   }

   private boolean passwordFieldVisible() {
      RiptideConfig config = RiptideConfig.getGlobal();
      return this.activePopup == 1 && this.generatorMode == 0 && config.accountGenSetPassword && "Set For All".equalsIgnoreCase(config.accountGenPasswordMode);
   }

   private void handlePopupMouse(MouseButtonEvent event, boolean doubleClick) {
      if (this.activePopup != 1 || !CompactDropdown.mouseClicked(this.popupDropdowns, event.x(), event.y(), event.button())) {
         for (CompactOverlayButton button : this.popupButtons) {
            if (CompactOverlayButton.fireIfHit(button, event.x(), event.y(), event.button())) {
               return;
            }
         }

         if (this.activePopup == 1) {
            if (this.generatorMode == 0) {
               if (this.passwordFieldVisible() && this.generatorPasswordField != null) {
                  if (this.generatorPasswordField.mouseClicked(event, doubleClick)) {
                     this.generatorPasswordField.setFocused(true);
                     if (this.generatorCountField != null) {
                        this.generatorCountField.setFocused(false);
                     }

                     return;
                  }

                  this.generatorPasswordField.setFocused(false);
               }

               if (this.generatorCountField != null && this.generatorCountField.mouseClicked(event, doubleClick)) {
                  this.generatorCountField.setFocused(true);
                  if (this.generatorPasswordField != null) {
                     this.generatorPasswordField.setFocused(false);
                  }

                  return;
               }

               if (this.generatorCountField != null) {
                  this.generatorCountField.setFocused(false);
               }
            } else if (this.generatorMode == 1 && this.generatorListField != null) {
               if (this.generatorListField.mouseClicked(event, doubleClick)) {
                  this.generatorListField.setFocused(true);
                  return;
               }

               this.generatorListField.setFocused(false);
            }
         }
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.activePopup != 0) {
         if (event.key() == 256) {
            this.closePopup();
            return true;
         } else {
            if (this.activePopup == 1) {
               if (this.generatorMode == 0
                  && this.passwordFieldVisible()
                  && this.generatorPasswordField != null
                  && this.generatorPasswordField.keyPressed(event)) {
                  return true;
               }

               if (this.generatorMode == 0 && this.generatorCountField != null && this.generatorCountField.keyPressed(event)) {
                  return true;
               }

               if (this.generatorMode == 1 && this.generatorListField != null && this.generatorListField.keyPressed(event)) {
                  return true;
               }
            }

            return true;
         }
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean charTyped(CharacterEvent event) {
      if (this.activePopup != 0) {
         if (this.activePopup == 1) {
            if (this.generatorMode == 0 && this.passwordFieldVisible() && this.generatorPasswordField != null && this.generatorPasswordField.charTyped(event)) {
               return true;
            }

            if (this.generatorMode == 0 && this.generatorCountField != null && this.generatorCountField.charTyped(event)) {
               return true;
            }

            if (this.generatorMode == 1 && this.generatorListField != null && this.generatorListField.charTyped(event)) {
               return true;
            }
         }

         return true;
      } else {
         return super.charTyped(event);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.accountScrollbarDragging) {
         this.accountScrollbarDragging = false;
         return true;
      } else if (this.previewDragging) {
         this.previewAutoRotationStartTime = Blaze3D.getTime();
         this.previewDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent(event));
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.accountScrollbarDragging) {
         CompactScrollbar.Metrics scrollbar = this.accountScrollbarMetrics(this.displayAccounts().size());
         this.savedListScrollOffset = quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), this.accountScrollbarGrabOffset), 24, scrollbar.maxScroll()
         );
         this.savedListScroll.jumpTo(this.savedListScrollOffset, scrollbar.maxScroll());
         this.rebuildButtons();
         return true;
      } else if (this.previewDragging) {
         this.previewRotationX = Math.max(-50.0F, Math.min(50.0F, this.previewRotationX - (float)RiptideUiScale.toVirtual(dy) * 2.5F));
         this.previewRotationY = this.previewRotationY + (float)RiptideUiScale.toVirtual(dx) * 2.5F;
         this.lastPreviewMouseX = virtualEvent.x();
         this.lastPreviewMouseY = virtualEvent.y();
         return true;
      } else {
         return super.mouseDragged(virtualEvent, RiptideUiScale.toVirtual(dx), RiptideUiScale.toVirtual(dy));
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      x = RiptideUiScale.toVirtual(x);
      y = RiptideUiScale.toVirtual(y);
      if (this.activePopup != 0) {
         CompactDropdown.mouseScrolled(this.popupDropdowns, x, y, scrollY);
         return true;
      } else if (this.compactListLayout()) {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      } else if (!(x < this.listX()) && !(x >= this.listX() + this.listWidth()) && !(y < this.listTop()) && !(y >= this.listTop() + this.listPanelHeight())) {
         int maxScroll = this.savedMaxScroll(this.displayAccounts().size());
         if (maxScroll <= 0) {
            return true;
         } else {
            int next = this.savedListScrollOffset - (int)Math.signum(scrollY) * 24;
            this.savedListScrollOffset = quantizeScrollOffset(next, 24, maxScroll);
            this.savedListScroll.setTarget(this.savedListScrollOffset, maxScroll);
            this.rebuildButtons();
            return true;
         }
      } else {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      }
   }

   public void onClose() {
      this.cancelOperation();
      this.accountScrollbarDragging = false;
      this.pruneSkinLookups();
      this.minecraft.gui.setScreen(this.parent);
   }

   private RiptideAccountsScreen.SkinLookup skinLookup(RiptideAccount account) {
      String key = this.skinKey(account);
      synchronized (this.skinLookups) {
         RiptideAccountsScreen.SkinLookup lookup = this.skinLookups.computeIfAbsent(key, ignored -> this.createSkinLookup(account));
         this.pruneSkinLookups();
         return lookup;
      }
   }

   private RiptideAccountsScreen.SkinLookup createSkinLookup(RiptideAccount account) {
      UUID id = this.accountUuid(account);
      String name = account != null && !account.displayName().isBlank() ? account.displayName() : "Riptide";
      if (account != null && account.type == RiptideAccountType.Cracked && !name.isBlank() && this.minecraft != null) {
         UUID offlineId = UUIDUtil.createOfflinePlayerUUID(name);
         PlayerSkin defaultSkin = DefaultPlayerSkin.get(offlineId);

         try {
            CompletableFuture<Optional<PlayerSkin>> future = CompletableFuture.<Optional<GameProfile>>supplyAsync(
                  () -> this.resolveCrackedSkinProfile(name), Util.nonCriticalIoPool()
               )
               .thenCompose(profilex -> profilex.map(this.minecraft.getSkinManager()::get).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
            return new RiptideAccountsScreen.SkinLookup(() -> {
               try {
                  return future.getNow(Optional.empty()).orElse(defaultSkin);
               } catch (Exception var3x) {
                  return defaultSkin;
               }
            }, future, defaultSkin);
         } catch (Exception var10) {
            return new RiptideAccountsScreen.SkinLookup(() -> defaultSkin, CompletableFuture.completedFuture(Optional.empty()), defaultSkin);
         }
      } else if (account != null && !name.isBlank() && this.minecraft != null && this.isDefaultAccount(account)) {
         UUID offlineId = UUIDUtil.createOfflinePlayerUUID(name);
         PlayerSkin defaultSkin = DefaultPlayerSkin.get(id == null ? offlineId : id);
         ResolvableProfile profile = id == null ? ResolvableProfile.createUnresolved(name) : ResolvableProfile.createUnresolved(id);

         try {
            RenderInfo defaultInfo = this.minecraft.playerSkinRenderCache().getOrDefault(profile);
            Supplier<RenderInfo> lookup = this.minecraft.playerSkinRenderCache().createLookup(profile);
            CompletableFuture<Optional<RenderInfo>> future = this.minecraft.playerSkinRenderCache().lookup(profile);
            return new RiptideAccountsScreen.SkinLookup(() -> {
               try {
                  RenderInfo info = lookup.get();
                  return info == null ? defaultSkin : info.playerSkin();
               } catch (Exception var3x) {
                  return defaultSkin;
               }
            }, future, defaultInfo.playerSkin());
         } catch (Exception var11) {
            return new RiptideAccountsScreen.SkinLookup(() -> defaultSkin, CompletableFuture.completedFuture(Optional.empty()), defaultSkin);
         }
      } else if (id == null) {
         UUID fallbackId = UUIDUtil.createOfflinePlayerUUID(name);
         PlayerSkin fallback = DefaultPlayerSkin.get(fallbackId);
         return new RiptideAccountsScreen.SkinLookup(() -> fallback, CompletableFuture.completedFuture(Optional.empty()), fallback);
      } else {
         try {
            PlayerSkin fallback = DefaultPlayerSkin.get(id);
            CompletableFuture<Optional<PlayerSkin>> future = CompletableFuture.<GameProfile>supplyAsync(
                  () -> this.minecraft.services().profileResolver().fetchById(id).orElse(new GameProfile(id, name)), Util.nonCriticalIoPool()
               )
               .thenCompose(profilex -> this.minecraft.getSkinManager().get(profilex));
            return new RiptideAccountsScreen.SkinLookup(() -> {
               try {
                  return future.getNow(Optional.empty()).orElse(fallback);
               } catch (Exception var3x) {
                  return fallback;
               }
            }, future, fallback);
         } catch (Exception var12) {
            PlayerSkin fallbackx = DefaultPlayerSkin.get(id);
            return new RiptideAccountsScreen.SkinLookup(() -> fallback, CompletableFuture.completedFuture(Optional.empty()), fallbackx);
         }
      }
   }

   private PlayerSkin fallbackSkin(RiptideAccount account) {
      String name = account != null && !account.displayName().isBlank() ? account.displayName() : "Riptide";
      UUID id = this.accountUuid(account);
      if (id == null || account != null && account.type == RiptideAccountType.Cracked) {
         id = UUIDUtil.createOfflinePlayerUUID(name);
      }

      return DefaultPlayerSkin.get(id);
   }

   private Optional<GameProfile> resolveCrackedSkinProfile(String name) {
      String username = safeTrim(name);
      if (isValidMinecraftUsername(username) && this.minecraft != null) {
         try {
            Optional<GameProfile> resolved = this.minecraft.services().profileResolver().fetchByName(username);
            if (resolved.isPresent() && resolved.get().properties().containsKey("textures")) {
               return resolved;
            }

            Optional<GameProfile> withTextures = resolved.flatMap(this::withMojangTextures);
            if (withTextures.isPresent()) {
               return withTextures;
            }
         } catch (Exception var5) {
         }

         return this.fetchMojangProfileByName(username);
      } else {
         return Optional.empty();
      }
   }

   private Optional<GameProfile> fetchMojangProfileByName(String username) {
      try {
         String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
         JsonObject profile = RiptideHttp.getJsonDirect("https://api.mojang.com/users/profiles/minecraft/" + encoded, null);
         if (profile != null && profile.has("id") && profile.has("name")) {
            UUID id = UndashedUuid.fromStringLenient(profile.get("id").getAsString());
            String resolvedName = profile.get("name").getAsString();
            return this.withMojangTextures(new GameProfile(id, resolvedName));
         } else {
            return Optional.empty();
         }
      } catch (Exception var6) {
         return Optional.empty();
      }
   }

   private Optional<GameProfile> withMojangTextures(GameProfile profile) {
      if (profile != null && profile.id() != null) {
         if (profile.properties().containsKey("textures")) {
            return Optional.of(profile);
         } else {
            try {
               String id = UndashedUuid.toString(profile.id());
               JsonObject textureProfile = RiptideHttp.getJsonDirect(
                  "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false", null
               );
               if (textureProfile == null || !textureProfile.has("properties") || !textureProfile.get("properties").isJsonArray()) {
                  return Optional.empty();
               }

               for (JsonElement element : textureProfile.getAsJsonArray("properties")) {
                  if (element.isJsonObject()) {
                     JsonObject property = element.getAsJsonObject();
                     String propertyName = jsonString(property, "name");
                     String value = jsonString(property, "value");
                     String signature = jsonString(property, "signature");
                     if ("textures".equals(propertyName) && !value.isBlank() && !signature.isBlank()) {
                        String resolvedName = jsonString(textureProfile, "name");
                        GameProfile texturedProfile = new GameProfile(profile.id(), resolvedName.isBlank() ? profile.name() : resolvedName);
                        texturedProfile.properties().put("textures", new Property("textures", value, signature));
                        return Optional.of(texturedProfile);
                     }
                  }
               }
            } catch (Exception var13) {
            }

            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private static boolean isValidMinecraftUsername(String username) {
      if (username != null && username.length() >= 3 && username.length() <= 16) {
         for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static String jsonString(JsonObject object, String key) {
      if (object != null && key != null && object.has(key)) {
         JsonElement element = object.get(key);
         return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
      } else {
         return "";
      }
   }

   private String skinKey(RiptideAccount account) {
      return account == null
         ? "empty"
         : (this.isDefaultAccount(account) ? "default" : account.type.name())
            + ":"
            + safeTrim(account.username)
            + ":"
            + safeTrim(account.uuid)
            + ":"
            + safeTrim(account.label);
   }

   private void pruneSkinLookups() {
      synchronized (this.skinLookups) {
         if (this.skinLookups.size() > 10) {
            List<String> protectedKeys = this.protectedSkinKeys();
            Iterator<Entry<String, RiptideAccountsScreen.SkinLookup>> iterator = this.skinLookups.entrySet().iterator();

            while (this.skinLookups.size() > 10 && iterator.hasNext()) {
               Entry<String, RiptideAccountsScreen.SkinLookup> entry = iterator.next();
               if (!protectedKeys.contains(entry.getKey())) {
                  iterator.remove();
               }
            }
         }
      }
   }

   private List<String> protectedSkinKeys() {
      List<String> keys = new ArrayList<>();
      RiptideAccount preview = this.previewAccount();
      this.addProtectedSkinKey(keys, preview);

      for (RiptideAccountsScreen.AccountRow row : this.accountRows) {
         this.addProtectedSkinKey(keys, row.account);
      }

      RiptideAccount defaultAccount = this.defaultMinecraftAccount();
      this.addProtectedSkinKey(keys, defaultAccount);
      return keys;
   }

   private void addProtectedSkinKey(List<String> keys, RiptideAccount account) {
      if (keys != null && account != null && keys.size() < 10) {
         String key = this.skinKey(account);
         if (!keys.contains(key)) {
            keys.add(key);
         }
      }
   }

   private List<RiptideAccount> filteredAccounts() {
      List<RiptideAccount> accounts = this.accountSnapshot();
      String query = normalizeSearch(this.searchQuery);
      int filterMask = this.accountFilterMask();
      if (this.cachedFilteredRevision == this.accountSnapshotRevision && query.equals(this.cachedAccountQuery) && filterMask == this.cachedAccountFilterMask) {
         return this.cachedFilteredAccounts;
      } else {
         this.cachedFilteredRevision = this.accountSnapshotRevision;
         this.cachedAccountQuery = query;
         this.cachedAccountFilterMask = filterMask;
         this.cachedDisplaySource = null;
         if (query.isEmpty() && this.categoryFilter == null) {
            this.cachedFilteredAccounts = accounts;
            return this.cachedFilteredAccounts;
         } else {
            List<RiptideAccount> filtered = new ArrayList<>();

            for (RiptideAccount account : accounts) {
               if (account != null
                  && (this.categoryFilter == null || account.type == this.categoryFilter)
                  && (query.isEmpty() || this.matchesNickname(account, query))) {
                  filtered.add(account);
               }
            }

            this.cachedFilteredAccounts = List.copyOf(filtered);
            return this.cachedFilteredAccounts;
         }
      }
   }

   private List<RiptideAccountsScreen.DisplayAccountRow> displayAccounts() {
      List<RiptideAccount> filtered = this.filteredAccounts();
      if (this.cachedDisplaySource == filtered) {
         return this.cachedDisplayAccounts;
      } else {
         List<RiptideAccountsScreen.DisplayAccountRow> rows = new ArrayList<>();
         RiptideAccount defaultAccount = this.defaultMinecraftAccount();
         if (defaultAccount != null) {
            rows.add(new RiptideAccountsScreen.DisplayAccountRow(defaultAccount, true));
         }

         for (RiptideAccount account : filtered) {
            rows.add(new RiptideAccountsScreen.DisplayAccountRow(account, false));
         }

         this.cachedDisplaySource = filtered;
         this.cachedDisplayAccounts = List.copyOf(rows);
         return this.cachedDisplayAccounts;
      }
   }

   private List<RiptideAccount> accountSnapshot() {
      if (!this.accountSnapshotDirty) {
         return this.cachedAccountSnapshot;
      } else {
         this.cachedAccountSnapshot = List.copyOf(RiptideAccountManager.get().all());
         this.accountSnapshotDirty = false;
         this.accountSnapshotRevision++;
         this.cachedFilteredRevision = Long.MIN_VALUE;
         this.cachedDisplaySource = null;
         return this.cachedAccountSnapshot;
      }
   }

   private void invalidateAccountSnapshot() {
      this.accountSnapshotDirty = true;
   }

   private int accountFilterMask() {
      return this.categoryFilter == null ? -1 : 1 << this.categoryFilter.ordinal();
   }

   private boolean matchesNickname(RiptideAccount account, String query) {
      String name = normalizeSearch(account == null ? "" : account.displayName());
      if (query.isEmpty()) {
         return true;
      } else {
         return name.contains(query) ? true : fuzzyContains(name, query);
      }
   }

   private static boolean fuzzyContains(String value, String query) {
      if (value != null && query != null && !query.isEmpty()) {
         int valueIndex = 0;
         int queryIndex = 0;
         int misses = 0;

         for (int maxMisses = Math.max(1, query.length() / 3); valueIndex < value.length() && queryIndex < query.length(); valueIndex++) {
            if (value.charAt(valueIndex) == query.charAt(queryIndex)) {
               queryIndex++;
            } else if (queryIndex > 0) {
               misses++;
            }

            if (misses > maxMisses) {
               return false;
            }
         }

         return queryIndex == query.length();
      } else {
         return true;
      }
   }

   private static String normalizeSearch(String value) {
      return safeTrim(value).toLowerCase(Locale.ROOT);
   }

   private UUID accountUuid(RiptideAccount account) {
      if (account != null && account.uuid != null && !account.uuid.isBlank()) {
         try {
            return UndashedUuid.fromStringLenient(account.uuid);
         } catch (Exception var5) {
            try {
               return UUID.fromString(account.uuid);
            } catch (Exception var4) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private RiptideAccount previewAccount() {
      if (this.selectedAccount != null) {
         return this.selectedAccount;
      } else {
         RiptideAccount defaultAccount = this.defaultMinecraftAccount();
         if (defaultAccount != null && this.isCurrentDefaultAccount()) {
            return defaultAccount;
         } else {
            List<RiptideAccount> accounts = this.accountSnapshot();

            for (RiptideAccount account : accounts) {
               if (this.isCurrentAccount(account)) {
                  return account;
               }
            }

            if (defaultAccount != null) {
               return defaultAccount;
            } else {
               return accounts.isEmpty() ? null : accounts.get(0);
            }
         }
      }
   }

   private boolean isCurrentAccount(RiptideAccount account) {
      if (account != null && this.minecraft != null && this.minecraft.getUser() != null) {
         UUID accountId = this.accountUuid(account);
         if (accountId != null) {
            return accountId.equals(this.minecraft.getUser().getProfileId());
         } else {
            String currentName = this.minecraft.getUser().getName();
            String accountName = safeTrim(account.username);
            if (accountName.isBlank()) {
               accountName = safeTrim(account.label);
            }

            return currentName != null && !accountName.isBlank() && currentName.equals(accountName);
         }
      } else {
         return false;
      }
   }

   private boolean isCurrentDefaultAccount() {
      User original = RiptideAccountSessionSwitcher.getOriginalUser();
      User current = this.minecraft == null ? null : this.minecraft.getUser();
      return original != null && current != null && original.getProfileId().equals(current.getProfileId()) && original.getName().equals(current.getName());
   }

   private boolean isDefaultAccount(RiptideAccount account) {
      RiptideAccount defaultAccount = this.defaultMinecraftAccount();
      return defaultAccount != null
         && account != null
         && defaultAccount.uuid.equals(account.uuid)
         && defaultAccount.username.equals(account.username)
         && account.label != null
         && account.label.startsWith("Default Minecraft:");
   }

   private RiptideAccount defaultMinecraftAccount() {
      User user = RiptideAccountSessionSwitcher.getOriginalUser();
      if (user == null) {
         return null;
      } else {
         RiptideAccount account = new RiptideAccount();
         account.type = safeTrim(user.getAccessToken()).isBlank() ? RiptideAccountType.Cracked : RiptideAccountType.Microsoft;
         account.label = "Default Minecraft: " + user.getName();
         account.username = user.getName();
         account.uuid = user.getProfileId().toString();
         account.token = user.getAccessToken();
         return account;
      }
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelWidth(), 12);
   }

   private int panelWidth() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 12, 520);
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int listWidth() {
      return Math.max(1, this.panelWidth() - 20);
   }

   private int previewX() {
      return this.panelX() + 10 + 292 + 8;
   }

   private boolean isInPreview(double x, double y) {
      if (this.narrowLayout()) {
         return false;
      } else {
         int previewX = this.previewX();
         return x >= previewX && x < previewX + 200 && y >= 20.0 && y < 198.0;
      }
   }

   private int listPanelHeight() {
      return Math.max(1, this.screenHeight() - this.listTop() - 12);
   }

   private int listTop() {
      return this.narrowLayout() ? 48 : 204;
   }

   private int listRowTop() {
      return this.listTop() + 22;
   }

   private int savedRowsTop() {
      return this.listRowTop();
   }

   private int savedRowsBottom() {
      return this.listTop() + this.listPanelHeight() - 6;
   }

   private int savedViewportHeight() {
      return Math.max(24, alignViewportHeight(Math.max(1, this.savedRowsBottom() - this.savedRowsTop()), 24));
   }

   private int savedViewportRows() {
      return Math.max(1, this.savedViewportHeight() / 24);
   }

   private int savedMaxScroll(int savedRows) {
      return Math.max(0, savedRows * 24 - this.savedViewportHeight());
   }

   private CompactScrollbar.Metrics accountScrollbarMetrics(int savedRows) {
      int contentPixels = Math.max(0, savedRows) * 24;
      int viewPixels = this.savedViewportHeight();
      int trackX = this.listX() + this.listWidth() - 8;
      int trackY = this.savedRowsTop();
      int trackHeight = this.savedViewportHeight();
      return CompactScrollbar.compute(
         contentPixels, viewPixels, trackX, trackY, 4, trackHeight, this.savedListScroll.tick(0.0F, this.savedMaxScroll(savedRows))
      );
   }

   private int rowX() {
      return this.listX() + 8;
   }

   private int rowRight() {
      return this.listX() + this.listWidth() - 8 - 12;
   }

   private int rowWidth() {
      return Math.max(1, this.rowRight() - this.rowX());
   }

   private int rowVisualHeight() {
      return 22;
   }

   private int rowButtonY(int rowY, int buttonHeight) {
      return rowY + Math.max(1, (this.rowVisualHeight() - buttonHeight) / 2);
   }

   private boolean narrowLayout() {
      return this.panelWidth() < 520 || this.screenHeight() < 248;
   }

   private boolean compactListLayout() {
      return this.listWidth() < 190 || this.listPanelHeight() < 46;
   }

   private static int alignViewportHeight(int height, int step) {
      return step <= 0 ? Math.max(1, height) : Math.max(step, Math.max(1, height) / step * step);
   }

   private static int quantizeScrollOffset(int value, int step, int maxScroll) {
      int clamped = Math.max(0, Math.min(maxScroll, value));
      if (step <= 0) {
         return clamped;
      } else {
         int rounded = Math.round((float)clamped / step) * step;
         return Math.max(0, Math.min(maxScroll, rounded));
      }
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
      this.drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
      Font renderer = this.font;
      Identifier font = THEME.fontFor(UiTone.BODY);
      String value = text == null ? "" : text;
      if (maxWidth != Integer.MAX_VALUE && !center) {
         UiText.drawFitted(graphics, renderer, value, font, color, x, y, Math.max(1, maxWidth), false);
      } else {
         if (maxWidth != Integer.MAX_VALUE) {
            value = UiText.trimToWidth(renderer, value, maxWidth, font, color);
         }

         int w = UiText.width(renderer, value, font, color);
         int drawX = center ? x - w / 2 : x;
         UiText.draw(graphics, renderer, value, font, color, drawX, y, false);
      }
   }

   private void clearInputFocus() {
      if (this.labelField != null) {
         this.labelField.setFocused(false);
      }

      if (this.tokenField != null) {
         this.tokenField.setFocused(false);
      }

      if (this.searchField != null) {
         this.searchField.setFocused(false);
      }

      this.setFocused(null);
   }

   private String inputLabel() {
      return switch (this.type) {
         case Cracked, Generated -> "Cracked username";
         case TheAltening -> "TheAltening token";
         case Session -> "Session access token";
         case Microsoft -> "";
      };
   }

   private String addButtonLabel() {
      if (this.renamingAccount != null) {
         return "Rename";
      } else {
         return switch (this.type) {
            case Cracked, Generated -> "Add Cracked";
            case TheAltening -> "Add Altening";
            case Session -> "Add Session";
            case Microsoft -> "Login with Microsoft";
         };
      }
   }

   private static int outlineColor(int argb) {
      return RiptideTheme.recolor(argb, RiptideTheme.Channel.OUTLINE);
   }

   private static int successColor(int argb) {
      return RiptideTheme.recolor(argb, RiptideTheme.Channel.SUCCESS);
   }

   private record AccountRow(
      RiptideAccount account,
      int y,
      CompactOverlayButton loginButton,
      CompactOverlayButton deleteButton,
      CompactOverlayButton shareButton,
      CompactOverlayButton renameButton,
      boolean defaultAccount,
      boolean loadSkin
   ) {
   }

   private record DisplayAccountRow(RiptideAccount account, boolean defaultAccount) {
   }

   private static enum Operation {
      NONE,
      ADD,
      LOGIN,
      MICROSOFT,
      CHECK;
   }

   private record SkinLookup(Supplier<PlayerSkin> supplier, CompletableFuture<?> future, PlayerSkin fallback) {
      private PlayerSkin skin() {
         try {
            PlayerSkin skin = this.supplier.get();
            return skin == null ? this.fallback : skin;
         } catch (Exception var2) {
            return this.fallback;
         }
      }

      private boolean loading() {
         return this.future != null && !this.future.isDone();
      }
   }
}
