package com.tarun.pontlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<TvApp>>(emptyList())
    private val favorites = mutableStateOf<List<String>>(emptyList())
    private val hasUsb = mutableStateOf(false)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reload()
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshUsb()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This IS the home screen — back does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        registerReceiver(mediaReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        })
        reload()
        refreshUsb()

        setContent {
            LauncherScreen(
                apps = apps.value,
                favorites = favorites.value,
                hasUsb = hasUsb.value,
                onLaunch = ::launch,
                onToggleFavorite = ::toggleFavorite,
                onAppInfo = ::openAppInfo,
                onUninstall = ::uninstall,
                onMoveFavorite = ::moveFavorite,
                onOpenUsb = ::openUsb,
                onSettings = { startSafely(Intent(Settings.ACTION_SETTINGS)) },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
        unregisterReceiver(mediaReceiver)
    }

    private fun reload() {
        thread {
            // Cold start: show the cached list instantly, then refresh for real.
            if (apps.value.isEmpty()) {
                AppCache.load(this)?.let { cached ->
                    runOnUiThread {
                        if (apps.value.isEmpty()) {
                            apps.value = cached
                            favorites.value = Favorites.load(this) ?: Favorites.defaults(cached)
                        }
                    }
                }
            }
            val loaded = loadTvApps(this)
            runOnUiThread {
                apps.value = loaded
                favorites.value = Favorites.load(this) ?: Favorites.defaults(loaded)
            }
            AppCache.save(this, loaded)
        }
    }

    private fun refreshUsb() {
        val sm = getSystemService(StorageManager::class.java)
        hasUsb.value = sm?.storageVolumes
            ?.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED } == true
    }

    private fun openUsb() {
        val vlc = launchIntentFor(this, "org.videolan.vlc")
        if (vlc != null) startSafely(vlc)
        else Toast.makeText(this, "VLC not installed", Toast.LENGTH_SHORT).show()
    }

    private fun launch(app: TvApp) {
        val intent = launchIntentFor(this, app.packageName)
        if (intent != null) startSafely(intent)
        else Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite(app: TvApp) {
        val current = favorites.value
        val updated =
            if (app.packageName in current) current - app.packageName
            else current + app.packageName
        favorites.value = updated
        Favorites.save(this, updated)
    }

    private fun moveFavorite(pkg: String, delta: Int) {
        val current = favorites.value.toMutableList()
        val i = current.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j < 0 || j >= current.size) return
        current[i] = current[j].also { current[j] = pkg }
        favorites.value = current
        Favorites.save(this, current)
    }

    private fun openAppInfo(app: TvApp) {
        startSafely(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            )
        )
    }

    private fun uninstall(app: TvApp) {
        startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open", Toast.LENGTH_SHORT).show()
        }
    }
}
