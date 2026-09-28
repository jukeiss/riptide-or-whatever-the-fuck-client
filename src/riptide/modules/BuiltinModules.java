package riptide.modules;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import riptide.api.module.ActionSetting;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.PacketListSetting;
import riptide.api.module.RangeSetting;
import riptide.api.module.RegistryListSetting;
import riptide.api.module.Setting;
import riptide.api.module.StringListSetting;
import riptide.api.module.StringSetting;
import riptide.api.module.ValueRange;
import riptide.commands.RiptideCommands;
import riptide.gui.screen.RiptideHudEditorScreen;
import riptide.gui.screen.RiptideTitleScreen;
import riptide.mixin.accessor.RiptideFishingHookAccessor;
import riptide.mixin.accessor.RiptideLocalPlayerAccessor;
import riptide.mixin.accessor.RiptideMobEffectInstanceAccessor;
import riptide.mixin.accessor.RiptideMovePlayerPacketAccessor;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.security.RiptideItemNbtSanity;
import riptide.util.AutoFishStopMacroFactory;
import riptide.util.PacketListCodec;
import riptide.util.QuantizedRotationSmoother;
import riptide.util.RegistryListCodec;
import riptide.util.RiptideAutoTool;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideBookFileReader;
import riptide.util.RiptideBookPayloadBuilder;
import riptide.util.RiptideClickPacer;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideFakeCoords;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideHumanRng;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideInputGate;
import riptide.util.RiptideInstaBreakRenderer;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideInventoryMoveHelper;
import riptide.util.RiptideItemCommandSerializer;
import riptide.util.RiptideItemNbtInspector;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideMouseInputSimulator;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePlacementTick;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideSilentAim;
import riptide.util.RiptideWaypoints;
import riptide.util.RiptideWindowBranding;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;
import riptide.util.macro.MacroConditionUtil;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.NbtBookAction;
import riptide.util.macro.ServerTickTracker;
import riptide.util.macro.WaitDurabilityAction;
import riptide.util.macro.WaitFreeSlotsAction;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.PacketTeleportController;
import riptide.util.oresim.RiptideOreGhostModels;
import riptide.util.oresim.RiptideOreSimEngine;
import riptide.util.oresim.RiptideOreSimOre;
import riptide.util.oresim.RiptideOreSimSeedStore;

public final class BuiltinModules {
   private static BuiltinModules.FastUseModule fastUseInstance;

   private BuiltinModules() {
   }

   static void register() {
      migrateUtilityModuleState("inv-move", RiptideConfig.getGlobal().inventoryMove);
      migrateUtilityModuleState("xcarry", RiptideConfig.getGlobal().xCarry);
      migrateUtilityModuleState("golden-lever", true);
      if (RiptideLiteVariant.enabled()) {
         ModuleRegistry.register(new BuiltinModules.HideModule());
      } else {
         ModuleRegistry.register(new BuiltinModules.FlightModule());
         ModuleRegistry.register(new BuiltinModules.SprintModule());
         ModuleRegistry.register(new BuiltinModules.ParkourModule());
         ModuleRegistry.register(new BuiltinModules.SpeedModule());
         ModuleRegistry.register(new BoatFlyModule());
         ModuleRegistry.register(new EntityControlModule());
         ModuleRegistry.register(new BuiltinModules.InvMoveModule());
         ModuleRegistry.register(new BuiltinModules.SneakModule());
         ModuleRegistry.register(new AirJumpModule());
         ModuleRegistry.register(new NoClipModule());
         ModuleRegistry.register(new PhaseModule());
         ModuleRegistry.register(new WaypointsModule());
         ModuleRegistry.register(new BuiltinModules.FastUseModule());
         ModuleRegistry.register(new BuiltinModules.FastBreakModule());
         ModuleRegistry.register(new BuiltinModules.AimAssistModule());
         ModuleRegistry.register(new BuiltinModules.AutoClickerModule());
         ModuleRegistry.register(new BuiltinModules.TriggerBotModule());
         ModuleRegistry.register(new AutoTotemModule());
         ModuleRegistry.register(new MacroRecorderModule());
         ModuleRegistry.register(new SpearSwapModule());
         ModuleRegistry.register(new InventoryTotemModule());
         ModuleRegistry.register(new BedDefenderModule());
         ModuleRegistry.register(new SurroundModule());
         ModuleRegistry.register(new AnchorAuraModule());
         ModuleRegistry.register(new CrystalAuraModule());
         ModuleRegistry.register(new AutoTrapModule());
         ModuleRegistry.register(new KillAuraModule());
         ModuleRegistry.register(new MaceSlamModule());
         ModuleRegistry.register(new AutoFarmModule());
         ModuleRegistry.register(new AutoArmorModule());
         ModuleRegistry.register(new AutoSignModule());
         ModuleRegistry.register(new AutoLoginModule());
         ModuleRegistry.register(new TpClickModule());
         migrateUtilityModuleState("antibot", true);
         ModuleRegistry.register(new AntiBotModule());
         ModuleRegistry.register(new AntiVanishModule());
         ModuleRegistry.register(new BuiltinModules.AutoFishModule());
         ModuleRegistry.register(new BuiltinModules.InstantRebreakModule());
         ModuleRegistry.register(new BuiltinModules.AutoToolModule());
         ModuleRegistry.register(new ScaffoldModule());
         ModuleRegistry.register(new BuiltinModules.NoFallModule());
         ModuleRegistry.register(new BuiltinModules.NoInteractModule());
         ModuleRegistry.register(new AirPlaceModule());
         ModuleRegistry.register(new BuiltinModules.OffhandInteractModule());
         ModuleRegistry.register(new BuiltinModules.SpamModule());
         ModuleRegistry.register(new PayAllModule());
         ModuleRegistry.register(new BuiltinModules.BookBotModule());
         ModuleRegistry.register(new BuiltinModules.PacketCancellerModule());
         ModuleRegistry.register(new BuiltinModules.AutoReconnectModule());
         ModuleRegistry.register(new BuiltinModules.HideModule());
         ModuleRegistry.register(new InventoryTweaksModule());
         ModuleRegistry.register(new AntiHungerModule());
         ModuleRegistry.register(new SafeWalkModule());
         ModuleRegistry.register(new BuiltinModules.SpawnerEspModule());
         ModuleRegistry.register(new BuiltinModules.BetterTooltipsModule());
         ModuleRegistry.register(new BuiltinModules.FullbrightModule());
         ModuleRegistry.register(new BuiltinModules.FakeCoordsModule());
         ModuleRegistry.register(new BuiltinModules.XrayModule());
         ModuleRegistry.register(new WorldModule());
         ModuleRegistry.register(new ViewmodelModule());
         ModuleRegistry.register(new BuiltinModules.FreecamModule());
         ModuleRegistry.register(new FreeLookModule());
         ModuleRegistry.register(new BuiltinModules.EspModule());
         ModuleRegistry.register(new ChamsModule());
         ModuleRegistry.register(new BuiltinModules.ItemEspModule());
         ModuleRegistry.register(new NameTagsModule());
         ModuleRegistry.register(new BuiltinModules.TracersModule());
         ModuleRegistry.register(new TrajectoriesModule());
         ModuleRegistry.register(new BuiltinModules.StorageEspModule());
         ModuleRegistry.register(new BuiltinModules.BlockEspModule());
         ModuleRegistry.register(new HoleEspModule());
         ModuleRegistry.register(new SusChunkFinderModule());
         ModuleRegistry.register(new LootEspModule());
         ModuleRegistry.register(new WatermarkModule());
         ModuleRegistry.register(new PvpCountHudModule());
         ModuleRegistry.register(new RadarHudModule());
         ModuleRegistry.register(new TargetHudModule());
         ModuleRegistry.register(new InfoHudModule());
         ModuleRegistry.register(new ArrayListHudModule());
         ModuleRegistry.register(new KeystrokesHudModule());
         ModuleRegistry.register(new CpsHudModule());
         ModuleRegistry.register(new PotionHudModule());
         ModuleRegistry.register(new ArmorHudModule());
         ModuleRegistry.register(new MobEspModule());
         ModuleRegistry.register(new HitboxModule());
         ModuleRegistry.register(new CoordSnapperModule());
         ModuleRegistry.register(new WeatherNotifierModule());
         ModuleRegistry.register(new StaffListModule());
         ModuleRegistry.register(new CustomFovModule());
         ModuleRegistry.register(new HitParticlesModule());
         ModuleRegistry.register(new ArmorTrimHiderModule());
         ModuleRegistry.register(new CustomGlintModule());
         ModuleRegistry.register(new FakePayModule());
         ModuleRegistry.register(new SpawnerProtectModule());
         ModuleRegistry.register(new RegionMapModule());
         ModuleRegistry.register(new PlayerArmorEspModule());
         ModuleRegistry.register(new FreeLookZoomModule());
         ModuleRegistry.register(new StatNametagsModule());
         ModuleRegistry.register(new PanicHotkeyModule());
         ModuleRegistry.register(new SlowPickaxeModule());
         ModuleRegistry.register(new ChunkReloaderModule());
         ModuleRegistry.register(new SpawnerFinderModule());
         ModuleRegistry.register(new SpawnerNametagsModule());
         ModuleRegistry.register(new BedrockHolesModule());
         ModuleRegistry.register(new JumpCirclesModule());
         ModuleRegistry.register(new AntiAfkModule());
         ModuleRegistry.register(new AutoAnvilModule());
         ModuleRegistry.register(new AutoFireworkModule());
         ModuleRegistry.register(new AutoLeaveModule());
         ModuleRegistry.register(new AutoMendModule());
         ModuleRegistry.register(new AutoRefillModule());
         ModuleRegistry.register(new AutoResponderModule());
         ModuleRegistry.register(new AutoShieldModule());
         ModuleRegistry.register(new AutoTpAcceptModule());
         ModuleRegistry.register(new AutoWeaponModule());
         ModuleRegistry.register(new BreakAlertModule());
         ModuleRegistry.register(new ChatFilterModule());
         ModuleRegistry.register(new ChatTimestampsModule());
         ModuleRegistry.register(new ChestStealerModule());
         ModuleRegistry.register(new CombatLogModule());
         ModuleRegistry.register(new CoordLoggerModule());
         ModuleRegistry.register(new CrosshairModule());
         ModuleRegistry.register(new DeathLogModule());
         ModuleRegistry.register(new ElytraSwapModule());
         ModuleRegistry.register(new HudCleanerModule());
         ModuleRegistry.register(new InventoryCleanerModule());
         ModuleRegistry.register(new ItemSaverModule());
         ModuleRegistry.register(new KillSayModule());
         ModuleRegistry.register(new NetWorthModule());
         ModuleRegistry.register(new NoFogModule());
         ModuleRegistry.register(new ParticleFilterModule());
         ModuleRegistry.register(new PearlTrackerModule());
         ModuleRegistry.register(new PlayerLogModule());
         ModuleRegistry.register(new PotionAlertModule());
         ModuleRegistry.register(new QuickThrowModule());
         ModuleRegistry.register(new ScreenEffectsModule());
         ModuleRegistry.register(new ShopAlertModule());
         ModuleRegistry.register(new SoundAlertModule());
         ModuleRegistry.register(new SoundControlModule());
         ModuleRegistry.register(new StatTrackerModule());
         ModuleRegistry.register(new TotemPopsModule());
         ModuleRegistry.register(new TrapAlertModule());
         ModuleRegistry.register(new VelocityModule());
         ModuleRegistry.register(new VisualRangeModule());
         ModuleRegistry.register(new WaypointShareModule());
         ModuleRegistry.register(new WeatherControlModule());
         ModuleRegistry.register(new XpTrackerModule());
         ModuleRegistry.register(new ZoomModule());
         ModuleRegistry.register(new AmethystGeodeFinderModule());
         ModuleRegistry.register(new NoRenderModule());
         ModuleRegistry.register(new BuiltinModules.XCarryModule());
         ModuleRegistry.register(new BuiltinModules.HudModule());
         ModuleRegistry.register(new SpotifyModule());
         ModuleRegistry.register(new SpotifyControlsModule());
         ModuleRegistry.register(new AutoSellModule());
         ModuleRegistry.register(new FakeScoreboardModule());
         ModuleRegistry.register(new RtpBaseFinderModule());
         ModuleRegistry.register(new SeedChunkFinderModule());
         ModuleRegistry.register(new BlockEspStylesModule());
         ModuleRegistry.register(new AutoRespawnModule());
         ModuleRegistry.register(new AutoEatModule());
         ModuleRegistry.register(new AutoWalkModule());
         ModuleRegistry.register(new BuiltinModules.AdminToolsModule());
         ModuleRegistry.register(new GoldenLeverModule());
         ModuleRegistry.register(new NameCensorModule());
         migrateUtilityModuleState("teams", true);
         ModuleRegistry.register(new TeamsModule());
         ModuleRegistry.register(new PingSpoofModule());
         ModuleRegistry.register(new BlinkModule());
      }
   }

   private static void migrateUtilityModuleState(String var0, boolean var1) {
      RiptideConfig var2 = RiptideConfig.getGlobal();
      if (!var2.modules.containsKey(var0)) {
         RiptideConfig.ModuleState var3 = var2.modules.computeIfAbsent(var0, var0x -> new RiptideConfig.ModuleState());
         var3.enabled = var1;
      }
   }

   public static boolean ownsManualFastUse() {
      BuiltinModules.FastUseModule var0 = activeFastUse();
      return var0 != null && var0.ownsManualInput();
   }

   public static boolean ownsManualFastExp() {
      BuiltinModules.FastUseModule var0 = activeFastUse();
      return var0 != null && var0.manualExpActive;
   }

   public static boolean beginManualFastUseClick() {
      BuiltinModules.FastUseModule var0 = activeFastUse();
      return var0 != null && var0.beginManualInputClick();
   }

   public static float manualFastExpUseYaw(float var0) {
      if (!RiptideInputClicker.isFastExpUseInProgress()) {
         return var0;
      } else {
         BuiltinModules.FastUseModule var1 = activeFastUse();
         RiptideRotationUtil.Rotation var2 = var1 == null ? null : var1.activeExpRotation();
         return var2 == null ? var0 : var2.yaw();
      }
   }

   public static float manualFastExpUsePitch(float var0) {
      if (!RiptideInputClicker.isFastExpUseInProgress()) {
         return var0;
      } else {
         BuiltinModules.FastUseModule var1 = activeFastUse();
         RiptideRotationUtil.Rotation var2 = var1 == null ? null : var1.activeExpRotation();
         return var2 == null ? var0 : var2.pitch();
      }
   }

   public static float outgoingFastExpMovementYaw(LocalPlayer var0, float var1) {
      RiptideRotationUtil.Rotation var2 = activeFastExpRotation(var0);
      return var2 == null ? var1 : var2.yaw();
   }

   public static float outgoingFastExpMovementPitch(LocalPlayer var0, float var1) {
      RiptideRotationUtil.Rotation var2 = activeFastExpRotation(var0);
      return var2 == null ? var1 : var2.pitch();
   }

