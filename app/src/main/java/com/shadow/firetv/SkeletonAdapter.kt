package com.shadow.firetv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class SkeletonAdapter(private val count: Int) : RecyclerView.Adapter<SkeletonAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_movie_skeleton, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.pulse.startDelay = (position % MovieAdapter.SPAN_COUNT) * PULSE_STAGGER_MS
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.pulse.start()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.pulse.cancel()
        holder.itemView.alpha = 1f
    }

    override fun getItemCount() = count

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pulse: ObjectAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f).apply {
            duration = PULSE_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    companion object {
        private const val PULSE_DURATION_MS = 700L
        private const val PULSE_STAGGER_MS = 80L
    }
}
