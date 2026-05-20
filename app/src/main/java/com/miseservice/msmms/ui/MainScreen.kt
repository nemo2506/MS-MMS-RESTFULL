package com.miseservice.msmms.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.miseservice.msmms.R
import com.miseservice.msmms.ui.components.ApiNetworkSection
import com.miseservice.msmms.ui.components.OvhApiConfigSection
import com.miseservice.msmms.ui.components.OvhSmsFormSection
import com.miseservice.msmms.ui.components.SendSmsSection
import com.miseservice.msmms.ui.components.ServiceStatusRow
import com.miseservice.msmms.ui.components.dialogs.PermissionActionDialog
import com.miseservice.msmms.ui.components.dialogs.SystemStatusDialog
import com.miseservice.msmms.ui.components.smsOvhButtonColors
import com.miseservice.msmms.ui.components.smsOvhTextFieldColors
import com.miseservice.msmms.util.NetworkInfoProvider
import com.miseservice.msmms.viewmodel.FeedbackType
import com.miseservice.msmms.viewmodel.MainViewModel
import com.miseservice.msmms.viewmodel.SmsDeniedDialogMode
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
    onRequestSmsPermission: (forAction: Boolean) -> Unit,
    onSendSmsRequested: () -> Unit,
    onSmsRationaleAllow: (forSendAction: Boolean) -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val context = LocalContext.current

    fun withStatusPrefix(message: String, type: FeedbackType): String {
        val trimmed = message.trim()
        return when (type) {
            FeedbackType.SUCCESS -> trimmed
            FeedbackType.ERROR -> trimmed
            else -> trimmed
        }
    }

    val focusManager = LocalFocusManager.current
    val uiState = viewModel.uiState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }
    var showStartupPermissionsDialog by rememberSaveable { mutableStateOf(false) }

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
            title = stringResource(R.string.tab_power_switch),
            icon = Icons.Outlined.PowerSettingsNew,
            contentDescription = stringResource(R.string.tab_power_switch)
        )
    )
    val selectedTabIndex = uiState.selectedTabIndex.coerceIn(0, tabs.lastIndex)

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

    // Le workflow des permissions est désormais centralisé via MainActivity.
    // L'UI réagit simplement aux changements d'états dans le ViewModel ou déclenche
    // le dialogue de démarrage (StartupPermissionsDialog) si nécessaire.

    LaunchedEffect(Unit) {
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val smsGranted = hasSendSmsPermission()
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        viewModel.setLocationPermissionGranted(locationGranted)

        // Si une permission critique manque, on affiche le dialogue de groupe au démarrage
        if (!locationGranted || !smsGranted || !phoneStateGranted || !notificationGranted) {
            showStartupPermissionsDialog = true
        }
    }

    if (showStartupPermissionsDialog) {
        PermissionActionDialog(
            title = stringResource(R.string.permission_required_title),
            message = stringResource(R.string.startup_permissions_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = {
                showStartupPermissionsDialog = false
                onRequestSmsPermission(false) // Déclenche le groupe complet via MainActivity
            },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { showStartupPermissionsDialog = false },
            onDismissRequest = { showStartupPermissionsDialog = false }
        )
    }

    // Le flux principal de permissions passe par StartupPermissionsDialog -> checkAndRequestPermissions()
    // Les dialogues spécifiques (SMS, Batterie) sont pilotés par le ViewModel.

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
    if (uiState.batteryOptimizationDialogVisible && uiState.batteryOptimizationEnabled) {
        PermissionActionDialog(
            title = stringResource(R.string.battery_optimization_title),
            message = stringResource(R.string.battery_optimization_message),
            confirmLabel = stringResource(R.string.allow),
            onConfirm = { onRequestBatteryOptimization() },
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { viewModel.dismissBatteryOptimizationDialog() }
        )
    }

    // Récupération de la localisation réseau (externaliser via ViewModel)

    LaunchedEffect(uiState.locationPermissionGranted) {
        if (uiState.locationPermissionGranted) {
            viewModel.fetchCurrentLocation()
        }
    }

    LaunchedEffect(uiState.powerSnackbarMessage) {
        uiState.powerSnackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.consumePowerSnackbar()
        }
    }

    // Récupération du token API sécurisé
    val token by viewModel.token.collectAsState()
    // La surveillance SIM est gérée automatiquement par le ViewModel
    // Plus besoin de boucle dans la UI ✓ (MVVM-compliant)


    // Dialogue état système persistant (4 conditions)
    val hasSystemIssue = !(
            uiState.serviceActive &&
                    uiState.isIpValid &&
                    uiState.simNetworkAvailable &&
                    uiState.powerManagerConnected
            )
    val systemIssueSignature = remember(
        uiState.serviceActive,
        uiState.isIpValid,
        uiState.simNetworkAvailable,
        uiState.powerManagerConnected
    ) {
        "svc:${uiState.serviceActive}|net:${uiState.isIpValid}|sim:${uiState.simNetworkAvailable}|pwr:${uiState.powerManagerConnected}"
    }
    var dismissedSystemIssueSignature by rememberSaveable { mutableStateOf<String?>(null) }

    // Quand tout est revenu OK, on réinitialise l'accusé de réception.
    LaunchedEffect(hasSystemIssue) {
        if (!hasSystemIssue) dismissedSystemIssueSignature = null
    }

    if (hasSystemIssue && dismissedSystemIssueSignature != systemIssueSignature) {
        SystemStatusDialog(
            serviceActive = uiState.serviceToggleTargetActive ?: uiState.serviceActive,
            networkConnected = uiState.isIpValid,
            powerManagerConnected = uiState.powerManagerConnected,
            simNetworkAvailable = uiState.simNetworkAvailable,
            onDismiss = { dismissedSystemIssueSignature = systemIssueSignature }
        )
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
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
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
                                FeedbackType.SUCCESS -> colorResource(id = R.color.smsovh_primary).copy(
                                    alpha = 0.18f
                                )

                                FeedbackType.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                FeedbackType.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.45f
                                )
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
                                    onRequestSmsPermission = { onRequestSmsPermission(true) },
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
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                ApiNetworkSection(
                                    uiState = uiState,
                                    token = token,
                                    onRestPortInputChange = { viewModel.setRestPortInput(it) },
                                    onRestPortCommit = {
                                        if (viewModel.commitRestPort()) {
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onResetToken = { viewModel.resetToken() },
                                    onCopy = copyToClipboard,
                                    onRefreshNetwork = { viewModel.refreshNetworkInfo() }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ─────────────────── Onglet 3 : Power ─────────────────────
                    3 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerPowerCharge() },
                            colors = smsOvhButtonColors(),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.power_charge_button))
                        }

                        // ── Carte Configuration ──────────────────────────────────
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.power_config_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorResource(id = R.color.smsovh_primary),
                                    fontWeight = FontWeight.Bold
                                )

                                // URL + SWITCH NUMBER alignés sur une seule ligne
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextField(
                                        value = uiState.powerBaseUrl,
                                        onValueChange = { viewModel.setPowerBaseUrl(it) },
                                        label = { Text(stringResource(R.string.power_url_label)) },
                                        placeholder = { Text(stringResource(R.string.power_url_placeholder)) },
                                        trailingIcon = {
                                            if (uiState.isPowerIpDiscoveryLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        },
                                        supportingText = {
                                            val resolvedIp = uiState.powerResolvedIp
                                            val supporting =
                                                if (uiState.isPowerIpDiscoveryLoading) {
                                                    stringResource(R.string.service_status_updating)
                                                } else if (resolvedIp.isNullOrBlank()) {
                                                    stringResource(R.string.power_url_supporting_text)
                                                } else {
                                                    stringResource(
                                                        R.string.power_url_resolved_ip,
                                                        resolvedIp
                                                    )
                                                }
                                            Text(supporting)
                                        },
                                        singleLine = true,
                                        colors = smsOvhTextFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        modifier = Modifier.weight(0.8f)
                                    )

                                    TextField(
                                        value = uiState.powerSwitchNumber,
                                        onValueChange = { viewModel.setPowerSwitchNumber(it) },
                                        label = { Text(stringResource(R.string.power_switch_number_label)) },
                                        singleLine = true,
                                        colors = smsOvhTextFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.2f)
                                    )
                                }
                                Button(
                                    onClick = { viewModel.resetPowerIpDiscovery() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorResource(id = R.color.smsovh_primary).copy(
                                            alpha = 0.15f
                                        ),
                                        contentColor = colorResource(id = R.color.smsovh_primary)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !uiState.isPowerIpDiscoveryLoading
                                ) {
                                    if (uiState.isPowerIpDiscoveryLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = colorResource(id = R.color.smsovh_primary)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = stringResource(R.string.power_reset_discovery_button),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextField(
                                    value = uiState.powerToken,
                                    onValueChange = { viewModel.setPowerToken(it) },
                                    label = { Text(stringResource(R.string.power_token_label)) },
                                    placeholder = { Text(stringResource(R.string.power_token_placeholder)) },
                                    singleLine = true,
                                    colors = smsOvhTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        // ── Battery Level─────────────────────────────────────
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.power_battery_level_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorResource(id = R.color.smsovh_primary),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextField(
                                        value = uiState.powerBatteryMin,
                                        onValueChange = { viewModel.setPowerBatteryMin(it) },
                                        label = { Text(stringResource(R.string.power_battery_min_label)) },
                                        singleLine = true,
                                        colors = smsOvhTextFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextField(
                                        value = uiState.powerBatteryMax,
                                        onValueChange = { viewModel.setPowerBatteryMax(it) },
                                        label = { Text(stringResource(R.string.power_battery_max_label)) },
                                        singleLine = true,
                                        colors = smsOvhTextFieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.power_device_battery_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorResource(id = R.color.smsovh_primary),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = uiState.deviceBatteryLevel?.let { "$it%" } ?: "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                uiState.powerStatusState?.let { status ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.power_endpoint_status_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorResource(id = R.color.smsovh_primary),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ── Carte optimisation batterie système ─────────────────
                        if (uiState.batteryOptimizationEnabled) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.battery_optimization_message),
                                        color = colorResource(id = R.color.white),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.showBatteryOptimizationDialog() },
                                        colors = smsOvhButtonColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.settings))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
