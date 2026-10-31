package com.shadow.firetv

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shadow.firetv.core.image.CoverLoader
import com.shadow.firetv.core.model.Episode
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.core.ui.TabRowRecyclerView
import com.shadow.firetv.feature.details.SeriesDetailEvent
import com.shadow.firetv.feature.details.SeriesDetailUiState
import com.shadow.firetv.feature.details.SeriesDetailViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class SeriesDetailActivity : AppCompatActivity() {
    private lateinit var rvSeasons: TabRowRecyclerView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnWatchlist: Button
    private lateinit var btnContinueWatching: Button
    private lateinit var btnReindex: Button
    private lateinit var progressReindex: ProgressBar
    private lateinit var tvMeta: TextView
    private lateinit var tvSeasonsHeader: TextView
    private lateinit var tvSeasonProgress: TextView
    private lateinit var tvEpisodesMessage: TextView
    private var hasResumedOnce = false
    private var hasShownContinue = false
    private var genre: String? = null
    private lateinit var epAdapter: EpisodeAdapter
    private lateinit var seasonAdapter: SeasonChipAdapter

    private val viewModel: SeriesDetailViewModel by viewModels { SeriesDetailViewModel.Factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_series_detail)

        val contentId = intent.getIntExtra("CONTENT_ID", -1)
        if (contentId == -1) {
            finish()
            return
        }

        val title = intent.getStringExtra("TITLE") ?: ""
        val description = intent.getStringExtra("DESCRIPTION") ?: ""
        genre = intent.getStringExtra("GENRE")
        val coverUrl = intent.getStringExtra("COVER_URL")
        val source = intent.getStringExtra("SOURCE")

        findViewById<TextView>(R.id.tv_title).text = title
        findViewById<TextView>(R.id.tv_description).text = description

        CoverLoader.load(findViewById(R.id.iv_cover), coverUrl, source, contentId)
        CoverLoader.load(findViewById(R.id.iv_backdrop), coverUrl, source, contentId)

        rvSeasons = findViewById(R.id.rv_seasons)
        rvEpisodes = findViewById(R.id.rv_episodes)
        progressBar = findViewById(R.id.progress_bar)
        btnWatchlist = findViewById(R.id.btn_watchlist_toggle)
        btnContinueWatching = findViewById(R.id.btn_continue_watching)
        btnReindex = findViewById(R.id.btn_reindex)
        progressReindex = findViewById(R.id.progress_reindex)
        tvMeta = findViewById(R.id.tv_meta)
        tvSeasonsHeader = findViewById(R.id.tv_seasons_header)
        tvSeasonProgress = findViewById(R.id.tv_season_progress)
        tvEpisodesMessage = findViewById(R.id.tv_episodes_message)

        tvMeta.text = genre.orEmpty()
        setupButtonFocusAnimations()
        applyThemeColors()

        rvEpisodes.layoutManager = LinearLayoutManager(this)
        rvEpisodes.itemAnimator = null
        epAdapter = EpisodeAdapter { episode ->
            startPlayer(episode.id, contentId)
        }
        rvEpisodes.adapter = epAdapter

        rvSeasons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSeasons.itemAnimator = null
        seasonAdapter = SeasonChipAdapter(
            onSeasonFocused = { season ->
                if (season != viewModel.uiState.value.selectedSeason) viewModel.selectSeason(season)
            },
            onSeasonClicked = { season ->
                if (season != viewModel.uiState.value.selectedSeason) viewModel.selectSeason(season)
                rvEpisodes.post { focusEpisodes() }
            },
            onMoveDown = { focusEpisodes() }
        )
        rvSeasons.adapter = seasonAdapter

        btnWatchlist.setOnClickListener {
            viewModel.toggleWatchlist()
        }

        btnReindex.setOnClickListener {
            viewModel.reindexSeries()
        }

        viewModel.initData(contentId, title, description, genre, coverUrl, source)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) viewModel.refreshEpisodes()
        hasResumedOnce = true
    }

    private fun focusEpisodes(): Boolean {
        if (epAdapter.itemCount == 0) return false
        val index = epAdapter.highlightedIndex().takeIf { it != -1 } ?: 0
        return rvEpisodes.layoutManager?.findViewByPosition(index)?.requestFocus() ?: false
    }

    private fun setupButtonFocusAnimations() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(6f).setDuration(120).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120).start()
            }
        }
        btnWatchlist.onFocusChangeListener = focusListener
        btnContinueWatching.onFocusChangeListener = focusListener
        btnReindex.onFocusChangeListener = focusListener
    }

    private fun applyThemeColors() {
        val themeManager = ThemeManager.getInstance(this)
        val onPrimary = ColorStateList.valueOf(themeManager.getOnPrimaryColorInt())
        btnContinueWatching.backgroundTintList = ColorStateList.valueOf(themeManager.getPrimaryColorInt())
        btnContinueWatching.setTextColor(onPrimary)
        btnContinueWatching.compoundDrawableTintList = onPrimary
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SeriesDetailEvent.ShowToast -> Toast.makeText(this@SeriesDetailActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: SeriesDetailUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        val episodesMessage = state.errorMessage
        tvEpisodesMessage.visibility = if (!state.isLoading && episodesMessage != null) View.VISIBLE else View.GONE
        tvEpisodesMessage.text = episodesMessage

        val allEpisodes = state.seasonsList.flatMap { state.seasonsData[it] ?: emptyList() }
        renderMeta(state, allEpisodes)

        val themeManager = ThemeManager.getInstance(this)
        if (state.isWatchlisted) {
            btnWatchlist.text = getString(R.string.btn_watchlist_added)
            btnWatchlist.backgroundTintList = ColorStateList.valueOf(themeManager.getPrimaryColorInt())
            btnWatchlist.setTextColor(themeManager.getOnPrimaryColorInt())
            btnWatchlist.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_star_filled, 0, 0, 0)
        } else {
            btnWatchlist.text = getString(R.string.btn_watchlist_add)
            btnWatchlist.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.button_secondary))
            btnWatchlist.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            btnWatchlist.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_star_outline, 0, 0, 0)
        }

        btnReindex.isEnabled = !state.isReindexing
        btnReindex.text = getString(if (state.isReindexing) R.string.btn_reindex_running else R.string.btn_reindex)
        progressReindex.visibility = if (state.isReindexing) View.VISIBLE else View.GONE

        val continueEp = state.continueEpisode
        if (continueEp != null) {
            btnContinueWatching.visibility = View.VISIBLE
            val season = state.continueSeasonNumber
            val ep = continueEp.episode_number.toString()

            val hasWatched = allEpisodes.any { it.last_watched_at > 0 || it.progress_seconds > 0 }
            val isAllCompleted = allEpisodes.isNotEmpty() && allEpisodes.all { it.is_completed }

            if (isAllCompleted && continueEp == allEpisodes.firstOrNull()) {
                btnContinueWatching.text = getString(R.string.btn_watch_restart, season, ep)
            } else if (!hasWatched) {
                btnContinueWatching.text = getString(R.string.btn_watch_start, season, ep)
            } else {
                btnContinueWatching.text = getString(R.string.btn_watch_continue, season, ep)
            }

            btnContinueWatching.setOnClickListener {
                startPlayer(continueEp.id, state.contentId)
            }
            if (!hasShownContinue) {
                hasShownContinue = true
                if (btnWatchlist.isFocused || currentFocus == null) btnContinueWatching.requestFocus()
            }
        } else {
            btnContinueWatching.visibility = View.GONE
        }

        epAdapter.updateEpisodes(state.currentEpisodes, state.continueEpisode?.id)
        renderSeasons(state)
    }

    private fun renderMeta(state: SeriesDetailUiState, allEpisodes: List<Episode>) {
        val parts = mutableListOf<String>()
        if (state.seasonsList.isNotEmpty()) {
            parts.add(resources.getQuantityString(R.plurals.detail_season_count, state.seasonsList.size, state.seasonsList.size))
            parts.add(resources.getQuantityString(R.plurals.detail_episode_count, allEpisodes.size, allEpisodes.size))
        }
        genre?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tvMeta.text = parts.joinToString(" · ")
        tvMeta.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderSeasons(state: SeriesDetailUiState) {
        val hasSeasons = state.seasonsList.isNotEmpty()
        val visibility = if (hasSeasons) View.VISIBLE else View.GONE
        tvSeasonsHeader.visibility = visibility
        rvSeasons.visibility = visibility
        tvSeasonProgress.visibility = visibility
        if (!hasSeasons) return

        val chips = state.seasonsList.map { season ->
            val eps = state.seasonsData[season] ?: emptyList()
            SeasonChip(season, getString(R.string.season_label, season), eps.isNotEmpty() && eps.all { it.is_completed })
        }
        val isFirstRender = seasonAdapter.itemCount == 0
        val selectedIndex = state.seasonsList.indexOf(state.selectedSeason)
        rvSeasons.selectedPosition = selectedIndex
        seasonAdapter.submit(chips, state.selectedSeason)
        if (isFirstRender && selectedIndex > 0) rvSeasons.scrollToPosition(selectedIndex)

        val watched = state.currentEpisodes.count { it.is_completed }
        tvSeasonProgress.text = getString(R.string.season_progress, watched, state.currentEpisodes.size)
    }

    private fun startPlayer(episodeId: Int, contentId: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EPISODE_ID", episodeId)
            putExtra("CONTENT_ID", contentId)
            putExtra("SOURCE", viewModel.uiState.value.source)
            putExtra("TITLE", viewModel.uiState.value.title)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

data class SeasonChip(val key: String, val label: String, val isCompleted: Boolean)

class SeasonChipAdapter(
    private val onSeasonFocused: (String) -> Unit,
    private val onSeasonClicked: (String) -> Unit,
    private val onMoveDown: () -> Boolean
) : RecyclerView.Adapter<SeasonChipAdapter.ViewHolder>() {

    private var items: List<SeasonChip> = emptyList()
    private var selectedKey: String = ""
    private var recyclerView: RecyclerView? = null
    private val focusInterpolator = FastOutSlowInInterpolator()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun safeNotify(action: () -> Unit) {
        val rv = recyclerView
        if (rv != null && rv.isComputingLayout) rv.post { action() } else action()
    }

    fun submit(newItems: List<SeasonChip>, newSelectedKey: String) {
        val oldIndex = items.indexOfFirst { it.key == selectedKey }
        val oldSize = items.size
        val itemsChanged = newItems != items
        items = newItems
        selectedKey = newSelectedKey
        val newIndex = items.indexOfFirst { it.key == selectedKey }
        safeNotify {
            when {
                itemsChanged && oldSize == items.size -> notifyItemRangeChanged(0, items.size)
                itemsChanged -> notifyDataSetChanged()
                oldIndex != newIndex -> {
                    if (oldIndex != -1) notifyItemChanged(oldIndex)
                    if (newIndex != -1) notifyItemChanged(newIndex)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_season_chip, parent, false) as TextView
        val themeManager = ThemeManager.getInstance(parent.context)
        view.background = chipBackground(parent.context, themeManager.getPrimaryColorInt())
        view.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(
                    themeManager.getOnPrimaryColorInt(),
                    ContextCompat.getColor(parent.context, R.color.text_primary),
                    ContextCompat.getColor(parent.context, R.color.text_secondary)
                )
            )
        )
        val holder = ViewHolder(view)
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 6f else 0f)
                .setInterpolator(focusInterpolator).setDuration(120).start()
            val pos = holder.bindingAdapterPosition
            if (hasFocus && pos in items.indices) onSeasonFocused(items[pos].key)
        }
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) onSeasonClicked(items[pos].key)
        }
        view.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN && onMoveDown()
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chip = items[position]
        holder.label.text = chip.label
        holder.label.isSelected = chip.key == selectedKey
        holder.label.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (chip.isCompleted) R.drawable.ic_check else 0, 0)
    }

    override fun getItemCount() = items.size

    private fun chipBackground(context: Context, primary: Int): StateListDrawable {
        val density = context.resources.displayMetrics.density
        fun shape(fill: Int, stroke: Int, strokeDp: Float) = GradientDrawable().apply {
            cornerRadius = CHIP_RADIUS_DP * density
            setColor(fill)
            setStroke((strokeDp * density).roundToInt(), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused, android.R.attr.state_selected), shape(primary, FOCUS_STROKE, 3f))
            addState(intArrayOf(android.R.attr.state_focused), shape(FOCUS_FILL, FOCUS_STROKE, 3f))
            addState(intArrayOf(android.R.attr.state_selected), shape(primary, primary, 1.5f))
            addState(intArrayOf(), shape(DEFAULT_FILL, DEFAULT_STROKE, 1.5f))
        }
    }

    class ViewHolder(val label: TextView) : RecyclerView.ViewHolder(label)

    companion object {
        private const val CHIP_RADIUS_DP = 24f
        private const val FOCUS_FILL = 0xFF2A2A40.toInt()
        private const val FOCUS_STROKE = 0xFFE0E0E0.toInt()
        private const val DEFAULT_FILL = 0xFF181824.toInt()
        private const val DEFAULT_STROKE = 0xFF2D2D42.toInt()
    }
}

