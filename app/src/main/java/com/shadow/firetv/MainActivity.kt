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
import com.shadow.firetv.core.model.MainAppVersion
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
    private lateinit var etSearch: EditText

    private lateinit var gridAdapter: MovieAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter
    private lateinit var sidebarAdapter: SidebarAdapter
    private lateinit var layoutManager: GridLayoutManager

    private var isSplashAnimDone = false
    private var isIntroAudioDone = false
    private var isInitialDataLoaded = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var splashOverlay: View
    private lateinit var splashProgressBar: ProgressBar
    private lateinit var splashStatusText: TextView
    private lateinit var splashPercentText: TextView

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory() }
    private val updateViewModel: UpdateViewModel by viewModels { UpdateViewModel.Factory() }
    private var updateDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_main)

        rvSidebarCategories = findViewById(R.id.rv_sidebar_categories)
        rvContentGrid = findViewById(R.id.rv_content_grid)
        progressMain = findViewById(R.id.progress_main)
        tvEmpty = findViewById(R.id.tv_empty)
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

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bljadflix_intro)
            if (mediaPlayer != null) {
                mediaPlayer?.setOnCompletionListener { mp ->
                    mp.release()
                    if (this.mediaPlayer === mp) {
                        this.mediaPlayer = null
                    }
                    isIntroAudioDone = true
                    checkSplashAndHide()
                }
                mediaPlayer?.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (this.mediaPlayer === mp) {
                        this.mediaPlayer = null
                    }
                    isIntroAudioDone = true
                    checkSplashAndHide()
                    true
                }
                mediaPlayer?.start()
            } else {
                isIntroAudioDone = true
            }
        } catch (_: Exception) {
            isIntroAudioDone = true
        }

        setupGrid()
        setupSearch()
        setupSidebar()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.getInstance(this).applyTheme(this)
        if (::sidebarAdapter.isInitialized) {
            if (rvSidebarCategories.isComputingLayout) {
                rvSidebarCategories.post { sidebarAdapter.notifyDataSetChanged() }
            } else {
                sidebarAdapter.notifyDataSetChanged()
            }
        }
        viewModel.checkAuthStatus()
        viewModel.refreshState()
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

        continueWatchingAdapter = ContinueWatchingAdapter(emptyList()) { item ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("EPISODE_ID", item.episode_id)
                putExtra("CONTENT_ID", item.id)
                putExtra("SOURCE", item.source)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        layoutManager = GridLayoutManager(this, MovieAdapter.SPAN_COUNT)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (gridAdapter.getItemViewType(position) == 1) MovieAdapter.SPAN_COUNT else 1
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

                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (gridAdapter.itemCount > 0 && lastVisible >= gridAdapter.itemCount - MovieAdapter.SPAN_COUNT * 3) {
                    viewModel.loadContentBottom()
                }
            }
        })
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
                if (query.isNotEmpty()) {
                    sidebarAdapter.clearSelection()
                    viewModel.onSearchQueryChanged(query)
                }
                etSearch.clearFocus()
                rvContentGrid.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
                true
            } else {
                false
            }
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

        progressMain.visibility = if (state.isLoading && state.movies.isEmpty() && state.continueWatchingItems.isEmpty()) View.VISIBLE else View.GONE

        if (state.errorMessage != null && state.movies.isEmpty() && state.continueWatchingItems.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = state.errorMessage
        } else {
            tvEmpty.visibility = View.GONE
        }

        if (state.mode == MainMode.CONTINUE_WATCHING) {
            if (rvContentGrid.adapter !== continueWatchingAdapter) {
                rvContentGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                rvContentGrid.adapter = continueWatchingAdapter
            }
            continueWatchingAdapter.setItems(state.continueWatchingItems)
        } else {
            if (rvContentGrid.adapter !== gridAdapter) {
                rvContentGrid.layoutManager = layoutManager
                rvContentGrid.adapter = gridAdapter
            }
            gridAdapter.setMovies(state.movies, state.isEndOfList)
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

    override fun onDestroy() {
        updateDialog?.dismiss()
        updateDialog = null
        super.onDestroy()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun checkSplashAndHide() {
        if (!isSplashAnimDone || !isIntroAudioDone) return

        if (isInitialDataLoaded) {
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
