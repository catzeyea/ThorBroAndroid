package dev.catzeyea.thorbro.android.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * "Is a PC connected?" from the USB state. Android sends the sticky broadcast
 * `android.hardware.usb.action.USB_STATE`; `configured = true` means a USB host (the PC) has set up
 * the data connection. A charger alone never configures USB.
 *
 * Note: the battery's "plugged" value is no help here, the Thor reports AC while it is on the PC's
 * USB port.
 */
object PcConnection {

    /** Hidden constant UsbManager.ACTION_USB_STATE. */
    const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    /** True / false, or null if the state can't be read (then the UI must not claim either). */
    fun fromIntent(intent: Intent?): Boolean? {
        if (intent == null || !intent.hasExtra("connected")) return null
        return intent.getBooleanExtra("connected", false) && intent.getBooleanExtra("configured", false)
    }

    /** Current state and a receiver for changes. Unregister the returned receiver when done. */
    fun register(context: Context, onChange: (Boolean?) -> Unit): Pair<Boolean?, BroadcastReceiver> {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange(fromIntent(intent))
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(ACTION_USB_STATE), Context.RECEIVER_EXPORTED)
        return fromIntent(sticky) to receiver
    }
}
