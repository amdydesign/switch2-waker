#!/usr/bin/env bash
# Builds the Switch 2 Google TV app without Gradle/Android Studio.
# Needs: JDK 17+, Android SDK (build-tools + platform). Usage:
#   ANDROID_HOME=~/Android/Sdk ./build.sh            -> out/switch2-waker.apk
# Signing: KEYSTORE / KEYSTORE_PASS / KEY_ALIAS env vars; a throwaway key is
# generated when KEYSTORE is not set.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)}"
JAR="${ANDROID_JAR:-$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar}"
echo "build-tools: $BT"; echo "android.jar: $JAR"
rm -rf out && mkdir -p out/classes out/dex
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml out/res.zip
javac -source 8 -target 8 -nowarn -bootclasspath "$JAR" -d out/classes $(find src -name '*.java') 2>&1 | grep -v '^warning' || true
"$BT/d8" --lib "$JAR" --min-api 21 --output out/dex $(find out/classes -name '*.class')
python3 - <<'EOF'
import zipfile
with zipfile.ZipFile('out/unsigned.apk', 'a') as z:
    z.write('out/dex/classes.dex', 'classes.dex')
EOF
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk
if [ -z "${KEYSTORE:-}" ]; then
  KEYSTORE=out/debug.jks; KEYSTORE_PASS=android; KEY_ALIAS=switch2
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=Switch2 Waker" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "${KEY_ALIAS:-switch2}" \
  --out out/switch2-waker.apk out/aligned.apk
"$BT/apksigner" verify out/switch2-waker.apk
ls -la out/switch2-waker.apk
