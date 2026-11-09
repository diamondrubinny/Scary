// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import io.github.diamondrubinny.scary.core.update.UpdateManifest
import io.github.diamondrubinny.scary.core.update.Updates
import io.github.diamondrubinny.scary.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Where an update stands, for the popup and the settings row. */
sealed interface UpdateState {
    /** Nothing known yet, or nothing to say. */
    data object Idle : UpdateState

    /** Not the official build (a fork, or a developer's own): updates from the official repository are off. */
    data object NotOfficial : UpdateState

    data object Checking : UpdateState
    /** Nothing newer on the stable release, as read at [checkedAt] (wall clock, ms). */
    data class UpToDate(val versionCode: Long, val checkedAt: Long) : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val percent: Int) : UpdateState

    /** Android needs the viewer's permission for SCARY to install apps, once. */
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState

    /** Android shows its own install screen now. */
    data class Installing(val manifest: UpdateManifest) : UpdateState
    data class Failed(val manifest: UpdateManifest?, val reason: Reason) : UpdateState

    enum class Reason { UNREACHABLE, NOT_VERIFIED, NOT_OFFICIAL, INSTALL_FAILED, NO_PERMISSION_SCREEN }
}

/**
 * New versions of SCARY from the official repository's "stable" release (see [Updates] for what is trusted and why).
 * Looks by itself at most once an hour while allowed (at start, on every return to SCARY and while it stays on
 * screen), and when asked in settings. Every new version is mandatory: the popup comes up by itself, the download
 * starts at once, and its only choice is "Update now". Installing always goes through Android's own confirmation: an
 * app cannot install anything silently. Only when something blocks the update (no network, a failed check, no
 * permission) can the viewer put it off ("Not now", until the next look), since SCARY is the TV's home screen and a
 * popup that could never close would lock the TV.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the popup is up. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private var work: Job? = null
    private var downloaded: File? = null

    private val packageManager: PackageManager get() = context.packageManager

    val installedVersion: Long by lazy {
        runCatching { PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(context.packageName, 0)) }.getOrDefault(0L)
    }

    /** Only the build signed with the official key looks for official updates: a fork's could never install them. */
    val official: Boolean by lazy { Updates.isOfficialBuild(installedCerts()) }

    /** Set once SCARY has had time to load its rows: the first look waits, so it never slows the start. */
    @Volatile
    private var started = false

    /**
     * Whether SCARY is on screen. Automatic looks happen only then, and an update downloaded meanwhile waits for it:
     * Android's install screen must never come up over a film playing in another app.
     */
    @Volatile
    private var inFront = false

    /** Downloaded and checked while SCARY was away: installed when it comes back. */
    @Volatile
    private var readyToInstall: UpdateManifest? = null

    init {
        scope.launch {
            delay(STARTUP_DELAY_MS)
            started = true
            while (true) {
                checkIfDue()
                delay(PERIODIC_MS)
            }
        }
    }

    /** In the middle of an update the viewer started: nothing else may change the state under them. */
    private fun busy(): Boolean = _prompt.value || when (_state.value) {
        is UpdateState.Downloading, is UpdateState.NeedsPermission, is UpdateState.Installing, UpdateState.Checking -> true
        else -> false
    }

    /** An automatic check, when due and allowed: official build, checks on, online data on. */
    fun checkIfDue() {
        if (!official) {
            _state.value = UpdateState.NotOfficial
            return
        }
        if (!started || !inFront || busy() || work?.isActive == true) return
        work = scope.launch {
            val prefs = settings.prefs.first()
            if (!prefs.autoUpdateCheck || !prefs.onlineEnabled) return@launch
            if (!Updates.checkDue(prefs.updateLastCheck, System.currentTimeMillis())) return@launch
            check(manual = false)
        }
    }

    /** "Check for updates" in settings: now, and a skipped or postponed version is offered again. */
    fun checkNow() {
        if (!official) {
            _state.value = UpdateState.NotOfficial
            return
        }
        if (work?.isActive == true || _state.value is UpdateState.Installing) return
        work = scope.launch { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        val before = _state.value
        _state.value = UpdateState.Checking
        val fetched = withContext(Dispatchers.IO) { fetchText(Updates.MANIFEST_URL) }
        val now = System.currentTimeMillis()
        val manifest = (fetched as? Fetched.Text)?.text?.let(Updates::parse)
        if (manifest == null) {
            // Offline, or no readable update.json: most often the few seconds in which CI replaces the stable
            // release with a new one. Never reported as "up to date", which would hide that version until the
            // next look; a check the viewer asked for says it could not check, so they try again.
            _state.value = if (manual) {
                UpdateState.Failed(null, UpdateState.Reason.UNREACHABLE)
            } else {
                before.takeUnless { it is UpdateState.Checking } ?: UpdateState.Idle
            }
            if (fetched !is Fetched.Failed) settings.setUpdateLastCheck(now)
            return
        }
        settings.setUpdateLastCheck(now)
        val prefs = settings.prefs.first()
        // Mandatory: a version skipped or put off under an older SCARY is offered all the same (manual = true).
        when (Updates.decide(manifest, installedVersion, Build.VERSION.SDK_INT, skippedVersion = null, laterUntilMillis = null, nowMillis = now, manual = true)) {
            Updates.Decision.OFFER, Updates.Decision.SKIPPED, Updates.Decision.LATER -> {
                _state.value = UpdateState.Available(manifest)
                _prompt.value = true
                // The download starts at once: the viewer only confirms the install on Android's screen.
                startUpdate(manifest)
            }
            Updates.Decision.UP_TO_DATE, Updates.Decision.TOO_NEW_FOR_DEVICE -> _state.value = UpdateState.UpToDate(installedVersion, now)
        }
    }

    /**
     * "Not now", offered only when something blocks the update: the popup goes until the next look (an hour, or the
     * next return to SCARY), then comes back.
     */
    fun notNow() {
        work?.cancel()
        _prompt.value = false
        val current = _state.value
        val manifest = when (current) {
            is UpdateState.NeedsPermission -> current.manifest
            is UpdateState.Failed -> current.manifest
            else -> null
        }
        if (manifest != null) _state.value = UpdateState.Available(manifest)
        // The next look is due at once when SCARY comes back, not in an hour.
        scope.launch { settings.setUpdateLastCheck(0L) }
    }

    /** Closes the popup after a failure or a cancel, keeping what is known. */
    fun dismiss() {
        work?.cancel()
        _prompt.value = false
        val current = _state.value
        val manifest = when (current) {
            is UpdateState.Downloading -> current.manifest
            is UpdateState.NeedsPermission -> current.manifest
            is UpdateState.Failed -> current.manifest
            else -> null
        }
        if (manifest != null) _state.value = UpdateState.Available(manifest)
    }

    /** Shows the popup for the version already found (the settings row, when one is available). */
    fun showPrompt() {
        if (_state.value is UpdateState.Available) _prompt.value = true
    }

    /** "Update now" (and "Try again"): download, verify, then hand the APK to Android's installer. */
    fun updateNow() {
        val manifest = when (val current = state.value) {
            is UpdateState.Available -> current.manifest
            is UpdateState.Failed -> current.manifest
            else -> null
        } ?: return
        _prompt.value = true
        startUpdate(manifest)
    }

    private fun startUpdate(manifest: UpdateManifest) {
        work?.cancel()
        work = scope.launch {
            _state.value = UpdateState.Downloading(manifest, 0)
            val file = withContext(Dispatchers.IO) { download(manifest) }
            if (file == null) {
                if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Failed(manifest, UpdateState.Reason.UNREACHABLE)
                return@launch
            }
            val check = withContext(Dispatchers.IO) { verify(manifest, file) }
            if (check != Updates.ApkCheck.OK) {
                Log.w(TAG, "Downloaded update refused: $check")
                file.delete()
                _state.value = UpdateState.Failed(
                    manifest,
                    if (check == Updates.ApkCheck.NOT_OFFICIAL) UpdateState.Reason.NOT_OFFICIAL else UpdateState.Reason.NOT_VERIFIED,
                )
                return@launch
            }
            downloaded = file
            if (inFront) {
                installOrAskPermission(manifest)
            } else {
                readyToInstall = manifest
                _state.value = UpdateState.Available(manifest)
            }
        }
    }

    /** Back on screen: if the viewer just allowed installs, or an update was downloaded meanwhile, carry on. */
    fun onResumed() {
        inFront = true
        val current = _state.value
        val ready = readyToInstall
        readyToInstall = null
        when {
            current is UpdateState.NeedsPermission && canInstall() -> installOrAskPermission(current.manifest)
            ready != null && current is UpdateState.Available && current.manifest == ready -> {
                _prompt.value = true
                installOrAskPermission(ready)
            }
            else -> checkIfDue()
        }
    }

    /** SCARY left the screen (another app, or the TV went off). */
    fun onLeft() {
        inFront = false
    }

    /** "Open settings" on the permission step. */
    fun openInstallPermission() {
        val current = _state.value as? UpdateState.NeedsPermission ?: return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some TVs hide the screen: the popup then says where to find the switch.
            _state.value = UpdateState.Failed(current.manifest, UpdateState.Reason.NO_PERMISSION_SCREEN)
        } catch (e: SecurityException) {
            _state.value = UpdateState.Failed(current.manifest, UpdateState.Reason.NO_PERMISSION_SCREEN)
        }
    }

    private fun canInstall(): Boolean = packageManager.canRequestPackageInstalls()

    private fun installOrAskPermission(manifest: UpdateManifest) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(manifest)
            return
        }
        val file = downloaded?.takeIf(File::exists) ?: run {
            _state.value = UpdateState.Available(manifest)
            return
        }
        _state.value = UpdateState.Installing(manifest)
        scope.launch(Dispatchers.IO) {
            try {
                install(file)
            } catch (e: IOException) {
                Log.w(TAG, "Install session failed: ${e.javaClass.simpleName}")
                _state.value = UpdateState.Failed(manifest, UpdateState.Reason.INSTALL_FAILED)
            } catch (e: SecurityException) {
                Log.w(TAG, "Install session refused: ${e.javaClass.simpleName}")
                _state.value = UpdateState.Failed(manifest, UpdateState.Reason.INSTALL_FAILED)
            }
        }
    }

    private fun install(file: File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("scary.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(context, sessionId, Intent(context, UpdateInstallReceiver::class.java), flags)
            session.commit(status.intentSender)
        }
    }

    /** From [UpdateInstallReceiver]: Android's answer to the install. */
    fun onInstallStatus(status: Int) {
        val manifest = (state.value as? UpdateState.Installing)?.manifest
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_SUCCESS -> {
                _prompt.value = false
                downloaded?.delete()
            }
            // The viewer said no on Android's screen: the update is mandatory, so the offer comes straight back.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                if (manifest != null) _state.value = UpdateState.Available(manifest)
                _prompt.value = manifest != null
            }
            else -> {
                Log.w(TAG, "Install failed with status $status")
                _state.value = UpdateState.Failed(manifest, UpdateState.Reason.INSTALL_FAILED)
            }
        }
    }

    /** Streams scary.apk to the app's own cache, hashing as it goes; null unless size and SHA-256 match. */
    private suspend fun download(manifest: UpdateManifest): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach(File::delete)
        val file = File(dir, "scary-${manifest.versionCode}.apk")
        val connection = open(Updates.APK_URL) ?: return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > manifest.size) return null.also { file.delete() }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        _state.value = UpdateState.Downloading(manifest, (total * 100 / manifest.size).toInt())
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (total == manifest.size && hash == manifest.sha256) file else null.also { file.delete() }
        } catch (e: IOException) {
            Log.i(TAG, "Update download failed: ${e.javaClass.simpleName}")
            file.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    /** What Android reads out of the APK: package, version and certificates (see [Updates.verifyApk]). */
    @Suppress("DEPRECATION")
    private fun verify(manifest: UpdateManifest, file: File): Updates.ApkCheck {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = packageManager.getPackageArchiveInfo(file.path, flags)
            ?: return Updates.ApkCheck.WRONG_PACKAGE
        return Updates.verifyApk(
            manifest = manifest,
            apkPackage = info.packageName,
            apkVersion = PackageInfoCompat.getLongVersionCode(info),
            installedVersion = installedVersion,
            apkCerts = certs(info),
            installedCerts = installedCerts(),
        )
    }

    private fun installedCerts(): Set<String>? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else signaturesFlag()
        return runCatching { certs(packageManager.getPackageInfo(context.packageName, flags)) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signaturesFlag(): Int = PackageManager.GET_SIGNATURES

    /** SHA-256 of each signing certificate, lowercase hex; null when Android does not say. */
    @Suppress("DEPRECATION")
    private fun certs(info: PackageInfo): Set<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        if (signatures.isNullOrEmpty()) return null
        return signatures.mapTo(HashSet()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private sealed interface Fetched {
        data class Text(val text: String) : Fetched
        data object Missing : Fetched
        data object Failed : Fetched
    }

    private fun fetchText(url: String): Fetched {
        val missing = BooleanArray(1)
        val connection = open(url) { code -> missing[0] = code == HttpURLConnection.HTTP_NOT_FOUND } ?: return if (missing[0]) Fetched.Missing else Fetched.Failed
        return try {
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(Updates.MAX_MANIFEST_BYTES + 1)
                if (bytes.size > Updates.MAX_MANIFEST_BYTES) Fetched.Missing else Fetched.Text(String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            Log.i(TAG, "Update check failed: ${e.javaClass.simpleName}")
            Fetched.Failed
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens [url] following GitHub's redirects by hand, so every hop is checked: HTTPS, GitHub's own hosts only
     * ([Updates.isAllowedUrl]). Null on anything else, and on any answer but 200.
     */
    private fun open(url: String, onRefused: (code: Int) -> Unit = {}): HttpsURLConnection? {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!Updates.isAllowedUrl(current)) return null
            val connection = try {
                (URL(current).openConnection() as? HttpsURLConnection) ?: return null
            } catch (e: IOException) {
                return null
            }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "SCARY-launcher")
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_OK -> return connection
                    code in REDIRECTS -> {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        current = URL(URL(current), location ?: return null).toString()
                    }
                    else -> {
                        connection.disconnect()
                        onRefused(code)
                        return null
                    }
                }
            } catch (e: IOException) {
                Log.i(TAG, "Update request failed: ${e.javaClass.simpleName}")
                connection.disconnect()
                return null
            }
        }
        return null
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** For UI tests: shows the popup for [manifest] as if a check had found it (without downloading anything). */
    fun offerForTest(manifest: UpdateManifest) {
        _state.value = UpdateState.Available(manifest)
        _prompt.value = true
    }

    /** For UI tests: the popup as when the download of [manifest] failed. */
    fun failForTest(manifest: UpdateManifest) {
        _state.value = UpdateState.Failed(manifest, UpdateState.Reason.UNREACHABLE)
        _prompt.value = true
    }

    private companion object {
        const val TAG = "ScaryUpdate"
        const val TIMEOUT_MS = 15_000
        const val BUFFER = 64 * 1024
        const val MAX_REDIRECTS = 4
        /** How often SCARY asks itself whether a look is due ([Updates.checkDue] decides; no request otherwise). */
        const val PERIODIC_MS = 15 * 60_000L
        const val STARTUP_DELAY_MS = 30_000L
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
