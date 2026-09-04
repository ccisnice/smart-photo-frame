#!/usr/bin/env bash
set -e

# ==============================================================================
# 智能相框 - 应用市场正式版签名打包自动化脚本
# ==============================================================================

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"

KEYSTORE="${KEYSTORE:-$PROJECT_ROOT/release.keystore}"
ALIAS="${ALIAS:-photoframe}"
KEYPASS="${KEYPASS:-photoframe123}"

echo "=================================================="
echo "📦 智能相框：开始生成应用市场正式发布包"
echo "=================================================="

# 1. 检查或生成正式签名证书
if [ ! -f "$KEYSTORE" ]; then
    echo "🔑 未检测到签名证书，正在自动生成官方发布证书: release.keystore ..."
    "$JAVA_HOME/bin/keytool" -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$KEYPASS" \
        -keypass "$KEYPASS" \
        -dname "CN=PhotoFrame, OU=App, O=Antigravity, L=Beijing, ST=Beijing, C=CN"
    echo "✅ 证书生成完毕！密码为: $KEYPASS (请妥善保存此 release.keystore 文件，更新版本必须使用同个证书)"
fi

# 2. 同步 Web 资源
echo "🔄 正在同步前端资产与 PWA 清单..."
npm run build
npx cap sync android

# 3. 编译正式 Release APK 和 Google Play AAB
echo "⚙️ 正在编译 Release APK 与 AAB ..."
cd android
./gradlew assembleRelease bundleRelease
cd "$PROJECT_ROOT"

UNSIGNED_APK="android/app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_APK="android/app/build/outputs/apk/release/app-release-aligned.apk"
SIGNED_APK="$PROJECT_ROOT/智能相框-市场正式签名版.apk"
AAB_SRC="android/app/build/outputs/bundle/release/app-release.aab"
AAB_DEST="$PROJECT_ROOT/智能相框-GooglePlay专版.aab"

# 4. 对齐并签名 APK
echo "✍️ 正在进行 4字节对齐与 V2/V3 签名..."
rm -f "$ALIGNED_APK" "$SIGNED_APK"
"$BUILD_TOOLS/zipalign" -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK" >/dev/null

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$KEYPASS" \
    --key-pass "pass:$KEYPASS" \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

# 5. 验证签名有效性
echo "🔍 验证签名合规性..."
"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED_APK"

# 6. 复制 AAB
cp "$AAB_SRC" "$AAB_DEST"

echo ""
echo "=================================================="
echo "🎉 打包完成！已生成应用市场标准交付文件："
echo "1. 国内各大安卓市场 APK (小米/华为/OPPO/VIVO/应用宝):"
echo "   👉 $SIGNED_APK"
echo "2. Google Play 专属 AAB (App Bundle):"
echo "   👉 $AAB_DEST"
echo "3. 永久签名密钥文件 (切记备份勿遗失):"
echo "   👉 $KEYSTORE"
echo "=================================================="
