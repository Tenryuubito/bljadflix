package com.shadow.firetv.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.shadow.firetv.R
import com.shadow.firetv.core.image.CoverLoader

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CoverImage(
    coverUrl: String?,
    source: String?,
    contentId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val fallback = placeholder(R.drawable.placeholder_cover)
    GlideImage(
        model = CoverLoader.coverUrl(coverUrl, source, contentId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        loading = fallback,
        failure = fallback
    ) { request ->
        request.format(DecodeFormat.PREFER_RGB_565).diskCacheStrategy(DiskCacheStrategy.ALL)
    }
}
