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
import com.grapsee.gsai.ui.navigation.GsRoutes

/**
 * The "reply is ready" notification. The stream lives in an APP-SCOPED
 * controller, so a turn keeps running while the user is in another app —
 * the reply lands with zero signal unless something says so. This is that
 * something: one quiet notification per finished reply, tapping it opens
 * that exact conversation.
 *
 * Honesty rules baked in:
 *  - never posts unless [AppForeground] says the app is NOT in front,
 *  - never posts without the runtime permission (API 33+) AND the channel
 *    being enabled — a silent no-op beats a SecurityException,
 *  - never fabricates content: the notification body is the FIRST SLICE of
 *    the REAL reply text,
 *  - the `pushNotifications` setting becomes a live switch here: if the
 *    user turned replies off, nothing posts. It was a dead toggle before.
 */
object ReplyNotifier {

    const val CHANNEL_ID = "gs_reply_done"

    /** Route extra MainActivity understands — reuses the quick-action bus. */
    private const val EXTRA_ROUTE = "gs.route"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Finished replies",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "One notice when a reply finishes while GS is in the background"
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** API 33+ runtime permission granted? Pre-33 the channel gate is enough. */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts the finished-reply notice. Safe to call for every terminal turn:
     * foreground turns, disabled settings and missing permission all no-op.
     *
     * @param conversationId the thread the reply belongs to (null → dropped)
     * @param replyText      the REAL streamed text; only its head is shown
     */
    fun postReplyReady(context: Context, conversationId: String?, replyText: String) {
        if (conversationId.isNullOrBlank()) return
        val text = replyText.trim()
        if (text.isEmpty()) return
        if (AppForeground.inForeground) return
        if (hasPermission(context).not()) return
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val preview = text.take(160).replace('\n', ' ')
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, GsRoutes.chat(conversationId))
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.grapsee.gsai.R.drawable.ic_stat_reply)
            .setContentTitle("Reply ready")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            // One slot per THREAD: two conversations finishing while the app is
            // backgrounded stack as two notices, not one silently replacing
            // the other.
            NotificationManagerCompat.from(context)
                .notify(conversationId.hashCode(), notification)
        }
    }
}
