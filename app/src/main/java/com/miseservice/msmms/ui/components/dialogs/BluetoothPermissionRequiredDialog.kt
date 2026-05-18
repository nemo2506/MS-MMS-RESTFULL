package com.miseservice.msmms.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miseservice.msmms.R

@Composable
fun BluetoothPermissionRequiredDialog(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    PermissionActionDialog(
        title = stringResource(R.string.bluetooth_permission_required_title),
        message = stringResource(R.string.bluetooth_permission_required_message),
        confirmLabel = stringResource(R.string.allow),
        onConfirm = onAllow,
        dismissLabel = stringResource(R.string.settings),
        onDismiss = onOpenSettings,
        onDismissRequest = {}
    )
}

