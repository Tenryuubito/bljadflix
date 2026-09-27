package com.shadow.firetv

import android.app.Application
import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.source.SourceManager
import com.shadow.firetv.core.theme.ThemeManager

class BljadflixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.getInstance(this)
        SourceManager.getInstance(this)
        ThemeManager.getInstance(this)
    }
}
