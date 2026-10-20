package com.pradeep.spamblocker.domain

/** What to do with a blocked call. REJECT = network sees a declined call, SILENCE = phone stays quiet. */
enum class BlockAction { REJECT, SILENCE }

enum class PatternType { STARTS_WITH, ENDS_WITH, CONTAINS }

enum class Reason(val label: String) {
    CONTACT("Saved contact"),
    ALLOWLIST("Your allowlist"),
    BLOCKLIST("Your blocklist"),
    NETWORK_SPAM("Flagged as spam by network"),
    PATTERN("Pattern rule"),
    REGION("Blocked region"),
    UNKNOWN("Unknown number"),
    DEFAULT_ALLOW("Allowed"),
}

/** Everything the rule engine needs to know about one incoming call. */
data class CallInfo(
    /** E.164 form (+9198...), or null for hidden / unparseable numbers. */
    val e164: String?,
    /** National significant number without country code or trunk 0, e.g. "8056123456". */
    val national: String?,
    val isContact: Boolean,
    /** False when READ_CONTACTS is missing; contact-dependent rules are then skipped for safety. */
    val contactsAvailable: Boolean,
    /** Caller display name supplied by the carrier/telecom stack (may carry a "Spam" label). */
    val callerName: String?,
    val verificationFailed: Boolean,
    /** Indian states / UTs this number belongs to (usually one). */
    val regions: Set<String>,
    /** true = mobile, false = landline, null = unknown. */
    val isMobile: Boolean?,
)

data class PatternRuleData(
    val id: Long,
    val type: PatternType,
    val pattern: String,
    val enabled: Boolean,
)

data class RegionRuleData(
    val region: String,
    val blockLandline: Boolean,
    val blockMobile: Boolean,
)

data class RuleSet(
    val allowlist: Set<String>,
    val blocklist: Map<String, BlockAction>,
    val patterns: List<PatternRuleData>,
    val regions: List<RegionRuleData>,
    val blockNetworkSpam: Boolean,
    val networkKeywords: List<String>,
    val blockFailedVerification: Boolean,
    val blockAllUnknown: Boolean,
    val defaultAction: BlockAction,
)

data class Decision(
    val blocked: Boolean,
    val action: BlockAction,
    val reason: Reason,
    val detail: String = "",
    /** Pattern rule id that matched, for the hit counter. */
    val patternId: Long? = null,
)
