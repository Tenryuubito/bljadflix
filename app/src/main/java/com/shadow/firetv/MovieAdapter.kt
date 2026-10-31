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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.shadow.firetv.core.model.Movie

class MovieAdapter(
    private val onMovieClick: (Movie) -> Unit,
    private val onFocusLeft: ((View) -> Boolean)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var isEndOfList: Boolean = false
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

    private val diffCallback = object : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun setMovies(newMovies: List<Movie>, endOfList: Boolean = false) {
        val oldHasFooter = isEndOfList
        isEndOfList = endOfList
        differ.submitList(newMovies) {
            if (oldHasFooter != endOfList) {
                safeNotify { notifyDataSetChanged() }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isEndOfList && position == differ.currentList.size) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 1) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_end_of_list, parent, false)
            return FooterViewHolder(view)
        }
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MovieViewHolder) {
            val movie = differ.currentList[position]
            holder.title.text = movie.title

            Glide.with(holder.poster.context)
                .load(movie.cover_url)
                .apply(
                    RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.stat_notify_error)
                )
                .into(holder.poster)

            holder.itemView.isFocusable = true
            holder.itemView.isClickable = true

            holder.itemView.setOnClickListener { onMovieClick(movie) }

            holder.itemView.setOnFocusChangeListener { view, hasFocus ->
                val interpolator = FastOutSlowInInterpolator()
                if (hasFocus) {
                    view.animate()
                        .scaleX(1.06f)
                        .scaleY(1.06f)
                        .translationZ(12f)
                        .setInterpolator(interpolator)
                        .setDuration(160)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setInterpolator(interpolator)
                        .setDuration(160)
                        .start()
                }
            }

            holder.itemView.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    val pos = holder.bindingAdapterPosition
                    val currentListSize = differ.currentList.size
                    if (pos != RecyclerView.NO_POSITION && currentListSize > 0) {
                        val isLastRow = (pos / 5) == ((currentListSize - 1) / 5)
                        if (isLastRow) {
                            if (!isEndOfList) {
                                return@setOnKeyListener true
                            } else {
                                if (event.action == KeyEvent.ACTION_DOWN) {
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
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (onFocusLeft?.invoke(view) == true) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        } else if (holder is FooterViewHolder) {
            holder.itemView.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (onFocusLeft?.invoke(view) == true) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    override fun getItemCount() = differ.currentList.size + if (isEndOfList) 1 else 0

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.movie_poster)
        val title: TextView = view.findViewById(R.id.movie_title)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
