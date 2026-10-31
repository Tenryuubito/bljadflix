package com.shadow.firetv.core.image

import com.shadow.firetv.BuildConfig
import com.shadow.firetv.core.source.SourceManager

object CoverLoader {

    fun coverUrl(originalUrl: String?, source: String?, contentId: Int): String? {
        if (originalUrl.isNullOrBlank() || contentId < 0) return null
        val sourceId = source?.takeIf { it.isNotBlank() } ?: SourceManager.getInstance().getSelectedSource().id
        return "${BuildConfig.BACKEND_URL.trimEnd('/')}/cover/$sourceId/$contentId"
    }
}
