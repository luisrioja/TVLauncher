package luisrioja.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tvInputs: TvInputManager
    private lateinit var inputsRow: LinearLayout
    private lateinit var appsRow: LinearLayout

    private var inputs: List<Input> = emptyList()
    private var apps: List<App> = emptyList()
    private var resumed = false
    private var inputsDirty = false
    private var appsDirty = true
    private var screenOnAt = 0L

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsDirty = true
            if (resumed) renderApps()
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOnAt = SystemClock.elapsedRealtime()
            if (resumed) switchAfterWake()
        }
    }

    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) = renderInputs()
        override fun onInputRemoved(inputId: String) = renderInputs()
        override fun onInputUpdated(inputId: String) = renderInputs()
        override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) = renderInputs()
        override fun onInputStateChanged(inputId: String, state: Int) = renderInputs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        prefs = Prefs(this)
        tvInputs = getSystemService(TvInputManager::class.java)
        inputsRow = findViewById(R.id.inputs)
        appsRow = findViewById(R.id.apps)

        tvInputs.registerCallback(inputCallback, Handler(Looper.getMainLooper()))
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))

        renderInputs()
        val start = startInputId()
        (inputsRow.getChildAt(inputs.indexOfFirst { it.id == start }.coerceAtLeast(0)) ?: appsRow).requestFocus()

        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        if (prefs.firstLaunchThisBoot(boot)) switchToStartInput()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (inputsDirty) renderInputs()
        if (appsDirty) renderApps()
        switchAfterWake()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        tvInputs.unregisterCallback(inputCallback)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(screenOnReceiver)
        super.onDestroy()
    }

    @Deprecated("Home screen: there is nothing to go back to")
    override fun onBackPressed() = Unit

    // --- Start input ---

    private fun startInputId(): String? = when (val saved = prefs.startInput) {
        null -> inputs.firstOrNull { it.title.contains("Apple", ignoreCase = true) }?.id
        "" -> null
        else -> saved
    }

    private fun switchToStartInput() {
        val id = startInputId() ?: return
        if (!openInput(id)) toast(R.string.input_failed)
    }

    /** Waking from standby lands on whatever was on top; if that is us, go to the start input. */
    private fun switchAfterWake() {
        if (screenOnAt == 0L) return
        val fresh = SystemClock.elapsedRealtime() - screenOnAt < WAKE_WINDOW_MS
        screenOnAt = 0L
        if (fresh) switchToStartInput()
    }

    // --- Rows ---

    private fun renderInputs() {
        inputs = loadInputs(this, tvInputs)
        if (!resumed && inputsRow.childCount > 0) {
            inputsDirty = true
            return
        }
        inputsDirty = false
        val start = startInputId()
        inputsRow.replaceTiles(inputs.map { input ->
            tile(inputContent(input, input.id == start), onClick = {
                if (!openInput(input.id)) toast(R.string.input_failed)
            }, onLongClick = { inputMenu(input) }).apply { alpha = if (input.connected) 1f else 0.45f }
        })
    }

    private fun renderApps(focus: Int? = null) {
        appsDirty = false
        apps = loadApps(this, prefs.hidden, prefs.order)
        val tiles = apps.map { app ->
            tile(appContent(app), onClick = { openApp(app.pkg) }, onLongClick = { appMenu(app) })
        } + tile(label(getString(R.string.settings), 18f), onClick = ::settingsMenu)
        appsRow.replaceTiles(tiles, focus)
    }

    /** Swaps a row's tiles, keeping focus at the same position (or at [focus]). */
    private fun ViewGroup.replaceTiles(tiles: List<View>, focus: Int? = null) {
        val focused = focus ?: indexOfChild(focusedChild).takeIf { it >= 0 }
        removeAllViews()
        tiles.forEach(::addView)
        if (focused != null && childCount > 0) getChildAt(focused.coerceIn(0, childCount - 1)).requestFocus()
    }

    // --- Menus (long press OK on the remote) ---

    private fun inputMenu(input: Input) {
        val isStart = input.id == startInputId()
        menu(input.title, listOf(getString(if (isStart) R.string.unset_start else R.string.set_start))) {
            prefs.startInput = if (isStart) "" else input.id
            renderInputs()
        }
    }

    private fun appMenu(app: App) {
        val items = listOf(getString(R.string.move_left), getString(R.string.move_right), getString(R.string.hide))
        menu(app.label, items) { which ->
            when (which) {
                0, 1 -> {
                    val order = apps.map { it.pkg }.toMutableList()
                    val to = (order.indexOf(app.pkg) + if (which == 0) -1 else 1).coerceIn(0, order.lastIndex)
                    order.remove(app.pkg)
                    order.add(to, app.pkg)
                    prefs.order = order
                    renderApps(focus = to)
                }
                else -> {
                    prefs.hidden = prefs.hidden + app.pkg
                    renderApps()
                }
            }
        }
    }

    private fun settingsMenu() {
        val hidden = prefs.hidden.size
        val items = listOf(getString(R.string.android_settings), getString(R.string.show_hidden, hidden))
        menu(getString(R.string.settings), items) { which ->
            if (which == 0) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } else if (hidden > 0) {
                prefs.hidden = emptySet()
                renderApps()
            }
        }
    }

    private fun menu(title: CharSequence, items: List<CharSequence>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which -> onPick(which) }
            .show()
    }

    private fun openApp(pkg: String) {
        val intent = launchIntent(pkg)
        if (intent == null) toast(R.string.app_failed) else startActivity(intent)
    }

    // --- Views ---

    private fun tile(content: View, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): View =
        FrameLayout(this).apply {
            // The vertical margin leaves room inside the row for the focus scale; the
            // scroll view clips anything that grows past the row's own bounds.
            layoutParams = LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)).apply {
                marginEnd = dp(TILE_GAP)
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            background = getDrawable(R.drawable.tile_bg)
            foreground = getDrawable(R.drawable.tile_focus)
            clipToOutline = true
            isFocusable = true
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
            setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) FOCUS_SCALE else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            }
        }

    private fun inputContent(input: Input, isStart: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(label(input.title, 20f, Color.WHITE))
        val sub = listOfNotNull(input.port, getString(R.string.on_start).takeIf { isStart })
        if (sub.isNotEmpty()) addView(label(sub.joinToString(" · "), 13f, Color.GRAY))
    }

    private fun appContent(app: App): View {
        if (app.banner) {
            return ImageView(this).apply {
                setImageDrawable(app.image)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = app.label
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ImageView(this@MainActivity).apply { setImageDrawable(app.image) }, dp(48), dp(48))
            addView(label(app.label, 14f))
        }
    }

    private fun label(text: String, sizeSp: Float, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        maxLines = 1
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        if (sizeSp >= 18f) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TILE_W = 200
        const val TILE_H = 112
        const val TILE_GAP = 20
        const val FOCUS_SCALE = 1.08f
        const val WAKE_WINDOW_MS = 5_000L
    }
}
