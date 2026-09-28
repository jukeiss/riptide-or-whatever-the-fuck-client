package riptide.mixin;

import java.util.ArrayList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.gui.vanillaui.components.Banner;
import riptide.modules.AntiVanishModule;
import riptide.modules.Module;
import riptide.modules.ModuleNameTagRenderer;
import riptide.modules.ModuleRegistry;
import riptide.modules.ModuleRenderUtil;
import riptide.modules.ModuleScreenRenderer;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.RiptideCaptureBannerSpec;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideMacroProgressRenderer;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePayloadStudySession;
import riptide.util.RiptidePerf;
import riptide.util.RiptideQueueRenderer;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideUiScale;
import riptide.util.macro.MacroExecutor;

@Mixin({Hud.class})
public abstract class RiptideInGameHudMixin {
   @Unique
   private static final Minecraft MC = Minecraft.getInstance();
   @Unique
   private static final int PACKUTIL_RIGHT_PANEL_W = 172;
   @Unique
   private static long riptide$lastRenderErrorMs;
   @Unique
   private BlockState riptide$cachedBlockState;
   @Unique
   private String riptide$cachedBlockName = "";
   @Unique
   private EntityType<?> riptide$cachedEntityType;
   @Unique
   private String riptide$cachedEntityLabel = "";

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void yang$renderRiptideQueue(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
      try {
         this.riptide$renderHudBody(context);
      } catch (Throwable var5) {
         this.riptide$logRenderError("hudRoot", var5);
      }
   }

