package com.shadow.firetv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.shadow.firetv.core.image.CoverLoader
import com.shadow.firetv.core.model.Movie

class MovieAdapter(
    private val onMovieClick: (Movie) -> Unit,
    private val onFocusLeft: ((View) -> Boolean)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var isEndOfList: Boolean = false
    private var pendingEndOfList: Boolean = false
    private val focusInterpolator = FastOutSlowInInterpolator()

    private val diffCallback = object : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun setMovies(newMovies: List<Movie>, endOfList: Boolean = false) {
        pendingEndOfList = endOfList
        differ.submitList(newMovies) {
            applyFooterState()
        }
    }

    private fun applyFooterState() {
        if (isEndOfList == pendingEndOfList) return
        isEndOfList = pendingEndOfList
        val footerPosition = differ.currentList.size
        if (isEndOfList) notifyItemInserted(footerPosition) else notifyItemRemoved(footerPosition)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isEndOfList && position == differ.currentList.size) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == 1) {
            val footer = FooterViewHolder(inflater.inflate(R.layout.item_end_of_list, parent, false))
            footer.itemView.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (onFocusLeft?.invoke(view) == true) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
            return footer
        }

        val holder = MovieViewHolder(inflater.inflate(R.layout.item_movie, parent, false))
        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in differ.currentList.indices) onMovieClick(differ.currentList[pos])
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1.0f
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(if (hasFocus) 12f else 0f)
                .setInterpolator(focusInterpolator)
                .setDuration(160)
                .start()
        }

        holder.itemView.setOnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val pos = holder.bindingAdapterPosition
                val currentListSize = differ.currentList.size
                if (pos != RecyclerView.NO_POSITION && currentListSize > 0) {
                    val isLastRow = (pos / SPAN_COUNT) == ((currentListSize - 1) / SPAN_COUNT)
                    if (isLastRow) {
                        if (isEndOfList && event.action == KeyEvent.ACTION_DOWN) {
                            val rv = view.parent as? RecyclerView
                            val footerView = rv?.layoutManager?.findViewByPosition(currentListSize)
                            if (footerView != null) {
                                footerView.requestFocus()
                            } else {
                                rv?.smoothScrollToPosition(currentListSize)
                            }
                        }
                        return@setOnKeyListener true
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                if (onFocusLeft?.invoke(view) == true) {
                    return@setOnKeyListener true
                }
            }
            false
        }
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MovieViewHolder) {
            val movie = differ.currentList[position]
            holder.title.text = movie.title
            CoverLoader.load(holder.poster, movie.cover_url, movie.source, movie.id)
            differ.currentList.getOrNull(position + PRELOAD_DISTANCE)?.let { ahead ->
                CoverLoader.preload(holder.poster, ahead.cover_url, ahead.source, ahead.id)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is MovieViewHolder) {
            CoverLoader.clear(holder.poster)
            holder.itemView.animate().cancel()
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
            holder.itemView.translationZ = 0f
        }
    }

    override fun getItemCount() = differ.currentList.size + if (isEndOfList) 1 else 0

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.movie_poster)
        val title: TextView = view.findViewById(R.id.movie_title)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        const val SPAN_COUNT = 5
        private const val PRELOAD_DISTANCE = SPAN_COUNT * 2
    }
}
