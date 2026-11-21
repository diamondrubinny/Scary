// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a VPN is on, and which VPN apps this TV has. This is about the TV's own VPN apps (SCARY's own tunnel is
 * VpnRepository): a VPN app covers the whole TV, SCARY included, and here SCARY only says whether one is on and opens
 * the app the viewer installed.
 *
 * Only what Android tells every app is read: that some network of the device is a VPN (not which, not where), and the
 * apps that declare a VPN service. Nothing is sent anywhere.
 */
class VpnWatcher(private val context: Context) {
    data class VpnApp(val packageName: String, val label: String)

    private val _active = MutableStateFlow(false)

    /** True while the device has a VPN network. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val networks = HashSet<Network>()

    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Every network that is a VPN: the default request leaves VPNs out, so that capability is taken off.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = change { networks.add(network) }
            override fun onLost(network: Network) = change { networks.remove(network) }
        }
        // A TV that refuses the watch simply never shows a VPN as on.
        runCatching { manager?.registerNetworkCallback(request, callback) }
    }

    @Synchronized
    private fun change(block: () -> Unit) {
        block()
        _active.value = networks.isNotEmpty()
    }

    /** The installed apps that declare a VPN service, by name; SCARY itself is never one. */
    fun apps(): List<VpnApp> {
        val pm = context.packageManager
        return runCatching { pm.queryIntentServices(Intent(VPN_SERVICE), 0) }.getOrDefault(emptyList())
            .mapNotNull { it.serviceInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { VpnApp(it.packageName, runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName)) }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        /** The action every VPN app's service declares (android.net.VpnService.SERVICE_INTERFACE). */
        const val VPN_SERVICE = "android.net.VpnService"
    }
}
