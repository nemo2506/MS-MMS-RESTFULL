package com.miseservice.msmms.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miseservice.msmms.R

@Composable
fun BluetoothPermissionDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionActionDialog(
        title = stringResource(R.string.permission_required_title),
        message = stringResource(R.string.bluetooth_permission_denied_message),
        confirmLabel = stringResource(R.string.settings),
        onConfirm = onOpenSettings,
        dismissLabel = stringResource(R.string.cancel),
        onDismiss = onDismiss
    )
}

