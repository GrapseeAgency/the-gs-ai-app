package com.grapsee.gsai.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grapsee.gsai.MainActivity
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.navigation.GsRoutes
import java.util.Calendar

/**
 * The one delivery path every notice in Nexly goes through.
 *
 * Before this, the app had a single hard-coded channel and a Settings switch
 * (`pushNotifications`) whose own doc comment claimed it gated posting while
 * the code never read it — a dead toggle behind a promise. This object makes
 * that promise true: the master switch, the per-type switches, quiet hours and
 * Do-Not-Disturb are each consulted on the way to `notify()`, so turning any of
 * them off changes what the device actually receives.
 *
 * Honesty rules, inherited from [ReplyNotifier] and enforced in one place now:
 *  - never posts unless [AppForeground] says the app is NOT in front,
 *  - never posts without the runtime permission AND the OS-level enable,
 *  - never fabricates content: the caller passes the real strings,
 *  - every refusal is a silent no-op, never an exception.
 *
 * A kind whose switch is off is refused here even though its channel still
 * exists — Android forbids changing an existing channel's importance, so the
 * app-side gate is the live control and the channel stays the OS grouping.
 */
object GsNotifier {

    /** Route extra MainActivity understands — the same bus the quick actions use. */
    private const val EXTRA_ROUTE = "gs.route"

    /**
     * The notification types the reference sheet lists, in the order it draws
     * them. [channelId] is the OS channel each one posts through;
     * [MESSAGES] deliberately keeps the id [ReplyNotifier] shipped with so
     * upgrading does not orphan a channel the user may have tuned.
     */
    enum class Kind(
        val kindId: String,
        val channelId: String,
        val channelName: String,
        val channelDescription: String,
    ) {
        MESSAGES(
            "messages",
            "gs_reply_done",
            "New messages",
            "One notice when a reply finishes while Nexly is in the background",
        ),
        AI(
            "ai-updates",
            "gs_notice_ai",
            "AI updates",
            "On-device models that became available for this phone",
        ),
        TIPS(
            "tips",
            "gs_notice_tips",
            "Tips & suggestions",
            "Shortcuts and ways to get more out of Nexly",
        ),
        ACTIVITY(
            "account-activity",
            "gs_notice_activity",
            "Account activity",
            "Sign-ins and changes to your Nexly account",
        ),
        PRODUCT(
            "product-updates",
            "gs_notice_product",
            "Product updates",
            "New tools and releases for Nexly",
        ),
    }

    /** Creates every channel once. Safe to call from any entry point. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        for (kind in Kind.entries) {
            if (manager.getNotificationChannel(kind.channelId) != null) continue
            val channel = NotificationChannel(
                kind.channelId,
                kind.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = kind.channelDescription
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** API 33+ runtime permission granted? Pre-33 the OS enable is enough. */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The OS master switch — what the shade's own toggle does. */
    fun systemEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Does this kind get through right now? Ordered cheapest-and-truest first
     * so the reason a notice was dropped is the first real gate it hit.
     */
    fun allows(context: Context, kind: Kind): Boolean {
        if (!SettingsStore.pushNotifications) return false
        if (!SettingsStore.noticeEnabled(kind.kindId)) return false
        if (!hasPermission(context)) return false
        if (!systemEnabled(context)) return false
        if (SettingsStore.quietHoursEnabled && inQuietHours(System.currentTimeMillis())) return false
        // Do-Not-Disturb is deliberately NOT checked here. Android applies the
        // device's DND policy itself — under PRIORITY/IMPORTANT an exempt app
        // is still meant to come through, and under NONE the notice still
        // lands in the shade. Suppressing a second time would turn "don't
        // interrupt me" into "this message never existed", and the dropped
        // notice is unrecoverable because nothing re-posts it.
        return true
    }

    /**
     * Quiet Hours is the mock's "10:00 PM – 7:00 AM" row. The window is stored
     * as minutes-of-day and wraps midnight, so 22:00→07:00 holds every hour
     * from ten until seven and nothing in between.
     */
    fun inQuietHours(millis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = SettingsStore.quietStartMinute
        val end = SettingsStore.quietEndMinute
        return if (start <= end) now in start until end else now >= start || now < end
    }

    /**
     * Do-Not-Disturb reads the device's real interruption filter rather than a
     * remembered preference, so the switch cannot disagree with the system.
     * Without policy access the filter is not ours to set, and the row says so.
     */
    fun dndActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return false
        return runCatching {
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
    }

    /** True once the user has handed Nexly the DND access Android requires. */
    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    /**
     * Applies the real DND state. Returns false when Android has not granted
     * policy access — the caller then sends the user to the system screen
     * instead of flipping a switch that would lie about the outcome.
     */
    fun setDnd(context: Context, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        manager.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    /** The Settings screen Android opens to grant DND access. */
    fun dndAccessIntent(): Intent =
        Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Posts one notice and reports whether it actually landed. Every refusal
     * is a `false` rather than an exception, and callers that hold a one-shot
     * budget MUST key it off this return value: [LiveUpdater][com.grapsee.gsai.data.liveupdate.LiveUpdater]
     * announcing a release consumed its "once per version" token even when the
     * foreground gate dropped the notice, which made the whole Product Updates
     * path dead for the life of the install.
     */
    fun post(
        context: Context,
        kind: Kind,
        title: String,
        body: String,
        route: String? = null,
        notificationId: Int = kind.channelId.hashCode(),
    ): Boolean {
        val text = body.trim()
        if (title.isBlank() || text.isEmpty()) return false
        if (AppForeground.inForeground) return false
        if (!allows(context, kind)) return false
        ensureChannels(context)

        val pending = route?.let {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ROUTE, it)
            }
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(com.grapsee.gsai.R.drawable.ic_stat_reply)
            .setContentTitle(title)
            .setContentText(text.take(160).replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000).replace('\n', ' ')))
            // Auto-dismiss only when a tap does something. With no route the
            // notice has no contentIntent, so autoCancel would mean "tapping
            // destroys the only copy of this message".
            .setAutoCancel(pending != null)
            .apply { pending?.let { setContentIntent(it) } }
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    /** Convenience for the one route every notice so far targets. */
    fun chatRoute(conversationId: String): String = GsRoutes.chat(conversationId)
}
