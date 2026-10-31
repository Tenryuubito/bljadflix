package com.shadow.firetv.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.Movie
import com.shadow.firetv.core.ui.components.CoverImage
import com.shadow.firetv.core.ui.components.LinearProgress
import com.shadow.firetv.core.ui.components.LoadingIndicator
import com.shadow.firetv.core.ui.components.TvActionButton
import com.shadow.firetv.core.ui.components.TvTextField
import com.shadow.firetv.core.ui.theme.BljadflixColors
import com.shadow.firetv.core.ui.theme.ScreenBackground
import kotlinx.coroutines.flow.distinctUntilChanged

const val GRID_COLUMNS = 5
private const val LOAD_MORE_THRESHOLD = GRID_COLUMNS * 3

data class MainActions(
    val onSidebarSelect: (Int) -> Unit,
    val onSearchChange: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onMovieClick: (Movie) -> Unit,
    val onContinueClick: (ContinueWatchingItem) -> Unit,
    val onContinueOptions: (ContinueWatchingItem) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit
)

@Composable
fun MainScreen(state: MainUiState, isSplashVisible: Boolean, actions: MainActions) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var focusResultsPending by remember { mutableStateOf(false) }
    val selectedItemFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val hasSearchResults = state.mode == MainMode.SEARCH && !state.isLoading && state.movies.isNotEmpty() &&
        state.currentQuery == searchText.trim()
    val exitContentLeft: () -> Boolean = {
        runCatching {
            if (state.mode == MainMode.SEARCH) searchFocus.requestFocus() else selectedItemFocus.requestFocus()
        }.isSuccess
    }
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val gridKey = when (state.mode) {
        MainMode.GENRE -> "genre:${state.selectedSource.id}:${state.currentGenre}"
        MainMode.SEARCH -> "search:${state.currentQuery}"
        else -> state.mode.name
    }
    val gridState = gridStates.getOrPut(gridKey) { LazyGridState() }

    LaunchedEffect(isSplashVisible) {
        if (!isSplashVisible) runCatching { selectedItemFocus.requestFocus() }
    }

    LaunchedEffect(focusResultsPending, state.isLoading, state.movies, state.mode) {
        if (!focusResultsPending || state.mode != MainMode.SEARCH || state.isLoading) return@LaunchedEffect
        focusResultsPending = false
        if (state.movies.isNotEmpty()) {
            gridState.scrollToItem(0)
            runCatching { firstResultFocus.requestFocus() }
        }
    }

    ScreenBackground {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                state = state,
                searchText = searchText,
                selectedItemFocus = selectedItemFocus,
                searchFocus = searchFocus,
                onSearchDown = {
                    hasSearchResults && runCatching { firstResultFocus.requestFocus() }.isSuccess
                },
                onSearchChange = {
                    searchText = it
                    actions.onSearchChange(it.trim())
                },
                onSearchSubmit = {
                    if (searchText.isNotBlank()) {
                        focusResultsPending = true
                        actions.onSearchChange(searchText.trim())
                    }
                },
                onSelect = { index ->
                    if (searchText.isNotEmpty()) {
                        searchText = ""
                        actions.onSearchChange("")
                    }
                    actions.onSidebarSelect(index)
                },
                onOpenSettings = actions.onOpenSettings
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, end = 48.dp, top = 28.dp)
            ) {
                ContentHeader(state)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxSize()) {
                    MainContent(state, gridState, firstResultFocus, exitContentLeft, actions)
                }
            }
        }
    }
}

@Composable
private fun ContentHeader(state: MainUiState) {
    val title = when (state.mode) {
        MainMode.CONTINUE_WATCHING -> stringResource(R.string.main_title_continue)
        MainMode.WATCHLIST -> stringResource(R.string.main_title_watchlist)
        MainMode.SEARCH -> stringResource(R.string.main_title_search, state.currentQuery)
        MainMode.GENRE -> if (state.currentGenre.equals("Alle", ignoreCase = true)) {
            stringResource(R.string.main_title_all)
        } else {
            state.currentGenre
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = BljadflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(16.dp))
        Text(
            state.selectedSource.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = BljadflixColors.TextMuted,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    if (state.mode == MainMode.CONTINUE_WATCHING && state.continueWatchingItems.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.continue_watching_hint), style = MaterialTheme.typography.bodyMedium, color = BljadflixColors.TextMuted)
    }
}

@Composable
private fun BoxScope.MainContent(
    state: MainUiState,
    gridState: LazyGridState,
    firstResultFocus: FocusRequester,
    onExitLeft: () -> Boolean,
    actions: MainActions
) {
    val isContentEmpty = state.movies.isEmpty() && state.continueWatchingItems.isEmpty()
    when {
        state.errorMessage != null && isContentEmpty -> EmptyState(state.errorMessage, state.canRetry, actions.onRetry)
        state.mode == MainMode.CONTINUE_WATCHING -> {
            if (state.isLoading && isContentEmpty) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            } else {
                ContinueWatchingList(state.continueWatchingItems, onExitLeft, actions)
            }
        }
        state.isLoading && state.movies.isEmpty() -> SkeletonGrid()
        else -> MovieGrid(state, gridState, firstResultFocus, onExitLeft, actions)
    }
}

@Composable
private fun BoxScope.EmptyState(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.titleLarge, color = BljadflixColors.TextSecondary)
        if (canRetry) {
            Spacer(Modifier.height(20.dp))
            TvActionButton(text = stringResource(R.string.btn_retry), onClick = onRetry, isPrimary = true)
        }
    }
}

