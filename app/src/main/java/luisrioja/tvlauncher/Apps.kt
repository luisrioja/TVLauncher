package luisrioja.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** A launchable TV app. [banner] is false when the app only ships a square icon. */
class App(val pkg: String, val label: String, val image: Drawable, val banner: Boolean)

fun loadApps(context: Context, hidden: Set<String>, order: List<String>): List<App> {
    val pm = context.packageManager
    val position = order.withIndex().associate { (i, pkg) -> pkg to i }
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

    return pm.queryIntentActivities(query, 0)
        .map { it.activityInfo }
        .filter { it.packageName != context.packageName && it.packageName !in hidden }
        .distinctBy { it.packageName }
        .map { info ->
            val banner = info.loadBanner(pm)
            App(info.packageName, info.loadLabel(pm).toString(), banner ?: info.loadIcon(pm), banner != null)
        }
        .sortedWith(compareBy({ position[it.pkg] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
}

fun Context.launchIntent(pkg: String): Intent? =
    packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
