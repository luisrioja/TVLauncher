package luisrioja.tvlauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/**
 * An HDMI port. [id] is always the physical port, so a saved start input survives the
 * CEC device coming and going; [title] is the CEC device name ("Apple TV") when there is one.
 */
class Input(val id: String, val title: String, val port: String?, val connected: Boolean)

fun loadInputs(context: Context, tim: TvInputManager): List<Input> {
    val hdmi = tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
    val devices = hdmi.filter { it.parentId != null }.associateBy { it.parentId }
    fun label(info: TvInputInfo) = (info.loadCustomLabel(context) ?: info.loadLabel(context)).toString()

    return hdmi.filter { it.parentId == null }.map { port ->
        val device = devices[port.id]
        Input(
            id = port.id,
            title = label(device ?: port),
            port = device?.let { label(port) },
            connected = tim.getInputState(port.id) != TvInputManager.INPUT_STATE_DISCONNECTED,
        )
    }.sortedBy { it.port ?: it.title }
}

/** Hands the input to the TV's own live-TV app, which owns the HDMI passthrough. */
fun Activity.openInput(id: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
    true
} catch (e: ActivityNotFoundException) {
    false
}
