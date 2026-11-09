// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI

/**
 * A new version of SCARY on the official "stable" release, as its `update.json` describes it. CI writes it when a
 * change is merged to main in the official repository, and nowhere else.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** SHA-256 of scary.apk, lowercase hex. */
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    /** "What's new", by language code; plain text. */
    val notes: Map<String, String>,
) {
    fun notesFor(language: String): String = notes[language] ?: notes["en"].orEmpty()
}

/**
 * Where SCARY looks for a new version, and the rules for trusting one. Everything here is fixed in the app: a fork
 * that changes it only changes its own builds, and a SCARY installed from the official repository listens to that
 * repository alone. Three things must hold before an update is installed, each enough to stop a stranger's APK:
 * - it comes from the official repository's "stable" release over HTTPS, from GitHub's own hosts only;
 * - its SHA-256 and size match the manifest, and it is a newer build of this very package;
 * - it is signed with the official certificate ([OFFICIAL_CERT_SHA256]), the one the installed app has; Android
 *   itself refuses an update signed with another key anyway.
 */
object Updates {
    const val OFFICIAL_REPOSITORY = "diamondrubinny/Scary"
    const val CHANNEL = "stable"
    const val PACKAGE_NAME = "io.github.diamondrubinny.scary"

    const val MANIFEST_URL = "https://github.com/$OFFICIAL_REPOSITORY/releases/download/$CHANNEL/update.json"

    /** The APK's address is fixed too: the manifest cannot send the TV anywhere else. */
    const val APK_URL = "https://github.com/$OFFICIAL_REPOSITORY/releases/download/$CHANNEL/scary.apk"

    /** GitHub serves release files from github.com, which redirects to its file hosts. */
    val ALLOWED_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /**
     * SHA-256 of the certificate the official APKs are signed with. CI refuses to publish an update signed with any
     * other (`.github/update-signing-cert.sha256` holds the same value).
     */
    val OFFICIAL_CERT_SHA256: Set<String> = setOf("5949b2e473f0b4b3e5f1ece023d6194ea186051a01582e5b6cf6cecffc7d791f")

    /** How often to look by itself, at most: updates are mandatory, so a new version should not wait long. */
    const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

    /** "Later" asks again after this. */
    const val LATER_MS = 24 * 60 * 60 * 1000L

    const val MAX_MANIFEST_BYTES = 16 * 1024
    const val MIN_APK_BYTES = 256 * 1024L
    const val MAX_APK_BYTES = 64 * 1024 * 1024L
    private const val MAX_NOTE_CHARS = 600
    private const val MAX_VERSION_NAME_CHARS = 32
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val VERSION_NAME = Regex("^[0-9A-Za-z.+-]{1,$MAX_VERSION_NAME_CHARS}$")
    private val LANGUAGE = Regex("^[a-z]{2}$")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads `update.json`, treated as hostile: every field is checked, it must name the official repository and the
     * stable channel, and the notes are kept as short plain text. Null when anything is off.
     */
    fun parse(text: String): UpdateManifest? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            return null
        }
        fun string(key: String): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (string("repository") != OFFICIAL_REPOSITORY || string("channel") != CHANNEL) return null
        if (string("package") != PACKAGE_NAME) return null
        val versionCode = (root["versionCode"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it > 0 } ?: return null
        val versionName = string("versionName")?.takeIf(VERSION_NAME::matches) ?: return null
        val sha256 = string("sha256")?.lowercase()?.takeIf(SHA256::matches) ?: return null
        val size = (root["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it in MIN_APK_BYTES..MAX_APK_BYTES } ?: return null
        val minSdk = (root["minSdk"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..100 } ?: 26
        val notes = (root["notes"] as? JsonObject)?.entries
            ?.mapNotNull { (language, value) ->
                val note = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                if (!LANGUAGE.matches(language)) return@mapNotNull null
                language to plainText(note)
            }
            ?.filter { it.second.isNotEmpty() }
            ?.toMap()
            .orEmpty()
        return UpdateManifest(versionCode, versionName, sha256, size, minSdk, notes)
    }

    /** No control characters (line breaks kept), no links to follow: just text, and not too much of it. */
    private fun plainText(text: String): String =
        text.filter { it == '\n' || !it.isISOControl() }
            .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .trim()
            .take(MAX_NOTE_CHARS)

    /** Whether SCARY may fetch [url]: HTTPS, one of GitHub's hosts, the default port, no user name in it. */
    fun isAllowedUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: java.net.URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in ALLOWED_HOSTS
    }

    /** What to do with a manifest the TV just read. */
    enum class Decision { OFFER, UP_TO_DATE, TOO_NEW_FOR_DEVICE, SKIPPED, LATER }

    fun decide(
        manifest: UpdateManifest,
        installedVersion: Long,
        deviceSdk: Int,
        skippedVersion: Int?,
        laterUntilMillis: Long?,
        nowMillis: Long,
        /** "Check now" in settings: a version skipped or put off is offered all the same. */
        manual: Boolean = false,
    ): Decision = when {
        manifest.versionCode <= installedVersion -> Decision.UP_TO_DATE
        manifest.minSdk > deviceSdk -> Decision.TOO_NEW_FOR_DEVICE
        !manual && skippedVersion == manifest.versionCode -> Decision.SKIPPED
        !manual && laterUntilMillis != null && nowMillis < laterUntilMillis -> Decision.LATER
        else -> Decision.OFFER
    }

    /** Whether an automatic check is due: never more than once per [CHECK_INTERVAL_MS], unless the clock went back. */
    fun checkDue(lastCheckMillis: Long?, nowMillis: Long): Boolean =
        lastCheckMillis == null || nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= CHECK_INTERVAL_MS

    /**
     * Whether a downloaded APK may be installed, from what Android reads out of it: this package, newer than what
     * is installed and exactly the version the manifest named, and signed only with official certificates that the
     * installed app is signed with too. [apkCerts] null: the device could not read them (Android 8), and Android's
     * own check at install time is all there is.
     */
    fun verifyApk(
        manifest: UpdateManifest,
        apkPackage: String?,
        apkVersion: Long,
        installedVersion: Long,
        apkCerts: Set<String>?,
        installedCerts: Set<String>?,
        official: Set<String> = OFFICIAL_CERT_SHA256,
    ): ApkCheck = when {
        apkPackage != PACKAGE_NAME -> ApkCheck.WRONG_PACKAGE
        apkVersion != manifest.versionCode.toLong() || apkVersion <= installedVersion -> ApkCheck.WRONG_VERSION
        apkCerts == null -> ApkCheck.OK
        apkCerts.isEmpty() || !official.containsAll(apkCerts) -> ApkCheck.NOT_OFFICIAL
        installedCerts != null && apkCerts != installedCerts -> ApkCheck.NOT_OFFICIAL
        else -> ApkCheck.OK
    }

    enum class ApkCheck { OK, WRONG_PACKAGE, WRONG_VERSION, NOT_OFFICIAL }

    /** Whether this app itself is the official build (signed with the official certificate); a fork's is not. */
    fun isOfficialBuild(installedCerts: Set<String>?): Boolean =
        installedCerts != null && installedCerts.isNotEmpty() && OFFICIAL_CERT_SHA256.containsAll(installedCerts)
}
