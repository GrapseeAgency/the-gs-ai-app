package com.grapsee.gsai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the dependency graph rooted here;
 * every [dagger.hilt.android.AndroidEntryPoint] consumer (activities,
 * fragments, ViewModels, workers) resolves from it.
 */
@HiltAndroidApp
class GSApplication : Application()
