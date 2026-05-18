package com.miseservice.msmms.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.msmms.R
import com.miseservice.msmms.ui.components.dialogs.SmsPermissionDialog

@Composable
fun SendSmsSection(
    enabled: Boolean,
    hasSendSmsPermission: () -> Boolean,
    onRequestSmsPermission: () -> Unit,
    onSendSmsRequested: () -> Unit
) {
    val context = LocalContext.current
    val showSmsPermissionDialog = remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value
    val buttonScale = animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "sendSmsButtonScale"
    ).value

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
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        enabled = enabled,
        colors = smsOvhButtonColors(),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(0.dp)
    ) {
        Text(stringResource(R.string.send_sms), fontSize = 15.sp)
    }
    Spacer(Modifier.height(12.dp))
}

