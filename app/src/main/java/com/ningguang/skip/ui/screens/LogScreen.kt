package com.ningguang.skip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.data.RuleAction
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.glow.NingguangListItem
import com.ningguang.skip.ui.glow.SectionTitle
import com.ningguang.skip.ui.theme.GlowGreen
import com.ningguang.skip.ui.theme.GlowWarm

@Composable
fun LogScreen() {
    val logs by Prefs.logsFlow.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = "日志",
            subtitle = "最近 ${logs.size} 条跳过记录（最多保留 300 条）"
        )

        if (logs.isEmpty()) {
            NingguangCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                Text(
                    "还没有记录。\n保持服务开启，下次开屏广告出现时会自动跳过并记在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            NingguangListItem(
                title = "清空日志",
                subtitle = "删除全部跳过记录",
                icon = Icons.Rounded.Delete,
                iconTint = GlowWarm,
                glow = GlowWarm,
                modifier = Modifier.padding(horizontal = 18.dp),
                onClick = { Prefs.clearLogs() }
            )

            // 按日期分组
            val grouped = logs.groupBy { dateText(it.time) }
            grouped.forEach { (date, items) ->
                SectionTitle(date)
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { log ->
                        NingguangCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            glow = if (log.success) GlowGreen else GlowWarm,
                            elevation = 3.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (log.success) Icons.Rounded.CheckCircle
                                    else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (log.success) GlowGreen else GlowWarm,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        log.appName.ifBlank { log.pkg },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        buildString {
                                            append(log.ruleName)
                                            if (log.matchedText.isNotBlank()) append(" · ${log.matchedText}")
                                            if (log.action == RuleAction.RECORD_ONLY) append(" · 仅记录")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    timeText(log.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}
