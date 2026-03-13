package com.symphogear.tv

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.symphogear.tv.adapter.CategoryAdapter
import com.symphogear.tv.adapter.SidebarAdapter
import com.symphogear.tv.databinding.ActivityMainBinding
import com.symphogear.tv.dialog.SearchDialog
import com.symphogear.tv.dialog.SettingDialog
import com.symphogear.tv.extension.*
import com.symphogear.tv.extra.*
import com.symphogear.tv.model.*

open class MainActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter
    private var sidebarAdapter: SidebarAdapter? = null
    private var fullPlaylist: Playlist? = null

    // Category name + icon mapping (sama dengan web version)
    private val catNames = mapOf(
        "nasional"      to "📺  NASIONAL",
        "berita"        to "📰  BERITA",
        "hiburan"       to "🎭  HIBURAN",
        "olahraga"      to "⚽  OLAHRAGA",
        "internasional" to "🌍  INTERNASIONAL",
        "jepang"        to "🗾  JEPANG",
        "vision+"       to "👁  VISION+ DRM",
        "indihome"      to "🏠  INDIHOME DRM",
        "custom"        to "🔗  CUSTOM",
        "favorit"       to "⭐  FAVORIT",
        "favorite"      to "⭐  FAVORIT"
    )

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> adapter.insertOrUpdateFavorite()
                REMOVE_FAVORITE -> adapter.removeFavorite()
            }
        }
    }

    companion object {
        const val MAIN_CALLBACK = "MAIN_CALLBACK"
        const val UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }
        binding.searchHint?.setOnClickListener { openSearch() }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else showAlertPlaylistError(getString(R.string.null_playlist))
    }

    private fun setLoadingPlaylist(show: Boolean) {
        if (show) {
            binding.loading.startShimmer()
            binding.loading.visibility = View.VISIBLE
        } else {
            binding.loading.stopShimmer()
            binding.loading.visibility = View.GONE
        }
    }

    private fun setupSidebar(playlistSet: Playlist) {
        val cats = playlistSet.categories
        sidebarAdapter = SidebarAdapter(cats) { cat, position ->
            // Update topbar title
            val key = cat.name?.lowercase()?.trim() ?: ""
            val matchedTitle = catNames.entries.firstOrNull { key.contains(it.key) }?.value
                ?: "📡  ${cat.name?.uppercase()}"
            binding.textCurrentCat?.text = matchedTitle

            // Scroll rv_category to that category position
            (binding.rvCategory.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
        }
        binding.rvSidebar.layoutManager = LinearLayoutManager(this)
        binding.rvSidebar.adapter = sidebarAdapter
    }

    @SuppressLint("SetTextI18n")
    private fun updateStats(playlistSet: Playlist) {
        try {
            val allChannels = playlistSet.categories
                .flatMap { it.channels ?: emptyList() }
            val total = allChannels.size
            val customCount = playlistSet.categories
                .filter { it.name?.lowercase()?.contains("custom") == true }
                .sumOf { it.channels?.size ?: 0 }

            binding.statTotal?.text = total.toString()
            binding.statCustom?.text = customCount.toString()

            // Update topbar title from first category
            val firstCat = playlistSet.categories.firstOrNull()
            if (firstCat != null) {
                val key = firstCat.name?.lowercase()?.trim() ?: ""
                val title = catNames.entries.firstOrNull { key.contains(it.key) }?.value
                    ?: "📡  ${firstCat.name?.uppercase()}"
                binding.textCurrentCat?.text = title
            }
        } catch (e: Exception) { }
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        if (preferences.sortCategory) playlistSet.sortCategories()
        if (preferences.sortChannel) playlistSet.sortChannels()
        playlistSet.trimChannelWithEmptyStreamUrl()

        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (preferences.sortFavorite) fav.sort()
        if (fav?.channels?.isNotEmpty() == true)
            playlistSet.insertFavorite(fav.channels)
        else playlistSet.removeFavorite()

        fullPlaylist = playlistSet
        adapter = CategoryAdapter(playlistSet.categories)
        binding.catAdapter = adapter

        Playlist.cached = playlistSet
        helper.writeCache(playlistSet)

        setupSidebar(playlistSet)
        updateStats(playlistSet)
        setLoadingPlaylist(false)
        Toast.makeText(applicationContext, R.string.playlist_updated, Toast.LENGTH_SHORT).show()

        if (preferences.playLastWatched && PlayerActivity.isFirst) {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayData.VALUE, preferences.watched)
            this.startActivity(intent)
        }
    }

    private fun updatePlaylist(useCache: Boolean) {
        setLoadingPlaylist(true)
        binding.catAdapter?.clear()
        val playlistSet = Playlist()

        SourcesReader().set(preferences.sources, object : SourcesReader.Result {
            override fun onError(source: String, error: String) {
                val snackbar = Snackbar.make(binding.root, "[${error.uppercase()}] $source", Snackbar.LENGTH_INDEFINITE)
                snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
                snackbar.show()
            }

            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
                else Toast.makeText(applicationContext, R.string.playlist_cant_be_parsed, Toast.LENGTH_SHORT).show()
            }

            override fun onFinish() {
                if (!playlistSet.isCategoriesEmpty()) setPlaylistToAdapter(playlistSet)
                else showAlertPlaylistError(getString(R.string.null_playlist))
            }
        }).process(useCache)
    }

    private fun showAlertPlaylistError(message: String?) {
        val alert = AlertDialog.Builder(this).apply {
            setTitle(R.string.alert_title_playlist_error)
            setMessage(message)
            setCancelable(false)
            setNeutralButton(R.string.settings) { _, _ -> openSettings() }
            setPositiveButton(R.string.dialog_retry) { _, _ -> updatePlaylist(true) }
        }
        val cache = helper.readCache()
        if (cache != null) {
            alert.setNegativeButton(R.string.dialog_cached) { _, _ -> setPlaylistToAdapter(cache) }
        }
        alert.create().show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> openSettings()
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    override fun onBackPressed() {
        if (isTelevision || doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finish()
            return
        }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_app), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun openSettings() {
        SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(), null)
    }
}
