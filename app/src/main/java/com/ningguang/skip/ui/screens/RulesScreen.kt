package com.ningguang.skip.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
// 注意：var x by remember { mutableStateOf() } 依赖 getValue / setValue 这对扩展导入，
// 它们只以 operator 形式被使用，不要当成「未使用的 import」删掉
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.data.Rule
import com.ningguang.skip.data.RuleAction
import com.ningguang.skip.data.normalized
import com.ningguang.skip.ui.glow.NingguangButton
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.glow.NingguangListItem
import com.ningguang.skip.ui.glow.NingguangSwitch
import com.ningguang.skip.ui.glow.SectionTitle
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.GlowViolet
import com.ningguang.skip.ui.theme.GlowWarm
import com.ningguang.skip.util.launcherApps

@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val rules by Prefs.rulesFlow.collectAsStateWithLifecycle()
    val settings by Prefs.settingsFlow.collectAsStateWithLifecycle()
    val subs by Prefs.subscriptionsFlow.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Rule?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }

    val apps = remember(context) { launcherApps(context) }
    val shownApps = if (showAllApps) apps else apps.take(10)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            ScreenHeader(
                title = "规则",
                subtitle = "共 ${rules.count { it.enabled }} 条启用中 · ${rules.size} 条总计"
            )

            // —— 操作区 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NingguangButton(
                    text = "新建",
                    icon = Icons.Filled.Add,
                    glow = GlowCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { editing = Rule(action = RuleAction.CLICK) }
                )
                NingguangButton(
                    text = "导入",
                    icon = Icons.Filled.Download,
                    glow = GlowViolet,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = { importing = true }
                )
            }

            // —— 订阅入口 ——
            SectionTitle("更新")
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                NingguangListItem(
                    title = if (subs.isEmpty()) "规则订阅" else "规则订阅 · ${subs.count { it.enabled }} 个启用中",
                    subtitle = if (subs.isEmpty()) {
                        "添加订阅源，规则随 App 改版自动更新"
                    } else {
                        "共 ${subs.size} 个源 · ${rules.count { it.isFromSubscription() }} 条订阅规则"
                    },
                    icon = Icons.Filled.Sync,
                    iconTint = GlowCyan,
                    glow = GlowCyan,
                    onClick = { showSubs = true }
                )
            }

            // —— 规则列表 ——
            SectionTitle("规则列表")
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rules.forEach { rule ->
                    RuleItem(
                        rule = rule,
                        onToggle = {
                            Prefs.updateRule(rule.copy(enabled = it))
                        },
                        onEdit = { editing = rule }
                    )
                }
                if (rules.isEmpty()) {
                    NingguangCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Text(
                            "还没有规则。可以点「新建」手写一条，或「导入」一份订阅规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // —— 应用例外 ——
            SectionTitle("应用例外（黑名单）")
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shownApps.forEach { (pkg, label) ->
                    val blocked = pkg in settings.disabledApps
                    NingguangListItem(
                        title = label,
                        subtitle = pkg,
                        glow = if (blocked) GlowWarm else MaterialTheme.colorScheme.primary,
                        onClick = {
                            val next = settings.disabledApps.toMutableSet()
                            if (blocked) next.remove(pkg) else next.add(pkg)
                            Prefs.updateSettings(settings.copy(disabledApps = next))
                        }
                    ) {
                        NingguangSwitch(
                            checked = !blocked,
                            glow = GlowCyan,
                            onCheckedChange = { enabled ->
                                val next = settings.disabledApps.toMutableSet()
                                if (enabled) next.remove(pkg) else next.add(pkg)
                                Prefs.updateSettings(settings.copy(disabledApps = next))
                            }
                        )
                    }
                }
                if (apps.size > 10) {
                    NingguangButton(
                        text = if (showAllApps) "收起" else "查看全部 ${apps.size} 个应用",
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showAllApps = !showAllApps }
                    )
                }
            }
        }

        // —— 订阅管理弹层 ——
        if (showSubs) {
            SubscriptionPanel(onDismiss = { showSubs = false })
        }

        // —— 规则编辑弹层 ——
        if (editing != null) {
            val isNew = rules.none { it.id == editing!!.id }
            RuleEditor(
                rule = editing!!,
                isNew = isNew,
                onDismiss = { editing = null },
                onSave = { saved ->
                    if (isNew) Prefs.addRule(saved) else Prefs.updateRule(saved)
                    editing = null
                },
                onDelete = {
                    Prefs.removeRule(it.id)
                    editing = null
                }
            )
        }

        // —— 订阅导入弹层 ——
        if (importing) {
            ImportDialog(
                onDismiss = { importing = false },
                onImport = { text ->
                    val ok = importRules(context, text)
                    Toast.makeText(
                        context,
                        if (ok > 0) "已导入 $ok 条规则" else "解析失败，请检查 JSON 格式",
                        Toast.LENGTH_SHORT
                    ).show()
                    importing = false
                },
                onExport = { exportRules(context) }
            )
        }
    }
}

