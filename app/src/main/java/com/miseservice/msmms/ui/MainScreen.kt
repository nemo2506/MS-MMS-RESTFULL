package com.miseservice.msmms.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import com.miseservice.msmms.R
import com.miseservice.msmms.model.BleRuntimeConfig
import com.miseservice.msmms.model.BleDeviceState
import com.miseservice.msmms.model.BleRelayState
import com.miseservice.msmms.ui.components.ApiNetworkSection
import com.miseservice.msmms.ui.components.BleConfigSection
import com.miseservice.msmms.ui.components.OvhApiConfigSection
import com.miseservice.msmms.ui.components.OvhSmsFormSection
import com.miseservice.msmms.ui.components.SendSmsSection
import com.miseservice.msmms.ui.components.ServiceStatusRow
import com.miseservice.msmms.ui.components.smsOvhButtonColors
import com.miseservice.msmms.ui.components.smsOvhTextFieldColors
import com.miseservice.msmms.ui.components.dialogs.LocationPermissionDeniedDialog
import com.miseservice.msmms.ui.components.dialogs.LocationPermissionRequiredDialog
import com.miseservice.msmms.ui.components.dialogs.BluetoothPermissionDeniedDialog
import com.miseservice.msmms.ui.components.dialogs.BluetoothPermissionRequiredDialog
import com.miseservice.msmms.ui.components.dialogs.PermissionActionDialog
import com.miseservice.msmms.ui.components.dialogs.SmsPermissionDialog
import com.miseservice.msmms.ui.components.dialogs.SystemStatusDialog
import com.miseservice.msmms.util.NetworkInfoProvider
import com.miseservice.msmms.viewmodel.FeedbackType
import com.miseservice.msmms.viewmodel.MainViewModel
import com.miseservice.msmms.viewmodel.SmsDeniedDialogMode
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

