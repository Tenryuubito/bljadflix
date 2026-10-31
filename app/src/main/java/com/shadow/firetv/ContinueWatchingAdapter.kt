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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)

        view.setOnFocusChangeListener { v, hasFocus ->
            val interpolator = FastOutSlowInInterpolator()
            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(8f).setInterpolator(interpolator).setDuration(160).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setInterpolator(interpolator).setDuration(160).start()
            }
        }

        return ViewHolder(view)
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

        if (!item.cover_url.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.cover_url)
                .apply(
                    RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(holder.ivCover)
        } else {
            holder.ivCover.setImageDrawable(null)
        }

        if (item.duration_seconds > 0) {
            val progressPercent = ((item.progress_seconds / item.duration_seconds) * 100).roundToInt()
            holder.pbProgress.progress = progressPercent
        } else {
            holder.pbProgress.progress = 0
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (position == differ.currentList.size - 1) {
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun getItemCount() = differ.currentList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.iv_cover)
        val tvSeriesTitle: TextView = view.findViewById(R.id.tv_series_title)
        val tvEpisodeInfo: TextView = view.findViewById(R.id.tv_episode_info)
        val pbProgress: ProgressBar = view.findViewById(R.id.pb_progress)
    }
}
