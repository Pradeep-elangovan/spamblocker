package com.pradeep.spamblocker.domain

data class LabelInput(
    val inAllowlist: Boolean,
    val inBlocklist: Boolean,
    /** answered / missed / rejected / blocked, or empty if unknown. */
    val outcome: String,
    val durationSec: Int,
    /** True when this app blocked the call itself (a decision, not evidence). */
    val appBlocked: Boolean,
    val appReason: String,
)

/** label is "SPAM", "LEGIT" or empty (leave out of training); source says how it was decided. */
data class Label(val label: String, val source: String)

/**
 * Turns what we know about a call into a training label. Order matters: your own taps win, then a real
 * conversation, then the app's own blocks (weak, to avoid learning from its own decisions), then the default
 * that an unanswered unknown call is spam.
 */
object Labeling {
    const val MIN_CONVERSATION_SECONDS = 20

    fun label(i: LabelInput): Label = when {
        i.inAllowlist -> Label("LEGIT", "user_allowlist")
        i.inBlocklist -> Label("SPAM", "user_blocklist")
        i.outcome == "answered" && i.durationSec >= MIN_CONVERSATION_SECONDS -> Label("LEGIT", "answered_long")
        i.appBlocked -> Label("SPAM", "app_blocked:${i.appReason}")
        i.outcome == "answered" -> Label("", "answered_short")
        i.outcome == "missed" || i.outcome == "rejected" || i.outcome == "blocked" -> Label("SPAM", "unanswered_default")
        else -> Label("", "unknown")
    }
}
