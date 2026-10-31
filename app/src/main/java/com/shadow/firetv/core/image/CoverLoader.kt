package com.shadow.firetv.core.image

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.shadow.firetv.BuildConfig
import com.shadow.firetv.R
import com.shadow.firetv.core.source.SourceManager

object CoverLoader {

    private val options = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.placeholder_cover)
        .error(R.drawable.placeholder_cover)
        .fallback(R.drawable.placeholder_cover)

    private val transition = DrawableTransitionOptions.withCrossFade(150)

    fun coverUrl(originalUrl: String?, source: String?, contentId: Int): String? {
        if (originalUrl.isNullOrBlank() || contentId < 0) return null
        val sourceId = source?.takeIf { it.isNotBlank() } ?: SourceManager.getInstance().getSelectedSource().id
        return "${BuildConfig.BACKEND_URL.trimEnd('/')}/cover/$sourceId/$contentId"
    }

    fun load(target: ImageView, originalUrl: String?, source: String?, contentId: Int) {
        Glide.with(target)
            .load(coverUrl(originalUrl, source, contentId))
            .apply(options)
            .transition(transition)
            .into(target)
    }

    fun preload(target: ImageView, originalUrl: String?, source: String?, contentId: Int) {
        val url = coverUrl(originalUrl, source, contentId) ?: return
        if (target.width <= 0 || target.height <= 0) return
        Glide.with(target)
            .load(url)
            .apply(options)
            .optionalCenterCrop()
            .preload(target.width, target.height)
    }

    fun clear(target: ImageView) {
        Glide.with(target).clear(target)
    }
}
