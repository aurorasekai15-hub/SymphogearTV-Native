package com.symphogear.tv.extra

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.symphogear.tv.model.Category
import com.symphogear.tv.model.Channel
import com.symphogear.tv.model.DrmLicense
import com.symphogear.tv.model.Playlist

/**
 * Symphogear TV - channels.json converter
 * Mengkonversi format channels.json kustom ke format Playlist NontonTV
 *
 * Format channels.json:
 * {
 *   "channels": [
 *     {
 *       "id": 100,
 *       "name": "RCTI",
 *       "cat": "nasional",
 *       "url": "https://...",
 *       "drm": false,
 *       "drmType": "ClearKey",   // opsional
 *       "licUrl": "keyid:key",   // opsional, untuk ClearKey
 *       "ua": "Mozilla/5.0 ..."  // opsional
 *     }
 *   ]
 * }
 */
object SymphogearJsonConverter {
    private const val TAG = "SymphogearConverter"

    // Nama kategori Indonesia
    private val CAT_NAMES = mapOf(
        "nasional"      to "Nasional",
        "berita"        to "Berita",
        "hiburan"       to "Hiburan",
        "olahraga"      to "Olahraga",
        "internasional" to "Internasional",
        "jepang"        to "Jepang",
        "vision"        to "Vision+",
        "indihome"      to "IndiHome",
        "custom"        to "Custom"
    )

    fun convert(jsonString: String): Playlist? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject

            // Cek apakah ini format channels.json Symphogear
            if (!root.has("channels")) return null

            val channelsArray: JsonArray = root.getAsJsonArray("channels")
            val playlist = Playlist()
            val categoryMap = LinkedHashMap<String, ArrayList<Channel>>()
            val drmMap = LinkedHashMap<String, String>() // keyid:key -> licName

            for (element in channelsArray) {
                val obj = element.asJsonObject
                val name = obj.get("name")?.asString ?: continue
                val url  = obj.get("url")?.asString  ?: continue
                val cat  = obj.get("cat")?.asString  ?: "nasional"
                val hasDrm = obj.get("drm")?.asBoolean ?: false
                val ua   = obj.get("ua")?.asString

                val channel = Channel()
                channel.name = name

                // Tambahkan User-Agent ke URL kalau ada
                // ExoPlayer baca UA dari DrmLicense atau header custom
                // Kita encode UA ke dalam URL dengan format khusus
                channel.streamUrl = if (!ua.isNullOrBlank()) {
                    // Format: url|ua=UserAgent (diparse di PlayerActivity)
                    "$url|ua=${ua}"
                } else {
                    url
                }

                // Handle DRM ClearKey
                if (hasDrm) {
                    val licUrl = obj.get("licUrl")?.asString
                    if (!licUrl.isNullOrBlank()) {
                        // Format licUrl: "keyid:key"
                        // Jadikan nama DRM unik per key
                        val drmName = "clearkey_${licUrl.hashCode()}"
                        channel.drmName = drmName
                        if (!drmMap.containsKey(drmName)) {
                            drmMap[drmName] = licUrl
                        }
                    }
                }

                // Masukkan ke kategori
                val catKey = cat.lowercase()
                if (!categoryMap.containsKey(catKey)) {
                    categoryMap[catKey] = ArrayList()
                }
                categoryMap[catKey]?.add(channel)
            }

            // Build categories dengan urutan tetap
            val orderedCats = listOf("nasional","berita","hiburan","olahraga",
                "internasional","jepang","vision","indihome","custom")
            val categories = ArrayList<Category>()

            // Kategori urutan tetap dulu
            for (key in orderedCats) {
                if (categoryMap.containsKey(key)) {
                    val category = Category()
                    category.name = CAT_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
                    category.channels = categoryMap[key]
                    categories.add(category)
                }
            }
            // Kategori lain yang tidak ada di urutan
            for ((key, channels) in categoryMap) {
                if (!orderedCats.contains(key)) {
                    val category = Category()
                    category.name = key.replaceFirstChar { it.uppercase() }
                    category.channels = channels
                    categories.add(category)
                }
            }

            playlist.categories = categories

            // Build DRM licenses
            val drmLicenses = ArrayList<DrmLicense>()
            for ((name, keyPair) in drmMap) {
                val drm = DrmLicense()
                drm.name = name
                drm.url = keyPair // "keyid:key" - diparse di PlayerActivity
                drmLicenses.add(drm)
            }
            playlist.drmLicenses = drmLicenses

            Log.d(TAG, "Converted ${channelsArray.size()} channels, ${categories.size} categories")
            playlist

        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert Symphogear JSON: ${e.message}")
            null
        }
    }

    /**
     * Deteksi apakah string JSON adalah format Symphogear channels.json
     */
    fun isSymphogearFormat(jsonString: String): Boolean {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            root.has("channels") && !root.has("categories")
        } catch (e: Exception) {
            false
        }
    }
}
