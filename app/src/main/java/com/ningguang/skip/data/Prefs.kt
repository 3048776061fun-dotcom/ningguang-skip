package com.ningguang.skip.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * 本地存储 + 进程内状态。
 * 全部走 SharedPreferences + Gson，不引数据库，装完即用、无需迁移。
 */
object Prefs {

    private const val FILE = "ningguang_skip"
    private const val K_SETTINGS = "settings"
    private const val K_RULES = "rules"
    private const val K_SUBS = "subscriptions"
    private const val K_LOGS = "logs"
    private const val K_TODAY = "today_count"
    private const val K_TODAY_DATE = "today_date"
    private const val K_TOTAL = "total_count"
    private const val MAX_LOGS = 300

    private val gson = Gson()
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _settings.value = loadSettings()
        val stored = loadRules()
        _rules.value = if (stored.isEmpty()) builtInRules() else stored
        _subscriptions.value = loadSubscriptions()
        _logs.value = loadLogs()
        refreshCounters()
    }

    /* ---------------- 设置 ---------------- */

    private val _settings = MutableStateFlow(Settings())
    val settingsFlow: StateFlow<Settings> = _settings.asStateFlow()
    val settings: Settings get() = _settings.value

    fun updateSettings(newValue: Settings) {
        _settings.value = newValue
        sp.edit().putString(K_SETTINGS, gson.toJson(newValue)).apply()
    }

    /**
     * 读设置。
     * 注意：Gson 走 Unsafe 反序列化，旧版本存档里不存在的字段会被填成 false / null，
     * 新增开关会因此被静默关闭，所以这里对新增字段做一次缺省补齐。
     */
    private fun loadSettings(): Settings {
        val raw = sp.getString(K_SETTINGS, null) ?: return Settings()
        return runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            if (!obj.has("overlayToast")) obj.addProperty("overlayToast", true)
            if (!obj.has("autoUpdateSubscription")) obj.addProperty("autoUpdateSubscription", true)
            if (!obj.has("notifySubscriptionUpdate")) obj.addProperty("notifySubscriptionUpdate", true)
            gson.fromJson(obj, Settings::class.java) ?: Settings()
        }.getOrDefault(Settings())
    }

    /* ---------------- 订阅源 ---------------- */

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptionsFlow: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()
    val subscriptions: List<Subscription> get() = _subscriptions.value

    fun saveSubscriptions(list: List<Subscription>) {
        _subscriptions.value = list
        sp.edit().putString(K_SUBS, gson.toJson(list)).apply()
    }

    fun addSubscription(sub: Subscription) = saveSubscriptions(subscriptions + sub)

    fun updateSubscription(sub: Subscription) =
        saveSubscriptions(subscriptions.map { if (it.id == sub.id) sub else it })

    /** 删除订阅，同时清掉它带来的全部规则，避免残留孤儿规则 */
    fun removeSubscription(id: String) {
        saveSubscriptions(subscriptions.filter { it.id != id })
        saveRules(rules.filter { it.sourceId != id })
    }

    private fun loadSubscriptions(): List<Subscription> = runCatching {
        val type = object : TypeToken<List<Subscription>>() {}.type
        (gson.fromJson<List<Subscription>>(sp.getString(K_SUBS, null), type) ?: emptyList())
    }.getOrDefault(emptyList())

    /* ---------------- 规则 ---------------- */

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rulesFlow: StateFlow<List<Rule>> = _rules.asStateFlow()
    val rules: List<Rule> get() = _rules.value

    fun saveRules(list: List<Rule>) {
        _rules.value = list
        sp.edit().putString(K_RULES, gson.toJson(list)).apply()
    }

    private fun loadRules(): List<Rule> = runCatching {
        val type = object : TypeToken<List<Rule>>() {}.type
        (gson.fromJson<List<Rule>>(sp.getString(K_RULES, null), type) ?: emptyList()).map { it.normalized() }
    }.getOrDefault(emptyList())

    fun addRule(rule: Rule) = saveRules(rules + rule)

    fun updateRule(rule: Rule) = saveRules(rules.map { if (it.id == rule.id) rule else it })

    fun removeRule(id: String) = saveRules(rules.filter { it.id != id })

    fun resetBuiltInRules() = saveRules(builtInRules())

    /* ---------------- 日志 ---------------- */

    private val _logs = MutableStateFlow<List<SkipLog>>(emptyList())
    val logsFlow: StateFlow<List<SkipLog>> = _logs.asStateFlow()
    val logs: List<SkipLog> get() = _logs.value

    fun addLog(log: SkipLog) {
        val next = (listOf(log) + logs).take(MAX_LOGS)
        _logs.value = next
        sp.edit().putString(K_LOGS, gson.toJson(next)).apply()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        sp.edit().remove(K_LOGS).apply()
    }

    private fun loadLogs(): List<SkipLog> = runCatching {
        val type = object : TypeToken<List<SkipLog>>() {}.type
        gson.fromJson<List<SkipLog>>(sp.getString(K_LOGS, null), type) ?: emptyList()
    }.getOrDefault(emptyList())

    /* ---------------- 计数 ---------------- */

    private val _todayCount = MutableStateFlow(0)
    val todayCountFlow: StateFlow<Int> = _todayCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCountFlow: StateFlow<Int> = _totalCount.asStateFlow()

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    fun refreshCounters() {
        val key = todayKey()
        if (sp.getString(K_TODAY_DATE, "") != key) {
            sp.edit().putString(K_TODAY_DATE, key).putInt(K_TODAY, 0).apply()
        }
        _todayCount.value = sp.getInt(K_TODAY, 0)
        _totalCount.value = sp.getInt(K_TOTAL, 0)
    }

    fun bumpCount() {
        refreshCounters()
        val today = sp.getInt(K_TODAY, 0) + 1
        val total = sp.getInt(K_TOTAL, 0) + 1
        sp.edit().putInt(K_TODAY, today).putInt(K_TOTAL, total).apply()
        _todayCount.value = today
        _totalCount.value = total
    }

    fun resetCounters() {
        sp.edit().putInt(K_TODAY, 0).putInt(K_TOTAL, 0).putString(K_TODAY_DATE, todayKey()).apply()
        _todayCount.value = 0
        _totalCount.value = 0
    }

    /* ---------------- 服务运行状态 ---------------- */

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunningFlow: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}