   private static RiptideRotationUtil.Rotation activeFastExpRotation(LocalPlayer var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var0 != null && var1 != null && var0 == var1.player) {
         BuiltinModules.FastUseModule var2 = activeFastUse();
         return var2 == null ? null : var2.activeExpRotation();
      } else {
         return null;
      }
   }

   private static BuiltinModules.FastUseModule activeFastUse() {
      BuiltinModules.FastUseModule var0 = fastUseInstance;
      return var0 != null && var0.isEnabled() ? var0 : null;
   }

   public static final class AdminToolsModule extends Module {
      private static final List<String> ITEM_EDITOR_SIGNATURE_FIELDS = List.of(
         "nbt-item-id",
         "nbt-item-count",
         "nbt-item-name",
         "nbt-item-lore",
         "nbt-unbreakable",
         "nbt-glint",
         "nbt-rarity",
         "nbt-max-damage",
         "nbt-damage",
         "nbt-max-stack",
         "nbt-enchants",
         "nbt-attributes",
         "nbt-custom-data",
         "nbt-command"
      );
      private final List<String> queuedCommands = new ArrayList<>();
      private int commandDelay;
      private int commandIndex;
      private String queuedDelayOption = "fireball-delay";
      private final Random random = new Random();
      private boolean fireballStreamEnabled;
      private boolean firestormEnabled;
      private boolean fireballStreamBindDown;
      private boolean firestormBindDown;
      private int liveFireballDelay;
      private int liveFirestormDelay;
      private int liveFirestormIndex;
      private long suppressSummonMessagesUntilMs;
      private boolean forceOpRunning;
      private Thread forceOpThread;
      private int forceOpIndex;
      private volatile boolean gotWrongPwMsg;
      private static final String[] RAW_FORCEOP_LIST = new String[]{
         "password",
         "passwort",
         "password1",
         "passwort1",
         "password123",
         "password1234",
         "passwort123",
         "pass",
         "pw",
         "pw1",
         "pw123",
         "hallo",
         "1122",
         "112233",
         "1234",
         "12345",
         "123456",
         "1234567",
         "12345678",
         "123456789",
         "login",
         "register",
         "test",
         "sicher",
         "me",
         "minecraft",
         "minecraft1",
         "minecraft123",
         "mc",
         "admin",
         "server",
         "tester",
         "account",
         "creeper",
         "gronkh",
         "lol",
         "auth",
         "authme",
         "qwerty",
         "qwertz",
         "2112",
         "1212",
         "cocacola",
         "xavier",
         "dolphin",
         "testing",
         "dragon",
         "baseball",
         "football",
         "letmein",
         "monkey",
         "696969",
         "abc123",
         "mustang",
         "michael",
         "shadow",
         "master",
         "jennifer",
         "111111",
         "2000",
         "jordan",
         "superman",
         "harley",
         "hunter",
         "trustno1",
         "ranger",
         "buster",
         "thomas",
         "tigger",
         "robert",
         "soccer",
         "batman",
         "killer",
         "hockey",
         "george",
         "charlie",
         "andrew",
         "michelle",
         "love",
         "sunshine",
         "jessica",
         "6969",
         "pepper",
         "daniel",
         "access",
         "654321",
         "joshua",
         "maggie",
         "starwars",
         "silver",
         "william",
         "dallas",
         "yankees",
         "123123",
         "ashley",
         "666666",
         "hello",
         "amanda",
         "orange",
         "freedom",
         "computer",
         "thunder",
         "nicole",
         "ginger",
         "heather",
         "hammer",
         "summer",
         "corvette",
         "taylor",
         "austin",
         "1111",
         "merlin",
         "matthew",
         "121212",
         "golfer",
         "cheese",
         "princess",
         "martin",
         "chelsea",
         "patrick",
         "richard",
         "diamond",
         "yellow",
         "bigdog",
         "secret",
         "asdfgh",
         "sparky",
         "cowboy",
         "camaro",
         "anthony",
         "matrix",
         "falcon",
         "iloveyou",
         "bailey",
         "guitar",
         "jackson",
         "purple",
         "scooter",
         "phoenix",
         "aaaaaa",
         "morgan",
         "tigers",
         "porsche",
         "mickey",
         "maverick",
         "cookie",
         "nascar",
         "peanut",
         "justin",
         "131313",
         "money",
         "123456",
         "password",
         "12345678",
         "qwerty",
         "123456789",
         "12345",
         "1234",
         "111111",
         "1234567",
         "admin",
         "1234567890",
         "123123",
         "000000",
         "123321",
         "654321",
         "qwertyuiop",
         "qwertzuiop",
         "1q2w3e4r5t",
         "1qaz2wsx",
         "zxcvbnm",
         "asdfgh",
         "7777777",
         "666666",
         "555555",
         "888888",
         "123qwe",
         "abc123",
         "aa123456",
         "password123",
         "password1",
         "secret"
      };
      private final String[] defaultForceOpList = Arrays.stream(RAW_FORCEOP_LIST).distinct().toArray(String[]::new);
      private String[] forceOpPasswords = this.defaultForceOpList;

      AdminToolsModule() {
         super("admin-tools", "AdminTools", ModuleCategory.MISC, "Creative/admin helpers.");
         this.add(new ActionSetting("admin-status", "Show Permission Status", this::showPermissionStatus).group("Status").build());
         this.add(new ActionSetting("admin-stop-queue", "Stop Queued Commands", this::stopQueuedCommands).group("Status").build());
         this.add(new IntSetting("fireball-count", "Count", 12, 1, 256, 1).group("Fireball Storm").build());
         this.add(new DoubleSetting("fireball-spread", "Spread", 8.0, 0.0, 90.0, 1.0).group("Fireball Storm").build());
         this.add(new DoubleSetting("fireball-speed", "Speed", 1.8, 0.1, 10.0, 0.1).group("Fireball Storm").build());
         this.add(new IntSetting("fireball-power", "Power", 3, 0, 127, 1).group("Fireball Storm").build());
         this.add(new IntSetting("fireball-delay", "Delay", 1, 0, 20, 1).group("Fireball Storm").build());
         this.add(new DoubleSetting("fireball-distance", "Spawn Dist", 2.0, 0.5, 16.0, 0.5).group("Fireball Storm").build());
         this.add(new ChoiceSetting("fireball-aim", "Aim", "Cone", "Look", "Cone", "Ring").group("Fireball Storm").build());
         this.add(new BoolSetting("fireball-randomize", "Randomize", true).group("Fireball Storm").build());
         this.add(new KeybindSetting("fireball-stream-bind", "Stream Bind", -1).group("Fireball Storm").build());
         this.add(new KeybindSetting("firestorm-bind", "Firestorm Bind", -1).group("Fireball Storm").build());
         this.add(new DoubleSetting("firestorm-distance", "Firestorm Dist", 8.0, 2.0, 48.0, 1.0).group("Fireball Storm").build());
         this.add(new DoubleSetting("firestorm-height", "Firestorm Height", 2.0, 0.0, 32.0, 1.0).group("Fireball Storm").build());
         this.add(new BoolSetting("fireball-stream-active", "Stream Active", false).visibleWhen(() -> false).build());
         this.add(new BoolSetting("firestorm-active", "Firestorm Active", false).visibleWhen(() -> false).build());
         this.add(new ActionSetting("fireball-preset-max", "Preset Max Storm", this::presetMaxFireballStorm).group("Fireball Storm").build());
         this.add(new ActionSetting("fireball-preview", "Preview Command", this::previewFireballCommand).group("Fireball Storm").build());
         this.add(new ActionSetting("fireball-copy", "Copy Command", this::copyFireballCommand).group("Fireball Storm").build());
         this.add(new ActionSetting("fireball-storm", "Run Storm", this::startFireballStorm).group("Fireball Storm").build());
         this.add(new IntSetting("airstrike-count", "Count", 24, 1, 256, 1).group("Airstrike").build());
         this.add(new DoubleSetting("airstrike-radius", "Radius", 12.0, 0.0, 96.0, 1.0).group("Airstrike").build());
         this.add(new DoubleSetting("airstrike-height", "Height", 48.0, 8.0, 256.0, 4.0).group("Airstrike").build());
         this.add(new DoubleSetting("airstrike-speed", "Fall Speed", 2.0, 0.1, 10.0, 0.1).group("Airstrike").build());
         this.add(new IntSetting("airstrike-power", "Power", 4, 0, 127, 1).group("Airstrike").build());
         this.add(new IntSetting("airstrike-delay", "Delay", 1, 0, 20, 1).group("Airstrike").build());
         this.add(new ChoiceSetting("airstrike-target", "Target", "Look", "Look", "Self").group("Airstrike").build());
         this.add(new ActionSetting("airstrike-preset-small", "Preset Tight Strike", this::presetTightAirstrike).group("Airstrike").build());
         this.add(new ActionSetting("airstrike-preset-max", "Preset Max Strike", this::presetMaxAirstrike).group("Airstrike").build());
         this.add(new ActionSetting("airstrike-run", "Run Airstrike", this::startAirstrike).group("Airstrike").build());
         this.add(new IntSetting("forceop-delay", "Delay (ms)", 500, 0, 5000, 50).group("ForceOP").build());
         this.add(new BoolSetting("forceop-wait", "Wait for Chat", true).group("ForceOP").build());
         this.add(new IntSetting("forceop-min-length", "Min Length", 0, 0, 32, 1).group("ForceOP").build());
         this.add(new IntSetting("forceop-max-length", "Max Length", 0, 0, 32, 1).group("ForceOP").build());
         this.add(new StringSetting("nbt-item-id", "Item ID", "minecraft:stick").group("NBT Item Editor").build());
         this.add(new IntSetting("nbt-item-count", "Count", 1, 1, 99, 1).group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-item-name", "Display Name", "Custom Item").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-item-lore", "Lore Lines", "").group("NBT Item Editor").build());
         this.add(new BoolSetting("nbt-unbreakable", "Unbreakable", false).group("NBT Item Editor").build());
         this.add(new ChoiceSetting("nbt-glint", "Glint", "Default", "Default", "On", "Off").group("NBT Item Editor").build());
         this.add(new ChoiceSetting("nbt-rarity", "Rarity", "Default", "Default", "Common", "Uncommon", "Rare", "Epic").group("NBT Item Editor").build());
         this.add(new IntSetting("nbt-max-damage", "Max Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
         this.add(new IntSetting("nbt-damage", "Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
         this.add(new IntSetting("nbt-max-stack", "Max Stack", 64, 1, 99, 1).group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-enchants", "Enchantments", "").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-attributes", "Attributes", "").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-custom-data", "Custom Data", "").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-command", "Embedded Command", "").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-item-components", "Raw Components", "").group("NBT Item Editor").build());
         this.add(new StringSetting("nbt-imported-components", "Imported Components", "").visibleWhen(() -> false).build());
         this.add(new StringSetting("nbt-imported-signature", "Imported Signature", "").visibleWhen(() -> false).build());
         this.add(new StringSetting("nbt-item-stack", "Full Item SNBT", "").visibleWhen(() -> false).build());
         this.add(new StringSetting("nbt-item-stack-signature", "Full Item Signature", "").visibleWhen(() -> false).build());
         this.add(new ActionSetting("fill-held-item", "Fill Held", this::fillHeldItemEditor).group("NBT Item Editor").build());
         this.add(new ActionSetting("copy-target-item", "Copy Target", this::copyTargetItem).group("NBT Item Editor").build());
         this.add(new ActionSetting("apply-held-item", "Apply to Held", this::applyHeldItemEditor).group("NBT Item Editor").build());
         this.add(new StringSetting("fireballPower", "Fireball Power Legacy", "3").visibleWhen(() -> false).build());
      }

      @Override
      public boolean opensSettingsOnClick() {
         return true;
      }

      @Override
      public boolean hasActivationToggle() {
         return false;
      }

      @Override
      public boolean showInModuleMenu() {
         return false;
      }

      @Override
      public boolean ticksWhenDisabled() {
         return true;
      }

      @Override
      public boolean hasDisabledTickWork() {
         boolean var1 = this.integer("fireball-stream-bind") != -1 || this.integer("firestorm-bind") != -1;
         return var1 || this.fireballStreamEnabled || this.firestormEnabled || !this.queuedCommands.isEmpty();
      }

      @Override
      protected String displayValueOverride(Setting<?, ?> var1) {
         return var1 != null && "nbt-item-components".equals(var1.id()) ? this.buildEditorComponents() : null;
      }

      @Override
      public void tick() {
         boolean var1 = this.integer("fireball-stream-bind") != -1 || this.integer("firestorm-bind") != -1;
         boolean var2 = this.fireballStreamEnabled || this.firestormEnabled;
         if (var1 || var2 || !this.queuedCommands.isEmpty()) {
            if (var1 || var2) {
               this.tickFireballBinds();
            }

            if (var2) {
               this.tickLiveFireballs();
            }

            if (!this.queuedCommands.isEmpty() && this.commandDelay-- <= 0) {
               if (!this.sendAdminCommand(this.queuedCommands.remove(0), true)) {
                  this.queuedCommands.clear();
                  this.commandDelay = 0;
                  this.commandIndex = 0;
               } else {
                  this.commandDelay = this.integer(this.queuedDelayOption);
                  this.commandIndex++;
               }
            }
         }
      }

      @Override
      public void onGameLeft() {
         this.fireballStreamEnabled = false;
         this.firestormEnabled = false;
         this.setValue("fireball-stream-active", "false");
         this.setValue("firestorm-active", "false");
         this.fireballStreamBindDown = false;
         this.firestormBindDown = false;
         this.queuedCommands.clear();
         this.commandDelay = 0;
         this.commandIndex = 0;
      }

      @Override
      public boolean onPacketReceive(Packet<?> var1) {
         if (var1 instanceof ClientboundSystemChatPacket var2) {
            String var3 = var2.content().getString();
            if (var3 == null) {
               return false;
            } else {
               String var4 = var3.toLowerCase(Locale.ROOT);
               if (this.forceOpRunning
                  && (
                     var4.contains("wrong")
                        || var4.contains("incorrect")
                        || var4.contains("falsch")
                        || var4.contains("mauvais")
                        || var4.contains("mal")
                        || var4.contains("sbagliato")
                  )) {
                  this.gotWrongPwMsg = true;
               }

               return System.currentTimeMillis() > this.suppressSummonMessagesUntilMs && !this.fireballStreamEnabled && !this.firestormEnabled
                  ? false
                  : var4.contains("fireball") || var4.contains("summoned");
            }
         } else {
            return false;
         }
      }

      private void showPermissionStatus() {
         RiptideClientMessaging.sendPrefixed(
            "Admin Tools: gamemaster=" + this.isAdminContext() + ", creative=" + this.isCreativeContext() + ", queued=" + this.queuedCommands.size() + "."
         );
      }

      private void stopQueuedCommands() {
         int var1 = this.queuedCommands.size();
         this.queuedCommands.clear();
         this.commandDelay = 0;
         this.commandIndex = 0;
         RiptideClientMessaging.sendPrefixed(var1 == 0 ? "Admin Tools: no queued commands." : "Admin Tools: stopped " + var1 + " queued commands.");
      }

      private void tickFireballBinds() {
         if (!RiptideInputGate.canRunRiptideKeybinds()) {
            this.fireballStreamBindDown = false;
         } else {
            boolean var1 = this.bindPressed("fireball-stream-bind");
            if (var1 && !this.fireballStreamBindDown) {
               this.toggleFireballStream();
            }

            this.fireballStreamBindDown = var1;
            boolean var2 = this.bindPressed("firestorm-bind");
            if (var2 && !this.firestormBindDown) {
               this.toggleFirestorm();
            }

            this.firestormBindDown = var2;
         }
      }

      private boolean bindPressed(String var1) {
         int var2 = this.integer(var1);
         return var2 != -1 && RiptideBindUtil.isBindPressed(MC, var2);
      }

      private void tickLiveFireballs() {
         if (MC.player != null && MC.getConnection() != null) {
            if (this.fireballStreamEnabled && this.liveFireballDelay-- <= 0) {
               this.sendLiveFireball(0, 1, false);
               this.liveFireballDelay = Math.max(0, this.integer("fireball-delay"));
            }

            if (this.firestormEnabled && this.liveFirestormDelay-- <= 0) {
               int var1 = Math.max(1, this.integer("fireball-count"));
               int var2 = Math.max(1, Math.min(8, var1));

               for (int var3 = 0; var3 < var2; var3++) {
                  this.sendLiveFireball(this.liveFirestormIndex++, var1, true);
               }

               this.liveFirestormDelay = Math.max(0, this.integer("fireball-delay"));
            }
         }
      }

      private void sendLiveFireball(int var1, int var2, boolean var3) {
         String var4 = var3 ? this.fireballCommand(var1, var2, this.firestormDistance(), this.firestormHeight()) : this.fireballCommand(var1, var2);
         if (!var4.isBlank()) {
            this.sendAdminCommand(var4, true, false);
         }
      }

      private void toggleFireballStream() {
         this.fireballStreamEnabled = !this.fireballStreamEnabled;
         this.setValue("fireball-stream-active", Boolean.toString(this.fireballStreamEnabled));
         this.liveFireballDelay = 0;
         RiptideClientMessaging.sendPrefixed("Admin Tools: fireball stream " + (this.fireballStreamEnabled ? "enabled." : "disabled."));
      }

      private void toggleFirestorm() {
         this.firestormEnabled = !this.firestormEnabled;
         this.setValue("firestorm-active", Boolean.toString(this.firestormEnabled));
         this.liveFirestormDelay = 0;
         this.liveFirestormIndex = 0;
         RiptideClientMessaging.sendPrefixed("Admin Tools: firestorm " + (this.firestormEnabled ? "enabled." : "disabled."));
      }

      public boolean isFireballStreamEnabled() {
         return this.fireballStreamEnabled;
      }

      public boolean isFirestormEnabled() {
         return this.firestormEnabled;
      }

      private void startFireballStorm() {
         this.queuedCommands.clear();
         this.commandIndex = 0;
         this.queuedDelayOption = "fireball-delay";
         int var1 = this.integer("fireball-count");

         for (int var2 = 0; var2 < var1; var2++) {
            String var3 = this.fireballCommand(var2, var1, this.firestormDistance(), this.firestormHeight());
            if (!var3.isBlank()) {
               this.queuedCommands.add(var3);
            }
         }

         if (this.queuedCommands.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not queue fireballs, join a world first.");
         } else {
            this.commandDelay = 0;
            RiptideClientMessaging.sendPrefixed("Admin Tools: queued " + var1 + " fireballs.");
         }
      }

      private void previewFireballCommand() {
         String var1 = this.fireballCommand(0, Math.max(1, this.integer("fireball-count")));
         RiptideClientMessaging.sendPrefixed(var1.isBlank() ? "Admin Tools: join a world first." : "Preview: /" + var1);
      }

      private void copyFireballCommand() {
         this.copyCommandPreview(this.fireballCommand(0, Math.max(1, this.integer("fireball-count"))), "fireball command");
      }

      private void startAirstrike() {
         this.queuedCommands.clear();
         this.commandIndex = 0;
         this.queuedDelayOption = "airstrike-delay";
         int var1 = this.integer("airstrike-count");

         for (int var2 = 0; var2 < var1; var2++) {
            String var3 = this.airstrikeCommand(var2, var1);
            if (!var3.isBlank()) {
               this.queuedCommands.add(var3);
            }
         }

         if (this.queuedCommands.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not queue airstrike, join a world first.");
         } else {
            this.commandDelay = 0;
            RiptideClientMessaging.sendPrefixed("Admin Tools: queued " + var1 + " airstrike fireballs.");
         }
      }

      public void loadForceOpPasswords() {
         MemoryStack var1 = MemoryStack.stackPush();

         try {
            PointerBuffer var2 = var1.mallocPointer(1);
            ByteBuffer var3 = MemoryUtil.memASCII("*.txt");
            var2.put(var3).rewind();

            try {
               String var4 = TinyFileDialogs.tinyfd_openFileDialog("Load Passwords", null, var2, "Text files", false);
               if (var4 != null && !var4.isBlank()) {
                  List var5 = Files.readAllLines(Paths.get(var4), StandardCharsets.UTF_8);
                  LinkedHashSet var6 = new LinkedHashSet();

                  for (String var8 : var5) {
                     String var9 = var8.trim();
                     if (!var9.isEmpty()) {
                        if (var9.contains(":")) {
                           String[] var10 = var9.split(":");
                           var9 = var10[var10.length - 1].trim();
                        }

                        if (!var9.isEmpty()) {
                           var6.add(var9);
                        }
                     }
                  }

                  this.forceOpPasswords = var6.toArray(new String[0]);
                  this.forceOpIndex = 0;
                  RiptideClientMessaging.sendPrefixed("Admin Tools: Loaded " + this.forceOpPasswords.length + " passwords.");
               }
            } catch (Exception var19) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: Failed to load passwords.");
               this.forceOpPasswords = this.defaultForceOpList;
               this.forceOpIndex = 0;
            } finally {
               MemoryUtil.memFree(var3);
            }
         } finally {
            var1.close();
         }
      }

      public void unloadForceOpPasswords() {
         this.forceOpPasswords = this.defaultForceOpList;
         this.forceOpIndex = 0;
         RiptideClientMessaging.sendPrefixed("Admin Tools: Unloaded custom passwords. Reverted to default.");
      }

      public String[] getForceOpPasswords() {
         return this.forceOpPasswords;
      }

      public void startForceOp() {
         if (!this.forceOpRunning) {
            if (MC.player == null) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: join a world first.");
            } else {
               this.forceOpRunning = true;
               this.forceOpIndex = 0;
               int var1 = this.integer("forceop-delay");
               boolean var2 = this.bool("forceop-wait");
               this.forceOpThread = new Thread(() -> {
                  MC.execute(() -> MC.getConnection().sendCommand("login " + MC.getUser().getName()));

                  for (int var3 = 0; var3 < this.forceOpPasswords.length; var3++) {
                     if (!this.forceOpRunning) {
                        return;
                     }

                     if (var2) {
                        this.gotWrongPwMsg = false;
                     }

                     long var4 = System.currentTimeMillis();

                     while (var2 && !this.gotWrongPwMsg && MC.player != null) {
                        if (!this.forceOpRunning) {
                           return;
                        }

                        if (System.currentTimeMillis() - var4 > 2000L) {
                           RiptideClientMessaging.sendPrefixed("Admin Tools: Timed out waiting for chat message. Stopping ForceOP.");
                           this.forceOpRunning = false;
                           return;
                        }

                        try {
                           Thread.sleep(50L);
                        } catch (InterruptedException var10) {
                        }
                     }

                     try {
                        Thread.sleep(var1);
                     } catch (InterruptedException var12) {
                     }

                     if (!this.forceOpRunning) {
                        return;
                     }

                     boolean var6 = false;

                     while (!var6 && this.forceOpRunning && MC.player != null) {
                        try {
                           String var7 = this.forceOpPasswords[var3];
                           int var8 = this.integer("forceop-min-length");
                           int var9 = this.integer("forceop-max-length");
                           if (var8 > 0 && var7.length() < var8) {
                              var6 = true;
                           } else if (var9 > 0 && var7.length() > var9) {
                              var6 = true;
                           } else {
                              MC.execute(() -> MC.getConnection().sendCommand("login " + var7));
                              var6 = true;
                           }
                        } catch (Exception var13) {
                           try {
                              Thread.sleep(50L);
                           } catch (InterruptedException var11) {
                           }
                        }
                     }

                     this.forceOpIndex = var3 + 1;
                  }

                  if (this.forceOpRunning) {
                     MC.execute(() -> RiptideClientMessaging.sendPrefixed("§c[§4§lFAILURE§c]§f All " + this.forceOpIndex + " passwords were wrong."));
                     this.forceOpRunning = false;
                  }
               }, "ForceOP");
               this.forceOpThread.start();
               RiptideClientMessaging.sendPrefixed("Admin Tools: ForceOP started.");
            }
         }
      }

      public void stopForceOp() {
         if (this.forceOpRunning) {
            this.forceOpRunning = false;
            if (this.forceOpThread != null) {
               this.forceOpThread.interrupt();
            }

            RiptideClientMessaging.sendPrefixed("Admin Tools: ForceOP stopped.");
         }
      }

      public int getForceOpTotal() {
         return this.forceOpPasswords.length;
      }

      public int getForceOpIndex() {
         return this.forceOpIndex;
      }

      public boolean isForceOpRunning() {
         return this.forceOpRunning;
      }

      private void presetMaxFireballStorm() {
         this.setValue("fireball-count", "256");
         this.setValue("fireball-spread", "16.0");
         this.setValue("fireball-speed", "3.5");
         this.setValue("fireball-power", "8");
         this.setValue("fireball-delay", "0");
         this.setValue("fireball-distance", "3.0");
         this.setValue("fireball-aim", "Cone");
         this.setValue("fireball-randomize", "true");
         RiptideClientMessaging.sendPrefixed("Admin Tools: loaded max fireball storm preset.");
      }

      private void presetTightAirstrike() {
         this.setValue("airstrike-count", "32");
         this.setValue("airstrike-radius", "8.0");
         this.setValue("airstrike-height", "56.0");
         this.setValue("airstrike-speed", "2.4");
         this.setValue("airstrike-power", "5");
         this.setValue("airstrike-delay", "1");
         this.setValue("airstrike-target", "Look");
         RiptideClientMessaging.sendPrefixed("Admin Tools: loaded tight airstrike preset.");
      }

      private void presetMaxAirstrike() {
         this.setValue("airstrike-count", "256");
         this.setValue("airstrike-radius", "48.0");
         this.setValue("airstrike-height", "128.0");
         this.setValue("airstrike-speed", "4.5");
         this.setValue("airstrike-power", "10");
         this.setValue("airstrike-delay", "0");
         this.setValue("airstrike-target", "Look");
         RiptideClientMessaging.sendPrefixed("Admin Tools: loaded max airstrike preset.");
      }

      private String fireballCommand(int var1, int var2) {
         return this.fireballCommand(var1, var2, this.decimal("fireball-distance"), 0.0);
      }

      private double firestormDistance() {
         return Math.max(2.0, this.decimal("firestorm-distance"));
      }

      private double firestormHeight() {
         return Math.max(0.0, this.decimal("firestorm-height"));
      }

      private String fireballCommand(int var1, int var2, double var3, double var5) {
         if (MC.player == null) {
            return "";
         } else {
            Vec3 var7 = MC.player.getLookAngle();
            Vec3 var8 = this.fireballDirection(var7, var1, var2).normalize();
            double var9 = this.decimal("fireball-speed");
            Vec3 var11 = var8.scale(var9);
            Vec3 var12 = MC.player.getEyePosition().add(var7.scale(var3)).add(0.0, var5, 0.0);
            return String.format(
               Locale.ROOT,
               "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[%.4f,%.4f,%.4f],Motion:[%.4f,%.4f,%.4f]}",
               var12.x,
               var12.y,
               var12.z,
               this.integer("fireball-power"),
               var8.x,
               var8.y,
               var8.z,
               var11.x,
               var11.y,
               var11.z
            );
         }
      }

      private Vec3 fireballDirection(Vec3 var1, int var2, int var3) {
         double var4 = Math.toRadians(this.decimal("fireball-spread"));
         if (!(var4 <= 0.0) && !"Look".equals(this.choice("fireball-aim"))) {
            double var6;
            double var8;
            if ("Ring".equals(this.choice("fireball-aim"))) {
               double var10 = (Math.PI * 2) * var2 / Math.max(1, var3);
               var6 = Math.cos(var10) * var4;
               var8 = Math.sin(var10) * var4;
            } else {
               var6 = (this.random.nextDouble() * 2.0 - 1.0) * var4;
               var8 = (this.random.nextDouble() * 2.0 - 1.0) * var4;
               if (!this.bool("fireball-randomize")) {
                  double var12 = var3 <= 1 ? 0.0 : (double)var2 / (var3 - 1) * 2.0 - 1.0;
                  var6 = var12 * var4;
                  var8 = 0.0;
               }
            }

            Vec3 var13 = var1.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            if (var13.lengthSqr() < 1.0E-4) {
               var13 = new Vec3(1.0, 0.0, 0.0);
            }

            Vec3 var11 = var13.cross(var1).normalize();
            return var1.add(var13.scale(Math.sin(var6))).add(var11.scale(Math.sin(var8)));
         } else {
            return var1;
         }
      }

      private String airstrikeCommand(int var1, int var2) {
         if (MC.player == null) {
            return "";
         } else {
            Vec3 var3 = this.airstrikeCenter();
            double var4 = this.decimal("airstrike-radius");
            double var6 = var2 <= 1 ? 0.0 : (Math.PI * 2) * ((double)var1 / var2);
            double var8 = var4 <= 0.0 ? 0.0 : Math.sqrt(this.random.nextDouble()) * var4;
            double var10 = var3.x + Math.cos(var6) * var8;
            double var12 = var3.z + Math.sin(var6) * var8;
            double var14 = var3.y + this.decimal("airstrike-height");
            double var16 = -Math.abs(this.decimal("airstrike-speed"));
            return String.format(
               Locale.ROOT,
               "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[0.0000,%.4f,0.0000],Motion:[0.0000,%.4f,0.0000]}",
               var10,
               var14,
               var12,
               this.integer("airstrike-power"),
               var16,
               var16
            );
         }
      }

      private Vec3 airstrikeCenter() {
         if (MC.player == null) {
            return Vec3.ZERO;
         } else if ("Self".equals(this.choice("airstrike-target"))) {
            return MC.player.position();
         } else {
            HitResult var1 = MC.hitResult;
            return var1 != null && var1.getType() != Type.MISS ? var1.getLocation() : MC.player.getEyePosition().add(MC.player.getLookAngle().scale(32.0));
         }
      }

      private void createNamedNbtItem() {
         if (MC.player != null && MC.getConnection() != null) {
            ItemStack var1 = this.buildEditorStack(true);
            if (!var1.isEmpty() && this.createCreativeStack(var1)) {
               RiptideClientMessaging.sendPrefixed("Sent edited item packet.");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
         }
      }

      private void fillHeldItemEditor() {
         if (MC.player == null) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: join a world first.");
         } else {
            ItemStack var1 = MC.player.getMainHandItem();
            if (var1.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: hold an item first.");
            } else if (this.fillItemEditorFromStack(var1, true)) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: filled editor from held item.");
            }
         }
      }

      private void copyTargetItem() {
         if (MC.player == null || MC.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: join a world first.");
         } else if (!this.isCreativeContext()) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
         } else if (MC.hitResult instanceof EntityHitResult var1) {
            Entity var6 = var1.getEntity();
            ItemStack var3 = var6 instanceof ItemEntity var5 ? var5.getItem() : (var6 instanceof LivingEntity var4 ? var4.getMainHandItem() : ItemStack.EMPTY);
            if (var3.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: targeted entity has no visible held item.");
            } else {
               ItemStack var7 = var3.copy();
               if (this.fillItemEditorFromStack(var7, false) && this.createCreativeStack(var7)) {
                  RiptideClientMessaging.sendPrefixed("Copied targeted item with all client-synced data.");
               }
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: look directly at an entity holding the item.");
         }
      }

      public boolean fillItemEditorFromStack(ItemStack var1, boolean var2) {
         if (var1 != null && !var1.isEmpty()) {
            this.selectEditorItemId(BuiltInRegistries.ITEM.getKey(var1.getItem()).toString(), Math.max(1, Math.min(99, var1.getMaxStackSize())));
            this.setValue("nbt-item-count", Integer.toString(Math.max(1, Math.min(99, var1.getCount()))));
            Component var3 = (Component)var1.get(DataComponents.CUSTOM_NAME);
            this.setValue("nbt-item-name", var3 == null ? "" : var3.getString());
            ItemLore var4 = (ItemLore)var1.get(DataComponents.LORE);
            if (var4 != null && !var4.lines().isEmpty()) {
               ArrayList var5 = new ArrayList();

               for (Component var7 : var4.lines()) {
                  var5.add(var7.getString());
               }

               this.setValue("nbt-item-lore", String.join("|", var5));
            } else {
               this.setValue("nbt-item-lore", "");
            }

            this.setValue("nbt-unbreakable", Boolean.toString(var1.has(DataComponents.UNBREAKABLE)));
            Boolean var13 = (Boolean)var1.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            this.setValue("nbt-glint", var13 == null ? "Default" : (var13 ? "On" : "Off"));
            Rarity var14 = this.explicitComponent(var1, DataComponents.RARITY);
            String var15;
            if (var14 == null) {
               var15 = "Default";
            } else {
               switch (var14) {
                  case COMMON:
                     var15 = "Common";
                     break;
                  case UNCOMMON:
                     var15 = "Uncommon";
                     break;
                  case RARE:
                     var15 = "Rare";
                     break;
                  case EPIC:
                     var15 = "Epic";
                     break;
                  default:
                     throw new MatchException(null, null);
               }
            }

            this.setValue("nbt-rarity", var15);
            this.setValue("nbt-max-damage", Integer.toString(Math.max(0, var1.getMaxDamage())));
            this.setValue("nbt-damage", Integer.toString(Math.max(0, var1.getDamageValue())));
            this.setValue("nbt-enchants", this.editorEnchantments(var1));
            this.setValue("nbt-attributes", this.editorAttributes(var1));
            CustomData var8 = (CustomData)var1.get(DataComponents.CUSTOM_DATA);
            CompoundTag var9 = var8 == null ? new CompoundTag() : var8.copyTag();
            this.setValue("nbt-custom-data", var9.isEmpty() ? "" : var9.toString());
            String var10 = var9.getStringOr("command", "");
            TypedEntityData var11 = (TypedEntityData)var1.get(DataComponents.BLOCK_ENTITY_DATA);
            if (var10.isBlank() && var11 != null) {
               var10 = var11.copyTagWithoutId().getStringOr("Command", "");
            }

            this.setValue("nbt-command", var10);
            String var12 = RiptideItemCommandSerializer.componentPatch(var1);
            this.setValue("nbt-imported-components", var12);
            this.setValue("nbt-imported-signature", this.editorSignature());
            this.setValue("nbt-item-components", var12);
            this.setValue("nbt-item-stack", RiptideItemNbtInspector.prettySnbt(RiptideItemCommandSerializer.itemStackSnbt(var1)));
            this.setValue("nbt-item-stack-signature", this.editorSignature());
            return true;
         } else {
            return false;
         }
      }

      public void syncEditorMaxStackForItemId() {
         this.selectEditorItemId(this.value("nbt-item-id"));
      }

      public void selectEditorItemId(String var1) {
         Identifier var2 = Identifier.tryParse(var1 == null ? "" : var1.trim());
         Item var3 = var2 == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(var2).orElse(Items.STICK);
         this.selectEditorItemId(BuiltInRegistries.ITEM.getKey(var3).toString(), this.editorItemDefaultMaxStack(var3));
      }

      private void selectEditorItemId(String var1, int var2) {
         int var3 = Math.max(1, Math.min(99, this.integer("nbt-max-stack")));
         int var4 = Math.max(1, Math.min(99, this.integer("nbt-item-count")));
         int var5 = Math.max(1, Math.min(99, var2));
         int var6 = var4;
         if (var4 > var5 || var4 == var3) {
            var6 = var5;
         }

         this.setValue("nbt-item-id", var1 != null && !var1.isBlank() ? var1 : BuiltInRegistries.ITEM.getKey(Items.STICK).toString());
         this.setValue("nbt-max-stack", Integer.toString(var5));
         this.setValue("nbt-item-count", Integer.toString(var6));
      }

      private void applyHeldItemEditor() {
         if (MC.player != null && MC.getConnection() != null) {
            ItemStack var1 = this.buildEditorStack(true);
            if (!var1.isEmpty() && this.createCreativeStack(var1)) {
               this.fillItemEditorFromStack(var1, false);
               RiptideClientMessaging.sendPrefixed("Sent edited held item packet.");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
         }
      }

      private ItemStack buildEditorStack() {
         return this.buildEditorStack(false);
      }

      private ItemStack buildEditorStack(boolean var1) {
         String var2 = this.value("nbt-item-stack").trim();
         boolean var3 = !var2.isBlank();
         ItemStack var4 = var3 ? RiptideItemCommandSerializer.itemStackFromSnbt(var2) : ItemStack.EMPTY;
         if (var3 && var4.isEmpty()) {
            if (var1) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: invalid full item SNBT.");
            }

            return ItemStack.EMPTY;
         } else {
            Identifier var5 = Identifier.tryParse(this.value("nbt-item-id").trim());
            Item var6 = var5 == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(var5).orElse(Items.STICK);
            boolean var7 = var3 && var4.getItem() != var6;
            ItemStack var8;
            if (var3 && !var7) {
               var8 = var4.copy();
            } else {
               var8 = new ItemStack(var6);
               if (var3) {
                  var8.applyComponents(var4.getComponentsPatch());
               }
            }

            boolean var9 = !var3;
            if (var9 || var7 || this.structuredFieldChanged("nbt-item-count")) {
               var8.setCount(Math.max(1, Math.min(99, this.integer("nbt-item-count"))));
            }

            if (!this.applyStructuredComponents(var8, var6, var9, var1)) {
               return ItemStack.EMPTY;
            } else {
               String var10 = RiptideItemCommandSerializer.validationError(var8);
               if (!var10.isBlank()) {
                  if (var1) {
                     RiptideClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + var10);
                  }

                  return ItemStack.EMPTY;
               } else {
                  return var8;
               }
            }
         }
      }

      private boolean applyStructuredComponents(ItemStack var1, Item var2, boolean var3, boolean var4) {
         if (var3 || this.structuredFieldChanged("nbt-item-name")) {
            String var5 = this.value("nbt-item-name").trim();
            if (var5.isBlank()) {
               var1.remove(DataComponents.CUSTOM_NAME);
            } else {
               var1.set(DataComponents.CUSTOM_NAME, Component.literal(var5));
            }
         }

         if (var3 || this.structuredFieldChanged("nbt-item-lore")) {
            List var10 = this.editorLoreComponents();
            if (var10.isEmpty()) {
               var1.remove(DataComponents.LORE);
            } else {
               var1.set(DataComponents.LORE, new ItemLore(var10));
            }
         }

         if (var3 || this.structuredFieldChanged("nbt-unbreakable")) {
            if (this.bool("nbt-unbreakable")) {
               var1.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            } else {
               var1.remove(DataComponents.UNBREAKABLE);
            }
         }

         if (var3 || this.structuredFieldChanged("nbt-glint")) {
            String var11 = this.choice("nbt-glint");
            switch (var11) {
               case "On":
                  var1.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                  break;
               case "Off":
                  var1.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                  break;
               default:
                  var1.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            }
         }

         if (var3 || this.structuredFieldChanged("nbt-rarity")) {
            String var12 = this.choice("nbt-rarity");
            switch (var12) {
               case "Common":
                  var1.set(DataComponents.RARITY, Rarity.COMMON);
                  break;
               case "Uncommon":
                  var1.set(DataComponents.RARITY, Rarity.UNCOMMON);
                  break;
               case "Rare":
                  var1.set(DataComponents.RARITY, Rarity.RARE);
                  break;
               case "Epic":
                  var1.set(DataComponents.RARITY, Rarity.EPIC);
                  break;
               default:
                  var1.remove(DataComponents.RARITY);
            }
         }

         boolean var13 = var3 || this.structuredFieldChanged("nbt-max-damage");
         boolean var15 = var3 || this.structuredFieldChanged("nbt-damage");
         if (var13) {
            int var7 = this.integer("nbt-max-damage");
            if (var7 > 0) {
               var1.set(DataComponents.MAX_DAMAGE, var7);
            } else {
               var1.remove(DataComponents.MAX_DAMAGE);
               var1.remove(DataComponents.DAMAGE);
            }
         }

         if (var15 && var1.getMaxDamage() > 0) {
            var1.set(DataComponents.DAMAGE, Math.min(Math.max(0, this.integer("nbt-damage")), var1.getMaxDamage()));
         }

         if (var3 || this.structuredFieldChanged("nbt-max-stack")) {
            int var16 = Math.max(1, Math.min(99, this.integer("nbt-max-stack")));
            int var8 = this.editorItemDefaultMaxStack(var2);
            if (var16 == var8) {
               var1.remove(DataComponents.MAX_STACK_SIZE);
            } else {
               var1.set(DataComponents.MAX_STACK_SIZE, var16);
            }
         }

         if ((var3 || this.structuredFieldChanged("nbt-enchants")) && !this.applyEditorEnchantments(var1, var4)) {
            return false;
         } else {
            if (var3 || this.structuredFieldChanged("nbt-attributes")) {
               ItemAttributeModifiers var17 = this.parseEditorAttributes(this.value("nbt-attributes"), var4);
               if (var17 == null) {
                  return false;
               }

               if (var17.modifiers().isEmpty()) {
                  var1.remove(DataComponents.ATTRIBUTE_MODIFIERS);
               } else {
                  var1.set(DataComponents.ATTRIBUTE_MODIFIERS, var17);
               }
            }

            boolean var18 = var3 || this.structuredFieldChanged("nbt-custom-data") || this.structuredFieldChanged("nbt-command");
            if (var18) {
               CompoundTag var19 = this.buildCustomDataTag(var4);
               if (var19 == null) {
                  return false;
               }

               if (var19.isEmpty()) {
                  var1.remove(DataComponents.CUSTOM_DATA);
               } else {
                  var1.set(DataComponents.CUSTOM_DATA, CustomData.of(var19));
               }

               String var9 = this.normalizedCommand(this.value("nbt-command"));
               if (this.isCommandBlockItem() && !var9.isBlank()) {
                  var1.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityTypes.COMMAND_BLOCK, this.commandBlockEntityTag(var9)));
               } else if (this.structuredFieldChanged("nbt-command")) {
                  var1.remove(DataComponents.BLOCK_ENTITY_DATA);
               }
            }

            return true;
         }
      }

      private int editorItemDefaultMaxStack() {
         Identifier var1 = Identifier.tryParse(this.value("nbt-item-id").trim());
         Item var2 = var1 == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(var1).orElse(Items.STICK);
         return this.editorItemDefaultMaxStack(var2);
      }

      private int editorItemDefaultMaxStack(Item var1) {
         ItemStack var2 = new ItemStack(var1 == null ? Items.STICK : var1);
         return Math.max(1, Math.min(99, var2.getMaxStackSize()));
      }

      private List<Component> editorLoreComponents() {
         String var1 = this.value("nbt-item-lore");
         if (var1 != null && !var1.isBlank()) {
            ArrayList var2 = new ArrayList();

            for (String var6 : var1.split("\\|")) {
               String var7 = var6.trim();
               if (!var7.isEmpty()) {
                  var2.add(Component.literal(var7));
               }
            }

            return var2;
         } else {
            return List.of();
         }
      }

      private void syncEditorComponentsPatch() {
         this.setValue("nbt-item-components", this.buildEditorComponents());
         this.setValue("nbt-imported-components", "");
         this.setValue("nbt-imported-signature", "");
         this.setValue("nbt-item-stack", "");
         this.setValue("nbt-item-stack-signature", "");
      }

      public void setRawItemComponents(String var1) {
         String var2 = var1 == null ? "" : var1.trim();
         this.setValue("nbt-item-components", var2);
         this.setValue("nbt-imported-components", var2);
         this.setValue("nbt-imported-signature", this.editorSignature());
         this.setValue("nbt-item-stack", "");
         this.setValue("nbt-item-stack-signature", "");
      }

      public boolean setRawItemStackSnbt(String var1) {
         ItemStack var2 = RiptideItemCommandSerializer.itemStackFromSnbt(var1);
         return !var2.isEmpty() && RiptideItemCommandSerializer.validationError(var2).isBlank() ? this.fillItemEditorFromStack(var2, false) : false;
      }

      public void prepareRawItemStackEditor() {
         if (!this.hasActiveImportedStack()) {
            ItemStack var1 = this.buildEditorStack(false);
            if (!var1.isEmpty()) {
               this.fillItemEditorFromStack(var1, false);
            }
         }
      }

      private boolean hasActiveImportedStack() {
         String var1 = this.value("nbt-item-stack").trim();
         return !var1.isBlank() && this.value("nbt-item-stack-signature").equals(this.editorSignature());
      }

      private String buildEditorComponents() {
         String var1 = this.buildEditorComponents(false);
         return var1 == null ? "" : var1;
      }

      private String buildEditorComponents(boolean var1) {
         String var2 = this.activeImportedComponents();
         return !var2.isBlank() ? var2 : this.buildManualEditorComponents(var1);
      }

      private String buildManualEditorComponents(boolean var1) {
         ArrayList var2 = new ArrayList();
         String var3 = this.value("nbt-item-name").trim();
         if (!var3.isBlank()) {
            var2.add("custom_name=[" + this.jsonText(var3, "white") + "]");
         }

         String var4 = this.loreComponentString();
         if (!var4.isBlank()) {
            var2.add("lore=" + var4);
         }

         if (this.bool("nbt-unbreakable")) {
            var2.add("unbreakable={}");
         }

         String var5 = this.choice("nbt-glint");
         if ("On".equals(var5)) {
            var2.add("enchantment_glint_override=true");
         } else if ("Off".equals(var5)) {
            var2.add("enchantment_glint_override=false");
         }

         String var6 = this.choice("nbt-rarity").toLowerCase(Locale.ROOT);
         if (!"default".equals(var6)) {
            var2.add("rarity=" + var6);
         }

         int var7 = this.integer("nbt-max-damage");
         if (var7 > 0) {
            var2.add("max_damage=" + var7);
            var2.add("damage=" + Math.min(this.integer("nbt-damage"), var7));
         } else if (this.integer("nbt-max-stack") != this.editorItemDefaultMaxStack()) {
            var2.add("max_stack_size=" + Math.max(1, Math.min(99, this.integer("nbt-max-stack"))));
         }

         String var8 = this.enchantmentsComponent(this.value("nbt-enchants"), var1);
         if (var8 == null) {
            return null;
         } else {
            if (!var8.isBlank()) {
               var2.add("enchantments=" + var8);
            }

            this.appendRawListComponent(var2, "attribute_modifiers", this.value("nbt-attributes"));
            String var9 = this.normalizedCommand(this.value("nbt-command"));
            boolean var10 = !var9.isBlank() && this.isCommandBlockItem();
            if (var10) {
               var2.add("block_entity_data=" + this.commandBlockEntityComponent(var9));
            }

            String var11 = this.customDataComponentString(var10 ? "" : var9);
            if (!var11.isBlank()) {
               var2.add("custom_data=" + var11);
            }

            return var2.isEmpty() ? "" : "[" + String.join(",", var2) + "]";
         }
      }

      private String activeImportedComponents() {
         String var1 = this.value("nbt-imported-components").trim();
         if (var1.isBlank()) {
            return "";
         } else {
            String var2 = this.value("nbt-imported-signature");
            if (!var2.equals(this.editorSignature())) {
               return "";
            } else {
               return var1.startsWith("[") ? var1 : "[" + var1 + "]";
            }
         }
      }

      private String editorSignature() {
         StringBuilder var1 = new StringBuilder();

         for (String var3 : ITEM_EDITOR_SIGNATURE_FIELDS) {
            String var4 = this.value(var3);
            var1.append(var4.length()).append(':').append(var4);
         }

         return var1.toString();
      }

      private boolean structuredFieldChanged(String var1) {
         int var2 = ITEM_EDITOR_SIGNATURE_FIELDS.indexOf(var1);
         if (var2 < 0) {
            return true;
         } else {
            String var3 = this.value("nbt-item-stack-signature");
            if (var3.isBlank()) {
               return true;
            } else if (var3.indexOf(31) >= 0) {
               String[] var5 = var3.split("\u001f", -1);
               return var2 >= var5.length || !this.value(var1).equals(var5[var2]);
            } else {
               String var4 = this.signatureField(var3, var2);
               return var4 == null || !this.value(var1).equals(var4);
            }
         }
      }

      private String signatureField(String var1, int var2) {
         int var3 = 0;

         for (int var4 = 0; var4 <= var2; var4++) {
            int var5 = var1.indexOf(58, var3);
            if (var5 < var3) {
               return null;
            }

            int var6;
            try {
               var6 = Integer.parseInt(var1.substring(var3, var5));
            } catch (NumberFormatException var9) {
               return null;
            }

            int var7 = var5 + 1;
            int var8 = var7 + var6;
            if (var6 < 0 || var8 < var7 || var8 > var1.length()) {
               return null;
            }

            if (var4 == var2) {
               return var1.substring(var7, var8);
            }

            var3 = var8;
         }

         return null;
      }

      private <T> T explicitComponent(ItemStack var1, DataComponentType<T> var2) {
         if (var1 != null && !var1.isEmpty() && var2 != null) {
            for (Entry var4 : var1.getComponentsPatch().entrySet()) {
               if (var4.getKey() == var2) {
                  return (T)((Optional)var4.getValue()).orElse(null);
               }
            }

            return null;
         } else {
            return null;
         }
      }

      private String editorEnchantments(ItemStack var1) {
         ArrayList var2 = new ArrayList();
         this.appendEditorEnchantments(var2, (ItemEnchantments)var1.get(DataComponents.ENCHANTMENTS));
         this.appendEditorEnchantments(var2, (ItemEnchantments)var1.get(DataComponents.STORED_ENCHANTMENTS));
         return String.join(",", var2);
      }

      private boolean applyEditorEnchantments(ItemStack var1, boolean var2) {
         List var3 = this.normalizedEnchantmentEntries(this.value("nbt-enchants"), var2);
         if (var3 == null) {
            return false;
         } else {
            LinkedHashMap var4 = new LinkedHashMap();

            for (String var6 : var3) {
               int var7 = var6.lastIndexOf(58);
               if (var7 <= 0 || var7 >= var6.length() - 1) {
                  return false;
               }

               String var8 = this.stripQuotes(var6.substring(0, var7));
               if (!var8.contains(":")) {
                  var8 = "minecraft:" + var8;
               }

               try {
                  var4.put(var8, Integer.parseInt(var6.substring(var7 + 1)));
               } catch (NumberFormatException var14) {
                  return false;
               }
            }

            ItemEnchantments var15 = (ItemEnchantments)var1.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments var16 = (ItemEnchantments)var1.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            Mutable var17 = new Mutable(var15);
            Mutable var18 = new Mutable(var16);
            LinkedHashSet var9 = new LinkedHashSet();

            for (Holder var11 : List.copyOf(var15.keySet())) {
               String var12 = this.enchantmentId(var11);
               Integer var13 = (Integer)var4.get(var12);
               var17.set(var11, var13 == null ? 0 : var13);
               if (var13 != null) {
                  var9.add(var12);
               }
            }

            for (Holder var21 : List.copyOf(var16.keySet())) {
               String var24 = this.enchantmentId(var21);
               Integer var27 = (Integer)var4.get(var24);
               var18.set(var21, var27 == null ? 0 : var27);
               if (var27 != null) {
                  var9.add(var24);
               }
            }

            boolean var20 = var1.is(Items.ENCHANTED_BOOK) || !var16.isEmpty() && var15.isEmpty();

            for (Entry var25 : var4.entrySet()) {
               if (!var9.contains(var25.getKey())) {
                  Holder var28 = this.enchantmentHolder((String)var25.getKey());
                  if (var28 == null) {
                     if (var2) {
                        RiptideClientMessaging.sendPrefixed("Admin Tools: unknown enchantment '" + (String)var25.getKey() + "'.");
                     }

                     return false;
                  }

                  if (var20) {
                     var18.set(var28, (Integer)var25.getValue());
                  } else {
                     var17.set(var28, (Integer)var25.getValue());
                  }
               }
            }

            ItemEnchantments var23 = var17.toImmutable();
            ItemEnchantments var26 = var18.toImmutable();
            if (var23.isEmpty()) {
               var1.remove(DataComponents.ENCHANTMENTS);
            } else {
               var1.set(DataComponents.ENCHANTMENTS, var23);
            }

            if (var26.isEmpty()) {
               var1.remove(DataComponents.STORED_ENCHANTMENTS);
            } else {
               var1.set(DataComponents.STORED_ENCHANTMENTS, var26);
            }

            return true;
         }
      }

      private String enchantmentId(Holder<Enchantment> var1) {
         return var1.unwrapKey().map(var0 -> var0.identifier().toString()).orElse("");
      }

      private Holder<Enchantment> enchantmentHolder(String var1) {
         try {
            if (MC.player == null) {
               return null;
            } else {
               Identifier var2 = Identifier.tryParse(var1);
               if (var2 == null) {
                  return null;
               } else {
                  ResourceKey var3 = ResourceKey.create(Registries.ENCHANTMENT, var2);
                  return (Holder<Enchantment>)MC.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(var3).orElse(null);
               }
            }
         } catch (RuntimeException var4) {
            return null;
         }
      }

      private String editorAttributes(ItemStack var1) {
         ItemAttributeModifiers var2 = (ItemAttributeModifiers)var1.get(DataComponents.ATTRIBUTE_MODIFIERS);
         if (var2 != null && !var2.modifiers().isEmpty() && MC.player != null) {
            try {
               Tag var3 = (Tag)ItemAttributeModifiers.CODEC
                  .encodeStart(MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), var2)
                  .getOrThrow();
               String var4 = var3.toString();
               return var4.length() >= 2 && var4.startsWith("[") && var4.endsWith("]") ? var4.substring(1, var4.length() - 1) : var4;
            } catch (RuntimeException var5) {
               return "";
            }
         } else {
            return "";
         }
      }

      private ItemAttributeModifiers parseEditorAttributes(String var1, boolean var2) {
         if (var1 == null || var1.isBlank()) {
            return ItemAttributeModifiers.EMPTY;
         } else if (MC.player == null) {
            return null;
         } else {
            try {
               CompoundTag var3 = TagParser.parseCompoundFully("{value:[" + var1 + "]}");
               Tag var4 = var3.get("value");
               return var4 == null
                  ? ItemAttributeModifiers.EMPTY
                  : (ItemAttributeModifiers)ItemAttributeModifiers.CODEC
                     .parse(MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), var4)
                     .getOrThrow();
            } catch (Throwable var5) {
               if (var2) {
                  RiptideClientMessaging.sendPrefixed("Admin Tools: invalid attribute modifiers.");
               }

               return null;
            }
         }
      }

      private void appendEditorEnchantments(List<String> var1, ItemEnchantments var2) {
         if (var2 != null && !var2.isEmpty()) {
            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry var4 : var2.entrySet()) {
               String var5 = ((Holder)var4.getKey()).unwrapKey().map(var0 -> var0.identifier().toString()).orElse("").trim();
               if (var5.startsWith("minecraft:")) {
                  var5 = var5.substring("minecraft:".length());
               }

               if (!var5.isBlank()) {
                  var1.add(var5 + ":" + var4.getIntValue());
               }
            }
         }
      }

      private String customDataComponentString(String var1) {
         String var2 = this.value("nbt-custom-data").trim();
         String var3 = this.mergeCustomDataPayload(var2, var1);
         return var3.isBlank() ? "" : this.wrapCompound(var3);
      }

      private CompoundTag buildCustomDataTag(boolean var1) {
         String var2 = this.normalizedCommand(this.value("nbt-command"));
         String var3 = this.customDataComponentString(this.isCommandBlockItem() ? "" : var2);
         if (var3.isBlank()) {
            return new CompoundTag();
         } else {
            try {
               return TagParser.parseCompoundFully(var3);
            } catch (Exception var5) {
               if (var1) {
                  RiptideClientMessaging.sendPrefixed("Admin Tools: invalid custom data / embedded command data.");
               }

               return null;
            }
         }
      }

      private String mergeCustomDataPayload(String var1, String var2) {
         String var3 = var1 == null ? "" : var1.trim();
         String var4 = this.normalizedCommand(var2);
         if (var4.isBlank()) {
            return var3;
         } else {
            ArrayList var5 = new ArrayList();
            String var6 = this.unwrapCompound(var3);
            if (!var6.isBlank()) {
               for (String var8 : this.splitTopLevelEntries(var6)) {
                  String var9 = this.topLevelSnbtKey(var8);
                  if (!"riptide_admin_tool".equals(var9) && !"command".equals(var9)) {
                     var5.add(var8.trim());
                  }
               }
            }

            var5.add("riptide_admin_tool:1b");
            var5.add("command:\"" + this.snbtString(var4) + "\"");
            return String.join(",", var5);
         }
      }

      private String commandBlockEntityComponent(String var1) {
         String var2 = this.normalizedCommand(var1);
         if (var2.isBlank()) {
            var2 = "say Riptide";
         }

         return "{id:\"minecraft:command_block\",Command:\"" + this.snbtString(var2) + "\",auto:0b,TrackOutput:1b}";
      }

      private CompoundTag commandBlockEntityTag(String var1) {
         CompoundTag var2 = new CompoundTag();
         var2.putString("id", "minecraft:command_block");
         var2.putString("Command", this.normalizedCommand(var1).isBlank() ? "say Riptide" : this.normalizedCommand(var1));
         var2.putByte("auto", (byte)0);
         var2.putByte("TrackOutput", (byte)1);
         return var2;
      }

      private boolean isCommandBlockItem() {
         String var1 = this.value("nbt-item-id").trim();
         return "minecraft:command_block".equals(var1)
            || "command_block".equals(var1)
            || "minecraft:chain_command_block".equals(var1)
            || "chain_command_block".equals(var1)
            || "minecraft:repeating_command_block".equals(var1)
            || "repeating_command_block".equals(var1);
      }

      private String loreComponentString() {
         ArrayList var1 = new ArrayList();
         String var2 = this.value("nbt-item-lore");
         if (var2 != null && !var2.isBlank()) {
            for (String var6 : var2.split("\\|")) {
               String var7 = var6.trim();
               if (!var7.isEmpty()) {
                  var1.add(this.jsonText(var7, "dark_purple"));
               }
            }

            if (var1.isEmpty()) {
               return "";
            } else {
               ArrayList var8 = new ArrayList();

               for (String var10 : var1) {
                  var8.add("[" + var10 + "]");
               }

               return "[" + String.join(",", var8) + "]";
            }
         } else {
            return "";
         }
      }

      private void appendRawListComponent(List<String> var1, String var2, String var3) {
         if (var3 != null && !var3.isBlank()) {
            String var4 = var3.trim();
            var1.add(var2 + "=" + (var4.startsWith("[") ? var4 : "[" + var4 + "]"));
         }
      }

      private String enchantmentsComponent(String var1, boolean var2) {
         List var3 = this.normalizedEnchantmentEntries(var1, var2);
         if (var3 == null) {
            return null;
         } else {
            return var3.isEmpty() ? "" : "{" + String.join(",", var3) + "}";
         }
      }

      private List<String> normalizedEnchantmentEntries(String var1, boolean var2) {
         ArrayList var3 = new ArrayList();
         if (var1 != null && !var1.isBlank()) {
            for (String var5 : this.splitTopLevelEntries(var1)) {
               String var6 = this.normalizeEnchantmentEntry(var5);
               if (var6 == null) {
                  if (var2) {
                     RiptideClientMessaging.sendPrefixed("Admin Tools: invalid enchantment entry '" + var5 + "'. Use id:level, for example binding_curse:1.");
                  }

                  return null;
               }

               var3.add(var6);
            }

            return var3;
         } else {
            return var3;
         }
      }

      private String normalizeEnchantmentEntry(String var1) {
         if (var1 == null) {
            return null;
         } else {
            String var2 = var1.trim();
            if (var2.isBlank()) {
               return null;
            } else {
               if (var2.startsWith("[") && var2.endsWith("]") && var2.length() > 2) {
                  var2 = var2.substring(1, var2.length() - 1).trim();
               }

               if (var2.startsWith("{") && var2.endsWith("}") && var2.contains("id:") && var2.contains("level:")) {
                  String var8 = this.extractComponentValue(var2, "id");
                  String var10 = this.extractComponentValue(var2, "level");
                  return this.normalizeEnchantmentIdAndLevel(var8, var10);
               } else {
                  int var3 = var2.indexOf(":{level:");
                  if (var3 > 0) {
                     String var9 = var2.substring(0, var3);
                     String var11 = var2.substring(var3 + ":{level:".length());
                     int var12 = var11.indexOf(125);
                     String var7 = var12 >= 0 ? var11.substring(0, var12) : var11;
                     return this.normalizeEnchantmentIdAndLevel(var9, var7);
                  } else {
                     int var4 = var2.lastIndexOf(58);
                     if (var4 > 0 && var4 < var2.length() - 1) {
                        String var5 = var2.substring(0, var4);

                        while (var5.endsWith(":")) {
                           var5 = var5.substring(0, var5.length() - 1);
                        }

                        String var6 = var2.substring(var4 + 1);
                        return this.normalizeEnchantmentIdAndLevel(var5, var6);
                     } else {
                        return null;
                     }
                  }
               }
            }
         }
      }

      private String normalizeEnchantmentIdAndLevel(String var1, String var2) {
         String var3 = this.normalizeEnchantmentId(var1);
         if (var3.isBlank()) {
            return null;
         } else {
            String var4 = this.stripQuotes(var2).trim();

            try {
               int var5 = Integer.parseInt(var4);
               if (var5 >= 1 && var5 <= 255) {
                  String var6 = var3.contains(":") ? "\"" + this.snbtString(var3) + "\"" : var3;
                  return var6 + ":" + var5;
               } else {
                  return null;
               }
            } catch (NumberFormatException var7) {
               return null;
            }
         }
      }

      private String normalizeEnchantmentId(String var1) {
         String var2 = this.stripQuotes(var1).trim();
         if (var2.isBlank()) {
            return "";
         } else {
            if (var2.startsWith("minecraft:")) {
               var2 = var2.substring("minecraft:".length());
            }

            Identifier var3 = var2.contains(":") ? Identifier.tryParse(var2) : Identifier.tryParse("minecraft:" + var2);
            return var3 == null ? "" : var2;
         }
      }

      private String extractComponentValue(String var1, String var2) {
         String var3 = var2 + ":";
         int var4 = var1.indexOf(var3);
         if (var4 < 0) {
            return "";
         } else {
            var4 += var3.length();
            int var5 = var4;
            boolean var6 = false;
            char var7 = 0;

            for (boolean var8 = false; var5 < var1.length(); var5++) {
               char var9 = var1.charAt(var5);
               if (var8) {
                  var8 = false;
               } else if (var9 == '\\') {
                  var8 = true;
               } else if (var6) {
                  if (var9 == var7) {
                     var6 = false;
                  }
               } else if (var9 != '\'' && var9 != '"') {
                  if (var9 == ',' || var9 == '}' || var9 == ']') {
                     break;
                  }
               } else {
                  var6 = true;
                  var7 = var9;
               }
            }

            return var1.substring(var4, var5).trim();
         }
      }

      private List<String> splitTopLevelEntries(String var1) {
         ArrayList var2 = new ArrayList();
         if (var1 != null && !var1.isBlank()) {
            String var3 = var1.trim();
            if (var3.startsWith("[") && var3.endsWith("]") || var3.startsWith("{") && var3.endsWith("}")) {
               var3 = var3.substring(1, var3.length() - 1);
            }

            StringBuilder var4 = new StringBuilder();
            int var5 = 0;
            boolean var6 = false;
            char var7 = 0;
            boolean var8 = false;

            for (int var9 = 0; var9 < var3.length(); var9++) {
               char var10 = var3.charAt(var9);
               if (var8) {
                  var4.append(var10);
                  var8 = false;
               } else if (var10 == '\\') {
                  var4.append(var10);
                  var8 = true;
               } else if (var6) {
                  var4.append(var10);
                  if (var10 == var7) {
                     var6 = false;
                  }
               } else if (var10 != '\'' && var10 != '"') {
                  if (var10 == '{' || var10 == '[' || var10 == '(') {
                     var5++;
                  } else if (var10 == '}' || var10 == ']' || var10 == ')') {
                     var5 = Math.max(0, var5 - 1);
                  }

                  if (var10 == ',' && var5 == 0) {
                     String var11 = var4.toString().trim();
                     if (!var11.isBlank()) {
                        var2.add(var11);
                     }

                     var4.setLength(0);
                  } else {
                     var4.append(var10);
                  }
               } else {
                  var6 = true;
                  var7 = var10;
                  var4.append(var10);
               }
            }

            String var12 = var4.toString().trim();
            if (!var12.isBlank()) {
               var2.add(var12);
            }

            return var2;
         } else {
            return var2;
         }
      }

      private String stripQuotes(String var1) {
         if (var1 == null) {
            return "";
         } else {
            String var2 = var1.trim();
            return var2.length() >= 2 && (var2.startsWith("\"") && var2.endsWith("\"") || var2.startsWith("'") && var2.endsWith("'"))
               ? var2.substring(1, var2.length() - 1)
               : var2;
         }
      }

      private String wrapCompound(String var1) {
         String var2 = var1.trim();
         return var2.startsWith("{") ? var2 : "{" + var2 + "}";
      }

      private String unwrapCompound(String var1) {
         String var2 = var1 == null ? "" : var1.trim();
         return var2.length() >= 2 && var2.startsWith("{") && var2.endsWith("}") ? var2.substring(1, var2.length() - 1).trim() : var2;
      }

      private String topLevelSnbtKey(String var1) {
         if (var1 == null) {
            return "";
         } else {
            String var2 = var1.trim();
            int var3 = var2.indexOf(58);
            return var3 <= 0 ? "" : this.stripQuotes(var2.substring(0, var3)).trim();
         }
      }

      private String normalizedCommand(String var1) {
         if (var1 == null) {
            return "";
         } else {
            var1 = var1.trim();
            return var1.startsWith("/") ? var1.substring(1) : var1;
         }
      }

      private void createCommandBlockPreset() {
         this.setValue("nbt-item-id", "minecraft:command_block");
         this.setValue("nbt-item-count", "1");
         this.setValue("nbt-item-name", "Admin Command Block");
         this.setValue("nbt-command", this.commandTemplate().isBlank() ? "say Riptide" : this.commandTemplate());
         this.syncEditorComponentsPatch();
         if (MC.player != null && MC.getConnection() != null) {
            ItemStack var1 = this.buildEditorStack(true);
            if (!var1.isEmpty() && this.createCreativeStack(var1)) {
               RiptideClientMessaging.sendPrefixed("Loaded preset and sent command block item packet.");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Loaded command block preset. Join a world to send the item packet.");
         }
      }

      private String adminTarget() {
         return MC.player == null ? "" : MC.player.getGameProfile().name();
      }

      private void createForceOpStickPreset() {
         String var1 = this.adminTarget().isBlank() ? "{target}" : this.adminTarget();
         this.setValue("nbt-item-id", "minecraft:stick");
         this.setValue("nbt-item-count", "1");
         this.setValue("nbt-item-name", "ForceOP Tool");
         this.setValue("nbt-command", "op " + var1);
         this.setValue("nbt-custom-data", "riptide_admin_tool:1b,command:\"" + this.snbtString("op " + var1) + "\"");
         this.syncEditorComponentsPatch();
         if (MC.player != null && MC.getConnection() != null) {
            ItemStack var2 = this.buildEditorStack(true);
            if (!var2.isEmpty() && this.createCreativeStack(var2)) {
               RiptideClientMessaging.sendPrefixed("Loaded preset and sent ForceOP tool item packet.");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Loaded ForceOP stick preset. Join a world to send the item packet.");
         }
      }

      private void presetForceOpCommandBlock() {
         String var1 = this.adminTarget().isBlank() ? "{target}" : this.adminTarget();
         this.setValue("nbt-item-id", "minecraft:command_block");
         this.setValue("nbt-item-count", "1");
         this.setValue("nbt-item-name", "ForceOP Command Block");
         this.setValue("nbt-command", "op " + var1);
         this.syncEditorComponentsPatch();
         RiptideClientMessaging.sendPrefixed("Loaded ForceOP command block preset. Use Apply Item in creative mode.");
      }

      private ItemStack namedStack(Item var1, int var2, String var3) {
         ItemStack var4 = new ItemStack(var1 == null ? Items.STICK : var1, Math.max(1, Math.min(99, var2)));
         if (var3 != null && !var3.isBlank()) {
            var4.set(DataComponents.CUSTOM_NAME, Component.literal(var3));
         }

         return var4;
      }

      private boolean createCreativeStack(ItemStack var1) {
         if (MC.player == null || MC.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
            return false;
         } else if (!this.isCreativeContext()) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
            return false;
         } else if (var1 != null && !var1.isEmpty()) {
            String var2 = RiptideItemCommandSerializer.validationError(var1);
            if (!var2.isBlank()) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + var2);
               return false;
            } else {
               int var3 = MC.player.getInventory().getSelectedSlot();
               int var4 = 36 + var3;
               MC.player.getInventory().setItem(var3, var1.copy());

               try {
                  MC.getConnection().send(new ServerboundSetCreativeModeSlotPacket(var4, var1));
                  return true;
               } catch (RuntimeException var6) {
                  RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet: " + var6.getClass().getSimpleName());
                  return false;
               }
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet, item stack is empty.");
            return false;
         }
      }

      private void createFireChargeStack() {
         if (MC.player != null && MC.getConnection() != null) {
            ItemStack var1 = new ItemStack(Items.FIRE_CHARGE, 64);
            var1.set(DataComponents.CUSTOM_NAME, Component.literal("Fireball Storm"));
            if (this.createCreativeStack(var1)) {
               RiptideClientMessaging.sendPrefixed("Sent Fireball Storm stack item packet.");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
         }
      }

      public void giveEditorItem() {
         String var1 = this.value("nbt-item-id").trim();
         if (var1.startsWith("/")) {
            var1 = var1.substring(1);
         }

         if (var1.isBlank()) {
            RiptideClientMessaging.sendPrefixed("Admin Tools: set an item id first.");
         } else {
            String var2 = this.buildEditorComponents(true);
            if (var2 != null) {
               int var3 = Math.max(1, Math.min(99, this.integer("nbt-item-count")));
               this.sendAdminCommand("give @p " + var1 + var2 + " " + var3, false);
            }
         }
      }

      private boolean sendAdminCommand(String var1, boolean var2) {
         return this.sendAdminCommand(var1, var2, true);
      }

      private boolean sendAdminCommand(String var1, boolean var2, boolean var3) {
         if (MC != null && MC.getConnection() != null) {
            if (var1 != null && !var1.isBlank()) {
               String var4 = var1.startsWith("/") ? var1.substring(1) : var1;

               try {
                  MC.getConnection().sendCommand(var4);
                  String var5 = var4.toLowerCase(Locale.ROOT);
                  if (var5.startsWith("summon") && var5.contains("fireball")) {
                     this.suppressSummonMessagesUntilMs = System.currentTimeMillis() + 5000L;
                  }

                  return true;
               } catch (RuntimeException var6) {
                  if (var3) {
                     RiptideClientMessaging.sendPrefixed(
                        "Admin Tools: could not send " + (var2 ? "queued " : "") + "command: " + var6.getClass().getSimpleName()
                     );
                  }

                  return false;
               }
            } else {
               if (var3) {
                  RiptideClientMessaging.sendPrefixed("Admin Tools: could not send command, command is empty.");
               }

               return false;
            }
         } else {
            if (var3) {
               RiptideClientMessaging.sendPrefixed("Admin Tools: could not send command, join a world first.");
            }

            return false;
         }
      }

      private void copyCommandPreview(String var1, String var2) {
         if (var1 != null && !var1.isBlank()) {
            this.copyToClipboard("/" + var1, "Copied " + var2 + ".");
         } else {
            RiptideClientMessaging.sendPrefixed("Admin Tools: join a world first.");
         }
      }

      private void copyToClipboard(String var1, String var2) {
         if (MC != null && MC.keyboardHandler != null && var1 != null && !var1.isBlank()) {
            MC.keyboardHandler.setClipboard(var1);
            RiptideNotifications.copied(var2);
         } else {
            RiptideNotifications.error("Nothing to copy.");
         }
      }

      private String commandTemplate() {
         String var1 = this.value("nbt-command").trim();
         if (var1.isBlank()) {
            var1 = "op {target}";
         }

         String var2 = this.adminTarget();
         if (!var2.isBlank()) {
            var1 = var1.replace("{target}", var2);
         }

         return var1.startsWith("/") ? var1.substring(1) : var1;
      }

      private String namedToolComponents(String var1, String var2, String var3) {
         return "[custom_name=["
            + this.jsonText(var1, "red")
            + "],lore=[["
            + this.jsonText(var2, "gray")
            + "]],custom_data={riptide_admin_tool:1b,command:\""
            + this.snbtString(var3)
            + "\"}]";
      }

      private String commandBlockComponents(String var1) {
         String var2 = var1 != null && !var1.isBlank() ? var1 : "say Riptide";
         var2 = var2.startsWith("/") ? var2.substring(1) : var2;
         return "[custom_name=["
            + this.jsonText(this.value("nbt-item-name").isBlank() ? "Admin Command Block" : this.value("nbt-item-name"), "red")
            + "],block_entity_data={id:\"minecraft:command_block\",Command:\""
            + this.snbtString(var2)
            + "\",auto:0b,TrackOutput:1b}]";
      }

      private String jsonText(String var1, String var2) {
         return "{\"text\":\"" + this.jsonString(var1) + "\",\"color\":\"" + this.jsonString(var2) + "\",\"italic\":false}";
      }

      private String jsonString(String var1) {
         return var1 == null ? "" : var1.replace("\\", "\\\\").replace("\"", "\\\"");
      }

      private String snbtString(String var1) {
         return this.jsonString(var1);
      }
   }

   static final class AimAssistModule extends Module {
      private static final int TARGET_SWITCH_CONFIRM_TICKS = 4;
      private final QuantizedRotationSmoother rotationSmoother = new QuantizedRotationSmoother();
      private RiptideRotationUtil.Rotation lastTargetRotation;
      private RiptideRotationUtil.Rotation filteredTargetRotation;
      private float sampledHorizontalSpeed = 0.4F;
      private float sampledVerticalSpeed = 0.3F;
      private float sampledDirectionChange;
      private int trackedTargetId = Integer.MIN_VALUE;
      private int switchCandidateId = Integer.MIN_VALUE;
      private int switchCandidateTicks;
      private String cachedEntityListSource = "";
      private Set<String> cachedEntityIds = Set.of();

      AimAssistModule() {
         super("aim-assist", "AimAssist", ModuleCategory.COMBAT, "Smoothly assists your aim toward configured entities.");
         this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").build());
         this.add(new DoubleSetting("range", "Range", 4.5, 1.0, 8.0, 0.1).build());
         this.add(new IntSetting("fov", "FOV", 80, 0, 180, 1).build());
         this.add(new ChoiceSetting("target-point", "Target Point", "Nearest", "Nearest", "Center", "Head", "Body", "Feet").build());
         this.add(new IntSetting("safe-zone", "Safe Zone", 5, 0, 100, 1).formatter(var0 -> var0 + "%").description("Deadzone around aim point.").build());
         this.add(new ChoiceSetting("priority", "Priority", "Direction", "Direction", "Type", "Health", "Distance", "HurtTime", "Age").build());
         this.add(new ChoiceSetting("axis", "Axis", "Both", "Both", "Horizontal", "Vertical").build());
         this.add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1).build());
         this.add(new ChoiceSetting("smooth-mode", "Smooth", "Interpolation", "Interpolation", "Instant").group("Smoothing").build());
         this.add(
            new IntSetting("horizontal-speed", "Horizontal", 40, 1, 100, 1)
               .group("Smoothing")
               .formatter(var0 -> var0 + "%")
               .visibleWhen(() -> usesSmoothing(this))
               .build()
         );
         this.add(
            new IntSetting("vertical-speed", "Vertical", 10, 1, 100, 1)
               .group("Smoothing")
               .formatter(var0 -> var0 + "%")
               .visibleWhen(() -> usesSmoothing(this))
               .build()
         );
         this.add(
            new IntSetting("direction-factor", "Direction Factor", 100, 0, 100, 1)
               .group("Smoothing")
               .formatter(var0 -> var0 + "%")
               .visibleWhen(() -> usesSmoothing(this))
               .build()
         );
         this.add(new DoubleSetting("midpoint", "Midpoint", 0.35, 0.0, 1.0, 0.05).group("Smoothing").visibleWhen(() -> usesSmoothing(this)).build());
      }

      private static boolean usesSmoothing(Module var0) {
         return !"Instant".equals(var0.value("smooth-mode"));
      }

      @Override
      public void onDisable() {
         this.clearAimState();
      }

      @Override
      public void tick() {
         if (MC != null
            && MC.player != null
            && MC.level != null
            && MC.gui.screen() == null
            && !ScaffoldModule.ownsTellyInput()
            && !ScaffoldModule.reservesRageInput()
            && RiptideSilentAim.packetRotation() == null
            && !ScaffoldModule.hasActiveSilentMovementRotation()
            && !AutoTotemModule.operationActive()) {
            LivingEntity var1 = this.selectTarget();
            if (var1 == null) {
               this.clearAimState();
            } else {
               Vec3 var2 = MC.player.getEyePosition();
               Vec3 var3 = this.aimPoint(var2, var1);
               if (var3 == null) {
                  this.clearAimState();
               } else {
                  RiptideRotationUtil.Rotation var4 = RiptideRotationUtil.lookingAt(var3, var2);
                  RiptideRotationUtil.Rotation var5 = RiptideRotationUtil.playerRotation(MC.player);
                  boolean var6 = this.trackedTargetId != var1.getId();
                  if (var6) {
                     this.trackedTargetId = var1.getId();
                     this.switchCandidateId = Integer.MIN_VALUE;
                     this.switchCandidateTicks = 0;
                     this.sampledDirectionChange = 0.0F;
                     this.sampledHorizontalSpeed = this.configuredPercent("horizontal-speed");
                     this.sampledVerticalSpeed = this.configuredPercent("vertical-speed");
                     this.filteredTargetRotation = this.filterTargetRotation(var5, var4, true);
                     this.rotationSmoother.reset((long)var1.getId() << 32 ^ System.nanoTime());
                     RiptideMouseInputSimulator.clear(RiptideMouseInputSimulator.Source.AIM_ASSIST);
                  } else {
                     float var7 = this.lastTargetRotation == null
                        ? 0.0F
                        : RiptideRotationUtil.angleTo(this.lastTargetRotation, var4) / 180.0F * (this.integer("direction-factor") / 100.0F);
                     this.sampledDirectionChange = approach(this.sampledDirectionChange, var7, 0.2F);
                     this.sampledHorizontalSpeed = approach(this.sampledHorizontalSpeed, this.configuredPercent("horizontal-speed"), 0.25F);
                     this.sampledVerticalSpeed = approach(this.sampledVerticalSpeed, this.configuredPercent("vertical-speed"), 0.25F);
                     this.filteredTargetRotation = this.filterTargetRotation(var5, var4, false);
                  }

                  RiptideRotationUtil.Rotation var10 = "Instant".equals(this.choice("smooth-mode")) ? var4 : this.filteredTargetRotation;
                  String var8 = this.choice("axis");
                  if (this.insideSafeZone(var5, var4, var2, var3, var1, var8)) {
                     this.rotationSmoother.halt();
                     RiptideMouseInputSimulator.clear(RiptideMouseInputSimulator.Source.AIM_ASSIST);
                     this.lastTargetRotation = var4;
                  } else {
                     if ("Instant".equals(this.choice("smooth-mode"))) {
                        RiptideRotationUtil.Rotation var9 = new RiptideRotationUtil.Rotation(
                           "Vertical".equals(var8) ? var5.yaw() : var10.yaw(), "Horizontal".equals(var8) ? var5.pitch() : var10.pitch()
                        );
                        RiptideMouseInputSimulator.queueRotation(RiptideMouseInputSimulator.Source.AIM_ASSIST, var5, var9);
                     } else {
                        QuantizedRotationSmoother.Step var11 = this.rotationSmoother
                           .step(
                              RiptideRotationUtil.angleDifference(var10.yaw(), var5.yaw()),
                              RiptideRotationUtil.angleDifference(var10.pitch(), var5.pitch()),
                              RiptideRotationUtil.mouseDegreesPerRawInput(),
                              this.sampledHorizontalSpeed,
                              this.sampledVerticalSpeed,
                              this.sampledDirectionChange,
                              (float)this.decimal("midpoint"),
                              !"Vertical".equals(var8),
                              !"Horizontal".equals(var8)
                           );
                        RiptideMouseInputSimulator.queueRotationCounts(RiptideMouseInputSimulator.Source.AIM_ASSIST, var11.yawCounts(), var11.pitchCounts());
                     }

                     this.lastTargetRotation = var4;
                  }
               }
            }
         } else {
            this.clearAimState();
         }
      }

      private void clearAimState() {
         this.lastTargetRotation = null;
         this.filteredTargetRotation = null;
         this.trackedTargetId = Integer.MIN_VALUE;
         this.switchCandidateId = Integer.MIN_VALUE;
         this.switchCandidateTicks = 0;
         this.rotationSmoother.reset();
         RiptideMouseInputSimulator.clear(RiptideMouseInputSimulator.Source.AIM_ASSIST);
      }

      private LivingEntity selectTarget() {
         double var1 = this.decimal("range") * this.decimal("range");
         RiptideRotationUtil.Rotation var3 = RiptideRotationUtil.playerRotation(MC.player);
         Vec3 var4 = MC.player.getEyePosition();
         String var5 = this.choice("priority");
         LivingEntity var6 = null;
         LivingEntity var7 = null;

         for (Entity var9 : MC.level.entitiesForRendering()) {
            if (var9 instanceof LivingEntity var10 && this.valid(var10, var1, var3)) {
               if (var10.getId() == this.trackedTargetId) {
                  var7 = var10;
               }

               if (var6 == null || this.compareTargets(var10, var6, var5, var3, var4) < 0) {
                  var6 = var10;
               }
            }
         }

         if (var7 == null || var6 == null || var6 == var7) {
            this.switchCandidateId = Integer.MIN_VALUE;
            this.switchCandidateTicks = 0;
            return var7 == null ? var6 : var7;
         } else if (this.compareTargets(var6, var7, var5, var3, var4) >= 0) {
            this.switchCandidateId = Integer.MIN_VALUE;
            this.switchCandidateTicks = 0;
            return var7;
         } else {
            if (this.switchCandidateId == var6.getId()) {
               this.switchCandidateTicks++;
            } else {
               this.switchCandidateId = var6.getId();
               this.switchCandidateTicks = 1;
            }

            if (this.switchCandidateTicks < 4) {
               return var7;
            } else {
               this.switchCandidateId = Integer.MIN_VALUE;
               this.switchCandidateTicks = 0;
               return var6;
            }
         }
      }

      private boolean valid(LivingEntity var1, double var2, RiptideRotationUtil.Rotation var4) {
         if (var1 == MC.player || var1.isRemoved() || !var1.isAlive()) {
            return false;
         } else if (RiptideAntiBot.suppress(var1)) {
            return false;
         } else if (TeamsModule.combatExcluded(var1, "aimassist")) {
            return false;
         } else if (var1.hurtTime > this.integer("hurt-time")) {
            return false;
         } else if (!this.matchesEntity(var1)) {
            return false;
         } else if (var1.distanceToSqr(MC.player) > var2) {
            return false;
         } else {
            RiptideRotationUtil.Rotation var5 = RiptideRotationUtil.lookingAt(var1.getBoundingBox().getCenter(), MC.player.getEyePosition());
            return RiptideRotationUtil.angleTo(var4, var5) <= this.integer("fov");
         }
      }

      private int compareTargets(LivingEntity var1, LivingEntity var2, String var3, RiptideRotationUtil.Rotation var4, Vec3 var5) {
         return switch (var3) {
            case "Type" -> Integer.compare(this.typeWeight(var1), this.typeWeight(var2));
            case "Health" -> Float.compare(var1.getHealth() + var1.getAbsorptionAmount(), var2.getHealth() + var2.getAbsorptionAmount());
            case "Distance" -> Double.compare(var1.distanceToSqr(MC.player), var2.distanceToSqr(MC.player));
            case "HurtTime" -> Integer.compare(var1.hurtTime, var2.hurtTime);
            case "Age" -> Integer.compare(var2.tickCount, var1.tickCount);
            default -> Float.compare(this.crosshairAngle(var1, var4, var5), this.crosshairAngle(var2, var4, var5));
         };
      }

      private int typeWeight(LivingEntity var1) {
         if (var1 instanceof Player) {
            return 0;
         } else {
            return var1 instanceof Enemy ? 1 : 100;
         }
      }

      private float crosshairAngle(LivingEntity var1, RiptideRotationUtil.Rotation var2, Vec3 var3) {
         RiptideRotationUtil.Rotation var4 = RiptideRotationUtil.lookingAt(var1.getBoundingBox().getCenter(), var3);
         return RiptideRotationUtil.angleTo(var2, var4);
      }

      private boolean matchesEntity(Entity var1) {
         String var2 = BuiltInRegistries.ENTITY_TYPE.getKey(var1.getType()).toString().toLowerCase(Locale.ROOT);
         Set var3 = this.cachedEntityIds();
         return var3.contains(var2) || var3.contains(var2.substring(var2.indexOf(58) + 1));
      }

      private Set<String> cachedEntityIds() {
         List var1 = this.list("entities");
         String var2 = String.join("|", var1);
         if (var2.equals(this.cachedEntityListSource)) {
            return this.cachedEntityIds;
         } else {
            LinkedHashSet var3 = new LinkedHashSet();

            for (String var5 : var1) {
               if (var5 != null) {
                  String var6 = var5.trim().toLowerCase(Locale.ROOT);
                  if (!var6.isEmpty()) {
                     var3.add(var6);
                     int var7 = var6.indexOf(58);
                     if (var7 >= 0 && var7 + 1 < var6.length()) {
                        var3.add(var6.substring(var7 + 1));
                     }
                  }
               }
            }

            this.cachedEntityListSource = var2;
            this.cachedEntityIds = Set.copyOf(var3);
            return this.cachedEntityIds;
         }
      }

      private Vec3 aimPoint(Vec3 var1, LivingEntity var2) {
         if (var2 instanceof Player) {
            return this.playerAimPoint(var1, var2);
         } else {
            AABB var3 = var2.getBoundingBox().inflate(var2.getPickRadius());
            double var4 = (var3.minX + var3.maxX) * 0.5;
            double var6 = (var3.minZ + var3.maxZ) * 0.5;
            String var9 = this.choice("target-point");

            Vec3 var8 = switch (var9) {
               case "Center", "Body" -> var3.getCenter();
               case "Head" -> var2.getEyePosition();
               case "Feet" -> new Vec3(var4, var3.minY + 0.1, var6);
               default -> this.nearestPoint(var1, var3);
            };
            return this.visible(var1, var8) ? var8 : this.bestVisiblePoint(var1, var3);
         }
      }

      private Vec3 playerAimPoint(Vec3 var1, LivingEntity var2) {
         AABB var3 = var2.getBoundingBox();
         Vec3 var4 = playerModelPoint(var1, var3, var2.getX(), var2.getEyeY(), var2.getZ(), this.choice("target-point"));
         return this.visible(var1, var4) ? var4 : this.bestVisiblePlayerPoint(var1, var3, var2.getX(), var2.getZ(), var4.y);
      }

      static Vec3 playerModelPoint(Vec3 var0, AABB var1, double var2, double var4, double var6, String var8) {
         double var9 = Math.max(1.0E-9, var1.maxY - var1.minY);
         double var11 = var1.minY + Math.min(0.1, var9 * 0.1);
         double var13 = var1.maxY - Math.min(0.1, var9 * 0.1);
         String var15 = var8 == null ? "nearest" : var8.trim().toLowerCase(Locale.ROOT);

         double var16 = switch (var15) {
            case "center", "body" -> (var1.minY + var1.maxY) * 0.5;
            case "head" -> Mth.clamp(Double.isFinite(var4) ? var4 : var1.maxY, var11, var13);
            case "feet" -> var11;
            default -> Mth.clamp(var0 == null ? (var1.minY + var1.maxY) * 0.5 : var0.y, var11, var13);
         };
         return new Vec3(var2, var16, var6);
      }

      private Vec3 bestVisiblePlayerPoint(Vec3 var1, AABB var2, double var3, double var5, double var7) {
         double var9 = Math.max(0.001, var2.maxY - var2.minY);
         Vec3 var11 = null;
         double var12 = Double.MAX_VALUE;

         for (int var14 = 0; var14 < 9; var14++) {
            double var15 = var2.minY + var9 * ((var14 + 0.5) / 9.0);
            Vec3 var17 = new Vec3(var3, var15, var5);
            if (this.visible(var1, var17)) {
               double var18 = Math.abs(var15 - var7);
               if (var18 < var12) {
                  var12 = var18;
                  var11 = var17;
               }
            }
         }

         return var11;
      }

      private Vec3 nearestPoint(Vec3 var1, AABB var2) {
         Vec3 var3 = new Vec3(Mth.clamp(var1.x, var2.minX, var2.maxX), Mth.clamp(var1.y, var2.minY, var2.maxY), Mth.clamp(var1.z, var2.minZ, var2.maxZ));
         return var3.distanceToSqr(var1) < 1.0E-6 ? var2.getCenter() : var3;
      }

      private Vec3 bestVisiblePoint(Vec3 var1, AABB var2) {
         RiptideRotationUtil.Rotation var3 = RiptideRotationUtil.playerRotation(MC.player);
         Vec3 var4 = null;
         float var5 = Float.MAX_VALUE;

         for (int var6 = 0; var6 <= 2; var6++) {
            double var7 = Mth.lerp(var6 / 2.0, var2.minX, var2.maxX);

            for (int var9 = 0; var9 <= 2; var9++) {
               double var10 = Mth.lerp(var9 / 2.0, var2.minY, var2.maxY);

               for (int var12 = 0; var12 <= 2; var12++) {
                  double var13 = Mth.lerp(var12 / 2.0, var2.minZ, var2.maxZ);
                  Vec3 var15 = new Vec3(var7, var10, var13);
                  if (this.visible(var1, var15)) {
                     float var16 = RiptideRotationUtil.angleTo(var3, RiptideRotationUtil.lookingAt(var15, var1));
                     if (var16 < var5) {
                        var5 = var16;
                        var4 = var15;
                     }
                  }
               }
            }
         }

         return var4;
      }

      private boolean visible(Vec3 var1, Vec3 var2) {
         if (MC.level != null && MC.player != null && var2 != null) {
            BlockHitResult var3 = MC.level.clip(new ClipContext(var1, var2, Block.COLLIDER, Fluid.NONE, MC.player));
            return var3 == null || var3.getType() == Type.MISS || var3.getLocation().distanceToSqr(var2) < 0.05;
         } else {
            return false;
         }
      }

      private boolean insideSafeZone(RiptideRotationUtil.Rotation var1, RiptideRotationUtil.Rotation var2, Vec3 var3, Vec3 var4, LivingEntity var5, String var6) {
         int var7 = this.integer("safe-zone");
         if (var7 <= 0 || var1 == null || var2 == null || var3 == null || var4 == null || var5 == null) {
            return false;
         } else if (!this.crosshairIntersectsTarget(var3, var5)) {
            return false;
         } else {
            AABB var8 = var5.getBoundingBox().inflate(var5.getPickRadius());
            double var9 = Math.max(var8.maxX - var8.minX, var8.maxZ - var8.minZ);
            double var11 = var9 * (var7 / 100.0);
            double var13 = Math.max(1.0E-4, var3.distanceTo(var4));
            double var15 = Math.toDegrees(Math.atan2(var11, var13));
            double var17 = RiptideRotationUtil.mouseDegreesPerRawInput();
            if (var17 > 0.0 && Double.isFinite(var17)) {
               var15 = Math.max(var15, var17);
            }

            boolean var19 = "Vertical".equals(var6) || Math.abs(RiptideRotationUtil.angleDifference(var2.yaw(), var1.yaw())) <= var15;
            boolean var20 = "Horizontal".equals(var6) || Math.abs(RiptideRotationUtil.angleDifference(var2.pitch(), var1.pitch())) <= var15;
            return var19 && var20;
         }
      }

      private boolean crosshairIntersectsTarget(Vec3 var1, LivingEntity var2) {
         if (MC.player != null && var1 != null && var2 != null) {
            AABB var3 = var2.getBoundingBox().inflate(var2.getPickRadius());
            double var4 = Math.max(this.decimal("range"), var1.distanceTo(var3.getCenter()) + var3.getSize());
            Vec3 var6 = var1.add(MC.player.getViewVector(1.0F).scale(var4));
            Optional var7 = var3.clip(var1, var6);
            return var7.isPresent() && this.visible(var1, (Vec3)var7.get());
         } else {
            return false;
         }
      }

      private RiptideRotationUtil.Rotation filterTargetRotation(RiptideRotationUtil.Rotation var1, RiptideRotationUtil.Rotation var2, boolean var3) {
         RiptideRotationUtil.Rotation var4 = !var3 && this.filteredTargetRotation != null ? this.filteredTargetRotation : var1;
         float var5 = var3 ? 0.35F : 0.5F;
         float var6 = Math.abs(RiptideRotationUtil.angleDifference(var2.yaw(), var4.yaw())) * var5;
         float var7 = Math.abs(RiptideRotationUtil.angleDifference(var2.pitch(), var4.pitch())) * var5;
         return RiptideRotationUtil.towardsLinear(var4, var2, var6, var7);
      }

      private float configuredPercent(String var1) {
         return Mth.clamp(this.integer(var1) / 100.0F, 0.01F, 1.0F);
      }

      private static float approach(float var0, float var1, float var2) {
         return var0 + (var1 - var0) * Mth.clamp(var2, 0.0F, 1.0F);
      }
   }

   static final class AutoClickerModule extends Module {
      private static final ValueRange DEFAULT_CPS = new ValueRange(6, 11);
      private final RiptideClickPacer pacer = new RiptideClickPacer();

      AutoClickerModule() {
         super("auto-clicker", "AutoClicker", ModuleCategory.COMBAT, "Auto clicks or holds");
         this.add(new RangeSetting("tps", "CPS", DEFAULT_CPS, 5.0, 20.0, 0.5).minSeparation(0.5).visibleWhen(() -> !"Hold".equals(this.value("mode"))).build());
         this.add(new ChoiceSetting("mode", "Mode", "Constant", "Constant", "Hold Down", "Hold").description("Click or hold").build());
         this.add(new ChoiceSetting("button", "Button", "Left", "Left", "Right").description("Mouse button").build());
      }

      @Override
      public void onEnable() {
         this.pacer.reset();
      }

      @Override
      public void onDisable() {
         this.standDown();
      }

      @Override
      public void tick() {
         if (MC != null
            && MC.player != null
            && MC.level != null
            && MC.gui.screen() == null
            && !ScaffoldModule.ownsTellyInput()
            && !ScaffoldModule.reservesRageInput()
            && RiptideSilentAim.packetRotation() == null
            && !ScaffoldModule.hasActiveSilentMovementRotation()
            && !AutoTotemModule.operationActive()) {
            String var1 = this.choice("mode");
            if ("Hold".equals(var1)) {
               this.pacer.reset();
               this.holdConfiguredButton();
            } else {
               this.releaseHeldButton();
               if ("Hold Down".equals(var1) && !this.configuredButtonDown()) {
                  this.pacer.reset();
               } else {
                  ValueRange var2 = ValueRange.parse(this.value("tps"), DEFAULT_CPS);
                  if (this.pacer.shouldClick(var2.min(), var2.max())) {
                     if ("Right".equals(this.choice("button"))) {
                        RiptideInputClicker.queueUseClick();
                     } else {
                        RiptideInputClicker.queueAttackClick();
                     }
                  }
               }
            }
         } else {
            this.standDown();
         }
      }

      private void standDown() {
         this.pacer.reset();
         this.releaseHeldButton();
      }

      private void holdConfiguredButton() {
         boolean var1 = "Right".equals(this.choice("button"));
         RiptideInputClicker.setAttackHeld(!var1);
         RiptideInputClicker.setUseHeld(var1);
      }

      private void releaseHeldButton() {
         RiptideInputClicker.setAttackHeld(false);
         RiptideInputClicker.setUseHeld(false);
      }

      private boolean configuredButtonDown() {
         if (MC.options == null) {
            return false;
         } else {
            return "Right".equals(this.choice("button"))
               ? RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$isActuallyDown()
               : RiptideKeyMappingBridge.of(MC.options.keyAttack).riptide$isActuallyDown();
         }
      }
   }

   static final class AutoFishModule extends Module {
      private final Random rng = new Random();
      private String cachedSoundListSource = "";
      private Set<String> cachedSoundIds = Set.of();
      private boolean pendingCatch;
      private int catchTimer;
      private boolean pendingRecast;
      private int recastTimer;
      private boolean movementActive;
      private int movementTick;
      private int movementDuration;
      private float movementStartYaw;
      private float movementTargetYaw;
      private float movementQueuedYaw;
      private float movementJitter;
      private float anchorYaw;
      private boolean anchorSet;
      private int stepIndex;
      private int stepDirection = 1;
      private boolean bobberWasBiting;
      private boolean stopMacroStarted;
      private long stopMacroRunId = -1L;
      private boolean stopMacroWarningShown;
      private int rodSwitchWaitTicks;
      private int autoCastTimer;
      private int awaitingHookTicks;
      private boolean virtualCastActive;
      private int virtualCastTimeoutTicks;
      private int ownedUsePacketTicks;
      private int ownedUsePackets;
      private int ownedSlotPacketTicks;
      private int ownedSlotPackets;
      private int manualInputCooldownTicks;
      private boolean screenWasOpen;
      private boolean awaitingReelConfirmation;
      private int reelConfirmationTicks;
      private int reelAttempts;
      private static final int REEL_CONFIRMATION_TICKS = 4;
      private static final int MAX_REEL_ATTEMPTS = 3;

      AutoFishModule() {
         super("auto-fish", "AutoFish", ModuleCategory.PLAYER, "Automatically reels and recasts fishing rods.");
         this.add(new ChoiceSetting("trigger-mode", "Trigger", "Sound", "Sound", "Bobber").build());
         this.add(
            RegistryListSetting.soundEvents("sounds", "Sounds", "minecraft:entity.fishing_bobber.splash")
               .visibleWhen(() -> "Sound".equals(this.value("trigger-mode")))
               .build()
         );
         this.add(
            new BoolSetting("sound-distance", "Sound Distance", true)
               .visibleWhen(() -> "Sound".equals(this.value("trigger-mode")))
               .description("Require sound near player.")
         );
         this.add(
            new DoubleSetting("max-sound-distance", "Max Distance", 12.0, 1.0, 128.0, 0.5)
               .visibleWhen(() -> "Sound".equals(this.value("trigger-mode")) && Boolean.parseBoolean(this.value("sound-distance")))
               .description("Max player sound range.")
               .build()
         );
         this.add(new ChoiceSetting("click-mode", "Click Mode", "Real Input", "Real Input", "Interaction API").build());
         this.add(new BoolSetting("auto-cast", "Auto Cast", true).group("Casting").description("Recast when missing").build());
         this.add(new IntSetting("cast-timeout", "No Bite Retry", 60, 0, 300, 1).group("Casting").description("0 = wait forever.").unit("s"));
         this.add(new BoolSetting("randomize-delays", "Randomize Delays", true).group("Delays").build());
         this.add(
            new IntSetting("catch-min", "Catch Min", 2, 0, 32, 1)
               .group("Delays")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(
            new IntSetting("catch-max", "Catch Max", 5, 0, 40, 1)
               .group("Delays")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(
            new IntSetting("recast-min", "Recast Min", 8, 0, 32, 1)
               .group("Delays")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(
            new IntSetting("recast-max", "Recast Max", 12, 0, 40, 1)
               .group("Delays")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(
            new IntSetting("catch-delay", "Catch Delay", 3, 0, 40, 1)
               .group("Delays")
               .visibleWhen(() -> !Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(
            new IntSetting("recast-delay", "Recast Delay", 10, 0, 40, 1)
               .group("Delays")
               .visibleWhen(() -> !Boolean.parseBoolean(this.value("randomize-delays")))
               .build()
         );
         this.add(new IntSetting("movement-steps", "Steps", 6, 0, 6, 1).group("Movement").build());
         this.add(
            new DoubleSetting("degrees-per-step", "Degrees / Step", 5.0, 0.1, 20.0, 0.1)
               .group("Movement")
               .visibleWhen(() -> !"0".equals(this.value("movement-steps")))
               .build()
         );
         this.add(
            new IntSetting("movement-duration", "Duration", 6, 1, 30, 1).group("Movement").visibleWhen(() -> !"0".equals(this.value("movement-steps"))).build()
         );
         this.add(new BoolSetting("anti-break", "Anti Break", true).group("Rod Safety").build());
         this.add(
            new IntSetting("min-durability", "Min Durability", 2, 1, 32, 1)
               .group("Rod Safety")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("anti-break")))
               .build()
         );
         this.add(new BoolSetting("auto-select-rod", "Auto Select Best Rod", true).group("Rod Safety").build());
         this.add(new BoolSetting("stop-macro-enabled", "Auto Stop", false).group("Auto Stop").description("Silent conditional rule").build());
         this.add(
            new StringSetting("stop-macro", "Rule", "")
               .group("Auto Stop")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("stop-macro-enabled")))
               .conditionalMacroPicker()
         );
         this.add(
            new ChoiceSetting("stop-macro-trigger", "On Trigger", "Stop AutoFish", "Stop AutoFish", "Run Steps Only", "Stop, Run, Resume")
               .group("Auto Stop")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("stop-macro-enabled")))
               .description("Action on condition")
               .build()
         );
      }

      @Override
      protected void onOptionValueChanged(String var1) {
         this.normalizeDelayPairForEditedOption(var1);
         if ("stop-macro".equals(var1)
            || "stop-macro-enabled".equals(var1)
            || "stop-macro-trigger".equals(var1)
            || "anti-break".equals(var1)
            || "min-durability".equals(var1)
            || "auto-select-rod".equals(var1)) {
            this.tuneSelectedAutoFishPresetMacro();
            this.cancelStopMacroIfExternal();
         }
      }

      @Override
      public void onEnable() {
         this.normalizeDelayPairs();
         this.migrateLegacyStopSettings();
         this.tuneSelectedAutoFishPresetMacro();
         this.resetStopMacroSession();
      }

      @Override
      public void onDisable() {
         this.cancelStopMacroIfExternal();
         this.reset();
      }

      @Override
      public void onGameLeft() {
         this.cancelStopMacro();
         this.reset();
      }

      @Override
      public void tick() {
         this.normalizeDelayPairs();
         this.tickOwnedPacketBudgets();
         if (MC != null && MC.player != null && MC.level != null) {
            if (!AutoTotemModule.operationActive() && (RiptideKillAuraRotation.currentOwner() == null || !RiptideKillAuraRotation.hasCurrentRotation())) {
               this.startStopMacroIfNeeded();
               if (MC.gui.screen() != null) {
                  this.pauseForOpenScreen();
               } else {
                  if (this.screenWasOpen) {
                     this.resumeAfterScreenClose();
                  }

                  if (this.manualInputCooldownTicks > 0) {
                     this.manualInputCooldownTicks--;
                     this.tickVirtualCastState(false);
                  } else {
                     BuiltinModules.AutoFishModule.RodDecision var1 = this.updateRodSafety();
                     if (!var1.canProceed()) {
                        this.resetRuntimeActions();
                     } else {
                        this.tickVirtualCastState(true);
                        if (!this.tickReelConfirmation() && !this.tickAutoCast()) {
                           if ("Bobber".equals(this.choice("trigger-mode"))) {
                              FishingHook var2 = this.reliableHook();
                              boolean var3 = var2 != null && ((RiptideFishingHookAccessor)var2).riptide$isBiting();
                              if (var3 && !this.bobberWasBiting) {
                                 this.scheduleCatch();
                              }

                              this.bobberWasBiting = var3;
                           }

                           if (this.pendingCatch) {
                              if (this.catchTimer > 0) {
                                 this.catchTimer--;
                              } else {
                                 boolean var4 = this.isCastActive() && this.performUseClick();
                                 this.pendingCatch = false;
                                 if (var4) {
                                    this.awaitingReelConfirmation = true;
                                    this.reelConfirmationTicks = 4;
                                    this.reelAttempts = 1;
                                 } else {
                                    this.autoCastTimer = Math.max(this.autoCastTimer, 2);
                                 }
                              }
                           }

                           this.tickMovement();
                           if (this.pendingRecast) {
                              if (this.recastTimer > 0) {
                                 this.recastTimer--;
                              }

                              if (this.recastTimer <= 0 && !this.movementActive) {
                                 if (this.performUseClick()) {
                                    this.markCastAttempt();
                                 }

                                 this.pendingRecast = false;
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               this.pendingCatch = false;
               this.catchTimer = 0;
               this.pendingRecast = false;
               this.recastTimer = 0;
               this.clearReelConfirmation();
            }
         } else {
            this.resetRuntimeActions();
         }
      }

      @Override
      public void onRenderLevel(float var1) {
         if (this.movementActive && MC != null && MC.player != null) {
            int var2 = Math.max(1, this.movementDuration);
            float var3 = Mth.clamp((this.movementTick + var1) / var2, 0.0F, 1.0F);
            float var4 = var3 * var3 * (3.0F - 2.0F * var3);
            float var5 = this.movementStartYaw + RiptideRotationUtil.angleDifference(this.movementTargetYaw, this.movementStartYaw) * var4;
            if (var3 < 1.0F) {
               var5 += (float)Math.sin(var3 * Math.PI) * this.movementJitter;
            }

            this.queueMovementYaw(var5);
         }
      }

      @Override
      public void onSoundPacket(ClientboundSoundPacket var1) {
         if ("Sound".equals(this.choice("trigger-mode"))
            && MC != null
            && MC.player != null
            && MC.level != null
            && this.isCastActive()
            && this.matchesSound(var1)) {
            if (this.bool("sound-distance")) {
               double var2 = this.effectiveSoundDistance(var1);
               FishingHook var4 = this.reliableHook();
               Vec3 var5 = var4 == null ? MC.player.position() : var4.position();
               if (var5.distanceToSqr(var1.getX(), var1.getY(), var1.getZ()) > var2 * var2) {
                  return;
               }
            }

            this.scheduleCatch();
         }
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (RiptideHandArbiter.handPacketOwner() != null) {
            return false;
         } else {
            if (var1 instanceof ServerboundUseItemPacket var2) {
               if (this.consumeOwnedUsePacket()) {
                  return false;
               }

               if (this.isRodHand(var2.getHand())) {
                  this.handleManualRodUse();
               }
            } else if (var1 instanceof ServerboundUseItemOnPacket var3) {
               if (this.consumeOwnedUsePacket()) {
                  return false;
               }

               if (this.isRodHand(var3.getHand())) {
                  this.handleManualRodUse();
               }
            } else if (var1 instanceof ServerboundSetCarriedItemPacket var4) {
               if (this.consumeOwnedSlotPacket()) {
                  return false;
               }

               this.handleManualSlotSwitch(var4.getSlot());
            }

            return false;
         }
      }

      private double effectiveSoundDistance(ClientboundSoundPacket var1) {
         double var2 = Math.max(1.0, this.decimal("max-sound-distance"));
         if (var1 == null) {
            return var2;
         } else {
            double var4 = Math.max(1.0, (double)var1.getVolume()) * 16.0;
            return Math.min(var2, var4);
         }
      }

      private void scheduleCatch() {
         if (!this.pendingCatch && !this.pendingRecast && !this.awaitingReelConfirmation) {
            this.pendingCatch = true;
            this.catchTimer = this.nextDelay("catch-delay", "catch-min", "catch-max");
         }
      }

      private int nextDelay(String var1, String var2, String var3) {
         this.normalizeDelayPairs();
         if (!this.bool("randomize-delays")) {
            return Math.max(0, this.integer(var1));
         } else {
            int var4 = Math.max(0, this.integer(var2));
            int var5 = Math.max(var4, this.integer(var3));
            return var4 + this.rng.nextInt(var5 - var4 + 1);
         }
      }

      private boolean matchesSound(ClientboundSoundPacket var1) {
         if (var1 != null && var1.getSound() != null && var1.getSound().value() != null) {
            Identifier var2 = ((SoundEvent)var1.getSound().value()).location();
            if (var2 == null) {
               return false;
            } else {
               String var3 = var2.toString().toLowerCase(Locale.ROOT);
               Set var4 = this.cachedSoundIds();
               return var4.contains(var3) || var4.contains(var3.substring(var3.indexOf(58) + 1));
            }
         } else {
            return false;
         }
      }

      private Set<String> cachedSoundIds() {
         List var1 = this.list("sounds");
         if (var1.isEmpty()) {
            var1 = List.of("minecraft:entity.fishing_bobber.splash");
         }

         String var2 = String.join("|", var1);
         if (var2.equals(this.cachedSoundListSource)) {
            return this.cachedSoundIds;
         } else {
            LinkedHashSet var3 = new LinkedHashSet();

            for (String var5 : var1) {
               if (var5 != null) {
                  String var6 = var5.trim().toLowerCase(Locale.ROOT);
                  if (!var6.isEmpty()) {
                     var3.add(var6);
                     int var7 = var6.indexOf(58);
                     if (var7 >= 0 && var7 + 1 < var6.length()) {
                        var3.add(var6.substring(var7 + 1));
                     }
                  }
               }
            }

            this.cachedSoundListSource = var2;
            this.cachedSoundIds = Set.copyOf(var3);
            return this.cachedSoundIds;
         }
      }

      private boolean hasFishingRod() {
         InteractionHand var1 = this.rodHand();
         return var1 == null ? false : this.isRodSafe(MC.player.getItemInHand(var1));
      }

      private boolean isFishingNow() {
         return this.reliableHook() != null;
      }

      private boolean isCastActive() {
         return this.virtualCastActive || this.isFishingNow();
      }

      private InteractionHand rodHand() {
         if (MC.player == null) {
            return null;
         } else if (MC.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof FishingRodItem) {
            return InteractionHand.MAIN_HAND;
         } else if (RiptideHandArbiter.offhandClaimedByOther(this.id())) {
            return null;
         } else {
            return MC.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof FishingRodItem ? InteractionHand.OFF_HAND : null;
         }
      }

      private boolean isRodHand(InteractionHand var1) {
         return MC.player != null && var1 != null && MC.player.getItemInHand(var1).getItem() instanceof FishingRodItem;
      }

      private boolean performUseClick() {
         if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return false;
         } else if (!this.hasFishingRod()) {
            return false;
         } else {
            this.markOwnedUsePacket();
            if ("Interaction API".equals(this.choice("click-mode"))) {
               InteractionHand var1 = this.rodHand();
               if (var1 == null || MC.gameMode == null || MC.player == null) {
                  return false;
               }

               if (ModuleRegistry.shouldCancelUseExcept(MC.hitResult, var1, this.id())) {
                  return false;
               }

               InteractionResult var2 = MC.gameMode.useItem(MC.player, var1);
               if (var2 != null && var2.consumesAction()) {
                  MC.player.swing(var1);
               }
            } else {
               RiptideInputClicker.queueUseClick();
            }

            return true;
         }
      }

      private boolean tickAutoCast() {
         if (!this.bool("auto-cast")) {
            this.autoCastTimer = 0;
            this.awaitingHookTicks = 0;
            return false;
         } else {
            FishingHook var1 = this.reliableHook();
            if (var1 != null) {
               this.autoCastTimer = 0;
               this.awaitingHookTicks = 0;
               this.virtualCastActive = true;
               return false;
            } else {
               this.bobberWasBiting = false;
               if (this.virtualCastActive) {
                  return false;
               } else if (this.pendingCatch || this.pendingRecast || this.awaitingReelConfirmation || this.movementActive) {
                  return false;
               } else if (this.awaitingHookTicks > 0) {
                  this.awaitingHookTicks--;
                  return true;
               } else if (this.autoCastTimer > 0) {
                  this.autoCastTimer--;
                  return true;
               } else {
                  if (this.performUseClick()) {
                     this.markCastAttempt();
                  } else {
                     this.autoCastTimer = 1;
                  }

                  return true;
               }
            }
         }
      }

      private void markCastAttempt() {
         this.virtualCastActive = true;
         this.virtualCastTimeoutTicks = this.castTimeoutTicks();
         this.awaitingHookTicks = 8;
         this.autoCastTimer = this.nextDelay("recast-delay", "recast-min", "recast-max");
      }

      private void tickVirtualCastState(boolean var1) {
         if (this.virtualCastActive && !this.pendingCatch && !this.pendingRecast && !this.awaitingReelConfirmation && this.castTimeoutTicks() > 0) {
            if (this.virtualCastTimeoutTicks <= 0) {
               this.virtualCastTimeoutTicks = this.castTimeoutTicks();
            }

            this.virtualCastTimeoutTicks--;
            if (this.virtualCastTimeoutTicks <= 0) {
               boolean var2 = this.isFishingNow();
               this.clearVirtualCastState();
               if (!var1 || !this.bool("auto-cast")) {
                  this.autoCastTimer = Math.max(this.autoCastTimer, 2);
               } else if (var2 && this.performUseClick()) {
                  this.pendingRecast = true;
                  this.recastTimer = this.nextDelay("recast-delay", "recast-min", "recast-max");
               } else {
                  this.autoCastTimer = Math.max(this.autoCastTimer, this.nextDelay("recast-delay", "recast-min", "recast-max"));
               }
            }
         }
      }

      private void clearVirtualCastState() {
         this.virtualCastActive = false;
         this.virtualCastTimeoutTicks = 0;
         this.awaitingHookTicks = 0;
      }

      private boolean tickReelConfirmation() {
         if (!this.awaitingReelConfirmation) {
            return false;
         } else if (!this.isFishingNow()) {
            this.awaitingReelConfirmation = false;
            this.reelConfirmationTicks = 0;
            this.reelAttempts = 0;
            this.clearVirtualCastState();
            this.startMovement();
            this.pendingRecast = this.bool("auto-cast");
            this.recastTimer = this.pendingRecast ? this.nextDelay("recast-delay", "recast-min", "recast-max") : 0;
            return false;
         } else if (this.reelConfirmationTicks > 0) {
            this.reelConfirmationTicks--;
            return true;
         } else if (this.reelAttempts < 3 && this.performUseClick()) {
            this.reelAttempts++;
            this.reelConfirmationTicks = 4;
            return true;
         } else {
            this.awaitingReelConfirmation = false;
            this.reelConfirmationTicks = 0;
            this.reelAttempts = 0;
            this.autoCastTimer = Math.max(this.autoCastTimer, 2);
            return false;
         }
      }

      private int castTimeoutTicks() {
         return Math.max(0, this.integer("cast-timeout")) * 20;
      }

      private void handleManualRodUse() {
         if (MC != null && MC.player != null && MC.level != null) {
            this.manualInputCooldownTicks = Math.max(this.manualInputCooldownTicks, 3);
            this.pendingCatch = false;
            this.catchTimer = 0;
            this.movementActive = false;
            this.movementTick = 0;
            this.clearReelConfirmation();
            if (this.isFishingNow()) {
               this.clearVirtualCastState();
               this.pendingRecast = this.bool("auto-cast");
               this.recastTimer = this.pendingRecast ? this.nextDelay("recast-delay", "recast-min", "recast-max") : 0;
               this.autoCastTimer = Math.max(this.autoCastTimer, 2);
            } else {
               this.pendingRecast = false;
               this.recastTimer = 0;
               this.markCastAttempt();
            }
         }
      }

      private void handleManualSlotSwitch(int var1) {
         this.manualInputCooldownTicks = Math.max(this.manualInputCooldownTicks, 8);
         this.pendingCatch = false;
         this.catchTimer = 0;
         this.pendingRecast = false;
         this.recastTimer = 0;
         this.movementActive = false;
         this.movementTick = 0;
         this.clearReelConfirmation();
         this.bobberWasBiting = false;
         if (!this.isFishingNow()) {
            this.clearVirtualCastState();
         }

         this.autoCastTimer = Math.max(this.autoCastTimer, 6);
      }

      private void pauseForOpenScreen() {
         this.screenWasOpen = true;
         this.pendingCatch = false;
         this.catchTimer = 0;
         this.pendingRecast = false;
         this.recastTimer = 0;
         this.movementActive = false;
         this.movementTick = 0;
         this.clearReelConfirmation();
         this.bobberWasBiting = false;
         this.autoCastTimer = Math.max(this.autoCastTimer, 4);
      }

      private void resumeAfterScreenClose() {
         this.screenWasOpen = false;
         this.manualInputCooldownTicks = Math.max(this.manualInputCooldownTicks, 4);
         if (this.isFishingNow()) {
            this.virtualCastActive = true;
            if (this.virtualCastTimeoutTicks <= 0) {
               this.virtualCastTimeoutTicks = this.castTimeoutTicks();
            }
         } else if (this.virtualCastActive) {
            this.clearVirtualCastState();
            this.autoCastTimer = Math.max(this.autoCastTimer, this.nextDelay("recast-delay", "recast-min", "recast-max"));
         }
      }

      private FishingHook reliableHook() {
         if (MC != null && MC.player != null && MC.level != null) {
            FishingHook var1 = MC.player.fishing;
            if (this.isReliableHookEntity(var1)) {
               return var1;
            } else {
               FishingHook var2 = null;

               for (Entity var4 : MC.level.entitiesForRendering()) {
                  if (var4 instanceof FishingHook var5
                     && this.isReliableHookEntity(var5)
                     && (var2 == null || var5.distanceToSqr(MC.player) < var2.distanceToSqr(MC.player))) {
                     var2 = var5;
                  }
               }

               return var2;
            }
         } else {
            return null;
         }
      }

      private boolean isReliableHookEntity(FishingHook var1) {
         if (var1 != null && !var1.isRemoved() && MC.player != null) {
            if (var1.distanceToSqr(MC.player) > 1024.0) {
               return false;
            } else {
               Entity var2 = var1.getOwner();
               return var2 == MC.player || var2 != null && var2.getUUID().equals(MC.player.getUUID());
            }
         } else {
            return false;
         }
      }

      private void markOwnedUsePacket() {
         this.ownedUsePackets = Math.min(this.ownedUsePackets + 2, 4);
         this.ownedUsePacketTicks = 4;
      }

      private boolean consumeOwnedUsePacket() {
         if (this.ownedUsePackets <= 0) {
            return false;
         } else {
            this.ownedUsePackets--;
            return true;
         }
      }

      private void markOwnedSlotPacket() {
         this.ownedSlotPackets = Math.min(this.ownedSlotPackets + 1, 4);
         this.ownedSlotPacketTicks = 4;
      }

      private boolean consumeOwnedSlotPacket() {
         if (this.ownedSlotPackets <= 0) {
            return false;
         } else {
            this.ownedSlotPackets--;
            return true;
         }
      }

      private void tickOwnedPacketBudgets() {
         if (this.ownedUsePacketTicks > 0 && --this.ownedUsePacketTicks <= 0) {
            this.ownedUsePackets = 0;
         }

         if (this.ownedSlotPacketTicks > 0 && --this.ownedSlotPacketTicks <= 0) {
            this.ownedSlotPackets = 0;
         }
      }

      private void startMovement() {
         int var1 = this.integer("movement-steps");
         if (var1 > 0 && MC.player != null) {
            if (!this.anchorSet) {
               this.anchorYaw = MC.player.getYRot();
               this.anchorSet = true;
            }

            if (this.stepDirection == 0) {
               this.stepDirection = 1;
            }

            this.stepIndex = this.stepIndex + this.stepDirection;
            if (this.stepIndex >= var1) {
               this.stepIndex = var1;
               this.stepDirection = -1;
            } else if (this.stepIndex <= -var1) {
               this.stepIndex = -var1;
               this.stepDirection = 1;
            }

            this.movementActive = true;
            this.movementTick = 0;
            this.movementDuration = Math.max(1, this.integer("movement-duration"));
            this.movementStartYaw = MC.player.getYRot();
            this.movementTargetYaw = this.anchorYaw + (float)(this.stepIndex * this.decimal("degrees-per-step"));
            this.movementQueuedYaw = this.movementStartYaw;
            this.movementJitter = (this.rng.nextFloat() - 0.5F) * 0.08F;
         }
      }

      private void tickMovement() {
         if (this.movementActive && MC.player != null) {
            this.movementTick++;
            if (this.movementTick >= Math.max(1, this.movementDuration)) {
               this.queueMovementYaw(this.movementTargetYaw);
               this.movementActive = false;
            }
         }
      }

      private void queueMovementYaw(float var1) {
         float var2 = RiptideRotationUtil.angleDifference(var1, this.movementQueuedYaw);
         this.movementQueuedYaw = var1;
         RiptideMouseInputSimulator.queueRotationDelta(RiptideMouseInputSimulator.Source.AUTO_FISH, var2, 0.0F);
      }

      private void resetRuntimeActions() {
         this.pendingCatch = false;
         this.catchTimer = 0;
         this.pendingRecast = false;
         this.recastTimer = 0;
         this.autoCastTimer = 0;
         this.clearVirtualCastState();
         this.ownedUsePacketTicks = 0;
         this.ownedUsePackets = 0;
         this.ownedSlotPacketTicks = 0;
         this.ownedSlotPackets = 0;
         this.manualInputCooldownTicks = 0;
         this.screenWasOpen = false;
         this.movementActive = false;
         this.movementTick = 0;
         this.bobberWasBiting = false;
         RiptideMouseInputSimulator.clear(RiptideMouseInputSimulator.Source.AUTO_FISH);
         this.clearReelConfirmation();
      }

      private void clearReelConfirmation() {
         this.awaitingReelConfirmation = false;
         this.reelConfirmationTicks = 0;
         this.reelAttempts = 0;
      }

      private void reset() {
         this.resetRuntimeActions();
         this.anchorSet = false;
         this.anchorYaw = 0.0F;
         this.stepIndex = 0;
         this.stepDirection = 1;
         this.rodSwitchWaitTicks = 0;
      }

      private void normalizeDelayPairs() {
         this.normalizeDelayPair("catch-min", "catch-max");
         this.normalizeDelayPair("recast-min", "recast-max");
      }

      private void normalizeDelayPairForEditedOption(String var1) {
         if ("catch-min".equals(var1) || "catch-max".equals(var1)) {
            this.normalizeDelayPair("catch-min", "catch-max", var1);
         } else if ("recast-min".equals(var1) || "recast-max".equals(var1)) {
            this.normalizeDelayPair("recast-min", "recast-max", var1);
         }
      }

      private void normalizeDelayPair(String var1, String var2) {
         this.normalizeDelayPair(var1, var2, "");
      }

      private void normalizeDelayPair(String var1, String var2, String var3) {
         int var4 = this.integer(var1);
         int var5 = this.integer(var2);
         if (var4 > var5) {
            if (var1.equals(var3)) {
               this.setValue(var2, Integer.toString(var4));
            } else {
               this.setValue(var1, Integer.toString(var5));
            }
         }
      }

      private BuiltinModules.AutoFishModule.RodDecision updateRodSafety() {
         if (MC.player == null) {
            return BuiltinModules.AutoFishModule.RodDecision.pause();
         } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return BuiltinModules.AutoFishModule.RodDecision.pause();
         } else {
            BuiltinModules.AutoFishModule.RodChoice var1 = this.findBestRod(true);
            if (var1 == null) {
               return BuiltinModules.AutoFishModule.RodDecision.pause();
            } else {
               if (this.bool("auto-select-rod")) {
                  BuiltinModules.AutoFishModule.RodChoice var2 = this.selectedMainHandRod();
                  if (var2 == null || !var2.safe() || var1.score() > var2.score()) {
                     if (var1.slot() >= 0 && var1.slot() < 9 && !RiptideHandArbiter.slotReserved(var1.slot(), this.id())) {
                        if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                           return BuiltinModules.AutoFishModule.RodDecision.pause();
                        }

                        try {
                           this.markOwnedSlotPacket();
                           RiptideInventoryHelper.selectHotbarSlot(MC, var1.slot());
                           this.rodSwitchWaitTicks = 1;
                        } finally {
                           RiptideHandArbiter.endHandPacketGroup(this.id());
                        }

                        return BuiltinModules.AutoFishModule.RodDecision.pause();
                     }

                     if (var1.slot() >= 9 && var1.slot() < 36 && !RiptideHandArbiter.slotReserved(var1.slot(), this.id())) {
                        int var3 = MC.player.getInventory().getSelectedSlot();
                        if (!RiptideHandArbiter.slotReserved(var3, this.id())) {
                           if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                              return BuiltinModules.AutoFishModule.RodDecision.pause();
                           }

                           try {
                              if (RiptideInventoryHelper.swapInventorySlots(MC, var1.slot(), var3)) {
                                 this.markOwnedSlotPacket();
                                 RiptideInventoryHelper.selectHotbarSlot(MC, var3);
                                 this.rodSwitchWaitTicks = 1;
                                 return BuiltinModules.AutoFishModule.RodDecision.pause();
                              }
                           } finally {
                              RiptideHandArbiter.endHandPacketGroup(this.id());
                           }
                        }
                     }
                  }
               }

               if (this.rodSwitchWaitTicks > 0) {
                  this.rodSwitchWaitTicks--;
                  return BuiltinModules.AutoFishModule.RodDecision.pause();
               } else {
                  return this.hasFishingRod() ? BuiltinModules.AutoFishModule.RodDecision.proceed() : BuiltinModules.AutoFishModule.RodDecision.pause();
               }
            }
         }
      }

      private BuiltinModules.AutoFishModule.RodChoice selectedMainHandRod() {
         if (MC.player == null) {
            return null;
         } else {
            int var1 = MC.player.getInventory().getSelectedSlot();
            return this.rodChoice(var1, MC.player.getInventory().getItem(var1), false);
         }
      }

      private BuiltinModules.AutoFishModule.RodChoice findBestRod(boolean var1) {
         if (MC.player == null) {
            return null;
         } else {
            BuiltinModules.AutoFishModule.RodChoice var2 = null;
            int var3 = MC.player.getInventory().getContainerSize();

            for (int var4 = 0; var4 < Math.min(36, var3); var4++) {
               BuiltinModules.AutoFishModule.RodChoice var5 = this.rodChoice(var4, MC.player.getInventory().getItem(var4), var1);
               if (var5 != null && (var2 == null || var5.score() > var2.score())) {
                  var2 = var5;
               }
            }

            BuiltinModules.AutoFishModule.RodChoice var6 = this.rodChoice(40, MC.player.getOffhandItem(), var1);
            if (var6 != null && (var2 == null || var6.score() > var2.score())) {
               var2 = var6;
            }

            return var2;
         }
      }

      private BuiltinModules.AutoFishModule.RodChoice rodChoice(int var1, ItemStack var2, boolean var3) {
         if (var2 != null && !var2.isEmpty() && var2.getItem() instanceof FishingRodItem) {
            boolean var4 = this.isRodSafe(var2);
            if (var3 && !var4) {
               return null;
            } else {
               int var5 = var2.isDamageableItem() ? Math.max(0, var2.getMaxDamage() - var2.getDamageValue()) : 999;
               int var6 = this.enchantLevel(var2, Enchantments.LUCK_OF_THE_SEA) * 900
                  + this.enchantLevel(var2, Enchantments.LURE) * 900
                  + this.enchantLevel(var2, Enchantments.UNBREAKING) * 200
                  + this.enchantLevel(var2, Enchantments.MENDING) * 100
                  + this.noVanishingBonus(var2) * 50
                  + Math.min(49, var5);
               return new BuiltinModules.AutoFishModule.RodChoice(var1, var6, var4);
            }
         } else {
            return null;
         }
      }

      private boolean isRodSafe(ItemStack var1) {
         if (var1 == null || var1.isEmpty() || !(var1.getItem() instanceof FishingRodItem)) {
            return false;
         } else if (this.bool("anti-break") && var1.isDamageableItem()) {
            int var2 = Math.max(0, var1.getMaxDamage() - var1.getDamageValue());
            return var2 > Math.max(1, this.integer("min-durability"));
         } else {
            return true;
         }
      }

      private int enchantLevel(ItemStack var1, ResourceKey<Enchantment> var2) {
         try {
            if (MC != null && MC.level != null && var1 != null && !var1.isEmpty()) {
               Reference var3 = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(var2);
               return EnchantmentHelper.getItemEnchantmentLevel(var3, var1);
            } else {
               return 0;
            }
         } catch (Throwable var4) {
            return 0;
         }
      }

      private int noVanishingBonus(ItemStack var1) {
         try {
            return EnchantmentHelper.has(var1, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) ? 0 : 1;
         } catch (Throwable var3) {
            return 1;
         }
      }

      private void migrateLegacyStopSettings() {
         if (!this.bool("stop-macro-enabled") && this.value("stop-macro").trim().isEmpty()) {
            if (Boolean.parseBoolean(this.value("stop-inv-slots"))) {
               RiptideMacro var1 = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.FREE_SLOTS);
               if (var1 != null) {
                  this.tuneAutoFishPresetMacro(var1, AutoFishStopMacroFactory.Preset.FREE_SLOTS);
                  this.setValue("stop-macro-enabled", "true");
                  this.setValue("stop-macro", var1.name);
               }
            } else if (Boolean.parseBoolean(this.value("stop-out-of-rods"))) {
               RiptideMacro var2 = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.DURABILITY);
               if (var2 != null) {
                  this.tuneAutoFishPresetMacro(var2, AutoFishStopMacroFactory.Preset.DURABILITY);
                  this.setValue("stop-macro-enabled", "true");
                  this.setValue("stop-macro", var2.name);
               }
            }
         }
      }

      private void tuneAutoFishPresetMacro(RiptideMacro var1, AutoFishStopMacroFactory.Preset var2) {
         if (var1 != null && var1.actions != null && !var1.actions.isEmpty() && var2 != null) {
            if (var2 == AutoFishStopMacroFactory.Preset.FREE_SLOTS && var1.actions.get(0) instanceof WaitFreeSlotsAction var3) {
               int var9 = Math.max(0, Math.min(36, this.integer("min-free-slots")));
               boolean var10 = var3.countMode != WaitFreeSlotsAction.CountMode.FREE_SLOTS
                  || var3.comparison != WaitFreeSlotsAction.Comparison.AT_MOST
                  || var3.slots != var9;
               var3.countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
               var3.comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
               var3.slots = var9;
               if (var10) {
                  RiptideMacroManager.get().save();
               }
            } else if (var2 == AutoFishStopMacroFactory.Preset.DURABILITY && var1.actions.get(0) instanceof WaitDurabilityAction var4) {
               int var8 = Math.max(1, this.integer("min-durability"));
               boolean var6 = var4.targetMode != WaitDurabilityAction.TargetMode.ITEM
                  || !"minecraft:fishing_rod".equals(var4.itemName)
                  || var4.measurement != WaitDurabilityAction.Measurement.REMAINING
                  || var4.comparison != WaitDurabilityAction.Comparison.AT_MOST
                  || var4.value != var8
                  || !var4.useNext;
               var4.targetMode = WaitDurabilityAction.TargetMode.ITEM;
               var4.itemName = "minecraft:fishing_rod";
               var4.measurement = WaitDurabilityAction.Measurement.REMAINING;
               var4.comparison = WaitDurabilityAction.Comparison.AT_MOST;
               var4.value = var8;
               var4.useNext = true;
               if (var6) {
                  RiptideMacroManager.get().save();
               }
            }
         }
      }

      private void startStopMacroIfNeeded() {
         if (this.bool("stop-macro-enabled") && !this.stopMacroStarted) {
            this.stopMacroStarted = true;
            this.tuneSelectedAutoFishPresetMacro();
            String var1 = this.value("stop-macro").trim();
            if (var1.isBlank()) {
               this.warnStopMacroOnce("AutoFish auto stop rule missing.");
            } else {
               RiptideMacro var2 = RiptideMacroManager.get().get(var1);
               if (var2 == null) {
                  this.warnStopMacroOnce("AutoFish auto stop rule not found: " + var1);
               } else if (!AutoFishStopMacroFactory.isValidStopMacro(var2)) {
                  this.warnStopMacroOnce("AutoFish auto stop rule must start with a conditional.");
               } else {
                  RiptideMacro var3 = this.buildAutoStopRuntimeMacro(var2);
                  if (var3 != null && var3.actions != null && !var3.actions.isEmpty()) {
                     var3.regenerateAllPackets();
                     this.stopMacroRunId = MacroExecutor.executeTracked(var3, MacroExecutor.RunOptions.silentBackground());
                     if (this.stopMacroRunId < 0L) {
                        this.warnStopMacroOnce("AutoFish auto stop could not start.");
                     }
                  } else {
                     this.warnStopMacroOnce("AutoFish auto stop could not build a valid rule.");
                  }
               }
            }
         }
      }

      private void tuneSelectedAutoFishPresetMacro() {
         String var1 = this.value("stop-macro").trim();
         AutoFishStopMacroFactory.Preset var2 = AutoFishStopMacroFactory.presetForGeneratedName(var1);
         if (var2 != null) {
            RiptideMacro var3 = RiptideMacroManager.get().get(var1);
            if (var3 != null) {
               this.tuneAutoFishPresetMacro(var3, var2);
            }
         }
      }

      private RiptideMacro buildAutoStopRuntimeMacro(RiptideMacro var1) {
         if (var1 != null && var1.actions != null) {
            RiptideMacro var2 = var1.deepCopy(var1.name);
            var2.keyCode = -1;
            var2.loop = false;
            var2.loopCount = -1;
            int var3 = this.firstEnabledConditionIndex(var2.actions);
            if (var3 < 0) {
               return null;
            } else {
               BuiltinModules.AutoFishModule.AutoStopTrigger var4 = BuiltinModules.AutoFishModule.AutoStopTrigger.from(this.choice("stop-macro-trigger"));
               boolean var5 = var4 != BuiltinModules.AutoFishModule.AutoStopTrigger.RUN_STEPS_ONLY;
               boolean var6 = var5 && var3 + 1 < var2.actions.size() && AutoFishStopMacroFactory.disablesAutoFish(var2.actions.get(var3 + 1));
               boolean var7 = var4 == BuiltinModules.AutoFishModule.AutoStopTrigger.STOP_RUN_RESUME
                  && !this.hasExplicitAutoFishToggleAfter(var2.actions, var3, var6);
               ArrayList var8 = new ArrayList();

               for (int var9 = 0; var9 < var2.actions.size(); var9++) {
                  MacroAction var10 = var2.actions.get(var9);
                  if (var9 == var3) {
                     var8.add(var10);
                     if (var5) {
                        var8.add(new BuiltinModules.AutoFishModule.AutoFishRuntimeToggleAction(this, false));
                     }
                  } else if (!var6 || var9 != var3 + 1) {
                     var8.add(var10);
                  }
               }

               if (var7) {
                  var8.add(new BuiltinModules.AutoFishModule.AutoFishRuntimeToggleAction(this, true));
               }

               var2.actions = var8;
               return var2;
            }
         } else {
            return null;
         }
      }

      private int firstEnabledConditionIndex(List<MacroAction> var1) {
         if (var1 == null) {
            return -1;
         } else {
            for (int var2 = 0; var2 < var1.size(); var2++) {
               MacroAction var3 = (MacroAction)var1.get(var2);
               if (var3 != null && var3.isEnabled()) {
                  return MacroConditionUtil.isWaitConditionAction(var3) ? var2 : -1;
               }
            }

            return -1;
         }
      }

      private boolean hasExplicitAutoFishToggleAfter(List<MacroAction> var1, int var2, boolean var3) {
         if (var1 == null) {
            return false;
         } else {
            for (int var4 = Math.max(0, var2 + 1); var4 < var1.size(); var4++) {
               if (!var3 || var4 != var2 + 1) {
                  MacroAction var5 = (MacroAction)var1.get(var4);
                  if (var5 != null && var5.isEnabled() && AutoFishStopMacroFactory.isAutoFishToggleAction(var5)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      private void warnStopMacroOnce(String var1) {
         if (!this.stopMacroWarningShown) {
            this.stopMacroWarningShown = true;
            RiptideNotifications.warning(var1);
         }
      }

      private void resetStopMacroSession() {
         this.stopMacroStarted = false;
         this.stopMacroWarningShown = false;
         this.stopMacroRunId = -1L;
      }

      private void cancelStopMacroIfExternal() {
         long var1 = this.stopMacroRunId;
         if (var1 >= 0L && MacroExecutor.isRunActive(var1) && MacroExecutor.currentRunId() != var1) {
            MacroExecutor.stopRun(var1);
         }

         this.resetStopMacroSession();
      }

      private void cancelStopMacro() {
         long var1 = this.stopMacroRunId;
         if (var1 >= 0L && MacroExecutor.isRunActive(var1)) {
            MacroExecutor.stopRun(var1);
         }

         this.resetStopMacroSession();
      }

      private static final class AutoFishRuntimeToggleAction implements MacroAction {
         private final BuiltinModules.AutoFishModule owner;
         private final boolean enable;

         private AutoFishRuntimeToggleAction(BuiltinModules.AutoFishModule var1, boolean var2) {
            this.owner = var1;
            this.enable = var2;
         }

         @Override
         public void execute(Minecraft var1) {
            if (this.owner != null) {
               this.owner.setEnabledSilently(this.enable);
            }
         }

         @Override
         public CompoundTag toTag() {
            CompoundTag var1 = new CompoundTag();
            var1.putString("type", MacroActionType.TOGGLE_MODULE.name());
            return var1;
         }

         @Override
         public void fromTag(CompoundTag var1) {
         }

         @Override
         public MacroActionType getType() {
            return MacroActionType.TOGGLE_MODULE;
         }

         @Override
         public String getDisplayName() {
            return this.enable ? "Resume AutoFish" : "Stop AutoFish";
         }

         @Override
         public String getIcon() {
            return "M";
         }
      }

      private static enum AutoStopTrigger {
         STOP_AUTO_FISH,
         RUN_STEPS_ONLY,
         STOP_RUN_RESUME;

         static BuiltinModules.AutoFishModule.AutoStopTrigger from(String var0) {
            String var1 = var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT);
            if ("run steps only".equals(var1)) {
               return RUN_STEPS_ONLY;
            } else {
               return "stop, run, resume".equals(var1) ? STOP_RUN_RESUME : STOP_AUTO_FISH;
            }
         }
      }

      private record RodChoice(int slot, int score, boolean safe) {
      }

      private record RodDecision(boolean canProceed) {
         static BuiltinModules.AutoFishModule.RodDecision proceed() {
            return new BuiltinModules.AutoFishModule.RodDecision(true);
         }

         static BuiltinModules.AutoFishModule.RodDecision pause() {
            return new BuiltinModules.AutoFishModule.RodDecision(false);
         }
      }
   }

   static final class AutoReconnectModule extends Module {
      AutoReconnectModule() {
         super("auto-reconnect", "AutoReconnect", ModuleCategory.MISC, "Reconnects after disconnect.");
         this.add(new DoubleSetting("delay", "Delay Sec", 3.5, 0.0, 60.0, 0.5));
      }
   }

   static final class AutoToolModule extends Module {
      private final Random delayRandom = new Random();
      private BlockPos currentTargetPos;
      private boolean switchedForTarget;
      private long mineStartMs;
      private long switchDueMs;
      private int previousSlot = -1;
      private int switchedToSlot = -1;
      private long lastMineMs;
      private long restoreDueMs = -1L;

      AutoToolModule() {
         super("auto-tool", "AutoTool", ModuleCategory.PLAYER, "Switches to the best tool to mine a block.");
         this.add(new IntSetting("switch-delay-ms", "Switch Delay", 12, 0, 1000, 1).description("Delay before switching.").build());
         this.add(new BoolSetting("consider-inventory", "Consider Inventory", false));
         this.add(new BoolSetting("ignore-durability", "Ignore Durability", true));
         this.add(new BoolSetting("prefer-silk-touch", "Prefer Silk Touch", false));
         this.add(new BoolSetting("require-sneaking", "Require Sneaking", false));
         this.add(new BoolSetting("switch-back", "Switch Back", false));
         this.add(new IntSetting("switch-back-delay-ms", "Restore Delay", 100, 0, 1000, 25).description("Delay before restoring.").build());
      }

      @Override
      public void onDisable() {
         this.previousSlot = -1;
         this.switchedToSlot = -1;
         this.currentTargetPos = null;
         this.switchedForTarget = false;
         this.switchDueMs = 0L;
         this.restoreDueMs = -1L;
      }

      @Override
      public void tick() {
         if (!MultiPilot.isActive()
            && MC.player != null
            && MC.level != null
            && MC.gameMode != null
            && !AutoTotemModule.operationActive()
            && MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var1) {
            boolean var6 = var1.riptide$isDestroying();
            if (var6) {
               this.restoreDueMs = -1L;
               if (this.bool("require-sneaking") && !MC.player.isShiftKeyDown()) {
                  return;
               }

               BlockPos var3 = var1.riptide$getDestroyBlockPos();
               if (var3 == null) {
                  return;
               }

               BlockState var4 = MC.level.getBlockState(var3);
               if (var4.isAir()) {
                  return;
               }

               this.lastMineMs = nowMs();
               if (!var3.equals(this.currentTargetPos)) {
                  this.currentTargetPos = var3;
                  this.switchedForTarget = false;
                  this.mineStartMs = nowMs();
                  this.switchDueMs = this.mineStartMs + this.sampledDelayMs(this.integer("switch-delay-ms"));
                  if (this.previousSlot < 0) {
                     this.previousSlot = MC.player.getInventory().getSelectedSlot();
                  }
               }

               if (!this.switchedForTarget && nowMs() >= this.switchDueMs) {
                  int var5 = this.resolveToolHotbarSlot(var4);
                  if (var5 >= 0) {
                     this.selectHotbarWithRealKey(var5, true);
                  }
               }
            } else if (this.bool("switch-back")) {
               if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
                  this.restoreDueMs = -1L;
               }

               if (this.previousSlot >= 0 && this.restoreDueMs < 0L) {
                  this.restoreDueMs = this.lastMineMs + this.sampledDelayMs(this.integer("switch-back-delay-ms"));
               }

               if (this.previousSlot >= 0 && nowMs() >= this.restoreDueMs) {
                  this.selectHotbarWithRealKey(this.previousSlot, false);
                  this.currentTargetPos = null;
                  this.switchedForTarget = false;
                  this.switchDueMs = 0L;
                  this.restoreDueMs = -1L;
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
               }
            } else {
               this.previousSlot = -1;
               this.switchedToSlot = -1;
               this.currentTargetPos = null;
               this.switchedForTarget = false;
               this.switchDueMs = 0L;
               this.restoreDueMs = -1L;
            }
         }
      }

      private void selectHotbarWithRealKey(int var1, boolean var2) {
         if (MC.player != null) {
            int var3 = Math.max(0, Math.min(8, var1));
            if (MC.player.getInventory().getSelectedSlot() == var3) {
               this.switchedForTarget = true;
               if (var2) {
                  this.switchedToSlot = var3;
               }
            } else if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
               try {
                  RiptideInputClicker.queueHotbarSlot(var3);
                  if (var2) {
                     this.switchedToSlot = var3;
                  }
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }
            }
         }
      }

      private int resolveToolHotbarSlot(BlockState var1) {
         if (MC.player != null && var1 != null) {
            int var2 = this.bool("consider-inventory") ? 36 : 9;
            int var3 = -1;
            if (this.bool("prefer-silk-touch")) {
               var3 = RiptideAutoTool.bestToolSlot(MC, var1, var2, this.bool("ignore-durability"), true);
            }

            if (var3 < 0) {
               var3 = RiptideAutoTool.bestToolSlot(MC, var1, var2, this.bool("ignore-durability"), false);
            }

            if (var3 < 0) {
               return -1;
            } else if (RiptideHandArbiter.slotReserved(var3, this.id())) {
               return -1;
            } else {
               int var4 = MC.player.getInventory().getSelectedSlot();
               if (var3 < 9) {
                  return !this.isToolBetterThanSelectedSlot(var1, var3, var4) ? var4 : var3;
               } else {
                  int var5 = MC.player.getInventory().getSelectedSlot();
                  if (!this.bool("consider-inventory")) {
                     return -1;
                  } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     return -1;
                  } else {
                     try {
                        if (!RiptideInventoryHelper.swapInventorySlots(MC, var3, var5)) {
                           return -1;
                        }
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }

                     return var5;
                  }
               }
            }
         } else {
            return -1;
         }
      }

      private boolean isToolBetterThanSelectedSlot(BlockState var1, int var2, int var3) {
         if (MC.player == null) {
            return false;
         } else if (var2 == var3) {
            return true;
         } else {
            ItemStack var4 = MC.player.getInventory().getItem(var2);
            ItemStack var5 = MC.player.getInventory().getItem(Math.max(0, Math.min(8, var3)));
            float var6 = RiptideAutoTool.destroySpeed(MC, var4, var1);
            float var7 = RiptideAutoTool.destroySpeed(MC, var5, var1);
            return this.bool("prefer-silk-touch") && var6 > 1.0F && RiptideAutoTool.hasSilkTouch(MC, var4)
               ? !(var7 > 1.0F) || !RiptideAutoTool.hasSilkTouch(MC, var5)
               : var6 > var7;
         }
      }

      private int sampledDelayMs(int var1) {
         if (var1 <= 0) {
            return 0;
         } else {
            int var2 = Math.max(15, Math.min(180, Math.round(var1 * 0.55F)));
            int var3 = this.delayRandom.nextInt(var2 + 1);
            if (this.delayRandom.nextInt(12) == 0) {
               var3 += 15 + this.delayRandom.nextInt(Math.max(16, var2 / 2 + 1));
            }

            return Math.min(1000, var1 + var3);
         }
      }

      private static long nowMs() {
         return System.currentTimeMillis();
      }
   }

   static final class BetterTooltipsModule extends Module {
      private ItemStack tooltipSizeCacheStack = ItemStack.EMPTY;
      private int tooltipSizeCacheBytes = -1;

      BetterTooltipsModule() {
         super("better-tooltips", "BetterTooltips", ModuleCategory.RENDER, "Adds item tooltip info.");
         boolean var1 = !RiptideConfig.getGlobal().modules.containsKey("better-tooltips");
         this.add(new StringSetting("nbt-shortcut", "NBT Shortcut", "Ctrl+Shift+Right-click").group("Info").readonlySummary());
         this.add(new BoolSetting("block-nbt-inspect", "Block NBT Inspect", false).group("Info").description("Ctrl+Shift+Right-click blocks.").build());
         this.add(new BoolSetting("open-contents", "Open Contents", true).group("Containers").description("Preview only.").visibleWhen(() -> false).build());
         this.add(new BoolSetting("containers", "Containers", true).group("Containers"));
         this.add(new BoolSetting("compact-shulker-tooltip", "Compact Shulker", true).group("Containers").description("Compact counts.").build());
         this.add(new BoolSetting("echests", "Ender Chests", true).group("Containers").description("Server-side data.").visibleWhen(() -> false).build());
         this.add(new BoolSetting("maps", "Maps", true).group("Items"));
         this.add(
            new DoubleSetting("map-scale", "Map Scale", 1.0, 0.5, 4.0, 0.1).group("Items").description("Map preview scale.").visibleWhen(() -> false).build()
         );
         this.add(new BoolSetting("books", "Books", true).group("Items"));
         this.add(new BoolSetting("banners", "Banners", true).group("Items"));
         this.add(new BoolSetting("buckets", "Buckets", true).group("Items"));
         this.add(new BoolSetting("bundles", "Bundles", true).group("Items"));
         this.add(new BoolSetting("minecraft-name", "Minecraft Name", true).group("Info"));
         this.add(new BoolSetting("durability", "Durability", true).group("Info"));
         this.add(new BoolSetting("food-info", "Food Info", true).group("Info"));
         this.add(new BoolSetting("byte-size", "Byte Size", true).group("Info"));
         this.add(new ChoiceSetting("byte-size-format", "Byte Format", "Both", "Raw", "Rounded", "Both").group("Info").build());
         if (var1) {
            this.setEnabledSilently(true);
         }
      }

      @Override
      public void appendTooltip(ItemStack var1, List<?> var2) {
         if (var1 != null && !var1.isEmpty()) {
            var2.add(Component.literal("NBT: Ctrl+Shift+Right-click").withStyle(ChatFormatting.DARK_GRAY));
            if (this.bool("minecraft-name")) {
               var2.add(Component.literal(BuiltInRegistries.ITEM.getKey(var1.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            }

            if (this.bool("durability") && var1.isDamageableItem()) {
               int var4 = Math.max(1, var1.getMaxDamage());
               int var5 = Math.max(0, var4 - var1.getDamageValue());
               var2.add(Component.literal("Durability: " + var5 + " / " + var4).withStyle(ChatFormatting.GRAY));
            }

            if (this.bool("food-info") && var1.has(DataComponents.FOOD)) {
               FoodProperties var7 = (FoodProperties)var1.get(DataComponents.FOOD);
               if (var7 != null) {
                  var2.add(
                     Component.literal("Food: " + var7.nutrition() + " / " + String.format(Locale.ROOT, "%.1f", var7.saturation()))
                        .withStyle(ChatFormatting.GRAY)
                  );
               }
            }

            if (this.bool("containers") && var1.has(DataComponents.CONTAINER)) {
               ItemContainerContents var8 = (ItemContainerContents)var1.get(DataComponents.CONTAINER);
               if (var8 != null) {
                  long var13 = var8.nonEmptyItemCopyStream().count();
                  var2.add(Component.literal("Container: " + var13 + " stacks").withStyle(ChatFormatting.DARK_GRAY));
               }
            }

            if (this.bool("bundles") && var1.has(DataComponents.BUNDLE_CONTENTS)) {
               BundleContents var9 = (BundleContents)var1.get(DataComponents.BUNDLE_CONTENTS);
               if (var9 != null) {
                  var2.add(Component.literal("Bundle: " + var9.size() + " stacks").withStyle(ChatFormatting.DARK_GRAY));
               }
            }

            if (this.bool("maps") && var1.has(DataComponents.MAP_ID)) {
               var2.add(Component.literal("Map: " + var1.get(DataComponents.MAP_ID)).withStyle(ChatFormatting.DARK_GRAY));
            }

            if (this.bool("banners") && var1.has(DataComponents.BANNER_PATTERNS)) {
               var2.add(Component.literal("Banner patterns: " + var1.get(DataComponents.BANNER_PATTERNS)).withStyle(ChatFormatting.DARK_GRAY));
            }

            if (this.bool("buckets") && var1.has(DataComponents.BUCKET_ENTITY_DATA)) {
               var2.add(Component.literal("Bucket entity data").withStyle(ChatFormatting.DARK_GRAY));
            }

            if (this.bool("books") && var1.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
               WrittenBookContent var11 = (WrittenBookContent)var1.get(DataComponents.WRITTEN_BOOK_CONTENT);
               if (var11 != null) {
                  var2.add(Component.literal("Pages: " + var11.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
               }
            } else if (this.bool("books") && var1.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
               WritableBookContent var10 = (WritableBookContent)var1.get(DataComponents.WRITABLE_BOOK_CONTENT);
               if (var10 != null) {
                  var2.add(Component.literal("Pages: " + var10.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
               }
            }

            if (this.bool("byte-size")) {
               int var12 = this.getTooltipSizeBytes(var1);
               if (var12 >= 0) {
                  var2.add(Component.literal("Bytes: " + this.formatBytes(var12)).withStyle(ChatFormatting.DARK_GRAY));
               } else {
                  var2.add(Component.literal("Bytes: unreadable (protected)").withStyle(ChatFormatting.DARK_GRAY));
               }
            }
         }
      }

      private String formatBytes(int var1) {
         String var2 = this.choice("byte-size-format");
         double var3 = var1 / 1024.0;

         return switch (var2) {
            case "Raw" -> Integer.toString(var1);
            case "Rounded" -> String.format(Locale.ROOT, "%.1f KiB", var3);
            default -> var1 + " (" + String.format(Locale.ROOT, "%.1f KiB", var3) + ")";
         };
      }

      private int getTooltipSizeBytes(ItemStack var1) {
         if (!this.tooltipSizeCacheStack.isEmpty()
            && this.tooltipSizeCacheStack.getCount() == var1.getCount()
            && ItemStack.isSameItemSameComponents(this.tooltipSizeCacheStack, var1)) {
            return this.tooltipSizeCacheBytes;
         } else if (MC != null && MC.player != null) {
            int var2 = RiptideItemNbtSanity.encodedSizeBytes(var1, MC.player.registryAccess());
            this.tooltipSizeCacheStack = var1.copy();
            this.tooltipSizeCacheBytes = var2;
            return var2;
         } else {
            return -1;
         }
      }
   }

   static final class BlockEspModule extends Module {
      BlockEspModule() {
         super("block-esp", "BlockESP", ModuleCategory.RENDER, "Highlights selected blocks.");
         this.add(new IntSetting("max-targets", "Max Blocks", 1024, 64, 8192, 64).group("General").description("Max rendered blocks"));
         this.add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
         this.add(new BoolSetting("meshing", "Meshing", true).group("General").description("Merge adjacent targets."));
         this.add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each target"));
         this.add(RegistryListSetting.blocks("blocks", "Blocks", "minecraft:diamond_ore").group("Blocks").description("Blocks to highlight."));
         this.add(new BoolSetting("barriers", "Barriers", false).group("Blocks").description("Show invisible barrier blocks."));
         this.add(new ColorSetting("color", "Color", -855688389).group("Colors"));
      }
   }

   static final class BookBotModule extends Module {
      private int timer;
      private int count;
      private final Random random = new Random();

      BookBotModule() {
         super("bookbot", "BookBot", ModuleCategory.MISC, "Writes into books.");
         this.add(new ChoiceSetting("mode", "Mode", "Random", "Random", "File").description("Random or file text.").build());
         this.add(
            new ChoiceSetting("random-type", "Random Type", "Utf8", "Utf8", "Ascii", "PaperMC").visibleWhen(() -> "Random".equals(this.value("mode"))).build()
         );
         this.add(
            new IntSetting("pages", "Pages", 100, 1, 100, 1)
               .visibleWhen(() -> "Random".equals(this.value("mode")) && !"PaperMC".equals(this.value("random-type")))
               .build()
         );
         this.add(
            new IntSetting("characters", "Chars/Page", 1024, 1, 1024, 16)
               .visibleWhen(() -> "Random".equals(this.value("mode")) && !"PaperMC".equals(this.value("random-type")))
               .build()
         );
         this.add(new IntSetting("delay", "Delay", 20, 1, 200, 1));
         this.add(new BoolSetting("sign", "Sign", true));
         this.add(new StringSetting("name", "Name", "Riptide").visibleWhen(() -> Boolean.parseBoolean(this.value("sign"))).build());
         this.add(new BoolSetting("append-count", "Append Count", true).visibleWhen(() -> Boolean.parseBoolean(this.value("sign"))).build());
         this.add(new BoolSetting("word-wrap", "Word Wrap", true).visibleWhen(() -> "File".equals(this.value("mode"))).build());
         this.add(
            new StringSetting("file-path", "File", "")
               .visibleWhen(() -> "File".equals(this.value("mode")))
               .formatter(BuiltinModules.BookBotModule::displayFileName)
               .description("Selected file.")
               .filePicker("pick-file")
         );
         this.add(
            new ActionSetting("pick-file", "Pick File", this::pickFile).availableOffline().visibleWhen(() -> false).description("Choose text file.").build()
         );
         this.add(new StringSetting("text", "Text", "").visibleWhen(() -> false).build());
         this.add(new BoolSetting("full-default-migrated", "Full Default Migrated", false).visibleWhen(() -> false).build());
      }

      @Override
      public void onEnable() {
         this.upgradeOldLightweightDefaults();
         this.timer = this.integer("delay");
         this.count = 0;
      }

      @Override
      public void tick() {
         if (MC.player != null && MC.getConnection() != null) {
            if ("File".equals(this.choice("mode"))) {
               String var1 = this.fileSelectionFailureMessage();
               if (var1 != null) {
                  this.disableWithToggleMessage(var1);
                  return;
               }
            }

            if (this.timer-- <= 0) {
               this.timer = this.integer("delay");
               if (!this.prepareWritableBookInHand()) {
                  this.disableWithToggleMessage("BookBot disabled: no empty writable book.");
               } else {
                  ItemStack var8 = MC.player.getMainHandItem();
                  List var2 = this.buildPages();
                  ArrayList var3 = new ArrayList();

                  for (String var5 : var2) {
                     var3.add(Filterable.passThrough(Component.literal(var5)));
                  }

                  String var9 = this.text("name").isBlank() ? "Riptide" : this.text("name");
                  if (this.bool("append-count") && this.count > 0) {
                     var9 = var9 + " #" + this.count;
                  }

                  if (this.bool("sign")) {
                     var8.set(
                        DataComponents.WRITTEN_BOOK_CONTENT,
                        new WrittenBookContent(Filterable.passThrough(var9), MC.player.getGameProfile().name(), 0, var3, true)
                     );
                  } else {
                     ArrayList var10 = new ArrayList();

                     for (String var7 : var2) {
                        var10.add(Filterable.passThrough(var7));
                     }

                     var8.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(var10));
                  }

                  MC.getConnection()
                     .send(
                        new ServerboundEditBookPacket(
                           MC.player.getInventory().getSelectedSlot(), var2, this.bool("sign") ? Optional.of(var9) : Optional.empty()
                        )
                     );
                  this.count++;
               }
            }
         }
      }

      private boolean prepareWritableBookInHand() {
         if (MC.player == null) {
            return false;
         } else if (this.isEmptyWritableBook(MC.player.getMainHandItem())) {
            return true;
         } else {
            int var1 = this.findEmptyWritableBookSlot();
            if (var1 < 0) {
               return false;
            } else {
               int var2 = MC.player.getInventory().getSelectedSlot();
               if (var1 > 8) {
                  RiptideInventoryHelper.swapInventorySlots(MC, var1, var2);
                  return true;
               } else {
                  RiptideInventoryHelper.selectHotbarSlot(MC, var1);
                  return true;
               }
            }
         }
      }

      private int findEmptyWritableBookSlot() {
         if (MC.player == null) {
            return -1;
         } else {
            for (int var1 = 0; var1 < MC.player.getInventory().getContainerSize(); var1++) {
               if (this.isEmptyWritableBook(MC.player.getInventory().getItem(var1))) {
                  return var1;
               }
            }

            return -1;
         }
      }

      private boolean isEmptyWritableBook(ItemStack var1) {
         if (var1 != null && var1.is(Items.WRITABLE_BOOK)) {
            WritableBookContent var2 = (WritableBookContent)var1.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return var2 == null || var2.pages().isEmpty();
         } else {
            return false;
         }
      }

      private List<String> buildPages() {
         ArrayList var1 = new ArrayList();
         String var2 = "File".equals(this.choice("mode")) ? this.fileText() : "";
         boolean var3 = "Random".equals(this.choice("mode"));
         int var4 = "File".equals(this.choice("mode")) ? 100 : Math.min(100, Math.max(1, this.integer("pages")));
         if (var3) {
            return RiptideBookPayloadBuilder.randomPages(var4, this.integer("characters"), this.choice("random-type"), this.random);
         } else {
            int var5 = this.integer("characters");
            if (!var2.isBlank() && this.bool("word-wrap")) {
               var1.addAll(NbtBookAction.splitTextIntoPages(MC.font, var2, var4));
               return (List<String>)(var1.isEmpty() ? List.of("Riptide") : var1);
            } else {
               var1.addAll(NbtBookAction.chunkTextIntoPages(var2, var4, var5));
               return (List<String>)(var1.isEmpty() ? List.of("Riptide") : var1);
            }
         }
      }

      private void upgradeOldLightweightDefaults() {
         if ("Random".equals(this.choice("mode")) && !this.bool("full-default-migrated")) {
            if (this.integer("pages") == 50) {
               this.setValue("pages", "100");
            }

            if (this.integer("characters") == 128 || this.integer("characters") == 512) {
               this.setValue("characters", Integer.toString(1024));
            }

            if ("Ascii".equals(this.choice("random-type"))) {
               this.setValue("random-type", "Utf8");
            }

            this.setValue("full-default-migrated", "true");
         }
      }

      private String fileText() {
         String var1 = this.text("file-path").trim();
         if (var1.isBlank()) {
            return "";
         } else {
            try {
               return RiptideBookFileReader.read(Path.of(var1));
            } catch (Exception var3) {
               RiptideClientMessaging.sendPrefixed("BookBot file read failed: " + var3.getMessage());
               return "";
            }
         }
      }

      private void pickFile() {
         String var1 = this.text("file-path").trim();
         PointerBuffer var2 = BufferUtils.createPointerBuffer(4);
         ByteBuffer var3 = MemoryUtil.memASCII("*.txt");
         ByteBuffer var4 = MemoryUtil.memASCII("*.md");
         ByteBuffer var5 = MemoryUtil.memASCII("*.json");
         ByteBuffer var6 = MemoryUtil.memASCII("*.nbt.txt");
         var2.put(var3).put(var4).put(var5).put(var6).rewind();

         try {
            String var7 = TinyFileDialogs.tinyfd_openFileDialog("BookBot Text File", var1.isBlank() ? null : var1, var2, "Text files", false);
            if (var7 != null && !var7.isBlank()) {
               this.setValue("file-path", var7);
            }
         } finally {
            MemoryUtil.memFree(var3);
            MemoryUtil.memFree(var4);
            MemoryUtil.memFree(var5);
            MemoryUtil.memFree(var6);
         }
      }

      private String fileSelectionFailureMessage() {
         String var1 = this.text("file-path").trim();
         if (var1.isBlank()) {
            return "BookBot disabled: choose a file first.";
         } else {
            try {
               Path var2 = Path.of(var1);
               if (!Files.isRegularFile(var2)) {
                  return "BookBot disabled: file missing.";
               } else {
                  return Files.size(var2) <= 0L ? "BookBot disabled: file is empty." : null;
               }
            } catch (Exception var3) {
               return "BookBot disabled: invalid file.";
            }
         }
      }

      private static String displayFileName(String var0) {
         if (var0 != null && !var0.isBlank()) {
            try {
               Path var1 = Paths.get(var0).getFileName();
               return var1 == null ? var0 : var1.toString();
            } catch (Exception var2) {
               return var0;
            }
         } else {
            return "No file selected";
         }
      }
   }

   static final class EspModule extends Module {
      EspModule() {
         super("esp", "ESP", ModuleCategory.RENDER, "Highlights entities.");
         this.add(new ChoiceSetting("mode", "Mode", "2D", "Shader", "2D").description("Entity highlight style.").build());
         this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").group("General").build());
         this.add(new BoolSetting("fill", "Fill", false).group("General").visibleWhen(() -> "2D".equals(this.choice("mode"))).build());
         this.add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General"));
         this.add(new ColorSetting("players-color", "Players", -855638017).group("Colors"));
         this.add(new ColorSetting("animals-color", "Animals", -864747633).group("Colors"));
         this.add(new ColorSetting("water-animals-color", "Water Animals", -865674753).group("Colors"));
         this.add(new ColorSetting("monsters-color", "Monsters", -855684534).group("Colors"));
         this.add(new ColorSetting("ambient-color", "Ambient", -860386049).group("Colors"));
         this.add(new ColorSetting("misc-color", "Misc", -858993460).group("Colors"));
         this.add(new BoolSetting("skeleton", "Skeleton", false).group("Skeleton").description("Draw animated skeletons.").build());
         this.add(
            new ChoiceSetting("skeleton-color-mode", "Color Mode", "Static", "Static", "Distance")
               .group("Skeleton")
               .visibleWhen(() -> this.bool("skeleton"))
               .description("Skeleton color source.")
               .build()
         );
         this.add(
            new ColorSetting("skeleton-color", "Color", -1)
               .group("Skeleton")
               .visibleWhen(() -> this.bool("skeleton") && "Static".equals(this.value("skeleton-color-mode")))
         );
         this.add(
            new DoubleSetting("skeleton-width", "Line Width", 2.0, 1.0, 6.0, 0.25)
               .group("Skeleton")
               .visibleWhen(() -> this.bool("skeleton"))
               .description("Skeleton line thickness.")
               .build()
         );
      }
   }

   static final class FakeCoordsModule extends Module {
      FakeCoordsModule() {
         super("fake-coords", "FakeCoords", ModuleCategory.RENDER, "Spoofs displayed coordinates only.");
         this.add(
            new ChoiceSetting("mode", "Mode", "Offset", "Offset", "Scaled", "Rotated", "Scrambled", "Frozen", "Custom")
               .description("Choose spoofing mode.")
               .build()
         );
         this.add(new BoolSetting("fake-y", "Fake Y", false).description("Spoof vertical coordinate.").build());
         this.add(
            new DoubleSetting("custom-x", "Custom X", 0.0, -3.0E7, 3.0E7, 1.0)
               .description("Set displayed X.")
               .numericTextField()
               .visibleWhen(() -> "Custom".equals(this.choice("mode")))
               .build()
         );
         this.add(
            new DoubleSetting("custom-y", "Custom Y", 64.0, -3.0E7, 3.0E7, 1.0)
               .description("Set displayed Y.")
               .numericTextField()
               .visibleWhen(() -> "Custom".equals(this.choice("mode")))
               .build()
         );
         this.add(
            new DoubleSetting("custom-z", "Custom Z", 0.0, -3.0E7, 3.0E7, 1.0)
               .description("Set displayed Z.")
               .numericTextField()
               .visibleWhen(() -> "Custom".equals(this.choice("mode")))
               .build()
         );
         this.add(
            new ActionSetting("anchor", "Anchor", this::captureCurrentPosition)
               .buttonLabel("Capture Position")
               .description("Use current position.")
               .visibleWhen(() -> "Custom".equals(this.choice("mode")))
               .build()
         );
      }

      @Override
      public boolean showInArrayList() {
         return false;
      }

      @Override
      public void onEnable() {
         RiptideFakeCoords.Mode var1 = this.mode();
         RiptideFakeCoords.enable(var1, this.bool("fake-y"));
         if (var1 == RiptideFakeCoords.Mode.CUSTOM) {
            this.pushCustom();
         }
      }

      @Override
      public void onDisable() {
         RiptideFakeCoords.disable();
      }

      @Override
      public void tick() {
         RiptideFakeCoords.Mode var1 = this.mode();
         RiptideFakeCoords.setMode(var1);
         RiptideFakeCoords.setFakeY(this.bool("fake-y"));
         if (var1 == RiptideFakeCoords.Mode.CUSTOM) {
            this.pushCustom();
         }
      }

      private void pushCustom() {
         RiptideFakeCoords.setCustom(this.decimal("custom-x"), this.decimal("custom-y"), this.decimal("custom-z"));
      }

      private void captureCurrentPosition() {
         if (MC.player != null) {
            this.setValue("custom-x", Double.toString(MC.player.getX()));
            this.setValue("custom-y", Double.toString(MC.player.getY()));
            this.setValue("custom-z", Double.toString(MC.player.getZ()));
            this.pushCustom();
         }
      }

      private RiptideFakeCoords.Mode mode() {
         String var1 = this.choice("mode");

         return switch (var1) {
            case "Scaled" -> RiptideFakeCoords.Mode.SCALED;
            case "Rotated" -> RiptideFakeCoords.Mode.ROTATED;
            case "Scrambled" -> RiptideFakeCoords.Mode.SCRAMBLED;
            case "Frozen" -> RiptideFakeCoords.Mode.FROZEN;
            case "Custom" -> RiptideFakeCoords.Mode.CUSTOM;
            default -> RiptideFakeCoords.Mode.OFFSET;
         };
      }
   }

   static final class FastBreakModule extends Module {
      private final Random fastBreakRandom = new Random();
      private BlockPos lastFastBreakPos;
      private boolean fastBreakActivated;

      FastBreakModule() {
         super("fast-break", "FastBreak", ModuleCategory.PLAYER, "Speeds up block breaking.");
         this.add(new ChoiceSetting("mode", "Mode", "Damage", "Normal", "Haste", "Damage", "Packet").description("Break method.").build());
         this.add(
            RegistryListSetting.blocks("blocks", "Blocks", "").visibleWhen(() -> !"Haste".equals(this.value("mode"))).description("Selected blocks.").build()
         );
         this.add(
            new ChoiceSetting("blocks-filter", "Blocks Filter", "Blacklist", "Whitelist", "Blacklist")
               .visibleWhen(() -> !"Haste".equals(this.value("mode")))
               .description("List mode.")
               .build()
         );
         this.add(
            new DoubleSetting("modifier", "Modifier", 1.4, 0.0, 999.0, 0.1)
               .sliderRange(0.0, 10.0)
               .visibleWhen(() -> normalMode(this))
               .description("Speed multiplier.")
               .build()
         );
         this.add(
            new IntSetting("haste-amplifier", "Haste Amplifier", 2, 1, 10, 1)
               .visibleWhen(() -> hasteMode(this))
               .description("Haste amplifier to apply.")
               .build()
         );
         this.add(new BoolSetting("instamine", "Instamine", true).visibleWhen(() -> damageMode(this)).description("Finish blocks early.").build());
         this.add(new BoolSetting("grim-bypass", "Grim Bypass", false).visibleWhen(() -> damageMode(this)).description("Abort after stop.").build());
         this.add(
            new IntSetting("activation-chance", "Activation Chance", 100, 0, 100, 1)
               .visibleWhen(() -> packetMode(this))
               .unit("%")
               .formatter(var0 -> var0 + "%")
               .description("% to fast-break.")
               .build()
         );
      }

      @Override
      public String info() {
         return this.choice("mode");
      }

      @Override
      public void onDisable() {
         this.removeHaste();
         this.lastFastBreakPos = null;
         this.fastBreakActivated = false;
      }

      @Override
      public void tick() {
         if (!MultiPilot.isActive()) {
            String var1 = this.choice("mode");
            if ("Haste".equals(var1)) {
               this.tickHasteMode();
            } else {
               this.removeHaste();
            }

            if ("Damage".equals(var1)) {
               this.tickDamageMode();
            } else if ("Packet".equals(var1) && MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var2) {
               var2.riptide$setDestroyDelay(0);
            }
         }
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (MultiPilot.isActive()) {
            return false;
         } else if ("Damage".equals(this.choice("mode")) && this.bool("grim-bypass") && MC.getConnection() != null) {
            if (var1 instanceof ServerboundPlayerActionPacket var2 && var2.getAction() == Action.STOP_DESTROY_BLOCK) {
               MC.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, var2.getPos().above(), var2.getDirection()));
            }

            return false;
         } else {
            return false;
         }
      }

      @Override
      public boolean onStartDestroyBlock(BlockPos var1, Direction var2) {
         if (MultiPilot.isActive()) {
            return false;
         } else if (!"Damage".equals(this.choice("mode")) || !this.bool("instamine")) {
            return false;
         } else if (MC.player != null && MC.level != null && MC.gameMode != null && MC.getConnection() != null && var1 != null) {
            BlockState var3 = MC.level.getBlockState(var1);
            if (this.canMine(var3, var1) && this.passesBlockFilter(var3)) {
               if (var3.getDestroyProgress(MC.player, MC.level, var1) <= 0.5F) {
                  return false;
               } else if (MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var4) {
                  Direction var6 = var2 == null ? Direction.UP : var2;
                  MC.gameMode.destroyBlock(var1);
                  var4.riptide$startPrediction(MC.level, var2x -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, var1.immutable(), var6, var2x));
                  var4.riptide$startPrediction(MC.level, var2x -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, var1.immutable(), var6, var2x));
                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      @Override
      public void onBlockBreakingProgress(BlockPos var1, Direction var2) {
         if (!MultiPilot.isActive()
            && "Packet".equals(this.choice("mode"))
            && MC.player != null
            && MC.level != null
            && MC.getConnection() != null
            && var1 != null
            && MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var3
            && !(var3.riptide$getDestroyProgress() >= 1.0F)) {
            BlockState var6 = MC.level.getBlockState(var1);
            if (this.canMine(var6, var1) && this.passesBlockFilter(var6)) {
               if (!var1.equals(this.lastFastBreakPos)) {
                  this.lastFastBreakPos = var1.immutable();
                  this.fastBreakActivated = this.fastBreakRandom.nextDouble() * 100.0 < Math.max(0, this.integer("activation-chance"));
               }

               if (this.fastBreakActivated) {
                  Direction var5 = var2 == null ? Direction.UP : var2;
                  MC.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, var1.immutable(), var5));
               }
            }
         }
      }

      @Override
      public boolean shouldCancelStartBreakingBlock(BlockPos var1, Direction var2) {
         return false;
      }

      float modifyNormalDestroyProgress(float var1, BlockState var2, BlockPos var3) {
         if (!"Normal".equals(this.choice("mode"))) {
            return var1;
         } else if (MC.player != null && MC.level != null && var3 != null) {
            return this.canMine(var2, var3) && this.passesBlockFilter(var2) ? (float)(var1 * Math.max(0.0, this.decimal("modifier"))) : var1;
         } else {
            return var1;
         }
      }

      boolean usesNormalDestroyModifier() {
         return "Normal".equals(this.choice("mode"));
      }

      private void tickHasteMode() {
         if (MC.player != null) {
            int var1 = Math.max(1, this.integer("haste-amplifier"));
            MobEffectInstance var2 = MC.player.getEffect(MobEffects.HASTE);
            if (var2 == null || !var2.showIcon() || var2.getAmplifier() <= var1 - 1) {
               MC.player.addEffect(new MobEffectInstance(MobEffects.HASTE, -1, var1 - 1, false, false, false), null);
            }
         }
      }

      private void removeHaste() {
         if (MC.player != null) {
            MobEffectInstance var1 = MC.player.getEffect(MobEffects.HASTE);
            if (var1 != null && !var1.showIcon()) {
               MC.player.removeEffect(MobEffects.HASTE);
            }
         }
      }

      private void tickDamageMode() {
         if (MC.player != null
            && MC.level != null
            && MC.gameMode != null
            && this.bool("instamine")
            && MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var1) {
            BlockPos var3 = var1.riptide$getDestroyBlockPos();
            if (var3 != null && !(var1.riptide$getDestroyProgress() <= 0.0F)) {
               this.finishDamageIfReady(var3);
            }
         }
      }

      private void finishDamageIfReady(BlockPos var1) {
         if (MC.player != null && MC.level != null && MC.gameMode != null && var1 != null && MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var2) {
            BlockState var6 = MC.level.getBlockState(var1);
            if (this.canMine(var6, var1) && this.passesBlockFilter(var6)) {
               float var4 = var2.riptide$getDestroyProgress();
               float var5 = var6.getDestroyProgress(MC.player, MC.level, var1);
               if (var4 > 0.0F && var4 + var5 >= 0.7F) {
                  var2.riptide$setDestroyProgress(1.0F);
               }
            }
         }
      }

      private boolean canMine(BlockState var1, BlockPos var2) {
         return var1 != null && !var1.isAir() && MC.level != null && var1.getDestroySpeed(MC.level, var2) >= 0.0F;
      }

      private boolean passesBlockFilter(BlockState var1) {
         if (var1 == null) {
            return false;
         } else {
            List var2 = this.list("blocks");
            if (var2.isEmpty()) {
               return "Blacklist".equals(this.choice("blocks-filter"));
            } else {
               String var3 = BuiltInRegistries.BLOCK.getKey(var1.getBlock()).toString().toLowerCase(Locale.ROOT);
               String var4 = var3.contains(":") ? var3.substring(var3.indexOf(58) + 1) : var3;
               boolean var5 = false;

               for (String var7 : var2) {
                  String var8 = var7 == null ? "" : var7.trim().toLowerCase(Locale.ROOT);
                  if (!var8.isEmpty() && (var8.equals(var3) || var8.equals(var4))) {
                     var5 = true;
                     break;
                  }
               }

               return "Whitelist".equals(this.choice("blocks-filter")) ? var5 : !var5;
            }
         }
      }

      private static boolean normalMode(Module var0) {
         return var0 != null && "Normal".equals(var0.value("mode"));
      }

      private static boolean hasteMode(Module var0) {
         return var0 != null && "Haste".equals(var0.value("mode"));
      }

      private static boolean damageMode(Module var0) {
         return var0 != null && "Damage".equals(var0.value("mode"));
      }

      private static boolean packetMode(Module var0) {
         return var0 != null && "Packet".equals(var0.value("mode"));
      }
   }

   static final class FastUseModule extends Module {
      private static final int EXP_ROTATION_RESET_TICKS = 5;
      private static final ValueRange DEFAULT_CPS = new ValueRange(8, 12);
      private final RiptideClickPacer usePacer = new RiptideClickPacer();
      private boolean manualExpActive;
      private boolean manualBlockActive;
      private int expRotationResetTicks;
      private RiptideRotationUtil.Rotation expSilentRotation;
      private RiptideRotationUtil.Rotation serverRotation;

      FastUseModule() {
         super("fast-use", "FastUse", ModuleCategory.PLAYER, "Speeds up item use.");
         BuiltinModules.fastUseInstance = this;
         this.add(new BoolSetting("exp", "Fast EXP", true).description("Speed experience bottles."));
         this.add(new RangeSetting("exp-cps", "EXP CPS", DEFAULT_CPS, 5.0, 20.0, 0.5).minSeparation(0.5).visibleWhen(() -> this.bool("exp")).build());
         this.add(new BoolSetting("blocks", "Fast Blocks", true).description("Speed block use."));
         this.add(new RangeSetting("blocks-cps", "Blocks CPS", DEFAULT_CPS, 5.0, 20.0, 0.5).minSeparation(0.5).visibleWhen(() -> this.bool("blocks")).build());
      }

      @Override
      public void preMovementTick() {
         if (MC == null || MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
            this.resetExpState();
         } else if (AutoTotemModule.operationActive()) {
            this.stopManualUse();
         } else if (MC.player.isUsingItem()) {
            this.stopManualUse();
         } else {
            InteractionHand var1 = this.bool("exp") && this.canUseManualUse() && !this.mainHandWouldPreempt(true) ? this.experienceBottleHand() : null;
            if (var1 != null && this.isPhysicalUseHeld()) {
               this.activateManualUse(true);
               this.handleExperienceBottles(var1);
            } else if (this.bool("blocks")
               && this.canUseManualUse()
               && this.isHoldingBlockItem()
               && this.isPhysicalUseHeld()
               && !this.mainHandWouldPreempt(false)) {
               this.activateManualUse(false);
               this.tickExpRotationReset();
               this.handleBlock();
            } else {
               this.stopManualUse();
            }
         }
      }

      @Override
      public void onDisable() {
         this.resetExpState();
      }

      @Override
      public void onGameJoin() {
         this.resetExpState();
      }

      @Override
      public void onGameLeft() {
         this.resetExpState();
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (var1 instanceof ServerboundMovePlayerPacket var2 && var2.hasRotation() && MC != null && MC.player != null) {
            RiptideRotationUtil.Rotation var3 = this.serverRotation();
            this.serverRotation = new RiptideRotationUtil.Rotation(var2.getYRot(var3.yaw()), var2.getXRot(var3.pitch()));
         }

         return false;
      }

      private void handleExperienceBottles(InteractionHand var1) {
         if (this.expSilentRotation == null) {
            RiptideRotationUtil.Rotation var2 = this.serverRotation();
            this.expSilentRotation = RiptideRotationUtil.normalizeToSensitivity(new RiptideRotationUtil.Rotation(var2.yaw(), 90.0F), var2);
            this.expRotationResetTicks = 5;
         }

         this.tickExpRotationReset();
         if (RiptideSilentAim.packetRotation() == null && !RiptideSilentAim.scaffoldOwnsRotation()) {
            this.expRotationResetTicks = 5;
         }

         if (sameRotation(this.expSilentRotation, this.serverRotation()) && this.bottleStillHeld(var1) != null && this.scheduleUseClick()) {
            RiptideInputClicker.queueFastExpUseClick();
         }
      }

      private boolean ownsManualInput() {
         return this.manualExpActive || this.manualBlockActive;
      }

      private boolean beginManualInputClick() {
         if (RiptideSilentAim.packetRotation() == null
            && !RiptideSilentAim.scaffoldOwnsRotation()
            && !ScaffoldModule.reservesRageInput()
            && !AutoTotemModule.operationActive()) {
            return this.manualExpActive
               ? this.experienceBottleHand() != null && this.activeExpRotation() != null && RiptideInputClicker.beginFastExpUseClick()
               : this.manualBlockActive && this.isHoldingBlockItem() && RiptideInputClicker.beginFastBlockUseClick();
         } else {
            return false;
         }
      }

      private boolean canUseManualUse() {
         return MC.options != null
            && MC.level != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !MC.player.isDeadOrDying()
            && !MC.player.isSpectator()
            && !PackHideState.isHardLocked()
            && !PackFreecamState.isActive()
            && !RiptideRemoteView.isActive()
            && !MultiPilot.isActive()
            && !PacketTeleportController.ownsMainMovement()
            && !MacroExecutor.isRunning();
      }

      private boolean isPhysicalUseHeld() {
         return MC.options != null && RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$isActuallyDown();
      }

      private InteractionHand experienceBottleHand() {
         if (MC.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
            return InteractionHand.MAIN_HAND;
         } else {
            return MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE) ? InteractionHand.OFF_HAND : null;
         }
      }

      private InteractionHand bottleStillHeld(InteractionHand var1) {
         return MC.player.getItemInHand(var1).is(Items.EXPERIENCE_BOTTLE) ? var1 : this.experienceBottleHand();
      }

      private void activateManualUse(boolean var1) {
         if ((!var1 || !this.manualExpActive) && (var1 || !this.manualBlockActive)) {
            this.stopManualUse();
            this.manualExpActive = var1;
            this.manualBlockActive = !var1;
            this.usePacer.reset();
         }
      }

      private ValueRange useBand() {
         return ValueRange.parse(this.value(this.manualExpActive ? "exp-cps" : "blocks-cps"), DEFAULT_CPS);
      }

      private boolean scheduleUseClick() {
         ValueRange var1 = this.useBand();
         return this.usePacer.shouldClick(var1.min(), var1.max());
      }

      private void stopManualUse() {
         if (this.manualExpActive) {
            RiptideInputClicker.cancelFastExpUseClick();
         }

         if (this.manualBlockActive) {
            RiptideInputClicker.cancelFastBlockUseClick();
         }

         this.manualExpActive = false;
         this.manualBlockActive = false;
         this.usePacer.reset();
         this.tickExpRotationReset();
      }

      private void tickExpRotationReset() {
         if (this.expRotationResetTicks > 0) {
            this.expRotationResetTicks--;
            if (this.expRotationResetTicks <= 0) {
               this.expSilentRotation = null;
            }
         }
      }

      private void resetExpState() {
         RiptideInputClicker.cancelFastExpUseClick();
         RiptideInputClicker.cancelFastBlockUseClick();
         this.manualExpActive = false;
         this.manualBlockActive = false;
         this.usePacer.reset();
         this.expRotationResetTicks = 0;
         this.expSilentRotation = null;
         this.serverRotation = null;
      }

      private RiptideRotationUtil.Rotation serverRotation() {
         if (this.serverRotation != null) {
            return this.serverRotation;
         } else {
            return MC != null && MC.player != null
               ? new RiptideRotationUtil.Rotation(MC.player.getYRot(), MC.player.getXRot())
               : new RiptideRotationUtil.Rotation(0.0F, 0.0F);
         }
      }

      private RiptideRotationUtil.Rotation activeExpRotation() {
         return this.expRotationResetTicks > 0 ? this.expSilentRotation : null;
      }

      private static boolean sameRotation(RiptideRotationUtil.Rotation var0, RiptideRotationUtil.Rotation var1) {
         return var0 != null && var1 != null && Float.compare(var0.yaw(), var1.yaw()) == 0 && Float.compare(var0.pitch(), var1.pitch()) == 0;
      }

      private void handleBlock() {
         if (this.activeExpRotation() == null
            && RiptideSilentAim.packetRotation() == null
            && !RiptideSilentAim.scaffoldOwnsRotation()
            && this.scheduleUseClick()) {
            RiptideInputClicker.queueFastBlockUseClick();
         }
      }

      private boolean mainHandWouldPreempt(boolean var1) {
         ItemStack var2 = MC.player.getMainHandItem();
         if (var2.isEmpty()) {
            return false;
         } else if (var2.getItem() instanceof BlockItem) {
            return var1 && !MC.player.onGround() && MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
         } else if (var1 && var2.is(Items.EXPERIENCE_BOTTLE)) {
            return false;
         } else {
            return var2.getUseAnimation() != ItemUseAnimation.NONE
               ? true
               : var2.is(Items.EXPERIENCE_BOTTLE)
                  || var2.is(Items.ENDER_PEARL)
                  || var2.is(Items.SNOWBALL)
                  || var2.is(Items.EGG)
                  || var2.is(Items.FISHING_ROD)
                  || var2.getItem() instanceof BucketItem
                  || var2.is(Items.WIND_CHARGE)
                  || var2.is(Items.FIRE_CHARGE)
                  || var2.is(Items.ENDER_EYE)
                  || var2.is(Items.SPLASH_POTION)
                  || var2.is(Items.LINGERING_POTION);
         }
      }

      private boolean isHoldingBlockItem() {
         return MC.player.getMainHandItem().getItem() instanceof BlockItem || MC.player.getOffhandItem().getItem() instanceof BlockItem;
      }
   }

   static final class FlightModule extends Module {
      private int delayLeft;
      private int offLeft;
      private double lastPacketY = Double.MAX_VALUE;
      private boolean touchedAbilities;
      private String lastMode = "Abilities";
      private double vulcanTargetY;
      private int vulcanJumpCooldown;
      private int vulcanGlideRequestCooldown;
      private boolean vulcanGlideRequestedThisAir;
      private Boolean meteorFlightActiveCache;
      private long meteorFlightActiveCacheAt;
      private int cachedSettingsRevision = Integer.MIN_VALUE;
      private String cachedMode = "Abilities";
      private String cachedAntiKickMode = "Packet";
      private double cachedSpeed = 0.1;
      private boolean cachedVerticalSpeedMatch;
      private boolean cachedNoSneak;
      private int cachedDelay = 20;
      private int cachedOffTime = 1;
      private long cachedAirTick = Long.MIN_VALUE;
      private boolean cachedOnAir;
      private boolean sendingAntiKickReplacement;
      private boolean vulcanNoElytraDisableQueued;

      FlightModule() {
         super("flight", "Flight", ModuleCategory.MOVEMENT, "Client-side flight.");
         this.add(new ChoiceSetting("mode", "Mode", "Abilities", "Abilities", "Velocity", "Vulcan").description("Flight method").build());
         this.add(
            new DoubleSetting("speed", "Speed", 0.1, 0.0, 2.0, 0.01)
               .visibleWhen(() -> !"Vulcan".equals(this.value("mode")))
               .description("Your speed when flying.")
               .build()
         );
         this.add(
            new BoolSetting("vertical-speed-match", "Vertical Speed Match", false)
               .description("Match vertical speed.")
               .visibleWhen(() -> "Velocity".equals(this.value("mode")))
               .build()
         );
         this.add(
            new BoolSetting("no-sneak", "No Sneak", false).description("Ignore sneak input.").visibleWhen(() -> "Velocity".equals(this.value("mode"))).build()
         );
         this.add(
            new BoolSetting("anti-break-elytra", "Anti Break Elytra", true)
               .visibleWhen(() -> "Vulcan".equals(this.value("mode")))
               .description("Preserve low-durability elytra.")
               .build()
         );
         this.add(new BoolSetting("anti-hunger", "AntiHunger", false).description("Hide sprint from server.").build());
         this.add(
            new ChoiceSetting("anti-kick-mode", "Anti Kick", "Packet", "Normal", "Packet", "None")
               .group("Anti Kick")
               .visibleWhen(() -> !"Vulcan".equals(this.value("mode")))
               .description("Reduce floating kicks")
               .build()
         );
         this.add(
            new IntSetting("delay", "Delay", 20, 1, 200, 1)
               .group("Anti Kick")
               .visibleWhen(() -> !"Vulcan".equals(this.value("mode")))
               .description("Ticks between anti-kick nudges.")
               .build()
         );
         this.add(
            new IntSetting("off-time", "Off Time", 1, 1, 20, 1)
               .group("Anti Kick")
               .visibleWhen(() -> !"Vulcan".equals(this.value("mode")))
               .description("Ticks spent nudging down.")
               .build()
         );
      }

      @Override
      public void onEnable() {
         this.updateFlightCache();
         this.resetFlightState();
         this.lastMode = this.cachedMode;
         this.vulcanNoElytraDisableQueued = false;
         if (!MultiPilot.isActive()) {
            if ("Vulcan".equals(this.lastMode)) {
               if (!this.ensureVulcanElytra()) {
                  this.disableVulcanNoElytra(true);
               } else {
                  this.vulcanTargetY = MC.player.getY();
                  boolean var1 = !MC.player.onGround();
                  this.vulcanJumpCooldown = var1 ? 10 : 0;
                  this.vulcanGlideRequestCooldown = 0;
                  this.vulcanGlideRequestedThisAir = MC.player.isFallFlying();
                  if (var1) {
                     this.requestVulcanGlide();
                  } else {
                     this.vulcanJump();
                  }
               }
            } else if ("Abilities".equals(this.lastMode)) {
               this.abilitiesOn();
            }
         }
      }

      @Override
      public void tick() {
         this.updateFlightCache();
         if (MC.player != null && !this.shouldYieldToMeteorFlight() && "Vulcan".equals(this.cachedMode)) {
         }
      }

      @Override
      public void preMovementTick() {
         if (!MultiPilot.isActive() && MC.player != null && MC.gameMode != null && !MC.player.isSpectator() && !this.shouldYieldToMeteorFlight()) {
            if (RiptideJoinGrace.isMovementPaused()) {
               if (this.touchedAbilities) {
                  this.abilitiesOff();
               }
            } else {
               this.updateFlightCache();
               String var1 = this.cachedMode;
               this.syncModeChange(var1);
               if ("Vulcan".equals(var1)) {
                  this.tickVulcan();
               } else {
                  if (this.delayLeft > 0) {
                     this.delayLeft--;
                  }

                  if (this.offLeft <= 0 && this.delayLeft <= 0) {
                     this.delayLeft = this.cachedDelay;
                     this.offLeft = this.cachedOffTime;
                     if (this.usesPacketAntiKick()) {
                        ((RiptideLocalPlayerAccessor)MC.player).riptide$setPositionReminder(20);
                     }
                  } else if (this.delayLeft <= 0) {
                     boolean var2 = false;
                     if ("Normal".equals(this.cachedAntiKickMode) && "Abilities".equals(var1)) {
                        this.abilitiesOff();
                        var2 = true;
                     } else if (this.usesPacketAntiKick() && this.offLeft == this.cachedOffTime) {
                        ((RiptideLocalPlayerAccessor)MC.player).riptide$setPositionReminder(20);
                     }

                     this.offLeft--;
                     if (var2) {
                        return;
                     }
                  }

                  if ("Velocity".equals(var1)) {
                     MC.player.getAbilities().flying = false;
                     MC.player.setDeltaMovement(Vec3.ZERO);
                     Vec3 var5 = MC.player.getDeltaMovement();
                     double var3 = this.cachedSpeed * (this.cachedVerticalSpeedMatch ? 10.0 : 5.0);
                     if (MC.options.keyJump.isDown()) {
                        var5 = var5.add(0.0, var3, 0.0);
                     }

                     if (MC.options.keyShift.isDown()) {
                        var5 = var5.subtract(0.0, var3, 0.0);
                     }

                     MC.player.setDeltaMovement(var5);
                     if (this.cachedNoSneak) {
                        MC.player.setOnGround(false);
                     }
                  } else if ("Abilities".equals(var1)) {
                     if (MC.player.isSpectator()) {
                        return;
                     }

                     MC.player.getAbilities().setFlyingSpeed((float)this.cachedSpeed);
                     MC.player.getAbilities().flying = true;
                     this.touchedAbilities = true;
                     if (!MC.player.getAbilities().instabuild) {
                        MC.player.getAbilities().mayfly = true;
                     }
                  }
               }
            }
         }
      }

      @Override
      public void onDisable() {
         if (MultiPilot.isActive()) {
            this.resetFlightState();
            this.touchedAbilities = false;
         } else {
            boolean var1 = "Vulcan".equals(this.lastMode) || "Vulcan".equals(this.cachedMode);
            if (var1) {
               this.stopVulcanGlideState();
            }

            this.resetFlightState();
            if (MC.player != null && !MC.player.isSpectator() && (this.touchedAbilities || "Abilities".equals(this.lastMode))) {
               this.abilitiesOff();
            }
         }
      }

      @Override
      public void onGameLeft() {
         this.resetFlightState();
         this.sendingAntiKickReplacement = false;
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (MultiPilot.isActive()) {
            return false;
         } else if (this.sendingAntiKickReplacement) {
            return false;
         } else if (RiptideJoinGrace.isMovementPaused()) {
            return false;
         } else if (MC.player != null && MC.getConnection() != null && !this.shouldYieldToMeteorFlight()) {
            this.updateFlightCache();
            if ("Vulcan".equals(this.cachedMode)) {
               return false;
            } else if (this.usesPacketAntiKick() && var1 instanceof ServerboundMovePlayerPacket var2) {
               double var3 = var2.getY(Double.MAX_VALUE);
               if (var3 != Double.MAX_VALUE) {
                  this.antiKickPacket(var2, var3);
                  return false;
               } else {
                  Object var5 = var2.hasRotation()
                     ? new PosRot(
                        MC.player.getX(),
                        MC.player.getY(),
                        MC.player.getZ(),
                        var2.getYRot(0.0F),
                        var2.getXRot(0.0F),
                        var2.isOnGround(),
                        MC.player.horizontalCollision
                     )
                     : new Pos(MC.player.getX(), MC.player.getY(), MC.player.getZ(), var2.isOnGround(), MC.player.horizontalCollision);
                  this.antiKickPacket((ServerboundMovePlayerPacket)var5, MC.player.getY());
                  this.sendingAntiKickReplacement = true;

                  try {
                     MC.getConnection().send((Packet)var5);
                  } finally {
                     this.sendingAntiKickReplacement = false;
                  }

                  return true;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      @Override
      public boolean onPacketReceive(Packet<?> var1) {
         if (MultiPilot.isActive()) {
            return false;
         } else if (MC.player != null && !this.shouldYieldToMeteorFlight()) {
            this.updateFlightCache();
            if (var1 instanceof ClientboundPlayerAbilitiesPacket var2 && "Abilities".equals(this.cachedMode)) {
               MC.player.getAbilities().invulnerable = var2.isInvulnerable();
               MC.player.getAbilities().instabuild = var2.canInstabuild();
               MC.player.getAbilities().setWalkingSpeed(var2.getWalkingSpeed());
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      float getFlyingSpeed() {
         if (RiptideJoinGrace.isMovementPaused()) {
            return -1.0F;
         } else {
            this.updateFlightCache();
            return this.isEnabled() && !this.shouldYieldToMeteorFlight() && "Velocity".equals(this.cachedMode)
               ? (float)this.cachedSpeed * (MC.player != null && MC.player.isSprinting() ? 15.0F : 10.0F)
               : -1.0F;
         }
      }

      boolean antiHungerToggle() {
         return this.bool("anti-hunger");
      }

      boolean noSneak() {
         if (RiptideJoinGrace.isMovementPaused()) {
            return false;
         } else {
            this.updateFlightCache();
            return this.isEnabled() && !this.shouldYieldToMeteorFlight() && "Velocity".equals(this.cachedMode) && this.cachedNoSneak;
         }
      }

      private void abilitiesOn() {
         if (MC.player != null && !MC.player.isSpectator()) {
            MC.player.getAbilities().flying = true;
            this.touchedAbilities = true;
            if (!MC.player.getAbilities().instabuild) {
               MC.player.getAbilities().mayfly = true;
            }
         }
      }

      private void abilitiesOff() {
         if (MC.player != null && !MC.player.isSpectator()) {
            MC.player.getAbilities().setFlyingSpeed(0.05F);
            MC.player.getAbilities().flying = false;
            if (!MC.player.getAbilities().instabuild) {
               MC.player.getAbilities().mayfly = false;
            }

            this.touchedAbilities = false;
         }
      }

      private void antiKickPacket(ServerboundMovePlayerPacket var1, double var2) {
         if (this.delayLeft <= 0 && this.lastPacketY != Double.MAX_VALUE && this.shouldFlyDown(var2, this.lastPacketY) && this.isOnAirCached()) {
            ((RiptideMovePlayerPacketAccessor)var1).riptide$setY(this.lastPacketY - 0.0313);
         } else {
            this.lastPacketY = var2;
         }
      }

      private boolean shouldFlyDown(double var1, double var3) {
         return var1 >= var3 || var3 - var1 < 0.0313;
      }

      private boolean isOnAirCached() {
         long var1 = MC.level == null ? Long.MIN_VALUE : MC.level.getGameTime();
         if (this.cachedAirTick == var1) {
            return this.cachedOnAir;
         } else {
            this.cachedAirTick = var1;
            this.cachedOnAir = this.isOnAirNow();
            return this.cachedOnAir;
         }
      }

      private boolean isOnAirNow() {
         if (MC.level != null && MC.player != null) {
            AABB var1 = MC.player.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
            return MC.level.getBlockStates(var1).allMatch(var0 -> var0.isAir());
         } else {
            return false;
         }
      }

      private void syncModeChange(String var1) {
         if (!var1.equals(this.lastMode)) {
            if ("Abilities".equals(this.lastMode)) {
               this.abilitiesOff();
            }

            this.lastMode = var1;
            if ("Abilities".equals(var1)) {
               this.abilitiesOn();
            }

            if ("Vulcan".equals(var1)) {
               this.resetFlightState();
            }
         }
      }

      private boolean usesPacketAntiKick() {
         return "Packet".equals(this.cachedAntiKickMode);
      }

      private void tickVulcan() {
         if (!this.ensureVulcanElytra()) {
            this.disableVulcanNoElytra(false);
         } else {
            if (this.vulcanJumpCooldown > 0) {
               this.vulcanJumpCooldown--;
            }

            if (this.vulcanGlideRequestCooldown > 0) {
               this.vulcanGlideRequestCooldown--;
            }

            if (MC.player.onGround()) {
               this.vulcanGlideRequestedThisAir = false;
            }

            this.requestVulcanGlide();
            boolean var1 = MC.options != null && MC.options.keyJump.isDown();
            boolean var2 = MC.options != null && MC.options.keyShift.isDown();
            boolean var3 = MC.player.isFallFlying();
            if (var1) {
               this.vulcanTargetY = Math.max(this.vulcanTargetY + (var3 ? 0.04 : 0.12), MC.player.getY());
               if (!var3 || MC.player.getY() < this.vulcanTargetY - 0.08 || MC.player.getDeltaMovement().y < -0.055) {
                  this.vulcanJump();
               }
            } else if (var2) {
               this.vulcanTargetY = MC.player.getY() - 0.35;
            } else if (MC.player.getY() < this.vulcanTargetY - 0.22 && MC.player.getDeltaMovement().y < -0.02) {
               this.vulcanJump();
            }
         }
      }

      private void disableVulcanNoElytra(boolean var1) {
         if (!this.vulcanNoElytraDisableQueued) {
            this.vulcanNoElytraDisableQueued = true;
            String var2 = "Flight disabled: elytra not found.";
            if (var1) {
               this.disableSilentlyWithToggleMessage(var2);
            } else {
               this.disableWithToggleMessage(var2);
            }

            this.vulcanNoElytraDisableQueued = false;
         }
      }

      private void vulcanJump() {
         if (this.vulcanJumpCooldown <= 0 && MC.player != null) {
            if (MC.player.onGround()) {
               if (MC.player.isFallFlying()) {
                  MC.player.stopFallFlying();
               }

               MC.player.jumpFromGround();
               this.vulcanJumpCooldown = 3;
            } else {
               MC.player.jumpFromGround();
               this.vulcanJumpCooldown = MC.player.isFallFlying() ? 12 : 3;
            }
         }
      }

      private void requestVulcanGlide() {
         if (this.vulcanGlideRequestCooldown <= 0
            && MC.player != null
            && MC.getConnection() != null
            && !MC.player.onGround()
            && !MC.player.isFallFlying()
            && !this.vulcanGlideRequestedThisAir) {
            MC.player.tryToStartFallFlying();
            MC.getConnection()
               .send(new ServerboundPlayerCommandPacket(MC.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            this.vulcanGlideRequestedThisAir = true;
            this.vulcanGlideRequestCooldown = 20;
         }
      }

      private void stopVulcanGlideState() {
         if (MC.player != null) {
            if (MC.player.isFallFlying()) {
               MC.player.stopFallFlying();
            }

            this.vulcanGlideRequestCooldown = 0;
            this.vulcanJumpCooldown = 0;
            this.vulcanGlideRequestedThisAir = false;
         }
      }

      private boolean ensureVulcanElytra() {
         if (MC.player == null) {
            return false;
         } else {
            ItemStack var1 = MC.player.getItemBySlot(EquipmentSlot.CHEST);
            if (this.isVulcanUsableElytra(var1)) {
               if (this.bool("anti-break-elytra") && !this.isVulcanSafeElytra(var1)) {
                  int var4 = this.findVulcanElytraSlot(true);
                  if (var4 >= 0 && RiptideInventoryHelper.swapInventorySlots(MC, var4, 38)) {
                     return true;
                  } else {
                     int var3 = this.findEmptyInventorySlot();
                     if (var3 >= 0) {
                        RiptideInventoryHelper.swapInventorySlots(MC, 38, var3);
                     }

                     return false;
                  }
               } else {
                  return true;
               }
            } else {
               int var2 = this.findVulcanElytraSlot(this.bool("anti-break-elytra"));
               return var2 < 0
                  ? false
                  : RiptideInventoryHelper.swapInventorySlots(MC, var2, 38) && this.isVulcanUsableElytra(MC.player.getItemBySlot(EquipmentSlot.CHEST));
            }
         }
      }

      private int findVulcanElytraSlot(boolean var1) {
         if (MC.player == null) {
            return -1;
         } else {
            int var2 = -1;
            int var3 = -1;

            for (int var4 = 0; var4 < 36; var4++) {
               ItemStack var5 = MC.player.getInventory().getItem(var4);
               if (this.isVulcanUsableElytra(var5) && (!var1 || this.isVulcanSafeElytra(var5))) {
                  int var6 = this.vulcanElytraRemainingDurability(var5);
                  if (var6 > var3) {
                     var3 = var6;
                     var2 = var4;
                  }
               }
            }

            return var2;
         }
      }

      private int findEmptyInventorySlot() {
         if (MC.player == null) {
            return -1;
         } else {
            for (int var1 = 0; var1 < 36; var1++) {
               if (MC.player.getInventory().getItem(var1).isEmpty()) {
                  return var1;
               }
            }

            return -1;
         }
      }

      private boolean isVulcanUsableElytra(ItemStack var1) {
         return var1 != null && var1.is(Items.ELYTRA) && !var1.isBroken() && this.vulcanElytraRemainingDurability(var1) > 0;
      }

      private boolean isVulcanSafeElytra(ItemStack var1) {
         return this.isVulcanUsableElytra(var1) && this.vulcanElytraRemainingDurability(var1) > this.vulcanElytraSafeDurability(var1);
      }

      private int vulcanElytraRemainingDurability(ItemStack var1) {
         return var1 != null && var1.isDamageableItem() ? Math.max(0, var1.getMaxDamage() - var1.getDamageValue()) : Integer.MAX_VALUE;
      }

      private int vulcanElytraSafeDurability(ItemStack var1) {
         int var2 = var1 != null && var1.isDamageableItem() ? var1.getMaxDamage() : 0;
         return Math.max(40, (int)Math.ceil(var2 * 0.1));
      }

      private void resetFlightState() {
         this.updateFlightCache();
         this.delayLeft = this.cachedDelay;
         this.offLeft = this.cachedOffTime;
         this.lastPacketY = Double.MAX_VALUE;
         this.cachedAirTick = Long.MIN_VALUE;
         this.cachedOnAir = false;
         this.vulcanGlideRequestCooldown = 0;
         this.vulcanGlideRequestedThisAir = false;
      }

      private void updateFlightCache() {
         int var1 = ModuleRegistry.revision();
         if (this.cachedSettingsRevision != var1) {
            this.cachedSettingsRevision = var1;
            this.cachedMode = this.choice("mode");
            this.cachedAntiKickMode = this.choice("anti-kick-mode");
            this.cachedSpeed = this.decimal("speed");
            this.cachedVerticalSpeedMatch = this.bool("vertical-speed-match");
            this.cachedNoSneak = this.bool("no-sneak");
            this.cachedDelay = Math.max(1, this.integer("delay"));
            this.cachedOffTime = Math.max(1, this.integer("off-time"));
         }
      }

      private boolean shouldYieldToMeteorFlight() {
         long var1 = System.currentTimeMillis();
         if (this.meteorFlightActiveCache != null && var1 - this.meteorFlightActiveCacheAt < 250L) {
            return this.meteorFlightActiveCache;
         } else {
            this.meteorFlightActiveCacheAt = var1;
            this.meteorFlightActiveCache = this.queryMeteorFlightActive();
            return this.meteorFlightActiveCache;
         }
      }

      private boolean queryMeteorFlightActive() {
         try {
            Class var1 = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, BuiltinModules.FlightModule.class.getClassLoader());
            Class var2 = Class.forName(
               "meteordevelopment.meteorclient.systems.modules.movement.Flight", false, BuiltinModules.FlightModule.class.getClassLoader()
            );
            Object var3 = var1.getMethod("get").invoke(null);
            Object var4 = var1.getMethod("get", Class.class).invoke(var3, var2);
            return var4 != null && Boolean.TRUE.equals(var4.getClass().getMethod("isActive").invoke(var4));
         } catch (Throwable var5) {
            return false;
         }
      }
   }

   static final class FreecamModule extends Module {
      FreecamModule() {
         super("freecam", "Freecam", ModuleCategory.RENDER, "Move the camera away from the player.");
         this.add(new DoubleSetting("speed", "Speed", 1.0, 0.05, 30.0, 0.05).description("Horizontal camera speed."));
         this.add(new DoubleSetting("vertical-speed", "Vertical Speed", 1.0, 0.05, 30.0, 0.05).description("Vertical camera speed."));
         this.add(new BoolSetting("reload-chunks", "Reload Chunks", true).description("Refresh chunk culling."));
         this.add(new BoolSetting("interact", "Interact", true).description("Interact from freecam view"));
      }

      @Override
      public void onEnable() {
         PackFreecamState.enable(this.bool("reload-chunks"));
         PackFreecamState.setInteractEnabled(this.bool("interact"));
      }

      @Override
      public void onDisable() {
         PackFreecamState.disable();
      }

      @Override
      public void onOptionValueChanged(String var1) {
         if ("interact".equals(var1)) {
            PackFreecamState.setInteractEnabled(this.bool("interact"));
         }
      }

      @Override
      public void onGameLeft() {
         this.setEnabledSilently(false);
      }

      @Override
      public void tick() {
         if (MC.player != null && MC.player.isDeadOrDying()) {
            this.disableWithToggleMessage("Freecam disabled: you died.");
         }
      }

      @Override
      public void preMovementTick() {
         PackFreecamState.tickMovement(this.decimal("speed"), this.decimal("vertical-speed"));
      }

      @Override
      public Vec3 onPlayerMove(MoverType var1, Vec3 var2) {
         return PackFreecamState.onPlayerMove(var1, var2);
      }

      @Override
      public String info() {
         return String.format(Locale.ROOT, "%.2f", this.decimal("speed"));
      }
   }

   static final class FullbrightModule extends Module {
      private String lastMode = "";
      private String lastLightType = "";
      private int lastMinimumLightLevel = Integer.MIN_VALUE;
      private boolean appliedNightVision;

      FullbrightModule() {
         super("fullbright", "Fullbright", ModuleCategory.RENDER, "Brightens the world.");
         boolean var1 = !RiptideConfig.getGlobal().modules.containsKey("fullbright");
         this.add(new ChoiceSetting("mode", "Mode", "Gamma", "Gamma", "Potion", "Luminance").description("How lighting is boosted.").build());
         this.add(new ChoiceSetting("light-type", "Light Type", "BLOCK", "SKY", "BLOCK").visibleWhen(() -> "Luminance".equals(this.value("mode"))).build());
         this.add(new IntSetting("minimum-light-level", "Min Light", 8, 0, 15, 1).visibleWhen(() -> "Luminance".equals(this.value("mode"))).build());
         if (var1) {
            this.setEnabledSilently(true);
         }
      }

      @Override
      public void onEnable() {
         ModuleRenderUtil.refreshWorldRenderer();
      }

      @Override
      public void onDisable() {
         this.disableNightVision();
         this.appliedNightVision = false;
         ModuleRenderUtil.refreshWorldRenderer();
      }

      @Override
      public void tick() {
         String var1 = this.choice("mode");
         String var2 = this.choice("light-type");
         int var3 = this.integer("minimum-light-level");
         boolean var4 = !var1.equals(this.lastMode) || !var2.equals(this.lastLightType) || var3 != this.lastMinimumLightLevel;
         if (var4) {
            this.lastMode = var1;
            this.lastLightType = var2;
            this.lastMinimumLightLevel = var3;
            if (!"Potion".equals(var1) && this.appliedNightVision) {
               this.disableNightVision();
               this.appliedNightVision = false;
            }

            ModuleRenderUtil.refreshWorldRenderer();
         }

         if ("Potion".equals(var1)) {
            this.applyNightVision();
         }
      }

      public boolean gammaActive() {
         return this.isEnabled() && "Gamma".equals(this.choice("mode"));
      }

      public int luminance(String var1) {
         return this.isEnabled() && "Luminance".equals(this.choice("mode")) && this.choice("light-type").equals(var1) ? this.integer("minimum-light-level") : 0;
      }

      private void applyNightVision() {
         if (MC.player != null) {
            MobEffectInstance var1 = MC.player.getEffect(MobEffects.NIGHT_VISION);
            if (var1 != null) {
               if (var1.getDuration() < 420) {
                  ((RiptideMobEffectInstanceAccessor)var1).riptide$setDuration(420);
               }
            } else {
               MC.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0));
            }

            this.appliedNightVision = true;
         }
      }

      private void disableNightVision() {
         if (MC.player != null && MC.player.hasEffect(MobEffects.NIGHT_VISION)) {
            MC.player.removeEffect(MobEffects.NIGHT_VISION);
         }
      }
   }

   static final class HideModule extends Module {
      HideModule() {
         super("hide", "PanicMode", ModuleCategory.MISC, "Temporarily hides client modules and UI.");
      }

      @Override
      public void onEnable() {
         PackHideState.enable(this);
         this.riptide$refreshDisguise();
      }

      @Override
      public void onDisable() {
         PackHideState.disableAndRestore(this);
         this.riptide$refreshDisguise();
      }

      private void riptide$refreshDisguise() {
         if (MC != null) {
            RiptideWindowBranding.refresh(MC);
            if (!RiptideLiteVariant.enabled() && MC.gui.screen() instanceof RiptideTitleScreen) {
               MC.gui.setScreen(new RiptideTitleScreen());
            }
         }
      }

      @Override
      public boolean emitsToggleMessage() {
         return false;
      }
   }

   static final class HudModule extends Module {
      HudModule() {
         super("hud", "HUD", ModuleCategory.MISC, "Shows HUD elements.");
         boolean var1 = !RiptideConfig.getGlobal().modules.containsKey("hud");
         this.add(new ActionSetting("edit-hud", "Edit HUD", this::openHudEditor).availableOffline().description("Open HUD editor.").group("Editor"));
         this.add(new BoolSetting("editor-grid", "Editor Grid", true).description("Show editor grid.").group("Editor"));
         this.add(new BoolSetting("hide-in-guis", "Hide In GUIs", true).description("Hide in screens.").group("Visibility"));
         this.add(new BoolSetting("show-in-chat", "Show In Chat", false).description("Show in chat.").group("Visibility"));
         this.add(new BoolSetting("show-in-pause", "Show In Pause", false).description("Show in pause.").group("Visibility"));
         this.add(new BoolSetting("show-in-hud-editor", "Show In HUD Editor", true).description("Show while editing.").group("Visibility"));
         if (var1) {
            this.setEnabledSilently(true);
         }
      }

      @Override
      public void onEnable() {
         RiptideHudManager.ensureDefaults();
         ServerTickTracker.reset();
      }

      @Override
      public void tick() {
         RiptideHudManager.tickHeartbeat();
      }

      @Override
      protected void onSettingsReset() {
         RiptideHudManager.resetAllElements();
      }

      private void openHudEditor() {
         RiptideHudManager.ensureDefaults();
         MC.gui.setScreen(new RiptideHudEditorScreen(MC.gui.screen()));
      }
   }

   static final class InstantRebreakModule extends Module {
      private BlockPos target;
      private Direction direction = Direction.UP;
      private int delay;

      InstantRebreakModule() {
         super("instant-rebreak", "InstantRebreak", ModuleCategory.PLAYER, "Rebreaks the last block.");
         this.add(new IntSetting("delay", "Delay", 0, 0, 20, 1).description("Delay between rebreak packets."));
         this.add(new BoolSetting("only-pick", "Only Pickaxe", true).description("Require pickaxe."));
         this.add(new BoolSetting("rotate", "Rotate", false).description("Face target first."));
         this.add(new BoolSetting("render", "Render", true).group("Render").description("Show the rebreak target."));
         this.add(new ColorSetting("color", "Color", -50373).group("Render").description("Single outline color.").build());
      }

      @Override
      public void onDisable() {
         RiptideInstaBreakRenderer.clear();
         this.target = null;
      }

      @Override
      public void onStartBreakingBlock(BlockPos var1, Direction var2) {
         if (!MultiPilot.isActive()) {
            this.target = var1 == null ? null : var1.immutable();
            this.direction = var2 == null ? Direction.UP : var2;
            if (this.bool("render")) {
               RiptideInstaBreakRenderer.setTarget(this.target, this.colorValue("color", this.colorValue("line-color", -12986502)));
            }

            this.delay = 0;
         }
      }

      @Override
      public void tick() {
         if (!MultiPilot.isActive()
            && MC.player != null
            && MC.level != null
            && MC.getConnection() != null
            && this.target != null
            && !AutoTotemModule.operationActive()) {
            if (!this.bool("render")) {
               RiptideInstaBreakRenderer.clear();
            } else {
               RiptideInstaBreakRenderer.setTarget(this.target, this.colorValue("color", this.colorValue("line-color", -12986502)));
            }

            if (!MC.level.isOutsideBuildHeight(this.target)
               && !MC.level.getBlockState(this.target).isAir()
               && (!this.bool("only-pick") || MC.player.getMainHandItem().is(ItemTags.PICKAXES))
               && this.delay++ >= this.integer("delay")) {
               this.delay = 0;
               if (this.bool("rotate")) {
                  MC.player.lookAt(Anchor.EYES, Vec3.atCenterOf(this.target));
               }

               BlockPos var1 = this.target;
               Direction var2 = this.direction == null ? Direction.UP : this.direction;
               if (MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor var3) {
                  var3.riptide$startPrediction(MC.level, var2x -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, var1, var2, var2x));
               } else {
                  MC.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, var1, var2));
               }

               MC.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
         }
      }

      private int colorValue(String var1, int var2) {
         try {
            String var3 = this.value(var1).replace("#", "");
            if (var3.length() == 6) {
               var3 = "FF" + var3;
            }

            return (int)Long.parseLong(var3, 16);
         } catch (NumberFormatException var4) {
            return var2;
         }
      }
   }

   static final class InvMoveModule extends Module {
      InvMoveModule() {
         super("inv-move", "InvMove", ModuleCategory.MOVEMENT, "Allows movement keys in normal container screens.");
      }

      @Override
      public void onEnable() {
         RiptideConfig.getGlobal().inventoryMove = true;
      }

      @Override
      public void onDisable() {
         RiptideConfig.getGlobal().inventoryMove = false;
      }

      @Override
      public void tick() {
         RiptideInventoryMoveHelper.syncHeldMovementKeysIfSafe();
      }
   }

   static final class ItemEspModule extends Module {
      ItemEspModule() {
         super("item-esp", "ItemESP", ModuleCategory.RENDER, "Outlines dropped items with the shader outline.");
         this.add(new ChoiceSetting("mode", "Mode", "Shader", "Shader").description("Dropped item highlight style.").build());
         this.add(new ChoiceSetting("items-mode", "Items", "All", "All", "Some").group("General").description("Which items to outline").build());
         this.add(
            RegistryListSetting.items("items", "Item List", "")
               .group("General")
               .visibleWhen(() -> "Some".equals(this.value("items-mode")))
               .description("Item ids to outline")
               .build()
         );
         this.add(new DoubleSetting("max-distance", "Max Distance", 64.0, 0.0, 256.0, 1.0).group("General").description("0 = unlimited.").build());
         this.add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General"));
         this.add(new ChoiceSetting("color-mode", "Color Mode", "Dynamic", "Dynamic", "Static").group("Colors").description("Color mode").build());
         this.add(new ColorSetting("color", "Color", -855648406).group("Colors").visibleWhen(() -> "Static".equals(this.value("color-mode"))));
      }
   }

   static final class NoFallModule extends Module {
      private boolean placedFluid;
      private BlockPos placedTarget;
      private int placedTimer;

      NoFallModule() {
         super("no-fall", "NoFall", ModuleCategory.MOVEMENT, "Reduces fall damage.");
         this.add(new ChoiceSetting("mode", "Mode", "Packet", "Packet", "AirPlace", "Place").description("Fall protection method.").build());
         this.add(
            new ChoiceSetting("placed-item", "Placed Item", "Bucket", "Bucket", "PowderSnow", "HayBale", "Cobweb", "SlimeBlock")
               .visibleWhen(() -> "Place".equals(this.value("mode")) || "AirPlace".equals(this.value("mode")))
               .build()
         );
         this.add(
            new ChoiceSetting("air-place-mode", "Air Place Mode", "BeforeDeath", "BeforeDamage", "BeforeDeath")
               .visibleWhen(() -> "AirPlace".equals(this.value("mode")))
               .build()
         );
         this.add(new BoolSetting("anchor", "Anchor", true).visibleWhen(() -> !"Packet".equals(this.value("mode"))).description("Slow before placing.").build());
         this.add(new BoolSetting("anti-bounce", "Anti Bounce", true).description("Stop bounce effects."));
         this.add(new BoolSetting("pause-on-mace", "Pause On Mace", true).description("Pause holding mace"));
         this.add(
            new BoolSetting("client-rotate", "Client Rotation", false)
               .visibleWhen(() -> !"Packet".equals(this.value("mode")))
               .description("Also rotate camera client-side.")
               .build()
         );
      }

      @Override
      public void onEnable() {
         this.placedFluid = false;
         this.placedTarget = null;
         this.placedTimer = 0;
      }

      @Override
      public void onDisable() {
         this.placedFluid = false;
         this.placedTarget = null;
         this.placedTimer = 0;
      }

      @Override
      public void tick() {
         if (!MultiPilot.isActive()
            && !RiptideJoinGrace.isMovementPaused()
            && MC.player != null
            && MC.gameMode != null
            && !"Packet".equals(this.choice("mode"))
            && (RiptideKillAuraRotation.currentOwner() == null || !RiptideKillAuraRotation.hasCurrentRotation())) {
            this.cleanupPlacedFluid();
            if (this.shouldPlaceModeAct()) {
               if (this.bool("anchor")) {
                  this.centerPlayer();
               }

               if ("AirPlace".equals(this.choice("mode"))) {
                  this.tryAirPlaceBelow();
               } else {
                  this.tryPlaceBelow();
               }
            }
         }
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (MultiPilot.isActive()) {
            return false;
         } else if (!("Packet".equals(this.choice("mode")) && var1 instanceof ServerboundMovePlayerPacket var2) || !this.shouldPacketSpoof()) {
            return false;
         } else if (var2.isOnGround()) {
            return false;
         } else {
            ((RiptideMovePlayerPacketAccessor)var2).riptide$setOnGround(true);
            return false;
         }
      }

      private boolean shouldPacketSpoof() {
         if (RiptideJoinGrace.isMovementPaused()) {
            return false;
         } else if (MC.player != null && !MC.player.getAbilities().instabuild) {
            if (this.bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) {
               return false;
            } else {
               Module var1 = ModuleRegistry.get("flight");
               if (var1 != null && var1.isEnabled()) {
                  return true;
               } else {
                  return MC.player.isFallFlying() ? false : MC.player.getDeltaMovement().y <= -0.5;
               }
            }
         } else {
            return false;
         }
      }

      private boolean shouldPlaceModeAct() {
         if (MC.player != null && !MC.player.getAbilities().instabuild && !MC.player.isFallFlying() && !MC.player.isInWater() && !MC.player.isInLava()) {
            if (this.bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) {
               return false;
            } else {
               double var1 = MC.player.fallDistance;
               if ("AirPlace".equals(this.choice("mode"))) {
                  return "BeforeDeath".equals(this.choice("air-place-mode"))
                     ? var1 > Math.max(2.0, (double)(MC.player.getHealth() + MC.player.getAbsorptionAmount()))
                     : var1 > 2.0;
               } else {
                  return var1 > 3.0 && !this.isAboveWater();
               }
            }
         } else {
            return false;
         }
      }

      private boolean tryPlaceBelow() {
         if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return false;
         } else {
            int var1 = MC.player.getInventory().getSelectedSlot();
            int var2 = this.findHotbarItem();
            if (var2 < 0) {
               return false;
            } else {
               BlockHitResult var3 = MC.level
                  .clip(new ClipContext(MC.player.position(), MC.player.position().subtract(0.0, 5.0, 0.0), Block.OUTLINE, Fluid.NONE, MC.player));
               if (var3 != null && var3.getType() == Type.BLOCK) {
                  BlockPos var4 = var3.getBlockPos().above();
                  if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     return false;
                  } else {
                     try {
                        RiptideInventoryHelper.selectHotbarSlot(MC, var2);

                        try {
                           Item var5 = this.placeItem();
                           if (var5 != Items.WATER_BUCKET && var5 != Items.POWDER_SNOW_BUCKET) {
                              BlockPos var18 = var4.below();
                              Vec3 var21 = Vec3.atCenterOf(var18).add(0.0, 0.5, 0.0);
                              BlockHitResult var8 = new BlockHitResult(var21, Direction.UP, var18, false);
                              if (ModuleRegistry.shouldCancelUseExcept(var8, InteractionHand.MAIN_HAND, this.id())) {
                                 return false;
                              }

                              if (!RiptidePlacementTick.claim(this.id())) {
                                 return false;
                              }

                              this.rotateAndAct(yawTo(var21), pitchTo(var21), () -> MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, var8));
                           } else {
                              Vec3 var6 = Vec3.atCenterOf(var4);
                              if (ModuleRegistry.shouldCancelUseExcept(var3, InteractionHand.MAIN_HAND, this.id())) {
                                 return false;
                              }

                              if (!RiptidePlacementTick.claim(this.id())) {
                                 return false;
                              }

                              this.rotateAndAct(yawTo(var6), pitchTo(var6), () -> MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND));
                              this.placedFluid = true;
                              this.placedTarget = var4;
                              this.placedTimer = 0;
                           }

                           return true;
                        } finally {
                           RiptideInventoryHelper.selectHotbarSlot(MC, var1);
                        }
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }
                  }
               } else {
                  return false;
               }
            }
         }
      }

      private boolean tryAirPlaceBelow() {
         if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return false;
         } else {
            int var1 = MC.player.getInventory().getSelectedSlot();
            int var2 = this.findHotbarBlockItem();
            if (var2 < 0) {
               return false;
            } else {
               Vec3 var3 = MC.player.getDeltaMovement();
               if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                  return false;
               } else {
                  boolean var7;
                  try {
                     RiptideInventoryHelper.selectHotbarSlot(MC, var2);

                     try {
                        MC.player.setDeltaMovement(var3.x, 0.0, var3.z);
                        BlockPos var4 = MC.player.blockPosition().below();
                        Vec3 var5 = Vec3.atCenterOf(var4).add(0.0, 0.5, 0.0);
                        BlockHitResult var6 = new BlockHitResult(var5, Direction.UP, var4, false);
                        if (ModuleRegistry.shouldCancelUseExcept(var6, InteractionHand.MAIN_HAND, this.id())) {
                           return false;
                        }

                        if (RiptidePlacementTick.claim(this.id())) {
                           this.rotateAndAct(MC.player.getYRot(), 90.0, () -> MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, var6));
                           return true;
                        }

                        var7 = false;
                     } finally {
                        MC.player.setDeltaMovement(var3);
                        RiptideInventoryHelper.selectHotbarSlot(MC, var1);
                     }
                  } finally {
                     RiptideHandArbiter.endHandPacketGroup(this.id());
                  }

                  return var7;
               }
            }
         }
      }

      private void rotateAndAct(double var1, double var3, Runnable var5) {
         if (MC.player != null && MC.getConnection() != null) {
            boolean var6 = this.bool("client-rotate");
            float var7 = (float)var1;
            float var8 = (float)var3;
            float var9 = MC.player.getYRot();
            float var10 = MC.player.getXRot();
            float var11 = MC.player.yRotO;
            float var12 = MC.player.xRotO;
            float var13 = MC.player.yHeadRot;
            float var14 = MC.player.yHeadRotO;
            float var15 = MC.player.yBodyRot;
            float var16 = MC.player.yBodyRotO;
            MC.player.setYRot(var7);
            MC.player.setXRot(var8);
            if (var6) {
               MC.player.yRotO = var7;
               MC.player.xRotO = var8;
               MC.player.yHeadRot = var7;
               MC.player.yHeadRotO = var7;
               MC.player.yBodyRot = var7;
               MC.player.yBodyRotO = var7;
            }

            MC.getConnection().send(new Rot(var7, var8, MC.player.onGround(), MC.player.horizontalCollision));

            try {
               var5.run();
            } finally {
               if (!var6) {
                  MC.player.setYRot(var9);
                  MC.player.setXRot(var10);
                  MC.player.yRotO = var11;
                  MC.player.xRotO = var12;
                  MC.player.yHeadRot = var13;
                  MC.player.yHeadRotO = var14;
                  MC.player.yBodyRot = var15;
                  MC.player.yBodyRotO = var16;
               }
            }
         } else {
            var5.run();
         }
      }

      private static double yawTo(Vec3 var0) {
         double var1 = var0.x - MC.player.getX();
         double var3 = var0.z - MC.player.getZ();
         return Math.toDegrees(Math.atan2(var3, var1)) - 90.0;
      }

      private static double pitchTo(Vec3 var0) {
         Vec3 var1 = MC.player.getEyePosition();
         double var2 = var0.x - var1.x;
         double var4 = var0.y - var1.y;
         double var6 = var0.z - var1.z;
         double var8 = Math.sqrt(var2 * var2 + var6 * var6);
         return -Math.toDegrees(Math.atan2(var4, var8));
      }

      private void cleanupPlacedFluid() {
         if (!RiptideBlinkManager.holdsActionsWithoutMovement()
            && this.placedFluid
            && this.placedTarget != null
            && MC.level != null
            && MC.player != null
            && MC.gameMode != null) {
            if (++this.placedTimer > 20) {
               this.placedFluid = false;
               this.placedTarget = null;
               this.placedTimer = 0;
            } else {
               boolean var1 = MC.player.getInBlockState().is(Blocks.WATER) || MC.player.getInBlockState().is(Blocks.POWDER_SNOW);
               boolean var2 = MC.level.getBlockState(MC.player.blockPosition().below()).is(Blocks.POWDER_SNOW) && MC.player.fallDistance == 0.0;
               if (var1 || var2) {
                  int var3 = MC.player.getInventory().getSelectedSlot();
                  int var4 = this.findHotbarItem(Items.BUCKET);
                  if (var4 >= 0 && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
                     try {
                        RiptideInventoryHelper.selectHotbarSlot(MC, var4);

                        try {
                           Vec3 var5 = Vec3.atCenterOf(this.placedTarget);
                           BlockHitResult var6 = new BlockHitResult(var5, Direction.UP, this.placedTarget, false);
                           if (ModuleRegistry.shouldCancelUseExcept(var6, InteractionHand.MAIN_HAND, this.id())) {
                              return;
                           }

                           if (!RiptidePlacementTick.claim(this.id())) {
                              return;
                           }

                           this.rotateAndAct(yawTo(var5), pitchTo(var5), () -> MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND));
                           this.placedFluid = false;
                           this.placedTarget = null;
                           this.placedTimer = 0;
                        } finally {
                           RiptideInventoryHelper.selectHotbarSlot(MC, var3);
                        }
                     } finally {
                        RiptideHandArbiter.endHandPacketGroup(this.id());
                     }

                     return;
                  }
               }
            }
         }
      }

      private void centerPlayer() {
         double var1 = Math.floor(MC.player.getX()) + 0.5;
         double var3 = Math.floor(MC.player.getZ()) + 0.5;
         MC.player.setPos(var1, MC.player.getY(), var3);
         if (!RiptideBlinkManager.isActive() && MC.getConnection() != null) {
            MC.getConnection().send(new Pos(MC.player.getX(), MC.player.getY(), MC.player.getZ(), MC.player.onGround(), MC.player.horizontalCollision));
         }
      }

      private boolean isAboveWater() {
         if (MC.level != null && MC.player != null) {
            MutableBlockPos var1 = MC.player.blockPosition().mutable();
            int var2 = MC.level.getMinY();

            while (var1.getY() > var2) {
               BlockState var3 = MC.level.getBlockState(var1);
               if (var3.blocksMotion()) {
                  break;
               }

               if (var3.getFluidState().is(FluidTags.WATER)) {
                  return true;
               }

               var1.move(Direction.DOWN);
            }

            return false;
         } else {
            return false;
         }
      }

      private int findHotbarItem() {
         Item var1 = this.placeItem();
         return this.findHotbarItem(var1);
      }

      private int findHotbarBlockItem() {
         Item var1 = this.placeItem();

         for (int var2 = 0; var2 < 9; var2++) {
            ItemStack var3 = MC.player.getInventory().getItem(var2);
            if (!var3.isEmpty() && var3.is(var1) && var3.getItem() instanceof BlockItem) {
               return var2;
            }
         }

         for (int var4 = 0; var4 < 9; var4++) {
            ItemStack var5 = MC.player.getInventory().getItem(var4);
            if (!var5.isEmpty() && var5.getItem() instanceof BlockItem) {
               return var4;
            }
         }

         return -1;
      }

      private int findHotbarItem(Item var1) {
         for (int var2 = 0; var2 < 9; var2++) {
            ItemStack var3 = MC.player.getInventory().getItem(var2);
            if (!var3.isEmpty() && var3.is(var1)) {
               return var2;
            }
         }

         return -1;
      }

      private Item placeItem() {
         String var1 = this.choice("placed-item");

         return switch (var1) {
            case "PowderSnow" -> Items.POWDER_SNOW_BUCKET;
            case "HayBale" -> Items.HAY_BLOCK;
            case "Cobweb" -> Items.COBWEB;
            case "SlimeBlock" -> Items.SLIME_BLOCK;
            default -> MC.level != null && "the_nether".equals(MC.level.dimension().identifier().getPath()) ? Items.POWDER_SNOW_BUCKET : Items.WATER_BUCKET;
         };
      }
   }

   static final class NoInteractModule extends Module {
      NoInteractModule() {
         super("no-interact", "NoInteract", ModuleCategory.PLAYER, "Cancels selected inputs.");
         this.add(RegistryListSetting.blocks("block-mine", "Block Mine", "").group("Blocks").description("Mining block list.").build());
         this.add(new ChoiceSetting("block-mine-mode", "Mine Mode", "BlackList", "WhiteList", "BlackList").group("Blocks").build());
         this.add(RegistryListSetting.blocks("block-interact", "Block Interact", "").group("Blocks").description("Use block list.").build());
         this.add(new ChoiceSetting("block-interact-mode", "Block Mode", "BlackList", "WhiteList", "BlackList").group("Blocks").build());
         this.add(new ChoiceSetting("block-interact-hand", "Block Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Blocks").build());
         this.add(RegistryListSetting.entityTypes("entity-interact", "Entity Interact", "").group("Entities").description("Use entity list.").build());
         this.add(new ChoiceSetting("entity-interact-mode", "Entity Mode", "BlackList", "WhiteList", "BlackList").group("Entities").build());
         this.add(new ChoiceSetting("entity-interact-hand", "Entity Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Entities").build());
         this.add(RegistryListSetting.entityTypes("entity-hit", "Entity Hit", "").group("Attacks").build());
         this.add(new ChoiceSetting("entity-hit-mode", "Hit Mode", "WhiteList", "WhiteList", "BlackList").group("Attacks").build());
         this.add(new BoolSetting("friends", "Friends", false).group("Entities"));
         this.add(new BoolSetting("babies", "Babies", false).group("Entities"));
         this.add(new BoolSetting("nametagged", "Nametagged", false).group("Entities"));
      }

      @Override
      public boolean shouldCancelStartBreakingBlock(BlockPos var1, Direction var2) {
         if (MC.level != null && var1 != null) {
            String var3 = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(var1).getBlock()).toString();
            return this.matchesRegistry(var3, this.list("block-mine"), this.choice("block-mine-mode"));
         } else {
            return this.matchesRegistry("", this.list("block-mine"), this.choice("block-mine-mode"));
         }
      }

      @Override
      public boolean shouldCancelAttack(HitResult var1) {
         if (var1 instanceof EntityHitResult var2) {
            Entity var3 = var2.getEntity();
            return !this.entityFilterAllowed(var3)
               ? false
               : this.matchesRegistry(BuiltInRegistries.ENTITY_TYPE.getKey(var3.getType()).toString(), this.list("entity-hit"), this.choice("entity-hit-mode"));
         } else {
            return false;
         }
      }

      @Override
      public boolean shouldCancelUse(HitResult var1, InteractionHand var2) {
         if (var1 instanceof BlockHitResult var3) {
            if (!this.matchesHand(this.choice("block-interact-hand"), var2)) {
               return false;
            } else if (MC.level == null) {
               return this.matchesRegistry("", this.list("block-interact"), this.choice("block-interact-mode"));
            } else {
               String var6 = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(var3.getBlockPos()).getBlock()).toString();
               return this.matchesRegistry(var6, this.list("block-interact"), this.choice("block-interact-mode"));
            }
         } else if (var1 instanceof EntityHitResult var4) {
            if (!this.matchesHand(this.choice("entity-interact-hand"), var2)) {
               return false;
            } else if (!this.entityFilterAllowed(var4.getEntity())) {
               return false;
            } else {
               String var5 = BuiltInRegistries.ENTITY_TYPE.getKey(var4.getEntity().getType()).toString();
               return this.matchesRegistry(var5, this.list("entity-interact"), this.choice("entity-interact-mode"));
            }
         } else {
            return false;
         }
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (var1 instanceof ServerboundUseItemOnPacket var2) {
            if (!this.matchesHand(this.choice("block-interact-hand"), var2.getHand())) {
               return false;
            } else if (MC.level == null) {
               return this.matchesRegistry("", this.list("block-interact"), this.choice("block-interact-mode"));
            } else {
               String var7 = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(var2.getHitResult().getBlockPos()).getBlock()).toString();
               return this.matchesRegistry(var7, this.list("block-interact"), this.choice("block-interact-mode"));
            }
         } else if (var1 instanceof ServerboundPlayerActionPacket var3 && var3.getAction() == Action.START_DESTROY_BLOCK) {
            return this.shouldCancelStartBreakingBlock(var3.getPos(), var3.getDirection());
         } else if (var1 instanceof ServerboundInteractPacket var4) {
            if (!this.matchesHand(this.choice("entity-interact-hand"), var4.hand())) {
               return false;
            } else if (MC.level == null) {
               return false;
            } else {
               Entity var5 = MC.level.getEntity(var4.entityId());
               if (var5 != null && this.entityFilterAllowed(var5)) {
                  String var6 = BuiltInRegistries.ENTITY_TYPE.getKey(var5.getType()).toString();
                  return this.matchesRegistry(var6, this.list("entity-interact"), this.choice("entity-interact-mode"));
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }

      private boolean matchesHand(String var1, InteractionHand var2) {
         if ("None".equals(var1)) {
            return false;
         } else {
            return "Both".equals(var1)
               ? true
               : "Mainhand".equals(var1) && var2 == InteractionHand.MAIN_HAND || "Offhand".equals(var1) && var2 == InteractionHand.OFF_HAND;
         }
      }

      private boolean matchesRegistry(String var1, List<String> var2, String var3) {
         return shouldCancelRegistryTarget(var1, var2, var3);
      }

      static boolean shouldCancelRegistryTarget(String var0, List<String> var1, String var2) {
         boolean var3 = "WhiteList".equalsIgnoreCase(var2);
         if (var1 != null && !var1.isEmpty()) {
            boolean var4 = false;

            for (String var6 : var1) {
               if (RegistryListCodec.matches(var0, var6)) {
                  var4 = true;
                  break;
               }
            }

            return var3 ? !var4 : var4;
         } else {
            return false;
         }
      }

      private boolean entityFilterAllowed(Entity var1) {
         if (var1 == null) {
            return false;
         } else {
            return !this.bool("nametagged") && var1.hasCustomName() ? false : !(!this.bool("babies") && var1 instanceof LivingEntity var2) || !var2.isBaby();
         }
      }
   }

   static final class OffhandInteractModule extends Module {
      private boolean sendingSynthetic;

      OffhandInteractModule() {
         super("offhand-interact-placement", "OffhandInteract", ModuleCategory.PLAYER, "Uses the offhand too.");
         this.add(new BoolSetting("cancelMainhand", "Cancel Mainhand", false));
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         if (!this.sendingSynthetic && MC.gameMode != null && MC.player != null) {
            if (!(var1 instanceof ServerboundUseItemOnPacket var2 && var2.getHand() == InteractionHand.MAIN_HAND)) {
               return false;
            } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
               return false;
            } else if (RiptideHandArbiter.offhandClaimedByOther(this.id())) {
               return false;
            } else if (AutoTotemModule.operationActive()) {
               return false;
            } else if (ModuleRegistry.shouldCancelUseExcept(var2.getHitResult(), InteractionHand.OFF_HAND, this.id())) {
               return false;
            } else {
               String var3 = RiptidePlacementTick.owner();
               if (var3 != null && !var3.equals(this.id())) {
                  return false;
               } else if (!RiptidePlacementTick.claim(this.id())) {
                  return false;
               } else {
                  this.sendingSynthetic = true;

                  try {
                     MC.gameMode.useItemOn(MC.player, InteractionHand.OFF_HAND, var2.getHitResult());
                  } finally {
                     this.sendingSynthetic = false;
                  }

                  return this.bool("cancelMainhand");
               }
            }
         } else {
            return false;
         }
      }
   }

   static final class PacketCancellerModule extends Module {
      private String cachedC2SValue = null;
      private String cachedS2CValue = null;
      private Set<Class<? extends Packet<?>>> cachedC2S = Set.of();
      private Set<Class<? extends Packet<?>>> cachedS2C = Set.of();

      PacketCancellerModule() {
         super("packet-canceller", "PacketCanceller", ModuleCategory.MISC, "Cancels selected packets.");
         this.add(new PacketListSetting("C2S-packets", "C2S Packets", "").description("Outgoing packet list."));
         this.add(new PacketListSetting("S2C-packets", "S2C Packets", "").description("Incoming packet list."));
      }

      @Override
      public boolean onPacketSend(Packet<?> var1) {
         return this.matches(var1, true);
      }

      @Override
      public boolean onPacketReceive(Packet<?> var1) {
         return this.matches(var1, false);
      }

      @Override
      public String info() {
         return "C2S " + this.packets(true).size() + " | S2C " + this.packets(false).size();
      }

      private boolean matches(Packet<?> var1, boolean var2) {
         if (var1 == null) {
            return false;
         } else {
            return this.packets(var2).contains(var1.getClass()) ? true : this.legacyMatches(var2 ? this.text("C2S-packets") : this.text("S2C-packets"), var1);
         }
      }

      private Set<Class<? extends Packet<?>>> packets(boolean var1) {
         String var2 = var1 ? this.text("C2S-packets") : this.text("S2C-packets");
         if (var1) {
            if (!var2.equals(this.cachedC2SValue)) {
               this.cachedC2SValue = var2;
               this.cachedC2S = PacketListCodec.resolvePackets(var2, true);
            }

            return this.cachedC2S;
         } else {
            if (!var2.equals(this.cachedS2CValue)) {
               this.cachedS2CValue = var2;
               this.cachedS2C = PacketListCodec.resolvePackets(var2, false);
            }

            return this.cachedS2C;
         }
      }

      private boolean legacyMatches(String var1, Packet<?> var2) {
         if (var2 != null && var1 != null && !var1.isBlank()) {
            String var3 = var2.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String var4 = var2.getClass().getName().toLowerCase(Locale.ROOT);
            Class var5 = var2.getClass();
            String var6 = String.valueOf(RiptidePacketRegistry.getName(var5)).toLowerCase(Locale.ROOT);

            for (String var10 : var1.split("[,|]")) {
               String var11 = var10.trim().toLowerCase(Locale.ROOT);
               if (!var11.isEmpty() && (var3.equals(var11) || var4.equals(var11) || var6.equals(var11))) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   static final class ParkourModule extends Module {
      private boolean releaseJumpNextTick;

      ParkourModule() {
         super("parkour", "Parkour", ModuleCategory.MOVEMENT, "Jumps at block edges.");
      }

      @Override
      public void tick() {
         if (MC.player != null && MC.level != null && !MultiPilot.isActive()) {
            if (this.releaseJumpNextTick) {
               RiptideKeyMappingBridge.of(MC.options.keyJump).riptide$simulatePress(false);
               this.releaseJumpNextTick = false;
            } else if (MC.player.onGround()
               && !MC.player.isShiftKeyDown()
               && !MC.options.keyShift.isDown()
               && !MC.options.keyJump.isDown()
               && !(MC.player.input.getMoveVector().lengthSquared() <= 0.0F)
               && !this.onGroundNextTick()) {
               RiptideKeyMappingBridge.of(MC.options.keyJump).riptide$simulatePress(true);
               this.releaseJumpNextTick = true;
            }
         }
      }

      private boolean onGroundNextTick() {
         Vec3 var1 = MC.player.getDeltaMovement();
         AABB var2 = MC.player.getBoundingBox().move(var1.x, var1.y - 0.08, var1.z).expandTowards(0.0, -0.02, 0.0);
         return !MC.level.noCollision(MC.player, var2);
      }

      @Override
      public void onDisable() {
         if (this.releaseJumpNextTick) {
            this.releaseJumpNextTick = false;
            if (MC != null && MC.options != null) {
               RiptideKeyMappingBridge.of(MC.options.keyJump).riptide$simulatePress(false);
            }
         }
      }
   }

   static final class SneakModule extends Module {
      private boolean sentPacketSneak;

      SneakModule() {
         super("sneak", "Sneak", ModuleCategory.MOVEMENT, "Keeps sneak held for you.");
         this.add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Packet").description("Which method to sneak.").build());
      }

      boolean holdsSneakDown() {
         return this.isEnabled()
            && !MultiPilot.isActive()
            && !PacketTeleportController.ownsMainMovement()
            && !"Packet".equals(this.choice("mode"))
            && MC != null
            && MC.options != null
            && MC.player != null
            && !MC.player.getAbilities().flying;
      }

      @Override
      public void tick() {
         this.holdSneak();
      }

      @Override
      public void preMovementTick() {
         if (!"Packet".equals(this.choice("mode"))) {
            this.holdSneak();
         }
      }

      private void holdSneak() {
         if (!MultiPilot.isActive()
            && !PacketTeleportController.ownsMainMovement()
            && MC.options != null
            && MC.player != null
            && !MC.player.getAbilities().flying) {
            if ("Packet".equals(this.choice("mode"))) {
               this.sendInputSneak(true);
               this.sentPacketSneak = true;
            } else {
               MC.options.keyShift.setDown(true);
            }
         }
      }

      @Override
      public void onDisable() {
         if (MultiPilot.isActive()) {
            this.sentPacketSneak = false;
         } else {
            if (this.sentPacketSneak) {
               this.sendInputSneak(false);
               this.sentPacketSneak = false;
            }

            if (MC.options != null) {
               MC.options.keyShift.setDown(false);
            }
         }
      }

      private void sendInputSneak(boolean var1) {
         if (MC.options != null && MC.getConnection() != null) {
            Input var2 = new Input(
               MC.options.keyUp.isDown(),
               MC.options.keyDown.isDown(),
               MC.options.keyLeft.isDown(),
               MC.options.keyRight.isDown(),
               MC.options.keyJump.isDown(),
               var1,
               MC.options.keySprint.isDown()
            );
            MC.getConnection().send(new ServerboundPlayerInputPacket(var2));
         }
      }
   }

   static final class SpamModule extends Module {
      private int timer;
      private int index;

      SpamModule() {
         super("spam", "Spam", ModuleCategory.MISC, "Sends timed chat.");
         this.add(new StringListSetting("messages", "Messages", "你好，世界！").description("Messages separated by |."));
         this.add(new IntSetting("delay", "Delay", 20, 0, 200, 1));
         this.add(new BoolSetting("disable-on-leave", "Disable On Leave", true).group("Safety"));
         this.add(new BoolSetting("disable-on-disconnect", "Disable On Disconnect", true).group("Safety"));
         this.add(new BoolSetting("randomise", "Randomise", false));
         this.add(new BoolSetting("auto-split-messages", "Auto Split", false).group("Splitting"));
         this.add(
            new IntSetting("split-length", "Split Length", 256, 8, 256, 8)
               .group("Splitting")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("auto-split-messages")))
               .build()
         );
         this.add(
            new IntSetting("split-delay", "Split Delay", 20, 0, 200, 1)
               .group("Splitting")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("auto-split-messages")))
               .build()
         );
         this.add(new BoolSetting("bypass", "Bypass", false).group("Bypass"));
         this.add(
            new BoolSetting("include-uppercase-characters", "Uppercase", false)
               .group("Bypass")
               .visibleWhen(() -> Boolean.parseBoolean(this.value("bypass")))
               .build()
         );
         this.add(new IntSetting("length", "Bypass Length", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> Boolean.parseBoolean(this.value("bypass"))).build());
         this.add(new BoolSetting("uppercase", "Uppercase Legacy", false).group("Bypass").visibleWhen(() -> false).build());
         this.add(new IntSetting("bypass-length", "Bypass Length Legacy", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> false).build());
      }

      @Override
      public void onEnable() {
         this.timer = this.integer("delay");
         this.index = 0;
      }

      @Override
      public void tick() {
         if (MC.getConnection() != null && this.timer-- <= 0) {
            this.timer = this.integer("delay");
            List var1 = this.splitMessages();
            if (!var1.isEmpty()) {
               int var2 = this.bool("randomise") ? new Random().nextInt(var1.size()) : this.index++ % var1.size();
               String var3 = (String)var1.get(var2);
               if (this.bool("bypass")) {
                  var3 = var3 + " " + this.bypassToken();
               }

               if (this.bool("auto-split-messages") && var3.length() > this.integer("split-length")) {
                  int var4 = Math.max(1, this.integer("split-length"));
                  var3 = var3.substring(0, var4);
                  this.timer = this.integer("split-delay");
               } else if (var3.length() > 256) {
                  var3 = var3.substring(0, 256);
               }

               if (var3.startsWith("/") && var3.length() > 1) {
                  MC.getConnection().sendCommand(var3.substring(1));
               } else {
                  RiptideCommands.sendPlainChat(MC.getConnection(), var3);
               }
            }
         }
      }

      @Override
      public void onGameLeft() {
         if (this.bool("disable-on-leave") || this.bool("disable-on-disconnect")) {
            this.setEnabled(false);
         }
      }

      private List<String> splitMessages() {
         ArrayList var1 = new ArrayList();

         for (String var5 : this.text("messages").split("\\|")) {
            String var6 = var5.trim();
            if (!var6.isEmpty()) {
               var1.add(var6);
            }
         }

         return var1;
      }

      private String bypassToken() {
         String var1 = !this.bool("include-uppercase-characters") && !this.bool("uppercase")
            ? "abcdefghijklmnopqrstuvwxyz0123456789"
            : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
         Random var2 = new Random();
         StringBuilder var3 = new StringBuilder();
         int var4 = this.integer("length") == 4 && this.integer("bypass-length") != 4 ? this.integer("bypass-length") : this.integer("length");

         for (int var5 = 0; var5 < var4; var5++) {
            var3.append(var1.charAt(var2.nextInt(var1.length())));
         }

         return var3.toString();
      }
   }

   static final class SpawnerEspModule extends Module {
      SpawnerEspModule() {
         super("spawner-esp", "SpawnerESP", ModuleCategory.RENDER, "Highlights monster spawners by spawned mob.");
         this.add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
         this.add(new BoolSetting("meshing", "Meshing", true).group("General").description("Merge adjacent spawners."));
         this.add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each spawner"));
         this.add(
            RegistryListSetting.entityTypes("spawner-list", "Spawners", ModuleSpawnerEsp.NATURAL_DEFAULT_VALUE)
               .group("Spawners")
               .description("Mobs to highlight.")
         );

         for (ModuleSpawnerEsp.NaturalTarget var2 : ModuleSpawnerEsp.NATURAL) {
            String var3 = var2.id();
            this.add(
               new ColorSetting("color-" + var3, var2.label(), var2.color()).group("Colors").visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, var3))
            );
         }
      }

      @Override
      public void onOptionValueChanged(String var1) {
         if ("spawner-list".equals(var1)) {
            this.syncCustomColorSettings();
         }
      }

      private void syncCustomColorSettings() {
         for (String var2 : ModuleSpawnerEsp.enabledIds(this)) {
            if (!ModuleSpawnerEsp.isNatural(var2)) {
               String var3 = "color-" + var2;
               if (this.setting(var3) == null) {
                  String var4 = RiptideRegistryLabels.entity(var2);
                  this.add(
                     new ColorSetting(var3, var4.isBlank() ? var2 : var4, -855688389)
                        .group("Colors")
                        .visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, var2))
                  );
               }
            }
         }
      }
   }

   static final class SpeedModule extends Module {
      private int strafeStage;
      private double strafeSpeed;
      private double lastDistance;
      private long speedLimitTimer;
      private String lastSpeedMode = "Vanilla";
      private Boolean meteorSpeedActiveCache;
      private long meteorSpeedActiveCacheAt;

      SpeedModule() {
         super("speed", "Speed", ModuleCategory.MOVEMENT, "Boosts movement speed.");
         this.add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Strafe").description("Speed method").build());
         this.add(
            new DoubleSetting("vanilla-speed", "Vanilla Speed", 5.6, 0.0, 20.0, 0.1)
               .visibleWhen(() -> "Vanilla".equals(this.value("mode")))
               .description("Blocks per second")
               .build()
         );
         this.add(
            new DoubleSetting("strafe-speed", "Strafe Speed", 1.6, 0.0, 3.0, 0.05)
               .visibleWhen(() -> "Strafe".equals(this.value("mode")))
               .description("Strafe speed multiplier.")
               .build()
         );
         this.add(
            new BoolSetting("speed-limit", "Speed Limit", false).visibleWhen(() -> "Strafe".equals(this.value("mode"))).description("Limit max speed.").build()
         );
         this.add(new DoubleSetting("timer", "Timer", 1.0, 0.01, 10.0, 0.05).description("Client timer speed."));
         this.add(new BoolSetting("in-liquids", "In Liquids", false));
         this.add(new BoolSetting("when-sneaking", "When Sneaking", false));
         this.add(new BoolSetting("only-on-ground", "Only Ground", false).visibleWhen(() -> "Vanilla".equals(this.value("mode"))).build());
      }

      @Override
      public void onEnable() {
         this.lastSpeedMode = this.choice("mode");
         this.resetStrafe();
      }

      @Override
      public void onDisable() {
         this.resetStrafe();
      }

      @Override
      public void preMovementTick() {
         if (MC.player != null && !this.shouldYieldToMeteorSpeed() && !this.stopSpeed()) {
            this.syncSpeedMode();
            if (this.isInLiquid()) {
               this.resetStrafe();
            } else {
               this.lastDistance = Math.sqrt(
                  (MC.player.getX() - MC.player.xo) * (MC.player.getX() - MC.player.xo) + (MC.player.getZ() - MC.player.zo) * (MC.player.getZ() - MC.player.zo)
               );
            }
         }
      }

      @Override
      public Vec3 onPlayerMove(MoverType var1, Vec3 var2) {
         if (var1 != MoverType.SELF || MC.player == null || MC.options == null || var2 == null) {
            return var2;
         } else if (!this.shouldYieldToMeteorSpeed() && !this.stopSpeed()) {
            this.syncSpeedMode();
            if (this.isInLiquid()) {
               return this.liquidMovement(var2);
            } else {
               return "Strafe".equals(this.choice("mode")) ? this.strafeMovement(var2) : this.vanillaMovement(var2);
            }
         } else {
            return var2;
         }
      }

      @Override
      public boolean onPacketReceive(Packet<?> var1) {
         if (var1 instanceof ClientboundPlayerPositionPacket) {
            this.resetStrafe();
         }

         return false;
      }

      @Override
      public boolean shouldApplySpeedTimer() {
         return !this.shouldYieldToMeteorSpeed() && !this.stopSpeed() && this.isMoving();
      }

      @Override
      public float speedTimerMultiplier() {
         if (!this.shouldApplySpeedTimer()) {
            return 1.0F;
         } else {
            try {
               return Math.max(0.01F, Math.min(10.0F, (float)this.decimal("timer")));
            } catch (Exception var2) {
               return 1.0F;
            }
         }
      }

      private Vec3 vanillaMovement(Vec3 var1) {
         Vec3 var2 = this.horizontalVelocity(this.decimal("vanilla-speed"));
         double var3 = var2.x;
         double var5 = var2.z;
         if (MC.player.hasEffect(MobEffects.SPEED)) {
            MobEffectInstance var7 = MC.player.getEffect(MobEffects.SPEED);
            if (var7 != null) {
               double var8 = (var7.getAmplifier() + 1) * 0.205;
               var3 += var3 * var8;
               var5 += var5 * var8;
            }
         }

         return new Vec3(var3, var1.y, var5);
      }

      private Vec3 liquidMovement(Vec3 var1) {
         if (!"Strafe".equals(this.choice("mode"))) {
            return this.vanillaMovement(var1);
         } else {
            double var2 = this.defaultSpeed() * Math.max(0.0, this.decimal("strafe-speed"));
            Vec3 var4 = this.transformStrafe(var2);
            return new Vec3(var4.x, var1.y, var4.z);
         }
      }

      Vec3 afterLiquidTravel(Vec3 var1) {
         if (var1 == null || MC.player == null || !this.bool("in-liquids") || !this.isInLiquid()) {
            return var1;
         } else if (!this.shouldYieldToMeteorSpeed() && !this.stopSpeed()) {
            this.syncSpeedMode();
            return this.liquidMovement(var1);
         } else {
            return var1;
         }
      }

      private Vec3 strafeMovement(Vec3 var1) {
         if (this.strafeStage == 0 && this.isMoving()) {
            this.strafeStage = 1;
            this.strafeSpeed = 1.18F * this.defaultSpeed() - 0.01;
         }

         if (this.strafeStage == 1) {
            if (this.isMoving() && MC.player.onGround()) {
               var1 = new Vec3(var1.x, this.hop(0.40123128), var1.z);
               this.strafeSpeed = this.strafeSpeed * this.decimal("strafe-speed");
               this.strafeStage = 2;
            }
         } else if (this.strafeStage == 2) {
            this.strafeSpeed = this.lastDistance - 0.76 * (this.lastDistance - this.defaultSpeed());
            this.strafeStage = 3;
         } else if (this.strafeStage == 3) {
            if (!this.hasVerticalMoveSpace() || MC.player.verticalCollision && this.strafeStage > 0) {
               this.strafeStage = 0;
            }

            this.strafeSpeed = this.lastDistance - this.lastDistance / 159.0;
         } else if (this.strafeStage > 3 || this.strafeStage < 0) {
            this.strafeStage = 0;
         }

         this.strafeSpeed = Math.max(this.strafeSpeed, this.defaultSpeed());
         if (this.bool("speed-limit")) {
            long var2 = System.currentTimeMillis();
            if (var2 - this.speedLimitTimer > 2500L) {
               this.speedLimitTimer = var2;
            }

            this.strafeSpeed = Math.min(this.strafeSpeed, var2 - this.speedLimitTimer > 1250L ? 0.44 : 0.43);
         }

         Vec3 var4 = this.transformStrafe(this.strafeSpeed);
         return new Vec3(var4.x, var1.y, var4.z);
      }

      private Vec3 horizontalVelocity(double var1) {
         if (MC.player != null && MC.player.input != null) {
            double var3 = var1 / 20.0;
            Vec3 var5 = Vec3.directionFromRotation(0.0F, MC.player.getYRot());
            Vec3 var6 = Vec3.directionFromRotation(0.0F, MC.player.getYRot() + 90.0F);
            double var7 = 0.0;
            double var9 = 0.0;
            boolean var11 = false;
            if (MC.options.keyUp.isDown()) {
               var7 += var5.x * var3;
               var9 += var5.z * var3;
               var11 = true;
            }

            if (MC.options.keyDown.isDown()) {
               var7 -= var5.x * var3;
               var9 -= var5.z * var3;
               var11 = true;
            }

            boolean var12 = false;
            if (MC.options.keyRight.isDown()) {
               var7 += var6.x * var3;
               var9 += var6.z * var3;
               var12 = true;
            }

            if (MC.options.keyLeft.isDown()) {
               var7 -= var6.x * var3;
               var9 -= var6.z * var3;
               var12 = true;
            }

            if (var11 && var12) {
               double var13 = 1.0 / Math.sqrt(2.0);
               var7 *= var13;
               var9 *= var13;
            }

            return new Vec3(var7, 0.0, var9);
         } else {
            return Vec3.ZERO;
         }
      }

      private Vec3 transformStrafe(double var1) {
         if (MC.player != null && MC.player.input != null) {
            Vec2 var3 = MC.player.input.getMoveVector();
            float var4 = Math.signum(var3.y);
            float var5 = Math.signum(var3.x);
            if (var4 == 0.0F && var5 == 0.0F) {
               return Vec3.ZERO;
            } else {
               float var6 = MC.player.getYRot(this.movementPartialTick());
               float var7 = 90.0F * var5;
               if (var4 != 0.0F) {
                  var7 *= var4 * 0.5F;
               }

               var6 -= var7;
               if (var4 < 0.0F) {
                  var6 -= 180.0F;
               }

               double var8 = Math.toRadians(var6);
               return new Vec3(-Math.sin(var8) * var1, 0.0, Math.cos(var8) * var1);
            }
         } else {
            return Vec3.ZERO;
         }
      }

      private float movementPartialTick() {
         try {
            return MC.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         } catch (Exception var2) {
            return 1.0F;
         }
      }

      private double defaultSpeed() {
         double var1 = 0.2873;
         if (MC.player.hasEffect(MobEffects.SPEED)) {
            MobEffectInstance var3 = MC.player.getEffect(MobEffects.SPEED);
            if (var3 != null) {
               var1 *= 1.0 + 0.2 * (var3.getAmplifier() + 1);
            }
         }

         if (MC.player.hasEffect(MobEffects.SLOWNESS)) {
            MobEffectInstance var4 = MC.player.getEffect(MobEffects.SLOWNESS);
            if (var4 != null) {
               var1 /= 1.0 + 0.2 * (var4.getAmplifier() + 1);
            }
         }

         return var1;
      }

      private double hop(double var1) {
         MobEffectInstance var3 = MC.player.getEffect(MobEffects.JUMP_BOOST);
         return var3 == null ? var1 : var1 + (var3.getAmplifier() + 1) * 0.1F;
      }

      private boolean hasVerticalMoveSpace() {
         return MC.level != null && MC.level.noCollision(MC.player.getBoundingBox().move(0.0, MC.player.getDeltaMovement().y, 0.0));
      }

      private boolean stopSpeed() {
         if (MC.player == null) {
            return true;
         } else if (MC.player.isFallFlying() || MC.player.onClimbable() || MC.player.getVehicle() != null) {
            return true;
         } else if (!this.bool("when-sneaking") && MC.player.isShiftKeyDown()) {
            return true;
         } else {
            boolean var1 = this.isInLiquid();
            boolean var2 = var1 && this.bool("in-liquids");
            return this.bool("only-on-ground") && !MC.player.onGround() && "Vanilla".equals(this.choice("mode")) && !var2 ? true : var1 && !var2;
         }
      }

      private boolean isMoving() {
         if (MC.player == null) {
            return false;
         } else {
            if (MC.player.input != null) {
               Vec2 var1 = MC.player.input.getMoveVector();
               if (var1.x != 0.0F || var1.y != 0.0F) {
                  return true;
               }
            }

            return MC.player.xxa != 0.0F || MC.player.zza != 0.0F;
         }
      }

      private boolean isInLiquid() {
         return MC.player != null && (MC.player.isInWater() || MC.player.isInLava());
      }

      private void syncSpeedMode() {
         String var1 = this.choice("mode");
         if (!var1.equals(this.lastSpeedMode)) {
            this.lastSpeedMode = var1;
            this.resetStrafe();
         }
      }

      private boolean shouldYieldToMeteorSpeed() {
         long var1 = System.currentTimeMillis();
         if (this.meteorSpeedActiveCache != null && var1 - this.meteorSpeedActiveCacheAt < 250L) {
            return this.meteorSpeedActiveCache;
         } else {
            this.meteorSpeedActiveCacheAt = var1;
            this.meteorSpeedActiveCache = this.queryMeteorSpeedActive();
            return this.meteorSpeedActiveCache;
         }
      }

      private boolean queryMeteorSpeedActive() {
         try {
            Class var1 = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, BuiltinModules.SpeedModule.class.getClassLoader());
            Class var2 = Class.forName(
               "meteordevelopment.meteorclient.systems.modules.movement.speed.Speed", false, BuiltinModules.SpeedModule.class.getClassLoader()
            );
            Object var3 = var1.getMethod("get").invoke(null);
            Object var4 = var1.getMethod("get", Class.class).invoke(var3, var2);
            return var4 != null && Boolean.TRUE.equals(var4.getClass().getMethod("isActive").invoke(var4));
         } catch (Throwable var5) {
            return false;
         }
      }

      private void resetStrafe() {
         this.strafeStage = 0;
         this.strafeSpeed = 0.2873;
         this.lastDistance = 0.0;
      }
   }

   static final class SprintModule extends Module {
      SprintModule() {
         super("sprint", "Sprint", ModuleCategory.MOVEMENT, "Sprints automatically.");
         this.add(new BoolSetting("ignore-blindness", "Blindness", false).description("Sprint while blind").group("Ignore").build());
         this.add(new BoolSetting("ignore-collision", "Collision", false).description("Sprint against walls").group("Ignore").build());
      }

      boolean sprintDecision(boolean var1, boolean var2) {
         boolean var3 = var1;
         if (this.isMoving()) {
            var3 = true;
         }

         if (var2 && this.shouldPreventSprint()) {
            var3 = false;
         }

         return var3;
      }

      private boolean shouldPreventSprint() {
         if (((RiptideLocalPlayerAccessor)MC.player).riptide$isSlowDueToUsingItem()) {
            return true;
         } else if (MC.player.isShiftKeyDown()) {
            return true;
         } else {
            RiptideRotationUtil.Rotation var1 = RiptideSilentAim.packetRotation();
            if (var1 == null) {
               return false;
            } else {
               float var2 = (MC.player.getYRot() - var1.yaw()) * (float) (Math.PI / 180.0);
               Vec2 var3 = MC.player.input.getMoveVector();
               boolean var4 = var3.y * Mth.cos(var2) + var3.x * Mth.sin(var2) > 1.0E-5F;
               return !var4;
            }
         }
      }

      boolean shouldPreventSprintPublic() {
         return this.shouldPreventSprint();
      }

      boolean omnidirectional() {
         return false;
      }

      boolean jumpUsesMovementYaw() {
         return false;
      }

      boolean ignoreCollision() {
         return this.bool("ignore-collision");
      }

      boolean ignoreBlindness() {
         return this.bool("ignore-blindness");
      }

      private boolean isMoving() {
         return MC.player.input.getMoveVector().lengthSquared() > 0.0F;
      }
   }

   static final class StorageEspModule extends Module {
      StorageEspModule() {
         super("storage-esp", "StorageESP", ModuleCategory.RENDER, "Highlights chests, shulkers, and other storage.");
         this.add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
         this.add(new BoolSetting("meshing", "Meshing", true).group("General").description("Merge adjacent storage."));
         this.add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each target"));
         this.add(RegistryListSetting.storages("storage-list", "Storage", ModuleStorageEsp.DEFAULT_VALUE).group("Storage").description("Blocks and entities"));
         this.add(new ColorSetting("chest-color", "Chest", -855662592).group("Colors").description("Chest + chest minecart/boat."));
         this.add(new ColorSetting("trapped-chest-color", "Trapped Chest", -855695328).group("Colors"));
         this.add(new ColorSetting("ender-chest-color", "Ender Chest", -864550657).group("Colors"));
         this.add(new ColorSetting("barrel-color", "Barrel", -855662592).group("Colors"));
         this.add(new ColorSetting("shulker-color", "Shulker Box", -860395777).group("Colors"));
         this.add(new ColorSetting("hopper-color", "Hopper", -864253185).group("Colors").description("Hopper + minecart."));
         this.add(new ColorSetting("dispenser-color", "Dispenser", -860862392).group("Colors").description("Dispenser + dropper."));
         this.add(new ColorSetting("furnace-color", "Furnace", -859000218).group("Colors").description("Furnaces + brewing stand."));
         this.add(new ColorSetting("crafter-color", "Crafter", -858214805).group("Colors").description("Crafter, pot, bookshelf"));
         this.add(new ColorSetting("other-color", "Other", -7566196).group("Colors").description("Fallback."));

         for (ModuleStorageStructures.Structure var2 : ModuleStorageStructures.ALL) {
            this.add(new BoolSetting(var2.settingId(), var2.label(), false).group("Ignore Structures"));
         }

         RiptideEspExtras.addRange(this);
      }
   }

   static final class TracersModule extends Module {
      TracersModule() {
         super("tracers", "Tracers", ModuleCategory.RENDER, "Draws tracer lines to entities.");
         this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Traced entities.").build());
         this.add(new IntSetting("max-distance", "Max Distance", 256, 0, 512, 16));
         this.add(new DoubleSetting("line-width", "Line Width", 2.0, 2.0, 6.0, 0.25).group("General").description("Tracer thickness."));
         this.add(new BoolSetting("height-line", "Height Line", false).group("General").description("Vertical line up the entity."));
         this.add(new BoolSetting("distance-color", "Distance Color", false).group("Colors").description("Color by distance"));
         this.add(
            new IntSetting("color-distance", "Color Distance", 40, 8, 256, 4)
               .group("Colors")
               .description("Far color distance")
               .visibleWhen(() -> this.bool("distance-color"))
         );
         this.add(new ColorSetting("players-color", "Players", -855638017).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
         this.add(new ColorSetting("animals-color", "Animals", -864747633).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
         this.add(new ColorSetting("water-animals-color", "Water Animals", -865674753).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
         this.add(new ColorSetting("monsters-color", "Monsters", -855684534).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
         this.add(new ColorSetting("ambient-color", "Ambient", -860386049).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
         this.add(new ColorSetting("misc-color", "Misc", -858993460).group("Colors").visibleWhen(() -> !this.bool("distance-color")));
      }

      @Override
      public boolean shouldTraceEntity(Entity var1) {
         return ModuleRenderUtil.shouldTrace(var1);
      }

      @Override
      public int traceColor(Entity var1) {
         return ModuleRenderUtil.tracerColor(var1);
      }
   }

   static final class TriggerBotModule extends Module {
      private long readyAtNanos;
      private boolean armed;
      private String cachedEntityListSource = "";
      private Set<String> cachedEntityIds = Set.of();

      TriggerBotModule() {
         super("trigger-bot", "TriggerBot", ModuleCategory.COMBAT, "Attacks configured entities when your crosshair is on them.");
         this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Trigger entities").build());
         this.add(new BoolSetting("respect-cooldown", "Respect Cooldown", true).description("Wait for full cooldown"));
         this.add(new BoolSetting("randomize-delay", "Randomize Delay", false).description("Small delay after cooldown."));
      }

      @Override
      public void onEnable() {
         this.resetTrigger();
      }

      @Override
      public void onDisable() {
         this.resetTrigger();
      }

      @Override
      public void tick() {
         if (MC != null
            && MC.player != null
            && MC.level != null
            && MC.gui.screen() == null
            && !ScaffoldModule.ownsTellyInput()
            && !ScaffoldModule.reservesRageInput()
            && RiptideSilentAim.packetRotation() == null
            && !ScaffoldModule.hasActiveSilentMovementRotation()
            && !BuiltinModules.ownsManualFastExp()
            && !AutoTotemModule.operationActive()) {
            if (MC.hitResult instanceof EntityHitResult var1 && this.matchesTarget(var1.getEntity())) {
               boolean var5 = this.bool("respect-cooldown");
               if (var5 && MC.player.getAttackStrengthScale(0.0F) < 1.0F) {
                  this.resetTrigger();
               } else {
                  long var3 = System.nanoTime();
                  if (var5 && this.bool("randomize-delay")) {
                     if (!this.armed) {
                        this.readyAtNanos = var3 + RiptideHumanRng.triggerJitterMs() * 1000000L;
                        this.armed = true;
                     }

                     if (var3 < this.readyAtNanos) {
                        return;
                     }
                  }

                  RiptideInputClicker.queueAttackClick();
                  this.resetTrigger();
               }
            } else {
               this.resetTrigger();
            }
         } else {
            this.resetTrigger();
         }
      }

      private boolean matchesTarget(Entity var1) {
         if (var1 == null || var1 == MC.player) {
            return false;
         } else if (RiptideAntiBot.suppress(var1)) {
            return false;
         } else if (TeamsModule.combatExcluded(var1, "triggerbot")) {
            return false;
         } else {
            Set var2 = this.cachedEntityIds();
            if (var2.isEmpty()) {
               return false;
            } else {
               String var3 = BuiltInRegistries.ENTITY_TYPE.getKey(var1.getType()).toString().toLowerCase(Locale.ROOT);
               return var2.contains(var3) || var2.contains(var3.substring(var3.indexOf(58) + 1));
            }
         }
      }

      private Set<String> cachedEntityIds() {
         List var1 = this.list("entities");
         String var2 = String.join("|", var1);
         if (var2.equals(this.cachedEntityListSource)) {
            return this.cachedEntityIds;
         } else {
            LinkedHashSet var3 = new LinkedHashSet();

            for (String var5 : var1) {
               if (var5 != null) {
                  String var6 = var5.trim().toLowerCase(Locale.ROOT);
                  if (!var6.isEmpty()) {
                     var3.add(var6);
                     int var7 = var6.indexOf(58);
                     if (var7 >= 0 && var7 + 1 < var6.length()) {
                        var3.add(var6.substring(var7 + 1));
                     }
                  }
               }
            }

            this.cachedEntityListSource = var2;
            this.cachedEntityIds = Set.copyOf(var3);
            return this.cachedEntityIds;
         }
      }

      private void resetTrigger() {
         this.readyAtNanos = 0L;
         this.armed = false;
      }
   }

   static final class XCarryModule extends Module {
      XCarryModule() {
         super("xcarry", "XCarry", ModuleCategory.PLAYER, "Keeps stored items in crafting / result / armor / offhand slots when closing inventory.");
         this.add(new BoolSetting("use-crafting", "Use Crafting Grid", true).description("Use all craft slots").build());
         this.add(new BoolSetting("use-armor", "Use Armor Slots", true).description("Use armor slots 5-8.").build());
         this.add(new BoolSetting("use-offhand", "Use Offhand", true).description("Use offhand slot.").build());
         this.add(new BoolSetting("carry-cursor", "Carry Cursor", true).description("Keep cursor stack.").build());
      }

      @Override
      public void onEnable() {
         RiptideConfig.getGlobal().xCarry = true;
      }

      @Override
      public void onDisable() {
         RiptideConfig.getGlobal().xCarry = false;
      }
   }

   static final class XrayModule extends Module {
      private String lastTintKey = "";

      XrayModule() {
         super("xray", "Xray", ModuleCategory.RENDER, "Shows selected blocks.");
         this.add(new ChoiceSetting("mode", "Mode", "Normal", "Normal", "OreSim").description("Real blocks or seed.").build());
         this.add(new ChoiceSetting("render-style", "Rendering Style", "Default", "Default", "ESP").description("See-through or boxes.").build());
         this.add(
            RegistryListSetting.blocks("whitelist", "Whitelist", defaultOreList()).description("Blocks to show.").visibleWhen(() -> !this.oreSimMode()).build()
         );
         this.add(
            RegistryListSetting.oreSimOres("oresim-ores", "Ores", String.join("|", RiptideOreSimOre.ORE_SIM_BLOCK_IDS))
               .description("Ores to predict.")
               .visibleWhen(this::oreSimMode)
               .build()
         );
         this.add(new IntSetting("opacity", "Opacity", 25, 0, 255, 1).description("Hidden block alpha.").visibleWhen(this::tintStyle).build());
         this.add(
            new ChoiceSetting("fluid-opacity", "Fluid Opacity", "Both", "None", "Water", "Lava", "Both")
               .description("Visible fluids.")
               .visibleWhen(this::tintStyle)
               .build()
         );
         this.add(
            new BoolSetting("exposed-only", "Exposed Only", false)
               .description("Only exposed ores.")
               .visibleWhen(() -> this.tintStyle() && !this.oreSimMode())
               .build()
         );
         this.add(new BoolSetting("fill", "Fill", true).description("Shade box interiors.").visibleWhen(this::espStyle).build());
         this.add(
            new StringSetting("oresim-seed", "World Seed", "")
               .description("Seed for this world")
               .keepOnReset()
               .requiresWorld()
               .visibleWhen(this::oreSimMode)
               .build()
         );
         this.add(
            new IntSetting("oresim-radius", "Simulation Radius", 2, 1, 8, 1).description("Chunk radius to simulate").visibleWhen(this::oreSimMode).build()
         );

         for (RiptideOreSimOre.Kind var4 : RiptideOreSimOre.Kind.values()) {
            this.add(
               new ColorSetting(var4.colorId(), var4.label, var4.defaultColor)
                  .group("Colors")
                  .visibleWhen(() -> this.espStyle() && ModuleOreSim.whitelistHasFamily(this, var4))
                  .build()
            );
         }

         this.syncCustomColorSettings();
      }

      private boolean tintStyle() {
         return !this.espStyle();
      }

      private boolean espStyle() {
         return "ESP".equals(this.value("render-style"));
      }

      private boolean oreSimMode() {
         return "OreSim".equals(this.value("mode"));
      }

      @Override
      protected String externalSettingValue(String var1) {
         if (!"oresim-seed".equals(var1)) {
            return null;
         } else {
            RiptideConfig.ModuleState var2 = RiptideConfig.getGlobal().modules.get(this.id());
            String var3 = var2 != null && var2.settings != null ? var2.settings.getOrDefault(var1, "") : "";
            return RiptideOreSimSeedStore.get().value(RiptideWaypoints.scopeKey(MC), var3);
         }
      }

      @Override
      protected boolean setExternalSettingValue(String var1, String var2) {
         if (!"oresim-seed".equals(var1)) {
            return false;
         } else {
            RiptideOreSimSeedStore.get().put(RiptideWaypoints.scopeKey(MC), var2);
            return true;
         }
      }

      @Override
      public void onOptionValueChanged(String var1) {
         if ("whitelist".equals(var1)) {
            this.syncCustomColorSettings();
         }

         if ("oresim-seed".equals(var1)) {
            RiptideOreSimEngine.clear();
         }
      }

      private void syncCustomColorSettings() {
         for (String var2 : ModuleOreSim.nonOreWhitelistIds(this)) {
            String var3 = "block-color-" + var2;
            if (this.setting(var3) == null) {
               String var4 = RiptideRegistryLabels.block(var2);
               this.add(
                  new ColorSetting(var3, var4.isBlank() ? var2 : var4, -3355444)
                     .group("Colors")
                     .visibleWhen(() -> this.espStyle() && !this.oreSimMode() && ModuleOreSim.whitelistHasId(this, var2))
                     .build()
               );
            }
         }
      }

      @Override
      public void onEnable() {
         if (this.tintStyle()) {
            ModuleRenderUtil.refreshWorldRenderer();
         }
      }

      @Override
      public void onDisable() {
         RiptideOreSimEngine.suspend();
         if (this.tintStyle()) {
            ModuleRenderUtil.refreshWorldRenderer();
         }
      }

      @Override
      public void onGameLeft() {
         RiptideOreSimEngine.clear();
         RiptideOreSimEngine.forgetDisproven();
         RiptideOreGhostModels.clear();
      }

      @Override
      public void tick() {
         String var1 = this.value("mode")
            + "|"
            + this.value("render-style")
            + "|"
            + this.integer("opacity")
            + "|"
            + this.value("fluid-opacity")
            + "|"
            + this.bool("exposed-only")
            + "|"
            + (this.oreSimMode() ? "" : this.value("whitelist"));
         if (!var1.equals(this.lastTintKey)) {
            this.lastTintKey = var1;
            ModuleRenderUtil.refreshWorldRenderer();
         }

         ModuleOreSim.tick(this, MC.level, MC.player);
      }

      @Override
      public String info() {
         return ModuleOreSim.info(this);
      }

      private static String defaultOreList() {
         return String.join("|", RiptideOreSimOre.ORE_SIM_BLOCK_IDS);
      }
   }
}
