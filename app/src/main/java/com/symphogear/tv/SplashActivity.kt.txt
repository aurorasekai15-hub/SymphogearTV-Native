package com.symphogear.tv

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.symphogear.tv.databinding.ActivitySplashBinding
import com.symphogear.tv.extension.*
import com.symphogear.tv.extra.*
import com.symphogear.tv.model.GithubUser
import com.symphogear.tv.model.Playlist
import com.symphogear.tv.model.Release
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val preferences = Preferences()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PlayerActivity.isPipMode) {
            val intent = Intent(this, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            this.startActivity(intent)
            this.finish()
            return
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animasi ring berputar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.root.post {
                binding.root.findViewById<android.view.View>(R.id.ring1)?.let { startRingAnimation(it, 14000L, false) }
                binding.root.findViewById<android.view.View>(R.id.ring2)?.let { startRingAnimation(it, 9000L, true) }
                binding.root.findViewById<android.view.View>(R.id.ring3)?.let { startRingAnimation(it, 18000L, false) }
            }
        }

        // Load karakter images
        loadCharacterImages()

        // Animasi karakter pop-in satu per satu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val charViews = listOf(R.id.charHibiki, R.id.charTsubasa, R.id.charChris,
                                   R.id.charKirika, R.id.charShirabe, R.id.charMaria)
            charViews.forEachIndexed { i, id ->
                val v = binding.root.findViewById<android.view.View>(id)
                v?.alpha = 0f
                v?.scaleX = 0f
                v?.scaleY = 0f
                v?.rotation = -180f
                v?.postDelayed({
                    v.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                        .setDuration(450).setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                        .start()
                }, 1500L + i * 200L)
            }
        }

        // Animate loading bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            animateLoadingBar()
        }

        binding.textPresents.setOnClickListener {
            openWebsite(getString(R.string.telegram_group))
        }
        binding.textTitle.setOnClickListener {
            openWebsite(getString(R.string.website))
        }
        binding.textUsers.text = preferences.contributors

        // update constributors
        HttpClient(true)
            .create(getString(R.string.gh_contributors).toRequest())
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("HttpClient", "Could not get contributors!", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val content = response.body()?.string()
                        if (content.isNullOrBlank()) throw Exception("null content")
                        if (!response.isSuccessful) throw Exception(response.message())
                        val ghUsers = Gson().fromJson(content, Array<GithubUser>::class.java)
                        val users = ghUsers.toStringContributor()
                        preferences.contributors = users
                        setContributors(users)
                    } catch (e: Exception) {
                        Log.e("HttpClient", "Could not get contributors!", e)
                    }
                }
            })

        // first time alert
        if (preferences.isFirstTime) {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.app_name)
                setMessage(R.string.alert_first_time)
                setCancelable(false)
                setPositiveButton(android.R.string.ok) { di,_ ->
                    preferences.isFirstTime = false
                    prepareWhatIsNeeded()
                    di.dismiss()
                }
                setNeutralButton(R.string.button_website) { _,_ ->
                    preferences.isFirstTime = false
                    openWebsite(getString(R.string.website))
                    finish()
                }
                create()
                show()
            }
        }
        else prepareWhatIsNeeded()
    }

    private fun loadCharacterImages() {
        val chars = listOf(
            R.id.imgHibiki to R.raw.char_hibiki,
            R.id.imgTsubasa to R.raw.char_tsubasa,
            R.id.imgChris to R.raw.char_chris,
            R.id.imgKirika to R.raw.char_kirika,
            R.id.imgShirabe to R.raw.char_shirabe,
            R.id.imgMaria to R.raw.char_maria
        )
        for ((viewId, rawId) in chars) {
            try {
                val iv = binding.root.findViewById<android.widget.ImageView>(viewId) ?: continue
                val bm = android.graphics.BitmapFactory.decodeStream(resources.openRawResource(rawId))
                iv.setImageBitmap(bm)
            } catch (e: Exception) {
                android.util.Log.e("Splash", "Failed to load char image: $e")
            }
        }
    }

    private fun animateLoadingBar() {
        val bar = binding.root.findViewById<android.view.View>(R.id.loadingBar) ?: return
        val dot = binding.root.findViewById<android.view.View>(R.id.loadDot)
        val track = bar.parent as? android.view.View
        bar.post {
            val fullWidth = (bar.parent as android.view.View).width.toFloat()
            val anim = android.animation.ValueAnimator.ofFloat(0f, fullWidth)
            anim.duration = 2200L
            anim.startDelay = 2800L
            anim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            anim.addUpdateListener { va ->
                val w = va.animatedValue as Float
                bar.layoutParams.width = w.toInt()
                bar.requestLayout()
                // Move dot along bar
                dot?.translationX = w - 4f
            }
            anim.start()
        }
    }

    private fun startRingAnimation(view: android.view.View, duration: Long, reverse: Boolean) {
        val endDeg = if (reverse) -360f else 360f
        val anim = android.animation.ObjectAnimator.ofFloat(view, "rotation", 0f, endDeg)
        anim.duration = duration
        anim.repeatCount = android.animation.ValueAnimator.INFINITE
        anim.interpolator = android.view.animation.LinearInterpolator()
        anim.start()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // check new release
        checkNewRelease()

        if (requestCode != 260621) return
        if (!grantResults.contains(PackageManager.PERMISSION_DENIED)) return
        Toast.makeText(this, getString(R.string.must_allow_permissions), Toast.LENGTH_LONG).show()
    }

    private fun prepareWhatIsNeeded() {
        // ask to grant all permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setStatus(R.string.status_checking_permission)
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            var passes = true
            for (perm in permissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permissions, 260621)
                    passes = false
                    break
                }
            }
            if (!passes) return
        }

        // check new release
        checkNewRelease()
    }

    private fun checkNewRelease() {
        // start checking
        setStatus(R.string.status_checking_new_update)
        val request = getString(R.string.json_release).toRequest()
        HttpClient(true).create(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HttpClient", "Could not check new update!", e)
                lunchMainActivity()
            }

            override fun onResponse(call: Call, response: Response) {
                val content = response.body()?.string()
                if (!response.isSuccessful || content.isNullOrBlank()) {
                    Log.e("HttpClient", "Could not check new update! ${response.message()}")
                    return lunchMainActivity()
                }
                val release = Gson().fromJson(content, Release::class.java)
                if (release.versionCode <= BuildConfig.VERSION_CODE ||
                    release.versionCode <= preferences.ignoredVersion) {
                    return lunchMainActivity()
                }

                val message = StringBuilder(String.format(getString(R.string.message_update),
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                        release.versionName, release.versionCode))

                for (log in release.changelog) {
                    message.append(String.format(getString(R.string.message_update_changelog), log))
                }
                if (release.changelog.isEmpty()) {
                    message.append(getString(R.string.message_update_no_changelog))
                }

                val downloadUrl = if (release.downloadUrl.isBlank()) {
                    String.format(getString(R.string.apk_release),
                        release.versionName, release.versionName, release.versionCode)
                }
                else release.downloadUrl

                AlertDialog.Builder(applicationContext).apply {
                    setTitle(R.string.alert_new_update); setMessage(message)
                    setCancelable(false)
                    setPositiveButton(R.string.dialog_download) { _,_ ->
                        downloadFile(downloadUrl); lunchMainActivity()
                    }
                    setNegativeButton(R.string.dialog_ignore) { _, _ ->
                        preferences.ignoredVersion = release.versionCode; lunchMainActivity()
                    }
                    setNeutralButton(R.string.button_website) { _,_ ->
                        openWebsite(getString(R.string.website)); lunchMainActivity()
                    }
                    create(); show()
                }
            }
        })
    }

    private fun setContributors(users: String?) {
        runOnUiThread {
            binding.textUsers.text = users
        }
    }

    private fun setStatus(resid: Int) {
        runOnUiThread {
            binding.textStatus.setText(resid)
        }
    }

    private fun lunchMainActivity() {
        val playlistSet = Playlist()
        setStatus(R.string.status_preparing_playlist)
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                runOnUiThread {
                    val message = "Source: $source, Error: $error"
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
            }
            override fun onFinish() {
                Playlist.cached = playlistSet
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(applicationContext, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }, 2000)
            }
        }).process(true)
    }

    private fun openWebsite(link: String) {
        startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(link)))
    }

    private fun downloadFile(url: String) {
        try {
            val uri = Uri.parse(url)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.lastPathSegment)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            dm.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}