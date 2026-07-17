package com.tarun.pontlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

data class TvApp(
    val label: String,
    val packageName: String,
    val art: ImageBitmap,
    val artIsBanner: Boolean,
)

/** All launchable TV apps (leanback first, then regular), excluding ourselves. */
fun loadTvApps(context: Context): List<TvApp> {
    val pm = context.packageManager
    val seen = LinkedHashMap<String, TvApp>()
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
    )
    for (intent in intents) {
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || seen.containsKey(pkg)) continue
            val banner: Drawable? = ri.activityInfo.loadBanner(pm)
                ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val art = banner ?: ri.loadIcon(pm)
            seen[pkg] = TvApp(
                label = ri.loadLabel(pm).toString(),
                packageName = pkg,
                art = art.toBitmap(
                    if (banner != null) 320 else 160,
                    if (banner != null) 180 else 160,
                ).asImageBitmap(),
                artIsBanner = banner != null,
            )
        }
    }
    return seen.values.sortedBy { it.label.lowercase() }
}

fun launchIntentFor(context: Context, packageName: String): Intent? {
    val pm = context.packageManager
    return pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
}

object Favorites {
    private const val PREFS = "launcher"
    private const val KEY = "favorites"

    private val DEFAULT_CANDIDATES = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "in.startv.hotstar",
        "com.lagradost.cloudstream3",
        "org.videolan.vlc",
    )

    fun load(context: Context): List<String>? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }

    fun save(context: Context, packages: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, packages.joinToString("\n"))
            .apply()
    }

    fun defaults(apps: List<TvApp>): List<String> =
        DEFAULT_CANDIDATES.filter { c -> apps.any { it.packageName == c } }
}

/**
 * Disk cache of the app list + art. Querying PackageManager and decoding
 * banners out of ~20 APKs takes seconds on this CPU; on cold start we render
 * the cached list instantly, then refresh from PackageManager in background.
 */
object AppCache {
    private fun dir(context: Context) = File(context.cacheDir, "apps").apply { mkdirs() }

    fun load(context: Context): List<TvApp>? = try {
        val d = dir(context)
        File(d, "meta.txt").takeIf { it.exists() }?.readLines()?.map { line ->
            val (pkg, label, banner) = line.split('\t')
            TvApp(
                label = label,
                packageName = pkg,
                art = BitmapFactory.decodeFile(File(d, "$pkg.png").path)!!.asImageBitmap(),
                artIsBanner = banner == "1",
            )
        }
    } catch (e: Exception) {
        null
    }

    fun save(context: Context, apps: List<TvApp>) {
        try {
            val d = dir(context)
            val meta = apps.joinToString("\n") {
                "${it.packageName}\t${it.label.replace('\t', ' ').replace('\n', ' ')}\t${if (it.artIsBanner) "1" else "0"}"
            }
            val metaFile = File(d, "meta.txt")
            if (metaFile.exists() && metaFile.readText() == meta) return
            d.listFiles()?.forEach { it.delete() }
            for (app in apps) {
                FileOutputStream(File(d, "${app.packageName}.png")).use {
                    app.art.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            metaFile.writeText(meta)
        } catch (e: Exception) {
            // Cache is best-effort; next cold start just does the slow path.
        }
    }
}

private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(b))
    return b
}
