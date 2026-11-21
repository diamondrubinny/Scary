// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.edit
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import io.github.diamondrubinny.scary.core.vpn.WireGuardConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A WireGuard tunnel for SCARY's own traffic (docs/VPN_PLAN.md), with the settings file the viewer got from their own
 * VPN provider. SCARY is the client only: it ships no provider, server or key, and the tunnel is WireGuard's own
 * library (Apache 2.0), not code of SCARY's.
 *
 * The file holds a private key: it is kept encrypted (SecretStore, Android Keystore), is not backed up, is never
 * logged and is never shown again; only where it connects to is. Only SCARY's traffic goes through the tunnel, so no
 * other app on the TV is touched. Android shows its own VPN notice while the tunnel is up.
 */
class VpnRepository(private val context: Context, private val secrets: SecretStore) {
    sealed interface State {
        /** No settings file on this TV. */
        data object None : State
        data class Down(val summary: WireGuardConfig.Summary) : State
        data class Connecting(val summary: WireGuardConfig.Summary) : State
        data class Up(val summary: WireGuardConfig.Summary) : State
        data class Failed(val summary: WireGuardConfig.Summary, val reason: Failure) : State
    }

    enum class Failure { NOT_ALLOWED, NO_DNS, START }

    enum class Install { OK, INVALID, STORAGE }

    private val prefs = context.getSharedPreferences("vpn", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var config: WireGuardConfig.Parsed? = null

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _killSwitch = MutableStateFlow(prefs.getBoolean(KILL_SWITCH, false))

    /** Off unless the viewer turns it on: with it, nothing goes to Real-Debrid or the viewer's source without the tunnel. */
    val killSwitch: StateFlow<Boolean> = _killSwitch.asStateFlow()

    fun setKillSwitch(on: Boolean) {
        _killSwitch.value = on
        prefs.edit { putBoolean(KILL_SWITCH, on) }
    }

    /**
     * True while the kill switch holds requests back: it is on, there are settings, and the tunnel is not up. Also
     * before the settings were read at startup, so nothing slips out in that moment.
     */
    val blocked: Boolean get() = _killSwitch.value && (!loaded || _state.value.let { it !is State.None && it !is State.Up })

    @Volatile
    private var loaded = false

    // Made only when a tunnel is first asked for: it loads WireGuard's native library.
    private val backend by lazy { GoBackend(context.applicationContext) }

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        // The system can take the tunnel down by itself (another VPN app starts, or the viewer revokes it).
        override fun onStateChange(newState: Tunnel.State) {
            val current = _state.value
            if (newState == Tunnel.State.DOWN && current is State.Up) _state.value = State.Down(current.summary)
        }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            config = secrets.read(SECRET_NAME)?.let(WireGuardConfig::parse)
            _state.value = config?.let { State.Down(it.summary) } ?: State.None
            loaded = true
        }
    }

    /** A settings file from the viewer (through the phone remote): read strictly, kept encrypted, never connected by itself. */
    suspend fun install(raw: String): Install = withContext(Dispatchers.IO) {
        val parsed = WireGuardConfig.parse(raw) ?: return@withContext Install.INVALID
        lock.withLock {
            takeDown()
            if (!secrets.write(SECRET_NAME, parsed.text)) return@withContext Install.STORAGE
            config = parsed
            _state.value = State.Down(parsed.summary)
        }
        Install.OK
    }

    /** The tunnel goes down and the file, with its key, is deleted from the TV. */
    suspend fun remove() = withContext(Dispatchers.IO) {
        lock.withLock {
            takeDown()
            secrets.remove(SECRET_NAME)
            config = null
            _state.value = State.None
        }
    }

    /**
     * What Android asks the viewer before an app may open a VPN, the first time: an intent to show, or null when they
     * already agreed. Null too on a TV that cannot be asked; [connect] then fails as not allowed.
     */
    fun consentIntent(): Intent? = runCatching { VpnService.prepare(context) }.getOrNull()

    suspend fun connect() = withContext(Dispatchers.IO) {
        lock.withLock {
            val current = config ?: return@withContext
            _state.value = State.Connecting(current.summary)
            _state.value = try {
                // Only SCARY's own traffic: no other app on the TV goes through the tunnel.
                val parsed = Config.parse(current.forApp(context.packageName).byteInputStream())
                backend.setState(tunnel, Tunnel.State.UP, parsed)
                State.Up(current.summary)
            } catch (e: BackendException) {
                // The kind of failure only: the settings hold a key.
                Log.w(TAG, "VPN did not start: ${e.reason}")
                State.Failed(
                    current.summary,
                    when (e.reason) {
                        BackendException.Reason.VPN_NOT_AUTHORIZED -> Failure.NOT_ALLOWED
                        BackendException.Reason.DNS_RESOLUTION_FAILURE -> Failure.NO_DNS
                        else -> Failure.START
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "VPN did not start: ${e.javaClass.simpleName}")
                State.Failed(current.summary, Failure.START)
            } catch (e: LinkageError) {
                // No tunnel library for this processor (an x86 device): see the packaging note in app/build.gradle.kts.
                Log.w(TAG, "VPN library missing: ${e.javaClass.simpleName}")
                State.Failed(current.summary, Failure.START)
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        lock.withLock {
            takeDown()
            config?.let { _state.value = State.Down(it.summary) }
        }
    }

    private fun takeDown() {
        val current = _state.value
        if (current !is State.Up && current !is State.Connecting) return
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            Log.w(TAG, "VPN did not stop cleanly: ${e.javaClass.simpleName}")
        } catch (e: LinkageError) {
            Log.w(TAG, "VPN library missing: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "ScaryVpn"
        const val SECRET_NAME = "wireguard_config"
        const val TUNNEL_NAME = "scary"
        const val KILL_SWITCH = "kill_switch"
    }
}
