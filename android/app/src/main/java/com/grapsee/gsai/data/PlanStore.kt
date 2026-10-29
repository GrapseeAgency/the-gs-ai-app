package com.grapsee.gsai.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The one place the plan, its price and its limits come from.
 *
 * Before this, "Subscription" existed only as a docs row and a placeholder
 * screen whose own comment said the app had "no subscription, payment
 * processing or usage metering". Every number the Subscription & Usage sheet
 * draws now reads from here or from a real query, never from the art.
 *
 * WHAT IS REAL HERE
 *  - the price: 12.99 USD/month, the figure the owner confirmed and the one
 *    already recorded in docs/UI_ARCHITECTURE.md:175 (the mock's 19.99 is
 *    placeholder art);
 *  - the window: the calendar month is computed in the DEVICE's timezone,
 *    because message timestamps are stored in UTC and comparing them to a
 *    local month boundary is a bug this repo has already paid for once;
 *  - activation: when the plan was actually started on this install.
 *
 * WHAT IS NOT REAL, AND THEREFORE NOT CLAIMED
 *  - there is no payment processor, no card, no invoice and no purchase path
 *    in this build. [planId] is stored locally, so it records the plan the
 *    install is on, not a settled transaction. The screen must not draw a card
 *    or a receipt, and does not.
 */
object PlanStore {

    const val PRO_ID = "pro"
    const val PRO_NAME = "Nexly Pro"
    const val PRO_PRICE_USD = 12.99

    private const val PREFS = "gs_plan"
    private const val KEY_PLAN = "gs.plan.id"
    private const val KEY_SINCE = "gs.plan.since"
    private const val KEY_ACTIVATED = "gs.plan.activated"

    /**
     * Monthly allowances for the four meters the sheet draws.
     *
     * HONEST STATUS: these are DISPLAYED, not ENFORCED. No code path consults
     * them, so reaching a limit changes nothing — the send path will keep
     * sending. An earlier version of this comment claimed "the send path
     * enforces", which was false. Until enforcement lands at ChatRepository's
     * send path, the Subscription screen must not imply a cap that bites.
     */
    const val LIMIT_MESSAGES = 1_000
    const val LIMIT_IMAGES = 500
    const val LIMIT_UPLOADS = 50
    const val LIMIT_REASONING = 200

    /** The plan this install is on. Defaults to Pro because that is the only
     *  plan that exists in this build — there is no free tier to fall back to,
     *  and inventing one would be a product decision, not a code one. */
    var planId by mutableStateOf(PRO_ID); private set

    /** Epoch millis the plan started, or 0 when never recorded. */
    var sinceMillis by mutableStateOf(0L); private set

    /**
     * Whether a Pro activation has actually been recorded on this install.
     *
     * Deliberately separate from [planId], and deliberately FALSE by default.
     * There is no purchase path in this build, so an install that has not gone
     * through one is not subscribed — and the screens must not print an
     * "Active" pill or a next-billing date for a plan nobody bought. That was
     * the mistake this flag exists to make impossible.
     */
    var activated by mutableStateOf(false); private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        planId = p.getString(KEY_PLAN, null) ?: PRO_ID
        sinceMillis = p.getLong(KEY_SINCE, 0L)
        activated = p.getBoolean(KEY_ACTIVATED, false)
        // No fabricating a cycle start here. A `sinceMillis` that exists only
        // because the app was opened is not a subscription, and deriving a
        // "next billing date" from it invented a renewal date for a plan
        // nobody bought.
    }

    /** The only way a plan becomes active. Called from a real activation flow. */
    fun recordActivation(context: Context, now: Long = System.currentTimeMillis()) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        planId = PRO_ID
        sinceMillis = now
        activated = true
        p.edit().putString(KEY_PLAN, PRO_ID).putLong(KEY_SINCE, now)
            .putBoolean(KEY_ACTIVATED, true).apply()
    }

    fun clearActivation(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        activated = false
        sinceMillis = 0L
        p.edit().remove(KEY_ACTIVATED).remove(KEY_SINCE).apply()
    }

    val planName: String
        get() = if (planId == PRO_ID) PRO_NAME else planId.replaceFirstChar { it.uppercase() }

    /** "$12.99 / month" — formatted from the constant, never re-typed in UI. */
    val priceLabel: String
        get() = String.format(java.util.Locale.US, "\$%.2f / month", PRO_PRICE_USD)

    val cycleLabel: String
        get() = "Monthly"

    /** True only when an activation was really recorded. */
    val active: Boolean
        get() = planId == PRO_ID && activated

    /**
     * The next renewal, derived from the real activation stamp by advancing
     * one month at a time until the date is in the future. 0 when the plan has
     * no recorded start, in which case the caller says so instead of printing
     * a made-up date.
     */
    fun nextBillingMillis(now: Long = System.currentTimeMillis()): Long {
        if (!activated || sinceMillis <= 0L) return 0L
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = sinceMillis }
        while (cal.timeInMillis <= now) cal.add(java.util.Calendar.MONTH, 1)
        return cal.timeInMillis
    }
}
