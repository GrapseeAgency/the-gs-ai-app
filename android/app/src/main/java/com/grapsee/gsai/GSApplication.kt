package com.grapsee.gsai

import android.app.Application
import android.util.Log
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.data.ProjectStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.ServiceLocator
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the dependency graph rooted here;
 * every [dagger.hilt.android.AndroidEntryPoint] consumer (activities,
 * fragments, ViewModels, workers) resolves from it.
 *
 * Launch survivability contract: NO store init may take the app down. Each
 * hydration runs inside its own guard — a corrupted preference from an older
 * build or an OEM quirk degrades that one subsystem instead of killing the
 * process, and [CrashReporter] captures whatever still escapes so the next
 * report carries the real stack trace instead of a shrug.
 */
@HiltAndroidApp
class GSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First — so anything that slips past the guards below is captured.
        CrashReporter.install(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        runCatching { ServiceLocator.init(this) }
            .onFailure { Log.e("GSStartup", "ServiceLocator init failed", it) }
        // User-created assistants (local-first CRUD) load before any UI reads them.
        runCatching { AssistantsStore.init(this) }
            .onFailure { Log.e("GSStartup", "AssistantsStore init failed", it) }
        // Remembered settings hydrate before any composable reads a switch.
        runCatching { SettingsStore.init(this) }
            .onFailure { Log.e("GSStartup", "SettingsStore init failed", it) }
        // User-created projects load before the dashboard, search or detail read them.
        runCatching { ProjectStore.init(this) }
            .onFailure { Log.e("GSStartup", "ProjectStore init failed", it) }
    }
}
