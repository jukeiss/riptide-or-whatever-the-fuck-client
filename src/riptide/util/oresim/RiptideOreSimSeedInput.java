package riptide.util.oresim;

import java.util.Objects;

public final class RiptideOreSimSeedInput {
   private static final RiptideOreSimSeedInput.Result EMPTY = new RiptideOreSimSeedInput.Result(RiptideOreSimSeedInput.Status.EMPTY, null);
   private static final RiptideOreSimSeedInput.Result INVALID = new RiptideOreSimSeedInput.Result(RiptideOreSimSeedInput.Status.INVALID, null);

   private RiptideOreSimSeedInput() {
   }

   public static RiptideOreSimSeedInput.Result parse(String raw) {
      if (raw == null) {
         return EMPTY;
      } else {
         String input = raw.strip();
         if (input.isEmpty()) {
            return EMPTY;
         } else {
            try {
               return new RiptideOreSimSeedInput.Result(RiptideOreSimSeedInput.Status.VALID, Long.parseLong(input));
            } catch (NumberFormatException var3) {
               return INVALID;
            }
         }
      }
   }

   public record Result(RiptideOreSimSeedInput.Status status, Long value) {
      public Result(RiptideOreSimSeedInput.Status status, Long value) {
         Objects.requireNonNull(status, "status");
         if (status == RiptideOreSimSeedInput.Status.VALID != (value != null)) {
            throw new IllegalArgumentException("Only a valid seed may carry a value");
         } else {
            this.status = status;
            this.value = value;
         }
      }

      public boolean isValid() {
         return this.status == RiptideOreSimSeedInput.Status.VALID;
      }
   }

   public static enum Status {
      EMPTY,
      VALID,
      INVALID;
   }
}
