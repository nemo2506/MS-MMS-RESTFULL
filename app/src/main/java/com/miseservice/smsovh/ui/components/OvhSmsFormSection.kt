package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.miseservice.smsovh.R

@Composable
fun OvhSmsFormSection(
    senderId: String,
    recipient: String,
    message: String,
    onSenderIdChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onMessageChange: (String) -> Unit
) {
    val formLabelSize = 15.sp

    Text(
        text = stringResource(R.string.config_ovh),
        fontSize = 16.5.sp,
        color = colorResource(id = R.color.smsovh_primary),
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = senderId,
        onValueChange = onSenderIdChange,
        label = { Text(stringResource(R.string.sender_id_hint), fontSize = formLabelSize) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = recipient,
        onValueChange = onRecipientChange,
        label = { Text(stringResource(R.string.recipient_hint), fontSize = formLabelSize) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = message,
        onValueChange = onMessageChange,
        label = { Text(stringResource(R.string.message_hint), fontSize = formLabelSize) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 8,
        minLines = 4,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(14.dp))
}

