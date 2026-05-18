package com.miseservice.smsovh.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miseservice.smsovh.R

@Composable
fun LocationPermissionRequiredDialog(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.location_permission_required_title)) },
        text = { Text(stringResource(R.string.location_permission_required_message)) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.allow))
            }
        },
        dismissButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings))
            }
        }
    )
}

