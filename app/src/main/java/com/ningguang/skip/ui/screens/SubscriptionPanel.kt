package com.ningguang.skip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.data.Subscription
import com.ningguang.skip.engine.SubscriptionRepository
import com.ningguang.skip.ui.glow.NingguangButton
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.glow.NingguangSwitch
import com.ningguang.skip.ui.glow.SectionTitle
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.GlowWarm
import kotlinx.coroutines.launch

/**
 * 订阅源管理。
 *
 * App 不预置任何第三方源：列表为空时不会产生任何网络请求。
 * 用户自己填 URL 添加，之后可以手动检查更新，或交给 WorkManager 每天自动检查。
 */
@Composable
fun SubscriptionPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subs by Prefs.subscriptionsFlow.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var workingId by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }

    NingguangSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("规则订阅", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "添加订阅源后，规则可随 App 改版自动更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null)
                }
            }

            Spacer(Modifier.height(14.dp))

            // —— 添加 ——
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("订阅地址", style = MaterialTheme.typography.labelSmall) },
                placeholder = {
                    Text("https://.../rules.json", style = MaterialTheme.typography.bodySmall)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowCyan)
            )
            Spacer(Modifier.height(10.dp))
            NingguangButton(
                text = if (working) "正在添加…" else "添加订阅",
                icon = if (working) null else Icons.Rounded.Add,
                glow = GlowCyan,
                enabled = !working && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        working = true
                        tip = ""
                        val r = SubscriptionRepository.add(input)
                        tip = if (r.isSuccess) {
                            input = ""
                            val s = r.getOrNull()
                            "已添加：${s?.name}（${s?.ruleCount} 条规则）"
                        } else {
                            r.exceptionOrNull()?.message ?: "添加失败"
                        }
                        working = false
                    }
                }
            )

            if (tip.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // —— 全部更新 ——
            if (subs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                NingguangButton(
                    text = if (working && workingId == "*") "正在检查…" else "检查全部更新",
                    icon = if (working && workingId == "*") null else Icons.Rounded.CloudSync,
                    glow = GlowCyan,
                    filled = false,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            working = true
                            workingId = "*"
                            val results = SubscriptionRepository.checkAll()
                            val added = results.sumOf { it.added }
                            val updated = results.sumOf { it.updated }
                            val failed = results.count { !it.ok }
                            tip = "新增 $added · 更新 $updated" + if (failed > 0) " · $failed 个源失败" else ""
                            working = false
                            workingId = ""
                        }
                    }
                )
            }

            // —— 订阅列表 ——
            if (subs.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                NingguangCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        "还没有订阅源。\nApp 默认不联网，也不会预置任何第三方规则；" +
                            "填入一个规则 JSON 地址后才会开始拉取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SectionTitle("已添加 ${subs.size} 个")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    subs.forEach { sub ->
                        SubscriptionItem(
                            sub = sub,
                            busy = working && workingId == sub.id,
                            onToggle = { Prefs.updateSubscription(sub.copy(enabled = it)) },
                            onRefresh = {
                                scope.launch {
                                    working = true
                                    workingId = sub.id
                                    val r = SubscriptionRepository.refresh(sub)
                                    tip = if (r.ok) "${sub.name}：${r.message}" else "${sub.name}：${r.message}"
                                    working = false
                                    workingId = ""
                                }
                            },
                            onDelete = {
                                Prefs.removeSubscription(sub.id)
                                tip = "已删除 ${sub.name} 及其 ${sub.ruleCount} 条规则"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubscriptionItem(
    sub: Subscription,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    NingguangCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        glow = if (sub.lastError.isBlank()) GlowCyan else GlowWarm,
        elevation = 4.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sub.name.ifBlank { "未命名订阅" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    sub.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (sub.lastError.isNotBlank()) {
                        "更新失败：${sub.lastError}"
                    } else {
                        "v${sub.version} · ${sub.ruleCount} 条 · ${dateTimeText(sub.lastUpdateAt)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sub.lastError.isNotBlank()) GlowWarm else cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
            NingguangSwitch(checked = sub.enabled, glow = GlowCyan, onCheckedChange = onToggle)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                if (busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = GlowCyan
                        )
                    }
                } else {
                    NingguangButton(
                        text = "更新",
                        icon = Icons.Rounded.Refresh,
                        glow = GlowCyan,
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRefresh
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                NingguangButton(
                    text = "删除",
                    icon = Icons.Rounded.Delete,
                    glow = GlowWarm,
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete
                )
            }
        }
    }
}
