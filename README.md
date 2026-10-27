# 凝光跳过 · NingguangSkip

一个本地运行的 Android 广告跳过工具：**通用关键词跳过 + 可订阅规则引擎**双引擎，界面采用 Jetpack Compose 还原 ColorOS 17「凝光视效 / 流体动效 / 柔性反馈」的设计语言。

> 交付的是**完整可编译的 Android 源码工程**，不是 APK —— 手机无法直接安装源码，必须先编译。
> 没装过 Android Studio 也没关系：
> - **最省心**：用 GitHub Actions 云端编译，本机零安装，直接下载 APK（见第三节路线 A）
> - **本机编译**：双击 `build.bat`（Windows）或 `./build.sh`（Mac/Linux），只需装个 JDK 17（路线 B）
> - **想改代码**：Android Studio 打开目录（路线 C）

---

## 一、能力总览

| 模块 | 说明 |
| --- | --- |
| 通用跳过 | 内置中英文关键词（跳过 / 跳过广告 / Skip Ad…）与控件 ID 词库（skip / ad_close / splash_skip…），装完开启无障碍即可用 |
| 规则引擎 | 按「包名 + Activity + 控件文本/ID」匹配，支持自定义动作（点击 / 返回键 / 仅记录）、延迟执行、JSON 导入导出 |
| 规则订阅 | 添加远端规则 JSON 地址后定期拉取更新；版本号比对、本地规则永不被覆盖、可每天自动检查 |
| 三重防误触 | ① 同规则同应用冷却 ② 同应用每分钟次数上限 ③ 排除词（跳过登录 / 跳过引导…） |
| 跳过日志 | 按日期分组记录命中应用、命中文本、规则名、成功与否，最多保留 300 条 |
| 应用黑名单 | 可为单个应用单独关闭自动跳过 |
| 凝光悬浮提示 | 跳过瞬间在屏幕顶部弹一枚玻璃胶囊（1.8 秒后自动滑出消失），不拦截点击；无悬浮窗权限时自动回退为通知 |
| 通知与震动 | 跳过成功发轻量通知 + 轻微震动，均可在设置中关闭 |
| 统计 | 今日跳过 / 累计跳过，实时刷新 |

隐私：全部逻辑在本地完成，**不联网、不上传、不读取输入内容**，只读取当前窗口的控件文本与控件 ID 用于匹配广告按钮。

---

## 二、凝光视效是怎么做的

ColorOS 17 的流体设计由三件事构成，本项目逐条落地：

**1. 凝光视效（材质与光）** — `ui/glow/Ningguang.kt`

- **材质**不是一块半透明色块，而是「半透玻璃基底 + 顶部锋利高光边 + 底部柔和反射光 + 彩色投影」四层叠加，投影色取自语义色，等于把控件颜色散射到背景上
- **光跟随手指**：按压位置以归一化坐标记录，光斑用 `radialGradient` + `BlendMode.Screen` 离屏合成，触点用低刚度弹簧追赶手指，产生自然的滞后流动感
- **光随状态变化**：开关打开、服务运行时卡片底部常驻一层状态光；首页核心光球在服务运行时才有两道反向旋转的光带
- **光感描边**：深浅色两套描边亮度（浅色哑光细边 / 深色柔和高亮边），保证任何底色下图标与文字都清晰可读
- **可读性优先**：所有光效都乘以全局强度系数，可在设置里整体关闭（关掉后退化成纯色界面，更省电）

**2. 流体动效（连贯）**

- 页面切换为「淡入 + 横向位移 + 缩放」组合过渡，元素间不出现生硬跳转
- 弹层从底部弹性浮起；统计数字用弹簧过渡，跳过一次会「弹」一下

**3. 柔性反馈（手感）**

- 按下收缩、松手弹性回弹，全部用 `spring(MediumBouncy)` 而非固定时长补间
- 开关滑块按住时被拉长、松手弹回，模拟果冻质感
- 每次按压同步 `HapticFeedback`，视觉与触觉同源
- 底部导航为**浮岛式导航条**，选中态药丸带弹性位移与彩色投影

