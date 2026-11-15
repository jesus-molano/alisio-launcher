package dev.jesusmolano.alisio.feature.home

import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(private val appsRepository: AppsRepository) : ViewModel() {
    val apps: StateFlow<List<AppEntry>?> = appsRepository.apps

    fun launch(app: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean =
        appsRepository.launch(app, sourceBounds, options)

    companion object {
        fun factory(appsRepository: AppsRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeViewModel(appsRepository) } }
    }
}
