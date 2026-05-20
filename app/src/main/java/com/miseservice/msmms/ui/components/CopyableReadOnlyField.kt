package com.miseservice.msmms.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextFieldColors
import com.miseservice.msmms.R

@Composable
fun CopyableReadOnlyField(
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    colors: TextFieldColors
) {
    TextField(
        value = value,
        onValueChange = {},
        label = label,
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        singleLine = true,
        enabled = true,
        trailingIcon = {
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy_to_clipboard_content_description)
                )
            }
        },
        colors = colors
    )
}
