package jp.simplist.smmultiplayer.trial

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import jp.simplist.smmultiplayer.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Manages the 24-hour free trial lifecycle.
 *
 * Persistence strategy (meets the "no reset on reinstall AND no reset on
 * clear-app-data" requirement):
 *
 *  1. Trial start timestamp is stored in "trial_state" SharedPreferences.
 *  2. That SharedPreferences file is registered in backup_rules.xml, so it is
 *     auto-backed-up to Google Drive via Android Auto Backup and restored
 *     automatically on reinstall (same Google account).
 *  3. A SHA-256 hash of SSAID is stored alongside so that even if the backup
 *     is restored on a different device, the trial does not continue from
 *     a stranger's clock.
 *  4. If stored trial-start is missing (user went to "App info → Storage →
 *     Clear data" to try to reset the trial), we fall back to the OS-managed
 *     PackageInfo.firstInstallTime. That value is owned by PackageManager,
 *     NOT by our app's data directory, so it survives "clear data" and can
 *     only be reset by a full uninstall.
 *  5. SSAID persists across reinstalls on the same device+signing-key, so
 *     the device-hash check also catches the "uninstall + reinstall without
 *     backup restore" path when the device is unchanged.
 *
 * State machine:
 *  - First launch  -> NOT_STARTED -> ensureStarted() -> ACTIVE
 *  - After 24h     -> ACTIVE -> EXPIRED
 *  - After purchase -> ACTIVE/EXPIRED -> PURCHASED (permanent)
 */
class TrialManager private constructor(context: Context) {

    enum class State { NOT_STARTED, ACTIVE, EXPIRED, PURCHASED }

    private val appCtx = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_TRIAL, Context.MODE_PRIVATE)
    private val purchasePrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_PURCHASE, Context.MODE_PRIVATE)
    private val deviceHash: String = DeviceIdProvider.deviceHash(context)

    /**
     * Returns the OS-managed "first install time" for this package, which
     * survives clearing the app's data (it lives in PackageManager state,
     * not in our /data/data/<pkg> directory).
     */
    private fun firstInstallTimeMs(): Long = try {
        appCtx.packageManager
            .getPackageInfo(appCtx.packageName, 0)
            .firstInstallTime
    } catch (_: PackageManager.NameNotFoundException) {
        System.currentTimeMillis()
    }

    /** Automatically begin the trial on first launch. */
    fun ensureStarted() {
        if (isPurchased()) return
        val storedHash = prefs.getString(KEY_DEVICE_HASH, null)
        if (storedHash != null && storedHash != deviceHash) {
            // Backup restored onto a *different* device — treat as fresh trial
            resetForNewDevice()
        }
        if (prefs.getLong(KEY_TRIAL_START, 0L) == 0L) {
            // Anchor the trial to firstInstallTime, NOT currentTimeMillis().
            // If the user cleared our app data to try to reset the trial,
            // firstInstallTime still points at the original install and
            // trial_start will correctly resolve to the past.
            prefs.edit()
                .putLong(KEY_TRIAL_START, firstInstallTimeMs())
                .putString(KEY_DEVICE_HASH, deviceHash)
                .apply()
        }
    }

    /**
     * Returns the effective trial-start timestamp. Normally this is the value
     * stored in SharedPreferences, but as a defence-in-depth measure against
     * "Clear data" abuse we also consult PackageInfo.firstInstallTime and
     * take whichever is EARLIER.
     */
    private fun effectiveTrialStartMs(): Long {
        val stored = prefs.getLong(KEY_TRIAL_START, 0L)
        val installed = firstInstallTimeMs()
        return when {
            stored == 0L -> installed
            else -> minOf(stored, installed)
        }
    }

    fun state(): State {
        if (isPurchased()) return State.PURCHASED
        val start = effectiveTrialStartMs()
        if (start == 0L) return State.NOT_STARTED
        val elapsed = System.currentTimeMillis() - start
        return if (elapsed >= TRIAL_DURATION_MS) State.EXPIRED else State.ACTIVE
    }

    fun remainingMillis(): Long {
        if (isPurchased()) return Long.MAX_VALUE
        val start = effectiveTrialStartMs()
        if (start == 0L) return TRIAL_DURATION_MS
        return (start + TRIAL_DURATION_MS - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun remainingHours(): Int = TimeUnit.MILLISECONDS.toHours(remainingMillis()).toInt()
    fun remainingMinutes(): Int = TimeUnit.MILLISECONDS.toMinutes(remainingMillis()).toInt()

    fun isPurchased(): Boolean = purchasePrefs.getBoolean(KEY_PURCHASE_ACTIVE, false)

    /**
     * Debug builds default to bypass so the developer can keep testing after the
     * trial expires. Release builds respect this only when `trialBypass=true`
     * is set in gradle.properties (local machine); Play releases ship false.
     */
    fun isTrialBypass(): Boolean = BuildConfig.TRIAL_BYPASS

    fun canUsePlayback(): Boolean {
        if (isTrialBypass()) return true
        return when (state()) {
            State.ACTIVE, State.PURCHASED -> true
            State.NOT_STARTED, State.EXPIRED -> false
        }
    }

    /** Called by BillingManager on a verified successful purchase. */
    fun markPurchased(purchaseToken: String, orderId: String?) {
        purchasePrefs.edit()
            .putBoolean(KEY_PURCHASE_ACTIVE, true)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putString(KEY_PURCHASE_ORDER_ID, orderId)
            .putLong(KEY_PURCHASE_SINCE, System.currentTimeMillis())
            .apply()
    }

    /** Used only when BillingClient reports the purchase has been revoked/refunded. */
    fun clearPurchase() {
        purchasePrefs.edit()
            .putBoolean(KEY_PURCHASE_ACTIVE, false)
            .remove(KEY_PURCHASE_TOKEN)
            .remove(KEY_PURCHASE_ORDER_ID)
            .apply()
    }

    private fun resetForNewDevice() {
        prefs.edit()
            .remove(KEY_TRIAL_START)
            .putString(KEY_DEVICE_HASH, deviceHash)
            .apply()
    }

    companion object {
        private const val PREFS_TRIAL = "trial_state"
        private const val PREFS_PURCHASE = "purchase_state"
        private const val KEY_TRIAL_START = "trial_start_epoch_ms"
        private const val KEY_DEVICE_HASH = "device_hash"
        private const val KEY_PURCHASE_ACTIVE = "purchase_active"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_PURCHASE_ORDER_ID = "purchase_order_id"
        private const val KEY_PURCHASE_SINCE = "purchase_since_epoch_ms"

        private val TRIAL_DURATION_MS = TimeUnit.HOURS.toMillis(24)

        @Volatile private var INSTANCE: TrialManager? = null

        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = TrialManager(context.applicationContext).also {
                            it.ensureStarted()
                        }
                    }
                }
            }
        }

        fun get(): TrialManager = INSTANCE
            ?: error("TrialManager not initialized. Call TrialManager.initialize() in Application.onCreate().")
    }
}
