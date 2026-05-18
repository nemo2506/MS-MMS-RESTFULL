package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.smsovh.R
import com.miseservice.smsovh.viewmodel.MainUiState
import java.util.Locale

@Composable
fun ApiNetworkSection(
    uiState: MainUiState,
    token: String,
    locationPermissionGranted: Boolean,
    locationData: Pair<Double, Double>?,
    onRestPortInputChange: (String) -> Unit,
    onRestPortCommit: () -> Unit,
    onResetToken: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    val emphasizedLabelSize = 18.sp

    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.api_network_info_title),
        fontSize = 16.5.sp,
        color = colorResource(id = R.color.smsovh_primary),
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.restPortInput,
        onValueChange = onRestPortInputChange,
        label = { Text(stringResource(R.string.rest_port_label), fontSize = emphasizedLabelSize) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            onRestPortCommit()
        }),
        isError = uiState.restPortError != null,
        colors = smsOvhTextFieldColors(),
        supportingText = {
            Text(uiState.restPortError ?: stringResource(R.string.rest_port_hint))
        }
    )
    Spacer(Modifier.height(10.dp))

    val ipValue = if (uiState.isIpValid) uiState.hostIp else stringResource(R.string.network_not_connected)
    CopyableReadOnlyField(
        value = ipValue,
        onCopy = { onCopy("connected-ip", ipValue) },
        label = { Text(stringResource(R.string.connected_ip_label), fontSize = emphasizedLabelSize) }
    )
    Spacer(Modifier.height(10.dp))

    val sendEndpoint = if (uiState.isIpValid) {
        "http://${uiState.hostIp}:${uiState.restPort}/api/send-message"
    } else {
        ""
    }
    val logsEndpoint = if (uiState.isIpValid) {
        "http://${uiState.hostIp}:${uiState.restPort}/api/logs"
    } else {
        ""
    }
    val batteryEndpoint = if (uiState.isIpValid) {
        "http://${uiState.hostIp}:${uiState.restPort}/api/battery"
    } else {
        ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.api_endpoints_title),
                color = colorResource(id = R.color.smsovh_primary),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            if (uiState.isIpValid) {
                CopyableReadOnlyField(
                    value = sendEndpoint,
                    onCopy = { onCopy("send-endpoint", sendEndpoint) },
                    label = { Text(stringResource(R.string.endpoint_send_label)) }
                )
                Spacer(Modifier.height(8.dp))
                CopyableReadOnlyField(
                    value = logsEndpoint,
                    onCopy = { onCopy("logs-endpoint", logsEndpoint) },
                    label = { Text(stringResource(R.string.endpoint_logs_label)) }
                )
                Spacer(Modifier.height(8.dp))
                CopyableReadOnlyField(
                    value = batteryEndpoint,
                    onCopy = { onCopy("battery-endpoint", batteryEndpoint) },
                    label = { Text(stringResource(R.string.endpoint_battery_label)) }
                )
            } else {
                Text(
                    text = stringResource(R.string.endpoint_wifi_hint),
                    color = colorResource(id = R.color.white),
                    fontSize = 15.sp
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    CopyableReadOnlyField(
        value = token,
        onCopy = { onCopy("token", token) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.api_token_label), fontSize = emphasizedLabelSize)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reset_token_label),
                    color = colorResource(id = R.color.smsovh_primary),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onResetToken() }
                )
            }
        }
    )

    Spacer(Modifier.height(10.dp))
    val locationValue = if (locationPermissionGranted && locationData != null) {
        "${String.format(Locale.getDefault(), "%.5f", locationData.first)}, ${String.format(
            Locale.getDefault(),
            "%.5f",
            locationData.second
        )}"
    } else if (!locationPermissionGranted) {
        stringResource(R.string.location_not_authorized)
    } else {
        stringResource(R.string.location_loading)
    }
    CopyableReadOnlyField(
        value = locationValue,
        onCopy = { onCopy("location", locationValue) },
        label = { Text(stringResource(R.string.location_label), fontSize = emphasizedLabelSize) }
    )


    Spacer(Modifier.height(24.dp))
}

