package com.shadow.firetv

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.core.ui.theme.BljadflixTheme
import com.shadow.firetv.feature.details.DetailActions
import com.shadow.firetv.feature.details.DetailScreen
import com.shadow.firetv.feature.details.SeriesDetailEvent
import com.shadow.firetv.feature.details.SeriesDetailViewModel
import kotlinx.coroutines.launch

class SeriesDetailActivity : AppCompatActivity() {

    private val viewModel: SeriesDetailViewModel by viewModels { SeriesDetailViewModel.Factory() }
    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contentId = intent.getIntExtra("CONTENT_ID", -1)
        if (contentId == -1) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            viewModel.initData(
                contentId = contentId,
                title = intent.getStringExtra("TITLE") ?: "",
                description = intent.getStringExtra("DESCRIPTION") ?: "",
                genre = intent.getStringExtra("GENRE"),
                coverUrl = intent.getStringExtra("COVER_URL"),
                source = intent.getStringExtra("SOURCE")
            )
        }

        val actions = DetailActions(
            onPlayEpisode = { episode -> startPlayer(episode.id) },
            onToggleWatchlist = viewModel::toggleWatchlist,
            onReindex = viewModel::reindexSeries,
            onSelectSeason = viewModel::selectSeason
        )

        setContent {
            BljadflixTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DetailScreen(state = state, actions = actions)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SeriesDetailEvent.ShowToast -> Toast.makeText(this@SeriesDetailActivity, event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) viewModel.refreshEpisodes()
        hasResumedOnce = true
    }

    private fun startPlayer(episodeId: Int) {
        val state = viewModel.uiState.value
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EPISODE_ID", episodeId)
            putExtra("CONTENT_ID", state.contentId)
            putExtra("SOURCE", state.source)
            putExtra("TITLE", state.title)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
