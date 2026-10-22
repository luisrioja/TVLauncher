package luisrioja.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends the remote's home-like buttons to this launcher instead of PatchWall or Google.
 * Every other key passes through untouched.
 */
class KeyService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in HOME_KEYS && event.scanCode !in HOME_SCAN_CODES) return false
        if (event.action == KeyEvent.ACTION_UP) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        val HOME_KEYS = setOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_EXPLORER)

        /**
         * Xiaomi RC (BT, Vendor_2717_Product_32b9), read with `getevent -lt`:
         * 172 = KEY_HOMEPAGE (house), 150 = KEY_WWW (Xiaomi/PatchWall button). Matched by
         * scan code because the vendor key layout, which we can't read, picks the key code.
         */
        val HOME_SCAN_CODES = setOf(172, 150)
    }
}
