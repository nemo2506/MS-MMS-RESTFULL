package com.miseservice.smsovh.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.miseservice.smsovh.R
import com.miseservice.smsovh.model.BleRuntimeConfig
import com.miseservice.smsovh.model.BleDeviceState
import com.miseservice.smsovh.model.BleRelayState
import com.miseservice.smsovh.ui.components.ApiNetworkSection
import com.miseservice.smsovh.ui.components.BleConfigSection
import com.miseservice.smsovh.ui.components.MainHeader
import com.miseservice.smsovh.ui.components.OvhApiConfigSection
import com.miseservice.smsovh.ui.components.OvhSmsFormSection
import com.miseservice.smsovh.ui.components.SendSmsSection
import com.miseservice.smsovh.ui.components.ServiceStatusRow
import com.miseservice.smsovh.ui.components.smsOvhButtonColors
import com.miseservice.smsovh.ui.components.smsOvhTextFieldColors
import com.miseservice.smsovh.ui.components.dialogs.LocationPermissionDeniedDialog
import com.miseservice.smsovh.ui.components.dialogs.LocationPermissionRequiredDialog
import com.miseservice.smsovh.util.NetworkInfoProvider
import com.miseservice.smsovh.viewmodel.FeedbackType
import com.miseservice.smsovh.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private data class MainTabItem(
    val title: String,
    val icon: ImageVector,
    val contentDescription: String
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasSendSmsPermission: () -> Boolean,
    onRequestSmsPermission: () -> Unit,
    onSendSmsRequested: () -> Unit
) {
    fun withStatusPrefix(message: String, type: FeedbackType): String {
        val trimmed = message.trim()
        return when (type) {
            FeedbackType.SUCCESS -> if (trimmed.startsWith("✅")) trimmed else "✅ $trimmed"
            FeedbackType.ERROR -> if (trimmed.startsWith("❌")) trimmed else "❌ $trimmed"
            else -> trimmed
        }
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState = viewModel.uiState.collectAsState().value
    val showLocationDeniedDialog = remember { mutableStateOf(false) }
    val currentContext = rememberUpdatedState(LocalContext.current)
    var showBlePinDialog by rememberSaveable { mutableStateOf(false) }
    var blePinInput by rememberSaveable { mutableStateOf("") }

    val copyToClipboard: (String, String) -> Unit = remember {
        { label, value ->
            Log.d("CLIPBOARD", "MainScreen START")
            val ctx = currentContext.value
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

            Log.d("CLIPBOARD", "MainScreen: $label, $value")
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                val messageRes = when (label) {
                    "send-endpoint" -> R.string.endpoint_send_copied
                    "logs-endpoint" -> R.string.endpoint_logs_copied
                    "battery-endpoint" -> R.string.endpoint_battery_copied
                    "connected-ip" -> R.string.connected_ip_copied
                    "token" -> R.string.token_copied
                    "location" -> R.string.location_copied
                    "network" -> R.string.network_copied
                    else -> R.string.endpoint_copied
                }
                Toast.makeText(
                    ctx,
                    withStatusPrefix(ctx.getString(messageRes), FeedbackType.SUCCESS),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Champs du formulaire SMS pilotés par le ViewModel
    val senderId = uiState.senderId
    val recipient = uiState.recipient
    val message = uiState.message
    val tabs = listOf(
        MainTabItem(
            title = stringResource(R.string.tab_compose_plus),
            icon = Icons.Outlined.AddCircle,
            contentDescription = stringResource(R.string.tab_compose_plus)
        ),
        MainTabItem(
            title = stringResource(R.string.tab_ovh),
            icon = Icons.Outlined.Cloud,
            contentDescription = stringResource(R.string.tab_ovh)
        ),
        MainTabItem(
            title = stringResource(R.string.tab_api),
            icon = Icons.Outlined.SettingsEthernet,
            contentDescription = stringResource(R.string.tab_api)
        ),
        MainTabItem(
            title = stringResource(R.string.tab_bluetooth_power_switch),
            icon = Icons.Outlined.PowerSettingsNew,
            contentDescription = stringResource(R.string.tab_bluetooth_power_switch)
        )
    )
    val selectedTabIndex = uiState.selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val activity = context as? android.app.Activity

    LaunchedEffect(uiState.serviceActive) {
        if (uiState.serviceActive) {
            while (true) {
                viewModel.refreshHostIp(NetworkInfoProvider.getHostIp())
                delay(1500)
            }
        } else {
            viewModel.refreshHostIp(NetworkInfoProvider.getHostIp())
        }
    }

    // Gestion de la permission de localisation
    val locationPermissionGranted = uiState.locationPermissionGranted
    val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.setLocationPermissionGranted(locationGranted)
        showLocationDialog = false
        if (!locationGranted) {
            showLocationDeniedDialog.value = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setLocationPermissionGranted(granted)
        showLocationDialog = false
        if (!granted) {
            showLocationDeniedDialog.value = true
        }
    }
    LaunchedEffect(Unit) {
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val smsGranted = hasSendSmsPermission()

        viewModel.setLocationPermissionGranted(locationGranted)
        showLocationDialog = false

        val startupPermissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startupPermissions += Manifest.permission.BLUETOOTH_SCAN
            startupPermissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (!locationGranted || !smsGranted || !bluetoothGranted) {
            startupPermissionsLauncher.launch(startupPermissions.toTypedArray())
        }
    }

    if (showLocationDialog && activity != null) {
        LocationPermissionRequiredDialog(
            onAllow = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onOpenSettings = {
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = ("package:" + context.packageName).toUri()
                context.startActivity(intent)
            }
        )
    }

    if (showLocationDeniedDialog.value) {
        LocationPermissionDeniedDialog(
            onOpenSettings = {
                showLocationDeniedDialog.value = false
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = ("package:" + context.packageName).toUri()
                context.startActivity(intent)
            },
            onDismiss = { showLocationDeniedDialog.value = false }
        )
    }

    if (showBlePinDialog) {
        AlertDialog(
            onDismissRequest = { showBlePinDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.bluetooth_pin_dialog_title),
                    color = colorResource(id = R.color.white)
                )
            },
            text = {
                OutlinedTextField(
                    value = blePinInput,
                    onValueChange = { blePinInput = it.filter(Char::isDigit).take(8) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bluetooth_pin_dialog_label), color = colorResource(id = R.color.white)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = smsOvhTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlePinDialog = false
                        if (blePinInput.isNotBlank()) {
                            viewModel.connectBleDevice(blePinInput)
                        }
                    },
                    colors = smsOvhButtonColors()
                ) {
                    Text(stringResource(R.string.bluetooth_pin_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlePinDialog = false }) {
                    Text(stringResource(R.string.bluetooth_pin_dialog_cancel), color = colorResource(id = R.color.white))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Récupération de la localisation réseau
    val locationData = uiState.locationData
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = try {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        locationManager.getLastKnownLocation(provider)
                    } else null
                } catch (_: Exception) {
                    null
                }
                if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                    bestLocation = l
                }
            }
            bestLocation?.let {
                viewModel.setLocationData(Pair(it.latitude, it.longitude))
            }
        }
    }

    // Récupération du token API sécurisé
    val token by viewModel.token.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        MainHeader()
        Spacer(modifier = Modifier.height(12.dp))
        uiState.feedbackMessage?.let { feedbackMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.feedbackType) {
                        FeedbackType.SUCCESS -> colorResource(id = R.color.smsovh_primary).copy(alpha = 0.18f)
                        FeedbackType.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                        FeedbackType.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Text(
                    text = withStatusPrefix(feedbackMessage, uiState.feedbackType),
                    color = colorResource(id = R.color.white),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        ServiceStatusRow(
            uiState = uiState,
            onCheckedChange = { checked -> viewModel.setServiceActive(checked) }
        )
        Spacer(Modifier.height(32.dp))
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { viewModel.setSelectedTab(index) },
                    modifier = Modifier.height(72.dp),
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.contentDescription,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (selectedTabIndex) {
            // Onglet 0 : "+" — Composer SMS
            0 -> {
                OvhSmsFormSection(
                    senderId = senderId,
                    recipient = recipient,
                    message = message,
                    onSenderIdChange = { viewModel.setSenderId(it.take(11)) },
                    onRecipientChange = { viewModel.setRecipient(it) },
                    onMessageChange = { viewModel.setMessage(it) }
                )
                SendSmsSection(
                    enabled = uiState.canSendLocalSms,
                    hasSendSmsPermission = hasSendSmsPermission,
                    onRequestSmsPermission = onRequestSmsPermission,
                    onSendSmsRequested = onSendSmsRequested
                )
                Button(
                    onClick = { viewModel.sendOvhSms() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canSendOvhSms,
                    colors = smsOvhButtonColors()
                ) {
                    Text(stringResource(R.string.send_via_ovh_api))
                }
            }

            // Onglet 1 : "OVH" — Configuration OVH
            1 -> {
                OvhApiConfigSection(
                    ovhAppKey = uiState.ovhAppKey,
                    ovhAppSecret = uiState.ovhAppSecret,
                    ovhConsumerKey = uiState.ovhConsumerKey,
                    ovhServiceName = uiState.ovhServiceName,
                    ovhEndpoint = uiState.ovhEndpoint,
                    ovhCountryPrefix = uiState.ovhCountryPrefix,
                    onOvhAppKeyChange = { viewModel.setOvhAppKey(it) },
                    onOvhAppSecretChange = { viewModel.setOvhAppSecret(it) },
                    onOvhConsumerKeyChange = { viewModel.setOvhConsumerKey(it) },
                    onOvhServiceNameChange = { viewModel.setOvhServiceName(it) },
                    onOvhEndpointChange = { viewModel.setOvhEndpoint(it) },
                    onOvhCountryPrefixChange = { viewModel.setOvhCountryPrefix(it) }
                )
            }

            // Onglet 2 : "API" — Configuration API REST
            2 -> {
                val networkLine =
                    "${context.getString(R.string.network_label)} ${if (uiState.isIpValid) "🟢" else "🔴"}"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyToClipboard("network", networkLine) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = networkLine,
                        color = colorResource(id = R.color.white),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(8.dp))

                ApiNetworkSection(
                    uiState = uiState,
                    token = token,
                    locationPermissionGranted = locationPermissionGranted,
                    locationData = locationData,
                    onRestPortInputChange = { viewModel.setRestPortInput(it) },
                    onRestPortCommit = {
                        if (viewModel.commitRestPort()) {
                            focusManager.clearFocus()
                        }
                    },
                    onResetToken = { viewModel.resetToken() },
                    onCopy = copyToClipboard
                )
            }

            // Onglet 3 : "📱" — Configuration Bluetooth
            3 -> {
                if (!bluetoothGranted) {
                    Text(
                        text = stringResource(R.string.bluetooth_permission_denied_banner),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                BleConfigSection(
                    batteryMinPercent = uiState.bleMinBattery,
                    batteryMaxPercent = uiState.bleMaxBattery,
                    isConnected = uiState.bleDeviceState == BleDeviceState.Connected,
                    relayEnabled = uiState.bleRelayState == BleRelayState.On,
                    wifiEnabled = uiState.bleWifiEnabled,
                    connectionStatus = when (uiState.bleDeviceState) {
                        BleDeviceState.Idle -> stringResource(R.string.ble_status_idle)
                        BleDeviceState.Scanning -> stringResource(R.string.ble_status_scanning)
                        is BleDeviceState.Found -> stringResource(R.string.ble_status_found, uiState.bleDeviceState.name)
                        BleDeviceState.NotFound -> stringResource(R.string.ble_status_not_found)
                        BleDeviceState.Connecting -> stringResource(R.string.ble_status_connecting)
                        BleDeviceState.Connected -> stringResource(R.string.ble_status_connected)
                        BleDeviceState.InvalidPin -> stringResource(R.string.ble_status_invalid_pin)
                        BleDeviceState.Timeout -> stringResource(R.string.ble_status_timeout)
                        BleDeviceState.ServiceNotFound -> stringResource(R.string.ble_status_service_not_found)
                        BleDeviceState.CharacteristicNotFound -> stringResource(R.string.ble_status_characteristic_not_found)
                        is BleDeviceState.Error -> stringResource(R.string.ble_status_error, uiState.bleDeviceState.message)
                        BleDeviceState.Disconnected -> stringResource(R.string.ble_status_disconnected)
                    },
                    errorMessage = uiState.bleErrorMessage,
                    onBatteryMinChange = { viewModel.setBleBatteryMin(it) },
                    onBatteryMaxChange = { viewModel.setBleBatteryMax(it) },
                    onBatteryMinCommit = { focusManager.clearFocus() },
                    onBatteryMaxCommit = { focusManager.clearFocus() },
                    onConnect = {
                        blePinInput = if (uiState.blePin.isBlank()) BleRuntimeConfig.pin else uiState.blePin
                        showBlePinDialog = true
                    },
                    onDisconnect = { viewModel.disconnectBle() },
                    onRelaySwitchChange = { isOn ->
                        if (isOn) viewModel.sendRelayOnCommand() else viewModel.sendRelayOffCommand()
                    },
                    onWifiSwitchChange = { isOn ->
                        if (isOn) viewModel.sendWifiOnCommand() else viewModel.sendWifiOffCommand()
                    }
                )
            }
        }
    }
}

