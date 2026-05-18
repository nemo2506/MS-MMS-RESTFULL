package com.miseservice.smsovh.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.smsovh.R
import com.miseservice.smsovh.ui.components.dialogs.SmsPermissionDialog

@Composable
fun SendSmsSection(
    enabled: Boolean,
    hasSendSmsPermission: () -> Boolean,
    onRequestSmsPermission: () -> Unit,
    onSendSmsRequested: () -> Unit
) {
    val context = LocalContext.current
    val showSmsPermissionDialog = remember { mutableStateOf(false) }

    if (showSmsPermissionDialog.value) {
        SmsPermissionDialog(
            onConfirm = {
                showSmsPermissionDialog.value = false
                onRequestSmsPermission()
            },
            onDismiss = {
                showSmsPermissionDialog.value = false
                Toast.makeText(
                    context,
                    context.getString(R.string.permission_denied_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    Button(
        onClick = {
            if (hasSendSmsPermission()) {
                onSendSmsRequested()
            } else {
                showSmsPermissionDialog.value = true
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = smsOvhButtonColors()
    ) {
        Text(stringResource(R.string.send_sms), fontSize = 15.sp)
    }
    Spacer(Modifier.height(12.dp))
}

