package com.shadow.firetv

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.shadow.firetv.feature.details.SeriesDetailEvent
import com.shadow.firetv.feature.details.SeriesDetailUiState
import com.shadow.firetv.feature.details.SeriesDetailViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class SeriesDetailActivity : AppCompatActivity() {
    private lateinit var spinnerSeasons: Spinner
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnWatchlist: Button
    private lateinit var btnContinueWatching: Button
    private lateinit var btnReindex: Button
    private lateinit var progressReindex: ProgressBar
    private lateinit var tvEpisodesMessage: TextView
    private var hasResumedOnce = false
    private lateinit var epAdapter: EpisodeAdapter
    private var shownSeasonItems: List<String> = emptyList()

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
        val genre = intent.getStringExtra("GENRE")
        val coverUrl = intent.getStringExtra("COVER_URL")
        val source = intent.getStringExtra("SOURCE")

        findViewById<TextView>(R.id.tv_title).text = title
        findViewById<TextView>(R.id.tv_description).text = description

        CoverLoader.load(findViewById(R.id.iv_cover), coverUrl, source, contentId)

        spinnerSeasons = findViewById(R.id.spinner_seasons)
        rvEpisodes = findViewById(R.id.rv_episodes)
        progressBar = findViewById(R.id.progress_bar)
        btnWatchlist = findViewById(R.id.btn_watchlist_toggle)
        btnContinueWatching = findViewById(R.id.btn_continue_watching)
        btnReindex = findViewById(R.id.btn_reindex)
        progressReindex = findViewById(R.id.progress_reindex)
        tvEpisodesMessage = findViewById(R.id.tv_episodes_message)

        setupButtonFocusAnimations()
        applyThemeColors()

        rvEpisodes.layoutManager = LinearLayoutManager(this)
        rvEpisodes.itemAnimator = null
        epAdapter = EpisodeAdapter { episode ->
            startPlayer(episode.id, contentId)
        }
        rvEpisodes.adapter = epAdapter

        spinnerSeasons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val seasons = viewModel.uiState.value.seasonsList
                if (position in seasons.indices && seasons[position] != viewModel.uiState.value.selectedSeason) {
                    viewModel.selectSeason(seasons[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnWatchlist.setOnClickListener {
            viewModel.toggleWatchlist()
        }

        btnReindex.setOnClickListener {
            viewModel.reindexSeries()
        }

        spinnerSeasons.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val index = epAdapter.highlightedIndex()
            if (index == -1) return@setOnKeyListener false
            rvEpisodes.layoutManager?.findViewByPosition(index)?.requestFocus() ?: false
        }

        viewModel.initData(contentId, title, description, genre, coverUrl, source)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) viewModel.refreshEpisodes()
        hasResumedOnce = true
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

            val allEpisodes = state.seasonsList.flatMap { state.seasonsData[it] ?: emptyList() }
            val hasWatched = allEpisodes.any { it.last_watched_at > 0 || it.progress_seconds > 0 }
            val isAllCompleted = allEpisodes.isNotEmpty() && allEpisodes.all { it.is_completed }

            btnContinueWatching.isFocusable = true
            btnContinueWatching.isClickable = true
            btnContinueWatching.alpha = 1.0f

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
        } else {
            btnContinueWatching.visibility = View.GONE
        }

        epAdapter.updateEpisodes(state.currentEpisodes, state.continueEpisode?.id)

        if (state.seasonsList.isNotEmpty()) {
            val spinnerItems = state.seasonsList.map { season ->
                val eps = state.seasonsData[season] ?: emptyList()
                if (eps.isNotEmpty() && eps.all { it.is_completed }) {
                    getString(R.string.season_label_completed, season)
                } else {
                    getString(R.string.season_label, season)
                }
            }

            if (spinnerItems != shownSeasonItems) {
                shownSeasonItems = spinnerItems
                val arrayAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, spinnerItems)
                arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                spinnerSeasons.adapter = arrayAdapter
            }

            val selectedIndex = state.seasonsList.indexOf(state.selectedSeason)
            if (selectedIndex != -1 && spinnerSeasons.selectedItemPosition != selectedIndex) {
                spinnerSeasons.setSelection(selectedIndex, false)
            }
        }
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