private data class MainTabItem(
    val title: String,
    val icon: ImageVector,
    val contentDescription: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasSendSmsPermission: () -> Boolean,
    onRequestSmsPermission: () -> Unit,
    onSendSmsRequested: () -> Unit,
    onSmsRationaleAllow: (forSendAction: Boolean) -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {}
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
    val showBluetoothDeniedDialog = remember { mutableStateOf(false) }
    val currentContext = rememberUpdatedState(LocalContext.current)
    var showBlePinDialog by rememberSaveable { mutableStateOf(false) }
    var blePinInput by rememberSaveable { mutableStateOf("") }
    var showStartupSmsPrePrompt by rememberSaveable { mutableStateOf(false) }
    var showStartupLocationPrePrompt by rememberSaveable { mutableStateOf(false) }
    var showStartupBluetoothPrePrompt by rememberSaveable { mutableStateOf(false) }
    var pendingStartupSms by rememberSaveable { mutableStateOf(false) }
    var pendingStartupLocation by rememberSaveable { mutableStateOf(false) }
    var pendingStartupBluetooth by rememberSaveable { mutableStateOf(false) }
    val startupApprovedPermissions = remember { mutableStateListOf<String>() }

    // Externaliser la copie via le ViewModel (MVVM-compliant)
    val copyToClipboard: (String, String) -> Unit = { label, value ->
        viewModel.copyToClipboard(label, value)
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
    var showBluetoothDialog by rememberSaveable { mutableStateOf(false) }

    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.setLocationPermissionGranted(locationGranted)
        if (locationGranted) {
            showLocationDialog = false
            showLocationDeniedDialog.value = false
        } else {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
            } == true
            showLocationDialog = shouldShowRationale
            showLocationDeniedDialog.value = !shouldShowRationale
        }

        val bluetoothNowGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        if (bluetoothNowGranted) {
            showBluetoothDialog = false
            showBluetoothDeniedDialog.value = false
        } else {
            val shouldShowBluetoothRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.BLUETOOTH_SCAN) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.BLUETOOTH_CONNECT)
            } == true
            showBluetoothDialog = shouldShowBluetoothRationale
            showBluetoothDeniedDialog.value = !shouldShowBluetoothRationale
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setLocationPermissionGranted(granted)
        if (granted) {
            showLocationDialog = false
            showLocationDeniedDialog.value = false
        } else {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
            } == true
            showLocationDialog = shouldShowRationale
            showLocationDeniedDialog.value = !shouldShowRationale
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        if (granted) {
            showBluetoothDialog = false
            showBluetoothDeniedDialog.value = false
        } else {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.BLUETOOTH_SCAN) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.BLUETOOTH_CONNECT)
            } == true
            showBluetoothDialog = shouldShowRationale
            showBluetoothDeniedDialog.value = !shouldShowRationale
        }
    }

    val proceedStartupPrePrompt: () -> Unit = {
        showStartupSmsPrePrompt = false
        showStartupLocationPrePrompt = false
        showStartupBluetoothPrePrompt = false

        when {
            pendingStartupSms -> showStartupSmsPrePrompt = true
            pendingStartupLocation -> showStartupLocationPrePrompt = true
            pendingStartupBluetooth -> showStartupBluetoothPrePrompt = true
            startupApprovedPermissions.isNotEmpty() -> {
                startupPermissionsLauncher.launch(startupApprovedPermissions.toTypedArray())
                startupApprovedPermissions.clear()
            }
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
        showBluetoothDialog = false
        showLocationDeniedDialog.value = false
        showBluetoothDeniedDialog.value = false
        startupApprovedPermissions.clear()

        pendingStartupSms = !smsGranted
        pendingStartupLocation = !locationGranted
        pendingStartupBluetooth = !bluetoothGranted
        proceedStartupPrePrompt()
    }

    if (showStartupSmsPrePrompt) {
        SmsPermissionDialog(
            onConfirm = {
                showStartupSmsPrePrompt = false
                pendingStartupSms = false
                if (!startupApprovedPermissions.contains(Manifest.permission.SEND_SMS)) {
                    startupApprovedPermissions += Manifest.permission.SEND_SMS
                }
                proceedStartupPrePrompt()
            },
            onDismiss = {
                showStartupSmsPrePrompt = false
                pendingStartupSms = false
                proceedStartupPrePrompt()
            }
        )
    }

    if (showStartupLocationPrePrompt) {
        PermissionActionDialog(
            title = stringResource(R.string.location_permission_required_title),
            message = stringResource(R.string.location_permission_required_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = {
                showStartupLocationPrePrompt = false
                pendingStartupLocation = false
                if (!startupApprovedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    startupApprovedPermissions += Manifest.permission.ACCESS_FINE_LOCATION
                }
                proceedStartupPrePrompt()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = {
                showStartupLocationPrePrompt = false
                pendingStartupLocation = false
                proceedStartupPrePrompt()
            },
            onDismissRequest = {}
        )
    }

    if (showStartupBluetoothPrePrompt && bluetoothPermissions.isNotEmpty()) {
        PermissionActionDialog(
            title = stringResource(R.string.bluetooth_permission_required_title),
            message = stringResource(R.string.bluetooth_permission_required_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = {
                showStartupBluetoothPrePrompt = false
                pendingStartupBluetooth = false
                bluetoothPermissions.forEach { permission ->
                    if (!startupApprovedPermissions.contains(permission)) {
                        startupApprovedPermissions += permission
                    }
                }
                proceedStartupPrePrompt()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = {
                showStartupBluetoothPrePrompt = false
                pendingStartupBluetooth = false
                proceedStartupPrePrompt()
            },
            onDismissRequest = {}
        )
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

    if (showBluetoothDialog && activity != null && bluetoothPermissions.isNotEmpty()) {
        BluetoothPermissionRequiredDialog(
            onAllow = { bluetoothPermissionLauncher.launch(bluetoothPermissions) },
            onOpenSettings = {
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = ("package:" + context.packageName).toUri()
                context.startActivity(intent)
            }
        )
    }

    if (showBluetoothDeniedDialog.value) {
        BluetoothPermissionDeniedDialog(
            onOpenSettings = {
                showBluetoothDeniedDialog.value = false
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = ("package:" + context.packageName).toUri()
                context.startActivity(intent)
            },
            onDismiss = { showBluetoothDeniedDialog.value = false }
        )
    }

    // Dialogues SMS refus (rationale / refus permanent) pilotés par le ViewModel
    val smsDeniedMode = uiState.smsDeniedDialogMode
    if (smsDeniedMode == SmsDeniedDialogMode.RATIONALE) {
        PermissionActionDialog(
            title = stringResource(R.string.permission_required_title),
            message = stringResource(R.string.permission_required_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = {
                onSmsRationaleAllow(uiState.smsDeniedForSendAction)
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { viewModel.dismissSmsDeniedDialog() }
        )
    }
    if (smsDeniedMode == SmsDeniedDialogMode.PERMANENTLY_DENIED) {
        PermissionActionDialog(
            title = stringResource(R.string.permission_required_title),
            message = stringResource(R.string.permission_denied_permanently_message),
            confirmLabel = stringResource(R.string.settings),
            onConfirm = {
                viewModel.dismissSmsDeniedDialog()
                onOpenAppSettings()
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { viewModel.dismissSmsDeniedDialog() }
        )
    }

    // Dialogue optimisation batterie piloté par le ViewModel
    if (uiState.batteryOptimizationDialogVisible) {
        PermissionActionDialog(
            title = stringResource(R.string.battery_optimization_title),
            message = stringResource(R.string.battery_optimization_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = { onRequestBatteryOptimization() },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { viewModel.dismissBatteryOptimizationDialog() }
        )
    }

    if (showBlePinDialog) {
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        Dialog(
            onDismissRequest = { showBlePinDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.bluetooth_pin_dialog_title),
                            color = colorResource(id = R.color.white),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        TextField(
                            value = blePinInput,
                            onValueChange = { blePinInput = it.filter(Char::isDigit).take(8) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(R.string.bluetooth_pin_dialog_label),
                                    color = colorResource(id = R.color.white)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = smsOvhTextFieldColors()
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showBlePinDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.bluetooth_pin_dialog_cancel),
                                    color = colorResource(id = R.color.white)
                                )
                            }
                            Spacer(Modifier.padding(horizontal = 8.dp))
                            Button(
                                onClick = {
                                    showBlePinDialog = false
                                    if (blePinInput.isNotBlank()) {
                                        viewModel.connectBleDevice(blePinInput)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = smsOvhButtonColors()
                            ) {
                                Text(stringResource(R.string.bluetooth_pin_dialog_confirm))
                            }
                        }
                    }
                }
            }
        }
    }

    // Récupération de la localisation réseau (externaliser via ViewModel)
    val locationData = uiState.locationData
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            viewModel.fetchCurrentLocation()
        }
    }

    // Récupération du token API sécurisé
    val token by viewModel.token.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // La surveillance SIM est gérée automatiquement par le ViewModel
    // Plus besoin de boucle dans la UI ✓ (MVVM-compliant)


    // Dialogue état système persistant (4 conditions)
    val bleConnected = uiState.bleDeviceState == com.miseservice.msmms.model.BleDeviceState.Connected
    val hasSystemIssue = !(uiState.serviceActive && uiState.isIpValid && bleConnected && uiState.simNetworkAvailable)
    val systemIssueSignature = remember(
        uiState.serviceActive,
        uiState.isIpValid,
        bleConnected,
        uiState.simNetworkAvailable
    ) {
        "svc:${uiState.serviceActive}|net:${uiState.isIpValid}|ble:$bleConnected|sim:${uiState.simNetworkAvailable}"
    }
    var dismissedSystemIssueSignature by rememberSaveable { mutableStateOf<String?>(null) }

    // Quand tout est revenu OK, on réinitialise l'accusé de réception.
    LaunchedEffect(hasSystemIssue) {
        if (!hasSystemIssue) dismissedSystemIssueSignature = null
    }

    if (hasSystemIssue && dismissedSystemIssueSignature != systemIssueSignature) {
        SystemStatusDialog(
            serviceActive = uiState.serviceActive,
            networkConnected = uiState.isIpValid,
            bluetoothConnected = bleConnected,
            simNetworkAvailable = uiState.simNetworkAvailable,
            onDismiss = { dismissedSystemIssueSignature = systemIssueSignature }
        )
    }

    LaunchedEffect(uiState.switchCommandStatusMessage) {
        val snackbarMessage = uiState.switchCommandStatusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = snackbarMessage)
        viewModel.consumeSwitchCommandStatusMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = colorResource(id = R.color.smsovh_primary),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            // Bandeau de retour (succès / erreur)
            uiState.feedbackMessage?.let { feedbackMessage ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
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
            }

            // Statut du service (pill + switch) — fixe, hors scroll
            ServiceStatusRow(
                uiState = uiState,
                onCheckedChange = { checked -> viewModel.setServiceActive(checked) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Barre d'onglets M3 — fixe, ne scrolle pas avec le contenu
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.contentDescription,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
            }

            // Contenu de chaque onglet — indépendamment scrollable
            when (selectedTabIndex) {

                // ─────────────────── Onglet 0 : Composer SMS ───────────────────
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OvhSmsFormSection(
                                senderId = senderId,
                                recipient = recipient,
                                message = message,
                                onSenderIdChange = { viewModel.setSenderId(it.take(11)) },
                                onRecipientChange = { viewModel.setRecipient(it) },
                                onMessageChange = { viewModel.setMessage(it) }
                            )
                        }
                    }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SendSmsSection(
                                enabled = uiState.canSendLocalSms,
                                hasSendSmsPermission = hasSendSmsPermission,
                                onRequestSmsPermission = onRequestSmsPermission,
                                onSendSmsRequested = onSendSmsRequested
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ─────────────────── Onglet 1 : Config OVH ─────────────────────
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ─────────────────── Onglet 2 : API REST ───────────────────────
                2 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // En-tête statut réseau (copiable)
                    val networkLine =
                        "${context.getString(R.string.network_label)} ${if (uiState.isIpValid) "🟢" else "🔴"}"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copyToClipboard("network", networkLine) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isIpValid)
                                colorResource(id = R.color.smsovh_primary).copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = networkLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorResource(id = R.color.white),
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Outlined.SettingsEthernet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colorResource(id = R.color.white).copy(alpha = 0.7f)
                            )
                        }
                    }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ─────────────────── Onglet 3 : Bluetooth / Power ──────────────
                3 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!bluetoothGranted) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.bluetooth_permission_denied_banner),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BleConfigSection(
                                batteryMinPercent = uiState.bleMinBattery,
                                batteryMaxPercent = uiState.bleMaxBattery,
                                isConnected = uiState.bleDeviceState == BleDeviceState.Connected,
                                isLoading = uiState.bleDeviceState == BleDeviceState.Scanning
                                        || uiState.bleDeviceState is BleDeviceState.Found
                                        || uiState.bleDeviceState == BleDeviceState.Connecting,
                                relayLoading = uiState.bleRelayLoading,
                                wifiLoading = uiState.bleWifiLoading,
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            }
        }

        // Snackbar fixé en haut pour les commandes Bluetooth, indépendant des dialogues.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp, start = 16.dp, end = 16.dp)
        )
    }
}

