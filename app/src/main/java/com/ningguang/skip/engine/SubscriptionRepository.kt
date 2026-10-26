package com.ningguang.skip.engine

import com.google.gson.Gson
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.data.Rule
import com.ningguang.skip.data.Subscription
import com.ningguang.skip.data.SubscriptionFile
import com.ningguang.skip.data.UpdateResult
import com.ningguang.skip.data.normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 订阅源更新。
 *
 * 流程：GET 订阅 URL → 解析 JSON → 比对 version → 按 rule.id 合并进本地规则库。
 *
 * 三条约束：
 *   1. 版本号只增不减：远端 version <= 本地时跳过（远端没标 version 视为 0，每次都更新一次）
 *   2. 本地优先：用户手写的规则永不被订阅覆盖
 *   3. 保留用户开关：订阅规则被更新时，沿用用户之前的启用/停用状态
 */
object SubscriptionRepository {

    private const val TIMEOUT_MS = 15_000
    private const val UA = "NingguangSkip/1.0 (rule-subscription)"

    /**
     * 拉取 + 合并单个订阅。
     * 注意：会**就地修改**传入的 [sub]（版本号、规则数、更新时间、错误信息），
     * 调用方负责决定是 updateSubscription 还是 addSubscription。
     */
    suspend fun check(sub: Subscription): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val text = fetch(sub.url)
            val file = parse(text)
            val version = file.version

            if (version > 0 && version <= sub.version) {
                sub.lastError = ""
                return@withContext UpdateResult(
                    subId = sub.id, subName = sub.name, ok = true,
                    version = sub.version, message = "已是最新"
                )
            }

            val incoming = normalizeRules(file, sub)
            val merged = merge(sub, incoming)

            sub.name = file.name.ifBlank { sub.name.ifBlank { hostOf(sub.url) } }
            sub.version = version
            sub.ruleCount = merged.list.count { it.sourceId == sub.id }
            sub.lastUpdateAt = System.currentTimeMillis()
            sub.lastError = ""

            UpdateResult(
                subId = sub.id,
                subName = sub.name,
                ok = true,
                version = version,
                added = merged.added,
                updated = merged.updated,
                message = "新增 ${merged.added} · 更新 ${merged.updated}"
            )
        } catch (e: Exception) {
            val reason = e.message?.take(60) ?: e.javaClass.simpleName
            sub.lastError = reason
            UpdateResult(
                subId = sub.id, subName = sub.name, ok = false,
                version = sub.version, message = reason
            )
        }
    }

    /** 检查全部订阅（串行，避免同时打多个源） */
    suspend fun checkAll(): List<UpdateResult> = withContext(Dispatchers.IO) {
        Prefs.subscriptions.filter { it.enabled }.map { check(it) }
    }

    /** 校验并添加一个订阅：先拉一次，拉不通就不写进列表，避免留下一个坏源每天重试 */
    suspend fun add(url: String): Result<Subscription> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("请填写 http/https 开头的地址"))
        }
        if (Prefs.subscriptions.any { it.url == trimmed }) {
            return@withContext Result.failure(IllegalArgumentException("这个订阅已经添加过了"))
        }
        val sub = Subscription(name = hostOf(trimmed), url = trimmed)
        val result = check(sub)
        if (!result.ok) {
            return@withContext Result.failure(IllegalStateException("拉取失败：${result.message}"))
        }
        Prefs.addSubscription(sub)
        Result.success(sub)
    }

    /** 更新单个已存在的订阅，并把变更写回存储 */
    suspend fun refresh(sub: Subscription): UpdateResult {
        val result = check(sub)
        Prefs.updateSubscription(sub)
        return result
    }

    /* ---------------- 网络 ---------------- */

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val stream = if (conn.contentEncoding.equals("gzip", true)) {
                GZIPInputStream(conn.inputStream)
            } else conn.inputStream
            stream.bufferedReader().use(BufferedReader::readText)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): SubscriptionFile {
        val file = Gson().fromJson(text, SubscriptionFile::class.java)
            ?: throw IllegalStateException("JSON 解析失败")
        if (file.rules.isEmpty()) throw IllegalStateException("订阅里没有规则")
        return file
    }

    /* ---------------- 合并 ---------------- */

    /** 给订阅规则补稳定 id 与来源标记：远端没写 id 时用「订阅id#序号」，保证下次更新能对齐同一条 */
    private fun normalizeRules(file: SubscriptionFile, sub: Subscription): List<Rule> {
        val prefix = file.id.ifBlank { sub.id }
        return file.rules.mapIndexed { index, raw ->
            val r = raw.normalized()
            val stableId = r.id.ifBlank { "${prefix}#${index}" }
            r.copy(
                id = stableId,
                sourceId = sub.id,
                builtIn = false,
                name = r.name.ifBlank { file.name }
            )
        }
    }

    private class MergeOutcome(val list: List<Rule>, val added: Int, val updated: Int)

    private fun merge(sub: Subscription, incoming: List<Rule>): MergeOutcome {
        val current = Prefs.rules.toMutableList()
        var added = 0
        var updated = 0

        incoming.forEach { r ->
            val idx = current.indexOfFirst { it.id == r.id }
            if (idx < 0) {
                current.add(r)
                added++
            } else {
                val old = current[idx]
                // 本地手写的规则永不被订阅覆盖；用户主动关掉的订阅规则保持关闭
                if (old.isLocal()) return@forEach
                current[idx] = r.copy(enabled = old.enabled)
                updated++
            }
        }

        // 远端删掉的规则，本地也一并移除（仅限该订阅贡献的）
        val remoteIds = incoming.map { it.id }.toSet()
        current.removeAll { it.sourceId == sub.id && it.id !in remoteIds }

        Prefs.saveRules(current)
        return MergeOutcome(current, added, updated)
    }

    private fun hostOf(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault(url).take(40)
}