@Composable
private fun RuleItem(rule: Rule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val detail = buildList {
        if (rule.appId.isNotBlank()) add(rule.appId)
        if (rule.texts.isNotBlank()) add("文本：${rule.texts}")
        if (rule.ids.isNotBlank()) add("ID：${rule.ids}")
        if (rule.action != RuleAction.CLICK) add(actionLabel(rule.action))
        if (rule.builtIn) add("内置")
        if (rule.isFromSubscription()) add("订阅")
    }.joinToString(" · ")

    NingguangListItem(
        title = rule.name.ifBlank { "未命名规则" },
        subtitle = detail.ifBlank { "点击编辑，填写匹配条件" },
        icon = Icons.Filled.List,
        iconTint = if (rule.builtIn) GlowViolet else GlowCyan,
        glow = if (rule.builtIn) GlowViolet else GlowCyan,
        onClick = onEdit
    ) {
        NingguangSwitch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}

private fun actionLabel(action: String) = when (action) {
    RuleAction.BACK -> "返回键"
    RuleAction.RECORD_ONLY -> "仅记录"
    else -> "点击"
}

/* ---------------------------------------------------------------------------
 * 规则编辑弹层
 * ------------------------------------------------------------------------- */

@Composable
private fun RuleEditor(
    rule: Rule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: (Rule) -> Unit
) {
    var name by remember { mutableStateOf(rule.name) }
    var appId by remember { mutableStateOf(rule.appId) }
    var activity by remember { mutableStateOf(rule.activity) }
    var texts by remember { mutableStateOf(rule.texts) }
    var ids by remember { mutableStateOf(rule.ids) }
    var exclude by remember { mutableStateOf(rule.excludeTexts) }
    var action by remember { mutableStateOf(rule.action) }

    NingguangSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "新建规则" else "编辑规则",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, null)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (rule.isFromSubscription()) {
                Text(
                    "这条规则来自订阅源，下次订阅更新会覆盖你的改动。" +
                        "想长期自定义，请复制一份内容再新建一条本地规则。",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlowWarm
                )
                Spacer(Modifier.height(10.dp))
            }

            EditorField("规则名称", name, { name = it }, "例如：抖音开屏")
            EditorField("应用包名（留空=全局）", appId, { appId = it }, "com.ss.android.ugc.aweme")
            EditorField("Activity（可选）", activity, { activity = it }, "SplashActivity")
            EditorField("匹配文本（| 分隔）", texts, { texts = it }, "跳过|跳过广告")
            EditorField("控件 ID 关键词（| 分隔）", ids, { ids = it }, "skip|ad_close")
            EditorField("排除文本（| 分隔）", exclude, { exclude = it }, "跳过登录")

            SectionTitle("动作")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionChip("点击", action == RuleAction.CLICK) { action = RuleAction.CLICK }
                ActionChip("返回键", action == RuleAction.BACK) { action = RuleAction.BACK }
                ActionChip("仅记录", action == RuleAction.RECORD_ONLY) { action = RuleAction.RECORD_ONLY }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NingguangButton(
                    text = "删除",
                    icon = Icons.Filled.Delete,
                    glow = GlowWarm,
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onDelete(rule) }
                )
                NingguangButton(
                    text = "保存",
                    icon = Icons.Filled.Edit,
                    glow = GlowCyan,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(
                            rule.copy(
                                name = name, appId = appId, activity = activity,
                                texts = texts, ids = ids, excludeTexts = exclude, action = action
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    NingguangCard(
        shape = RoundedCornerShape(16.dp),
        glow = if (selected) GlowCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        stateGlow = if (selected) 0.8f else 0f,
        elevation = 2.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        onClick = onClick
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) GlowCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GlowCyan,
            cursorColor = GlowCyan
        )
    )
}

/* ---------------------------------------------------------------------------
 * 导入 / 导出
 * ------------------------------------------------------------------------- */

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onExport: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    NingguangSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "订阅规则",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }
            Text(
                "粘贴规则 JSON 数组即可导入；也可以先把现有规则导出到剪贴板，改完再粘回来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text("[\n  {\"name\":\"示例\",\"appId\":\"\",\"texts\":\"跳过\"}\n]") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowViolet)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NingguangButton(
                    text = "导出",
                    icon = Icons.Filled.Upload,
                    filled = false,
                    glow = GlowViolet,
                    modifier = Modifier.weight(1f),
                    onClick = onExport
                )
                NingguangButton(
                    text = "导入",
                    icon = Icons.Filled.Download,
                    glow = GlowCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { onImport(text) }
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * 序列化
 * ------------------------------------------------------------------------- */

private fun importRules(context: Context, json: String): Int {
    return runCatching {
        val type = object : TypeToken<List<Rule>>() {}.type
        val list: List<Rule> = Gson().fromJson(json, type) ?: return 0
        val existing = Prefs.rules.toMutableList()
        var added = 0
        list.forEach { raw ->
            // 手动导入的一律视为本地规则：不挂订阅来源，避免订阅删除/更新时误伤
            val r = raw.normalized().copy(builtIn = false, sourceId = "")
            if (existing.none { it.id == r.id }) {
                existing.add(r)
                added++
            } else {
                val idx = existing.indexOfFirst { it.id == r.id }
                existing[idx] = r
                added++
            }
        }
        if (added > 0) Prefs.saveRules(existing)
        added
    }.getOrDefault(0)
}

private fun exportRules(context: Context) {
    val json = Gson().toJson(Prefs.rules)
    val cm = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("ningguang_rules", json))
    Toast.makeText(context, "规则已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
