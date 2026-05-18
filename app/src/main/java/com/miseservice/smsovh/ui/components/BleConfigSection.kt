package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.smsovh.R

@Composable
fun BleConfigSection(
    batteryMinPercent: Int = 20,
    batteryMaxPercent: Int = 80,
    isConnected: Boolean = false,
    relayEnabled: Boolean = false,
    wifiEnabled: Boolean = false,
    connectionStatus: String = "",
    errorMessage: String? = null,
    onBatteryMinChange: (Int) -> Unit = {},
    onBatteryMaxChange: (Int) -> Unit = {},
    onBatteryMinCommit: () -> Unit = {},
    onBatteryMaxCommit: () -> Unit = {},
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onRelaySwitchChange: (Boolean) -> Unit = {},
    onWifiSwitchChange: (Boolean) -> Unit = {}
) {
    val labelSize = 15.sp
    val sectionSpacing = 14.dp
    val connected = isConnected
    val resolvedConnectionStatus = connectionStatus.ifBlank {
        stringResource(R.string.bluetooth_status_disconnected)
    }
    var minInput by rememberSaveable { mutableStateOf(batteryMinPercent.toString()) }
    var maxInput by rememberSaveable { mutableStateOf(batteryMaxPercent.toString()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(batteryMinPercent) {
        if (minInput != batteryMinPercent.toString()) {
            minInput = batteryMinPercent.toString()
        }
    }
    LaunchedEffect(batteryMaxPercent) {
        if (maxInput != batteryMaxPercent.toString()) {
            maxInput = batteryMaxPercent.toString()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = colorResource(id = R.color.smsovh_primary),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.bluetooth_section_title),
            fontSize = 16.5.sp,
            color = colorResource(id = R.color.smsovh_primary),
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(12.dp))

    if (!errorMessage.isNullOrBlank()) {
        Text(
            text = errorMessage,
            color = colorResource(id = R.color.white),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(sectionSpacing))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (connected) colorResource(id = R.color.smsovh_primary) else colorResource(id = R.color.white),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = resolvedConnectionStatus,
                color = colorResource(id = R.color.white),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (connected) stringResource(R.string.bluetooth_action_disconnect) else stringResource(R.string.bluetooth_action_connect),
                color = colorResource(id = R.color.white),
                fontSize = 13.sp
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = connected,
                onCheckedChange = { isChecked -> if (isChecked) onConnect() else onDisconnect() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorResource(id = R.color.smsovh_primary),
                    checkedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
                    uncheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.85f),
                    uncheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.25f)
                )
            )
        }
    }

    Spacer(Modifier.height(sectionSpacing))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.bluetooth_relay_label),
            color = colorResource(id = R.color.white),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Switch(
            checked = relayEnabled,
            onCheckedChange = onRelaySwitchChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorResource(id = R.color.smsovh_primary),
                checkedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
                uncheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.85f),
                uncheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.25f)
            )
        )
    }

    Spacer(Modifier.height(sectionSpacing))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.bluetooth_wifi_label),
            color = colorResource(id = R.color.white),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Switch(
            checked = wifiEnabled,
            onCheckedChange = onWifiSwitchChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorResource(id = R.color.smsovh_primary),
                checkedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
                uncheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.85f),
                uncheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.25f)
            )
        )
    }

    Spacer(Modifier.height(sectionSpacing))

     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.spacedBy(12.dp),
         verticalAlignment = Alignment.CenterVertically
     ) {
         OutlinedTextField(
             value = minInput,
             onValueChange = { value ->
                 val digits = value.filter { it.isDigit() }.take(3)
                 minInput = digits
                 digits.toIntOrNull()?.let { parsed ->
                     onBatteryMinChange(parsed.coerceIn(0, 100))
                 }
             },
             label = { Text(stringResource(R.string.bluetooth_battery_min_label), fontSize = labelSize) },
             modifier = Modifier.weight(1f),
             singleLine = true,
             keyboardOptions = KeyboardOptions(
                 keyboardType = KeyboardType.Number,
                 imeAction = ImeAction.Done
             ),
             keyboardActions = KeyboardActions(
                 onDone = {
                     onBatteryMinCommit()
                     focusManager.clearFocus()
                 }
             ),
             colors = smsOvhTextFieldColors()
         )

         OutlinedTextField(
             value = maxInput,
             onValueChange = { value ->
                 val digits = value.filter { it.isDigit() }.take(3)
                 maxInput = digits
                 digits.toIntOrNull()?.let { parsed ->
                     onBatteryMaxChange(parsed.coerceIn(0, 100))
                 }
             },
             label = { Text(stringResource(R.string.bluetooth_battery_max_label), fontSize = labelSize) },
             modifier = Modifier.weight(1f),
             singleLine = true,
             keyboardOptions = KeyboardOptions(
                 keyboardType = KeyboardType.Number,
                 imeAction = ImeAction.Done
             ),
             keyboardActions = KeyboardActions(
                 onDone = {
                     onBatteryMaxCommit()
                     focusManager.clearFocus()
                 }
             ),
             colors = smsOvhTextFieldColors()
         )
     }

    Spacer(Modifier.height(sectionSpacing))
}
