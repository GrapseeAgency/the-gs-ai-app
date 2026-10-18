package com.grapsee.gsai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 5 — the `ledger` wire contract (deep-research branch progress).
 *
 * The payloads marked REAL were captured from a live backend run
 * (runBranchingResearch, 2026-10-18) exactly as `JSON.stringify` put them on
 * the SSE wire — the parser must accept the server's real bytes, not a
 * convenient guess. The rest cover the rare paths (branch failure, preserved
 * contradictions, user stop) and the tolerance contract every other wire
 * parser in this package keeps: unknown type or malformed JSON → null,
 * missing upsert key → null, missing optional fields → defaults.
 */
class LedgerEventsTest {

    // --- REAL captured payloads ---------------------------------------------

    @Test
    fun `real ledger_started payload parses`() {
        val event = parseLedgerEventPayload(
            """{"type":"ledger_started","question":"Compare the runtime performance of Kotlin coroutines versus Java threads on Android","branches":3}"""
        )
        assertTrue(event is LedgerEvent.Started)
        event as LedgerEvent.Started
        assertEquals(3, event.branches)
        assertEquals("Compare the runtime performance of Kotlin coroutines versus Java threads on Android", event.question)
    }

    @Test
    fun `real branch_started payload parses`() {
        val event = parseLedgerEventPayload(
            """{"type":"branch_started","branchId":"br-1","query":"Compare the runtime performance of Kotlin coroutines"}"""
        )
        assertTrue(event is LedgerEvent.BranchStarted)
        event as LedgerEvent.BranchStarted
        assertEquals("br-1", event.branchId)
        assertEquals("Compare the runtime performance of Kotlin coroutines", event.query)
    }

    @Test
    fun `real inline branch_completed payload parses`() {
        val event = parseLedgerEventPayload(
            """{"type":"branch_completed","branchId":"br-2","query":"Java threads on Android","sources":7,"retrieved":3,"ok":true}"""
        )
        assertTrue(event is LedgerEvent.BranchCompleted)
        event as LedgerEvent.BranchCompleted
        assertEquals("br-2", event.branchId)
        assertEquals(7, event.sources)
        assertEquals(3, event.retrieved)
        assertEquals(true, event.ok)
        assertNull(event.via)
    }

    @Test
    fun `real subagent branch_completed payload parses with via marker`() {
        // The gap-branch sources that travel through the subagent boundary
        // carry their aggregator facts (sourceType/authority/engine); the
        // wire marks HOW the branch was collected.
        val event = parseLedgerEventPayload(
            """{"type":"branch_completed","branchId":"br-6","query":"threads runtime performance","sources":8,"retrieved":1,"ok":true,"via":"subagent"}"""
        )
        assertTrue(event is LedgerEvent.BranchCompleted)
        event as LedgerEvent.BranchCompleted
        assertEquals("subagent", event.via)
        assertEquals(true, event.ok)
    }

    @Test
    fun `real gaps_open payload parses both lists`() {
        val event = parseLedgerEventPayload(
            """{"type":"gaps_open","gaps":["need sourced information about \"coroutines\"","need sourced information about \"versus\""],"queries":["coroutines runtime performance","versus runtime performance"]}"""
        )
        assertTrue(event is LedgerEvent.GapsOpen)
        event as LedgerEvent.GapsOpen
        assertEquals(2, event.gaps.size)
        assertEquals(listOf("coroutines runtime performance", "versus runtime performance"), event.queries)
    }

    @Test
    fun `real subagents_launched and ledger_final payloads parse`() {
        val launched = parseLedgerEventPayload("""{"type":"subagents_launched","count":2}""")
        assertTrue(launched is LedgerEvent.SubagentsLaunched)
        assertEquals(2, (launched as LedgerEvent.SubagentsLaunched).count)

        val final = parseLedgerEventPayload(
            """{"type":"ledger_final","claims":26,"contradictions":0,"openGaps":3,"branches":6}"""
        )
        assertTrue(final is LedgerEvent.Final)
        final as LedgerEvent.Final
        assertEquals(26, final.claims)
        assertEquals(0, final.contradictions)
        assertEquals(3, final.openGaps)
        assertEquals(6, final.branches)
    }

    // --- rare paths -----------------------------------------------------------

    @Test
    fun `branch_failed carries the wire error verbatim`() {
        val event = parseLedgerEventPayload(
            """{"type":"branch_failed","branchId":"br-3","query":"gap query","error":"all engines failed"}"""
        )
        assertTrue(event is LedgerEvent.BranchFailed)
        event as LedgerEvent.BranchFailed
        assertEquals("br-3", event.branchId)
        assertEquals("all engines failed", event.error)
    }

    @Test
    fun `contradictions and stopped_by_user parse`() {
        val contradictions = parseLedgerEventPayload(
            """{"type":"contradictions","count":2,"note":"conflicting evidence preserved explicitly"}"""
        )
        assertTrue(contradictions is LedgerEvent.Contradictions)
        assertEquals(2, (contradictions as LedgerEvent.Contradictions).count)

        val stopped = parseLedgerEventPayload(
            """{"type":"stopped_by_user","note":"stop honored — synthesizing with what exists"}"""
        )
        assertTrue(stopped is LedgerEvent.StoppedByUser)
        assertEquals("stop honored — synthesizing with what exists", (stopped as LedgerEvent.StoppedByUser).note)
    }

    // --- tolerance contract ----------------------------------------------------

    @Test
    fun `unknown type yields null`() {
        assertNull(parseLedgerEventPayload("""{"type":"ledger_exploded","branchId":"br-1"}"""))
    }

    @Test
    fun `malformed json yields null`() {
        assertNull(parseLedgerEventPayload("""{"type":"branch_started","branchId":"""))
        assertNull(parseLedgerEventPayload("not json at all"))
        assertNull(parseLedgerEventPayload("[]"))
    }

    @Test
    fun `branch events without a branchId are dropped`() {
        // branchId is the upsert key — without it there is no row to update.
        assertNull(parseLedgerEventPayload("""{"type":"branch_started","query":"q"}"""))
        assertNull(parseLedgerEventPayload("""{"type":"branch_completed","sources":3}"""))
        assertNull(parseLedgerEventPayload("""{"type":"branch_failed","error":"x"}"""))
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val started = parseLedgerEventPayload("""{"type":"ledger_started"}""") as LedgerEvent.Started
        assertNull(started.branches)
        assertNull(started.question)

        val completed = parseLedgerEventPayload(
            """{"type":"branch_completed","branchId":"br-9"}"""
        ) as LedgerEvent.BranchCompleted
        assertEquals(0, completed.sources)
        assertEquals(0, completed.retrieved)
        assertEquals(false, completed.ok)
        assertNull(completed.via)

        val gaps = parseLedgerEventPayload("""{"type":"gaps_open"}""") as LedgerEvent.GapsOpen
        assertTrue(gaps.gaps.isEmpty())
        assertTrue(gaps.queries.isEmpty())
    }
}
