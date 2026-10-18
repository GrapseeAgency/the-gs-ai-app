package com.grapsee.gsai.data.notifications

/**
 * Is the app in front of the user? Single source of truth, written by
 * MainActivity's own lifecycle (ON_START/ON_STOP — the same signal that
 * decides whether a notification should exist at all). Deliberately NOT
 * a Compose state: it is read from the stream observer, a suspend context
 * where snapshot state would be the wrong tool anyway.
 */
object AppForeground {

    @Volatile
    var inForeground: Boolean = true
        internal set
}
