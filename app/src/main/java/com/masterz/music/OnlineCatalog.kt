package com.masterz.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineTrack(
    val identifier: String,
    val title: String,
    val artist: String,
    val license: String
)

object OnlineCatalog {
    private const val BASE = "https://archive.org"

    suspend fun search(term: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val clean = term.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val safeTerm = clean.replace("\\", "\\\\").replace("\"", "\\\"")
        val rawQuery = "mediatype:audio AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain*) AND (title:\"$safeTerm\" OR creator:\"$safeTerm\")"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val url = "$BASE/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl&rows=30&page=1&output=json"
        val json = JSONObject(readUrl(url))
        val docs = json.getJSONObject("response").getJSONArray("docs")
        buildList {
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val license = doc.optString("licenseurl")
                if (license.contains("creativecommons.org", true) || license.contains("publicdomain", true) || license.contains("public-domain", true)) {
                    val id = doc.optString("identifier")
                    if (id.isNotBlank()) add(
                        OnlineTrack(
                            identifier = id,
                            title = doc.optString("title").ifBlank { id },
                            artist = doc.optString("creator").ifBlank { "Arsip Internet" },
                            license = license
                        )
                    )
                }
            }
        }
    }

    suspend fun streamUrl(track: OnlineTrack): String = withContext(Dispatchers.IO) {
        val metadata = JSONObject(readUrl("$BASE/metadata/${track.identifier}"))
        val files = metadata.optJSONArray("files") ?: error("File audio tidak ditemukan")
        var selected: String? = null
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            val format = file.optString("format")
            if (name.endsWith(".mp3", true) || format.contains("mp3", true) || format.contains("mpeg", true)) {
                if (name.isNotBlank() && !name.contains("__ia_thumb")) {
                    selected = name
                    if (format.contains("vbr", true) || name.endsWith(".mp3", true)) break
                }
            }
        }
        val filename = selected ?: error("Arsip ini tidak menyediakan MP3 yang bisa diputar")
        val encoded = filename.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        "$BASE/download/${track.identifier}/$encoded"
    }

    private fun readUrl(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MasterZ-Android/0.2")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Server katalog membalas HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
