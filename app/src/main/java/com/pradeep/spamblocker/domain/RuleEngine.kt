package com.pradeep.spamblocker.domain

/**
 * Pure Kotlin rule engine. First match wins:
 *
 *  1. Saved contact            -> allow
 *  2. Allowlist                -> allow
 *  3. Blocklist                -> block
 *  4. Network-flagged spam     -> block (toggle)
 *  5. Pattern rules            -> block
 *  6. Region rules             -> block
 *  7. "Block all unknown"      -> block (toggle)
 *  8. Default                  -> allow
 */
object RuleEngine {

    fun decide(call: CallInfo, rules: RuleSet): Decision {
        if (call.isContact) return allow(Reason.CONTACT, rules)

        val e164 = call.e164
        if (e164 != null && e164 in rules.allowlist) return allow(Reason.ALLOWLIST, rules)

        if (e164 != null) {
            rules.blocklist[e164]?.let { return block(it, Reason.BLOCKLIST) }
        }

        if (rules.blockNetworkSpam) {
            val name = call.callerName?.lowercase()
            if (name != null) {
                val hit = rules.networkKeywords
                    .map { it.trim().lowercase() }
                    .firstOrNull { it.isNotEmpty() && name.contains(it) }
                if (hit != null) {
                    return block(rules.defaultAction, Reason.NETWORK_SPAM, "Caller name: ${call.callerName}")
                }
            }
            if (rules.blockFailedVerification && call.verificationFailed) {
                return block(rules.defaultAction, Reason.NETWORK_SPAM, "Caller ID verification failed")
            }
        }

        // Everything below relies on knowing whether the caller is a saved contact.
        if (!call.contactsAvailable) return allow(Reason.DEFAULT_ALLOW, rules)

        for (p in rules.patterns) {
            if (p.enabled && matches(p, call.e164, call.national)) {
                return block(
                    rules.defaultAction,
                    Reason.PATTERN,
                    "${p.type.pretty()} ${p.pattern}",
                    p.id,
                )
            }
        }

        for (r in rules.regions) {
            if (r.region !in call.regions) continue
            val applies = when (call.isMobile) {
                true -> r.blockMobile
                false -> r.blockLandline
                null -> r.blockMobile || r.blockLandline
            }
            if (applies) return block(rules.defaultAction, Reason.REGION, r.region)
        }

        if (rules.blockAllUnknown) {
            return block(rules.defaultAction, Reason.UNKNOWN, if (e164 == null) "Hidden number" else "")
        }

        return allow(Reason.DEFAULT_ALLOW, rules)
    }

    fun matches(rule: PatternRuleData, e164: String?, national: String?): Boolean {
        var pat = rule.pattern.filter { !it.isWhitespace() && it != '-' }
        if (pat.isEmpty()) return false
        val target: String
        if (pat.startsWith("+")) {
            target = e164 ?: return false
        } else {
            target = national ?: return false
            // Let people type an STD code the way it is dialled (011 -> 11).
            if (rule.type == PatternType.STARTS_WITH && pat.length > 1 && pat.startsWith("0")) {
                pat = pat.removePrefix("0")
            }
        }
        return when (rule.type) {
            PatternType.STARTS_WITH -> target.startsWith(pat)
            PatternType.ENDS_WITH -> target.endsWith(pat)
            PatternType.CONTAINS -> target.contains(pat)
        }
    }

    private fun allow(reason: Reason, rules: RuleSet) =
        Decision(blocked = false, action = rules.defaultAction, reason = reason)

    private fun block(action: BlockAction, reason: Reason, detail: String = "", patternId: Long? = null) =
        Decision(blocked = true, action = action, reason = reason, detail = detail, patternId = patternId)

    private fun PatternType.pretty() = when (this) {
        PatternType.STARTS_WITH -> "Starts with"
        PatternType.ENDS_WITH -> "Ends with"
        PatternType.CONTAINS -> "Contains"
    }
}
