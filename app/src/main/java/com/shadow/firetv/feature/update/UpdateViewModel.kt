package com.shadow.firetv.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadow.firetv.BuildConfig
import com.shadow.firetv.core.data.MovieRepository
import com.shadow.firetv.core.data.MovieRepositoryImpl
import com.shadow.firetv.core.model.MainAppVersion
import com.shadow.firetv.core.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val repository: MovieRepository,
    private val installedVersionCode: Int
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<MainAppVersion?>(null)
    val availableUpdate: StateFlow<MainAppVersion?> = _availableUpdate.asStateFlow()

    private var dismissedVersionCode = installedVersionCode
    private var isChecking = false

    fun checkForUpdate() {
        if (isChecking) return
        isChecking = true
        viewModelScope.launch {
            repository.getAppVersion().onSuccess { info ->
                if (info.mainApp.versionCode > dismissedVersionCode) {
                    _availableUpdate.value = info.mainApp
                }
            }
            isChecking = false
        }
    }

    fun dismissUpdate() {
        _availableUpdate.value?.let { dismissedVersionCode = it.versionCode }
        _availableUpdate.value = null
    }

    class Factory(
        private val repository: MovieRepository = MovieRepositoryImpl(RetrofitClient.instance),
        private val installedVersionCode: Int = BuildConfig.VERSION_CODE
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdateViewModel(repository, installedVersionCode) as T
        }
    }
}
