package com.miseservice.msmms.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miseservice.msmms.R

@Composable
fun SmsPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionActionDialog(
        title = stringResource(R.string.permission_required_title),
        message = stringResource(R.string.permission_required_message),
        confirmLabel = stringResource(R.string.allow),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.cancel),
        onDismiss = onDismiss
    )
}

