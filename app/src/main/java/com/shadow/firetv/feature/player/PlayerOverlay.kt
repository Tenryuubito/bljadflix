package com.shadow.firetv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.ui.components.LinearProgress
import com.shadow.firetv.core.ui.components.LoadingIndicator
import com.shadow.firetv.core.ui.components.TvActionButton
import com.shadow.firetv.core.ui.theme.BljadflixColors
import java.util.Locale

enum class PlayerPanel { ERROR, OPTIONS }

data class PlayerPanelActions(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onRetry: () -> Unit,
    val onOtherHoster: () -> Unit,
    val onClose: () -> Unit
)

fun formatPlaybackTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
private fun episodeLabel(episode: Episode): String {
    val base = stringResource(R.string.player_episode_label, episode.season_number, episode.episode_number)
    return if (episode.title.isNullOrBlank()) base else "$base – ${episode.title}"
}

@Composable
fun PlayerOverlay(
    state: PlayerUiState,
    controlsVisible: Boolean,
    seekText: String?,
    panel: PlayerPanel?,
    showSpinner: Boolean,
    actions: PlayerPanelActions
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = controlsVisible && panel == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControls(state)
        }

        AnimatedVisibility(
            visible = seekText != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                seekText.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = BljadflixColors.TextPrimary,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            )
        }

        val next = state.nextEpisode
        if (state.showNextEpisodeCard && next != null && panel == null) {
            NextEpisodeCard(state, next)
        }

        if (showSpinner) {
            LoadingIndicator(Modifier.align(Alignment.Center), size = 64.dp)
        }

        if (panel != null) {
            PlayerPanelView(state, panel, actions)
        }
    }
}

@Composable
private fun PlayerControls(state: PlayerUiState) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp)
        ) {
            if (state.seriesTitle.isNotBlank()) {
                Text(state.seriesTitle, style = MaterialTheme.typography.headlineMedium, color = BljadflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            state.currentEpisode?.let {
                Spacer(Modifier.height(4.dp))
                Text(episodeLabel(it), style = MaterialTheme.typography.titleMedium, color = BljadflixColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(R.string.player_play_pause),
                    tint = BljadflixColors.TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(20.dp))
                val duration = state.duration.takeIf { it > 0 } ?: 0f
                val current = if (duration > 0) state.currentTime else 0f
                Text(formatPlaybackTime(current.toInt()), style = MaterialTheme.typography.titleSmall, color = BljadflixColors.TextPrimary)
                Spacer(Modifier.width(16.dp))
                LinearProgress(
                    progress = if (duration > 0) current / duration else 0f,
                    modifier = Modifier.weight(1f),
                    height = 6.dp
                )
                Spacer(Modifier.width(16.dp))
                Text(formatPlaybackTime(duration.toInt()), style = MaterialTheme.typography.titleSmall, color = BljadflixColors.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.player_controls_hint), style = MaterialTheme.typography.bodyMedium, color = BljadflixColors.TextMuted)
        }
    }
}

@Composable
private fun BoxScope.NextEpisodeCard(state: PlayerUiState, next: Episode) {
    val countdown = state.countdownSeconds
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 48.dp, bottom = 120.dp)
            .width(380.dp)
            .background(BljadflixColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(
            if (countdown != null) stringResource(R.string.player_next_in, countdown) else stringResource(R.string.player_next_episode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(episodeLabel(next), style = MaterialTheme.typography.titleMedium, color = BljadflixColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (countdown != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgress(progress = countdown / PlayerViewModel.COUNTDOWN_SECONDS, height = 5.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (countdown != null) R.string.player_next_hint_countdown else R.string.player_next_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = BljadflixColors.TextMuted
        )
    }
}

@Composable
private fun PlayerPanelView(state: PlayerUiState, panel: PlayerPanel, actions: PlayerPanelActions) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(panel) { runCatching { firstButton.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(520.dp)
                .background(BljadflixColors.Surface, RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, BljadflixColors.Border), RoundedCornerShape(20.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isError = panel == PlayerPanel.ERROR
            Text(
                stringResource(if (isError) R.string.player_error_title else R.string.player_options_title),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isError) BljadflixColors.Error else BljadflixColors.TextPrimary
            )
            val message = if (isError) state.errorMessage.orEmpty() else state.currentEpisode?.let { episodeLabel(it) }.orEmpty()
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = BljadflixColors.TextSecondary)
            }
            Spacer(Modifier.height(4.dp))

            val buttons = buildList {
                if (!isError && state.nextEpisode != null) add(Triple(stringResource(R.string.player_next_episode), actions.onNext, true))
                if (!isError && state.previousEpisode != null) add(Triple(stringResource(R.string.player_previous_episode), actions.onPrevious, false))
                add(Triple(stringResource(if (isError) R.string.player_retry else R.string.player_reload), actions.onRetry, isError))
                add(Triple(stringResource(R.string.player_other_hoster), actions.onOtherHoster, false))
                add(Triple(stringResource(if (isError) R.string.player_back_to_overview else R.string.player_continue_watching), actions.onClose, false))
            }
            buttons.forEachIndexed { index, (label, onClick, primary) ->
                TvActionButton(
                    text = label,
                    onClick = onClick,
                    isPrimary = primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstButton) else Modifier)
                )
            }
        }
    }
}
