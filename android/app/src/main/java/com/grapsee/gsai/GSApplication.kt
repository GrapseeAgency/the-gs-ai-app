package com.grapsee.gsai

import android.app.Application
import android.util.Log
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.ServiceLocator
import dagger.hilt.android.HiltAndroidApp
import com.grapsee.gsai.data.local.ModelStore

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
        // Remembered settings hydrate before any composable reads a switch.
        runCatching { SettingsStore.init(this) }
            .onFailure { Log.e("GSStartup", "SettingsStore init failed", it) }
        // The plan and its cycle live on from launch to launch; the screens
        // read it, so it must be hydrated before any UI touches it.
        runCatching { com.grapsee.gsai.data.PlanStore.init(this) }
            .onFailure { Log.e("GSStartup", "PlanStore init failed", it) }
        // Create the notification channels up front rather than on the first
        // successful post: the Settings screen deep-links into the OS channel
        // list, and a quiet-hours/DND refusal would leave that list empty.
        runCatching { com.grapsee.gsai.data.notifications.GsNotifier.ensureChannels(this) }
            .onFailure { Log.e("GSStartup", "notifier channels failed", it) }
        // The wizard's answers are read by Home, the chat empty state and the
        // local responder; hydrate before any of them compose.
        runCatching { OnboardingProfileStore.init(this) }
            .onFailure { Log.e("GSStartup", "OnboardingProfileStore init failed", it) }
        // On-device model consent and the installed path hydrate before any UI
        // asks whether local AI is available. It also re-checks the installed
        // file, because a restored backup can carry a preference pointing at a
        // model that is not on disk.
        runCatching { ModelStore.init(this) }
            .onFailure { Log.e("GSStartup", "ModelStore init failed", it) }
    }
}