@Composable
private fun Sidebar(
    state: MainUiState,
    searchText: String,
    selectedItemFocus: FocusRequester,
    searchFocus: FocusRequester,
    onSearchDown: () -> Boolean,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF00A0A10), Color(0xCC0A0A10))))
            .padding(start = 28.dp, end = 20.dp, top = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = BljadflixColors.TextSecondary,
                    focusedContainerColor = BljadflixColors.TextPrimary,
                    focusedContentColor = BljadflixColors.Surface
                )
            ) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.main_settings), modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        TvTextField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.main_search_hint),
            imeAction = ImeAction.Search,
            leadingIcon = painterResource(R.drawable.ic_search),
            onImeAction = onSearchSubmit,
            modifier = Modifier
                .focusRequester(searchFocus)
                .onPreviewKeyEvent { it.isKeyDown(Key.DirectionDown) && onSearchDown() }
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .focusRestorerCompat(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(state.sidebarItems, key = { _, item -> item }) { index, item ->
                val selected = index == state.selectedSidebarIndex
                val currentSelected by rememberUpdatedState(state.selectedSidebarIndex)
                SidebarItem(
                    label = item,
                    selected = selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .then(if (selected || (state.selectedSidebarIndex == -1 && index == 0)) Modifier.focusRequester(selectedItemFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused && index != currentSelected) onSelect(index) }
                )
                if (index == BASE_SECTION_COUNT - 1 && state.sidebarItems.size > BASE_SECTION_COUNT) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.main_genres_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = BljadflixColors.TextMuted,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

private const val BASE_SECTION_COUNT = 3

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.focusRestorerCompat(): Modifier = this.focusRestorer()

@Composable
private fun SidebarItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SelectableSurfaceDefaults.shape(shape),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
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
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MovieGrid(
    state: MainUiState,
    gridState: LazyGridState,
    firstResultFocus: FocusRequester,
    onExitLeft: () -> Boolean,
    actions: MainActions
) {
    val onLoadMore by rememberUpdatedState(actions.onLoadMore)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - LOAD_MORE_THRESHOLD) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        itemsIndexed(state.movies, key = { _, movie -> "${movie.source}:${movie.id}" }) { index, movie ->
            PosterCard(
                movie = movie,
                onClick = { actions.onMovieClick(movie) },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                    .onPreviewKeyEvent {
                        index % GRID_COLUMNS == 0 && it.isKeyDown(Key.DirectionLeft) && onExitLeft()
                    }
            )
        }
        if (state.isEndOfList && state.movies.isNotEmpty()) {
            item(span = { GridItemSpan(GRID_COLUMNS) }) {
                Text(
                    stringResource(R.string.main_end_of_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BljadflixColors.TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else if (state.isLoading && state.movies.isNotEmpty()) {
            item(span = { GridItemSpan(GRID_COLUMNS) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(size = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun PosterCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StandardCardContainer(
        modifier = modifier,
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                scale = CardDefaults.scale(focusedScale = 1.08f),
                border = CardDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, BljadflixColors.TextPrimary), shape = RoundedCornerShape(12.dp))
                ),
                colors = CardDefaults.colors(containerColor = BljadflixColors.SurfaceElevated)
            ) {
                CoverImage(
                    coverUrl = movie.cover_url,
                    source = movie.source,
                    contentId = movie.id,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        title = {
            Text(
                movie.title,
                style = MaterialTheme.typography.titleSmall,
                color = BljadflixColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    )
}

@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        userScrollEnabled = false,
        contentPadding = PaddingValues(top = 8.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        items(GRID_COLUMNS * 3) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BljadflixColors.SurfaceElevated)
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(BljadflixColors.SurfaceElevated)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContinueWatchingList(items: List<ContinueWatchingItem>, onExitLeft: () -> Boolean, actions: MainActions) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(items, key = { _, item -> "${item.source}:${item.id}:${item.episode_id}" }) { _, item ->
            ContinueWatchingCard(
                item = item,
                onClick = { actions.onContinueClick(item) },
                onOptions = { actions.onContinueOptions(item) },
                modifier = Modifier.onPreviewKeyEvent { it.isKeyDown(Key.DirectionLeft) && onExitLeft() }
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val progress = if (item.duration_seconds > 0) item.progress_seconds / item.duration_seconds else 0f
    Surface(
        onClick = onClick,
        onLongClick = onOptions,
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent {
                if (it.isKeyDown(Key.Menu)) {
                    onOptions()
                    true
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BljadflixColors.Surface.copy(alpha = 0.85f),
            contentColor = BljadflixColors.TextPrimary,
            focusedContainerColor = BljadflixColors.SurfaceFocused,
            focusedContentColor = BljadflixColors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, BljadflixColors.Border), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, BljadflixColors.TextPrimary), shape = shape)
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(84.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                CoverImage(item.cover_url, item.source, item.id, item.title, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val episodeTitle = item.episode_title?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Text(
                    stringResource(R.string.main_continue_episode, item.season_number, item.episode_number, episodeTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BljadflixColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                LinearProgress(progress = progress, modifier = Modifier.fillMaxWidth(0.6f), height = 5.dp)
            }
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isKeyDown(target: Key): Boolean =
    key == target && type == KeyEventType.KeyDown
