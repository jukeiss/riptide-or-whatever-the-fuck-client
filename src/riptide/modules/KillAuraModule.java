package riptide.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.mixin.accessor.RiptideLivingEntityAccessor;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.util.RiptideChamsHit;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideCpsTracker;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideKillAuraRenderer;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptideMaceAssist;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.PacketTeleportController;

public final class KillAuraModule extends Module implements RiptideSilentAim.Owner {
   static final int HURT_TIME = 10;
   static final double SCAN_ADDITION_MIN = 2.0;
   static final double SCAN_ADDITION_MAX = 3.0;
   static final double THROUGH_WALLS_RANGE = 0.0;
   static final int CPS_MIN = 5;
   static final int CPS_MAX = 8;
   static final int CLICK_CYCLE = 20;
   static final int CLICK_ITERATIONS = 2;
   static final long ENFORCED_CLICK_INTERVAL_MS = 1000L;
   static final int AUTO_SWORD_SWITCH_BACK_TICKS = 20;
   static final int SHIELD_BREAK_HOLD_TICKS = 10;
   static final int SHIELD_DISABLE_TICKS = 100;
   static final int POST_USE_SUPPRESS_TICKS = 3;
   static final int HIT_CONFIRM_TICKS = 8;
   static final double MISS_LATERAL_MIN = 0.1;
   static final double MISS_LATERAL_MAX = 0.2;
   static final double MISS_DEPTH_JITTER = 0.1;
   static final double MISS_VERTICAL_JITTER = 0.02;
   static final long ATTACK_ACCURACY_WINDOW_MS = 60L;
   static final float WINDOW_MAX_YAW_STEP = 12.0F;
   static final float WINDOW_MAX_PITCH_STEP = 6.0F;
   static final float THROTTLED_MAX_YAW_STEP = 5.5F;
   static final float THROTTLED_MAX_PITCH_STEP = 1.2F;
   static final float WHIFF_CONVERGENCE_DEG = 2.0F;
   static final double AIM_HORIZONTAL_INSET = 0.12;
   static final double AIM_VERTICAL_INSET = 0.2;
   private static final double[] AIM_SCAN_HORIZONTAL = new double[]{0.12, 0.31, 0.5, 0.69, 0.88};
   private static final double[] AIM_SCAN_VERTICAL = new double[]{0.2, 0.35, 0.5, 0.65, 0.8};
   private static long lastClickTime;
   private static final SoundEvent HITSOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("riptide", "hitsound"));
   private final Random random = new Random();
   private final KillAuraModule.AimPointTracker aimPointTracker = new KillAuraModule.AimPointTracker(new Random());
   private final KillAuraModule.Clicker clicker = new KillAuraModule.Clicker();
   private final Random missRandom = new Random();
   private final KillAuraModule.MissState missState = new KillAuraModule.MissState();
   private final KillAuraModule.AccuracyGovernor accuracyGovernor = new KillAuraModule.AccuracyGovernor();
   private LivingEntity currentTarget;
   private double closestSquaredEnemyDistance;
   private double scanAddition = this.nextScanAddition();
   private int previousSlot = -1;
   private int switchedToSlot = -1;
   private int switchBackTicks;
   private int hotbarChangeTick = Integer.MIN_VALUE;
   private int lastShieldSeenTick = Integer.MIN_VALUE;
   private int shieldHoldEntityId = -1;
   private int shieldBreakLandedTick = Integer.MIN_VALUE;
   private int shieldBreakLandedEntityId = -1;
   private int postUseSuppressTicks;
   private int pendingHitEntityId = -1;
   private int pendingHitPrevHurtTime;
   private int pendingHitTicks;
   private String cachedEntityListSource = "";
   private Set<String> cachedEntityIds = Set.of();
   private final KillAuraModule.TickVerdict throwableVerdict = new KillAuraModule.TickVerdict();
   private float nextCooldown = 1.0F;
   private float clickOffsetTicks = this.rollClickOffsetTicks();

   public KillAuraModule() {
      super("kill-aura", "KillAura", ModuleCategory.COMBAT, "Automatically attacks configured entities.");
      this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").build());
      this.add(new ChoiceSetting("attack-mode", "Attack Mode", "Real Input", "Real Input", "Packet").description("How attacks are sent").build());
      this.add(new ChoiceSetting("targeting", "Targeting", "FOV", "Type", "HP", "Distance", "FOV", "Hurt Time", "Age").build());
      this.add(new IntSetting("fov", "FOV", 180, 10, 360, 10).description("Attack cone in degrees").build());
      this.add(new BoolSetting("miss-injection", "Miss Injection", false).description("Adds human-like misses").build());
      this.add(
         new IntSetting("miss-chance", "Miss Chance", 6, 1, 20, 1).description("Miss rate percent").visibleWhen(() -> this.bool("miss-injection")).build()
      );
      this.add(new BoolSetting("criticals", "Criticals", false).description("Smart critical hits").build());
      this.add(new BoolSetting("auto-sword", "Auto Sword", true).build());
      this.add(new BoolSetting("shield-break", "Shield Break", true).build());
      this.add(new BoolSetting("switch-back", "Switch Back", true).visibleWhen(() -> this.bool("auto-sword")).build());
      this.add(new ChoiceSetting("throwables", "Throwables", "MainHand", "MainHand", "BothHands").description("Hands checked for throwables").build());
      this.add(new BoolSetting("hit-marker", "Render", true).build());
      this.add(new BoolSetting("hitsound", "Hitsound", true).build());
   }

   @Override
   public void onEnable() {
      this.resetRuntime();
   }

   @Override
   public void onDisable() {
      this.resetRuntime();
      RiptideKillAuraRotation.beginWindDown("kill-aura");
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
      if ("kill-aura".equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.reset();
      }

      this.accuracyGovernor.reset();
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if ("entities".equals(settingId)) {
         this.cachedEntityListSource = null;
      }

      if ("auto-sword".equals(settingId) && !this.bool("auto-sword")) {
         if (this.previousSlot >= 0
            && MC != null
            && MC.player != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !RiptideBlinkManager.holdsActionsWithoutMovement()
            && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            try {
               RiptideInventoryHelper.selectHotbarSlot(MC, this.previousSlot);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }
         }

         this.previousSlot = -1;
         this.switchBackTicks = 0;
      }

      if ("switch-back".equals(settingId) && !this.bool("switch-back")) {
         this.previousSlot = -1;
         this.switchBackTicks = 0;
      }

      if ("shield-break".equals(settingId) && !this.bool("shield-break")) {
         if (this.previousSlot >= 0
            && MC != null
            && MC.player != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !RiptideBlinkManager.holdsActionsWithoutMovement()
            && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            try {
               RiptideInventoryHelper.selectHotbarSlot(MC, this.previousSlot);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }
         }

         this.previousSlot = -1;
         this.switchedToSlot = -1;
         this.switchBackTicks = 0;
         this.shieldHoldEntityId = -1;
         this.lastShieldSeenTick = Integer.MIN_VALUE;
         this.shieldBreakLandedTick = Integer.MIN_VALUE;
         this.shieldBreakLandedEntityId = -1;
      }
   }

   @Override
   public void tick() {
      if (!this.isEnabled() && MC != null && MC.player != null) {
         RiptideKillAuraRotation.update("kill-aura", MC.player);
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return RiptideKillAuraRotation.hasCurrentRotation();
   }

   @Override
   public void preMovementTick() {
      if (MC != null && MC.player != null && MC.level != null) {
         if (!RiptideSilentAim.scaffoldOwnsRotation() && !ScaffoldModule.reservesRageInput()) {
            boolean usingItem = this.isUsingHeldItem();
            if (usingItem) {
               this.postUseSuppressTicks = 3;
            } else if (this.postUseSuppressTicks > 0) {
               this.postUseSuppressTicks--;
            }

            boolean throwable = this.throwableHeldThisTick();
            if (!this.isBreakingBlock() && !usingItem && !throwable) {
               this.clicker.tick();
               this.confirmHitFeedback();
               boolean postAttackWindow = this.accuracyGovernor.attackedRecently();
               if (this.canRun()) {
                  this.updateTargetRotation(postAttackWindow);
               } else {
                  this.currentTarget = null;
                  this.missState.clear();
               }

               this.tickAutoSwordReset();
               boolean inAttackWindow = postAttackWindow || this.attackImminentThisTick();
               boolean throttle = inAttackWindow && this.accuracyGovernor.speedAtRisk();
               RiptideKillAuraRotation.update(
                  "kill-aura", MC.player, !inAttackWindow ? 72.0F : (throttle ? 5.5F : 12.0F), !inAttackWindow ? 72.0F : (throttle ? 1.2F : 6.0F)
               );
               if (this.canRun()) {
                  this.attackPhase();
               }

               this.recordOutgoingRotation();
            } else {
               this.currentTarget = null;
               this.missState.clear();
               RiptideKillAuraRotation.beginWindDown("kill-aura");
               this.clicker.tick();
               this.confirmHitFeedback();
               RiptideKillAuraRotation.update("kill-aura", MC.player);
               this.recordOutgoingRotation();
            }
         } else {
            this.currentTarget = null;
            this.missState.clear();
            if ("kill-aura".equals(RiptideKillAuraRotation.currentOwner())) {
               RiptideKillAuraRotation.reset();
            }

            this.accuracyGovernor.forgetPacketHistory();
         }
      }
   }

   private void recordOutgoingRotation() {
      RiptideRotationUtil.Rotation sent = RiptideKillAuraRotation.getCurrentRotation();
      if (sent == null) {
         sent = RiptideRotationUtil.playerRotation(MC.player);
      }

      this.accuracyGovernor.onOutgoingRotation(sent.yaw(), sent.pitch(), this.perfectYaw(this.currentTarget));
   }

   private float perfectYaw(LivingEntity target) {
      if (target != null && MC.player != null) {
         double diffX = target.getX() - MC.player.getX();
         double diffZ = target.getZ() - MC.player.getZ();
         return (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
      } else {
         return Float.NaN;
      }
   }

   private boolean isBreakingBlock() {
      return MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor accessor && accessor.riptide$isDestroying();
   }

   private boolean isUsingHeldItem() {
      return MC.player != null && MC.player.isUsingItem();
   }

   static boolean isInstantThrowable(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         Item item = stack.getItem();
         return item instanceof EnderpearlItem
            || item instanceof SnowballItem
            || item instanceof EggItem
            || item instanceof ExperienceBottleItem
            || item instanceof ThrowablePotionItem
            || item instanceof WindChargeItem;
      }
   }

   private boolean holdsInstantThrowable() {
      if (MC.player == null) {
         return false;
      } else {
         ItemStack mainHand = MC.player.getMainHandItem();
         if (isInstantThrowable(mainHand)) {
            return true;
         } else {
            return !"BothHands".equals(this.choice("throwables")) && !mainHand.isEmpty() ? false : isInstantThrowable(MC.player.getOffhandItem());
         }
      }
   }

   private boolean throwableHeldThisTick() {
      return this.throwableVerdict.resolve(RiptideSharedState.get().getClientTickCounter(), this::holdsInstantThrowable);
   }

   private boolean canRun() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.gameMode != null
         && MC.getConnection() != null
         && !MC.player.isDeadOrDying()
         && !MC.player.isSpectator()
         && !MC.player.isUsingItem()
         && !this.throwableHeldThisTick()
         && !PackHideState.isActive()
         && !PackFreecamState.isActive()
         && !RiptideRemoteView.isActive()
         && !MultiPilot.isActive()
         && !MacroExecutor.isRunning()
         && !PacketTeleportController.ownsMainMovement()
         && !BuiltinModules.ownsManualFastExp()
         && !ScaffoldModule.reservesTellyInput()
         && !ScaffoldModule.hasActiveSilentMovementRotation()
         && !ScaffoldModule.reservesRageInput()
         && !BedDefenderModule.ownsSilentRotation()
         && !SurroundModule.ownsSilentRotation()
         && !CrystalAuraModule.reservesCombatTick()
         && !AnchorAuraModule.reservesCombatTick()
         && !autoTrapOwnsSilentRotation()
         && !RiptideBlinkManager.holdsActionsWithoutMovement()
         && !AutoTotemModule.operationActive()
         && !AutoArmorModule.operationActive();
   }

   private static boolean missTimeActive() {
      return ((RiptideMinecraftAccessor)MC).riptide$getMissTime() > 0;
   }

   private static boolean autoTrapOwnsSilentRotation() {
      return "auto-trap".equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   static boolean silentCorrectionApplies(boolean windingDown, boolean enabled, boolean canRun, boolean scaffoldOwnsRotation) {
      return !scaffoldOwnsRotation && (windingDown || enabled && canRun);
   }

   @Override
   public boolean silentCorrectionApplies() {
      boolean enabled = this.isEnabled();
      return silentCorrectionApplies(RiptideKillAuraRotation.isWindingDown(), enabled, enabled && this.canRun(), RiptideSilentAim.scaffoldOwnsRotation());
   }

   private static KillAuraModule activeInstance() {
      return ModuleRegistry.get("kill-aura") instanceof KillAuraModule aura && aura.isEnabled() ? aura : null;
   }

   public LivingEntity currentTarget() {
      return this.currentTarget;
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return this.isEnabled() && this.currentTarget != null && this.canRun() ? hitResult == null || hitResult.getType() != Type.BLOCK : false;
   }

   private void updateTargetRotation(boolean inAttackWindow) {
      boolean allowExcursion = !this.accuracyGovernor.errorAtRisk();
      double interactionRange = this.interactionRange();
      double normalRangeSq = interactionRange * interactionRange;
      double maximumRange = this.closestSquaredEnemyDistance > normalRangeSq ? this.scanRange() : interactionRange;
      double maximumRangeSq = maximumRange * maximumRange;
      List<LivingEntity> targets = this.collectTargets();
      List<LivingEntity> filtered = new ArrayList<>();

      for (LivingEntity entity : targets) {
         if (this.boxedDistanceToPlayerSqr(entity) <= maximumRangeSq) {
            filtered.add(entity);
         }
      }

      filtered.sort(Comparator.comparingInt(entityx -> this.boxedDistanceToPlayerSqr(entityx) <= normalRangeSq ? 0 : 1));
      LivingEntity chosen = null;
      RiptideRotationUtil.Rotation chosenRotation = null;
      int bestRangeBucket = filtered.isEmpty() ? Integer.MAX_VALUE : (this.boxedDistanceToPlayerSqr((Entity)filtered.get(0)) <= normalRangeSq ? 0 : 1);
      if (this.currentTarget != null) {
         for (LivingEntity entityx : filtered) {
            if (entityx == this.currentTarget) {
               int currentRangeBucket = this.boxedDistanceToPlayerSqr(entityx) <= normalRangeSq ? 0 : 1;
               if (currentRangeBucket == bestRangeBucket) {
                  Vec3 preferred = this.aimPointTracker
                     .advance(entityx.getId(), entityx.getBoundingBox(), MC.player.getEyePosition(), inAttackWindow, allowExcursion);
                  chosenRotation = this.rotationForAimPoint(entityx, maximumRange, preferred);
                  if (chosenRotation != null) {
                     chosen = entityx;
                  }
               }
               break;
            }
         }
      }

      if (chosen == null) {
         for (LivingEntity entityxx : filtered) {
            if (entityxx != this.currentTarget) {
               AABB box = entityxx.getBoundingBox();
               Vec3 acquisition = safeAimPoint(box, 0.5, 0.56, 0.5);
               RiptideRotationUtil.Rotation rotation = this.findRotation(entityxx, maximumRange, acquisition);
               if (rotation != null) {
                  Vec3 preferred = this.aimPointTracker.begin(entityxx.getId(), box, MC.player.getEyePosition(), inAttackWindow, allowExcursion);
                  RiptideRotationUtil.Rotation tracked = this.rotationForAimPoint(entityxx, maximumRange, preferred);
                  chosen = entityxx;
                  chosenRotation = tracked != null ? tracked : rotation;
                  break;
               }
            }
         }
      }

      if (chosenRotation != null) {
         chosenRotation = this.applyMissOverride(chosen, chosenRotation);
         RiptideKillAuraRotation.setTarget(chosenRotation);
      } else {
         this.aimPointTracker.clear();
         this.missState.clear();
      }

      this.currentTarget = chosen;
   }

   private List<LivingEntity> collectTargets() {
      List<LivingEntity> entities = new ArrayList<>();
      Vec3 eyes = MC.player.getEyePosition();

      for (Entity entity : MC.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living && this.validate(living, eyes)) {
            entities.add(living);
         }
      }

      if (entities.isEmpty()) {
         return entities;
      } else {
         entities.sort(this.targetComparator());
         double closest = Double.MAX_VALUE;

         for (LivingEntity entityx : entities) {
            closest = Math.min(closest, this.boxedDistanceToPlayerSqr(entityx));
         }

         this.closestSquaredEnemyDistance = closest;
         return entities;
      }
   }

   private boolean validate(LivingEntity entity, Vec3 eyes) {
      if (entity == MC.player) {
         return false;
      } else if (entity.isRemoved()) {
         return false;
      } else if (entity.hurtTime > 10) {
         return false;
      } else {
         return !this.shouldBeAttacked(entity) ? false : this.crosshairAngleToEntity(entity, eyes) <= this.integer("fov") * 0.5F;
      }
   }

   private boolean shouldBeAttacked(LivingEntity entity) {
      if (!(entity instanceof Attackable)) {
         return false;
      } else if (!EntitySelector.CAN_BE_PICKED.test(entity)) {
         return false;
      } else if (entity == MC.player || entity.hasPassenger(MC.player)) {
         return false;
      } else if (RiptideAntiBot.suppress(entity)) {
         return false;
      } else if (TeamsModule.combatExcluded(entity, "killaura")) {
         return false;
      } else if (!entity.isAlive()) {
         return false;
      } else {
         return entity instanceof Player player && player.isSleeping() ? false : this.matchesEntity(entity);
      }
   }

   private float crosshairAngleToEntity(Entity entity, Vec3 eyes) {
      RiptideRotationUtil.Rotation toCenter = RiptideRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
      return RiptideRotationUtil.rotationAngleTo(RiptideRotationUtil.playerRotation(MC.player), toCenter);
   }

   private boolean matchesEntity(Entity entity) {
      String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
      Set<String> ids = this.cachedEntityIds();
      int separator = id.indexOf(58);
      return ids.contains(id) || separator >= 0 && ids.contains(id.substring(separator + 1));
   }

   private Set<String> cachedEntityIds() {
      List<String> entries = this.list("entities");
      String source = String.join("|", entries);
      if (source.equals(this.cachedEntityListSource)) {
         return this.cachedEntityIds;
      } else {
         Set<String> normalized = new LinkedHashSet<>();

         for (String entry : entries) {
            if (entry != null) {
               String value = entry.trim().toLowerCase(Locale.ROOT);
               if (!value.isEmpty()) {
                  normalized.add(value);
                  int separator = value.indexOf(58);
                  if (separator >= 0 && separator + 1 < value.length()) {
                     normalized.add(value.substring(separator + 1));
                  }
               }
            }
         }

         this.cachedEntityListSource = source;
         this.cachedEntityIds = Set.copyOf(normalized);
         return this.cachedEntityIds;
      }
   }

   private int typeWeight(LivingEntity entity) {
      if (entity instanceof Player) {
         return 0;
      } else if (entity instanceof Enemy) {
         return 1;
      } else {
         if (entity instanceof NeutralMob neutral) {
            EntityReference<LivingEntity> angerTarget = neutral.getPersistentAngerTarget();
            if (angerTarget != null && angerTarget.matches(MC.player)) {
               return 2;
            }
         }

         return Integer.MAX_VALUE;
      }
   }

   private Comparator<LivingEntity> targetComparator() {
      String var1 = this.choice("targeting");

      return switch (var1) {
         case "HP" -> Comparator.comparingDouble(this::actualHealth);
         case "Distance" -> Comparator.comparingDouble(this::boxedDistanceToPlayerSqr);
         case "FOV" -> Comparator.comparingDouble(entity -> this.crosshairAngleToEntity(entity, MC.player.getEyePosition()));
         case "Hurt Time" -> Comparator.comparingInt(entity -> entity.hurtTime);
         case "Age" -> Comparator.comparingInt(entity -> -entity.tickCount);
         default -> Comparator.comparingInt(this::typeWeight);
      };
   }

   private float actualHealth(LivingEntity entity) {
      try {
         Scoreboard scoreboard = entity.level().getScoreboard();
         Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
         if (objective != null) {
            String displayName = objective.getDisplayName().getString();
            if (displayName.contains("❤")
               || displayName.contains("HP")
               || displayName.contains("Health")
               || displayName.contains("Здоровья")
               || displayName.contains("Здоровье")) {
               ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(entity, objective);
               if (score != null) {
                  return (double)score.value();
               }
            }
         }
      } catch (Throwable var6) {
      }

      return (double)entity.getHealth();
   }

   private double boxedDistanceToPlayerSqr(Entity entity) {
      return entity.getBoundingBox().inflate(entity.getPickRadius()).distanceToSqr(MC.player.getEyePosition());
   }

   private RiptideRotationUtil.Rotation findRotation(Entity entity, double range, Vec3 preferredPoint) {
      Vec3 eyes = MC.player.getEyePosition();
      AABB box = entity.getBoundingBox();
      double rangeSq = range * range;
      Vec3 preferred = clampToSafeAimPoint(box, preferredPoint);
      RiptideRotationUtil.Rotation direct = this.visibleInteriorRotation(eyes, box, preferred, rangeSq);
      if (direct != null) {
         return direct;
      } else {
         RiptideRotationUtil.Rotation reference = RiptideKillAuraRotation.getCurrentRotation();
         if (reference == null) {
            reference = RiptideRotationUtil.playerRotation(MC.player);
         }

         RiptideRotationUtil.Rotation best = null;
         double bestScore = Double.POSITIVE_INFINITY;
         double diagonal = Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize());
         diagonal = Math.max(diagonal, 1.0E-6);

         for (double x : AIM_SCAN_HORIZONTAL) {
            for (double y : AIM_SCAN_VERTICAL) {
               for (double z : AIM_SCAN_HORIZONTAL) {
                  Vec3 point = safeAimPoint(box, x, y, z);
                  RiptideRotationUtil.Rotation candidate = this.visibleInteriorRotation(eyes, box, point, rangeSq);
                  if (candidate != null) {
                     double pointDistance = Math.sqrt(point.distanceToSqr(preferred)) / diagonal;
                     double score = RiptideRotationUtil.rotationAngleTo(reference, candidate) + pointDistance * 8.0;
                     if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private RiptideRotationUtil.Rotation rotationForAimPoint(Entity entity, double range, Vec3 point) {
      return !entity.getBoundingBox().contains(point)
         ? RiptideRotationUtil.lookingAt(point, MC.player.getEyePosition())
         : this.findRotation(entity, range, point);
   }

   private RiptideRotationUtil.Rotation visibleInteriorRotation(Vec3 eyes, AABB box, Vec3 point, double rangeSq) {
      if (eyes.distanceToSqr(point) <= 1.0E-12) {
         return null;
      } else {
         Vec3 hit = box.contains(eyes) ? eyes : firstHit(box, eyes, fma(eyes, 1.25, point.subtract(eyes)));
         if (hit == null || !(eyes.distanceToSqr(hit) < rangeSq)) {
            return null;
         } else {
            return !this.aimVisibility(eyes, hit) ? null : RiptideRotationUtil.lookingAt(point, eyes);
         }
      }
   }

   static Vec3 safeAimPoint(AABB box, double x, double y, double z) {
      double safeX = Mth.clamp(x, 0.12, 0.88);
      double safeY = Mth.clamp(y, 0.2, 0.8);
      double safeZ = Mth.clamp(z, 0.12, 0.88);
      return new Vec3(Math.fma(box.getXsize(), safeX, box.minX), Math.fma(box.getYsize(), safeY, box.minY), Math.fma(box.getZsize(), safeZ, box.minZ));
   }

   private static Vec3 clampToSafeAimPoint(AABB box, Vec3 point) {
      return point == null
         ? safeAimPoint(box, 0.5, 0.56, 0.5)
         : safeAimPoint(box, normalized(point.x, box.minX, box.maxX), normalized(point.y, box.minY, box.maxY), normalized(point.z, box.minZ, box.maxZ));
   }

   private static double normalized(double value, double min, double max) {
      double size = max - min;
      return size > 1.0E-9 ? (value - min) / size : 0.5;
   }

   private static Vec3 firstHit(AABB box, Vec3 from, Vec3 to) {
      return (Vec3)(box.contains(from) ? box.clip(to, from) : box.clip(from, to)).orElse(null);
   }

   private static Vec3 fma(Vec3 base, double scale, Vec3 other) {
      return new Vec3(Math.fma(scale, other.x, base.x), Math.fma(scale, other.y, base.y), Math.fma(scale, other.z, base.z));
   }

   private boolean aimVisibility(Vec3 eyes, Vec3 point) {
      return this.hasLineOfSight(eyes, point, MC.player);
   }

   private boolean hasLineOfSight(Vec3 eyes, Vec3 point, Entity entity) {
      return MC.level.clip(new ClipContext(eyes, point, Block.COLLIDER, Fluid.NONE, entity)).getType() == Type.MISS;
   }

   private EntityHitResult findEntityInCrosshair(double range, RiptideRotationUtil.Rotation rotation, Predicate<Entity> predicate) {
      Entity camera = MC.getCameraEntity();
      if (camera == null) {
         return null;
      } else {
         Vec3 eyes = camera.getEyePosition();
         Vec3 direction = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
         Vec3 end = eyes.add(direction.x * range, direction.y * range, direction.z * range);
         AABB search = camera.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0, 1.0, 1.0);
         return ProjectileUtil.getEntityHitResult(camera, eyes, end, search, EntitySelector.CAN_BE_PICKED.and(predicate), range * range);
      }
   }

   private EntityHitResult isLookingAtEntity(Entity target, RiptideRotationUtil.Rotation rotation, double range, double wallsRange) {
      Entity camera = MC.getCameraEntity();
      if (camera == null) {
         return null;
      } else {
         EntityHitResult hit = this.findEntityInCrosshair(range, rotation, entity -> entity == target);
         if (hit != null && hit.getEntity() == target) {
            Vec3 eyes = camera.getEyePosition();
            double distanceSq = eyes.distanceToSqr(hit.getLocation());
            return !(distanceSq <= wallsRange * wallsRange) && (!(distanceSq <= range * range) || !this.hasLineOfSight(eyes, hit.getLocation(), camera))
               ? null
               : hit;
         } else {
            return null;
         }
      }
   }

   private void attackPhase() {
      LivingEntity target = this.currentTarget;
      if (target != null) {
         if (ScaffoldModule.hasActiveSilentMovementRotation()) {
            this.missState.clear();
         } else if (!this.missState.isPending()) {
            RiptideRotationUtil.Rotation rotation = RiptideKillAuraRotation.getCurrentRotation();
            if (rotation == null) {
               rotation = RiptideRotationUtil.playerRotation(MC.player);
            }

            this.attackTarget(target, rotation);
         } else {
            if (this.missState.isFireTick()) {
               RiptideRotationUtil.Rotation current = RiptideKillAuraRotation.getCurrentRotation();
               RiptideRotationUtil.Rotation toMiss = RiptideRotationUtil.lookingAt(this.missState.point(), MC.player.getEyePosition());
               if (current == null || RiptideRotationUtil.rotationAngleTo(current, toMiss) > 2.0F) {
                  return;
               }

               if (RiptideCombatClicker.queueAttackMiss(BlockHitResult.miss(MC.player.getEyePosition(), Direction.DOWN, MC.player.blockPosition()))) {
                  this.missState.advance();
               }
            } else {
               this.missState.advance();
            }
         }
      }
   }

   private boolean attackImminentThisTick() {
      LivingEntity target = this.currentTarget;
      if (target != null && !this.missState.isPending() && this.clicker.isClickTick()) {
         ItemStack stack = MC.player.getMainHandItem();
         if (!this.canAttackNow(target, stack)) {
            return false;
         } else {
            RiptideRotationUtil.Rotation rotation = RiptideKillAuraRotation.getCurrentRotation();
            if (rotation == null) {
               rotation = RiptideRotationUtil.playerRotation(MC.player);
            }

            EntityHitResult hit = this.isLookingAtEntity(target, rotation, this.interactionRange(), 0.0);
            return hit != null && this.attackRangeIsInRange(stack, hit.getLocation());
         }
      } else {
         return false;
      }
   }

   private void attackTarget(Entity target, RiptideRotationUtil.Rotation rotation) {
      EntityHitResult attackHit = this.isLookingAtEntity(target, rotation, this.interactionRange(), 0.0);
      boolean isInRange = attackHit != null && this.attackRangeIsInRange(MC.player.getMainHandItem(), attackHit.getLocation());
      if (isInRange) {
         if (!this.prepareWeaponSwitchTick(target)) {
            if (!this.performDueSwitchBack()) {
               ItemStack mainHandStack = MC.player.getMainHandItem();
               boolean shieldBreakHit = target instanceof LivingEntity living && this.shieldBreakHitPending(living);
               if ((this.clicker.isClickTick() || shieldBreakHit) && this.canAttackNow(target, mainHandStack)) {
                  this.clicker.shieldBreakBypass = shieldBreakHit;
                  this.clickerPrepareForAttack(() -> {
                     if (!this.canAttackNow(target, mainHandStack)) {
                        return false;
                     } else if (!this.attackEntity(target, attackHit)) {
                        return false;
                     } else {
                        boolean brokeShield = shieldBreakHit && mainHandStack.is(ItemTags.AXES);
                        if (brokeShield) {
                           this.shieldBreakLandedTick = RiptideSharedState.get().getClientTickCounter();
                           this.shieldBreakLandedEntityId = target.getId();
                        }

                        this.accuracyGovernor.onAttackSent();
                        this.missState.onAttackFired();
                        if (this.bool("switch-back") && this.previousSlot >= 0) {
                           this.switchBackTicks = brokeShield ? 0 : 20;
                        }

                        this.scanAddition = this.nextScanAddition();
                        return true;
                     }
                  });
               }
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation applyMissOverride(LivingEntity target, RiptideRotationUtil.Rotation rotation) {
      if (this.missState.isPending()) {
         if (this.missState.matches(target.getId())) {
            return RiptideRotationUtil.lookingAt(this.missState.point(), MC.player.getEyePosition());
         } else {
            this.missState.clear();
            return rotation;
         }
      } else if (!this.missState.mayRoll() || !this.bool("miss-injection") || !this.clicker.isClickTick()) {
         return rotation;
      } else if (!this.canAttackNow(target, MC.player.getMainHandItem())) {
         return rotation;
      } else if (this.wouldBeLethal(target)) {
         return rotation;
      } else if (this.shieldBreakHitPending(target)) {
         return rotation;
      } else if (this.missRandom.nextDouble() >= this.integer("miss-chance") * 0.01) {
         return rotation;
      } else {
         this.missState.begin(target.getId(), missAimPoint(target.getBoundingBox(), MC.player.getEyePosition(), this.missRandom));
         return RiptideRotationUtil.lookingAt(this.missState.point(), MC.player.getEyePosition());
      }
   }

   private boolean wouldBeLethal(LivingEntity target) {
      return target.getHealth() <= MC.player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5;
   }

   static Vec3 missAimPoint(AABB box, Vec3 eyes, Random random) {
      Vec3 center = box.getCenter();
      double halfWidth = Math.min(box.getXsize(), box.getZsize()) * 0.5;
      double lateral = halfWidth + 0.1 + random.nextDouble() * 0.1;
      if (random.nextBoolean()) {
         lateral = -lateral;
      }

      double depth = (random.nextDouble() * 2.0 - 1.0) * 0.1;
      double dy = (random.nextDouble() * 2.0 - 1.0) * 0.02;
      if (dy == 0.0) {
         dy = 0.01;
      }

      double dx = center.x - eyes.x;
      double dz = center.z - eyes.z;
      double horizontal = Math.max(Math.sqrt(dx * dx + dz * dz), 0.3);
      double dirX = dx / horizontal;
      double dirZ = dz / horizontal;
      return new Vec3(center.x + dirX * depth - dirZ * lateral, center.y + dy, center.z + dirZ * depth + dirX * lateral);
   }

   private boolean prepareWeaponSwitchTick(Entity target) {
      if (target instanceof LivingEntity living) {
         boolean shieldBreak = this.shieldBreakEngaged(living);
         if (!this.bool("auto-sword") && !shieldBreak) {
            return false;
         } else {
            Integer slot = this.determineWeaponSlot(living, shieldBreak);
            if (slot != null && !this.isAutoWeaponBusy()) {
               int selected = MC.player.getInventory().getSelectedSlot();
               if (selected == slot) {
                  return false;
               } else {
                  if (this.bool("switch-back")) {
                     if (this.previousSlot < 0) {
                        this.previousSlot = selected;
                     }

                     this.switchBackTicks = 20;
                  }

                  int tick = RiptideSharedState.get().getClientTickCounter();
                  if (tick == this.hotbarChangeTick) {
                     return true;
                  } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     return true;
                  } else {
                     try {
                        RiptideInventoryHelper.selectHotbarSlot(MC, slot);
                        this.switchedToSlot = slot;
                        this.hotbarChangeTick = tick;
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }

                     return true;
                  }
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private boolean shieldBreakEngaged(LivingEntity target) {
      if (!this.bool("shield-break")) {
         return false;
      } else {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (this.wouldBlockHit(target)) {
            this.lastShieldSeenTick = tick;
            this.shieldHoldEntityId = target.getId();
            return true;
         } else {
            return target instanceof Player && target.getId() == this.shieldBreakLandedEntityId && tick - this.shieldBreakLandedTick <= 100
               ? false
               : target.getId() == this.shieldHoldEntityId && tick - this.lastShieldSeenTick <= 10;
         }
      }
   }

   private boolean shieldBreakHitPending(LivingEntity target) {
      return this.bool("shield-break") && this.wouldBlockHit(target);
   }

   private boolean performDueSwitchBack() {
      if (this.bool("switch-back") && this.previousSlot >= 0 && this.switchBackTicks <= 0) {
         if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (!CrystalAuraModule.holdsBorrowedSlot(selected) && !AnchorAuraModule.holdsBorrowedSlot(selected)) {
               this.previousSlot = -1;
               this.switchedToSlot = -1;
               return false;
            } else {
               return false;
            }
         } else {
            int back = this.previousSlot;
            if (MC.player.getInventory().getSelectedSlot() == back) {
               this.previousSlot = -1;
               this.switchedToSlot = -1;
               return false;
            } else {
               int tick = RiptideSharedState.get().getClientTickCounter();
               if (tick == this.hotbarChangeTick) {
                  return false;
               } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                  return false;
               } else {
                  try {
                     RiptideInventoryHelper.selectHotbarSlot(MC, back);
                     this.hotbarChangeTick = tick;
                  } finally {
                     RiptideHandArbiter.endHandPacketGroup(this.id());
                  }

                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean canAttackNow(Entity target, ItemStack stack) {
      if (!stack.isItemEnabled(MC.level.enabledFeatures())) {
         return false;
      } else if (MC.player.cannotAttackWithItem(stack, 0)) {
         return false;
      } else if (ScaffoldModule.hasActiveSilentMovementRotation()) {
         return false;
      } else {
         return this.postUseSuppressTicks > 0
            ? false
            : !(this.bool("criticals") && target instanceof LivingEntity living)
               || MC.player.isFallFlying()
               || this.shieldBreakHitPending(living)
               || !this.shouldWaitForCrit();
      }
   }

   private boolean shouldWaitForCrit() {
      double motionY = MC.player.getDeltaMovement().y;
      if (this.allowsCriticalHit() && !(motionY < -0.08)) {
         float ticksTillCrit = Math.max(this.ticksUntilNextCrit(), (float)(motionY / 0.08));
         float damageOnCrit = 0.375F;
         return damageOnCrit <= this.cooldownDamageFactor(ticksTillCrit) ? false : this.willStayAirborne((int)(ticksTillCrit * 1.3F));
      } else {
         return false;
      }
   }

   private float ticksUntilNextCrit() {
      return Math.max(this.currentItemAttackStrengthDelay() * 0.9F - 0.5F - this.attackStrengthTicker(), 0.0F);
   }

   private float cooldownDamageFactor(float ticks) {
      float base = (ticks + 0.5F) / this.currentItemAttackStrengthDelay();
      return Math.min(0.2F + base * base * 0.8F, 1.0F);
   }

   private boolean willStayAirborne(int ticks) {
      double motionY = MC.player.getDeltaMovement().y;
      AABB box = MC.player.getBoundingBox();

      for (int i = 0; i < ticks; i++) {
         motionY = (motionY - 0.08) * 0.98;
         box = box.move(0.0, motionY, 0.0);
         if (!MC.level.noCollision(MC.player, box)) {
            return false;
         }
      }

      return true;
   }

   private boolean shouldStopSprintingForCrit() {
      if (this.bool("criticals") && MC.player != null && !MC.player.onGround() && this.currentTarget != null && this.clicker.willClickAt(1)) {
         LivingEntity var2 = this.currentTarget;
         if (!(var2 instanceof LivingEntity) || !this.shieldBreakHitPending(var2)) {
            return true;
         }
      }

      return false;
   }

   public static boolean blocksSprintForCrit() {
      KillAuraModule aura = activeInstance();
      return aura != null && aura.shouldStopSprintingForCrit();
   }

   public static boolean holdsBorrowedSlot(int slot) {
      return ModuleRegistry.get("kill-aura") instanceof KillAuraModule aura && aura.isEnabled() ? aura.previousSlot >= 0 && aura.switchedToSlot == slot : false;
   }

   private void clickerPrepareForAttack(BooleanSupplier attack) {
      if (this.clicker.canExecuteClickNow()) {
         if (!MC.player.isBlocking()) {
            if (!MC.player.isUsingItem()) {
               this.clicker.click(attack);
            }
         }
      }
   }

   private boolean attackEntity(Entity target, EntityHitResult hit) {
      ItemStack stack = MC.player.getMainHandItem();
      PiercingWeapon piercing = (PiercingWeapon)stack.get(DataComponents.PIERCING_WEAPON);
      if (piercing != null && !MC.gameMode.isSpectator()) {
         MC.gameMode.piercingAttack(piercing);
         MC.player.swing(InteractionHand.MAIN_HAND);
         RiptideCpsTracker.recordLeft();
         this.queueHitFeedback(target);
         return true;
      } else if (!this.canBeAttackedWithVanillaPacket(target)) {
         return false;
      } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         return false;
      } else {
         try {
            ((RiptideMultiPlayerGameModeAccessor)MC.gameMode).riptide$ensureHasSentCarriedItem();
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }

         if ("Packet".equals(this.choice("attack-mode"))) {
            MC.getConnection().send(new ServerboundAttackPacket(target.getId()));
            if (!MC.gameMode.isSpectator()) {
               MC.player.attack(target);
               MC.player.resetAttackStrengthTicker();
            }

            MC.player.swing(InteractionHand.MAIN_HAND);
            RiptideCpsTracker.recordLeft();
            this.queueHitFeedback(target);
            return true;
         } else if (!RiptideCombatClicker.queueAttack(hit)) {
            return false;
         } else {
            this.queueHitFeedback(target);
            MC.player.resetAttackStrengthTicker();
            return true;
         }
      }
   }

   private boolean canBeAttackedWithVanillaPacket(Entity target) {
      return target != null
         && target != MC.player
         && !(target instanceof ItemEntity)
         && !(target instanceof ExperienceOrb)
         && (!(target instanceof AbstractArrow) || target.isAttackable());
   }

   private void queueHitFeedback(Entity target) {
      if (target instanceof LivingEntity living) {
         if (this.bool("hitsound") || this.bool("hit-marker")) {
            this.pendingHitEntityId = living.getId();
            this.pendingHitPrevHurtTime = living.hurtTime;
            this.pendingHitTicks = 8;
         }
      }
   }

   private void confirmHitFeedback() {
      if (this.pendingHitEntityId >= 0) {
         Entity entity = MC.level.getEntity(this.pendingHitEntityId);
         boolean landed = entity instanceof LivingEntity living
            && (living.hurtTime > this.pendingHitPrevHurtTime || this.pendingHitPrevHurtTime >= 10 && living.hurtTime >= 10);
         if (landed) {
            this.showHitMarker(entity);
            this.playHitsound();
            RiptideChamsHit.mark(entity);
         }

         if (landed || entity == null || --this.pendingHitTicks <= 0) {
            this.pendingHitEntityId = -1;
         }
      }
   }

   private void showHitMarker(Entity target) {
      if (this.bool("hit-marker")) {
         RiptideKillAuraRenderer.show(target.getBoundingBox().inflate(target.getPickRadius()));
      }
   }

   private void playHitsound() {
      if (this.bool("hitsound")) {
         MC.getSoundManager().play(SimpleSoundInstance.forUI(HITSOUND, 1.0F, 0.7F));
      }
   }

   private boolean wouldDoCriticalHit() {
      return this.canDoCriticalHit() && MC.player.fallDistance > 0.0;
   }

   private boolean canDoCriticalHit() {
      return this.allowsCriticalHit() && MC.player.getAttackStrengthScale(0.5F) > 0.9F;
   }

   private boolean allowsCriticalHit() {
      Module flight = ModuleRegistry.get("flight");
      boolean flyRunning = flight != null && flight.isEnabled();
      return !flyRunning
         && !MC.player.isInLiquid()
         && !MC.player.isPassenger()
         && !this.insideWebBlock()
         && !MC.player.hasEffect(MobEffects.LEVITATION)
         && !MC.player.hasEffect(MobEffects.BLINDNESS)
         && !MC.player.hasEffect(MobEffects.SLOW_FALLING)
         && !MC.player.onClimbable()
         && !MC.player.isNoGravity()
         && !MC.player.isHandsBusy()
         && !MC.player.getAbilities().flying
         && !MC.player.onGround();
   }

   private boolean insideWebBlock() {
      return MC.level.getBlockStates(MC.player.getBoundingBox()).anyMatch(state -> state.getBlock() instanceof WebBlock);
   }

   private double interactionRange() {
      return MC.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
   }

   private double scanRange() {
      return Math.max(this.interactionRange(), 0.0) + this.scanAddition;
   }

   private boolean attackRangeIsInRange(ItemStack stack, Vec3 pos) {
      AttackRange attackRange = (AttackRange)stack.get(DataComponents.ATTACK_RANGE);
      if (attackRange == null) {
         attackRange = AttackRange.defaultFor(MC.player);
      }

      return attackRange.isInRange(MC.player, pos);
   }

   private double nextScanAddition() {
      return 2.0 + this.random.nextDouble() * 1.0;
   }

   private boolean hasCooldown() {
      return MC.player.getAttributeValue(Attributes.ATTACK_SPEED) < 20.0;
   }

   private float currentItemAttackStrengthDelay() {
      double attackSpeed = MC.player.getAttributeValue(Attributes.ATTACK_SPEED);
      if (this.bool("auto-sword") && this.hasCooldown() && this.switchedToSlot < 0) {
         Integer slot = this.determineWeaponSlot(null, false);
         if (slot != null) {
            attackSpeed = this.attributeValue(
               MC.player.getInventory().getItem(slot), Attributes.ATTACK_SPEED, MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED)
            );
         }
      }

      return (float)(1.0 / attackSpeed * 20.0);
   }

   private int attackStrengthTicker() {
      return ((RiptideLivingEntityAccessor)MC.player).riptide$getAttackStrengthTicker();
   }

   private boolean isCooldownPassed(int ticks) {
      float delay = this.currentItemAttackStrengthDelay();
      return (this.attackStrengthTicker() + ticks) / delay >= this.nextCooldown + this.clickOffsetTicks / delay;
   }

   private void newCooldown() {
      this.nextCooldown = 1.0F;
      this.clickOffsetTicks = this.rollClickOffsetTicks();
   }

   private float rollClickOffsetTicks() {
      double magnitude = (this.random.nextDouble() + this.random.nextDouble()) * 0.5;
      return (float)(this.random.nextDouble() < 0.2 ? -magnitude : magnitude);
   }

   private boolean wouldBlockHit(LivingEntity target) {
      DamageSource source = target.level().damageSources().playerAttack(MC.player);
      return this.getBlockedDamage(target, source, 1.0F) > 0.0F;
   }

   private float getBlockedDamage(LivingEntity target, DamageSource source, float amount) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else {
         ItemStack blockingStack = target.getItemBlockingWith();
         if (blockingStack == null) {
            return 0.0F;
         } else {
            BlocksAttacks blocksAttacks = (BlocksAttacks)blockingStack.get(DataComponents.BLOCKS_ATTACKS);
            if (blocksAttacks == null) {
               return 0.0F;
            } else if (blocksAttacks.bypassedBy().map(tag -> tag.contains(source.typeHolder())).orElse(false)) {
               return 0.0F;
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
               return 0.0F;
            } else {
               double horizontalAngle = Math.PI;
               Vec3 sourcePosition = source.getSourcePosition();
               if (sourcePosition != null) {
                  Vec3 view = target.calculateViewVector(0.0F, target.getYHeadRot());
                  Vec3 to = sourcePosition.subtract(target.position());
                  Vec3 sourceDirection = new Vec3(to.x, 0.0, to.z).normalize();
                  horizontalAngle = Math.acos(sourceDirection.dot(view));
               }

               return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
            }
         }
      }
   }

   static void stabilizedFill(int[] cycle, Random random) {
      if (cycle.length != 0) {
         int clicks = Math.min(cycle.length, 5 + random.nextInt(4));
         int[] gaps = new int[clicks];
         int baseGap = cycle.length / clicks;
         int remainder = cycle.length % clicks;
         Arrays.fill(gaps, baseGap);

         for (int i = 0; i < remainder; i++) {
            gaps[i]++;
         }

         shuffle(gaps, random);
         boolean allEqual = true;

         for (int i = 1; i < gaps.length; i++) {
            if (gaps[i] != gaps[0]) {
               allEqual = false;
               break;
            }
         }

         if (allEqual && gaps.length > 1 && gaps[0] > 1) {
            int donor = random.nextInt(gaps.length);
            int receiver = (donor + 1 + random.nextInt(gaps.length - 1)) % gaps.length;
            gaps[donor]--;
            gaps[receiver]++;
         }

         int transfers = random.nextInt(gaps.length + 1) + random.nextInt(gaps.length + 1);

         for (int ix = 0; ix < transfers; ix++) {
            int donor = random.nextInt(gaps.length);
            int receiver = random.nextInt(gaps.length);
            if (donor != receiver && gaps[donor] > 1) {
               gaps[donor]--;
               gaps[receiver]++;
            }
         }

         shuffle(gaps, random);
         int position = random.nextInt(cycle.length);

         for (int gap : gaps) {
            cycle[position]++;
            position = (position + gap) % cycle.length;
         }
      }
   }

   private static void shuffle(int[] values, Random random) {
      for (int i = values.length - 1; i > 0; i--) {
         int other = random.nextInt(i + 1);
         int value = values[i];
         values[i] = values[other];
         values[other] = value;
      }
   }

   private Integer determineWeaponSlot(LivingEntity target, boolean enforceShield) {
      if (RiptideMaceAssist.suppressWeaponSwitch()) {
         return null;
      } else {
         boolean requiresShield = enforceShield || target != null && this.wouldBlockHit(target);
         boolean requiresMace = this.canMaceSmash() && (this.hotbarContainsMace() || !requiresShield);
         Integer bestSlot = null;
         ItemStack bestStack = null;

         for (int slot = 0; slot < 9; slot++) {
            if (!RiptideHandArbiter.slotReserved(slot, this.id())) {
               ItemStack stack = MC.player.getInventory().getItem(slot);
               if (!stack.isEmpty()) {
                  boolean eligible = requiresMace
                     ? stack.getItem() instanceof MaceItem
                     : (requiresShield ? stack.is(ItemTags.AXES) : stack.is(ItemTags.SWORDS));
                  if (eligible && (bestStack == null || (requiresMace ? this.compareMaces(stack, bestStack) > 0 : this.compareWeapons(stack, bestStack) > 0))) {
                     bestSlot = slot;
                     bestStack = stack;
                  }
               }
            }
         }

         return bestSlot;
      }
   }

   private boolean hotbarContainsMace() {
      for (int slot = 0; slot < 9; slot++) {
         if (!RiptideHandArbiter.slotReserved(slot, this.id()) && MC.player.getInventory().getItem(slot).getItem() instanceof MaceItem) {
            return true;
         }
      }

      return false;
   }

   private boolean canMaceSmash() {
      return MaceItem.canSmashAttack(MC.player);
   }

   private boolean isAutoWeaponBusy() {
      return MC.player.isUsingItem() && MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND && MC.player.getUseItem().has(DataComponents.CONSUMABLE);
   }

   private void tickAutoSwordReset() {
      if (this.bool("switch-back")) {
         if (this.previousSlot >= 0 && this.switchBackTicks > 0) {
            if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
               int selected = MC.player.getInventory().getSelectedSlot();
               if (!CrystalAuraModule.holdsBorrowedSlot(selected) && !AnchorAuraModule.holdsBorrowedSlot(selected)) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
                  this.switchBackTicks = 0;
               }
            } else {
               this.switchBackTicks--;
               if (this.switchBackTicks == 0 && (!this.canRun() || this.targetOutOfRange(this.currentTarget))) {
                  if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
                     if (!CrystalAuraModule.reservesCombatTick() && !CrystalAuraModule.hasLiveCommitment() && !AnchorAuraModule.reservesCombatTick()) {
                        if (!RiptideHandArbiter.slotReserved(this.previousSlot, this.id())) {
                           int tick = RiptideSharedState.get().getClientTickCounter();
                           if (tick != this.hotbarChangeTick) {
                              if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                                 try {
                                    int back = this.previousSlot;
                                    this.previousSlot = -1;
                                    this.switchedToSlot = -1;
                                    RiptideInventoryHelper.selectHotbarSlot(MC, back);
                                    this.hotbarChangeTick = tick;
                                 } finally {
                                    RiptideHandArbiter.endHandPacketGroup(this.id());
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
   }

   private boolean targetOutOfRange(LivingEntity target) {
      if (target == null) {
         return true;
      } else {
         RiptideRotationUtil.Rotation rotation = RiptideKillAuraRotation.getCurrentRotation();
         if (rotation == null) {
            rotation = RiptideRotationUtil.playerRotation(MC.player);
         }

         EntityHitResult hit = this.isLookingAtEntity(target, rotation, this.interactionRange(), 0.0);
         return hit == null || !this.attackRangeIsInRange(MC.player.getMainHandItem(), hit.getLocation());
      }
   }

   private int compareWeapons(ItemStack first, ItemStack second) {
      int result = Double.compare(this.estimatedWeaponDamage(first), this.estimatedWeaponDamage(second));
      if (result != 0) {
         return result;
      } else {
         result = Double.compare(this.secondaryWeaponValue(first), this.secondaryWeaponValue(second));
         if (result != 0) {
            return result;
         } else {
            result = Boolean.compare(first.is(ItemTags.SWORDS), second.is(ItemTags.SWORDS));
            if (result != 0) {
               return result;
            } else {
               result = Integer.compare(durability(first), durability(second));
               if (result != 0) {
                  return result;
               } else {
                  result = Integer.compare(enchantableValue(first), enchantableValue(second));
                  return result != 0 ? result : Integer.compare(first.hashCode(), second.hashCode());
               }
            }
         }
      }
   }

   private int compareMaces(ItemStack first, ItemStack second) {
      int result = Double.compare(this.estimatedMaceDamage(first), this.estimatedMaceDamage(second));
      if (result != 0) {
         return result;
      } else {
         result = Integer.compare(durability(first), durability(second));
         if (result != 0) {
            return result;
         } else {
            result = Integer.compare(enchantableValue(first), enchantableValue(second));
            return result != 0 ? result : Integer.compare(first.hashCode(), second.hashCode());
         }
      }
   }

   private double estimatedWeaponDamage(ItemStack stack) {
      double damage = this.attributeValue(stack, Attributes.ATTACK_DAMAGE, MC.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
      int sharpness = this.enchantmentLevel(stack, Enchantments.SHARPNESS);
      if (sharpness > 0) {
         damage += 0.5 * sharpness + 0.5;
      }

      double speed = this.attributeValue(stack, Attributes.ATTACK_SPEED, MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
      double probability = Math.pow(0.85, 0.05);
      double adjusted = Math.pow(probability, Math.ceil(20.0 / speed * 0.9));
      double fire = Math.max(0.0, this.enchantmentLevel(stack, Enchantments.FIRE_ASPECT) * 4.0 - 1.0) * 0.33;
      double factor = this.enchantmentLevel(stack, Enchantments.SMITE) * 0.2
         + this.enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 0.2
         + this.enchantmentLevel(stack, Enchantments.KNOCKBACK) * 0.2;
      return damage * speed * adjusted * (1.0 + factor) + fire;
   }

   private double estimatedMaceDamage(ItemStack stack) {
      double damage = this.attributeValue(stack, Attributes.ATTACK_DAMAGE, MC.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
      int sharpness = this.enchantmentLevel(stack, Enchantments.SHARPNESS);
      if (sharpness > 0) {
         damage += 0.5 * sharpness + 0.5;
      }

      double speed = this.attributeValue(stack, Attributes.ATTACK_SPEED, MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
      double probability = Math.pow(0.85, 0.05);
      double adjusted = Math.pow(probability, Math.ceil(20.0 / speed * 0.9));
      double factor = this.enchantmentLevel(stack, Enchantments.DENSITY) * 0.5
         + this.enchantmentLevel(stack, Enchantments.BREACH) * 0.15
         + this.enchantmentLevel(stack, Enchantments.SMITE) * 0.2
         + this.enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 0.2
         + this.enchantmentLevel(stack, Enchantments.WIND_BURST) * 0.2;
      return damage * speed * adjusted + factor + 29.0;
   }

   private double secondaryWeaponValue(ItemStack stack) {
      return this.enchantmentLevel(stack, Enchantments.LOOTING) * 0.05
         + this.enchantmentLevel(stack, Enchantments.UNBREAKING) * 0.05
         + this.enchantmentLevel(stack, Enchantments.MENDING) * 0.1
         - this.enchantmentLevel(stack, Enchantments.VANISHING_CURSE) * 0.1
         + this.enchantmentLevel(stack, Enchantments.SWEEPING_EDGE) * 0.2
         + this.enchantmentLevel(stack, Enchantments.KNOCKBACK) * 0.25;
   }

   private static int durability(ItemStack stack) {
      return stack.getMaxDamage() - stack.getDamageValue();
   }

   private static int enchantableValue(ItemStack stack) {
      return stack.has(DataComponents.ENCHANTABLE) ? ((Enchantable)stack.get(DataComponents.ENCHANTABLE)).value() : 0;
   }

   private double attributeValue(ItemStack stack, Holder<Attribute> attribute, double base) {
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
      return modifiers == null
         ? ((Attribute)attribute.value()).sanitizeValue(base)
         : ((Attribute)attribute.value()).sanitizeValue(modifiers.compute(attribute, base, EquipmentSlot.MAINHAND));
   }

   private int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
      try {
         Holder<Enchantment> holder = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
         return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
      } catch (Throwable var4) {
         return 0;
      }
   }

   private void resetRuntime() {
      this.currentTarget = null;
      this.previousSlot = -1;
      this.switchedToSlot = -1;
      this.switchBackTicks = 0;
      this.lastShieldSeenTick = Integer.MIN_VALUE;
      this.shieldHoldEntityId = -1;
      this.shieldBreakLandedTick = Integer.MIN_VALUE;
      this.shieldBreakLandedEntityId = -1;
      this.postUseSuppressTicks = 0;
      this.pendingHitEntityId = -1;
      this.aimPointTracker.clear();
      this.missState.clear();
      RiptideKillAuraRenderer.clear();
   }

   static final class AccuracyGovernor {
      static final double SAMPLE_YAW_SPEED = 5.0;
      static final int WINDOW_SAMPLES = 21;
      static final int STALE_TICKS = 30;
      static final double SAFE_MEAN = 8.5;
      static final double THROTTLED_SAMPLE = 6.7F;
      static final double BAND_SAMPLE = 6.5;
      private long lastAttackMs = Long.MIN_VALUE;
      private int samples;
      private double speedSum;
      private double errorSum;
      private float lastYaw;
      private float lastPitch;
      private boolean seeded;
      private int ticksSinceSample;

      void onAttackSent() {
         this.lastAttackMs = System.currentTimeMillis();
      }

      boolean attackedRecently() {
         return this.lastAttackMs != Long.MIN_VALUE && System.currentTimeMillis() - this.lastAttackMs <= 60L;
      }

      void onOutgoingRotation(float yaw, float pitch, float perfectYaw) {
         if (!this.seeded) {
            this.lastYaw = yaw;
            this.lastPitch = pitch;
            this.seeded = true;
         } else {
            double yawSpeed = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw));
            double pitchSpeed = Math.abs(Mth.wrapDegrees(pitch - this.lastPitch));
            this.lastYaw = yaw;
            this.lastPitch = pitch;
            if (!(yawSpeed <= 5.0) && !Float.isNaN(perfectYaw) && this.attackedRecently()) {
               this.ticksSinceSample = 0;
               if (this.samples >= 21) {
                  this.samples = 0;
                  this.speedSum = 0.0;
                  this.errorSum = 0.0;
               }

               this.samples++;
               this.speedSum += yawSpeed + pitchSpeed;
               this.errorSum = this.errorSum + Math.abs(Mth.wrapDegrees(yaw - perfectYaw));
            } else {
               this.ticksSinceSample++;
            }
         }
      }

      void forgetPacketHistory() {
         this.seeded = false;
      }

      boolean speedAtRisk() {
         return !this.discardIfStale() && this.projectedMean(this.speedSum, 6.7F) > 8.5;
      }

      boolean errorAtRisk() {
         return !this.discardIfStale() && this.projectedMean(this.errorSum, 6.5) > 8.5;
      }

      private boolean discardIfStale() {
         if (this.samples > 0 && this.ticksSinceSample > 30) {
            this.samples = 0;
            this.speedSum = 0.0;
            this.errorSum = 0.0;
            return true;
         } else {
            return false;
         }
      }

      private double projectedMean(double sum, double remainingSample) {
         return this.samples <= 0 ? 0.0 : (sum + (21 - this.samples) * remainingSample) / 21.0;
      }

      void reset() {
         this.lastAttackMs = Long.MIN_VALUE;
         this.samples = 0;
         this.speedSum = 0.0;
         this.errorSum = 0.0;
         this.seeded = false;
         this.ticksSinceSample = 0;
      }
   }

   static final class AimPointTracker {
      private static final double BAND_FLOOR_DEGREES = 2.2;
      private static final double BAND_LO_HWA_FRACTION = 0.55;
      private static final double BAND_HI_MIN_WIDTH = 3.6;
      private static final double BAND_HI_HWA_FRACTION = 0.85;
      private static final double HWA_INSET_BLOCKS = 0.04;
      private static final int SIDE_FLIP_TICKS_MIN = 2;
      private static final int SIDE_FLIP_TICKS_SPAN = 3;
      private static final int EXCURSION_INTERVAL_MIN = 30;
      private static final int EXCURSION_INTERVAL_SPAN = 9;
      private static final int EXCURSION_TICKS = 2;
      private static final int EXCURSION_MAX_HOLD_TICKS = 8;
      private static final double EXCURSION_DEGREES_MIN = 13.0;
      private static final double EXCURSION_DEGREES_SPAN = 3.0;
      private static final double VERTICAL_OFFSET_MIN = 0.5;
      private static final double VERTICAL_OFFSET_SPAN = 1.5;
      private static final int VERTICAL_HOLD_TICKS_MIN = 50;
      private static final int VERTICAL_HOLD_TICKS_SPAN = 50;
      private static final double VERTICAL_MAX_STEP_DEGREES = 0.5;
      static final double FREE_MAX_STEP_DEGREES = 30.0;
      static final double WINDOW_MAX_STEP_DEGREES = 4.0;
      private final Random random;
      private int entityId = Integer.MIN_VALUE;
      private int side = 1;
      private int sideFlipTicks;
      private boolean sideChangePending;
      private int excursionCountdown;
      private int excursionTicksLeft;
      private int excursionHoldTicks;
      private double excursionDegrees;
      private double errorDegrees = Double.NaN;
      private double verticalOffsetDegrees;
      private double verticalTargetDegrees;
      private int verticalHoldTicks;

      AimPointTracker(Random random) {
         this.random = random;
      }

      Vec3 begin(int targetEntityId, AABB box, Vec3 eyes, boolean inAttackWindow, boolean allowExcursion) {
         if (this.entityId != targetEntityId) {
            this.resetTarget(targetEntityId);
         }

         return this.aimPoint(box, eyes, inAttackWindow, allowExcursion);
      }

      Vec3 advance(int targetEntityId, AABB box, Vec3 eyes, boolean inAttackWindow, boolean allowExcursion) {
         if (this.entityId != targetEntityId) {
            return this.begin(targetEntityId, box, eyes, inAttackWindow, allowExcursion);
         } else {
            this.tickState(inAttackWindow, allowExcursion);
            return this.aimPoint(box, eyes, inAttackWindow, allowExcursion);
         }
      }

      void clear() {
         this.entityId = Integer.MIN_VALUE;
      }

      private void resetTarget(int targetEntityId) {
         this.entityId = targetEntityId;
         this.side = this.random.nextBoolean() ? 1 : -1;
         this.sideFlipTicks = rollSideFlipTicks(this.random);
         this.sideChangePending = false;
         this.excursionCountdown = rollExcursionInterval(this.random);
         this.excursionTicksLeft = 0;
         this.excursionHoldTicks = 0;
         this.errorDegrees = Double.NaN;
         this.verticalOffsetDegrees = rollVerticalOffset(this.random);
         this.verticalTargetDegrees = this.verticalOffsetDegrees;
         this.verticalHoldTicks = 50 + this.random.nextInt(50);
      }

      private void tickState(boolean inAttackWindow, boolean allowExcursion) {
         if (this.excursionTicksLeft > 0 && Math.abs(this.errorDegrees) >= this.excursionDegrees - 1.0E-9) {
            this.excursionTicksLeft--;
         }

         if (--this.excursionCountdown <= 0) {
            if (allowExcursion && (!inAttackWindow || this.excursionHoldTicks >= 8)) {
               this.excursionTicksLeft = 2;
               this.excursionDegrees = rollExcursionDegrees(this.random);
               this.excursionCountdown = rollExcursionInterval(this.random);
               this.excursionHoldTicks = 0;
            } else {
               this.excursionCountdown = 1;
               this.excursionHoldTicks++;
            }
         }

         if (this.excursionTicksLeft <= 0 && !this.sideChangePending && --this.sideFlipTicks <= 0) {
            this.sideChangePending = true;
         }

         if (--this.verticalHoldTicks <= 0) {
            double magnitude = 0.5 + this.random.nextDouble() * 1.5;
            this.verticalTargetDegrees = Math.copySign(magnitude, this.verticalOffsetDegrees);
            this.verticalHoldTicks = 50 + this.random.nextInt(50);
         }

         double verticalDelta = this.verticalTargetDegrees - this.verticalOffsetDegrees;
         if (Math.abs(verticalDelta) > 0.5) {
            this.verticalOffsetDegrees = this.verticalOffsetDegrees + Math.copySign(0.5, verticalDelta);
         } else {
            this.verticalOffsetDegrees = this.verticalTargetDegrees;
         }

         if (Math.abs(this.verticalOffsetDegrees) < 0.5) {
            this.verticalOffsetDegrees = Math.copySign(0.5, verticalDelta);
         }
      }

      private Vec3 aimPoint(AABB box, Vec3 eyes, boolean inAttackWindow, boolean allowExcursion) {
         Vec3 center = box.getCenter();
         double dx = center.x - eyes.x;
         double dz = center.z - eyes.z;
         double horizontal = Math.max(Math.sqrt(dx * dx + dz * dz), 0.3);
         double halfWidth = Math.min(box.getXsize(), box.getZsize()) * 0.5;
         double hwa = horizontalHalfWidthAngle(halfWidth, horizontal);
         double signed = this.advanceErrorAngle(hwa, inAttackWindow, allowExcursion);
         double perpX = -dz / horizontal;
         double perpZ = dx / horizontal;
         double lateral = horizontal * Math.tan(Math.toRadians(signed));
         double dy = horizontal * Math.tan(Math.toRadians(this.verticalOffsetDegrees));
         return new Vec3(center.x + perpX * lateral, center.y + dy, center.z + perpZ * lateral);
      }

      private double advanceErrorAngle(double hwa, boolean inAttackWindow, boolean allowExcursion) {
         double bandLo = aimBandLow(hwa);
         if (Double.isNaN(this.errorDegrees)) {
            this.errorDegrees = this.side * rollBandMagnitude(this.random, hwa);
            return this.errorDegrees;
         } else {
            boolean excursion = allowExcursion && this.excursionTicksLeft > 0;
            if (this.sideChangePending && !excursion && Math.abs(this.errorDegrees) <= bandLo + 1.0E-9) {
               this.side = -this.side;
               this.errorDegrees = -this.errorDegrees;
               this.sideChangePending = false;
               this.sideFlipTicks = rollSideFlipTicks(this.random);
               return this.errorDegrees;
            } else {
               double goal;
               if (excursion) {
                  goal = this.side * this.excursionDegrees;
               } else if (this.sideChangePending) {
                  goal = this.side * bandLo;
               } else {
                  goal = this.side * rollBandMagnitude(this.random, hwa);
               }

               double cap = inAttackWindow ? 4.0 : 30.0;
               double delta = goal - this.errorDegrees;
               this.errorDegrees = Math.abs(delta) > cap ? this.errorDegrees + Math.copySign(cap, delta) : goal;
               if (Math.abs(this.errorDegrees) < bandLo) {
                  this.errorDegrees = Math.copySign(bandLo, this.errorDegrees == 0.0 ? this.side : this.errorDegrees);
               }

               return this.errorDegrees;
            }
         }
      }

      static double horizontalHalfWidthAngle(double halfWidth, double distance) {
         return Math.toDegrees(Math.atan(Math.max(halfWidth - 0.04, 0.01) / Math.max(distance, 0.3)));
      }

      static double aimBandLow(double hwa) {
         return Math.min(2.2, 0.55 * hwa);
      }

      static double aimBandHigh(double hwa) {
         return Math.max(aimBandLow(hwa) + 3.6, 0.85 * hwa);
      }

      static double rollBandMagnitude(Random random, double hwa) {
         double low = aimBandLow(hwa);
         return low + random.nextDouble() * (aimBandHigh(hwa) - low);
      }

      static int rollSideFlipTicks(Random random) {
         return 2 + random.nextInt(4);
      }

      static int rollExcursionInterval(Random random) {
         return 30 + random.nextInt(9);
      }

      static double rollExcursionDegrees(Random random) {
         return 13.0 + random.nextDouble() * 3.0;
      }

      private static double rollVerticalOffset(Random random) {
         double magnitude = 0.5 + random.nextDouble() * 1.5;
         return random.nextBoolean() ? magnitude : -magnitude;
      }
   }

   private final class Clicker {
      private final KillAuraModule.RollingClickArray clickArray;
      private int ticksSinceLastClick;
      private boolean shieldBreakBypass;

      Clicker() {
         Objects.requireNonNull(KillAuraModule.this);
         super();
         this.clickArray = new KillAuraModule.RollingClickArray(20, 2);
         this.fill();
      }

      void tick() {
         this.ticksSinceLastClick++;
         if (this.clickArray.advance(1)) {
            int[] cycle = new int[20];
            KillAuraModule.stabilizedFill(cycle, KillAuraModule.this.random);
            this.clickArray.push(cycle);
         }
      }

      private void fill() {
         this.clickArray.clear();
         int[] cycle = new int[20];

         for (int i = 0; i < this.clickArray.iterations; i++) {
            Arrays.fill(cycle, 0);
            KillAuraModule.stabilizedFill(cycle, KillAuraModule.this.random);
            this.clickArray.push(cycle);
            this.clickArray.advance(20);
         }
      }

      int getClickAmount(int tick) {
         return this.isEnforcedClick() ? 1 : this.clickArray.get(tick);
      }

      private boolean isEnforcedClick() {
         return KillAuraModule.this.hasCooldown() && KillAuraModule.this.isCooldownPassed(0)
            ? true
            : System.currentTimeMillis() - KillAuraModule.lastClickTime >= 1000L;
      }

      boolean willClickAt(int tick) {
         return this.getClickAmount(tick) > 0;
      }

      boolean isClickTick() {
         return this.willClickAt(0);
      }

      boolean canExecuteClickNow() {
         if (!this.shieldBreakBypass && this.getClickAmount(0) <= 0) {
            return false;
         } else {
            return KillAuraModule.missTimeActive() ? false : this.shieldBreakBypass || KillAuraModule.this.isCooldownPassed(0);
         }
      }

      void click(BooleanSupplier attack) {
         int amount = this.shieldBreakBypass ? Math.max(1, this.getClickAmount(0)) : this.getClickAmount(0);

         for (int i = 0; i < amount; i++) {
            if (!KillAuraModule.missTimeActive() && (this.shieldBreakBypass || KillAuraModule.this.isCooldownPassed(0)) && attack.getAsBoolean()) {
               KillAuraModule.this.newCooldown();
               KillAuraModule.lastClickTime = System.currentTimeMillis();
               this.ticksSinceLastClick = 0;
            }
         }
      }
   }

   static final class MissState {
      private int pendingTicks;
      private boolean lastWasMiss;
      private int targetId = -1;
      private Vec3 point;

      boolean isPending() {
         return this.pendingTicks > 0;
      }

      boolean isFireTick() {
         return this.pendingTicks == 1;
      }

      boolean mayRoll() {
         return this.pendingTicks == 0 && !this.lastWasMiss;
      }

      void begin(int entityId, Vec3 missPoint) {
         this.pendingTicks = 2;
         this.targetId = entityId;
         this.point = missPoint;
      }

      boolean matches(int entityId) {
         return this.targetId == entityId;
      }

      Vec3 point() {
         return this.point;
      }

      void advance() {
         if (this.pendingTicks == 1) {
            this.lastWasMiss = true;
         }

         if (this.pendingTicks > 0) {
            this.pendingTicks--;
         }

         if (this.pendingTicks == 0) {
            this.targetId = -1;
            this.point = null;
         }
      }

      void onAttackFired() {
         this.lastWasMiss = false;
      }

      void clear() {
         this.pendingTicks = 0;
         this.lastWasMiss = false;
         this.targetId = -1;
         this.point = null;
      }
   }

   static final class RollingClickArray {
      private final int cycleLength;
      final int iterations;
      private final int[] array;
      private int head;

      RollingClickArray(int cycleLength, int iterations) {
         this.cycleLength = cycleLength;
         this.iterations = iterations;
         this.array = new int[cycleLength * iterations];
      }

      int get(int relativeIndex) {
         return this.array[(this.head + relativeIndex) % this.array.length];
      }

      boolean advance(int amount) {
         this.head = (this.head + amount) % this.array.length;
         return this.head % this.cycleLength == 0;
      }

      void clear() {
         Arrays.fill(this.array, 0);
         this.head = 0;
      }

      void push(int[] cycle) {
         if (cycle.length != this.cycleLength) {
            throw new IllegalArgumentException("Array size must match cycle length");
         } else {
            if (this.head == 0) {
               System.arraycopy(cycle, 0, this.array, this.cycleLength, this.cycleLength);
            } else {
               if (this.head != this.cycleLength) {
                  throw new IllegalStateException("Head must be at 0 or cycle length");
               }

               System.arraycopy(cycle, 0, this.array, 0, this.cycleLength);
            }
         }
      }

      int cycleClickCount(int offset) {
         int total = 0;

         for (int index = offset; index < offset + this.cycleLength; index++) {
            total += this.array[index];
         }

         return total;
      }
   }

   static final class TickVerdict {
      private int tick = Integer.MIN_VALUE;
      private boolean value;

      boolean resolve(int clientTick, BooleanSupplier live) {
         if (clientTick != this.tick) {
            this.tick = clientTick;
            this.value = live.getAsBoolean();
         }

         return this.value;
      }
   }
}
