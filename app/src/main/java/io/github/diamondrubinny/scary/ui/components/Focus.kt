// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/**
 * Moves focus here and says whether it worked. `requestFocus()` no longer throws when its target is not on screen
 * (Compose UI 1.12): it logs and returns, so `runCatching { requestFocus() }.isSuccess` was always true and fallbacks
 * never ran. This asks the overload that reports the result.
 */
fun FocusRequester.tryRequestFocus(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)
