package com.ningguang.skip.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.data.Rule
import com.ningguang.skip.data.RuleAction
import com.ningguang.skip.data.Settings
import com.ningguang.skip.data.SkipLog
import com.ningguang.skip.ui.overlay.GlowToast
import com.ningguang.skip.util.Notify
import com.ningguang.skip.util.appLabel
import com.ningguang.skip.util.vibrateTick
import java.util.HashMap
import java.util.LinkedList

/**
 * 双引擎跳过核心。
 *
 * 一、规则引擎：按「包名 + Activity + 控件文本/ID」匹配，支持导入订阅规则（GKD 思路）
 * 二、通用引擎：内置一套跨 App 的关键词与控件 ID，装完即用（李跳跳思路）
 *
 * 三重安全策略，防止误触和循环点击：
 *   1. 冷却：同一规则在同一 App 内冷却 cooldownMs
 *   2. 限流：同一 App 每分钟最多 maxPerMinute 次
 *   3. 排除词：命中排除词的控件一律放弃
 */
class SkipEngine(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val lastHitAt = HashMap<String, Long>()
    private val recentHits = HashMap<String, LinkedList<Long>>()
    private val pendingRecycle = ArrayList<AccessibilityNodeInfo>()

    private val pkg: String get() = service.packageName

    /** 供服务在窗口变化时调用 */
    fun onWindowChanged(packageName: CharSequence?, className: CharSequence?) {
        val target = packageName?.toString() ?: return
        val s = Prefs.settings
        if (!s.masterEnabled) return
        if (target == pkg) return                       // 不跳自己
        if (target in s.disabledApps) return            // 黑名单
        if (!allowByRate(target, s.maxPerMinute)) return

        val root = service.rootInActiveWindow ?: return
        val nodes = LinkedList<AccessibilityNodeInfo>()
        try {
            flatten(root, nodes, MAX_NODES)
            for (rule in candidates(target, s)) {
                if (!rule.enabled) continue
                if (rule.appId.isNotBlank() && rule.appId != target) continue
                if (rule.activity.isNotBlank() && className != null &&
                    !className.contains(rule.activity, ignoreCase = true)
                ) continue

                val key = "${rule.id}@$target"
                val now = System.currentTimeMillis()
                if (now - (lastHitAt[key] ?: 0L) < s.cooldownMs) continue

                val node = matchFirst(nodes, rule) ?: continue
                lastHitAt[key] = now
                recentHits.getOrPut(target) { LinkedList() }.add(now)
                perform(rule, node, target, s)
                return
            }
        } finally {
            // 及时释放，避免 native 侧节点句柄堆积
            nodes.forEach { runCatching { it.recycle() } }
            runCatching { root.recycle() }
        }
    }

    /* ---------------- 候选规则 ---------------- */

    private fun candidates(pkg: String, s: Settings): List<Rule> {
        if (!s.ruleEnabled) {
            return if (s.genericEnabled) listOf(genericRule(s)) else emptyList()
        }
        val all = Prefs.rules.filter { it.enabled }
        // 组内优先级：用户手写 > 订阅 > 内置，用户自己写的永远说了算
        val bySource: (List<Rule>) -> List<Rule> = { list ->
            list.filter { it.isLocal() } + list.filter { it.isFromSubscription() } +
                list.filter { it.builtIn && !it.isFromSubscription() }
        }
        val specific = bySource(all.filter { it.appId.isNotBlank() && it.appId == pkg })
        val global = bySource(all.filter { it.appId.isBlank() })
        val generic = if (s.genericEnabled) listOf(genericRule(s)) else emptyList()
        // 具体到通用：包名命中的规则优先，避免通用关键词「抢先」误触
        return specific + global + generic
    }

    private fun genericRule(s: Settings): Rule {
        val texts = mutableListOf(
            "跳过", "跳过广告", "跳过此广告", "立即跳过", "跳过视频", "跳过片头",
            "Skip", "Skip Ad", "Skip Ads", "SKIP AD", "Skip advertisement", "No, thanks"
        )
        if (s.matchCloseText) texts += "关闭"
        val ids = mutableListOf(
            "skip", "splash_skip", "ad_skip", "iv_skip", "tv_skip", "btn_skip",
            "ad_close", "iv_ad_close", "close_ad", "adskip"
        )
        val customText = s.customKeywords.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val customId = s.customIds.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return Rule(
            id = "generic",
            name = "通用跳过",
            texts = (texts + customText).joinToString("|"),
            ids = (ids + customId).joinToString("|"),
            excludeTexts = "跳过登录|跳过此步|跳过引导|跳过教程|跳过验证|Skip login|Skip for now",
            action = RuleAction.CLICK,
            builtIn = true
        )
    }

    /* ---------------- 节点匹配 ---------------- */

    private fun flatten(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        limit: Int
    ) {
        val stack = LinkedList<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val node = stack.removeFirst()
            out.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (out.size + stack.size < limit) stack.add(child) else runCatching { child.recycle() }
            }
        }
    }

    private fun matchFirst(nodes: List<AccessibilityNodeInfo>, rule: Rule): AccessibilityNodeInfo? {
        val texts = rule.textList()
        val ids = rule.idList()
        val excludes = rule.excludeList()
        if (texts.isEmpty() && ids.isEmpty()) return null

        for (node in nodes) {
            if (!node.isVisibleToUser) continue
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName.orEmpty()
            val blob = if (desc.isEmpty()) text else "$text $desc"

            if (excludes.any { blob.contains(it, ignoreCase = true) }) continue

            val textHit = texts.any { blob.contains(it, ignoreCase = true) }
            val idHit = ids.any { id.contains(it, ignoreCase = true) }
            if (textHit || idHit) return node
        }
        return null
    }

    /* ---------------- 执行 ---------------- */

    private fun perform(rule: Rule, node: AccessibilityNodeInfo, target: String, s: Settings) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val matched = (node.text?.toString() ?: node.viewIdResourceName ?: "").take(24)
        val delay = rule.delayMs.coerceAtLeast(0) + s.clickDelayMs.toLong()

        val run = {
            val ok = when (rule.action) {
                RuleAction.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                RuleAction.RECORD_ONLY -> true
                else -> clickNode(node) || clickByGesture(rect)
            }
            if (rule.action != RuleAction.RECORD_ONLY) {
                if (ok) {
                    Prefs.bumpCount()
                    // 悬浮胶囊与通知二选一：胶囊更直观且只在眼前闪一下，优先级更高；
                    // 没拿到悬浮窗权限时自动回退到通知，保证反馈不丢
                    val name = appLabel(service, target)
                    if (s.overlayToast && GlowToast.canOverlay(service)) {
                        GlowToast.show(service, name, matched)
                    } else if (s.showNotification) {
                        Notify.showSkip(service, name, matched)
                    }
                }
                if (s.hapticFeedback) service.vibrateTick()
            }
            Prefs.addLog(
                SkipLog(
                    pkg = target,
                    appName = appLabel(service, target),
                    matchedText = matched,
                    ruleName = rule.name.ifBlank { rule.id },
                    action = rule.action,
                    success = ok
                )
            )
        }

        if (delay > 0) handler.postDelayed(run, delay) else run()
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        runCatching { node.refresh() }
        var target = node
        var up = 0
        while (!target.isClickable && up < 3) {
            val parent = target.parent ?: break
            if (up > 0) pendingRecycle.add(target)
            target = parent
            up++
        }
        val result = if (target.isClickable && target.isEnabled) {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else false
        if (target !== node) pendingRecycle.add(target)
        pendingRecycle.forEach { runCatching { it.recycle() } }
        pendingRecycle.clear()
        return result
    }

    /** 兜底：节点不可点击时，用无障碍手势点它的中心点 */
    private fun clickByGesture(rect: Rect): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val cx = (rect.left + rect.width() / 2f).coerceIn(0f, 2000f)
        val cy = (rect.top + rect.height() / 2f).coerceIn(0f, 2000f)
        val path = Path().apply { moveTo(cx, cy) }
        return runCatching {
            service.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60))
                    .build(),
                null,
                null
            )
        }.getOrDefault(false)
    }

    /* ---------------- 限流 ---------------- */

    private fun allowByRate(pkg: String, maxPerMinute: Int): Boolean {
        if (maxPerMinute <= 0) return true
        val list = recentHits[pkg] ?: return true
        val cutoff = System.currentTimeMillis() - 60_000
        while (list.isNotEmpty() && list.first < cutoff) list.removeFirst()
        return list.size < maxPerMinute
    }

    companion object {
        private const val MAX_NODES = 260
    }
}
