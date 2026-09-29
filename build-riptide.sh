#!/bin/bash
# Build this session's Riptide changes into the readable jar.
#
#   bash build-riptide.sh            compile + register + LinkTest + update the readable jar
#   bash build-riptide.sh --obf      the above, then the obfuscated jar too
#
# Stops at the first failure. Nothing is written to the jars until LinkTest
# reports 0 VERIFY BUGS, so a failed run leaves your shippable jars untouched.

set -euo pipefail

# ---------------------------------------------------------------- paths
# ROOT is the workspace holding build/, cp/, tools/, fapi/ and the jars. You
# rename/move folders, so instead of a hard-coded path this auto-finds it:
#   1. $RIPTIDE_ROOT if you set it,   2. the script's own folder if it qualifies,
#   3. the newest scratch-* under Claude's scratch-workspaces that has build/ + a jar.
# A workspace "qualifies" when it has build/riptide.mixins.json and cp/compile.cp.
qualifies() { [ -f "$1/build/riptide.mixins.json" ] && [ -f "$1/cp/compile.cp" ]; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
ROOT=""
if [ -n "${RIPTIDE_ROOT:-}" ] && qualifies "$RIPTIDE_ROOT"; then
  ROOT="$RIPTIDE_ROOT"
elif qualifies "$SCRIPT_DIR"; then
  ROOT="$SCRIPT_DIR"
else
  SCRATCH="$HOME/Library/Application Support/Claude/scratch-workspaces"
  # newest first (-t), take the first candidate that qualifies
  while IFS= read -r cand; do
    [ -n "$cand" ] || continue
    if qualifies "$cand"; then ROOT="$cand"; break; fi
  done < <(ls -dt "$SCRATCH"/*/*/scratch-* 2>/dev/null)
fi
if [ -z "$ROOT" ]; then
  echo "Could not find your Riptide workspace (a folder with build/ and cp/)." >&2
  echo "Set it explicitly and re-run, e.g.:" >&2
  echo '  RIPTIDE_ROOT="/path/to/scratch-2026-...-XXXXXX" bash build-riptide.sh' >&2
  exit 1
fi
echo "==> workspace: $ROOT"

JDK="$HOME/Library/Application Support/PrismLauncher/java/java-runtime-epsilon/bin"
PRISM_LIBS="$HOME/Library/Application Support/PrismLauncher/libraries"

# Source: prefer a src/ sitting next to this script (i.e. you ran it straight
# from the git clone, which has this session's new files), else the workspace's
# own src/. This is why running from the clone "just works" without a copy step.
if [ -f "$SCRIPT_DIR/src/riptide/modules/SoundRadarModule.java" ]; then
  SRC="$SCRIPT_DIR/src"
else
  SRC="$ROOT/src"
fi
echo "==> source:    $SRC"
BUILD="$ROOT/build"
TOOLS="$ROOT/tools"
CP="$ROOT/cp"
JAR="$ROOT/Riptide Client-5.0-26.2.jar"
OBF_JAR="$ROOT/Riptide Client-5.0-26.2-obf.jar"

JAVAC="$JDK/javac"
JAVA="$JDK/java"
T25=/tmp/tools25
OUT=/tmp/riptide-out

for p in "$ROOT" "$SRC" "$BUILD" "$TOOLS" "$CP" "$JAVAC" "$JAVA"; do
  [ -e "$p" ] || { echo "MISSING: $p" >&2; exit 1; }
done

FAPI=$(find "$ROOT/fapi/META-INF/jars" -name '*.jar' | tr '\n' ':')
LIBS=$(find "$PRISM_LIBS" -name '*.jar' | tr '\n' ':')
# cp/compile.cp has no LWJGL-GLFW, which CpsHudModule (mouse polling) needs. Take the newest
# non-natives lwjgl-glfw jar from PrismLauncher's libraries.
GLFW_JAR=$(find "$PRISM_LIBS" -name 'lwjgl-glfw-*.jar' ! -name '*natives*' 2>/dev/null | sort -V | tail -1)
if [ -n "$GLFW_JAR" ]; then
  echo "==> glfw:      $GLFW_JAR"
else
  echo "WARNING: no lwjgl-glfw jar under $PRISM_LIBS - CpsHudModule will not compile." >&2
fi
CP_COMPILE="$BUILD:$(cat "$CP/compile.cp"):$FAPI${GLFW_JAR:+:$GLFW_JAR}"
# Mixins use MixinExtras (@WrapOperation / @ModifyReturnValue,
# com.llamalad7.mixinextras.*), which compile3.cp lacks. It lives in the
# PrismLauncher libraries, so append LIBS (curated cp wins by coming first).
CP_MIXIN="$BUILD:$(cat "$CP/compile3.cp"):$FAPI${GLFW_JAR:+:$GLFW_JAR}:$LIBS"
MIXIN_EXTRAS=$(find "$PRISM_LIBS" \( -name '*mixinextras*.jar' -o -name '*MixinExtras*.jar' \) 2>/dev/null | head -1)
if [ -n "$MIXIN_EXTRAS" ]; then
  echo "==> mixinextras: $MIXIN_EXTRAS"
else
  echo "==> mixinextras: not found as a standalone jar (expecting it shaded into fabric-loader in LIBS)"
fi

rm -rf "$OUT" && mkdir -p "$OUT" "$T25"

# ------------------------------------------------------- 1. plain classes
# BuiltinModules.java is deliberately NOT here: it is decompiler output, and
# step 3 patches the existing .class with AddRegister instead of recompiling it.
echo "==> compiling modules + util"
"$JAVAC" --release 25 -nowarn -cp "$CP_COMPILE" -d "$OUT" \
  "$SRC"/riptide/modules/ArmorHudModule.java \
  "$SRC"/riptide/modules/ArmorTrimHiderModule.java \
  "$SRC"/riptide/modules/ArrayListHudModule.java \
  "$SRC"/riptide/modules/CpsHudModule.java \
  "$SRC"/riptide/modules/CustomFovModule.java \
  "$SRC"/riptide/modules/CustomGlintModule.java \
  "$SRC"/riptide/modules/FakePayModule.java \
  "$SRC"/riptide/modules/HitParticlesModule.java \
  "$SRC"/riptide/modules/RegionMapModule.java \
  "$SRC"/riptide/modules/LootEspModule.java \
  "$SRC"/riptide/modules/MobEspModule.java \
  "$SRC"/riptide/modules/HitboxModule.java \
  "$SRC"/riptide/modules/SpawnerFinderModule.java \
  "$SRC"/riptide/modules/StatNametagsModule.java \
  "$SRC"/riptide/modules/SpawnerNametagsModule.java \
  "$SRC"/riptide/modules/BedrockHolesModule.java \
  "$SRC"/riptide/modules/JumpCirclesModule.java \
  "$SRC"/riptide/modules/GoldenLeverModule.java \
  "$SRC"/riptide/modules/HudDuplicate.java \
  "$SRC"/riptide/modules/HudStack.java \
  "$SRC"/riptide/modules/InfoHudModule.java \
  "$SRC"/riptide/modules/KeystrokesHudModule.java \
  "$SRC"/riptide/modules/KillEffectsModule.java \
  "$SRC"/riptide/modules/PlayerArmorEspModule.java \
  "$SRC"/riptide/modules/PotionHudModule.java \
  "$SRC"/riptide/modules/PvpCountHudModule.java \
  "$SRC"/riptide/modules/RadarHudModule.java \
  "$SRC"/riptide/modules/SoundRadarModule.java \
  "$SRC"/riptide/modules/SpawnerProtectModule.java \
  "$SRC"/riptide/modules/SpotifyModule.java \
  "$SRC"/riptide/modules/StaffListModule.java \
  "$SRC"/riptide/modules/TargetHudModule.java \
  "$SRC"/riptide/util/RiptideFakeScoreboard.java \
  "$SRC"/riptide/util/RiptideSpotify.java

# ------------------------------------------------------------ 2. mixins
echo "==> compiling mixins"
"$JAVAC" --release 25 -nowarn -cp "$CP_MIXIN" -d "$OUT" \
  "$SRC"/riptide/mixin/RiptideArmorTrimHiderMixin.java \
  "$SRC"/riptide/mixin/RiptideCameraZoomMixin.java \
  "$SRC"/riptide/mixin/RiptideHudSuiteMixin.java

echo "==> copying classes into build/"
( cd "$OUT" && find . -name '*.class' -print0 | while IFS= read -r -d '' f; do
    mkdir -p "$BUILD/$(dirname "$f")"
    cp "$f" "$BUILD/$f"
  done )

# -------------------------------------------------- 3. register modules
echo "==> recompiling ASM tools with JDK 25"
"$JAVAC" --release 25 -nowarn -cp "$(cat "$CP/asm.cp")" -d "$T25" \
  "$TOOLS"/AddRegister.java "$TOOLS"/LinkTest.java

echo "==> registering the 9 new modules"
"$JAVA" -cp "$T25:$(cat "$CP/asm.cp")" AddRegister \
  "$BUILD/riptide/modules/BuiltinModules.class" riptide/modules/WatermarkModule \
  riptide/modules/StaffListModule \
  riptide/modules/CustomFovModule \
  riptide/modules/HitParticlesModule \
  riptide/modules/ArmorTrimHiderModule \
  riptide/modules/CustomGlintModule \
  riptide/modules/FakePayModule \
  riptide/modules/SpawnerProtectModule \
  riptide/modules/KillEffectsModule \
  riptide/modules/SoundRadarModule

# --------------------------------------------- 4. register the new mixin
MIXJSON="$BUILD/riptide.mixins.json"
if grep -q 'RiptideArmorTrimHiderMixin' "$MIXJSON"; then
  echo "==> mixin already registered"
else
  echo "==> adding RiptideArmorTrimHiderMixin to riptide.mixins.json"
  cp "$MIXJSON" "$MIXJSON.bak"
  python3 - "$MIXJSON" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as fh:
    data = json.load(fh)
client = data.setdefault("client", [])
if "RiptideArmorTrimHiderMixin" not in client:
    client.append("RiptideArmorTrimHiderMixin")
    client.sort()
with open(path, "w") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")
PY
fi

# ------------------------------------------------------------ 5. verify
echo "==> LinkTest (must report 0 VERIFY BUGS)"
"$JAVA" -cp "$T25:$(cat "$CP/asm.cp")" LinkTest \
  "$BUILD:$(cat "$CP/compile.cp"):$FAPI:$LIBS" "$BUILD" | tee /tmp/riptide-linktest.txt
grep -qE '\b0 VERIFY BUGS\b' /tmp/riptide-linktest.txt \
  || { echo "LinkTest found problems - jars NOT touched. See /tmp/riptide-linktest.txt" >&2; exit 1; }

# ----------------------------------------------------- 6. readable jar
echo "==> updating $(basename "$JAR")"
cp "$JAR" "$JAR.bak"
( cd "$BUILD" && jar uf "$JAR" riptide riptide.mixins.json )
echo "OK: $JAR"

# --------------------------------------------------- 7. obfuscated jar
if [ "${1:-}" = "--obf" ]; then
  echo "==> obfuscating"
  rm -rf /tmp/ob
  "$JAVAC" --release 25 -nowarn -cp "$(cat "$CP/asm.cp")" -d "$T25" \
    "$TOOLS"/Obf.java "$TOOLS"/EncryptStrings.java
  "$JAVA" -cp "$T25:$(cat "$CP/asm.cp")" Obf "$BUILD" /tmp/ob
  mkdir -p /tmp/ob/riptide/util
  cp "$TOOLS"/riptide/util/RiptideStrings.class /tmp/ob/riptide/util/
  "$JAVA" -cp "$T25:$(cat "$CP/asm.cp")" EncryptStrings /tmp/ob \
    riptide/util/RiptideStrings riptide/mixin/RiptideMixinPlugin riptide/util/RiptideStrings
  "$JAVA" -cp "$T25:$(cat "$CP/asm.cp")" LinkTest \
    "/tmp/ob:$(cat "$CP/compile.cp"):$FAPI:$LIBS" /tmp/ob | tee /tmp/riptide-linktest-obf.txt
  grep -qE '\b0 VERIFY BUGS\b' /tmp/riptide-linktest-obf.txt \
    || { echo "Obfuscated LinkTest failed - obf jar NOT rebuilt." >&2; exit 1; }
  cp "$OBF_JAR" "$OBF_JAR.bak"
  ( cd /tmp/ob && jar --create --no-manifest --file "$OBF_JAR" . )
  echo "OK: $OBF_JAR"
fi

echo
echo "Done. Test in-game before shipping:"
echo "  - Spotify card fills in (and if not, its module line now names the reason)"
echo "  - no doubled Keystrokes / module list / CPS / armour / potions"
echo "  - Sound Radar bottom-right, clear of chat; PlayerESP+ panels track players"
echo "  - Custom Glint recolours item AND armour glint; trims vanish with Armor Trim Hider"
