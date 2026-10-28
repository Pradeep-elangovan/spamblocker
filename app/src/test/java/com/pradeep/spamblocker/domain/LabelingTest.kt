package com.pradeep.spamblocker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelingTest {
    private fun input(
        allow: Boolean = false, block: Boolean = false, outcome: String = "missed", seconds: Int = 0,
        appBlocked: Boolean = false, reason: String = "DEFAULT_ALLOW",
    ) = LabelInput(allow, block, outcome, seconds, appBlocked, reason)

    @Test fun yourOwnTapsWin() {
        assertEquals(Label("LEGIT", "user_allowlist"), Labeling.label(input(allow = true, outcome = "missed")))
        assertEquals(Label("SPAM", "user_blocklist"), Labeling.label(input(block = true, outcome = "answered", seconds = 90)))
    }

    @Test fun longConversationIsLegit() {
        assertEquals(Label("LEGIT", "answered_long"), Labeling.label(input(outcome = "answered", seconds = 45)))
    }

    @Test fun shortAnsweredCallIsLeftOut() {
        assertEquals(Label("", "answered_short"), Labeling.label(input(outcome = "answered", seconds = 5)))
    }

    @Test fun unansweredUnknownDefaultsToSpam() {
        assertEquals(Label("SPAM", "unanswered_default"), Labeling.label(input(outcome = "missed")))
        assertEquals(Label("SPAM", "unanswered_default"), Labeling.label(input(outcome = "rejected")))
    }

    @Test fun appBlocksAreMarkedWeak() {
        assertEquals(
            Label("SPAM", "app_blocked:PATTERN"),
            Labeling.label(input(outcome = "blocked", appBlocked = true, reason = "PATTERN")),
        )
    }

    @Test fun unknownOutcomeIsLeftOut() {
        assertEquals(Label("", "unknown"), Labeling.label(input(outcome = "")))
    }
}
