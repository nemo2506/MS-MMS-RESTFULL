package com.miseservice.msmms.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.msmms.R
import com.miseservice.msmms.ui.components.dialogs.PermissionActionDialog
import com.miseservice.msmms.viewmodel.MainUiState
import java.util.Locale

@Composable
fun ApiNetworkSection(
    uiState: MainUiState,
    token: String,
    onRestPortInputChange: (String) -> Unit,
    onRestPortCommit: () -> Unit,
    onResetToken: () -> Unit,
    onCopy: (String, String) -> Unit,
    onRefreshNetwork: () -> Unit
) {
    val emphasizedLabelSize = 18.sp
    val mutedFieldColors = smsOvhMutedTextFieldColors()
    var showResetTokenDialog by rememberSaveable { mutableStateOf(false) }

    // Note: Le rafraîchissement est maintenant déclenché par le ViewModel lors du changement d'onglet
    // ou manuellement via une action utilisateur si nécessaire.

    if (showResetTokenDialog) {
        PermissionActionDialog(
            title = stringResource(R.string.reset_token_dialog_title),
            message = stringResource(R.string.reset_token_dialog_message),
            confirmLabel = stringResource(R.string.reset_token_dialog_confirm),
            onConfirm = {
                showResetTokenDialog = false
                onResetToken()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { showResetTokenDialog = false }
        )
    }

    Text(
        text = stringResource(R.string.api_network_info_title),
        fontSize = 16.5.sp,
        color = colorResource(id = R.color.smsovh_primary),
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))

    TextField(
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
        label = { Text(stringResource(R.string.connected_ip_label), fontSize = emphasizedLabelSize) },
        colors = mutedFieldColors
    )
    Spacer(Modifier.height(10.dp))

    val ssidValue = if (uiState.isNetworkLoading && uiState.wifiSsid == null) {
        ""
    } else {
        uiState.wifiSsid ?: stringResource(R.string.ssid_unavailable)
    }
    
    // Message d'avertissement si le SSID est masqué par manque de permissions précises ou GPS
    val ssidHint = when {
        uiState.wifiSsid == stringResource(R.string.ssid_unavailable) && !uiState.hasFineLocation -> 
            stringResource(R.string.ssid_hint_fine_location_required)
        uiState.wifiSsid == stringResource(R.string.ssid_unavailable) && !uiState.isLocationEnabledInSystem ->
            stringResource(R.string.ssid_hint_gps_required)
        else -> null
    }

    CopyableReadOnlyField(
        value = ssidValue,
        onCopy = { onCopy("ssid", ssidValue) },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRefreshNetwork() }
            ) {
                Text(stringResource(R.string.ssid_label), fontSize = emphasizedLabelSize)
                Spacer(modifier = Modifier.width(8.dp))
                if (uiState.isNetworkLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorResource(id = R.color.smsovh_primary)
                    )
                } else {
                    // Petite icône de refresh ou texte indicatif pour rafraîchir manuellement
                    Text(
                        text = " ↻",
                        color = colorResource(id = R.color.smsovh_primary),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        colors = mutedFieldColors
    )
    if (ssidHint != null) {
        Text(
            text = ssidHint,
            color = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )
    }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.api_endpoints_title),
            color = colorResource(id = R.color.smsovh_primary),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        if (uiState.isIpValid) {
            CopyableReadOnlyField(
                value = sendEndpoint,
                onCopy = { onCopy("send-endpoint", sendEndpoint) },
                label = { Text(stringResource(R.string.endpoint_send_label)) },
                colors = mutedFieldColors
            )
            Spacer(Modifier.height(10.dp))
            CopyableReadOnlyField(
                value = logsEndpoint,
                onCopy = { onCopy("logs-endpoint", logsEndpoint) },
                label = { Text(stringResource(R.string.endpoint_logs_label)) },
                colors = mutedFieldColors
            )
            Spacer(Modifier.height(10.dp))
            CopyableReadOnlyField(
                value = batteryEndpoint,
                onCopy = { onCopy("battery-endpoint", batteryEndpoint) },
                label = { Text(stringResource(R.string.endpoint_battery_label)) },
                colors = mutedFieldColors
            )
        } else {
            Text(
                text = stringResource(R.string.endpoint_wifi_hint),
                color = colorResource(id = R.color.white),
                fontSize = 15.sp
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.api_token_section_title),
        color = colorResource(id = R.color.smsovh_primary),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
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
                    modifier = Modifier.clickable { showResetTokenDialog = true }
                )
            }
        },
        colors = mutedFieldColors
    )

    Spacer(Modifier.height(10.dp))
    val locationValue = if (uiState.locationPermissionGranted && uiState.locationData != null) {
        "${String.format(Locale.getDefault(), "%.5f", uiState.locationData.first)}, ${String.format(
            Locale.getDefault(),
            "%.5f",
            uiState.locationData.second
        )}"
    } else if (!uiState.locationPermissionGranted) {
        stringResource(R.string.location_not_authorized)
    } else {
        stringResource(R.string.location_loading)
    }
    CopyableReadOnlyField(
        value = locationValue,
        onCopy = { onCopy("location", locationValue) },
        label = { Text(stringResource(R.string.location_label), fontSize = emphasizedLabelSize) },
        colors = mutedFieldColors
    )


    Spacer(Modifier.height(24.dp))
}

