package com.grapsee.gsai.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.grapsee.gsai.data.PlanStore

/**
 * The one way this app asks a human for help.
 *
 * Both the Help page and every article page compose the same message, carrying
 * the same three facts a support engineer would otherwise have to ask for: the
 * plan as actually recorded, the device and the OS. A plan name is only
 * reported when an activation was really recorded — the same rule every other
 * surface follows.
 */
fun mailSupport(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@grapsee.com")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(
            Intent.EXTRA_TEXT,
            body + "\n\n—\nPlan: " +
                (if (PlanStore.active) PlanStore.planName else "Nexly Pro ($12.99/month)") +
                "\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}" +
                "\nAndroid: ${Build.VERSION.RELEASE}",
        )
    }
    runCatching { context.startActivity(intent) }
}
