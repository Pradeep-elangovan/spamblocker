package com.pradeep.spamblocker.data

import com.pradeep.spamblocker.domain.BlockAction
import com.pradeep.spamblocker.domain.PatternRuleData
import com.pradeep.spamblocker.domain.PatternType
import com.pradeep.spamblocker.domain.RegionRuleData
import com.pradeep.spamblocker.domain.RuleSet
import org.json.JSONArray
import org.json.JSONObject

class SpamRepository(
    private val dao: SpamDao,
    private val settingsStore: SettingsStore,
) {
    val blocked = dao.observeBlocked()
    val allowed = dao.observeAllowed()
    val patterns = dao.observePatterns()
    val regions = dao.observeRegions()
    val calls = dao.observeCalls()
    val settings = settingsStore.flow

    suspend fun settingsNow(): Settings = settingsStore.current()

    suspend fun updateSettings(transform: (Settings) -> Settings) = settingsStore.update(transform)

    /** Snapshot of everything the rule engine needs. Kept cheap: this runs while a call is ringing. */
    suspend fun buildRuleSet(): RuleSet {
        val s = settingsStore.current()
        return RuleSet(
            allowlist = dao.allowed().map { it.e164 }.toSet(),
            blocklist = dao.blocked().associate { it.e164 to actionOf(it.action, s.action) },
            patterns = dao.patterns().map {
                PatternRuleData(
                    id = it.id,
                    type = runCatching { PatternType.valueOf(it.type) }.getOrDefault(PatternType.STARTS_WITH),
                    pattern = it.pattern,
                    enabled = it.enabled,
                )
            },
            regions = dao.regions().map { RegionRuleData(it.region, it.blockLandline, it.blockMobile) },
            blockAllUnknown = s.blockAllUnknown,
            defaultAction = s.action,
        )
    }

    suspend fun block(e164: String, spam: Boolean, label: String = "") {
        val action = settingsStore.current().action
        dao.deleteAllowed(e164)
        dao.upsertBlocked(BlockedEntity(e164, action.name, label, spam))
    }

    suspend fun allow(e164: String, label: String = "") {
        dao.deleteBlocked(e164)
        dao.upsertAllowed(AllowedEntity(e164, label))
    }

    /** "Not spam" undo: stop blocking and make sure no pattern / region / unknown rule catches it again. */
    suspend fun notSpam(e164: String) = allow(e164)

    suspend fun removeBlocked(e164: String) = dao.deleteBlocked(e164)
    suspend fun removeAllowed(e164: String) = dao.deleteAllowed(e164)

    suspend fun toggleBlockedAction(e164: String, current: String) {
        val next = if (current == BlockAction.REJECT.name) BlockAction.SILENCE else BlockAction.REJECT
        dao.setBlockedAction(e164, next.name)
    }

    suspend fun addPattern(type: PatternType, pattern: String, label: String) {
        dao.insertPattern(PatternEntity(type = type.name, pattern = pattern.trim(), label = label.trim()))
    }

    suspend fun setPatternEnabled(id: Long, enabled: Boolean) = dao.setPatternEnabled(id, enabled)
    suspend fun deletePattern(id: Long) = dao.deletePattern(id)
    suspend fun bumpPattern(id: Long) = dao.bumpPattern(id)

    suspend fun setRegion(region: String, blocked: Boolean, landline: Boolean = true, mobile: Boolean = true) {
        if (blocked) dao.upsertRegion(RegionEntity(region, landline, mobile)) else dao.deleteRegion(region)
    }

    /** Returns the new row id, or -1 if the entry was a duplicate. */
    suspend fun logCall(entry: CallEntity): Long = dao.insertCall(entry)

    /** Returns true when an entry for this number already exists around [timestamp]. */
    suspend fun hasCallNear(e164: String, timestamp: Long, windowMs: Long = 120_000): Boolean =
        dao.countNear(e164, timestamp - windowMs, timestamp + windowMs) > 0

    suspend fun findCallNear(e164: String, timestamp: Long, windowMs: Long = 120_000): CallEntity? =
        dao.findNear(e164, timestamp - windowMs, timestamp + windowMs)

    suspend fun setCallOutcome(id: Long, outcome: String, durationSec: Int) = dao.setOutcome(id, outcome, durationSec)

    suspend fun allCallsAscending() = dao.allCallsAscending()
    suspend fun blockedNumbers(): Set<String> = dao.blocked().map { it.e164 }.toSet()
    suspend fun allowedNumbers(): Set<String> = dao.allowed().map { it.e164 }.toSet()

    suspend fun clearCalls() = dao.clearCalls()

    // ---- backup -----------------------------------------------------------------------------

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("blocked", JSONArray().also { arr ->
            dao.blocked().forEach {
                arr.put(JSONObject().put("e164", it.e164).put("action", it.action).put("label", it.label).put("spam", it.spam))
            }
        })
        root.put("allowed", JSONArray().also { arr ->
            dao.allowed().forEach { arr.put(JSONObject().put("e164", it.e164).put("label", it.label)) }
        })
        root.put("patterns", JSONArray().also { arr ->
            dao.patterns().forEach {
                arr.put(
                    JSONObject().put("type", it.type).put("pattern", it.pattern)
                        .put("label", it.label).put("enabled", it.enabled),
                )
            }
        })
        root.put("regions", JSONArray().also { arr ->
            dao.regions().forEach {
                arr.put(
                    JSONObject().put("region", it.region)
                        .put("landline", it.blockLandline).put("mobile", it.blockMobile),
                )
            }
        })
        return root.toString(2)
    }

    /** Merges a file produced by [exportJson]; returns the number of items read. */
    suspend fun importJson(text: String): Int {
        val root = JSONObject(text)
        var n = 0
        root.optJSONArray("blocked")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsertBlocked(
                    BlockedEntity(
                        o.getString("e164"), o.optString("action", BlockAction.REJECT.name),
                        o.optString("label"), o.optBoolean("spam"),
                    ),
                )
                n++
            }
        }
        root.optJSONArray("allowed")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsertAllowed(AllowedEntity(o.getString("e164"), o.optString("label")))
                n++
            }
        }
        root.optJSONArray("patterns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertPattern(
                    PatternEntity(
                        type = o.getString("type"), pattern = o.getString("pattern"),
                        label = o.optString("label"), enabled = o.optBoolean("enabled", true),
                    ),
                )
                n++
            }
        }
        root.optJSONArray("regions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsertRegion(RegionEntity(o.getString("region"), o.optBoolean("landline", true), o.optBoolean("mobile", true)))
                n++
            }
        }
        return n
    }

    private fun actionOf(name: String, fallback: BlockAction): BlockAction =
        runCatching { BlockAction.valueOf(name) }.getOrDefault(fallback)
}
