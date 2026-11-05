// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sync.RefreshPolicy
import io.github.diamondrubinny.scary.core.sync.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    private val now = 1_700_000_000_000
    private val minute = 60_000L

    @Test
    fun `fresh data is not fetched, missing or old data is, at once the first time`() {
        assertFalse(RefreshPolicy.due(needed = false, RefreshState(), now))
        assertTrue(RefreshPolicy.due(needed = true, RefreshState(), now))
    }

    @Test
    fun `after a failure it waits longer each time, up to half an hour`() {
        assertEquals(0, RefreshPolicy.retryDelay(0))
        assertEquals(1 * minute, RefreshPolicy.retryDelay(1))
        assertEquals(2 * minute, RefreshPolicy.retryDelay(2))
        assertEquals(16 * minute, RefreshPolicy.retryDelay(5))
        assertEquals(30 * minute, RefreshPolicy.retryDelay(6))
        assertEquals(30 * minute, RefreshPolicy.retryDelay(1_000))

        var state = RefreshPolicy.after(RefreshState(), success = false, now)
        assertFalse("too soon after a failure", RefreshPolicy.due(true, state, now + 30_000))
        assertTrue(RefreshPolicy.due(true, state, now + minute))
        state = RefreshPolicy.after(state, success = false, now + minute)
        assertFalse(RefreshPolicy.due(true, state, now + 2 * minute))
        assertTrue(RefreshPolicy.due(true, state, now + 3 * minute))
    }

    @Test
    fun `a success clears the failures, and a clock set back does not block a fetch`() {
        val failed = RefreshState(failures = 6, lastAttemptMillis = now)
        assertEquals(RefreshState(0, now + minute), RefreshPolicy.after(failed, success = true, now + minute))
        assertTrue(RefreshPolicy.due(true, failed, now - 10 * minute))
    }
}
