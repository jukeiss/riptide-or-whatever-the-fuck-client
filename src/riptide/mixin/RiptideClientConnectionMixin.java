package riptide.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.mixin.accessor.RiptideClientConnectionAccessor;
import riptide.modules.AutoLoginModule;
import riptide.modules.BuiltinModules;
import riptide.modules.Module;
import riptide.modules.ModuleEspChunkCache;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.modules.RiptideBlinkManager;
import riptide.modules.RiptideModule;
import riptide.modules.ScaffoldModule;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideResourcePackTruthGuard;
import riptide.security.RiptideSpoofPayloadFilter;
import riptide.util.RiptideClientWake;
import riptide.util.RiptideCommandSuggestionIds;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;
import riptide.util.custommenu.CustomMenuTracker;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.PacketGateManager;
import riptide.util.macro.PingSpoofController;
import riptide.util.macro.ServerTickTracker;
import riptide.util.macro.XCarryAction;
import riptide.util.multi.MultiConnectionContext;
import riptide.util.multi.MultiConnectionMarker;
import riptide.util.multi.PacketTeleportController;

@Mixin({Connection.class})
public abstract class RiptideClientConnectionMixin implements MultiConnectionMarker {
   @Unique
   private static final boolean RIPTIDE_PACKET_TRACE = Boolean.getBoolean("riptide.packet.trace");
   @Unique
   private volatile boolean riptide$spoofPipelineInstalled;
   @Unique
   private volatile boolean riptide$multiManaged;
   @Unique
   private volatile MultiConnectionContext.ProxySpec riptide$multiProxy;
   @Unique
   private volatile PacketListener riptide$protocolHintListener;
   @Unique
   private volatile String riptide$protocolHintCache = "";
   @Unique
   private static final String RIPTIDE_SPOOF_FILTER = "riptide_spoof_filter";
   @Unique
   private static volatile Module riptide$noFallCached;
   @Unique
   private static volatile int riptide$noFallRevision = -1;

   @Unique
   private static Module riptide$noFallModule() {
      int revision = ModuleRegistry.revision();
      if (revision != riptide$noFallRevision) {
         riptide$noFallCached = ModuleRegistry.get("no-fall");
         riptide$noFallRevision = revision;
      }

      return riptide$noFallCached;
   }

   @Unique
   private boolean riptide$isMultiConnectionOrChannel() {
      return this.riptide$multiManaged;
   }

   @Override
   public boolean riptide$isMultiManaged() {
      return this.riptide$multiManaged;
   }

   @Override
   public MultiConnectionContext.ProxySpec riptide$multiProxy() {
      return this.riptide$multiProxy;
   }

   @Override
   public void riptide$setMultiManaged(MultiConnectionContext.ProxySpec proxy) {
      this.riptide$multiProxy = proxy;
      this.riptide$multiManaged = true;
   }

   @Override
   public void riptide$clearMultiManaged() {
      this.riptide$multiManaged = false;
      this.riptide$multiProxy = null;
   }

