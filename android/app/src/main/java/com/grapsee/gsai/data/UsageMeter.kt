package com.grapsee.gsai.data

import com.grapsee.gsai.data.local.MessageDao
import java.util.Calendar
import java.util.Locale

/**
 * The real numbers behind Subscription & Usage.
 *
 * Every field is a count of rows that actually exist in this device's database
 * for the current calendar month — no constants, no plausible-looking sample
 * data. A field that cannot be counted is `-1` and the screen prints "—",
 * because a fabricated zero would read as "you have used none of your
 * allowance", which is a different and equally false claim.
 *
 * The month boundary is computed in the DEVICE's timezone and emitted as an
 * ISO-8601 stamp for lexicographic comparison against `MessageEntity.createdAt`
 * (also ISO). Comparing a UTC stamp to a local day boundary is a bug this repo
 * has already shipped once; the window is therefore derived from the local
 * calendar, not from `System.currentTimeMillis()` arithmetic.
 */
data class UsageMeter(
    val messages: Int,
    val uploads: Int,
) {
    companion object {
        /** Nothing counted yet — the first frame, before the query returns. */
        val Zero = UsageMeter(messages = -1, uploads = -1)

        /** The query failed; the screen must not invent numbers. */
        val Unknown = UsageMeter(messages = -1, uploads = -1)

        /** First instant of the current month, local time, as ISO-8601. */
        fun monthStartIso(now: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // MessageEntity.createdAt is written by ChatRepository.nowIso() as
            // UTC with millis ("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"). Comparing a
            // naive LOCAL stamp against those strings drops the first hours of
            // the month (east of UTC) or counts the last hours of the previous
            // one (west of UTC). The window is the local calendar month, but it
            // must be EMITTED in the writer's exact shape and zone.
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return fmt.format(cal.time)
        }

        suspend fun read(dao: MessageDao, now: Long = System.currentTimeMillis()): UsageMeter {
            val start = monthStartIso(now)
            return UsageMeter(
                messages = dao.countUserMessagesSince(start),
                uploads = dao.countMessagesWithAttachmentsSince(start),
            )
        }
    }
}
