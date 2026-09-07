package com.grapsee.gsai

import android.app.Application
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.di.ServiceLocator
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the dependency graph rooted here;
 * every [dagger.hilt.android.AndroidEntryPoint] consumer (activities,
 * fragments, ViewModels, workers) resolves from it.
 */
@HiltAndroidApp
class GSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // User-created assistants (local-first CRUD) load before any UI reads them.
        AssistantsStore.init(this)
    }
}
