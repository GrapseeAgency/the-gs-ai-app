package com.grapsee.gsai.data.notifications

import android.content.Context
import com.grapsee.gsai.ui.navigation.GsRoutes

/**
 * The "reply is ready" notification. The stream lives in an APP-SCOPED
 * controller, so a turn keeps running while the user is in another app —
 * the reply lands with zero signal unless something says so. This is that
 * something: one quiet notification per finished reply, tapping it opens
 * that exact conversation.
 *
 * This is a thin, reply-shaped facade over [GsNotifier]. It used to own its
 * own channel and its own gate chain, which is why a per-type or quiet-hours
 * preference could never have reached it; both moved to the dispatcher so
 * every notice in the app answers to the same switches.
 *
 * Honesty rules baked in (enforced by GsNotifier.allows):
 *  - never posts unless [AppForeground] says the app is NOT in front,
 *  - never posts without the runtime permission (API 33+) AND the OS enable,
 *  - never fabricates content: the notification body is the FIRST SLICE of
 *    the REAL reply text,
 *  - the master and per-type switches are live here: with replies off,
 *    nothing posts. It was a dead toggle before.
 */
object ReplyNotifier {

    /** Kept for the settings rows that report the OS permission. */
    const val CHANNEL_ID = "gs_reply_done"

    /** API 33+ runtime permission granted? Pre-33 the OS enable is enough. */
    fun hasPermission(context: Context): Boolean = GsNotifier.hasPermission(context)

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
        GsNotifier.post(
            context = context,
            kind = GsNotifier.Kind.MESSAGES,
            title = "Reply ready",
            body = text,
            route = GsRoutes.chat(conversationId),
            // One slot per THREAD: two conversations finishing while the app is
            // backgrounded stack as two notices, not one silently replacing
            // the other.
            notificationId = conversationId.hashCode(),
        )
    }
}
