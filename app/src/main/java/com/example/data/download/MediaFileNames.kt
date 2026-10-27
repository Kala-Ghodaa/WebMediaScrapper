package com.example.data.download

import java.security.MessageDigest
import java.util.Locale

internal object MediaFileNames {
    fun choose(title: String, extension: String, url: String, reservedNames: Set<String>): String {
        val ext = extension.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
        val clean = title.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_', '.').ifBlank { "media" }
        val stem = (if (clean.endsWith(".$ext", true)) clean.dropLast(ext.length + 1) else clean).take(120).ifBlank { "media" }
        val reserved = reservedNames.map { it.lowercase(Locale.ROOT) }.toSet()
        val basic = "$stem.$ext"
        if (basic.lowercase(Locale.ROOT) !in reserved) return basic
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
        var candidate = "${stem}_$hash.$ext"
        var suffix = 2
        while (candidate.lowercase(Locale.ROOT) in reserved) candidate = "${stem}_${hash}_${suffix++}.$ext"
        return candidate
    }
}
