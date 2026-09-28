package riptide.util;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

public final class RiptideAccountManager extends PersistentNbtManager<RiptideAccount> implements Iterable<RiptideAccount> {
   private static final RiptideAccountManager INSTANCE = new RiptideAccountManager();

   private RiptideAccountManager() {
   }

   public static RiptideAccountManager get() {
      INSTANCE.ensureLoaded();
      RiptideMeteorImport.ensureImported();
      INSTANCE.ensureStableIds();
      return INSTANCE;
   }

   private synchronized void ensureStableIds() {
      boolean changed = false;

      for (RiptideAccount account : this.items) {
         if (account != null) {
            account.stableId();
            changed |= account.generatedStableId;
            account.generatedStableId = false;
         }
      }

      if (changed) {
         this.save();
      }
   }

   public synchronized RiptideAccount findById(String id) {
      if (id != null && !id.isBlank()) {
         for (RiptideAccount account : this.items) {
            if (id.equals(account.stableId())) {
               return account;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public synchronized void applyResolvedCredentials(RiptideAccount resolved) {
      if (resolved != null && resolved.id != null) {
         RiptideAccount stored = this.findById(resolved.id);
         if (stored != null) {
            stored.label = resolved.label;
            stored.token = resolved.token;
            stored.sessionToken = resolved.sessionToken;
            stored.username = resolved.username;
            stored.uuid = resolved.uuid;
            stored.sessionTokenExpiresAt = resolved.sessionTokenExpiresAt;
            this.save();
         }
      }
   }

   public synchronized boolean rename(String accountId, String newLabel) {
      if (newLabel != null && !newLabel.isBlank()) {
         RiptideAccount stored = this.findById(accountId);
         if (stored != null && stored.type == RiptideAccountType.Cracked) {
            String trimmed = newLabel.trim();
            if (trimmed.equals(stored.label)) {
               return true;
            } else {
               for (RiptideAccount account : this.items) {
                  if (account != stored && trimmed.equalsIgnoreCase(account.label)) {
                     return false;
                  }
               }

               stored.label = trimmed;
               stored.username = trimmed;
               stored.uuid = UUIDUtil.createOfflinePlayerUUID(trimmed).toString();
               this.save();
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public synchronized void invalidateSessionToken(String accountId) {
      RiptideAccount stored = this.findById(accountId);
      if (stored != null && stored.type == RiptideAccountType.Microsoft) {
         stored.token = "";
         stored.sessionTokenExpiresAt = 0L;
         this.save();
      }
   }

   @Override
   protected File saveFile() {
      return new File(Minecraft.getInstance().gameDirectory, "riptide-accounts.nbt");
   }

   @Override
   protected String listKey() {
      return "accounts";
   }

   protected RiptideAccount fromTag(CompoundTag tag) {
      return new RiptideAccount().fromTag(tag);
   }

   protected CompoundTag toTag(RiptideAccount item) {
      return item.toTag();
   }

   @Override
   protected String describe() {
      return "Riptide accounts";
   }

   public synchronized void add(RiptideAccount account) {
      if (account != null) {
         this.items.add(account);
         this.save();
      }
   }

   public synchronized int addAll(List<RiptideAccount> accounts) {
      if (accounts != null && !accounts.isEmpty()) {
         int added = 0;

         for (RiptideAccount account : accounts) {
            if (account != null) {
               this.items.add(account);
               added++;
            }
         }

         if (added > 0) {
            this.save();
         }

         return added;
      } else {
         return 0;
      }
   }

   public synchronized void remove(RiptideAccount account) {
      if (this.items.remove(account)) {
         this.save();
      }
   }

   public synchronized int removeExpired() {
      int removed = 0;
      Iterator<RiptideAccount> iterator = this.items.iterator();

      while (iterator.hasNext()) {
         if (iterator.next().checkStatus == RiptideAccount.CheckStatus.EXPIRED) {
            iterator.remove();
            removed++;
         }
      }

      if (removed > 0) {
         this.save();
      }

      return removed;
   }

   public void login(RiptideAccount account) {
      if (account != null) {
         Thread thread = new Thread(() -> {
            if (account.fetchInfo() && account.login()) {
               this.save();
               RiptideClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
            } else {
               RiptideClientMessaging.sendPrefixed("Failed to login account: " + account.displayName() + account.failureSuffix());
            }
         }, "Riptide-Account-Login");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public void loginMicrosoft(RiptideAccount account) {
      if (account != null && account.type == RiptideAccountType.Microsoft) {
         RiptideMicrosoftLogin.getRefreshToken(refreshToken -> {
            if (refreshToken == null) {
               RiptideClientMessaging.sendPrefixed("Microsoft login cancelled or failed.");
            } else {
               account.label = refreshToken;
               Thread thread = new Thread(() -> {
                  if (account.fetchInfo() && account.login()) {
                     synchronized (this) {
                        if (!this.items.contains(account)) {
                           this.items.add(account);
                        }
                     }

                     this.save();
                     RiptideClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
                  } else {
                     RiptideClientMessaging.sendPrefixed("Failed to login Microsoft account" + account.failureSuffix() + ".");
                  }
               }, "Riptide-Microsoft-Login");
               thread.setDaemon(true);
               thread.start();
            }
         });
      }
   }

   @Override
   public Iterator<RiptideAccount> iterator() {
      return this.all().iterator();
   }
}
