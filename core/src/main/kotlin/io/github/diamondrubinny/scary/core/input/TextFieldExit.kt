// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.input

/**
 * When the remote's arrows leave a one-line text field instead of moving its cursor. Many TV remotes report themselves
 * as a keyboard, and a text field then keeps every arrow for its cursor: the focus is trapped in the field, and the
 * buttons and tabs around it cannot be reached. Up and down always leave (one line has nowhere to go); left and right
 * leave from an empty field, or when the cursor already stands at the edge in that direction, so typing still edits.
 */
object TextFieldExit {
    enum class Arrow { UP, DOWN, LEFT, RIGHT }

    /**
     * [selectionStart] and [selectionEnd] are the field's selection (equal for a plain cursor); [rightToLeft] is the
     * text's direction: null to tell from its first letter (Hebrew and Arabic read right to left).
     */
    fun leaves(arrow: Arrow, text: String, selectionStart: Int, selectionEnd: Int, rightToLeft: Boolean? = null): Boolean {
        if (arrow == Arrow.UP || arrow == Arrow.DOWN || text.isEmpty()) return true
        if (selectionStart != selectionEnd) return false
        val rtl = rightToLeft ?: startsRightToLeft(text)
        // The edge the arrow points at, in the text's own order: the end is on the right for left-to-right text.
        val atEnd = selectionEnd >= text.length
        val atStart = selectionStart <= 0
        return when (arrow) {
            Arrow.RIGHT -> if (rtl) atStart else atEnd
            else -> if (rtl) atEnd else atStart
        }
    }

    /** Whether the first letter with a direction reads right to left. */
    fun startsRightToLeft(text: String): Boolean {
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            }
        }
        return false
    }
}
