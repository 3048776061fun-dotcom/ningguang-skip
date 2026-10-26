#!/usr/bin/env bash
# 一键编译 NingguangSkip —— 只要求你机器上已经有 JDK 17。
# 脚本会自动下载 Android 命令行工具、装 SDK、装 Gradle 依赖、编出 APK。
#
# 用法：
#   chmod +x build.sh && ./build.sh
# 国内网络下载慢时：
#   USE_MIRROR=1 ./build.sh
#
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
SDK_DIR="${SDK_DIR:-$PROJECT_DIR/android-sdk}"

# 官方地址失效时，可自行覆盖：
#   CMDLINE_URL=https://你的镜像/commandlinetools.zip ./build.sh
CMDLINE_URL="${CMDLINE_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
if [[ "$(uname)" == "Darwin" ]]; then
  CMDLINE_URL="${CMDLINE_URL//linux/mac}"
fi

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31m[失败] %s\033[0m\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- JDK 检查
java_major() {
  command -v java >/dev/null 2>&1 || { echo 0; return; }
  local v; v="$(java -version 2>&1 | head -1)"
  local m; m="$(printf '%s' "$v" | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$m" =~ ^[0-9]+$ ]] || m=0
  echo "$m"
}

install_jdk_hint() {
  case "$(uname)" in
    Darwin) echo "brew install openjdk@17 && sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk" ;;
    *)      echo "sudo apt update && sudo apt install -y openjdk-17-jdk    # Ubuntu/Debian" ;;
  esac
}

say "检查 JDK"
MAJOR="$(java_major)"
if (( MAJOR < 17 )); then
  if [[ "$(uname)" == "Linux" ]] && command -v apt-get >/dev/null 2>&1; then
    say "未检测到 JDK 17，尝试自动安装（需要输入密码）"
    sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-17-jdk || true
    MAJOR="$(java_major)"
  fi
  if (( MAJOR < 17 )); then
    die "需要 JDK 17。请手动安装后重试：
  $(install_jdk_hint)"
  fi
fi
echo "JDK $MAJOR ✓"

# ---------------------------------------------------------------- Gradle 镜像（可选）
if [[ "${USE_MIRROR:-0}" == "1" ]]; then
  say "写入 Gradle 依赖镜像"
  mkdir -p "$HOME/.gradle/init.d"
  cat > "$HOME/.gradle/init.d/mirrors.gradle.kts" <<'KTS'
beforeSettings {
    dependencyResolutionManagement {
        repositories {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
    }
}
KTS
  if [[ -f gradle/wrapper/gradle-wrapper.properties ]]; then
    sed -i.bak 's|https\\://services.gradle.org/distributions/|https\\://mirrors.cloud.tencent.com/gradle/|' \
      gradle/wrapper/gradle-wrapper.properties
    rm -f gradle/wrapper/gradle-wrapper.properties.bak
  fi
  echo "镜像已配置 ✓"
fi

# ---------------------------------------------------------------- Android SDK
if [[ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  say "Android SDK 已存在，跳过下载"
else
  say "下载 Android 命令行工具（约 130MB，请耐心等待）"
  mkdir -p "$SDK_DIR/cmdline-tools"
  TMPZIP="$PROJECT_DIR/cmdline-tools.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 2 -o "$TMPZIP" "$CMDLINE_URL" || die "下载失败，可手动下载后放到项目根目录并重命名为 cmdline-tools.zip：
  $CMDLINE_URL"
  else
    wget -O "$TMPZIP" "$CMDLINE_URL" || die "下载失败：$CMDLINE_URL"
  fi

  say "解压"
  command -v unzip >/dev/null 2>&1 || die "需要 unzip，请先安装：sudo apt install -y unzip"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  unzip -q "$TMPZIP" -d "$SDK_DIR/cmdline-tools"
  rm -f "$TMPZIP"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

SDKMGR="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMGR" ]] || die "sdkmanager 不存在：$SDKMGR"
chmod +x "$SDKMGR" 2>/dev/null || true

say "同意 Android SDK 许可协议"
mkdir -p "$SDK_DIR/licenses"
printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n' > "$SDK_DIR/licenses/android-sdk-license"
printf '\n84831b9409646a918e30573bab4c9c91346d8abd\n' > "$SDK_DIR/licenses/android-sdk-preview-license"
yes 2>/dev/null | "$SDKMGR" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true

say "安装 Android 平台与构建工具（约 200MB）"
"$SDKMGR" --sdk_root="$SDK_DIR" --install \
  "platforms;android-34" "build-tools;34.0.0" "platform-tools" \
  >/dev/null || die "SDK 组件安装失败，检查网络后重试"

echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"

# ---------------------------------------------------------------- 编译
say "编译 APK（首次会下载依赖，可能需要十几分钟）"
chmod +x ./gradlew
./gradlew assembleDebug --no-daemon || die "编译失败，请把上面的报错发出来"

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || die "没找到 APK：$APK"

say "完成"
echo "APK 位置：$APK"
echo
echo "装到手机："
echo "  1) 数据线连手机，手机开启 USB 调试"
echo "  2) $SDK_DIR/platform-tools/adb install -r \"$APK\""
echo "  或者把 APK 拷到手机，用文件管理器点开安装"
echo
echo "装好后的必做一步：打开 App → 点首页中央光球 → 在系统无障碍设置里开启「凝光跳过」"
