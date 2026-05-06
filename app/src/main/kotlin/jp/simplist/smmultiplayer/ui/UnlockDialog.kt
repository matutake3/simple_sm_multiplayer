package jp.simplist.smmultiplayer.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.billing.BillingManager

/**
 * Trial-expired modal. Non-cancelable; user can only:
 *  - Buy        → BillingManager.launchPurchase(activity)
 *  - Restore    → BillingManager.queryPurchasesOnce()  (inline tertiary)
 *  - Close app  → finishes the host Activity            (dismissButton)
 *
 * On successful purchase the caller observes the trial state change and
 * pulls this dialog out of the composition.
 */
@Composable
fun UnlockDialog(
    billing: BillingManager,
    onCloseApp: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val price = billing.formattedPrice() ?: stringResource(R.string.price_fallback)

    AlertDialog(
        onDismissRequest = { /* non-cancelable */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.Inherit,
        ),
        title = { Text(stringResource(R.string.trial_expired_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.trial_expired_message, price))
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { billing.queryPurchasesOnce() }) {
                    Text(stringResource(R.string.purchase_restore))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (activity != null) billing.launchPurchase(activity)
                },
            ) {
                Text(stringResource(R.string.unlock_cta, price))
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseApp) {
                Text(stringResource(R.string.unlock_close_app))
            }
        },
    )
}
