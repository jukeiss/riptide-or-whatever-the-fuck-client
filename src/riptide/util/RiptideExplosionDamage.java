package riptide.util;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import riptide.mixin.accessor.RiptideEntityAccessor;
import riptide.mixin.accessor.RiptideLivingEntityAccessor;

public final class RiptideExplosionDamage {
   public static final float END_CRYSTAL_POWER = 6.0F;
   public static final float RESPAWN_ANCHOR_POWER = 5.0F;
   public static final float BED_POWER = 5.0F;
   public static final float TERRAIN_BLAST_RESISTANCE = 9.0F;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final int CACHE_MAX_ENTRIES = 256;
   private static final long NO_SCAN = -1L;
   private static final Object CACHE_LOCK = new Object();
   private static final Map<RiptideExplosionDamage.CacheKey, Double> CACHE = new LinkedHashMap<RiptideExplosionDamage.CacheKey, Double>(256, 1.0F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<RiptideExplosionDamage.CacheKey, Double> eldest) {
         return this.size() > 256;
      }
   };
   private static final AtomicLong GENERATION = new AtomicLong();
   private static long cachePassId = -1L;
   private static long cacheGeneration = -1L;
   private static WeakReference<Level> cacheLevel = new WeakReference<>(null);
   private static int cacheTick = Integer.MIN_VALUE;
   public static final int HURT_COOLDOWN_GRACE = 10;
   public static final double SELF_LETHAL_MARGIN = 1.15;
   public static final double SELF_LETHAL_HEADROOM = 2.0;
   public static final double TARGET_LETHAL_MARGIN = 1.0;

   private RiptideExplosionDamage() {
   }

   public static double damageTo(LivingEntity target, Vec3 explosionPos, float power) {
      return damageTo(target, explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static double damageTo(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return damage(target, explosionPos, power, options == null ? RiptideExplosionDamage.Options.DEFAULT : options, true);
   }

   private static double damage(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options, boolean sampleExposure) {
      if (target != null && explosionPos != null && power > 0.0F) {
         Level level = target.level();
         if (level != null && !isExplosionImmune(target)) {
            float range = power * 2.0F;
            double distanceSqr = distanceSqr(target, explosionPos, options);
            if (distanceSqr > (double)range * range) {
               return 0.0;
            } else {
               double decay = 1.0 - Math.sqrt(distanceSqr) / range;
               double seen = (sampleExposure ? exposure(target, explosionPos, options) : 1.0F) * decay;
               double raw = (seen * seen + seen) / 2.0 * 7.0 * range + 1.0;
               if (raw <= 0.0) {
                  return 0.0;
               } else {
                  DamageSource source = options.damageSource() != null ? options.damageSource() : explosionSource(level, explosionPos);
                  return effectiveDamage(target, source, (float)raw, options.estimateProtection());
               }
            }
         } else {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   private static double distanceSqr(LivingEntity target, Vec3 explosionPos, RiptideExplosionDamage.Options options) {
      AABB box = options.overrideBox();
      if (box == null) {
         return target.distanceToSqr(explosionPos);
      } else {
         double dx = (box.minX + box.maxX) / 2.0 - explosionPos.x;
         double dy = box.minY - explosionPos.y;
         double dz = (box.minZ + box.maxZ) / 2.0 - explosionPos.z;
         return dx * dx + dy * dy + dz * dz;
      }
   }

   public static double selfDamage(Vec3 explosionPos, float power) {
      return selfDamage(explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static double selfDamage(Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return damageTo(Minecraft.getInstance().player, explosionPos, power, options);
   }

   public static boolean wouldKill(LivingEntity target, Vec3 explosionPos, float power) {
      return wouldKill(target, explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static boolean wouldKill(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      double health = effectiveHealth(target);
      return health <= 0.0 ? false : damageTo(target, explosionPos, power, options) >= health;
   }

   public static boolean wouldKillSelf(Vec3 explosionPos, float power) {
      return wouldKill(Minecraft.getInstance().player, explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static double effectiveHealth(LivingEntity target) {
      return target != null && !target.isDeadOrDying() ? (double)target.getHealth() + target.getAbsorptionAmount() : 0.0;
   }

   public static boolean inHurtCooldown(LivingEntity target, DamageSource source) {
      if (target == null) {
         return false;
      } else {
         return source != null && source.is(DamageTypeTags.BYPASSES_COOLDOWN) ? false : target.invulnerableTime > 10;
      }
   }

   public static boolean killsTarget(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return killsTarget(target, damageTo(target, explosionPos, power, options), options);
   }

   public static boolean killsTarget(LivingEntity target, double predictedDamage, RiptideExplosionDamage.Options options) {
      if (target != null && predictedDamage > 0.0) {
         double health = effectiveHealth(target);
         if (health <= 0.0) {
            return false;
         } else {
            return inHurtCooldown(target, options == null ? null : options.damageSource()) ? false : predictedDamage * 1.0 >= health;
         }
      } else {
         return false;
      }
   }

   public static boolean killsSelf(Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return killsSelf(selfDamage(explosionPos, power, options));
   }

   public static boolean killsSelf(double predictedSelfDamage) {
      LocalPlayer player = Minecraft.getInstance().player;
      double health = effectiveHealth(player);
      return health <= 0.0 ? false : predictedSelfDamage * 1.15 + 2.0 >= health;
   }

   public static boolean killsSelfThroughTotem(double predictedSelfDamage) {
      return !killsSelf(predictedSelfDamage) ? false : !totemHeld();
   }

   private static boolean totemHeld() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? false : player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   public static RiptideExplosionDamage.Lethality lethality(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return lethalityOf(rank(target, explosionPos, power, options), target, options);
   }

   public static RiptideExplosionDamage.Lethality cachedLethality(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return lethalityOf(cachedRank(target, explosionPos, power, options), target, options);
   }

   public static RiptideExplosionDamage.Lethality lethalityOf(
      RiptideExplosionDamage.Ranking ranking, LivingEntity target, RiptideExplosionDamage.Options options
   ) {
      return ranking == null
         ? new RiptideExplosionDamage.Lethality(false, false, 0.0, 0.0)
         : new RiptideExplosionDamage.Lethality(
            killsTarget(target, ranking.targetDamage(), options), killsSelf(ranking.selfDamage()), ranking.targetDamage(), ranking.selfDamage()
         );
   }

   public static RiptideExplosionDamage.Ranking rank(LivingEntity target, Vec3 explosionPos, float power) {
      return rank(target, explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static RiptideExplosionDamage.Ranking rank(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      double targetDamage = damageTo(target, explosionPos, power, options);
      LocalPlayer player = Minecraft.getInstance().player;
      double self = target == player ? targetDamage : damageTo(player, explosionPos, power, options);
      return new RiptideExplosionDamage.Ranking(targetDamage, self);
   }

   public static double maxDamageTo(LivingEntity target, Vec3 explosionPos, float power) {
      return maxDamageTo(target, explosionPos, power, RiptideExplosionDamage.Options.DEFAULT);
   }

   public static double maxDamageTo(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return damage(target, explosionPos, power, options == null ? RiptideExplosionDamage.Options.DEFAULT : options, false);
   }

   public static double maxSelfDamage(Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return maxDamageTo(Minecraft.getInstance().player, explosionPos, power, options);
   }

   public static RiptideExplosionDamage.ScanPass beginScan() {
      Minecraft mc = Minecraft.getInstance();
      Level level = mc.level;
      int tick = currentTick(mc);
      synchronized (CACHE_LOCK) {
         cachePassId = GENERATION.incrementAndGet();
         rearmLocked(level, tick);
         return new RiptideExplosionDamage.ScanPass(cachePassId);
      }
   }

   private static void endScan(long passId) {
      synchronized (CACHE_LOCK) {
         if (cachePassId == passId) {
            CACHE.clear();
            GENERATION.incrementAndGet();
            cachePassId = -1L;
            cacheGeneration = -1L;
            cacheLevel = new WeakReference<>(null);
            cacheTick = Integer.MIN_VALUE;
         }
      }
   }

   public static double cachedDamageTo(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      if (target != null && explosionPos != null && power > 0.0F) {
         RiptideExplosionDamage.Options opts = options == null ? RiptideExplosionDamage.Options.DEFAULT : options;
         Minecraft mc = Minecraft.getInstance();
         Level level = mc.level;
         long generation;
         synchronized (CACHE_LOCK) {
            if (cachePassId == -1L) {
               generation = -1L;
            } else {
               int tick = currentTick(mc);
               if (cacheLevel.get() != level || cacheTick != tick) {
                  rearmLocked(level, tick);
               }

               generation = cacheGeneration;
            }
         }

         if (generation == -1L) {
            return damageTo(target, explosionPos, power, opts);
         } else {
            RiptideExplosionDamage.CacheKey key = new RiptideExplosionDamage.CacheKey(
               target, target.getBoundingBox(), explosionPos, power, opts, stateFingerprint(target)
            );
            synchronized (CACHE_LOCK) {
               if (cacheGeneration != generation) {
                  return damageTo(target, explosionPos, power, opts);
               }

               Double cached = CACHE.get(key);
               if (cached != null) {
                  return cached;
               }
            }

            double damage = damageTo(target, explosionPos, power, opts);
            boolean moved = stateMoved(key, target);
            synchronized (CACHE_LOCK) {
               if (cacheGeneration != generation) {
                  return damage;
               } else if (cacheLevel.get() == level && cacheTick == currentTick(mc)) {
                  if (!moved) {
                     CACHE.put(key, damage);
                  }

                  return damage;
               } else {
                  return damage;
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   private static int currentTick(Minecraft mc) {
      return mc.player == null ? Integer.MIN_VALUE : RiptideSharedState.get().getClientTickCounter();
   }

   private static void rearmLocked(Level level, int tick) {
      CACHE.clear();
      cacheGeneration = GENERATION.incrementAndGet();
      cacheLevel = new WeakReference<>(level);
      cacheTick = tick;
   }

   public static double cachedSelfDamage(Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      return cachedDamageTo(Minecraft.getInstance().player, explosionPos, power, options);
   }

   public static RiptideExplosionDamage.Ranking cachedRank(LivingEntity target, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options) {
      double targetDamage = cachedDamageTo(target, explosionPos, power, options);
      LocalPlayer player = Minecraft.getInstance().player;
      double self = target == player ? targetDamage : cachedDamageTo(player, explosionPos, power, options);
      return new RiptideExplosionDamage.Ranking(targetDamage, self);
   }

   public static void invalidateCache() {
      synchronized (CACHE_LOCK) {
         CACHE.clear();
         long generation = GENERATION.incrementAndGet();
         if (cachePassId != -1L) {
            cacheGeneration = generation;
         }
      }
   }

   private static boolean stateMoved(RiptideExplosionDamage.CacheKey key, LivingEntity target) {
      return !key.targetBox().equals(target.getBoundingBox()) ? true : key.fingerprint() != stateFingerprint(target);
   }

   private static long stateFingerprint(LivingEntity target) {
      long hash = 17L;
      hash = hash * 31L + (target.isRemoved() ? 1L : 0L);
      hash = hash * 31L + (target.isDeadOrDying() ? 1L : 0L);
      hash = hash * 31L + (target.isSpectator() ? 1L : 0L);
      hash = hash * 31L + (target.isInvulnerable() ? 1L : 0L);
      if (target instanceof Player player) {
         hash = hash * 31L + (player.getAbilities().invulnerable ? 1L : 0L);
      }

      Level level = target.level();
      hash = hash * 31L + (level == null ? -1L : level.getDifficulty().ordinal());
      hash = hash * 31L + target.getArmorValue();
      hash = hash * 31L + Double.doubleToLongBits(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
      MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
      hash = hash * 31L + (resistance == null ? -1L : resistance.getAmplifier());
      hash = hash * 31L + (target.hasEffect(MobEffects.FIRE_RESISTANCE) ? 1L : 0L);
      hash = hash * 31L + Float.floatToIntBits(target.getYHeadRot());
      hash = hash * 31L + stackFingerprint(target.getItemBlockingWith());

      for (EquipmentSlot slot : ARMOR_SLOTS) {
         hash = hash * 31L + stackFingerprint(target.getItemBySlot(slot));
      }

      return hash;
   }

   private static long stackFingerprint(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         long hash = stack.getItem().hashCode();
         hash = hash * 31L + stack.getDamageValue();
         ItemEnchantments enchantments = stack.getEnchantments();
         return hash * 31L + (enchantments == null ? 0L : enchantments.hashCode());
      } else {
         return 0L;
      }
   }

   public static float exposure(LivingEntity target, Vec3 explosionPos, RiptideExplosionDamage.Options options) {
      if (target != null && explosionPos != null) {
         Level level = target.level();
         if (level == null) {
            return 0.0F;
         } else {
            RiptideExplosionDamage.Options opts = options == null ? RiptideExplosionDamage.Options.DEFAULT : options;
            if (!opts.needsTweakedRays()) {
               return ServerExplosion.getSeenPercent(explosionPos, target);
            } else {
               AABB box = opts.overrideBox() != null ? opts.overrideBox() : target.getBoundingBox();
               CollisionContext shapeContext = opts.overrideBox() != null ? CollisionContext.withPosition(target, box.minY) : CollisionContext.of(target);
               double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
               double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
               double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
               if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
                  double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
                  double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
                  int hits = 0;
                  int totalRays = 0;

                  for (double x = 0.0; x <= 1.0; x += stepX) {
                     for (double y = 0.0; y <= 1.0; y += stepY) {
                        for (double z = 0.0; z <= 1.0; z += stepZ) {
                           Vec3 sample = new Vec3(
                              Mth.lerp(x, box.minX, box.maxX) + offsetX, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + offsetZ
                           );
                           ClipContext clip = new ClipContext(sample, explosionPos, Block.COLLIDER, Fluid.NONE, shapeContext);
                           if (raycast(level, clip, opts).getType() == Type.MISS) {
                              hits++;
                           }

                           totalRays++;
                        }
                     }
                  }

                  return (float)hits / totalRays;
               } else {
                  return 0.0F;
               }
            }
         }
      } else {
         return 0.0F;
      }
   }

   private static BlockHitResult raycast(Level level, ClipContext context, RiptideExplosionDamage.Options options) {
      List<BlockPos> exclude = options.exclude();
      BlockPos include = options.include();
      Float maxBlastResistance = options.maxBlastResistance();
      return (BlockHitResult)BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
         boolean excluded = !exclude.isEmpty() && exclude.contains(pos);
         BlockState blockState;
         if (excluded) {
            blockState = Blocks.VOID_AIR.defaultBlockState();
         } else if (pos.equals(include)) {
            blockState = Blocks.OBSIDIAN.defaultBlockState();
         } else {
            blockState = level.getBlockState(pos);
            if (maxBlastResistance != null && blockState.getBlock().getExplosionResistance() < maxBlastResistance) {
               blockState = Blocks.VOID_AIR.defaultBlockState();
            }
         }

         FluidState fluidState;
         if (excluded) {
            fluidState = Fluids.EMPTY.defaultFluidState();
         } else {
            fluidState = level.getFluidState(pos);
            if (maxBlastResistance != null && fluidState.getExplosionResistance() < maxBlastResistance) {
               fluidState = Fluids.EMPTY.defaultFluidState();
            }
         }

         Vec3 from = ctx.getFrom();
         Vec3 to = ctx.getTo();
         BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, pos, ctx.getBlockShape(blockState, level, pos), blockState);
         BlockHitResult fluidHit = ctx.getFluidShape(fluidState, level, pos).clip(from, to, pos);
         double blockDistance = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
         double fluidDistance = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
         return blockDistance <= fluidDistance ? blockHit : fluidHit;
      }, ctx -> {
         Vec3 delta = ctx.getFrom().subtract(ctx.getTo());
         return BlockHitResult.miss(ctx.getTo(), Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(ctx.getTo()));
      });
   }

   public static double effectiveDamage(LivingEntity target, DamageSource source, float damage, boolean estimateProtection) {
      if (target != null && source != null) {
         if (((RiptideEntityAccessor)target).riptide$isInvulnerableToBase(source)) {
            return 0.0;
         } else if (target.isDeadOrDying()) {
            return 0.0;
         } else {
            float amount = damage;
            if (target instanceof Player player) {
               if (player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                  return 0.0;
               }

               if (source.scalesWithDifficulty()) {
                  Difficulty difficulty = target.level().getDifficulty();
                  if (difficulty == Difficulty.PEACEFUL) {
                     amount = 0.0F;
                  } else if (difficulty == Difficulty.EASY) {
                     amount = Math.min(damage / 2.0F + 1.0F, damage);
                  } else if (difficulty == Difficulty.HARD) {
                     amount = damage * 3.0F / 2.0F;
                  }
               }
            }

            if (amount <= 0.0F) {
               return 0.0;
            } else if (source.is(DamageTypeTags.IS_FIRE) && target.hasEffect(MobEffects.FIRE_RESISTANCE)) {
               return 0.0;
            } else {
               amount -= blockedDamage(target, source, amount);
               if (amount <= 0.0F) {
                  return 0.0;
               } else {
                  RiptideLivingEntityAccessor accessor = (RiptideLivingEntityAccessor)target;
                  amount = accessor.riptide$getDamageAfterArmorAbsorb(source, amount);
                  amount = accessor.riptide$getDamageAfterMagicAbsorb(source, amount);
                  if (estimateProtection) {
                     amount = afterProtection(target, source, amount);
                  }

                  return Math.max(amount, 0.0F);
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   private static float blockedDamage(LivingEntity target, DamageSource source, float amount) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else {
         ItemStack blocking = target.getItemBlockingWith();
         if (blocking == null) {
            return 0.0F;
         } else {
            BlocksAttacks blocksAttacks = (BlocksAttacks)blocking.get(DataComponents.BLOCKS_ATTACKS);
            if (blocksAttacks == null) {
               return 0.0F;
            } else if (blocksAttacks.bypassedBy().isPresent() && ((HolderSet)blocksAttacks.bypassedBy().get()).contains(source.typeHolder())) {
               return 0.0F;
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
               return 0.0F;
            } else {
               double horizontalAngle = Math.PI;
               Vec3 sourcePosition = source.getSourcePosition();
               if (sourcePosition != null) {
                  float yaw = target.getYHeadRot() * (float) (Math.PI / 180.0);
                  Vec3 view = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
                  Vec3 direction = sourcePosition.subtract(target.position());
                  direction = new Vec3(direction.x, 0.0, direction.z).normalize();
                  horizontalAngle = Math.acos(direction.dot(view));
               }

               return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
            }
         }
      }
   }

   private static float afterProtection(LivingEntity target, DamageSource source, float amount) {
      if (amount <= 0.0F) {
         return amount;
      } else if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
         return amount;
      } else if (target.level() instanceof ServerLevel) {
         return amount;
      } else {
         float points = protectionPoints(target, source);
         return points <= 0.0F ? amount : amount * (1.0F - Mth.clamp(points, 0.0F, 20.0F) / 25.0F);
      }
   }

   private static float protectionPoints(LivingEntity target, DamageSource source) {
      boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
      boolean generic = !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
      if (!explosion && !generic) {
         return 0.0F;
      } else {
         float points = 0.0F;

         for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            if (!stack.isEmpty()) {
               ItemEnchantments enchantments = stack.getEnchantments();
               if (!enchantments.isEmpty()) {
                  for (Holder<Enchantment> holder : enchantments.keySet()) {
                     int level = enchantments.getLevel(holder);
                     if (level > 0) {
                        if (generic && holder.is(Enchantments.PROTECTION)) {
                           points += level;
                        }

                        if (explosion && holder.is(Enchantments.BLAST_PROTECTION)) {
                           points += level * 2.0F;
                        }
                     }
                  }
               }
            }
         }

         return points;
      }
   }

   public static DamageSource explosionSource(Level level, Vec3 explosionPos) {
      return new DamageSource(level.damageSources().explosion((Explosion)null).typeHolder(), explosionPos);
   }

   public static DamageSource badRespawnPointSource(Level level, Vec3 explosionPos) {
      return level.damageSources().badRespawnPointExplosion(explosionPos);
   }

   private static boolean isExplosionImmune(LivingEntity target) {
      if (!target.isRemoved() && !target.isDeadOrDying() && !target.isSpectator()) {
         return target instanceof Player player && player.getAbilities().invulnerable ? true : target.isInvulnerable();
      } else {
         return true;
      }
   }

   private record CacheKey(LivingEntity target, AABB targetBox, Vec3 explosionPos, float power, RiptideExplosionDamage.Options options, long fingerprint) {
   }

   public record Lethality(boolean killsTarget, boolean killsSelf, double targetDamage, double selfDamage) {
      public boolean lethalOverride() {
         return this.killsTarget && !this.killsSelf;
      }
   }

   public record Options(
      List<BlockPos> exclude, BlockPos include, Float maxBlastResistance, AABB overrideBox, DamageSource damageSource, boolean estimateProtection
   ) {
      public static final RiptideExplosionDamage.Options DEFAULT = new RiptideExplosionDamage.Options(List.of(), null, null, null, null, true);

      public Options(
         List<BlockPos> exclude, BlockPos include, Float maxBlastResistance, AABB overrideBox, DamageSource damageSource, boolean estimateProtection
      ) {
         exclude = exclude == null ? List.of() : List.copyOf(exclude);
         this.exclude = exclude;
         this.include = include;
         this.maxBlastResistance = maxBlastResistance;
         this.overrideBox = overrideBox;
         this.damageSource = damageSource;
         this.estimateProtection = estimateProtection;
      }

      public RiptideExplosionDamage.Options withExclude(List<BlockPos> positions) {
         return new RiptideExplosionDamage.Options(
            positions, this.include, this.maxBlastResistance, this.overrideBox, this.damageSource, this.estimateProtection
         );
      }

      public RiptideExplosionDamage.Options withInclude(BlockPos pos) {
         return new RiptideExplosionDamage.Options(this.exclude, pos, this.maxBlastResistance, this.overrideBox, this.damageSource, this.estimateProtection);
      }

      public RiptideExplosionDamage.Options withMaxBlastResistance(Float resistance) {
         return new RiptideExplosionDamage.Options(this.exclude, this.include, resistance, this.overrideBox, this.damageSource, this.estimateProtection);
      }

      public RiptideExplosionDamage.Options withTerrain(boolean terrain) {
         return this.withMaxBlastResistance(terrain ? 9.0F : null);
      }

      public RiptideExplosionDamage.Options withOverrideBox(AABB box) {
         return new RiptideExplosionDamage.Options(this.exclude, this.include, this.maxBlastResistance, box, this.damageSource, this.estimateProtection);
      }

      public RiptideExplosionDamage.Options withDamageSource(DamageSource source) {
         return new RiptideExplosionDamage.Options(this.exclude, this.include, this.maxBlastResistance, this.overrideBox, source, this.estimateProtection);
      }

      public RiptideExplosionDamage.Options withEstimateProtection(boolean estimate) {
         return new RiptideExplosionDamage.Options(this.exclude, this.include, this.maxBlastResistance, this.overrideBox, this.damageSource, estimate);
      }

      public boolean needsTweakedRays() {
         return !this.exclude.isEmpty() || this.include != null || this.maxBlastResistance != null || this.overrideBox != null;
      }
   }

   public record Ranking(double targetDamage, double selfDamage) {
      public double margin() {
         return this.targetDamage - this.selfDamage;
      }

      public boolean isEfficient() {
         return this.targetDamage > this.selfDamage;
      }

      public boolean passes(double minTargetDamage, double maxSelfDamage) {
         return this.targetDamage >= minTargetDamage && this.selfDamage <= maxSelfDamage;
      }
   }

   public static final class ScanPass implements AutoCloseable {
      private final long id;

      private ScanPass(long id) {
         this.id = id;
      }

      @Override
      public void close() {
         RiptideExplosionDamage.endScan(this.id);
      }
   }
}
