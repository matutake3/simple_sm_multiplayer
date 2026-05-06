package jp.simplist.smmultiplayer.trial

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Derives a stable, device-and-signing-key-scoped identifier from SSAID
 * (Settings.Secure.ANDROID_ID), then hashes it with a per-app salt so we never
 * store or transmit the raw SSAID itself.
 *
 * Why SSAID:
 *  - Persists across uninstall/reinstall of THIS app on the SAME device
 *    (same user + same app signing key on Android 8.0+).
 *  - Resets only on factory reset, which is acceptable for a trial window.
 *  - Requires no network round-trip.
 *
 * Security considerations:
 *  - The raw SSAID is PII under Google Play's policy. We only ever persist
 *    its SHA-256 hash with a static salt, never the raw value.
 *  - The hash is used only locally to verify "is the trial-start timestamp
 *    that Auto Backup restored really associated with THIS device?"
 *  - Nothing is sent to any external server.
 *
 * NOTE: SALT must remain stable forever. Changing it would be interpreted as a
 * "different device" for every existing user, resetting their trial state.
 */
object DeviceIdProvider {

    private const val SALT = "simplist-smmultiplayer-v1"

    @SuppressLint("HardwareIds")
    fun deviceHash(context: Context): String {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "fallback-no-ssaid"
        return sha256("$SALT|$raw")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
