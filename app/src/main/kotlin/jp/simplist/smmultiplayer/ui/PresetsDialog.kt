package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.simplist.smmultiplayer.PlayerViewModel
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.data.LayoutPreset

@Composable
fun PresetsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var saveOpen by remember { mutableStateOf(false) }
    var loadConfirm by remember { mutableStateOf<LayoutPreset?>(null) }
    var deleteConfirm by remember { mutableStateOf<LayoutPreset?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_presets)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
                    .simpleVerticalScrollbar(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.preset_empty),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    presets.forEach { p ->
                        PresetRow(
                            preset = p,
                            onLoad = { loadConfirm = p },
                            onDelete = { deleteConfirm = p },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { saveOpen = true }) {
                Text(stringResource(R.string.preset_save_current))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )

    if (saveOpen) {
        SavePresetDialog(
            defaultName = viewModel.defaultPresetName(),
            onSave = { name ->
                viewModel.savePreset(name)
                saveOpen = false
            },
            onDismiss = { saveOpen = false },
        )
    }

    loadConfirm?.let { preset ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_load_preset_title),
            message = stringResource(R.string.dialog_load_preset_message, preset.name),
            confirmLabel = stringResource(R.string.preset_load),
            onConfirm = {
                viewModel.loadPreset(preset.id)
                loadConfirm = null
                // Close the outer presets dialog so the user immediately sees
                // the restored layout.
                onDismiss()
            },
            onDismiss = { loadConfirm = null },
        )
    }

    deleteConfirm?.let { preset ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_preset_title),
            message = stringResource(R.string.dialog_delete_preset_message, preset.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deletePreset(preset.id)
                deleteConfirm = null
            },
            onDismiss = { deleteConfirm = null },
        )
    }
}

@Composable
private fun PresetRow(
    preset: LayoutPreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    R.string.preset_summary,
                    preset.layoutMode,
                    preset.videoCount,
                ),
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onLoad) {
            Icon(Icons.Filled.PlayCircle, stringResource(R.string.preset_load))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
        }
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_save_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preset_save_dialog_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
