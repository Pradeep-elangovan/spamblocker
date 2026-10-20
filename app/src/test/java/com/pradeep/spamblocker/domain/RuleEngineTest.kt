package com.pradeep.spamblocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun call(
        e164: String? = "+918056123456",
        national: String? = "8056123456",
        contact: Boolean = false,
        contactsAvailable: Boolean = true,
        name: String? = null,
        verificationFailed: Boolean = false,
        regions: Set<String> = emptySet(),
        mobile: Boolean? = true,
    ) = CallInfo(e164, national, contact, contactsAvailable, name, verificationFailed, regions, mobile)

    private fun rules(
        allow: Set<String> = emptySet(),
        block: Map<String, BlockAction> = emptyMap(),
        patterns: List<PatternRuleData> = emptyList(),
        regions: List<RegionRuleData> = emptyList(),
        network: Boolean = false,
        verification: Boolean = false,
        unknown: Boolean = false,
        action: BlockAction = BlockAction.REJECT,
    ) = RuleSet(
        allowlist = allow, blocklist = block, patterns = patterns, regions = regions,
        blockNetworkSpam = network, networkKeywords = listOf("spam", "scam"),
        blockFailedVerification = verification, blockAllUnknown = unknown, defaultAction = action,
    )

    private fun pattern(type: PatternType, p: String, enabled: Boolean = true) = PatternRuleData(1, type, p, enabled)

    @Test fun defaultsToAllow() {
        val d = RuleEngine.decide(call(), rules())
        assertFalse(d.blocked)
        assertEquals(Reason.DEFAULT_ALLOW, d.reason)
    }

    @Test fun contactBeatsEverything() {
        val r = rules(block = mapOf("+918056123456" to BlockAction.REJECT), unknown = true,
            patterns = listOf(pattern(PatternType.STARTS_WITH, "8056")))
        val d = RuleEngine.decide(call(contact = true), r)
        assertFalse(d.blocked)
        assertEquals(Reason.CONTACT, d.reason)
    }

    @Test fun allowlistBeatsBlockingRules() {
        val r = rules(allow = setOf("+918056123456"), unknown = true,
            patterns = listOf(pattern(PatternType.STARTS_WITH, "8056")))
        assertEquals(Reason.ALLOWLIST, RuleEngine.decide(call(), r).reason)
    }

    @Test fun blocklistUsesPerNumberAction() {
        val r = rules(block = mapOf("+918056123456" to BlockAction.SILENCE), action = BlockAction.REJECT)
        val d = RuleEngine.decide(call(), r)
        assertTrue(d.blocked)
        assertEquals(BlockAction.SILENCE, d.action)
        assertEquals(Reason.BLOCKLIST, d.reason)
    }

    @Test fun startsWithPattern() {
        val r = rules(patterns = listOf(pattern(PatternType.STARTS_WITH, "8056")))
        val d = RuleEngine.decide(call(), r)
        assertTrue(d.blocked)
        assertEquals(Reason.PATTERN, d.reason)
        assertEquals(1L, d.patternId)
        assertFalse(RuleEngine.decide(call("+919999123456", "9999123456"), r).blocked)
    }

    @Test fun endsWithAndContainsPatterns() {
        assertTrue(RuleEngine.decide(call(), rules(patterns = listOf(pattern(PatternType.ENDS_WITH, "3456")))).blocked)
        assertTrue(RuleEngine.decide(call(), rules(patterns = listOf(pattern(PatternType.CONTAINS, "0561")))).blocked)
        assertFalse(RuleEngine.decide(call(), rules(patterns = listOf(pattern(PatternType.ENDS_WITH, "9999")))).blocked)
    }

    @Test fun disabledPatternIsIgnored() {
        val r = rules(patterns = listOf(pattern(PatternType.STARTS_WITH, "8056", enabled = false)))
        assertFalse(RuleEngine.decide(call(), r).blocked)
    }

    @Test fun patternWithCountryCodeMatchesE164() {
        val r = rules(patterns = listOf(pattern(PatternType.STARTS_WITH, "+918056")))
        assertTrue(RuleEngine.decide(call(), r).blocked)
    }

    @Test fun stdCodeWithTrunkZeroMatches() {
        val r = rules(patterns = listOf(pattern(PatternType.STARTS_WITH, "011")))
        assertTrue(RuleEngine.decide(call("+911123456789", "1123456789", mobile = false), r).blocked)
    }

    @Test fun regionRuleRespectsLandlineAndMobileToggles() {
        val r = rules(regions = listOf(RegionRuleData("Gujarat", blockLandline = false, blockMobile = true)))
        assertTrue(RuleEngine.decide(call(regions = setOf("Gujarat"), mobile = true), r).blocked)
        assertFalse(RuleEngine.decide(call(regions = setOf("Gujarat"), mobile = false), r).blocked)
        assertFalse(RuleEngine.decide(call(regions = setOf("Delhi"), mobile = true), r).blocked)
    }

    @Test fun regionRuleNeverBlocksContacts() {
        val r = rules(regions = listOf(RegionRuleData("Delhi", true, true)))
        assertFalse(RuleEngine.decide(call(contact = true, regions = setOf("Delhi")), r).blocked)
    }

    @Test fun blockAllUnknownToggle() {
        val d = RuleEngine.decide(call(), rules(unknown = true))
        assertTrue(d.blocked)
        assertEquals(Reason.UNKNOWN, d.reason)
        assertTrue(RuleEngine.decide(call(e164 = null, national = null), rules(unknown = true)).blocked)
        assertFalse(RuleEngine.decide(call(), rules(unknown = false)).blocked)
    }

    @Test fun networkSpamByCallerName() {
        val r = rules(network = true)
        val d = RuleEngine.decide(call(name = "Suspected SPAM caller"), r)
        assertTrue(d.blocked)
        assertEquals(Reason.NETWORK_SPAM, d.reason)
        assertFalse(RuleEngine.decide(call(name = "Pizza Place"), r).blocked)
        assertFalse(RuleEngine.decide(call(name = "Spam"), rules(network = false)).blocked)
    }

    @Test fun failedVerificationOnlyWhenEnabled() {
        assertTrue(RuleEngine.decide(call(verificationFailed = true), rules(network = true, verification = true)).blocked)
        assertFalse(RuleEngine.decide(call(verificationFailed = true), rules(network = true, verification = false)).blocked)
    }

    @Test fun withoutContactsPermissionOnlySafeRulesApply() {
        val r = rules(unknown = true, patterns = listOf(pattern(PatternType.STARTS_WITH, "8056")))
        assertFalse(RuleEngine.decide(call(contactsAvailable = false), r).blocked)
        val withBlocklist = rules(block = mapOf("+918056123456" to BlockAction.REJECT))
        assertTrue(RuleEngine.decide(call(contactsAvailable = false), withBlocklist).blocked)
    }
}
