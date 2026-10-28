// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A TV's own input: HDMI 1, the antenna... [kind] picks its icon. */
data class TvInput(val id: String, val label: String, val kind: Kind, val packageName: String) {
    enum class Kind { HDMI, TUNER, OTHER }
}

/**
 * The inputs of a TV with screen (HDMI, composite, the tuner), for tiles at the start of the apps row: a launcher that
 * replaces the TV's own home screen must still lead to them. Streaming boxes and the emulator have none. No permission
 * is needed to list them.
 */
class TvInputsRepository(private val context: Context) {

    suspend fun load(): List<TvInput> = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return@withContext emptyList()
            manager.tvInputList
                .filter { !it.isHidden(context) && it.parentId == null }
                .map { info ->
                    val label = info.loadCustomLabel(context)?.toString()?.takeIf(String::isNotBlank)
                        ?: info.loadLabel(context).toString()
                    TvInput(info.id, label.take(MAX_LABEL), kindOf(info), info.serviceInfo.packageName)
                }
                .sortedWith(compareBy<TvInput>({ it.kind != TvInput.Kind.TUNER }, { it.label }))
        } catch (e: RuntimeException) {
            // A TV service that misbehaves must not stop the home screen from loading.
            Log.w(TAG, "No TV inputs: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun kindOf(info: TvInputInfo): TvInput.Kind = when {
        info.type == TvInputInfo.TYPE_HDMI -> TvInput.Kind.HDMI
        info.type == TvInputInfo.TYPE_TUNER || !info.isPassthroughInput -> TvInput.Kind.TUNER
        else -> TvInput.Kind.OTHER
    }

    private companion object {
        const val TAG = "ScaryInputs"
        const val MAX_LABEL = 40
    }
}
