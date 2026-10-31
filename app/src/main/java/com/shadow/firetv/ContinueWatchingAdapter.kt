package com.shadow.firetv

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.shadow.firetv.core.image.CoverLoader
import com.shadow.firetv.core.model.ContinueWatchingItem
import kotlin.math.roundToInt

class ContinueWatchingAdapter(
    initialItems: List<ContinueWatchingItem> = emptyList(),
    private val onItemClick: (ContinueWatchingItem) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<ContinueWatchingItem>() {
        override fun areItemsTheSame(oldItem: ContinueWatchingItem, newItem: ContinueWatchingItem): Boolean {
            return oldItem.id == newItem.id && oldItem.episode_id == newItem.episode_id
        }

        override fun areContentsTheSame(oldItem: ContinueWatchingItem, newItem: ContinueWatchingItem): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    init {
        if (initialItems.isNotEmpty()) {
            differ.submitList(initialItems)
        }
    }

    fun setItems(newItems: List<ContinueWatchingItem>) {
        differ.submitList(newItems)
    }

    private val focusInterpolator = FastOutSlowInInterpolator()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)
        val holder = ViewHolder(view)

        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.03f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(if (hasFocus) 8f else 0f)
                .setInterpolator(focusInterpolator)
                .setDuration(160)
                .start()
        }

        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in differ.currentList.indices) onItemClick(differ.currentList[pos])
        }

        view.setOnKeyListener { _, keyCode, event ->
            event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                holder.bindingAdapterPosition == differ.currentList.size - 1
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = differ.currentList[position]

        holder.tvSeriesTitle.text = item.title

        val epTitle = if (!item.episode_title.isNullOrEmpty()) " - ${item.episode_title}" else ""
        holder.tvEpisodeInfo.text = holder.itemView.context.getString(
            R.string.episodes_episode_info,
            item.season_number.toString(),
            item.episode_number.toString(),
            epTitle
        )

        CoverLoader.load(holder.ivCover, item.cover_url, item.source, item.id)

        holder.pbProgress.progress = if (item.duration_seconds > 0) {
            ((item.progress_seconds / item.duration_seconds) * 100).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        CoverLoader.clear(holder.ivCover)
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1.0f
        holder.itemView.scaleY = 1.0f
        holder.itemView.translationZ = 0f
    }

    override fun getItemCount() = differ.currentList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.iv_cover)
        val tvSeriesTitle: TextView = view.findViewById(R.id.tv_series_title)
        val tvEpisodeInfo: TextView = view.findViewById(R.id.tv_episode_info)
        val pbProgress: ProgressBar = view.findViewById(R.id.pb_progress)
    }
}
