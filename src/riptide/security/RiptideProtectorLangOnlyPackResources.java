package riptide.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;

public final class RiptideProtectorLangOnlyPackResources implements PackResources {
   private final PackResources delegate;

   public RiptideProtectorLangOnlyPackResources(PackResources delegate) {
      this.delegate = delegate;
   }

   public IoSupplier<InputStream> getRootResource(String... paths) {
      return this.delegate.getRootResource(paths);
   }

   public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
      return !isLangResource(type, location.getPath()) ? null : this.delegate.getResource(type, location);
   }

   public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      if (isLangPath(type, path)) {
         this.delegate.listResources(type, namespace, path, output);
      }
   }

   public Set<String> getNamespaces(PackType type) {
      return type != PackType.CLIENT_RESOURCES ? Set.of() : Set.copyOf(this.delegate.getNamespaces(type));
   }

   public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
      return (T)this.delegate.getMetadataSection(type);
   }

   public PackLocationInfo location() {
      return this.delegate.location();
   }

   public String packId() {
      return this.delegate.packId();
   }

   public Optional<KnownPack> knownPackInfo() {
      return this.delegate.knownPackInfo();
   }

   public void close() {
      this.delegate.close();
   }

   private static boolean isLangResource(PackType type, String path) {
      return type == PackType.CLIENT_RESOURCES && path.startsWith("lang/") && path.endsWith(".json");
   }

   private static boolean isLangPath(PackType type, String path) {
      return type == PackType.CLIENT_RESOURCES && (path.equals("lang") || path.startsWith("lang/"));
   }
}
