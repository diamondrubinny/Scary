// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * :core is plain Kotlin, so Android lint's NewApi check never sees it, yet it runs on TVs from Android 8 (minSdk 26).
 * This test fails when core uses Java APIs those TVs lack. URLEncoder.encode(String, Charset), which needs Android 13,
 * crashed SCARY on the API 30 emulator before this test existed.
 */
class AndroidCompatibilityTest {
    private val missingBeforeAndroid13 = mapOf(
        // The second argument is a Charset (any spelling), not the charset's name as a string.
        Regex("""URLEncoder\.encode\([^,]*,[^)"]*(Charsets?\b|UTF_8)""") to "URLEncoder.encode(String, Charset)",
        Regex("""URLDecoder\.decode\([^,]*,[^)"]*(Charsets?\b|UTF_8)""") to "URLDecoder.decode(String, Charset)",
        Regex("""\.readAllBytes\(\)""") to "InputStream.readAllBytes()",
        Regex("""\.readNBytes\(""") to "InputStream.readNBytes()",
        Regex("""\.transferTo\(""") to "InputStream.transferTo()",
        Regex("""\b(List|Set|Map)\.of\(""") to "List/Set/Map.of() (Android 11)",
        Regex("""\bHexFormat\b""") to "java.util.HexFormat",
        Regex("""Files\.(readString|writeString)\(""") to "Files.readString/writeString",
        Regex("""\.(strip|stripLeading|stripTrailing)\(\)""") to "String.strip()",
        Regex("""java\.net\.http""") to "java.net.http",
    )

    @Test
    fun coreUsesOnlyJavaApisThatOldTvsHave() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no sources found from ${File(".").absolutePath}", sources.isNotEmpty())
        val problems = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                missingBeforeAndroid13.entries.firstOrNull { it.key.containsMatchIn(code) }
                    ?.let { "${file.name}:${index + 1} uses ${it.value}" }
            }
        }
        assertTrue("Java APIs missing on Android 8 TVs:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