   @Inject(
      method = {"channelActive"},
      at = {@At("HEAD")}
   )
   private void riptide$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
      this.riptide$spoofPipelineInstalled = false;
      MultiConnectionContext.bindChannel((Connection)this, context.channel());
      if (!this.riptide$isMultiConnectionOrChannel()) {
         this.riptide$ensureSpoofPipelineFilter();
      }
   }

   @Inject(
      method = {"channelInactive"},
      at = {@At("HEAD")}
   )
   private void riptide$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
      this.riptide$spoofPipelineInstalled = false;
      RiptideNetworkCaptureState.clearCodecSuppression();
      if (this.riptide$isMultiConnectionOrChannel()) {
         MultiConnectionContext.unbindChannel(context.channel());
      } else if (((Connection)this).getPacketListener() instanceof ClientGamePacketListener) {
         RiptideServerRotationView.reset();
         ScaffoldModule.onConnectionClosed();
         RiptideProtectorPackStrip.clearAll();
         RiptideResourcePackTruthGuard.clearAll();
      }
   }

   @Inject(
      method = {"doSendPacket"},
      at = {@At("HEAD")}
   )
   private void riptide$recordWrittenServerRotation(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         RiptideServerRotationView.onPacketWritten(packet);
         ScaffoldModule.onFinalPacketWritten(packet);
      }
   }

   @Inject(
      method = {"configurePacketHandler"},
      at = {@At("TAIL")}
   )
   private void riptide$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         this.riptide$ensureSpoofPipelineFilter();
      }
   }

   @ModifyVariable(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private Packet<?> riptide$silentUseItemRotation(Packet<?> packet) {
      if (PackHideState.isHardLocked()) {
         return packet;
      } else if (!this.riptide$isMultiConnectionOrChannel() && packet instanceof ServerboundUseItemPacket usePacket) {
         float yaw = usePacket.getYRot();
         float pitch = usePacket.getXRot();
         if (RiptideInputClicker.isFastExpUseInProgress()) {
            yaw = BuiltinModules.manualFastExpUseYaw(yaw);
            pitch = BuiltinModules.manualFastExpUsePitch(pitch);
         } else {
            RiptideRotationUtil.Rotation rotation = RiptideSilentAim.activeUseItemRotation(Minecraft.getInstance().player);
            if (rotation != null) {
               yaw = rotation.yaw();
               pitch = rotation.pitch();
            }
         }

         return (Packet<?>)(Float.compare(yaw, usePacket.getYRot()) == 0 && Float.compare(pitch, usePacket.getXRot()) == 0
            ? packet
            : new ServerboundUseItemPacket(usePacket.getHand(), usePacket.getSequence(), yaw, pitch));
      } else {
         return packet;
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         ScaffoldModule.onPacketQueued(packet);
         this.riptide$trackSentMessage(packet);
         this.riptide$ensureSpoofPipelineFilter();
         if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            if (RiptideResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
               ci.cancel();
               return;
            }

            RiptideProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
         }

         PacketListener packetListener = ((Connection)this).getPacketListener();
         RiptideModule module = RiptideModule.get();
         RiptideModule.PacketHookSnapshot hooks = module == null
            ? RiptideModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(this.isPlayConnectionActive());
         boolean normalLoggerPath = hooks.normalPath();
         String protocolHint = !hooks.packetLoggerCapturing() && !hooks.pluginDiscoveryObservation() ? "" : this.riptide$protocolHint(packetListener);
         if (packet instanceof ServerboundCustomPayloadPacket) {
            if (RiptideSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
               ci.cancel();
               return;
            }

            if (RiptideSpoofPayloadFilter.shouldDropForProtector(packet)) {
               ci.cancel();
               return;
            }
         }

         if (!PackHideState.isHardLocked()) {
            if (!PacketTeleportController.isControllerOwnedSend()) {
               if (PacketTeleportController.shouldSuppressMainMovement(packet)) {
                  ci.cancel();
               } else if (RiptideClientWake.isActive()
                  && packet instanceof ServerboundPlayerCommandPacket riptide$cmd
                  && riptide$cmd.getAction() == Action.STOP_SLEEPING) {
                  ci.cancel();
               } else {
                  if (packet instanceof ServerboundMovePlayerPacket) {
                     Module riptide$noFall = riptide$noFallModule();
                     if (riptide$noFall != null && riptide$noFall.isEnabled()) {
                        riptide$noFall.onPacketSend(packet);
                     }
                  }

                  if (!this.riptide$isLocalClientPlayConnection()
                     || !PingSpoofController.interceptOutbound(packet) && !RiptideBlinkManager.interceptOutbound(packet)) {
                     boolean payloadLoggedEarly = false;
                     if (module != null && hooks.packetLoggerCapturing()) {
                        payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "C2S", protocolHint);
                     }

                     if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
                        module.observePluginDiscoveryPacketSend(packet);
                     }

                     RiptideSharedState shared = RiptideSharedState.get();
                     if (packet instanceof ServerboundUseItemOnPacket pibp
                        && shared.consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
                        ScaffoldModule.onPacketAbandoned(packet);
                        ci.cancel();
                     } else if (packet instanceof ServerboundInteractPacket && shared.hasEntityCaptureCallback()) {
                        shared.consumeEntityCaptureCallback(Minecraft.getInstance().crosshairPickEntity);
                        ScaffoldModule.onPacketAbandoned(packet);
                        ci.cancel();
                     } else {
                        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
                           if (shared.consumeSuppressNextContainerClosePacket()) {
                              ci.cancel();
                              return;
                           }

                           if (RiptideContainerHold.isHeld(closeForHold.getContainerId())) {
                              RiptideContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                              ci.cancel();
                              return;
                           }

                           if (this.riptide$shouldKeepXCarryOpen(shared, closeForHold)) {
                              ci.cancel();
                              return;
                           }
                        }

                        if (normalLoggerPath) {
                           if (packet instanceof ServerboundUseItemOnPacket pibp) {
                              shared.setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
                              shared.setLastContainerTarget(RiptideContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
                           }

                           if (packet instanceof ServerboundInteractPacket entityPacket) {
                              Entity targeted = Minecraft.getInstance().crosshairPickEntity;
                              if (targeted != null && targeted != Minecraft.getInstance().player) {
                                 InteractionHand capturedHand = entityPacket.hand();
                                 Vec3 capturedHitPos = entityPacket.location();
                                 shared.setLastContainerTarget(
                                    capturedHitPos != null
                                       ? RiptideContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                                       : RiptideContainerTarget.forEntity(targeted, capturedHand)
                                 );
                              }
                           }

                           if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
                              ci.cancel();
                           } else if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
                              ci.cancel();
                           } else {
                              boolean forceBookOrSignPacket = packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                                 || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();
                              if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
                                 shared.setAllowSignEditing(true);
                                 if (!forceBookOrSignPacket) {
                                    ci.cancel();
                                    return;
                                 }
                              }

                              if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
                                 shared.setAllowBookUpdate(true);
                                 if (!forceBookOrSignPacket) {
                                    ci.cancel();
                                    return;
                                 }
                              }

                              if (shared.isGBreakCapturing()) {
                                 if (packet instanceof ServerboundPlayerActionPacket) {
                                    shared.onGBreakPacket(packet);
                                 }
                              } else if (packet instanceof ServerboundPlayerActionPacket actionPacket
                                 && actionPacket.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                                 && ModuleRegistry.dispatchStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                                 ci.cancel();
                              } else if (module.handlePacketSend(packet, payloadLoggedEarly)) {
                                 ScaffoldModule.onPacketAbandoned(packet);
                                 ci.cancel();
                              } else if (!forceBookOrSignPacket) {
                                 if (!shared.isFlushing()) {
                                    PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
                                    if (gateResult == PacketGateManager.Result.CANCEL) {
                                       ScaffoldModule.onPacketAbandoned(packet);
                                       ci.cancel();
                                       return;
                                    }

                                    if (gateResult == PacketGateManager.Result.DELAY) {
                                       shared.enqueuePacket(packet);
                                       ci.cancel();
                                       return;
                                    }
                                 }

                                 boolean anyFeatureActive = shared.shouldDelayGuiPackets() || !shared.shouldSendGuiPackets() || shared.shouldUseCustomPackets();
                                 if (anyFeatureActive) {
                                    if (!shared.isFlushing()) {
                                       if (!riptide$isTransactionSync(packet)) {
                                          boolean shouldHandle = false;
                                          if (shared.shouldUseCustomPackets()) {
                                             shouldHandle = shared.getC2SPackets().contains(packet.getClass());
                                          } else {
                                             shouldHandle = this.isGuiPacket(packet);
                                          }

                                          if (shouldHandle) {
                                             if (RIPTIDE_PACKET_TRACE) {
                                                riptide.RiptideClientAddon.LOG
                                                   .debug(
                                                      "[Riptide] Packet detected: {} | Send={} Delay={} | Custom={}",
                                                      new Object[]{
                                                         packet.getClass().getSimpleName(),
                                                         shared.shouldSendGuiPackets(),
                                                         shared.shouldDelayGuiPackets(),
                                                         shared.shouldUseCustomPackets()
                                                      }
                                                   );
                                             }

                                             if (!shared.shouldSendGuiPackets()) {
                                                if (RIPTIDE_PACKET_TRACE) {
                                                   riptide.RiptideClientAddon.LOG.debug("[Riptide] CANCELLED packet (send disabled)");
                                                }

                                                ScaffoldModule.onPacketAbandoned(packet);
                                                ci.cancel();
                                             } else {
                                                if (shared.shouldDelayGuiPackets()) {
                                                   if (RIPTIDE_PACKET_TRACE) {
                                                      riptide.RiptideClientAddon.LOG.debug("[Riptide] QUEUED packet (delay enabled)");
                                                   }

                                                   RiptideModule captureModule = RiptideModule.get();
                                                   RiptideSharedState.ReplayMode captureMode = captureModule != null && captureModule.isCaptureAsExact()
                                                      ? RiptideSharedState.ReplayMode.EXACT
                                                      : RiptideSharedState.ReplayMode.REGENERATE;
                                                   shared.enqueuePacket(packet, captureMode);
                                                   ci.cancel();
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
                  } else {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = {@At("TAIL")}
   )
   private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         if (!PackHideState.isHardLocked()) {
            if (this.isRiptideActive()) {
               if (this.isPlayConnectionActive()) {
                  MacroExecutor.onPacketSent(packet);
               }
            }
         }
      }
   }

   @Unique
   private static boolean riptide$isTransactionSync(Packet<?> packet) {
      return packet instanceof ServerboundPongPacket
         || packet instanceof ClientboundPingPacket
         || packet instanceof ServerboundKeepAlivePacket
         || packet instanceof ClientboundKeepAlivePacket;
   }

   @Unique
   private void riptide$trackSentMessage(Packet<?> packet) {
      if (packet instanceof ServerboundChatPacket chat) {
         RiptideSharedState.get().setLastSentMessage(chat.message());
      } else if (packet instanceof ServerboundChatCommandPacket command) {
         RiptideSharedState.get().setLastSentMessage("/" + command.command());
      } else if (packet instanceof ServerboundChatCommandSignedPacket signed) {
         RiptideSharedState.get().setLastSentMessage("/" + signed.command());
      }
   }

   @Unique
   private boolean isGuiPacket(Packet<?> packet) {
      return packet instanceof ServerboundContainerClickPacket
         || packet instanceof ServerboundContainerButtonClickPacket
         || packet instanceof ServerboundSetCreativeModeSlotPacket
         || packet instanceof ServerboundPlayerActionPacket
         || packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundSignUpdatePacket
         || packet instanceof ServerboundEditBookPacket
         || packet instanceof ServerboundChatPacket
         || packet instanceof ServerboundChatCommandPacket
         || packet instanceof ServerboundChatCommandSignedPacket
         || packet instanceof ServerboundCustomClickActionPacket;
   }

   @Inject(
      method = {"channelRead0"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         PacketListener listener = ((Connection)this).getPacketListener();
         RiptideModule module = RiptideModule.get();
         RiptideModule.PacketHookSnapshot hooks = module == null
            ? RiptideModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayReceiveListener(listener));
         boolean normalLoggerPath = hooks.normalPath();
         boolean vanillaDialogPacket = packet instanceof ClientboundShowDialogPacket || packet instanceof ClientboundClearDialogPacket;
         boolean customMenuPacket = !vanillaDialogPacket && CustomMenuAdapterRegistry.acceptsInbound(packet);
         String protocolHint = !hooks.packetLoggerCapturing() && !hooks.pluginDiscoveryObservation() && !customMenuPacket
            ? ""
            : this.riptide$protocolHint(listener);
         if (PackHideState.isHardLocked()) {
            this.riptide$clearRuntimeConnectionStateOnDisconnect(packet);
         } else {
            if (customMenuPacket) {
               CustomMenuTracker.acceptInterested(packet, protocolHint);
            }

            if (!isPlayReceiveListener(listener) || !PingSpoofController.interceptInbound(packet) && !RiptideBlinkManager.interceptInbound(packet)) {
               boolean payloadLoggedEarly = false;
               if (module != null && hooks.packetLoggerCapturing()) {
                  payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "S2C", protocolHint);
               }

               if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
                  module.observePluginDiscoveryPacketReceive(packet);
               }

               if (riptide$isIncomingChatPacket(packet)) {
                  MacroExecutor.observeIncomingChat(packet);
                  AutoLoginModule.observeIncomingChat(packet);
               }

               ModuleEspChunkCache.onPacketReceived(packet);
               if (normalLoggerPath) {
                  ServerTickTracker.onS2CPacket(packet);
                  MacroExecutor.onPacketReceived(packet);
                  if (packet instanceof ClientboundCommandSuggestionsPacket macroSuggestions && RiptideCommandSuggestionIds.isMacroId(macroSuggestions.id())) {
                     ci.cancel();
                  } else {
                     if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
                        RiptideContainerHold.onContainerOpened(openScreenPacket.getContainerId());
                     }

                     if (packet instanceof ClientboundDisconnectPacket) {
                        this.riptide$clearRuntimeConnectionStateOnDisconnect(packet);
                     }

                     if (module.handlePacketReceive(packet, payloadLoggedEarly)) {
                        ci.cancel();
                     } else {
                        RiptideSharedState shared = RiptideSharedState.get();
                        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
                        if (gateResult == PacketGateManager.Result.CANCEL) {
                           ci.cancel();
                        } else if (gateResult == PacketGateManager.Result.DELAY) {
                           shared.enqueuePacket(packet);
                           ci.cancel();
                        } else {
                           boolean anyFeatureActive = shared.shouldDelayGuiPackets() || !shared.shouldSendGuiPackets() || shared.shouldUseCustomPackets();
                           if (anyFeatureActive) {
                              if (!riptide$isTransactionSync(packet)) {
                                 boolean shouldHandle = false;
                                 if (shared.shouldUseCustomPackets()) {
                                    shouldHandle = shared.getS2CPackets().contains(packet.getClass());
                                 }

                                 if (shouldHandle) {
                                    if (RIPTIDE_PACKET_TRACE) {
                                       riptide.RiptideClientAddon.LOG
                                          .debug(
                                             "[Riptide] S2C Packet detected: {} | Send={} Delay={}",
                                             new Object[]{packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(), shared.shouldDelayGuiPackets()}
                                          );
                                    }

                                    if (!shared.shouldSendGuiPackets()) {
                                       ci.cancel();
                                    } else {
                                       if (shared.shouldDelayGuiPackets()) {
                                          shared.enqueuePacket(packet);
                                          ci.cancel();
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               ci.cancel();
            }
         }
      }
   }

   @Unique
   private boolean isRiptideActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.arePacketHooksActive();
   }

   @Unique
   private void riptide$ensureSpoofPipelineFilter() {
      if (!this.riptide$spoofPipelineInstalled) {
         Channel channel = null;

         try {
            channel = ((RiptideClientConnectionAccessor)this).getChannel();
         } catch (Throwable var3) {
         }

         if (channel != null) {
            try {
               ChannelPipeline pipeline = channel.pipeline();
               if (pipeline == null || pipeline.get("riptide_spoof_filter") != null) {
                  this.riptide$spoofPipelineInstalled = true;
                  return;
               }

               if (pipeline.get("encoder") != null) {
                  pipeline.addAfter("encoder", "riptide_spoof_filter", new RiptideSpoofPayloadFilter());
                  this.riptide$spoofPipelineInstalled = true;
               }
            } catch (Throwable var4) {
               riptide.RiptideClientAddon.LOG.debug("[Riptide] Failed to install client spoof payload filter", var4);
            }
         }
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      if (!this.riptide$isMultiConnectionOrChannel()) {
         ScaffoldModule.onPacketQueued(packet);
         this.riptide$trackSentMessage(packet);
         this.riptide$ensureSpoofPipelineFilter();
         if (packet instanceof ServerboundResourcePackPacket resourcePackPacket && RiptideResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)
            )
          {
            ci.cancel();
         } else {
            if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
               RiptideProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
            }

            if (packet instanceof ServerboundCustomPayloadPacket) {
               if (RiptideSpoofPayloadFilter.shouldBlockForVanillaSpoof(RiptideModule.get(), packet)) {
                  ci.cancel();
               } else {
                  if (RiptideSpoofPayloadFilter.shouldDropForProtector(packet)) {
                     ci.cancel();
                  }
               }
            } else if (!PackHideState.isHardLocked()) {
               RiptideSharedState shared = RiptideSharedState.get();
               if (packet instanceof ServerboundContainerClosePacket closeForHold) {
                  if (shared.consumeSuppressNextContainerClosePacket()) {
                     ci.cancel();
                     return;
                  }

                  if (RiptideContainerHold.isHeld(closeForHold.getContainerId())) {
                     RiptideContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                     ci.cancel();
                     return;
                  }

                  if (this.riptide$shouldKeepXCarryOpen(shared, closeForHold)) {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }

   @Unique
   private void riptide$clearRuntimeConnectionStateOnDisconnect(Packet<?> packet) {
      if (packet instanceof ClientboundDisconnectPacket) {
         RiptideContainerHold.clearAll();
         PacketGateManager.clearAll();
         PingSpoofController.clearQueue();
         RiptideBlinkManager.clear();
         RiptideSharedState s = RiptideSharedState.get();
         s.setXCarryForcedTargets(Collections.emptySet(), false);
         s.setXCarryForced(false);
         s.setXCarryActive(false);
      }
   }

   @Unique
   private boolean isPlayConnectionActive() {
      Minecraft client = Minecraft.getInstance();
      return client != null && client.getConnection() != null;
   }

   @Unique
   private static boolean isPlayReceiveListener(PacketListener listener) {
      return listener instanceof ClientGamePacketListener;
   }

   @Unique
   private boolean riptide$isLocalClientPlayConnection() {
      return ((Connection)this).getPacketListener() instanceof ClientGamePacketListener;
   }

   @Unique
   private static boolean riptide$isIncomingChatPacket(Packet<?> packet) {
      return packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDisguisedChatPacket || packet instanceof ClientboundPlayerChatPacket;
   }

   @Unique
   private String riptide$protocolHint(PacketListener listener) {
      if (listener == null) {
         return "";
      } else if (listener == this.riptide$protocolHintListener) {
         return this.riptide$protocolHintCache;
      } else {
         String hint;
         if (listener instanceof ClientGamePacketListener) {
            hint = "play";
         } else {
            String name = listener.getClass().getName();
            hint = name.toLowerCase(Locale.ROOT).contains("configuration") ? "configuration" : "";
         }

         this.riptide$protocolHintCache = hint;
         this.riptide$protocolHintListener = listener;
         return hint;
      }
   }

   @Unique
   private boolean riptide$shouldKeepXCarryOpen(RiptideSharedState shared, ServerboundContainerClosePacket packet) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         RiptideModule module = RiptideModule.get();
         boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();
         if (!allowPassiveXCarry && !shared.isXCarryForced()) {
            return false;
         } else if (packet.getContainerId() != client.player.inventoryMenu.containerId) {
            return false;
         } else {
            Set<Integer> mask;
            boolean carryCursor;
            if (shared.isXCarryForced()) {
               mask = shared.getXCarryForcedSlotMask();
               carryCursor = shared.isXCarryForcedCarryCursor();
            } else {
               mask = module == null ? null : module.getXCarryModuleSlotMask();
               carryCursor = module == null || module.isXCarryCarryCursor();
            }

            boolean hasItems = XCarryAction.hasStoredItems(client.player.inventoryMenu, carryCursor, mask);
            shared.setXCarryActive(hasItems);
            return true;
         }
      } else {
         return false;
      }
   }
}
