package com.symphogear.tv.extension

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.symphogear.tv.extra.M3uTool
import com.symphogear.tv.model.*

fun List<M3U>?.toPlaylist(): Playlist? {
    if (this == null) return null
    val playlist = Playlist()

    // LinkedHashMap mempertahankan urutan insert (tidak acak seperti HashMap)
    val linkedMap = LinkedHashMap<String, ArrayList<Channel>>()

    val hashSet = HashSet<DrmLicense>()
    val drms = ArrayList<DrmLicense>()
    val cats = ArrayList<Category>()

    var category: Category?
    var ch: Channel?
    var drm: DrmLicense?

    for (item in this) {
        for (i in item.streamUrl!!.indices) {
            // hashset drm (disable same value)
            if (!item.licenseKey.isNullOrEmpty()) {
                drm = DrmLicense()
                drm.name = item.licenseName
                drm.url = item.licenseKey
                if (hashSet.none { d -> d.name == item.licenseName })
                    hashSet.add(drm)
            }

            // LinkedHashMap: map same groupname as key
            val map = linkedMap.getOrPut(item.groupName.toString()) { ArrayList() }
            ch = Channel()
            ch.name = if (i > 0) item.channelName + " #$i" else item.channelName
            ch.streamUrl = item.streamUrl!![i]
            ch.drmName = item.licenseName
            map.add(ch)
        }
    }

    // set map as categories (urutan terjaga)
    for (entry in linkedMap) {
        category = Category()
        category.name = entry.key
        category.channels = entry.value
        cats.add(category)
    }
    playlist.categories = cats

    // set drm licenses
    drms.addAll(hashSet)
    playlist.drmLicenses = drms

    return playlist
}

fun Playlist?.sortCategories() {
    this?.categories?.sortBy { category -> category.name?.lowercase() }
}

fun Playlist?.sortChannels() {
    if (this == null) return
    for (catId in this.categories.indices) {
        this.categories[catId].channels?.sortBy { channel -> channel.name?.lowercase() }
    }
}

fun Playlist?.trimChannelWithEmptyStreamUrl() {
    if (this == null) return
    for (catId in this.categories.indices) {
        this.categories[catId].channels!!.removeAll { channel -> channel.streamUrl.isNullOrBlank() }
    }
}

fun Playlist?.mergeWith(playlist: Playlist?) {
    if (playlist == null) return

    // Merge kategori: kalau nama sama -> gabung channels, jangan duplikat
    for (incomingCat in playlist.categories) {
        val existing = this?.categories?.firstOrNull {
            it.name?.trim()?.lowercase() == incomingCat.name?.trim()?.lowercase()
        }
        if (existing != null) {
            // kategori sudah ada -> tambahkan channels ke dalamnya
            existing.channels?.addAll(incomingCat.channels ?: ArrayList())
        } else {
            // kategori baru -> tambahkan kategorinya
            this?.categories?.add(incomingCat)
        }
    }

    // Merge DRM licenses (hindari duplikat nama)
    for (incomingDrm in playlist.drmLicenses) {
        if (this?.drmLicenses?.none { it.name == incomingDrm.name } == true) {
            this.drmLicenses.add(incomingDrm)
        }
    }
}

fun Playlist?.insertFavorite(channels: ArrayList<Channel>) {
    if (this == null) return
    if (this.categories[0].isFavorite())
        this.categories[0].channels = channels
    else
        this.categories.addFavorite(channels)
}

fun Playlist?.removeFavorite() {
    if (this == null) return
    if (this.categories[0].isFavorite())
        this.categories.removeAt(0)
}

fun String?.toPlaylist(): Playlist? {
    // Coba Symphogear channels.json format dulu
    try {
        if (com.symphogear.tv.extra.SymphogearJsonConverter.isSymphogearFormat(this ?: "")) {
            val result = com.symphogear.tv.extra.SymphogearJsonConverter.convert(this ?: "")
            if (result != null && !result.isCategoriesEmpty()) return result
        }
    } catch (e: Exception) { e.printStackTrace() }

    // trying to parse NontonTV json format
    try { return Gson().fromJson(this, Playlist::class.java) }
    catch (e: JsonParseException) { e.printStackTrace() }

    // if not json then m3u
    try { return M3uTool.parse(this).toPlaylist() }
    catch (e: Exception) { e.printStackTrace() }

    // content cant be parsed
    return null
}

fun Playlist?.isCategoriesEmpty(): Boolean {
    return this?.categories?.isEmpty() == true
}
// Symphogear TV: parse format channels.json kustom
fun String?.toSymphogearPlaylist(): Playlist? {
    if (this.isNullOrBlank()) return null
    return com.symphogear.tv.extra.SymphogearJsonConverter.convert(this)
}
