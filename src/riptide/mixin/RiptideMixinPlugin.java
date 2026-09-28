package riptide.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class RiptideMixinPlugin implements IMixinConfigPlugin {
   private static final Set<String> SODIUM_MIXINS = Set.of(
      "RiptideSodiumBlockRendererMixin",
      "RiptideSodiumBlockContextMixin",
      "RiptideSodiumLightDataAccessMixin",
      "RiptideSodiumDefaultFluidRendererMixin",
      "RiptideSodiumFluidRendererImplMixin",
      "RiptideSodiumRenderSectionManagerMixin",
      "RiptideSodiumDefaultChunkRendererMixin"
   );
   private boolean sodiumLoaded;
   private boolean indigoLoaded;
   private boolean opsecLoaded;
   private boolean exploitPreventerLoaded;
   private boolean replayModLoaded;
   private boolean flashbackLoaded;
   private boolean essentialLoaded;
   private boolean lithiumLoaded;
   private static final Set<String> EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS = Set.of(
      "RiptideProtectorTranslatableContentsMixin",
      "RiptideProtectorKeybindContentsMixin",
      "RiptideProtectorComponentSerializationMixin",
      "RiptideProtectorPacketProcessorMixin",
      "RiptideProtectorPacketDecoderMixin",
      "RiptideProtectorHttpUtilMixin",
      "RiptideProtectorConnectionTrackingMixin",
      "RiptideProtectorDownloadQueueMixin",
      "RiptideProtectorClientLanguageMixin",
      "RiptideProtectorDeprecatedTranslationsInfoMixin",
      "RiptideProtectorOptionsMixin",
      "RiptideProtectorKeyMappingRegistryImplMixin",
      "RiptideProtectorPayloadTypeRegistryImplMixin",
      "RiptideProtectorResourceLoaderImplMixin"
   );

   public void onLoad(String mixinPackage) {
      FabricLoader loader = FabricLoader.getInstance();
      this.sodiumLoaded = loader.isModLoaded("sodium");
      this.indigoLoaded = loader.isModLoaded("fabric-renderer-indigo");
      this.opsecLoaded = loader.isModLoaded("opsec");
      this.exploitPreventerLoaded = loader.isModLoaded("exploitpreventer");
      this.replayModLoaded = loader.isModLoaded("replaymod");
      this.flashbackLoaded = loader.isModLoaded("flashback");
      this.essentialLoaded = loader.isModLoaded("essential") || loader.isModLoaded("essential-container") || loader.isModLoaded("essential-loader");
      this.lithiumLoaded = loader.isModLoaded("lithium");
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf(46) + 1);
      if (SODIUM_MIXINS.contains(simpleName)) {
         return this.sodiumLoaded;
      } else if ("RiptideFluidRendererMixin".equals(simpleName) && this.sodiumLoaded) {
         return false;
      } else if ("RiptideReplayModGuiHandlerMixin".equals(simpleName) || "RiptideReplayStudioTeamMixin".equals(simpleName)) {
         return this.replayModLoaded;
      } else if ("RiptideFlashbackConnectionMixin".equals(simpleName)) {
         return this.flashbackLoaded;
      } else if (simpleName.startsWith("RiptideEssential")) {
         return this.essentialLoaded;
      } else if (mixinClassName.startsWith("riptide.mixin.indigo.")) {
         return this.indigoLoaded && !this.sodiumLoaded;
      } else if (mixinClassName.startsWith("riptide.mixin.lithium.")) {
         return this.lithiumLoaded;
      } else if (!mixinClassName.startsWith("riptide.mixin.security.")) {
         return true;
      } else {
         return this.opsecLoaded ? false : !this.exploitPreventerLoaded || !EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS.contains(simpleName);
      }
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
