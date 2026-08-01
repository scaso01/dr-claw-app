package com.scaso.drclawapp.service

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.scaso.drclawapp.BuildConfig
import kotlinx.serialization.json.Json

/**
 * Exported receiver: lets a headless on-phone process (the proot `claude` agent, via
 * `am broadcast -a com.scaso.drclawapp.UI_ACTION ...`) drive other apps' UIs through the
 * accessibility service, and read the current screen hierarchy.
 *
 * Trust boundary: the `secret` extra must equal [BuildConfig.UI_BRIDGE_SECRET]. The receiver is
 * exported (any app could send to it), so the secret is the gate. HITL is enforced UPSTREAM on the
 * phone — the broadcast only fires after the user approved a `termux-dialog` — so authorized
 * actions execute directly (no in-app confirmation).
 *
 * ponytail: secret-extra gate. Upgrade to a signed-caller check only if it ever matters.
 *
 * Extras: op=tap|type|scroll|read, secret, viewId, text, inputText, forward(true/false).
 */
class UiActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val op = intent.getStringExtra("op")?.trim()?.lowercase() ?: return
        val secret = intent.getStringExtra("secret") ?: ""
        val configured = BuildConfig.UI_BRIDGE_SECRET
        if (configured.isBlank() || secret != configured) {
            Log.w(TAG, "UI_ACTION rejected: blank/mismatched secret (op=$op)")
            return
        }

        if (op == "read") {
            writeHierarchy(context)
            return
        }

        val dispatched = handle(
            op = op,
            secret = secret,
            viewId = intent.getStringExtra("viewId"),
            text = intent.getStringExtra("text"),
            inputText = intent.getStringExtra("inputText"),
            forward = intent.getStringExtra("forward")?.toBooleanStrictOrNull() ?: true,
            configuredSecret = configured,
            dispatch = { UiAccessibilityService.requestAction(it) },
        )
        if (!dispatched) Log.w(TAG, "UI_ACTION op not handled: $op")
    }

    /** Dump the live UI tree to public Downloads so the Termux-side process (other UID) can read it. */
    private fun writeHierarchy(context: Context) {
        val node = UiAccessibilityService.instance?.readHierarchy()
        if (node == null) {
            Log.w(TAG, "ui read: accessibility service not running / no active window — nothing written")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "ui read: needs Android 10+ (MediaStore.Downloads)")
            return
        }
        val jsonStr = json.encodeToString(UiNode.serializer(), node)
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            // Overwrite any prior copy (last-write-wins).
            resolver.delete(collection, "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(UI_READ_FILE))
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, UI_READ_FILE)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = resolver.insert(collection, values)
            if (uri == null) {
                Log.e(TAG, "ui read: MediaStore insert returned null")
                return
            }
            resolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray()) }
            Log.i(TAG, "ui read: wrote ${jsonStr.length} bytes to Download/$UI_READ_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "ui read: failed to write hierarchy", e)
        }
    }

    companion object {
        private const val TAG = "UiActionReceiver"

        /** Termux reads this back as /sdcard/Download/.drclaw-ui.json */
        const val UI_READ_FILE = ".drclaw-ui.json"

        private val json = Json { encodeDefaults = true }

        /**
         * Pure, Context-free parse + dispatch for tap/type/scroll — unit-testable without Android.
         * Returns true iff the secret matched and a valid action was dispatched.
         */
        fun handle(
            op: String,
            secret: String,
            viewId: String?,
            text: String?,
            inputText: String?,
            forward: Boolean,
            configuredSecret: String,
            dispatch: (UiActionRequest) -> Unit,
        ): Boolean {
            if (configuredSecret.isBlank() || secret != configuredSecret) return false
            val type = when (op.trim().lowercase()) {
                "tap" -> UiActionType.TAP
                "type" -> UiActionType.TYPE
                "scroll" -> UiActionType.SCROLL
                else -> return false
            }
            dispatch(
                UiActionRequest(
                    type = type,
                    viewId = viewId,
                    text = text,
                    inputText = inputText,
                    forward = forward,
                    confirmed = true, // HITL handled upstream (termux-dialog before the broadcast)
                )
            )
            return true
        }
    }
}