   @Unique
   private void riptide$renderHudBody(GuiGraphicsExtractor context) {
      if (this.isRiptideActive()) {
         boolean macroFrameWork = MacroExecutor.hasRenderWork();
         RiptideRuntimeActivity.Snapshot activity = RiptideRuntimeActivity.current();
         long hudWork = 2496L;
         if (macroFrameWork || activity.has(hudWork)) {
            UiScissorStack.global().clear(context);
            if (macroFrameWork) {
               MacroExecutor.onRender(1.0F);
            }

            if (!MC.gui.hud.isHidden()) {
               if (!PackHideState.isActive()) {
                  RiptideSharedState shared = RiptideSharedState.get();
                  Screen screen = MC.gui.screen();
                  boolean macroRunning = MacroExecutor.isVisibleRunning();
                  boolean queueSending = shared.hasStaggeredPackets();
                  boolean queueVisible = shared.shouldDelayGuiPackets() || shared.hasDelayedPackets() || queueSending;
                  boolean captureActive = this.hasAnyCaptureSession(shared);
                  boolean payloadStudyActive = RiptidePayloadStudySession.isActive();
                  Module hud = activity.has(64L) ? ModuleRegistry.get("hud") : null;
                  boolean nativeHudVisible = hud != null && RiptideHudManager.shouldRenderInGame(screen, hud);
                  boolean esp2dVisible = ModuleRenderUtil.has2dEspWork();
                  boolean nametagsVisible = activity.has(128L);
                  boolean antiVanishHudVisible = screen == null && AntiVanishModule.shouldShowHud();
                  boolean mainHudVisible = nativeHudVisible
                     || antiVanishHudVisible
                     || macroRunning
                     || queueVisible
                     || captureActive
                     || payloadStudyActive
                     || esp2dVisible
                     || nametagsVisible;
                  RiptideServerInfoOverlay serverInfoOverlay = null;
                  boolean serverProbeBannerVisible = false;
                  RiptideOverlayManager overlayManager = null;
                  boolean overlayVisible = false;
                  if (screen == null) {
                     serverInfoOverlay = RiptideModule.get().getServerDataOverlayIfExists();
                     serverProbeBannerVisible = serverInfoOverlay != null && serverInfoOverlay.shouldRenderBackgroundProbeBanner();
                     if (activity.has(256L)) {
                        overlayManager = RiptideOverlayManager.get();
                        overlayVisible = overlayManager.hasVisibleOverlay();
                     }
                  }

                  boolean notificationsVisible = RiptideNotifications.hasVisible();
                  if (mainHudVisible || serverProbeBannerVisible || overlayVisible || notificationsVisible) {
                     Runnable renderHudElements = () -> {
                        int screenWidth = RiptideUiScale.getVirtualScreenWidth();
                        int x = Math.max(0, screenWidth - 172);
                        int y = 0;
                        RiptideCaptureBannerSpec captureBanner = captureActive ? this.captureBannerSpec(shared, context) : null;
                        RiptideCaptureBannerSpec payloadStudyBanner = payloadStudyActive
                           ? this.payloadStudyBannerSpec(context, captureBanner == null ? 0 : captureBanner.height())
                           : null;
                        ArrayList<RiptideHudManager.ElementBounds> hudOccluders = new ArrayList<>(3);
                        if (captureBanner != null) {
                           hudOccluders.add(
                              new RiptideHudManager.ElementBounds(
                                 "capture_banner", captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()
                              )
                           );
                        }

                        if (payloadStudyBanner != null) {
                           hudOccluders.add(
                              new RiptideHudManager.ElementBounds(
                                 "payload_study_banner",
                                 payloadStudyBanner.x(),
                                 payloadStudyBanner.y(),
                                 payloadStudyBanner.width(),
                                 payloadStudyBanner.height()
                              )
                           );
                        } else if (captureActive) {
                           int fallbackW = Math.min(screenWidth - 16, 300);
                           hudOccluders.add(new RiptideHudManager.ElementBounds("capture_banner", Math.max(0, (screenWidth - fallbackW) / 2), 0, fallbackW, 56));
                        }

                        if (queueVisible) {
                           int queueHeight = RiptideQueueRenderer.measureStacked(MC.font, 172, 8);
                           if (queueHeight > 0) {
                              hudOccluders.add(new RiptideHudManager.ElementBounds("packet_queue", x, y, 172, queueHeight));
                              y += queueHeight;
                           }
                        }

                        if (macroRunning) {
                           int macroHeight = RiptideMacroProgressRenderer.measureStacked(MC.font, 172, 10);
                           if (macroHeight > 0) {
                              hudOccluders.add(new RiptideHudManager.ElementBounds("macro_queue", x, y, 172, macroHeight));
                           }
                        }

                        if (nativeHudVisible) {
                           RiptideHudManager.render(context, MC.font, false, null, -1, -1, hudOccluders);
                        } else if (antiVanishHudVisible) {
                           RiptideHudManager.renderSingle(context, MC.font, "anti_vanish");
                        }

                        if (captureBanner != null) {
                           Banner.render(
                              UiContexts.overlay(context, MC.font, 0, 0),
                              UiBounds.of(captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()),
                              captureBanner.title(),
                              captureBanner.line1(),
                              captureBanner.line2()
                           );
                        }

                        if (payloadStudyBanner != null) {
                           Banner.render(
                              UiContexts.overlay(context, MC.font, 0, 0),
                              UiBounds.of(payloadStudyBanner.x(), payloadStudyBanner.y(), payloadStudyBanner.width(), payloadStudyBanner.height()),
                              payloadStudyBanner.title(),
                              payloadStudyBanner.line1(),
                              payloadStudyBanner.line2()
                           );
                        }

                        y = 0;
                        if (queueVisible) {
                           int queueHeight = RiptideQueueRenderer.renderStacked(context, MC.font, x, y, 172, 8, false, !macroRunning, false);
                           y += queueHeight;
                        }

                        if (macroRunning) {
                           RiptideMacroProgressRenderer.renderStacked(context, MC.font, x, y, 172, 10, false, true, false);
                        }

                        if (esp2dVisible) {
                           ModuleScreenRenderer.render(context);
                        }

                        if (nametagsVisible) {
                           ModuleNameTagRenderer.render(context);
                        }
                     };
                     if (mainHudVisible) {
                        long perfStart = RiptidePerf.beginSampled();
                        RiptideUiScale.pushOverlayScale(context);

                        try {
                           renderHudElements.run();
                        } catch (Throwable var57) {
                           this.riptide$logRenderError("hudElements", var57);
                        } finally {
                           RiptideUiScale.popOverlayScale(context);
                        }

                        RiptidePerf.end("hud.section.hudElements", perfStart);
                     }

                     if (MC.gui.screen() == null) {
                        if (serverProbeBannerVisible) {
                           long perfStart = RiptidePerf.beginSampled();
                           RiptideUiScale.pushOverlayScale(context);

                           try {
                              serverInfoOverlay.renderBackgroundProbeBanner(context);
                           } catch (Throwable var55) {
                              this.riptide$logRenderError("probeBanner", var55);
                           } finally {
                              RiptideUiScale.popOverlayScale(context);
                           }

                           RiptidePerf.end("hud.section.probeBanner", perfStart);
                        }

                        if (overlayVisible) {
                           long perfStart = RiptidePerf.beginSampled();

                           try {
                              overlayManager.renderAll(context, -1, -1, 0.0F);
                           } catch (Throwable var54) {
                              this.riptide$logRenderError("overlays", var54);
                           }

                           RiptidePerf.end("hud.section.overlays", perfStart);
                        }
                     }

                     if (notificationsVisible) {
                        long perfStart = RiptidePerf.beginSampled();
                        RiptideUiScale.pushOverlayScale(context);

                        try {
                           RiptideNotifications.render(context);
                        } catch (Throwable var52) {
                           this.riptide$logRenderError("notifications", var52);
                        } finally {
                           RiptideUiScale.popOverlayScale(context);
                        }

                        RiptidePerf.end("hud.section.notifications", perfStart);
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private void riptide$logRenderError(String where, Throwable t) {
      long now = System.currentTimeMillis();
      if (now - riptide$lastRenderErrorMs >= 5000L) {
         riptide$lastRenderErrorMs = now;
         riptide.RiptideClientAddon.LOG.warn("[Riptide] HUD section '{}' failed; isolated to protect the UI", where, t);
      }
   }

   @Unique
   private boolean isRiptideActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.isActive();
   }

   @Unique
   private RiptideCaptureBannerSpec captureBannerSpec(RiptideSharedState shared, GuiGraphicsExtractor graphics) {
      boolean blockCap = shared.hasBlockCaptureCallback();
      boolean entityCap = shared.hasEntityCaptureCallback();
      boolean attackCap = shared.hasAttackCaptureCallback();
      boolean gbreakCap = shared.isGBreakCapturing();
      if (!blockCap && !entityCap && !attackCap && !gbreakCap) {
         return null;
      } else {
         String title = gbreakCap ? "GBreak Capture" : (blockCap ? "Block Capture" : (entityCap ? "Entity Capture" : "Position Capture"));
         String line1 = gbreakCap
            ? "Break a block to capture the insta-break packet. Esc = cancel"
            : (
               blockCap
                  ? "Right-click a block to capture it. Esc = cancel"
                  : (entityCap ? "Right-click an entity to capture it. Esc = cancel" : "Left-click to capture the target position. Esc = cancel")
            );
         String line2 = "";
         if (gbreakCap) {
            line2 = "Waiting for the block-break packet from your next block break";
         } else if (blockCap && MC.hitResult != null && MC.hitResult.getType() == Type.BLOCK && MC.level != null) {
            BlockHitResult bhr = (BlockHitResult)MC.hitResult;
            BlockPos bp = bhr.getBlockPos();
            BlockState state = MC.level.getBlockState(bp);
            if (state != this.riptide$cachedBlockState) {
               this.riptide$cachedBlockName = state.getBlock().getName().getString();
               this.riptide$cachedBlockState = state;
            }

            line2 = "Aimed at: " + this.riptide$cachedBlockName + " (" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + ")";
         } else if (entityCap && MC.crosshairPickEntity != null && MC.crosshairPickEntity != MC.player) {
            EntityType<?> type = MC.crosshairPickEntity.getType();
            if (type != this.riptide$cachedEntityType) {
               String eName = type.getDescription().getString();
               String eId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
               this.riptide$cachedEntityLabel = eName + " (" + eId + ")";
               this.riptide$cachedEntityType = type;
            }

            line2 = "Aimed at: " + this.riptide$cachedEntityLabel;
         }

         int sw = RiptideUiScale.getVirtualScreenWidth();
         UiTextRenderer text = UiContexts.textRenderer(MC.font);
         int boxWidth = Math.min(sw - 16, Math.max(270, Math.max(text.width(title), Math.max(text.width(line1), line2.isEmpty() ? 0 : text.width(line2))) + 18));
         int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
         return new RiptideCaptureBannerSpec((sw - boxWidth) / 2, 0, boxWidth, height, title, line1, line2);
      }
   }

   @Unique
   private RiptideCaptureBannerSpec payloadStudyBannerSpec(GuiGraphicsExtractor graphics, int topOffset) {
      String title = RiptidePayloadStudySession.bannerTitle();
      if (title.isBlank()) {
         return null;
      } else {
         String line1 = RiptidePayloadStudySession.bannerLine1();
         String line2 = RiptidePayloadStudySession.bannerLine2();
         int sw = RiptideUiScale.getVirtualScreenWidth();
         UiTextRenderer text = UiContexts.textRenderer(MC.font);
         int boxWidth = Math.min(sw - 16, Math.max(276, Math.max(text.width(title), Math.max(text.width(line1), text.width(line2))) + 18));
         int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
         return new RiptideCaptureBannerSpec((sw - boxWidth) / 2, Math.max(0, topOffset), boxWidth, height, title, line1, line2);
      }
   }

   @Unique
   private boolean hasAnyCaptureSession(RiptideSharedState shared) {
      if (shared == null) {
         return false;
      } else if (!shared.isCaptureMode()
         && !shared.hasCaptureCancelCallback()
         && !shared.hasAttackCaptureCallback()
         && !shared.hasBlockCaptureCallback()
         && !shared.hasEntityCaptureCallback()
         && !shared.isGBreakCapturing()) {
         ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlayIfExists();
         return editor != null && editor.hasActiveCaptureSession();
      } else {
         return true;
      }
   }
}
