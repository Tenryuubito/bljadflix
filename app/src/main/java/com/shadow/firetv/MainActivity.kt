package com.shadow.firetv

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.shadow.firetv.core.model.ContinueWatchingItem
import com.shadow.firetv.core.model.MainAppVersion
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager
import com.shadow.firetv.feature.auth.LoginActivity
import com.shadow.firetv.feature.main.MainEvent
import com.shadow.firetv.feature.main.MainMode
import com.shadow.firetv.feature.main.MainUiState
import com.shadow.firetv.feature.main.MainViewModel
import com.shadow.firetv.feature.settings.SettingsActivity
import com.shadow.firetv.feature.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var rvSidebarCategories: RecyclerView
    private lateinit var rvContentGrid: RecyclerView
    private lateinit var progressMain: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var btnRetry: Button
    private lateinit var tvContentHint: TextView
    private lateinit var etSearch: EditText

    private lateinit var gridAdapter: MovieAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter
    private val skeletonAdapter = SkeletonAdapter(MovieAdapter.SPAN_COUNT * 3)
    private lateinit var sidebarAdapter: SidebarAdapter
    private lateinit var layoutManager: GridLayoutManager

    private var isSplashAnimDone = false
    private var isIntroAudioDone = false
    private var isInitialDataLoaded = false
    private var isSplashHiding = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var splashOverlay: View
    private lateinit var splashProgressBar: ProgressBar
    private lateinit var splashStatusText: TextView
    private lateinit var splashPercentText: TextView

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory() }
    private val updateViewModel: UpdateViewModel by viewModels { UpdateViewModel.Factory() }
    private var updateDialog: AlertDialog? = null
    private var optionsDialog: AlertDialog? = null

    private val scrollPositions = mutableMapOf<String, Int>()
    private var renderedScrollKey: String? = null
    private var pendingScrollRestore: Int? = null
    private var pendingSearchFocus = false
    private var appliedTheme: AppTheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_main)

        rvSidebarCategories = findViewById(R.id.rv_sidebar_categories)
        rvContentGrid = findViewById(R.id.rv_content_grid)
        progressMain = findViewById(R.id.progress_main)
        tvEmpty = findViewById(R.id.tv_empty)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        btnRetry = findViewById(R.id.btn_retry)
        tvContentHint = findViewById(R.id.tv_content_hint)
        setupRetryButton()
        etSearch = findViewById(R.id.et_search)
        setupSettingsButton(findViewById(R.id.btn_settings))

        splashOverlay = findViewById(R.id.splash_screen_overlay)
        splashProgressBar = findViewById(R.id.splash_progress_bar)
        splashStatusText = findViewById(R.id.splash_status_text)
        splashPercentText = findViewById(R.id.splash_percent_text)
        val splashLogo = findViewById<ImageView>(R.id.splash_logo)
        val splashText = findViewById<TextView>(R.id.splash_text)

        splashLogo.scaleX = 0.5f
        splashLogo.scaleY = 0.5f
        splashLogo.alpha = 0f
        splashText.alpha = 0f

        splashLogo.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .alpha(1f)
            .setDuration(1000)
            .withEndAction {
                isSplashAnimDone = true
                checkSplashAndHide()
            }
            .start()

        splashText.animate()
            .alpha(1f)
            .setDuration(700)
            .setStartDelay(150)
            .start()

        if (ThemeManager.getInstance(this).isIntroEnabled()) {
            playIntro()
        } else {
            isIntroAudioDone = true
        }

        setupGrid()
        setupSearch()
        setupSidebar()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        val themeManager = ThemeManager.getInstance(this)
        val theme = themeManager.getSelectedTheme()
        if (theme != appliedTheme) {
            val isThemeChange = appliedTheme != null
            appliedTheme = theme
            themeManager.applyTheme(this)
            if (isThemeChange && ::sidebarAdapter.isInitialized) {
                if (rvSidebarCategories.isComputingLayout) {
                    rvSidebarCategories.post { sidebarAdapter.notifyDataSetChanged() }
                } else {
                    sidebarAdapter.notifyDataSetChanged()
                }
            }
        }
        viewModel.checkAuthStatus()
        viewModel.refreshState()
        viewModel.refreshContinueWatching()
        updateViewModel.checkForUpdate()
    }

    private fun setupGrid() {
        gridAdapter = MovieAdapter(
            onMovieClick = { movie ->
                val intent = Intent(this, SeriesDetailActivity::class.java).apply {
                    putExtra("CONTENT_ID", movie.id)
                    putExtra("TITLE", movie.title)
                    putExtra("DESCRIPTION", movie.description)
                    putExtra("GENRE", movie.genre)
                    putExtra("COVER_URL", movie.cover_url)
                    putExtra("SOURCE", movie.source)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            },
            onFocusLeft = { view ->
                val nextFocus = view.focusSearch(View.FOCUS_LEFT)
                if (nextFocus != null && nextFocus.parent !== rvContentGrid && viewModel.uiState.value.mode == MainMode.SEARCH) {
                    etSearch.requestFocus()
                    return@MovieAdapter true
                }
                if (nextFocus != null && nextFocus.parent === rvSidebarCategories) {
                    val targetPos = sidebarAdapter.getSelectedIndex()
                    if (targetPos != -1) {
                        val targetView = rvSidebarCategories.layoutManager?.findViewByPosition(targetPos)
                        targetView?.requestFocus()
                        return@MovieAdapter true
                    }
                }
                false
            }
        )

        continueWatchingAdapter = ContinueWatchingAdapter(
            onItemOptions = { item -> showContinueWatchingOptions(item) },
            onItemClick = { item -> openPlayer(item) }
        )

        layoutManager = GridLayoutManager(this, MovieAdapter.SPAN_COUNT)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = rvContentGrid.adapter
                return if (adapter === gridAdapter && gridAdapter.getItemViewType(position) == 1) MovieAdapter.SPAN_COUNT else 1
            }
        }
        rvContentGrid.layoutManager = layoutManager
        rvContentGrid.setHasFixedSize(true)
        rvContentGrid.setItemViewCacheSize(MovieAdapter.SPAN_COUNT * 2)
        (rvContentGrid.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rvContentGrid.adapter = gridAdapter
        rvContentGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val mode = viewModel.uiState.value.mode
                if (mode == MainMode.CONTINUE_WATCHING || mode == MainMode.WATCHLIST) return
                if (rvContentGrid.adapter !== gridAdapter) return

                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (gridAdapter.itemCount > 0 && lastVisible >= gridAdapter.itemCount - MovieAdapter.SPAN_COUNT * 3) {
                    viewModel.loadContentBottom()
                }
            }
        })
    }

    private fun openPlayer(item: ContinueWatchingItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EPISODE_ID", item.episode_id)
            putExtra("CONTENT_ID", item.id)
            putExtra("SOURCE", item.source)
            putExtra("TITLE", item.title)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSeries(item: ContinueWatchingItem) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("CONTENT_ID", item.id)
            putExtra("TITLE", item.title)
            putExtra("DESCRIPTION", item.description)
            putExtra("GENRE", item.genre)
            putExtra("COVER_URL", item.cover_url)
            putExtra("SOURCE", item.source)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showContinueWatchingOptions(item: ContinueWatchingItem) {
        if (optionsDialog?.isShowing == true) return
        val options = arrayOf(
            getString(R.string.continue_option_play, item.season_number, item.episode_number),
            getString(R.string.continue_option_series),
            getString(R.string.continue_option_remove)
        )
        optionsDialog = AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPlayer(item)
                    1 -> openSeries(item)
                    2 -> viewModel.hideContinueWatching(item)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupRetryButton() {
        btnRetry.setOnClickListener { viewModel.retry() }
        btnRetry.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.05f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
    }

    private fun setupSettingsButton(button: ImageView) {
        button.setOnClickListener { viewModel.openSettings() }
        button.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.15f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    sidebarAdapter.clearSelection()
                }
                viewModel.onSearchQueryChanged(query)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val query = etSearch.text.toString().trim()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
                if (query.isNotEmpty()) {
                    sidebarAdapter.clearSelection()
                    if (hasSearchResults(query)) {
                        focusFirstResult()
                    } else {
                        pendingSearchFocus = true
                        viewModel.onSearchQueryChanged(query)
                    }
                }
                true
            } else {
                false
            }
        }

        etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val atEnd = etSearch.selectionEnd == etSearch.text.length
            val wantsResults = keyCode == KeyEvent.KEYCODE_DPAD_DOWN || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && atEnd)
            if (wantsResults && hasSearchResults(etSearch.text.toString().trim())) {
                focusFirstResult()
                true
            } else {
                false
            }
        }
    }

    private fun hasSearchResults(query: String): Boolean {
        val state = viewModel.uiState.value
        return query.isNotEmpty() && state.mode == MainMode.SEARCH && state.currentQuery == query &&
            !state.isLoading && state.movies.isNotEmpty() && rvContentGrid.adapter === gridAdapter
    }

    private fun focusFirstResult() {
        pendingSearchFocus = false
        rvContentGrid.scrollToPosition(0)
        rvContentGrid.post {
            layoutManager.findViewByPosition(0)?.requestFocus()
        }
    }

    private fun setupSidebar() {
        rvSidebarCategories.layoutManager = LinearLayoutManager(this)
        rvSidebarCategories.setHasFixedSize(true)
        (rvSidebarCategories.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        sidebarAdapter = SidebarAdapter(emptyList()) { position ->
            etSearch.text.clear()
            viewModel.selectSidebarIndex(position)
        }
        rvSidebarCategories.adapter = sidebarAdapter
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
                    updateViewModel.availableUpdate.collect { showUpdateDialogIfReady() }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MainEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            is MainEvent.NavigateToLogin -> {
                                val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
                                    if (!event.message.isNullOrEmpty()) {
                                        putExtra("KICKOUT_MESSAGE", event.message)
                                    }
                                }
                                startActivity(intent)
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                                finish()
                            }
                            is MainEvent.NavigateToSettings -> {
                                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                startActivity(intent)
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: MainUiState) {
        sidebarAdapter.updateItems(state.sidebarItems)
        sidebarAdapter.selectItemQuietly(state.selectedSidebarIndex)

        val isContentEmpty = state.movies.isEmpty() && state.continueWatchingItems.isEmpty()
        val showSkeleton = state.mode != MainMode.CONTINUE_WATCHING && state.isLoading && state.movies.isEmpty()
        progressMain.visibility = if (state.mode == MainMode.CONTINUE_WATCHING && state.isLoading && isContentEmpty) View.VISIBLE else View.GONE

        if (state.errorMessage != null && isContentEmpty) {
            layoutEmptyState.visibility = View.VISIBLE
            tvEmpty.text = state.errorMessage
            btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
        } else {
            if (btnRetry.hasFocus()) rvSidebarCategories.requestFocus()
            layoutEmptyState.visibility = View.GONE
        }

        tvContentHint.visibility = if (state.mode == MainMode.CONTINUE_WATCHING && state.continueWatchingItems.isNotEmpty()) View.VISIBLE else View.GONE

        val scrollKey = if (state.mode == MainMode.GENRE) "${state.selectedSource.id}:${state.currentGenre}" else null
        if (scrollKey != renderedScrollKey) {
            val previousKey = renderedScrollKey
            if (previousKey != null && rvContentGrid.adapter === gridAdapter) {
                scrollPositions[previousKey] = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
            }
            renderedScrollKey = scrollKey
            pendingScrollRestore = scrollKey?.let { scrollPositions[it] }?.takeIf { it > 0 }
        }

        if (state.mode == MainMode.CONTINUE_WATCHING) {
            if (rvContentGrid.adapter !== continueWatchingAdapter) {
                rvContentGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                rvContentGrid.adapter = continueWatchingAdapter
            }
            continueWatchingAdapter.setItems(state.continueWatchingItems)
        } else if (showSkeleton) {
            if (rvContentGrid.adapter !== skeletonAdapter) {
                rvContentGrid.layoutManager = layoutManager
                rvContentGrid.adapter = skeletonAdapter
            }
            gridAdapter.setMovies(state.movies, state.isEndOfList)
        } else {
            if (rvContentGrid.adapter !== gridAdapter) {
                rvContentGrid.layoutManager = layoutManager
                rvContentGrid.adapter = gridAdapter
            }
            gridAdapter.setMovies(state.movies, state.isEndOfList) {
                val target = pendingScrollRestore
                if (target != null && target < state.movies.size) {
                    pendingScrollRestore = null
                    layoutManager.scrollToPositionWithOffset(target, 0)
                }
                if (pendingSearchFocus && state.mode == MainMode.SEARCH && !state.isLoading &&
                    state.currentQuery == etSearch.text.toString().trim()
                ) {
                    if (state.movies.isNotEmpty()) focusFirstResult() else pendingSearchFocus = false
                }
            }
        }
        if (pendingSearchFocus && state.mode == MainMode.SEARCH && !state.isLoading && state.errorMessage != null) {
            pendingSearchFocus = false
        }

        splashProgressBar.progress = state.initProgressPercent
        splashStatusText.text = state.initStepDescription
        splashPercentText.text = "${state.initProgressPercent}%"

        if (!isInitialDataLoaded && !state.isInitializing) {
            isInitialDataLoaded = true
            checkSplashAndHide()
        }
    }

    private fun showUpdateDialogIfReady() {
        val update = updateViewModel.availableUpdate.value ?: return
        if (updateDialog?.isShowing == true || splashOverlay.visibility != View.GONE || isFinishing) return
        updateDialog = createUpdateDialog(update).also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
            }
            dialog.show()
        }
    }

    private fun createUpdateDialog(update: MainAppVersion): AlertDialog {
        val changelog = update.changelog?.takeIf { it.isNotBlank() }
        val message = buildString {
            append(getString(R.string.update_dialog_message, update.versionName, BuildConfig.VERSION_NAME))
            if (changelog != null) {
                append("\n\n")
                append(changelog)
            }
        }
        return AlertDialog.Builder(this)
            .setTitle(R.string.update_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_dialog_install) { _, _ -> startLauncherUpdate() }
            .setNegativeButton(R.string.update_dialog_later) { _, _ -> updateViewModel.dismissUpdate() }
            .setOnCancelListener { updateViewModel.dismissUpdate() }
            .create()
    }

    private fun startLauncherUpdate() {
        val launchIntent = packageManager.getLaunchIntentForPackage(LAUNCHER_PACKAGE)
        if (launchIntent == null) {
            updateViewModel.dismissUpdate()
            Toast.makeText(
                this,
                getString(R.string.update_launcher_missing, BuildConfig.BACKEND_URL.trimEnd('/')),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
        finishAffinity()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (splashOverlay.visibility == View.VISIBLE && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) skipIntro()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun playIntro() {
        try {
            val player = MediaPlayer.create(this, R.raw.bljadflix_intro)
            if (player == null) {
                isIntroAudioDone = true
                return
            }
            mediaPlayer = player
            player.setOnCompletionListener { mp -> onIntroFinished(mp) }
            player.setOnErrorListener { mp, _, _ ->
                onIntroFinished(mp)
                true
            }
            player.start()
        } catch (_: Exception) {
            isIntroAudioDone = true
        }
    }

    private fun onIntroFinished(mp: MediaPlayer) {
        mp.release()
        if (mediaPlayer === mp) {
            mediaPlayer = null
        }
        isIntroAudioDone = true
        checkSplashAndHide()
    }

    private fun skipIntro() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isIntroAudioDone = true
        isSplashAnimDone = true
        checkSplashAndHide()
    }

    override fun onDestroy() {
        updateDialog?.dismiss()
        updateDialog = null
        optionsDialog?.dismiss()
        optionsDialog = null
        super.onDestroy()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun checkSplashAndHide() {
        if (!isSplashAnimDone || !isIntroAudioDone || isSplashHiding) return

        if (isInitialDataLoaded) {
            isSplashHiding = true
            splashOverlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    splashOverlay.visibility = View.GONE
                    showUpdateDialogIfReady()
                    if (sidebarAdapter.itemCount > 0) {
                        rvSidebarCategories.post {
                            val firstChild = rvSidebarCategories.getChildAt(0)
                            if (firstChild != null) {
                                firstChild.requestFocus()
                            } else {
                                rvSidebarCategories.requestFocus()
                            }
                        }
                    } else {
                        rvSidebarCategories.requestFocus()
                    }
                }
                .start()
        }
    }

    companion object {
        private const val LAUNCHER_PACKAGE = "com.shadow.firetv.installer"
    }
}

class SidebarAdapter(
    private var items: List<String>,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.ViewHolder>() {

    private var selectedIndex = -1
    private var recyclerView: RecyclerView? = null

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
        if (rv != null && rv.isComputingLayout) {
            rv.post { action() }
        } else {
            action()
        }
    }

    fun getSelectedIndex() = selectedIndex

    fun updateItems(newItems: List<String>) {
        if (items == newItems) return
        items = newItems
        safeNotify { notifyDataSetChanged() }
    }

    fun clearSelection() {
        val old = selectedIndex
        selectedIndex = -1
        if (old != -1) {
            safeNotify { notifyItemChanged(old) }
        }
    }

    fun selectItemQuietly(position: Int) {
        if (selectedIndex == position) return
        val old = selectedIndex
        selectedIndex = position
        safeNotify {
            if (old in items.indices) notifyItemChanged(old)
            if (position in items.indices) notifyItemChanged(position)
        }
    }

    fun selectItem(position: Int) {
        if (selectedIndex == position) return
        selectItemQuietly(position)
        if (position in items.indices) {
            onItemSelected(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_category, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tv.text = item
        val isSelected = position == selectedIndex
        holder.itemView.isSelected = isSelected

        ThemeManager.getInstance(holder.itemView.context).applySidebarTheme(holder.itemView, holder.tv)

        holder.itemView.setOnClickListener {
            selectItem(position)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val interpolator = FastOutSlowInInterpolator()
            if (hasFocus) {
                view.animate().scaleX(1.04f).scaleY(1.04f).setInterpolator(interpolator).setDuration(120).start()
                view.isSelected = true
                selectItem(position)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setInterpolator(interpolator).setDuration(120).start()
            }
        }

        holder.itemView.setOnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                val rvContentGrid = view.rootView.findViewById<RecyclerView>(R.id.rv_content_grid)
                if (rvContentGrid != null && rvContentGrid.adapter != null && rvContentGrid.adapter!!.itemCount > 0) {
                    val layoutManager = rvContentGrid.layoutManager as? LinearLayoutManager
                    val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: 0
                    val targetPos = if (firstVisible >= 0) firstVisible else 0
                    val targetView = layoutManager?.findViewByPosition(targetPos)
                    if (targetView != null) {
                        targetView.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(android.R.id.text1)
    }
}
