# 一键编译 NingguangSkip（Windows）
# 由 build.bat 调用，也可在 PowerShell 里直接跑： .\build.ps1
# 国内网络下载慢时： $env:USE_MIRROR=1; .\build.ps1

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectDir

$SdkDir = if ($env:SDK_DIR) { $env:SDK_DIR } else { Join-Path $ProjectDir "android-sdk" }
$CmdlineUrl = if ($env:CMDLINE_URL) { $env:CMDLINE_URL } `
  else { "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" }

function Say($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Die($msg) { Write-Host "`n[失败] $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- JDK 检查
Say "检查 JDK"
function Get-JavaMajor {
  $java = Get-Command java -ErrorAction SilentlyContinue
  if (-not $java) { return 0 }
  $out = (& java -version 2>&1) | Select-Object -First 1
  if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
  return 0
}
$major = Get-JavaMajor
if ($major -lt 17) {
  if (Get-Command winget -ErrorAction SilentlyContinue) {
    Say "未检测到 JDK 17，尝试通过 winget 安装"
    winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path", "User")
    $major = Get-JavaMajor
  }
  if ($major -lt 17) {
    Die @"
需要 JDK 17。请到下面的地址下载安装后重新运行本脚本：
  https://adoptium.net/temurin/releases/?version=17
（安装时勾选 Set JAVA_HOME variable）
"@
  }
}
Write-Host "JDK $major OK" -ForegroundColor Green

# ---------------------------------------------------------------- Gradle 镜像（可选）
if ($env:USE_MIRROR -eq "1") {
  Say "写入 Gradle 依赖镜像"
  $initDir = Join-Path $env:USERPROFILE ".gradle\init.d"
  New-Item -ItemType Directory -Force -Path $initDir | Out-Null
  @'
beforeSettings {
    dependencyResolutionManagement {
        repositories {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
    }
}
'@ | Set-Content -Encoding UTF8 (Join-Path $initDir "mirrors.gradle.kts")

  $wrapper = Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.properties"
  if (Test-Path $wrapper) {
    (Get-Content $wrapper -Raw) -replace 'services\.gradle\.org/distributions/', 'mirrors.cloud.tencent.com/gradle/' |
      Set-Content -Encoding UTF8 $wrapper
  }
  Write-Host "镜像已配置 OK" -ForegroundColor Green
}

# ---------------------------------------------------------------- Android SDK
$SdkMgr = Join-Path $SdkDir "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkMgr)) {
  Say "下载 Android 命令行工具（约 130MB，请耐心等待）"
  $zip = Join-Path $ProjectDir "cmdline-tools.zip"
  try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $CmdlineUrl -OutFile $zip -UseBasicParsing
  } catch {
    Die "下载失败，可手动下载后放到项目根目录并重命名为 cmdline-tools.zip：
  $CmdlineUrl"
  }

  Say "解压"
  $tmp = Join-Path $SdkDir "cmdline-tools"
  New-Item -ItemType Directory -Force -Path $tmp | Out-Null
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  Remove-Item $zip -Force
  if (Test-Path (Join-Path $tmp "latest")) { Remove-Item (Join-Path $tmp "latest") -Recurse -Force }
  Move-Item (Join-Path $tmp "cmdline-tools") (Join-Path $tmp "latest") -Force
}
if (-not (Test-Path $SdkMgr)) { Die "sdkmanager 不存在：$SdkMgr" }

Say "同意 Android SDK 许可协议"
$licDir = Join-Path $SdkDir "licenses"
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
Set-Content -Encoding ASCII (Join-Path $licDir "android-sdk-license") "`n24333f8a63b6825ea9c5514f83c2829b004d1fee"
Set-Content -Encoding ASCII (Join-Path $licDir "android-sdk-preview-license") "`n84831b9409646a918e30573bab4c9c91346d8abd"

Say "安装 Android 平台与构建工具（约 200MB）"
& $SdkMgr --sdk_root="$SdkDir" --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
if ($LASTEXITCODE -ne 0) { Die "SDK 组件安装失败，检查网络后重试" }

# Gradle 也接受正斜杠，避免 Windows 路径里反斜杠需要转义的坑
Set-Content -Encoding ASCII (Join-Path $ProjectDir "local.properties") "sdk.dir=$($SdkDir.Replace('\','/'))"

# ---------------------------------------------------------------- Gradle
# 工程里不带 gradle-wrapper.jar，先装一份 Gradle 再让它在本地生成 wrapper
$GradleVersion = if ($env:GRADLE_VERSION) { $env:GRADLE_VERSION } else { "8.0" }
$GradleHome = Join-Path $ProjectDir ".gradle-dist\gradle-$GradleVersion"
$GradleBin = Join-Path $GradleHome "bin\gradle.bat"

if (-not (Test-Path $GradleBin)) {
  $GradleUrl = if ($env:USE_MIRROR -eq "1") {
    "https://mirrors.cloud.tencent.com/gradle/gradle-$GradleVersion-bin.zip"
  } else {
    "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
  }
  Say "下载 Gradle $GradleVersion（约 100MB）"
  $gzip = Join-Path $ProjectDir "gradle-dist.zip"
  try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $GradleUrl -OutFile $gzip -UseBasicParsing
  } catch {
    Die "Gradle 下载失败，可手动下载放到项目根目录改名 gradle-dist.zip：
  $GradleUrl"
  }
  Expand-Archive -Path $gzip -DestinationPath (Join-Path $ProjectDir ".gradle-dist") -Force
  Remove-Item $gzip -Force
}
if (-not (Test-Path $GradleBin)) { Die "Gradle 不可用：$GradleBin" }

if (-not (Test-Path (Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.jar"))) {
  Say "生成 Gradle Wrapper"
  & $GradleBin wrapper --gradle-version $GradleVersion --distribution-type bin
  if ($LASTEXITCODE -ne 0) { Die "生成 wrapper 失败" }
}

# ---------------------------------------------------------------- 编译
Say "编译 APK（首次会下载依赖，可能需要十几分钟）"
& (Join-Path $ProjectDir "gradlew.bat") assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { Die "编译失败，请把上面的报错发出来" }

$apk = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Die "没找到 APK：$apk" }

Say "完成"
Write-Host "APK 位置：$apk"
Write-Host ""
Write-Host "装到手机："
Write-Host "  1) 数据线连手机，手机开启 USB 调试"
Write-Host "  2) $(Join-Path $SdkDir 'platform-tools\adb.exe') install -r `"$apk`""
Write-Host "  或者把 APK 拷到手机，用文件管理器点开安装"
Write-Host ""
Write-Host "装好后的必做一步：打开 App → 点首页中央光球 → 在系统无障碍设置里开启「凝光跳过」"
