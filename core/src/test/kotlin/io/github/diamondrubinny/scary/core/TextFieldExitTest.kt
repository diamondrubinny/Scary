// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.input.TextFieldExit
import io.github.diamondrubinny.scary.core.input.TextFieldExit.Arrow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFieldExitTest {
    @Test
    fun upAndDownAlwaysLeaveAOneLineField() {
        assertTrue(TextFieldExit.leaves(Arrow.UP, "Hereditary", 3, 3))
        assertTrue(TextFieldExit.leaves(Arrow.DOWN, "Hereditary", 0, 10))
    }

    @Test
    fun anEmptyFieldLetsEveryArrowLeave() {
        Arrow.entries.forEach { assertTrue(TextFieldExit.leaves(it, "", 0, 0)) }
    }

    @Test
    fun leftAndRightMoveTheCursorUntilItReachesTheEdge() {
        val text = "like Hereditary"
        assertFalse(TextFieldExit.leaves(Arrow.RIGHT, text, 5, 5))
        assertTrue(TextFieldExit.leaves(Arrow.RIGHT, text, text.length, text.length))
        assertFalse(TextFieldExit.leaves(Arrow.LEFT, text, text.length, text.length))
        assertTrue(TextFieldExit.leaves(Arrow.LEFT, text, 0, 0))
        // A selection is edited, not left.
        assertFalse(TextFieldExit.leaves(Arrow.RIGHT, text, 0, text.length))
    }

    @Test
    fun rightToLeftTextHasItsEndOnTheLeft() {
        val text = "כמו תורשתי"
        assertTrue(TextFieldExit.startsRightToLeft(text))
        assertTrue(TextFieldExit.leaves(Arrow.LEFT, text, text.length, text.length))
        assertFalse(TextFieldExit.leaves(Arrow.RIGHT, text, text.length, text.length))
        assertTrue(TextFieldExit.leaves(Arrow.RIGHT, text, 0, 0))
        // A field held left to right (keys, addresses) follows its own direction, whatever is typed.
        assertTrue(TextFieldExit.leaves(Arrow.RIGHT, text, text.length, text.length, rightToLeft = false))
        assertTrue(TextFieldExit.startsRightToLeft("123 فيلم"))
        assertFalse(TextFieldExit.startsRightToLeft("2026 Scream"))
    }
}
