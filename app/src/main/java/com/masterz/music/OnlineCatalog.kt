package com.masterz.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val downloads: Long = 0L,
    val source: String = "Internet Archive"
)

object OnlineCatalog {
    private const val ARCHIVE = "https://archive.org"
    private const val AUDIUS = "https://api.audius.co/v1"
    private const val APP_NAME = "MasterZ"

    private val indonesiaTerms = listOf(
        "indonesia", "indonesian", "lagu indo", "pop indonesia", "indo pop",
        "dangdut", "koplo", "campursari", "gamelan", "musik jawa",
        "lagu jawa", "sunda", "bahasa indonesia", "musik indonesia"
    )

    suspend fun search(term: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val clean = term.trim()
        if (clean.isEmpty()) return@withContext emptyList()

        val audius = try {
            searchAudius(clean)
        } catch (_: Exception) {
            emptyList()
        }
        val archive = try {
            searchArchive(clean)
        } catch (_: Exception) {
            emptyList()
        }
        (audius + archive).distinctBy { "${it.source}:${it.identifier}" }.take(50)
    }

    suspend fun trending(): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val audius = try {
            fetchAudiusTrending()
        } catch (_: Exception) {
            emptyList()
        }
        val indonesiaSearches = try {
            coroutineScope {
                indonesiaTerms.map { term ->
                    async { try { searchAudius(term, 12) } catch (_: Exception) { emptyList() } }
                }.awaitAll().flatten()
            }
        } catch (_: Exception) {
            emptyList()
        }

        val indonesiaTracks = (audius + indonesiaSearches)
            .distinctBy { it.identifier }
            .filter { isIndonesianTrack(it) }
            .sortedByDescending { it.downloads }
            .take(30)

        if (indonesiaTracks.isNotEmpty()) return@withContext indonesiaTracks

        // Fallback: only open-licensed Indonesian-tagged archive items, never global tracks mislabeled as local.
        try {
            searchArchive("Indonesia OR Indonesian OR dangdut OR koplo OR gamelan").take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchAudius(query: String, limit: Int = 30): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val url = "$AUDIUS/tracks/search?query=${enc(query)}&limit=$limit&sort_method=popular&app_name=$APP_NAME"
        parseAudiusTracks(readUrl(url))
    }

    private suspend fun fetchAudiusTrending(): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val url = "$AUDIUS/tracks/trending?time=week&limit=100&app_name=$APP_NAME"
        parseAudiusTracks(readUrl(url))
    }

    private fun parseAudiusTracks(body: String): List<OnlineTrack> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                if (!item.optBoolean("is_streamable", true)) continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val user = item.optJSONObject("user")
                val artist = user?.optString("name").orEmpty().ifBlank {
                    user?.optString("handle").orEmpty().ifBlank { "Artis Audius" }
                }
                val tags = item.optString("tags")
                add(
                    OnlineTrack(
                        identifier = id,
                        title = item.optString("title").ifBlank { "Tanpa judul" },
                        artist = artist,
                        license = "Track publik Audius • cek ketentuan kreator",
                        downloads = item.optLong("play_count", 0L),
                        source = "Audius"
                    )
                )
            }
        }
    }

    private fun isIndonesianTrack(track: OnlineTrack): Boolean {
        val text = "${track.title} ${track.artist}".lowercase()
        return indonesiaTerms.any { text.contains(it) } ||
            listOf("indonesia", "indonesian", "dangdut", "koplo", "campursari", "gamelan", "jawa", "sunda")
                .any { track.license.contains(it, true) }
    }

    private suspend fun searchArchive(term: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val clean = term.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val safeTerm = clean.replace("\\", "\\\\").replace("\"", "\\\"")
        val rawQuery = "mediatype:audio AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain* OR licenseurl:*public-domain*) AND (title:\"$safeTerm\" OR contributor:\"$safeTerm\" OR subject:\"$safeTerm\")"
        fetchArchiveTracks(rawQuery, 30, false)
    }

    private suspend fun fetchArchiveTrending(): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val rawQuery = "mediatype:audio AND (licenseurl:*creativecommons.org* OR licenseurl:*publicdomain* OR licenseurl:*public-domain*)"
        fetchArchiveTracks(rawQuery, 20, true)
    }

    private fun fetchArchiveTracks(rawQuery: String, rows: Int, sortByDownloads: Boolean): List<OnlineTrack> {
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val sort = if (sortByDownloads) "&sort[]=downloads+desc" else ""
        val url = "$ARCHIVE/advancedsearch.php?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=contributor&fl[]=licenseurl&fl[]=downloads&rows=$rows&page=1&output=json$sort"
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
        if (track.source == "Audius") {
            return@withContext "$AUDIUS/tracks/${enc(track.identifier)}/stream?app_name=$APP_NAME"
        }
        val files = metadataFiles(track)
        val file = files.firstOrNull { it.first.endsWith(".mp3", true) }
            ?: files.firstOrNull { it.second.contains("mp3", true) || it.second.contains("mpeg", true) }
            ?: error("Arsip ini tidak menyediakan MP3 yang bisa diputar")
        archiveDownloadUrl(track, file.first)
    }

    suspend fun videoUrl(track: OnlineTrack): String? = withContext(Dispatchers.IO) {
        if (track.source == "Audius") return@withContext null
        val file = metadataFiles(track).firstOrNull {
            it.first.endsWith(".mp4", true) || it.second.contains("mpeg4", true) || it.second.contains("h.264", true)
        } ?: return@withContext null
        archiveDownloadUrl(track, file.first)
    }

    private suspend fun metadataFiles(track: OnlineTrack): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val metadata = JSONObject(readUrl("$ARCHIVE/metadata/${track.identifier}"))
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

    private fun archiveDownloadUrl(track: OnlineTrack, filename: String): String {
        val encoded = filename.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "$ARCHIVE/download/${track.identifier}/$encoded"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun readUrl(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "MasterZ-Android/0.4")
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
