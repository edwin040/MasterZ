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
    val license: String,
    val downloads: Long = 0L
)

object OnlineCatalog {
    private const val BASE = "https://archive.org"

    suspend fun search(term: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val clean = term.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val safeTerm = clean.replace("\\", "\\\\").replace("\"", "\\\"")
        val rawQuery = "mediatype:audio AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain* OR licenseurl:*public-domain*) AND (title:\"$safeTerm\" OR contributor:\"$safeTerm\" OR subject:\"$safeTerm\")"
        fetchTracks(rawQuery, 30, false)
    }

    suspend fun trending(): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val rawQuery = "mediatype:audio AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain* OR licenseurl:*public-domain*)"
        fetchTracks(rawQuery, 20, true)
    }

    private fun fetchTracks(rawQuery: String, rows: Int, sortByDownloads: Boolean): List<OnlineTrack> {
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val sort = if (sortByDownloads) "&sort[]=downloads+desc" else ""
        val url = "$BASE/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=contributor&fl[]=licenseurl&fl[]=downloads&rows=$rows&page=1&output=json$sort"
        val json = JSONObject(readUrl(url))
        val docs = json.getJSONObject("response").getJSONArray("docs")
        return buildList {
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val license = doc.optString("licenseurl")
                if (license.contains("creativecommons.org", true) || license.contains("publicdomain", true) || license.contains("public-domain", true)) {
                    val id = doc.optString("identifier")
                    if (id.isNotBlank()) add(
                        OnlineTrack(
                            identifier = id,
                            title = doc.optString("title").ifBlank { id },
                            artist = doc.optString("contributor").ifBlank { "Arsip Internet" },
                            license = license,
                            downloads = doc.optLong("downloads", 0L)
                        )
                    )
                }
            }
        }
    }

    suspend fun streamUrl(track: OnlineTrack): String = withContext(Dispatchers.IO) {
        val files = metadataFiles(track)
        val file = files.firstOrNull { it.first.endsWith(".mp3", true) }
            ?: files.firstOrNull { it.second.contains("mp3", true) || it.second.contains("mpeg", true) }
            ?: error("Arsip ini tidak menyediakan MP3 yang bisa diputar")
        downloadUrl(track, file.first)
    }

    suspend fun videoUrl(track: OnlineTrack): String? = withContext(Dispatchers.IO) {
        val file = metadataFiles(track).firstOrNull {
            it.first.endsWith(".mp4", true) || it.second.contains("mpeg4", true) || it.second.contains("h.264", true)
        } ?: return@withContext null
        downloadUrl(track, file.first)
    }

    private suspend fun metadataFiles(track: OnlineTrack): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val metadata = JSONObject(readUrl("$BASE/metadata/${track.identifier}"))
        val files = metadata.optJSONArray("files") ?: error("File media tidak ditemukan")
        buildList {
            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val name = file.optString("name")
                val format = file.optString("format")
                if (name.isNotBlank() && !name.contains("__ia_thumb")) add(name to format)
            }
        }
    }

    private fun downloadUrl(track: OnlineTrack, filename: String): String {
        val encoded = filename.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "$BASE/download/${track.identifier}/$encoded"
    }

    private fun readUrl(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MasterZ-Android/0.3")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Server katalog membalas HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

object LyricsClient {
    suspend fun fetch(artist: String, title: String): String = withContext(Dispatchers.IO) {
        val a = URLEncoder.encode(artist.trim(), "UTF-8")
        val t = URLEncoder.encode(title.trim(), "UTF-8")
        val connection = URL("https://api.lyrics.ovh/v1/$a/$t").openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Lirik belum tersedia untuk lagu ini")
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            body.optString("lyrics").takeIf { it.isNotBlank() } ?: error("Lirik belum tersedia")
        } finally {
            connection.disconnect()
        }
    }
}
