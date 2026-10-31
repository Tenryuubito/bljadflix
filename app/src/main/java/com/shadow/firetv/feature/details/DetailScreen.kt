package com.shadow.firetv.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.ui.components.CoverImage
import com.shadow.firetv.core.ui.components.LinearProgress
import com.shadow.firetv.core.ui.components.LoadingIndicator
import com.shadow.firetv.core.ui.components.TvActionButton
import com.shadow.firetv.core.ui.theme.BljadflixColors
import com.shadow.firetv.core.ui.theme.ScreenBackground
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class DetailActions(
    val onPlayEpisode: (Episode) -> Unit,
    val onToggleWatchlist: () -> Unit,
    val onReindex: () -> Unit,
    val onSelectSeason: (String) -> Unit
)

private const val HEADER_ITEM_COUNT = 1

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailScreen(state: SeriesDetailUiState, actions: DetailActions) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val continueFocus = remember { FocusRequester() }
    val watchlistFocus = remember { FocusRequester() }
    val selectedTabFocus = remember { FocusRequester() }
    val targetEpisodeFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }

    val allEpisodes = remember(state.seasonsData, state.seasonsList) {
        state.seasonsList.flatMap { state.seasonsData[it] ?: emptyList() }
    }
    val continueEpisode = state.continueEpisode
    val targetIndex = state.currentEpisodes.indexOfFirst { it.id == continueEpisode?.id }.takeIf { it != -1 } ?: 0

    LaunchedEffect(Unit) { runCatching { watchlistFocus.requestFocus() } }
    LaunchedEffect(continueEpisode != null) {
        if (continueEpisode != null && !initialFocusDone) {
            initialFocusDone = true
            runCatching { continueFocus.requestFocus() }
        }
    }

    val focusEpisodes: () -> Boolean = {
        if (state.currentEpisodes.isEmpty()) {
            false
        } else {
            scope.launch {
                listState.scrollToItem(HEADER_ITEM_COUNT + targetIndex, -200)
                runCatching { targetEpisodeFocus.requestFocus() }
            }
            true
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground {
            CoverImage(
                coverUrl = state.coverUrl,
                source = state.source,
                contentId = state.contentId,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.22f
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xF20A0A10), Color(0xCC0A0A10), Color(0x990A0A10))
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, top = 32.dp)
            ) {
                Box(
                    Modifier
                        .width(250.dp)
                        .height(375.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, BljadflixColors.Border), RoundedCornerShape(16.dp))
                ) {
                    CoverImage(state.coverUrl, state.source, state.contentId, state.title, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(40.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        DetailHeader(
                            state = state,
                            allEpisodes = allEpisodes,
                            continueFocus = continueFocus,
                            watchlistFocus = watchlistFocus,
                            selectedTabFocus = selectedTabFocus,
                            onTabsDown = focusEpisodes,
                            actions = actions
                        )
                    }
                    itemsIndexed(state.currentEpisodes, key = { _, ep -> ep.id }) { index, episode ->
                        EpisodeRow(
                            episode = episode,
                            isContinue = episode.id == continueEpisode?.id,
                            onClick = { actions.onPlayEpisode(episode) },
                            modifier = if (index == targetIndex) Modifier.focusRequester(targetEpisodeFocus) else Modifier
                        )
                    }
                }
            }
        }
        if (state.isLoading) {
            LoadingIndicator(Modifier.align(Alignment.Center), size = 56.dp)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DetailHeader(
    state: SeriesDetailUiState,
    allEpisodes: List<Episode>,
    continueFocus: FocusRequester,
    watchlistFocus: FocusRequester,
    selectedTabFocus: FocusRequester,
    onTabsDown: () -> Boolean,
    actions: DetailActions
) {
    Column {
        Text(
            state.title,
            style = MaterialTheme.typography.displaySmall,
            color = BljadflixColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = buildList {
            if (state.seasonsList.isNotEmpty()) {
                add(pluralStringResource(R.plurals.detail_season_count, state.seasonsList.size, state.seasonsList.size))
                add(pluralStringResource(R.plurals.detail_episode_count, allEpisodes.size, allEpisodes.size))
            }
            state.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = BljadflixColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.description.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                state.description,
                style = MaterialTheme.typography.bodyLarge,
                color = BljadflixColors.TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(24.dp))

        val continueEpisode = state.continueEpisode
        if (continueEpisode != null) {
            val hasWatched = allEpisodes.any { it.last_watched_at > 0 || it.progress_seconds > 0 }
            val isAllCompleted = allEpisodes.isNotEmpty() && allEpisodes.all { it.is_completed }
            val season = state.continueSeasonNumber
            val episodeNumber = continueEpisode.episode_number.toString()
            val label = when {
                isAllCompleted && continueEpisode == allEpisodes.firstOrNull() -> stringResource(R.string.btn_watch_restart, season, episodeNumber)
                !hasWatched -> stringResource(R.string.btn_watch_start, season, episodeNumber)
                else -> stringResource(R.string.btn_watch_continue, season, episodeNumber)
            }
            TvActionButton(
                text = label,
                onClick = { actions.onPlayEpisode(continueEpisode) },
                isPrimary = true,
                icon = painterResource(R.drawable.ic_play),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(continueFocus)
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvActionButton(
                text = stringResource(if (state.isWatchlisted) R.string.btn_watchlist_added else R.string.btn_watchlist_add),
                onClick = actions.onToggleWatchlist,
                icon = painterResource(if (state.isWatchlisted) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                iconTint = if (state.isWatchlisted) Color.Unspecified else null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(watchlistFocus)
            )
            TvActionButton(
                text = stringResource(if (state.isReindexing) R.string.btn_reindex_running else R.string.btn_reindex),
                onClick = actions.onReindex,
                enabled = !state.isReindexing,
                icon = painterResource(R.drawable.ic_sync),
                modifier = Modifier.weight(1f),
                trailing = if (state.isReindexing) {
                    { LoadingIndicator(size = 18.dp, color = BljadflixColors.TextPrimary) }
                } else {
                    null
                }
            )
        }

        if (state.seasonsList.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Text(
                stringResource(R.string.detail_seasons_header),
                style = MaterialTheme.typography.labelLarge,
                color = BljadflixColors.TextMuted
            )
            Spacer(Modifier.height(10.dp))
            SeasonTabs(state, selectedTabFocus, onTabsDown, actions.onSelectSeason)
            Spacer(Modifier.height(10.dp))
            val watched = state.currentEpisodes.count { it.is_completed }
            Text(
                stringResource(R.string.season_progress, watched, state.currentEpisodes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = BljadflixColors.TextSecondary
            )
        }

        val message = state.errorMessage
        if (!state.isLoading && message != null) {
            Spacer(Modifier.height(20.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = BljadflixColors.TextMuted)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeasonTabs(
    state: SeriesDetailUiState,
    selectedTabFocus: FocusRequester,
    onTabsDown: () -> Boolean,
    onSelectSeason: (String) -> Unit
) {
    val selectedIndex = state.seasonsList.indexOf(state.selectedSeason).coerceAtLeast(0)
    val primary = MaterialTheme.colorScheme.primary
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier
            .focusProperties { enter = { selectedTabFocus } }
            .onPreviewKeyEvent { it.key == Key.DirectionDown && it.type == KeyEventType.KeyDown && onTabsDown() },
        separator = { Spacer(Modifier.width(10.dp)) },
        indicator = { tabPositions, doesTabRowHaveFocus ->
            tabPositions.getOrNull(selectedIndex)?.let { position ->
                TabRowDefaults.PillIndicator(
                    currentTabPosition = position,
                    doesTabRowHaveFocus = doesTabRowHaveFocus,
                    activeColor = primary,
                    inactiveColor = primary.copy(alpha = 0.55f)
                )
            }
        }
    ) {
        state.seasonsList.forEachIndexed { index, season ->
            val episodes = state.seasonsData[season] ?: emptyList()
            val isCompleted = episodes.isNotEmpty() && episodes.all { it.is_completed }
            Tab(
                selected = index == selectedIndex,
                onFocus = { if (season != state.selectedSeason) onSelectSeason(season) },
                onClick = { onTabsDown() },
                modifier = if (index == selectedIndex) Modifier.focusRequester(selectedTabFocus) else Modifier,
                colors = TabDefaults.pillIndicatorTabColors(
                    contentColor = BljadflixColors.TextSecondary,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.season_label, season), style = MaterialTheme.typography.titleSmall)
                    if (isCompleted) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, isContinue: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BljadflixColors.Surface.copy(alpha = 0.7f),
            contentColor = BljadflixColors.TextPrimary,
            focusedContainerColor = BljadflixColors.SurfaceFocused,
            focusedContentColor = BljadflixColors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(if (isContinue) 2.dp else 1.dp, if (isContinue) MaterialTheme.colorScheme.primary else BljadflixColors.Border),
                shape = shape
            ),
            focusedBorder = Border(BorderStroke(3.dp, BljadflixColors.TextPrimary), shape = shape)
        )
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(BljadflixColors.SurfaceElevated, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.episode_number_label, episode.episode_number), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (episode.is_completed) BljadflixColors.TextMuted else BljadflixColors.TextPrimary
                )
                if (!episode.is_completed && episode.progress_seconds > 0) {
                    Spacer(Modifier.height(8.dp))
                    val progress = if (episode.duration_seconds > 0) episode.progress_seconds / episode.duration_seconds else 0.5f
                    LinearProgress(progress = progress, modifier = Modifier.fillMaxWidth(0.7f))
                    if (episode.duration_seconds > episode.progress_seconds) {
                        Spacer(Modifier.height(6.dp))
                        val minutes = ceil((episode.duration_seconds - episode.progress_seconds) / 60f).toInt().coerceAtLeast(1)
                        Text(
                            context.getString(R.string.episode_remaining, minutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BljadflixColors.TextMuted
                        )
                    }
                }
            }
            if (isContinue) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        stringResource(R.string.episode_continue_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (episode.is_completed) {
                Spacer(Modifier.width(12.dp))
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            }
        }
    }
}
