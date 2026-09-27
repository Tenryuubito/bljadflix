package com.shadow.firetv.core.source

import android.content.Context
import android.content.SharedPreferences

enum class StreamingSource(
    val id: String,
    val displayName: String,
    val url: String,
    val key: String
) {
    ANIWORLD("aniworld", "AniWorld (aniworld.to)", "https://aniworld.to/", "1"),
    STO("sto", "S.to (186.2.175.5)", "http://186.2.175.5/", "2"),
    FILMO("filmo", "Filmo (filmo.to)", "https://filmo.to/", "3");

    companion object {
        fun fromId(id: String?): StreamingSource {
            if (id.isNullOrEmpty()) return ANIWORLD
            return values().find {
                it.id.equals(id, ignoreCase = true) ||
                        it.key == id ||
                        it.url.equals(id, ignoreCase = true)
            } ?: ANIWORLD
        }
    }
}

class SourceManager private constructor(private var sharedPreferences: SharedPreferences?) {

    private var memorySource: StreamingSource? = null

    companion object {
        private const val PREF_NAME = "firetv_source_prefs"
        private const val KEY_SOURCE_ID = "selected_source_id"

        @Volatile
        private var instance: SourceManager? = null

        fun getInstance(context: Context? = null): SourceManager {
            val current = instance
            if (current != null) {
                if (context != null && current.sharedPreferences == null) {
                    synchronized(this) {
                        if (current.sharedPreferences == null) {
                            current.sharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        }
                    }
                }
                return current
            }
            return synchronized(this) {
                instance ?: SourceManager(
                    context?.applicationContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        fun createForTesting(sharedPreferences: SharedPreferences? = null): SourceManager {
            return SourceManager(sharedPreferences)
        }
    }

    fun getSelectedSource(): StreamingSource {
        if (memorySource != null) return memorySource!!

        val storedId = sharedPreferences?.getString(KEY_SOURCE_ID, null)
        val source = StreamingSource.fromId(storedId)
        memorySource = source
        return source
    }

    fun setSelectedSource(source: StreamingSource) {
        memorySource = source
        sharedPreferences?.edit()?.putString(KEY_SOURCE_ID, source.id)?.apply()
    }
}
