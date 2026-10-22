package luisrioja.tvlauncher

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Input id to switch to on boot and wake. null = not chosen yet (guess Apple TV), "" = none. */
    var startInput: String?
        get() = sp.getString("start_input", null)
        set(value) = sp.edit().putString("start_input", value).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", DEFAULT_HIDDEN)!!.toSet()
        set(value) = sp.edit().putStringSet("hidden", value).apply()

    var order: List<String>
        get() = sp.getString("order", "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = sp.edit().putString("order", value.joinToString(",")).apply()

    /**
     * True on the first launch after a reboot. The very first run only records the boot,
     * so installing the launcher doesn't jump straight to an HDMI input.
     */
    fun firstLaunchThisBoot(bootCount: Int): Boolean {
        val seen = sp.contains("boot")
        if (seen && sp.getInt("boot", -1) == bootCount) return false
        sp.edit().putInt("boot", bootCount).apply()
        return seen
    }

    private companion object {
        /** Xiaomi/Google system apps and the old launchers, hidden until "Mostrar apps ocultas". */
        val DEFAULT_HIDDEN = setOf(
            "com.android.vending",
            "com.google.android.play.games",
            "com.google.android.videos",
            "com.mediatek.wwtv.tvcenter",
            "com.mitv.dream",
            "com.mitv.gallery",
            "com.mitv.tvhome.atv",
            "com.mitv.tvhome.mitvplus",
            "com.spocky.projengmenu",
            "com.xiaomi.amazon.alexa",
            "com.xiaomi.mitv.mediaexplorer",
            "com.xiaomi.mitv.smartshare",
            "com.xiaomi.mitv.tvmanager",
            "flar2.homebutton",
        )
    }
}
