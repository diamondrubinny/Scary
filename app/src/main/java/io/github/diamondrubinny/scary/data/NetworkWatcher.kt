// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Tells when the TV has a working internet connection again. A TV often starts SCARY before its Wi-Fi is up, so the
 * first fetch of online rows fails; this is the cue to try again at once instead of waiting for the next retry.
 */
class NetworkWatcher(private val context: Context) {

    /** One signal each time the default network is (again) validated: it reaches the internet. */
    val online: Flow<Unit> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) trySend(Unit)
            }
        }
        val registered = manager != null && runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }.conflate()
}