**跳过瞬间的凝光提示** — `ui/overlay/GlowToast.kt`

- 命中广告按钮后，在屏幕顶部弹一枚圆角玻璃胶囊（青色状态光 + 光感描边 + 闪电图标 + 「已跳过 XX 的广告 / 命中文本」），1.8 秒后向上滑出
- 入场为「下滑 + 淡入 + 轻微放大」，退出为「上滑 + 淡出 + 轻微缩小」，与流体动效同一套节奏
- 窗口带 `NOT_FOCUSABLE / NOT_TOUCHABLE / NOT_TOUCH_MODAL`，只做展示，绝不拦截用户对下层应用的点击
- 用**原生 View** 渲染（`GradientDrawable` 做玻璃底 + 光感描边 + 闪电图标）。不用 ComposeView，是因为悬浮窗的 context 不是 Activity，得自己造 `LifecycleOwner` 宿主，而 `ViewTreeLifecycleOwner` 这类宿主类在编译期不可见；原生 View 绕开了这一整层依赖
- 显示时 `addView`，出场动画结束后 `removeView`，不常驻、不留残余窗口
- 未授予悬浮窗权限时自动回退为通知，保证反馈不丢；设置页可一键跳转授权、随时开关

---

## 三、怎么变成手机能装的 APK

源码不能直接装到手机，必须先编译成 APK。三条路由你选，从上到下越来越省事。

### 路线 A：云端编译（零安装，推荐给没装过 Android Studio 的人）

借 GitHub 的服务器编译，你本机什么都不用装。