class EpisodeAdapter(
    private val onEpisodeClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<Episode>() {
        override fun areItemsTheSame(oldItem: Episode, newItem: Episode): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Episode, newItem: Episode): Boolean = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)
    private var highlightedEpisodeId: Int? = null

    fun updateEpisodes(newEpisodes: List<Episode>, continueEpisodeId: Int?) {
        val highlightChanged = highlightedEpisodeId != continueEpisodeId
        val oldIndex = if (highlightChanged) highlightedIndex() else -1
        highlightedEpisodeId = continueEpisodeId
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        differ.submitList(newEpisodes) {
            if (highlightChanged) highlightedIndex().takeIf { it != -1 }?.let { notifyItemChanged(it) }
        }
    }

    fun highlightedIndex(): Int = differ.currentList.indexOfFirst { it.id == highlightedEpisodeId }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        )
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.02f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 6f else 0f).setDuration(120).start()
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in differ.currentList.indices) onEpisodeClick(differ.currentList[pos])
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ep = differ.currentList[position]
        holder.num.text = String.format(Locale.getDefault(), "EP %d", ep.episode_number)
        holder.title.text = ep.title
        holder.continueBadge.visibility = if (ep.id == highlightedEpisodeId) View.VISIBLE else View.GONE
        holder.remaining.visibility = View.GONE

        if (ep.is_completed) {
            holder.ivCheck.visibility = View.VISIBLE
            holder.pbProgress.visibility = View.GONE
            holder.title.alpha = 0.5f
        } else if (ep.progress_seconds > 0) {
            holder.ivCheck.visibility = View.GONE
            holder.pbProgress.visibility = View.VISIBLE
            holder.pbProgress.progress = if (ep.duration_seconds > 0) {
                (ep.progress_seconds / ep.duration_seconds * 100).roundToInt().coerceIn(1, 100)
            } else {
                PROGRESS_UNKNOWN
            }
            if (ep.duration_seconds > ep.progress_seconds) {
                val minutes = ceil((ep.duration_seconds - ep.progress_seconds) / 60f).toInt().coerceAtLeast(1)
                holder.remaining.text = holder.itemView.context.getString(R.string.episode_remaining, minutes)
                holder.remaining.visibility = View.VISIBLE
            }
            holder.title.alpha = 1.0f
        } else {
            holder.ivCheck.visibility = View.GONE
            holder.pbProgress.visibility = View.GONE
            holder.title.alpha = 1.0f
        }
    }

    override fun getItemCount() = differ.currentList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val num: TextView = view.findViewById(R.id.tv_ep_number)
        val title: TextView = view.findViewById(R.id.tv_ep_title)
        val ivCheck: ImageView = view.findViewById(R.id.iv_check)
        val pbProgress: ProgressBar = view.findViewById(R.id.pb_ep_progress)
        val remaining: TextView = view.findViewById(R.id.tv_ep_remaining)
        val continueBadge: TextView = view.findViewById(R.id.tv_ep_continue)
    }

    companion object {
        private const val PROGRESS_UNKNOWN = 50
    }
}
