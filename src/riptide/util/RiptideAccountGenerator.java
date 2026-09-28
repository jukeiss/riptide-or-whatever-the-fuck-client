package riptide.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;
import riptide.util.multi.MultiManager;

public final class RiptideAccountGenerator {
   private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
   private static final int MAX_COUNT = 5000;
   private static final String[] ADJECTIVES = new String[]{
      "Swift",
      "Silent",
      "Dark",
      "Lucky",
      "Wild",
      "Rapid",
      "Clever",
      "Mighty",
      "Tiny",
      "Brave",
      "Calm",
      "Eager",
      "Fuzzy",
      "Golden",
      "Happy",
      "Jolly",
      "Keen",
      "Lazy",
      "Merry",
      "Noble",
      "Proud",
      "Quiet",
      "Sneaky",
      "Witty"
   };
   private static final String[] NOUNS = new String[]{
      "Fox",
      "Wolf",
      "Bear",
      "Hawk",
      "Tiger",
      "Panda",
      "Otter",
      "Raven",
      "Cobra",
      "Falcon",
      "Badger",
      "Lynx",
      "Moose",
      "Bison",
      "Viper",
      "Heron",
      "Gecko",
      "Llama",
      "Mantis",
      "Newt",
      "Osprey",
      "Puma",
      "Sloth",
      "Wren"
   };

   private RiptideAccountGenerator() {
   }

   public static List<String> randomNames(int count) {
      int wanted = Math.max(1, Math.min(5000, count));
      Set<String> taken = existingNameKeys();
      Set<String> batch = new LinkedHashSet<>();
      Random random = new Random();
      int guard = wanted * 30;

      while (batch.size() < wanted && guard-- > 0) {
         String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
         String noun = NOUNS[random.nextInt(NOUNS.length)];
         int style = random.nextInt(4);

         String name = switch (style) {
            case 0 -> adjective + noun + digits(random, 2, 3);
            case 1 -> adjective + noun;
            case 2 -> noun + digits(random, 2, 4);
            default -> adjective + noun + "_" + digits(random, 1, 2);
         };
         if (name.length() > 16) {
            name = name.substring(0, 16);
         }

         if (VALID_NAME.matcher(name).matches()) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!taken.contains(key) && !batch.contains(name)) {
               batch.add(name);
               taken.add(key);
            }
         }
      }

      return new ArrayList<>(batch);
   }

   public static RiptideAccountGenerator.ParseResult parseNameList(String raw) {
      List<String> names = new ArrayList<>();
      if (raw != null && !raw.isBlank()) {
         Set<String> taken = existingNameKeys();
         Set<String> seen = new LinkedHashSet<>();
         int skipped = 0;
         int duplicates = 0;

         for (String token : raw.split("[\\s,;]+")) {
            if (!token.isBlank()) {
               String candidate = token;
               int colon = token.indexOf(58);
               if (colon >= 0) {
                  candidate = token.substring(0, colon);
               }

               candidate = candidate.trim();
               if (!candidate.isEmpty() && VALID_NAME.matcher(candidate).matches()) {
                  String key = candidate.toLowerCase(Locale.ROOT);
                  if (!taken.contains(key) && seen.add(key)) {
                     names.add(candidate);
                  } else {
                     duplicates++;
                  }
               } else {
                  skipped++;
               }
            }
         }

         return new RiptideAccountGenerator.ParseResult(names, skipped, duplicates);
      } else {
         return new RiptideAccountGenerator.ParseResult(names, 0, 0);
      }
   }

   public static int addGeneratedAccounts(List<String> names) {
      if (names != null && !names.isEmpty()) {
         if (names.size() > 5000) {
            names = names.subList(0, 5000);
         }

         RiptideAccountManager manager = RiptideAccountManager.get();
         RiptideConfig config = RiptideConfig.getGlobal();
         boolean setPassword = config.accountGenSetPassword;
         boolean sharedForAll = "Set For All".equalsIgnoreCase(config.accountGenPasswordMode);
         String shared = config.accountGenSharedPassword == null ? "" : config.accountGenSharedPassword.trim();
         List<RiptideAccount> batch = new ArrayList<>();

         for (String name : names) {
            if (name != null && !name.isBlank()) {
               RiptideAccount account = new RiptideAccount();
               account.type = RiptideAccountType.Generated;
               account.label = name;
               account.username = name;
               account.uuid = UUIDUtil.createOfflinePlayerUUID(name).toString();
               if (setPassword) {
                  account.password = sharedForAll ? shared : MultiManager.generatePassword();
               }

               batch.add(account);
            }
         }

         return manager.addAll(batch);
      } else {
         return 0;
      }
   }

   private static Set<String> existingNameKeys() {
      Set<String> keys = new LinkedHashSet<>();

      for (RiptideAccount account : RiptideAccountManager.get().all()) {
         String name = account.displayName();
         if (name != null && !name.isBlank()) {
            keys.add(name.toLowerCase(Locale.ROOT));
         }
      }

      return keys;
   }

   private static String digits(Random random, int min, int max) {
      int length = min + random.nextInt(max - min + 1);
      StringBuilder out = new StringBuilder(length);

      for (int i = 0; i < length; i++) {
         out.append(random.nextInt(10));
      }

      return out.toString();
   }

   public record ParseResult(List<String> names, int skipped, int duplicates) {
   }
}
