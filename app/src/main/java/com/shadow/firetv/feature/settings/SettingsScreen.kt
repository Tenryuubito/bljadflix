package com.shadow.firetv.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.source.StreamingSource
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.ui.components.LoadingIndicator
import com.shadow.firetv.core.ui.components.TvActionButton
import com.shadow.firetv.core.ui.theme.BljadflixColors
import com.shadow.firetv.core.ui.theme.ScreenBackground
import com.shadow.firetv.core.ui.theme.themeAccentColor

data class SettingsActions(
    val onSourceSelected: (StreamingSource) -> Unit,
    val onThemeSelected: (AppTheme) -> Unit,
    val onToggleIntro: () -> Unit,
    val onTriggerIndexer: () -> Unit,
    val onSync: () -> Unit,
    val onClearCache: () -> Unit,
    val onLogout: () -> Unit
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_logo_desc),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.title_settings), style = MaterialTheme.typography.headlineMedium, color = BljadflixColors.TextPrimary)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SettingsCard(stringResource(R.string.settings_section_streaming), Modifier.weight(1.15f)) {
                    SectionLabel(stringResource(R.string.settings_source_label))
                    StreamingSource.values().forEachIndexed { index, source ->
                        OptionRow(
                            label = source.displayName,
                            selected = source == state.selectedSource,
                            onClick = { actions.onSourceSelected(source) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel(stringResource(R.string.settings_theme_label))
                    AppTheme.values().forEach { theme ->
                        OptionRow(
                            label = theme.displayName,
                            selected = theme == state.selectedTheme,
                            onClick = { actions.onThemeSelected(theme) },
                            swatch = themeAccentColor(theme)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TvActionButton(
                        text = stringResource(if (state.isIntroEnabled) R.string.settings_intro_on else R.string.settings_intro_off),
                        onClick = actions.onToggleIntro,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TvActionButton(
                        text = stringResource(R.string.btn_trigger_indexer),
                        onClick = actions.onTriggerIndexer,
                        enabled = !state.isIndexing,
                        icon = painterResource(R.drawable.ic_sync),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = if (state.isIndexing) {
                            { LoadingIndicator(size = 18.dp, color = BljadflixColors.TextPrimary) }
                        } else {
                            null
                        }
                    )
                    StatusText(state.indexingStatusMessage)
                }

                SettingsCard(stringResource(R.string.settings_section_profile), Modifier.weight(1f)) {
                    InfoRow(stringResource(R.string.settings_username), state.username)
                    InfoRow(stringResource(R.string.settings_user_id), if (state.userId != -1) "#${state.userId}" else "-")
                    InfoRow(stringResource(R.string.settings_device_id), state.deviceId)
                    Spacer(Modifier.height(10.dp))
                    TvActionButton(
                        text = stringResource(R.string.btn_sync_data),
                        onClick = actions.onSync,
                        enabled = !state.isSyncing,
                        icon = painterResource(R.drawable.ic_sync),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = if (state.isSyncing) {
                            { LoadingIndicator(size = 18.dp, color = BljadflixColors.TextPrimary) }
                        } else {
                            null
                        }
                    )
                    StatusText(state.syncStatusMessage)
                }

                SettingsCard(stringResource(R.string.settings_section_app), Modifier.weight(1f)) {
                    InfoRow(stringResource(R.string.settings_application), stringResource(R.string.settings_app_label))
                    InfoRow(stringResource(R.string.settings_version), state.appVersion)
                    Spacer(Modifier.height(10.dp))
                    TvActionButton(
                        text = stringResource(R.string.btn_clear_cache),
                        onClick = actions.onClearCache,
                        icon = painterResource(R.drawable.ic_trash),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TvActionButton(
                        text = stringResource(R.string.btn_logout),
                        onClick = actions.onLogout,
                        isPrimary = true,
                        icon = painterResource(R.drawable.ic_logout),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(BljadflixColors.Surface.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, BljadflixColors.Border), RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = BljadflixColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun StatusText(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = BljadflixColors.TextSecondary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = BljadflixColors.TextMuted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = BljadflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatch: Color? = null
) {
    val shape = RoundedCornerShape(10.dp)
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SelectableSurfaceDefaults.shape(shape),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = BljadflixColors.SurfaceElevated,
            contentColor = BljadflixColors.TextSecondary,
            selectedContainerColor = primary.copy(alpha = 0.18f),
            selectedContentColor = BljadflixColors.TextPrimary,
            focusedContainerColor = BljadflixColors.TextPrimary,
            focusedContentColor = BljadflixColors.Surface,
            focusedSelectedContainerColor = BljadflixColors.TextPrimary,
            focusedSelectedContentColor = BljadflixColors.Surface
        ),
        border = SelectableSurfaceDefaults.border(
            selectedBorder = Border(BorderStroke(1.5.dp, primary), shape = shape)
        )
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(swatch, CircleShape)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
            }
        }
    }
}
