package io.github.mangi.eta.ui.screens.tokenstats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.db.TokenUsageDayAggregate
import io.github.mangi.eta.data.db.TokenUsageModelAggregate
import io.github.mangi.eta.data.db.TokenUsageProviderAggregate
import io.github.mangi.eta.data.db.TokenUsageTotal
import io.github.mangi.eta.data.model.TokenPriceTable
import io.github.mangi.eta.data.repository.TokenUsageRepository
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffold
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Token 用量统计页。
 *
 * 数据来源：AgentLoop 在每次模型调用收到 ProviderEvent.Usage 时写入 Room。
 * 费用为估算值（单价表见 TokenPriceTable，可在页面内覆盖）。
 */
@Composable
internal fun TokenUsageStatsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf<TokenUsageTotal?>(null) }
    var monthTotal by remember { mutableStateOf<TokenUsageTotal?>(null) }
    var monthCost by remember { mutableStateOf<Double?>(null) }
    var days by remember { mutableStateOf<List<TokenUsageDayAggregate>>(emptyList()) }
    var providers by remember { mutableStateOf<List<TokenUsageProviderAggregate>>(emptyList()) }
    var models by remember { mutableStateOf<List<TokenUsageModelAggregate>>(emptyList()) }
    var prices by remember { mutableStateOf<List<TokenPriceTable.PriceEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPriceEditor by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            today = TokenUsageRepository.totalToday()
            monthTotal = TokenUsageRepository.totalLast30Days()
            monthCost = TokenUsageRepository.estimateCostUsd(30)
            days = TokenUsageRepository.dayAggregates(30)
            providers = TokenUsageRepository.providerAggregates(30)
            models = TokenUsageRepository.modelAggregates(30)
            prices = TokenPriceTable.allEntries(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    MiuixScaffold(
        title = stringResource(R.string.token_usage_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.token_usage_clear),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) { paddingValues, scrollBehavior, sidePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = sidePadding)
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .navigationBarsPadding(),
        ) {
            // ── 概览 ──
            EtaPreferenceGroupTitle(stringResource(R.string.token_usage_overview))
            EtaPreferenceGroup {
                OverviewRow(
                    label = stringResource(R.string.token_usage_today),
                    total = today,
                )
                EtaPreferenceDivider()
                OverviewRow(
                    label = stringResource(R.string.token_usage_last_30_days),
                    total = monthTotal,
                    costUsd = monthCost,
                )
            }

            // ── 按天 ──
            if (days.isNotEmpty()) {
                EtaPreferenceGroupTitle(stringResource(R.string.token_usage_by_day))
                EtaPreferenceGroup {
                    days.forEachIndexed { index, row ->
                        if (index > 0) EtaPreferenceDivider()
                        EtaPreference(
                            title = row.day,
                            summary = stringResource(
                                R.string.token_usage_calls_format, row.calls,
                            ),
                            endActions = {
                                Text(
                                    text = formatTokens(row.inputTokens + row.outputTokens),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            },
                        )
                    }
                }
            }

            // ── 按服务商 ──
            if (providers.isNotEmpty()) {
                EtaPreferenceGroupTitle(stringResource(R.string.token_usage_by_provider))
                EtaPreferenceGroup {
                    providers.forEachIndexed { index, row ->
                        if (index > 0) EtaPreferenceDivider()
                        EtaPreference(
                            title = row.providerName,
                            summary = stringResource(
                                R.string.token_usage_calls_format, row.calls,
                            ),
                            endActions = {
                                Text(
                                    text = formatTokens(row.inputTokens + row.outputTokens),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            },
                        )
                    }
                }
            }

            // ── 按模型 ──
            if (models.isNotEmpty()) {
                EtaPreferenceGroupTitle(stringResource(R.string.token_usage_by_model))
                EtaPreferenceGroup {
                    models.forEachIndexed { index, row ->
                        if (index > 0) EtaPreferenceDivider()
                        val cost = TokenPriceTable.estimateUsd(
                            row.model, row.inputTokens, row.outputTokens, context,
                        )
                        EtaPreference(
                            title = row.model,
                            summary = row.providerName + " · " + stringResource(
                                R.string.token_usage_calls_format, row.calls,
                            ),
                            endActions = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatTokens(row.inputTokens + row.outputTokens),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    if (cost != null) {
                                        Text(
                                            text = formatUsd(cost),
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (!loading && days.isEmpty()) {
                EtaPreferenceGroup {
                    EtaPreference(
                        title = stringResource(R.string.token_usage_empty),
                        summary = stringResource(R.string.token_usage_empty_hint),
                    )
                }
            }

            // ── 单价设置 ──
            EtaPreferenceGroupTitle(stringResource(R.string.token_usage_price_settings))
            EtaPreferenceGroup {
                EtaPreference(
                    title = stringResource(R.string.token_usage_price_hint),
                    summary = stringResource(R.string.token_usage_price_hint_summary),
                )
                prices.forEach { entry ->
                    EtaPreferenceDivider()
                    EtaPreference(
                        title = entry.keyword + if (entry.isCustom) " · " +
                            stringResource(R.string.token_usage_custom) else "",
                        summary = "$${entry.inputPricePer1M} / $${entry.outputPricePer1M}" +
                            stringResource(R.string.token_usage_per_1m),
                        endActions = {
                            if (entry.isCustom) {
                                IconButton(
                                    onClick = {
                                        TokenPriceTable.removeCustomPrice(context, entry.keyword)
                                        reload()
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        },
                    )
                }
                EtaPreferenceDivider()
                EtaPreference(
                    title = stringResource(R.string.token_usage_add_price),
                    startAction = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showPriceEditor = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 清空确认
    EtaWindowDialog(
        show = showClearConfirm,
        title = stringResource(R.string.token_usage_clear),
        summary = stringResource(R.string.token_usage_clear_confirm),
        onDismissRequest = { showClearConfirm = false },
    ) {
        MiuixDialogActions(
            confirmText = stringResource(R.string.action_delete),
            onCancel = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                scope.launch {
                    TokenUsageRepository.clearAll()
                    reload()
                }
            },
            destructive = true,
        )
    }

    // 单价编辑器
    if (showPriceEditor) {
        PriceEditorDialog(
            onDismiss = { showPriceEditor = false },
            onSave = { keyword, inputPrice, outputPrice ->
                TokenPriceTable.setCustomPrice(context, keyword, inputPrice, outputPrice)
                showPriceEditor = false
                reload()
            },
        )
    }
}

@Composable
private fun OverviewRow(
    label: String,
    total: TokenUsageTotal?,
    costUsd: Double? = null,
) {
    val input = total?.inputTokens ?: 0L
    val output = total?.outputTokens ?: 0L
    val calls = total?.calls ?: 0L
    EtaPreference(
        title = label,
        summary = if (calls > 0) {
            "↑ ${formatTokens(input)} · ↓ ${formatTokens(output)}"
        } else {
            null
        },
        endActions = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTokens(input + output),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (costUsd != null) {
                    Text(
                        text = "≈ ${formatUsd(costUsd)}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        },
    )
}

@Composable
private fun PriceEditorDialog(
    onDismiss: () -> Unit,
    onSave: (keyword: String, inputPrice: Double, outputPrice: Double) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var inputPrice by remember { mutableStateOf("") }
    var outputPrice by remember { mutableStateOf("") }
    val valid = keyword.isNotBlank() &&
        inputPrice.toDoubleOrNull() != null &&
        outputPrice.toDoubleOrNull() != null

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.token_usage_add_price),
        summary = stringResource(R.string.token_usage_add_price_hint),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            PriceTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = stringResource(R.string.token_usage_keyword_hint),
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceTextField(
                    value = inputPrice,
                    onValueChange = { inputPrice = it },
                    placeholder = stringResource(R.string.token_usage_input_price_hint),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PriceTextField(
                    value = outputPrice,
                    onValueChange = { outputPrice = it },
                    placeholder = stringResource(R.string.token_usage_output_price_hint),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            MiuixDialogActions(
                confirmText = stringResource(R.string.action_save),
                onCancel = onDismiss,
                onConfirm = {
                    onSave(
                        keyword.trim().lowercase(),
                        inputPrice.toDouble(),
                        outputPrice.toDouble(),
                    )
                },
                confirmEnabled = valid,
            )
        }
    }
}

@Composable
private fun PriceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = colors.onSurface,
            fontSize = 15.sp,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        decorationBox = { innerTextField ->
            Column {
                if (value.isEmpty()) {
                    Text(text = placeholder, fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

private fun formatUsd(usd: Double): String = when {
    usd >= 100 -> "$%.0f".format(usd)
    usd >= 1 -> "$%.2f".format(usd)
    else -> "$%.4f".format(usd)
}
