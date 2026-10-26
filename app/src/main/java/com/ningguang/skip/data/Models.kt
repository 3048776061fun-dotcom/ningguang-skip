package com.ningguang.skip.data

import java.util.UUID

/* ---------------------------------------------------------------------------
 * 规则
 * ------------------------------------------------------------------------- */

object RuleAction {
    const val CLICK = "click"       // 点击命中控件
    const val BACK = "back"         // 直接返回键（对付整屏不可点的广告）
    const val RECORD_ONLY = "record" // 只记录不动作，用来调试规则命中率
}

/**
 * 一条跳过规则。
 *
 * 匹配优先级：先按 [appId] 精确匹配包名（为空表示全局），再看 [activity]，
 * 最后在窗口里找文本 / 控件 ID 命中 [texts] / [ids] 的控件。
 */
data class Rule(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var appId: String = "",              // 包名，空 = 全局
    var activity: String = "",           // Activity 名，空 = 任意
    var texts: String = "",              // 文本关键词，用 | 分隔
    var ids: String = "",                // 控件 id 关键词，用 | 分隔
    var excludeTexts: String = "",       // 排除词，命中则放弃（避免误触）
    var action: String = RuleAction.CLICK,
    var delayMs: Long = 0L,              // 命中后延迟执行，给广告动画留出时间
    var enabled: Boolean = true,
    var builtIn: Boolean = false,
    /** 来源订阅 id；空 = 内置或本地自建 */
    var sourceId: String = ""
) {
    fun textList(): List<String> = texts.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    fun idList(): List<String> = ids.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    fun excludeList(): List<String> = excludeTexts.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    /** 来自某个订阅源 */
    fun isFromSubscription(): Boolean = sourceId.isNotBlank()

    /** 用户自己手写的（既不是内置，也不是订阅来的）—— 优先级最高，永远不被订阅覆盖 */
    fun isLocal(): Boolean = !builtIn && sourceId.isBlank()
}

/** 内置规则：随包体分发，覆盖国内主流 App 的开屏 / 浮层广告按钮 */
fun builtInRules(): List<Rule> = listOf(
    Rule(
        id = "builtin_generic_cn",
        name = "中文通用跳过",
        appId = "",
        texts = "跳过|跳过广告|跳过此广告|立即跳过|跳过视频|关闭广告|跳过开头",
        ids = "",
        excludeTexts = "跳过登录|跳过此步|跳过引导|跳过教程",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_generic_en",
        name = "英文通用跳过",
        appId = "",
        texts = "Skip|Skip Ad|Skip Ads|SKIP AD|Skip advertisement|No, thanks|Close Ad",
        ids = "",
        excludeTexts = "Skip login|Skip for now|Skip tutorial",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_viewid_skip",
        name = "控件 ID 通用（skip/close 类）",
        appId = "",
        texts = "",
        ids = "skip|splash_skip|ad_skip|iv_skip|tv_skip|btn_skip|tt_splash",
        excludeTexts = "",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_viewid_close",
        name = "控件 ID 通用（ad_close 类）",
        appId = "",
        texts = "",
        ids = "ad_close|iv_ad_close|close_ad|btn_close_ad|adskip|ad_tv_skip",
        excludeTexts = "",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_wechat",
        name = "微信 · 小程序 / 朋友圈广告",
        appId = "com.tencent.mm",
        texts = "关闭|跳过",
        ids = "",
        excludeTexts = "关闭朋友圈|关闭小程序",
        action = RuleAction.CLICK,
        delayMs = 200L,
        builtIn = true
    ),
    Rule(
        id = "builtin_douyin",
        name = "抖音 · 开屏与信息流广告",
        appId = "com.ss.android.ugc.aweme",
        texts = "跳过|关闭广告",
        ids = "",
        excludeTexts = "",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_taobao",
        name = "淘宝 · 开屏广告",
        appId = "com.taobao.taobao",
        texts = "跳过",
        ids = "",
        excludeTexts = "",
        action = RuleAction.CLICK,
        builtIn = true
    ),
    Rule(
        id = "builtin_bilibili",
        name = "哔哩哔哩 · 贴片广告",
        appId = "tv.danmaku.bili",
        texts = "跳过广告",
        ids = "",
        excludeTexts = "",
        action = RuleAction.CLICK,
        builtIn = true
    )
)

/**
 * 归一化：Gson 用 Unsafe 反序列化时不会走构造器默认值，
 * 外部订阅的 JSON 一旦缺字段就会留下 null，这里统一补全，避免后续 NPE。
 */
private fun safeStr(value: String?): String = value ?: ""

fun Rule.normalized(): Rule = Rule(
    id = safeStr(id).ifBlank { UUID.randomUUID().toString() },
    name = safeStr(name),
    appId = safeStr(appId),
    activity = safeStr(activity),
    texts = safeStr(texts),
    ids = safeStr(ids),
    excludeTexts = safeStr(excludeTexts),
    action = safeStr(action).takeIf {
        it == RuleAction.CLICK || it == RuleAction.BACK || it == RuleAction.RECORD_ONLY
    } ?: RuleAction.CLICK,
    delayMs = delayMs,
    enabled = enabled,
    builtIn = builtIn,
    sourceId = safeStr(sourceId)
)

/* ---------------------------------------------------------------------------
 * 跳过日志
 * ------------------------------------------------------------------------- */

data class SkipLog(
    val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val pkg: String = "",
    val appName: String = "",
    val matchedText: String = "",
    val ruleName: String = "",
    val action: String = RuleAction.CLICK,
    val success: Boolean = true
)

/* ---------------------------------------------------------------------------
 * 设置
 * ------------------------------------------------------------------------- */

data class Settings(
    val masterEnabled: Boolean = true,
    val genericEnabled: Boolean = true,
    val ruleEnabled: Boolean = true,

    val clickDelayMs: Int = 0,        // 命中后延迟点击
    val cooldownMs: Int = 1500,       // 同一规则在同一 App 内的冷却时间
    val maxPerMinute: Int = 4,        // 同一 App 每分钟最多跳过次数，防止误触循环

    val customKeywords: String = "",  // 用户追加的关键词，| 分隔
    val customIds: String = "",       // 用户追加的控件 id，| 分隔
    val matchCloseText: Boolean = false, // 是否把「关闭」也算作广告按钮（误触风险高，默认关）

    val disabledApps: Set<String> = emptySet(), // 黑名单：这些 App 不自动跳过

    val showNotification: Boolean = true,
    val hapticFeedback: Boolean = true,

    /** 跳过时弹一枚凝光悬浮胶囊；开启后优先用它，不再发通知，避免双重打扰 */
    val overlayToast: Boolean = true,

    val followSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val glowEnabled: Boolean = true,
    val glowIntensity: Float = 1f,

    /** 每天自动检查订阅更新（只在有订阅源且联网时才真正发生请求） */
    val autoUpdateSubscription: Boolean = true,
    /** 是否在自动更新拿到新规则时发一条通知 */
    val notifySubscriptionUpdate: Boolean = true
)