1. 注册 / 登录 [GitHub](https://github.com)，新建一个仓库（比如叫 `ningguang-skip`），**不要勾任何初始化选项**（不要 README、不要 .gitignore）
2. 把解压出来的 `NingguangSkip` 文件夹里的**全部内容**上传到这个仓库。不会用 git 也没关系：在仓库页面点 `Add file → Upload files`，把文件全拖进去（`.github` 文件夹也要拖，它是隐藏目录，需要一并上传）
3. 上传完点顶部 `Actions` 标签 → 左侧选 `Build APK` → 右上角 `Run workflow` → 再点绿色的 Run workflow
4. 等 3~8 分钟，任务变绿后点进去，在页面底部 `Artifacts` 区域下载 `ningguang-skip-debug`
5. 下载的是 zip，解压出 `app-debug.apk`，这就是安装包

> 如果上传后 Actions 没有自动跑，多半是 `.github` 文件夹没传上去，或者分支名不是 `main`/`master`。手动 `Run workflow` 可以绕过这个问题。

### 路线 B：本机一键脚本（需要装 JDK 17，其余全自动）

不用装 Android Studio，脚本会自动下载 Android SDK、配置环境、编译出 APK。

**Windows**：双击 `build.bat`
**macOS / Linux**：终端里进到项目目录，执行

```bash
chmod +x build.sh && ./build.sh
```

脚本会做这些事：检测 JDK → 缺了就尝试自动装（Linux 走 apt，Windows 走 winget）→ 下载 Android 命令行工具 → 装 SDK 平台与构建工具 → 执行 `gradlew assembleDebug` → 打印 APK 位置。

**唯一的前置条件：JDK 17。** 没有的话脚本会提示你装，也可以自己去 [Adoptium](https://adoptium.net/temurin/releases/?version=17) 下载（Windows 安装时记得勾选 `Set JAVA_HOME variable`）。

国内下载慢就加镜像：

```bash
USE_MIRROR=1 ./build.sh        # macOS / Linux
```
Windows 在 PowerShell 里先执行 `$env:USE_MIRROR=1` 再运行 `build.ps1`。

跑完后 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

### 路线 C：Android Studio（想继续开发代码时用）

`File → Open` 选中 `NingguangSkip` 目录 → 等 Gradle Sync → 顶部选设备 → `Run ▶`。
正式包用 `Build → Generate Signed Bundle / APK` 生成。

### 把 APK 装到手机上

- **最稳**：数据线拷到手机存储，用文件管理器点开安装
- **adb**：`adb install -r app-debug.apk`
- 用微信 / QQ 传 APK 时，文件名常被改成 `.apk.1`，安装前改回 `.apk`
- 安装时提示「未知来源应用」，允许即可

装好后的必做一步：**打开 App → 点首页中央的光球 → 在系统无障碍设置里开启「凝光跳过」**。不开这个，什么都跳不了。

### 常见报错速查

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| 需要 JDK 17 | 没装或版本太低 | 装 JDK 17 后重跑脚本 |
| 下载 Android 命令行工具失败 | 网络问题 | 用 `USE_MIRROR=1`，或手动下载放到项目根目录改名 `cmdline-tools.zip` |
| `SDK location not found` | 没生成 local.properties | 脚本会自动生成；也可以手动写 `sdk.dir=你的SDK路径` |
| `Unable to find Gradle tasks to build` | 打开错目录 | 打开 `NingguangSkip` 这一层，不是它的父目录 |
| 一运行就失败（十几秒就红） | 工程不含 `gradle-wrapper.jar` | 脚本和 Actions 都会先装 Gradle 再生成 wrapper，已内置处理 |
| 依赖下载卡住不动 | 网络问题 | 加 `USE_MIRROR=1`，或配代理 |
| 装完没反应 / 不跳过 | 无障碍服务没开 | 确认无障碍已开启，并加入电池优化白名单 |
| 无障碍开关自动关闭 | 部分 ROM 会回收 | 加电池白名单 + 在多任务列表里锁定 App |

> 首次编译的总下载量约 500MB~1GB（Android SDK + Gradle + 依赖），这是编译 Android 应用绕不开的开销。


## 四、目录结构

```
build.sh / build.bat / build.ps1  # 本机一键编译脚本
.github/workflows/build-apk.yml   # 云端编译（GitHub Actions）
app/src/main/java/com/ningguang/skip/
├── MainActivity.kt            # 导航骨架、浮岛导航条、页面转场
├── NingguangApp.kt            # Application：初始化存储与光效配置
├── data/
│   ├── Models.kt              # Rule / SkipLog / Settings / 内置规则
│   └── Prefs.kt               # SharedPreferences + Gson 持久化与内存状态
├── engine/
│   ├── SkipEngine.kt          # 双引擎匹配、点击、冷却与限流、日志
│   └── SubscriptionRepository.kt  # 订阅拉取、解析、版本比对、合并
├── worker/
│   └── SubscriptionWorker.kt  # WorkManager 每日自动检查订阅更新
├── service/
│   └── SkipAdService.kt       # 无障碍服务入口（事件节流）
├── ui/
│   ├── theme/                 # 凝光配色、排版、主题
│   ├── glow/
│   │   ├── Ningguang.kt       # 凝光玻璃 Modifier、指尖弥散光、背景光
│   │   └── Components.kt      # 卡片 / 按钮 / 开关 / 列表项 / 浮岛导航
│   ├── overlay/
│   │   └── GlowToast.kt       # 跳过时的凝光悬浮胶囊
│   └── screens/               # 首页、规则、日志、设置
└── util/                      # 应用信息、通知、无障碍与悬浮窗权限
```

---

## 五、规则 JSON 格式

在「规则 → 导入」里粘贴数组即可，也可以先「导出」现有规则再改：

```json
[
  {
    "id": "douyin_splash",
    "name": "抖音开屏",
    "appId": "com.ss.android.ugc.aweme",
    "activity": "Splash",
    "texts": "跳过|跳过广告",
    "ids": "iv_skip|tt_splash_skip",
    "excludeTexts": "跳过登录",
    "action": "click",
    "delayMs": 0,
    "enabled": true
  }
]
```

字段说明：

- `appId` 留空表示全局生效；`activity` 留空表示任意页面
- `texts` / `ids` / `excludeTexts` 多个值用 `|` 分隔
- `action`：`click`（点击命中控件）/ `back`（返回键）/ `record`（只记录不动作，用于调试命中率）
- `delayMs`：命中后延迟执行，给广告动画留时间

---

## 六、规则订阅

规则是消耗品：App 一改版，控件 ID 和文案就变了，本地写死的规则会慢慢失效。订阅源解决的正是这件事——规则由维护者更新，你自动同步。

### 订阅文件格式

外层多三个字段，`rules` 结构与上面的规则 JSON 完全一致：

```json
{
  "id": "ng-sample",
  "name": "凝光规则 · 示例订阅",
  "author": "ningguang",
  "version": 1,
  "updateUrl": "https://example.com/ningguang-rules/rules.json",
  "rules": [ ... ]
}
```

- `version`：**整数，只增不减**。本地记录的已是最新时直接跳过，不再写规则库
- `updateUrl` 可选，指向自身，便于后续迁移地址
- 规则没写 `id` 时，会按 `订阅id#序号` 自动生成稳定 id，保证下次更新能对齐同一条而不是重复新增
- 仓库根目录 `subscription-sample/rules.json` 是一份可直接使用的示例（含点击 / 返回 / 仅记录三种动作）

### 怎么用

1. 把这个 JSON 托管到任何能公网直链访问的地方（GitHub Raw、jsDelivr、Gitee、自己的服务器都行）
2. App 内：规则 → 更新 → 「规则订阅」→ 粘贴地址 → 添加（添加时会先拉一次，拉不通就不会写进列表，避免留下坏源天天重试）
3. 设置 → 规则订阅：开「每天自动检查更新」，或点「立即检查一次」

### 三条合并规则

1. **版本号只增不减**：远端 `version <= 本地` 时跳过；远端没标 `version`（视为 0）则每次都更新一次
2. **本地优先**：用户手写的规则永远不被订阅覆盖（匹配优先级同样是 本地 > 订阅 > 内置）
3. **保留用户开关**：订阅规则被更新时，沿用你之前的启用/停用状态；远端删掉的规则，本地一并移除

### 关于联网

App **不预置任何第三方订阅源**，订阅列表为空时不会产生任何网络请求（`INTERNET` 权限只在有订阅源时才被真正使用）。加不加源、加谁的源，完全由你决定——第三方规则写得过宽可能误触，所以冷却与限流对订阅规则同样生效，且每个订阅可单独开关、一键删除（连带清掉它带来的全部规则）。

`version` 相同的重复更新会被跳过，因此每天自动检查的实际流量很小（一次 GET + 一个 JSON）。

---

## 七、可调参数（设置页）

| 参数 | 默认 | 作用 |
| --- | --- | --- |
| 点击延迟 | 0 ms | 命中后延迟点击 |
| 冷却时间 | 1500 ms | 同规则同应用最小间隔 |
| 每分钟上限 | 4 次 | 同应用每分钟最多跳过次数 |
| 匹配「关闭」 | 关 | 命中率更高，误触风险也更高 |
| 光效强度 | 100% | 调低更克制、更省电；可整体关闭 |

---

## 八、后续可加的扩展点

- **GKD 选择器兼容**：GKD 规则用类 CSS 选择器（能表达层级、兄弟节点），比我们的「文本/ID 关键词」表达力强得多。想直接吃它的订阅源，需要在 `SkipEngine` 前加一层选择器解析
- **订阅源健康度**：记录每个源连续失败次数，失败过多自动停用并在界面上标红
- **WebView 广告**：当前基于原生控件树，网页内广告需要配合 `AccessibilityNodeInfo` 的 Web 文本匹配或改用其他方案
- **规则命中调试**：把某条规则设为 `record`，在日志里观察命中情况再决定动作

---

## 九、声明

本项目仅供学习与技术交流，用于跳过应用内的广告按钮。请遵守所在地区法律法规与相关应用的用户协议，勿用于破坏他人正常商业收益或违规用途。
