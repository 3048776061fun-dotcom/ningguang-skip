package com.ningguang.skip.data

import java.util.UUID

/**
 * 一条订阅源。
 *
 * App 本体不预置任何第三方订阅：没有源就不联网，保持默认形态的纯净。
 * 用户自己添加 URL 之后，才会定期去拉取更新。
 */
data class Subscription(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var url: String = "",
    var version: Int = 0,          // 已应用的版本号，用于比对是否需要更新
    var ruleCount: Int = 0,        // 该订阅贡献的规则条数
    var lastUpdateAt: Long = 0L,   // 上次成功更新时间
    var lastError: String = "",    // 上次失败原因，展示在订阅卡片上
    var enabled: Boolean = true    // 关闭后该订阅的规则不再参与匹配
)

/** 订阅文件的结构。字段全部可空，缺什么都不会让解析整个失败 */
data class SubscriptionFile(
    var id: String = "",
    var name: String = "",
    var author: String = "",
    var version: Int = 0,
    var updateUrl: String = "",
    var rules: List<Rule> = emptyList()
)

/** 一次检查更新的结果 */
data class UpdateResult(
    val subId: String,
    val subName: String,
    val ok: Boolean,
    val version: Int,
    val added: Int = 0,
    val updated: Int = 0,
    val message: String = ""
) {
    val changed: Boolean get() = added > 0 || updated > 0
}
