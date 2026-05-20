package com.miseservice.msmms.viewmodel

import com.miseservice.msmms.BuildConfig
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miseservice.msmms.R
import com.miseservice.msmms.data.repository.NetworkRepository
import com.miseservice.msmms.data.repository.SettingsRepository
import com.miseservice.msmms.domain.usecase.GetSettingsUseCase
import com.miseservice.msmms.domain.usecase.SendOvhSmsUseCase
import com.miseservice.msmms.domain.usecase.SendSmsUseCase
import com.miseservice.msmms.domain.usecase.UpdateRestPortUseCase
import com.miseservice.msmms.model.OvhSmsRequest
import com.miseservice.msmms.model.SendResult
import com.miseservice.msmms.model.SmsMessage
import com.miseservice.msmms.power.PowerRepository
import com.miseservice.msmms.service.ServiceControlManager
import com.miseservice.msmms.util.ApiTokenManager
import com.miseservice.msmms.util.BatteryLevelProvider
import com.miseservice.msmms.util.BatteryOptimizationHelper
import com.miseservice.msmms.util.ClipboardProvider
import com.miseservice.msmms.util.LocationDataProvider
import com.miseservice.msmms.util.PhoneNumberValidator
import com.miseservice.msmms.util.RestServerEventManager
import com.miseservice.msmms.util.RestServerEventType
import com.miseservice.msmms.util.SimNetworkStatusProvider
import com.miseservice.msmms.util.ValidationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendSmsUseCase: SendSmsUseCase,
    private val sendOvhSmsUseCase: SendOvhSmsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateRestPortUseCase: UpdateRestPortUseCase,
    private val settingsRepository: SettingsRepository,
    private val restServerEventManager: RestServerEventManager,
    private val serviceControlManager: ServiceControlManager,
    private val serviceToggleProcessor: ServiceToggleProcessor,
    private val locationDataProvider: LocationDataProvider,
    private val simNetworkStatusProvider: SimNetworkStatusProvider,
    private val clipboardProvider: ClipboardProvider,
    private val networkRepository: NetworkRepository,
    private val powerRepository: PowerRepository,
    private val batteryLevelProvider: BatteryLevelProvider
) : ViewModel() {
    private val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    private var saveSettingsJob: Job? = null
    private var restPortEditingUnlockJob: Job? = null
    private var simNetworkMonitorJob: Job? = null
    private var networkMonitorJob: Job? = null
    private var powerAutomationJob: Job? = null
    private var powerIpDiscoveryJob: Job? = null
    private var lastDiscoveryUrl: String? = null
    private var lastAppliedServiceActive: Boolean? = null

    @Volatile
    private var isRestPortEditing: Boolean = false
    private var pendingObservedSettings: com.miseservice.msmms.data.local.AppSettingsEntity? = null

    private companion object {
        const val SETTINGS_SAVE_DEBOUNCE_MS = 500L
        const val REST_PORT_EDIT_GRACE_MS = 5000L
        const val AUTO_RELAY_CHECK_INTERVAL_MS = 60_000L
        const val DEFAULT_POWER_BATTERY_MIN = 20
        const val DEFAULT_POWER_BATTERY_MAX = 80
        const val POWER_IP_DISCOVERY_INPUT_DEBOUNCE_MS = 250L
        const val POWER_IP_DISCOVERY_BASE_RETRY_MS = 500L
        const val POWER_IP_DISCOVERY_MAX_RETRY_MS = 5_000L
        const val POWER_IP_DISCOVERY_RESOLVE_TIMEOUT_MS = 12_000L
        const val POWER_IP_DISCOVERY_FAST_FAIL_TIMEOUT_MS = 1_500L
    }

    private fun beginRestPortEditingWindow() {
        isRestPortEditing = true
        restPortEditingUnlockJob?.cancel()
        restPortEditingUnlockJob = viewModelScope.launch {
            delay(REST_PORT_EDIT_GRACE_MS)
            endRestPortEditingWindow()
        }
    }

    private fun endRestPortEditingWindow() {
        restPortEditingUnlockJob?.cancel()
        restPortEditingUnlockJob = null
        isRestPortEditing = false
        val pending = pendingObservedSettings
        pendingObservedSettings = null
        if (pending != null) {
            applyObservedSettings(pending)
        }
    }

    private fun applyObservedSettings(currentSettings: com.miseservice.msmms.data.local.AppSettingsEntity) {
        val host = currentSettings.hostIp ?: _uiState.value.hostIp
        val restPort = currentSettings.restPort.takeIf { ValidationHelper.isPortValid(it) }
            ?: ValidationHelper.DEFAULT_REST_PORT
        runCatching {
            applyServiceActiveState(currentSettings.serviceActive)
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                feedbackMessage = context.getString(
                    R.string.service_unavailable_message,
                    error.message.orEmpty()
                ),
                feedbackType = FeedbackType.ERROR
            )
        }

        val baseState = _uiState.value.copy(
            senderId = currentSettings.senderId.orEmpty(),
            recipient = currentSettings.recipient.orEmpty(),
            message = currentSettings.message.orEmpty(),
            ovhAppKey = currentSettings.ovhAppKey.orEmpty(),
            ovhAppSecret = currentSettings.ovhAppSecret.orEmpty(),
            ovhConsumerKey = currentSettings.ovhConsumerKey.orEmpty(),
            ovhServiceName = currentSettings.ovhServiceName.orEmpty(),
            ovhEndpoint = currentSettings.ovhEndpoint ?: "ovh-eu",
            ovhCountryPrefix = currentSettings.ovhCountryPrefix ?: "+33",
            serviceActive = currentSettings.serviceActive,
            hostIp = host,
            isIpValid = ValidationHelper.isHostIpUsable(host),
            powerToken = currentSettings.powerToken
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: ApiTokenManager.getPowerToken(),
            powerBaseUrl = currentSettings.powerBaseUrl.ifBlank { defaultPowerUrl() },
            powerResolvedIp = currentSettings.powerResolvedIp,
            powerSwitchNumber = currentSettings.powerSwitchNumber.ifBlank { BuildConfig.API_CHARGE_PIN },
            powerBatteryMin = currentSettings.powerBatteryMin.toString(),
            powerBatteryMax = currentSettings.powerBatteryMax.toString()
        )

        _uiState.value = if (isRestPortEditing) {
            baseState
        } else {
            baseState.copy(
                restPort = restPort,
                restPortInput = restPort.toString(),
                restPortError = null
            )
        }

        // Lance une résolution persistante si l'URL n'est pas déjà une IPv4 et qu'on n'a pas d'IP résolue valide.
        if (_uiState.value.powerResolvedIp.isNullOrBlank() && shouldDiscoverPowerIp(_uiState.value.powerBaseUrl)) {
            schedulePowerIpDiscovery()
        }
    }


    private fun persistCurrentSettingsNow() {
        val state = _uiState.value
        val currentToken = _token.value
        viewModelScope.launch {
            val normalizedPowerUrl = normalizePowerUrl(state.powerBaseUrl).ifBlank { defaultPowerUrl() }
            val entity = com.miseservice.msmms.data.local.AppSettingsEntity(
                senderId = state.senderId,
                recipient = state.recipient,
                message = state.message,
                ovhAppKey = state.ovhAppKey,
                ovhAppSecret = state.ovhAppSecret,
                ovhConsumerKey = state.ovhConsumerKey,
                ovhServiceName = state.ovhServiceName,
                ovhEndpoint = state.ovhEndpoint,
                ovhCountryPrefix = state.ovhCountryPrefix,
                serviceActive = state.serviceActive,
                hostIp = state.hostIp,
                restPort = state.restPort,
                token = currentToken,
                powerToken = state.powerToken,
                powerBaseUrl = normalizedPowerUrl,
                powerSwitchNumber = state.powerSwitchNumber.trim().ifBlank { BuildConfig.API_CHARGE_PIN },
                powerBatteryMin = parseLevelOrDefault(state.powerBatteryMin, DEFAULT_POWER_BATTERY_MIN),
                powerBatteryMax = parseLevelOrDefault(state.powerBatteryMax, DEFAULT_POWER_BATTERY_MAX),
                powerResolvedIp = state.powerResolvedIp
            )
            settingsRepository.saveSettings(entity)
        }
    }

    private fun defaultPowerUrl(): String = BuildConfig.API_BASE_URL

    private fun normalizePowerUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }

    private fun extractHost(raw: String): String? = runCatching {
        URI(normalizePowerUrl(raw)).host
    }.getOrNull()?.trim()?.ifBlank { null }

    private fun extractIpv4Host(raw: String): String? {
        val host = extractHost(raw) ?: return null
        return if (isIpv4Host(host)) host else null
    }

    private fun isIpv4Host(host: String): Boolean = host.matches(ipv4Regex)

    private fun shouldDiscoverPowerIp(rawUrl: String): Boolean {
        val host = extractHost(rawUrl) ?: return false
        return !isIpv4Host(host)
    }

    private fun schedulePowerIpDiscovery() {
        val currentUrl = normalizePowerUrl(_uiState.value.powerBaseUrl).ifBlank { defaultPowerUrl() }
        
        // Si une découverte est déjà en cours pour la MÊME URL, on ne fait rien.
        if (powerIpDiscoveryJob?.isActive == true && lastDiscoveryUrl == currentUrl) {
            return
        }

        powerIpDiscoveryJob?.cancel()
        lastDiscoveryUrl = currentUrl

        if (!shouldDiscoverPowerIp(currentUrl)) {
            _uiState.value = _uiState.value.copy(isPowerIpDiscoveryLoading = false)
            return
        }

        // Si l'URL est déjà persistée en IPv4, on évite toute redécouverte DNS.
        if (!extractIpv4Host(currentUrl).isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                powerResolvedIp = extractIpv4Host(currentUrl),
                isPowerIpDiscoveryLoading = false
            )
            return
        }

        powerIpDiscoveryJob = viewModelScope.launch {
            val networkSnapshot = withContext(Dispatchers.IO) {
                networkRepository.detect(_uiState.value.restPort)
            }
            if (!networkSnapshot.isWifiConnected) {
                _uiState.value = _uiState.value.copy(isPowerIpDiscoveryLoading = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isPowerIpDiscoveryLoading = true)
            delay(POWER_IP_DISCOVERY_INPUT_DEBOUNCE_MS)
            runPersistentPowerIpDiscovery()
        }
    }

    private suspend fun runPersistentPowerIpDiscovery() {
        var attempt = 0
        _uiState.value = _uiState.value.copy(isPowerIpDiscoveryLoading = true)
        try {
            while (currentCoroutineContext().isActive) {
                val currentUrl = normalizePowerUrl(_uiState.value.powerBaseUrl).ifBlank { defaultPowerUrl() }
                val host = extractHost(currentUrl)
                if (host.isNullOrBlank()) {
                    Log.w("PowerDiscovery", "Discovery aborted: host is null or blank")
                    return
                }

                if (isIpv4Host(host)) {
                    _uiState.value = _uiState.value.copy(
                        powerResolvedIp = host,
                        powerManagerConnected = true
                    )
                    return
                }

                val resolvedUrl = if (attempt == 0) {
                    // 1er essai rapide: DNS court, puis fallback immédiat vers la résolution complète.
                    resolvePowerBaseUrlToIpFastFail(currentUrl) ?: resolvePowerBaseUrlToIp(currentUrl)
                } else {
                    resolvePowerBaseUrlToIp(currentUrl)
                }

                val resolvedIp = extractIpv4Host(resolvedUrl)
                if (!resolvedIp.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        powerResolvedIp = resolvedIp,
                        powerManagerConnected = true
                    )
                    // On persiste immédiatement l'IP trouvée
                    persistCurrentSettingsNow()
                    return
                }

                val multiplier = 1L shl minOf(attempt, 5)
                val retryDelayMs = minOf(
                    POWER_IP_DISCOVERY_MAX_RETRY_MS,
                    POWER_IP_DISCOVERY_BASE_RETRY_MS * multiplier
                )
                delay(retryDelayMs)
                attempt++
            }
        } finally {
            _uiState.value = _uiState.value.copy(isPowerIpDiscoveryLoading = false)
        }
    }

    private suspend fun resolvePowerBaseUrlToIpFastFail(rawUrl: String): String? {
        val normalized = normalizePowerUrl(rawUrl).ifBlank { defaultPowerUrl() }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) return null
        if (isIpv4Host(host)) return normalized

        val ip = withTimeoutOrNull(POWER_IP_DISCOVERY_FAST_FAIL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching { InetAddress.getByName(host).hostAddress }
                    .getOrNull()
                    ?.takeIf { isIpv4Host(it) }
            }
        } ?: run {
            Log.v("PowerDiscovery", "Fast-fail resolution failed or timed out for $host")
            return null
        }

        val scheme = uri.scheme ?: "http"
        val port = uri.port
        return if (port > 0) "$scheme://$ip:$port" else "$scheme://$ip"
    }

    private suspend fun resolvePowerBaseUrlToIp(rawUrl: String): String {
        val normalized = normalizePowerUrl(rawUrl).ifBlank { defaultPowerUrl() }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) return normalized
        if (isIpv4Host(host)) return normalized

        val ip = withTimeoutOrNull(POWER_IP_DISCOVERY_RESOLVE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                // 1. Essai via DNS standard (InetAddress)
                val standardIp = runCatching {
                    InetAddress.getAllByName(host)
                        .mapNotNull { it.hostAddress }
                        .firstOrNull { isIpv4Host(it) }
                }.getOrNull()

                if (standardIp != null) {
                    return@withContext standardIp
                }

                // 2. Fallback mDNS si l'host finit par .local
                if (host.endsWith(".local", ignoreCase = true)) {
                    var jmdns: JmDNS? = null
                    try {
                        val name = host.removeSuffix(".local").lowercase()
                        jmdns = JmDNS.create(InetAddress.getByName(_uiState.value.hostIp))

                        val services = jmdns.list("_http._tcp.local.")

                        val match = services.firstOrNull { it.server.lowercase().startsWith(name) }
                            ?: services.firstOrNull { it.name.lowercase().contains(name) }

                        val foundIp = match?.inet4Addresses?.firstOrNull()?.hostAddress

                        if (foundIp == null) {
                            Log.w("PowerDiscovery", "mDNS could not find any matching service for $name")
                        }
                        foundIp
                    } catch (e: Exception) {
                        Log.e("PowerDiscovery", "mDNS error for $host", e)
                        null
                    } finally {
                        runCatching { jmdns?.close() }
                    }
                } else null
            }
        }

        if (ip == null) {
            Log.w("PowerDiscovery", "Resolution TIMEOUT or failed for $host (after ${POWER_IP_DISCOVERY_RESOLVE_TIMEOUT_MS}ms). Returning original URL.")
            return normalized
        }

        val scheme = uri.scheme ?: "http"
        val port = uri.port
        val result = if (port > 0) "$scheme://$ip:$port" else "$scheme://$ip"
        return result
    }

    private fun parseLevelOrDefault(raw: String, fallback: Int): Int =
        raw.trim().toIntOrNull()?.coerceIn(0, 100) ?: fallback

    private fun schedulePersistCurrentSettings() {
        saveSettingsJob?.cancel()
        saveSettingsJob = viewModelScope.launch {
            delay(SETTINGS_SAVE_DEBOUNCE_MS)
            persistCurrentSettingsNow()
        }
    }

    private fun applyServiceActiveState(active: Boolean) {
        if (lastAppliedServiceActive == active) return

        if (active) {
            serviceControlManager.start()
        } else {
            serviceControlManager.stop()
        }

        lastAppliedServiceActive = active
    }

    private val _uiState = MutableStateFlow(
        MainUiState(
            powerBaseUrl = defaultPowerUrl(),
            powerSwitchNumber = BuildConfig.API_CHARGE_PIN
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { observed ->
                val currentSettings = observed ?: return@collect
                
                // Synchronisation des tokens vers le cache mémoire
                ApiTokenManager.setServerToken(currentSettings.token)
                ApiTokenManager.setPowerToken(currentSettings.powerToken)
                
                _token.value = ApiTokenManager.getServerToken()

                if (isRestPortEditing) {
                    pendingObservedSettings = currentSettings
                }
                applyObservedSettings(currentSettings)
            }
        }

        // Écoute des événements du serveur REST
        viewModelScope.launch {
            restServerEventManager.eventFlow.collect { event ->
                val feedbackType = when (event.type) {
                    RestServerEventType.SMS_SENT_SUCCESS -> FeedbackType.SUCCESS
                    RestServerEventType.SMS_SENT_ERROR -> FeedbackType.ERROR
                    RestServerEventType.LOG_RECEIVED_SUCCESS -> FeedbackType.SUCCESS
                    RestServerEventType.LOG_RECEIVED_ERROR -> FeedbackType.ERROR
                    RestServerEventType.SERVER_START_SUCCESS -> FeedbackType.SUCCESS
                    RestServerEventType.SERVER_START_ERROR -> FeedbackType.ERROR
                    RestServerEventType.SERVER_PORT_IN_USE -> FeedbackType.ERROR
                }
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = event.message,
                    feedbackType = feedbackType
                )
                delay(if (feedbackType == FeedbackType.SUCCESS) 3000 else 5000)
                clearFeedback()
            }
        }

        // Surveillance autonome de l'état du réseau SIM
        startSimNetworkMonitoring()
        startNetworkMonitoring()
        refreshBatteryOptimizationState()
        startPowerAutomation()
        // schedulePowerIpDiscovery() est déjà déclenché par l'observation des settings
    }

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isNetworkLoading = true,
                wifiSsid = null
            )
            // Détection réseau via le repo, idéalement sur un thread IO car c'est bloquant
            val snapshot = withContext(Dispatchers.IO) {
                networkRepository.detect(_uiState.value.restPort)
            }
            val ssidValue = when {
                !snapshot.isWifiConnected -> context.getString(R.string.network_not_connected)
                !snapshot.wifiSsid.isNullOrBlank() -> snapshot.wifiSsid
                !snapshot.hasLocationPermission -> context.getString(R.string.ssid_requires_location_permission)
                !snapshot.isLocationEnabled -> context.getString(R.string.ssid_requires_location_enabled)
                else -> context.getString(R.string.ssid_unavailable)
            }
            _uiState.value = _uiState.value.copy(
                wifiSsid = ssidValue,
                hasFineLocation = snapshot.hasLocationPermission,
                isLocationEnabledInSystem = snapshot.isLocationEnabled,
                hostIp = snapshot.localIpAddress ?: _uiState.value.hostIp,
                isIpValid = ValidationHelper.isHostIpUsable(snapshot.localIpAddress ?: ""),
                isNetworkLoading = false
            )

            // Relance la découverte UNIQUEMENT si :
            // 1. Wi-Fi est disponible
            // 2. L'URL n'est pas déjà une IPv4
            // 3. L'IP n'est PAS déjà résolue et en cache (optimisation batterie en veille)
            // 4. Aucun job de découverte n'est en cours
            val currentPowerUrl = normalizePowerUrl(_uiState.value.powerBaseUrl).ifBlank { defaultPowerUrl() }
            if (
                snapshot.isWifiConnected &&
                shouldDiscoverPowerIp(currentPowerUrl) &&
                _uiState.value.powerResolvedIp.isNullOrBlank() &&  // ← NE redécouvrir que si pas en cache
                powerIpDiscoveryJob?.isActive != true
            ) {
                schedulePowerIpDiscovery()
            }

            // Refresh location if permission is already granted during network refresh
            if (_uiState.value.locationPermissionGranted) {
                fetchCurrentLocation()
            }
        }
    }

    fun resetToken() {
        viewModelScope.launch {
            val newToken = java.util.UUID.randomUUID().toString().replace("-", "")
            ApiTokenManager.setServerToken(newToken)
            _token.value = newToken
            settingsRepository.updateToken(newToken)
        }
    }

    fun showSmsDeniedDialog(mode: SmsDeniedDialogMode, forSendAction: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            smsDeniedDialogMode = mode,
            smsDeniedForSendAction = forSendAction
        )
    }

    fun dismissSmsDeniedDialog() {
        _uiState.value = _uiState.value.copy(smsDeniedDialogMode = SmsDeniedDialogMode.NONE)
    }

    fun showBatteryOptimizationDialog() {
        if (_uiState.value.batteryOptimizationEnabled) {
            _uiState.value = _uiState.value.copy(batteryOptimizationDialogVisible = true)
        }
    }

    fun dismissBatteryOptimizationDialog() {
        _uiState.value = _uiState.value.copy(batteryOptimizationDialogVisible = false)
    }

    fun refreshBatteryOptimizationState() {
        val enabled = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        _uiState.value = _uiState.value.copy(
            batteryOptimizationEnabled = enabled,
            batteryOptimizationDialogVisible = _uiState.value.batteryOptimizationDialogVisible && enabled
        )
    }

    fun updateSimNetworkStatus(available: Boolean) {
        if (_uiState.value.simNetworkAvailable != available) {
            _uiState.value = _uiState.value.copy(simNetworkAvailable = available)
        }
    }

    fun setSenderId(senderId: String) {
        _uiState.value = _uiState.value.copy(senderId = senderId)
        schedulePersistCurrentSettings()
    }

    fun setRecipient(recipient: String) {
        _uiState.value = _uiState.value.copy(recipient = recipient)
        schedulePersistCurrentSettings()
    }

    fun setMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
        schedulePersistCurrentSettings()
    }

    fun setSelectedTab(index: Int) {
        val previousTab = _uiState.value.selectedTabIndex
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
        // Refresh network info when entering API tab (index 1)
        if (index == 1 && previousTab != 1) {
            refreshNetworkInfo()
        }
    }

    fun setOvhAppKey(value: String) {
        _uiState.value = _uiState.value.copy(ovhAppKey = value)
        schedulePersistCurrentSettings()
    }

    fun setOvhAppSecret(value: String) {
        _uiState.value = _uiState.value.copy(ovhAppSecret = value)
        schedulePersistCurrentSettings()
    }

    fun setOvhConsumerKey(value: String) {
        _uiState.value = _uiState.value.copy(ovhConsumerKey = value)
        schedulePersistCurrentSettings()
    }

    fun setOvhServiceName(value: String) {
        _uiState.value = _uiState.value.copy(ovhServiceName = value)
        schedulePersistCurrentSettings()
    }

    fun setOvhEndpoint(value: String) {
        _uiState.value = _uiState.value.copy(ovhEndpoint = value)
        schedulePersistCurrentSettings()
    }

    fun setOvhCountryPrefix(value: String) {
        _uiState.value = _uiState.value.copy(ovhCountryPrefix = value)
        schedulePersistCurrentSettings()
    }

    fun setRestPortInput(portText: String) {
        beginRestPortEditingWindow()
        val trimmed = ValidationHelper.filterPortInput(portText)
        val parsed = ValidationHelper.parsePortOrNull(trimmed)
        val error = when {
            trimmed.isBlank() -> context.getString(R.string.rest_port_required)
            parsed == null -> context.getString(R.string.rest_port_invalid)
            else -> null
        }

        _uiState.value = _uiState.value.copy(
            restPortInput = trimmed,
            restPortError = error
        )
    }

    fun commitRestPort(): Boolean {
        val port = ValidationHelper.parsePortOrNull(_uiState.value.restPortInput)
        if (port == null) {
            _uiState.value = _uiState.value.copy(
                restPortError = context.getString(R.string.rest_port_invalid),
                restPortInput = _uiState.value.restPort.toString()
            )
            endRestPortEditingWindow()
            return false
        }

        endRestPortEditingWindow()
        viewModelScope.launch {
            updateRestPortUseCase(port)
        }
        _uiState.value = _uiState.value.copy(
            restPort = port,
            restPortInput = port.toString(),
            restPortError = null
        )
        schedulePersistCurrentSettings()
        return true
    }

    fun setServiceActive(active: Boolean) {
        val currentHostIp = _uiState.value.hostIp
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                serviceToggleTargetActive = active
            )
            kotlinx.coroutines.yield()
            runCatching {
                val feedback = if (active) {
                    context.getString(R.string.service_started_success)
                } else {
                    context.getString(R.string.service_stopped_success)
                }
                val updatedState = serviceToggleProcessor.toggle(
                    active = active,
                    currentState = _uiState.value,
                    currentHostIp = currentHostIp,
                    feedbackMessage = feedback
                )
                _uiState.value = updatedState.copy(
                    isLoading = false,
                    serviceToggleTargetActive = null
                )
            }.onSuccess {
                schedulePersistCurrentSettings()
                delay(3000)
                clearFeedback()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serviceToggleTargetActive = null,
                    feedbackMessage = context.getString(
                        if (active) R.string.service_start_failed_message else R.string.service_stop_failed_message,
                        error.message.orEmpty()
                    ),
                    feedbackType = FeedbackType.ERROR
                )
            }
        }
    }

    fun refreshHostIp(hostIp: String) {
        _uiState.value = _uiState.value.copy(
            hostIp = hostIp,
            isIpValid = ValidationHelper.isHostIpUsable(hostIp)
        )
        schedulePersistCurrentSettings()
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        val previous = _uiState.value.locationPermissionGranted
        _uiState.value = _uiState.value.copy(locationPermissionGranted = granted)
        if (granted && !previous) {
            refreshNetworkInfo()
        }
    }

    fun setLocationData(location: Pair<Double, Double>?) {
        _uiState.value = _uiState.value.copy(locationData = location)
    }


    fun sendSms() {
        val state = _uiState.value
        val sms = SmsMessage(state.senderId, state.recipient, state.message)
        viewModelScope.launch {
            val result = sendSmsUseCase(sms)
            when (result) {
                is SendResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        message = "",
                        feedbackMessage = context.getString(R.string.sms_sent_success),
                        feedbackType = FeedbackType.SUCCESS
                    )
                    delay(4000)
                    clearFeedback()
                }

                is SendResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        feedbackMessage = context.getString(
                            R.string.feedback_error_with_message,
                            result.message
                        ),
                        feedbackType = FeedbackType.ERROR
                    )
                    delay(5000)
                    clearFeedback()
                }
            }
        }
    }

    fun sendOvhSms() {
        val state = _uiState.value
        val defaultCountryCode = state.ovhCountryPrefix.removePrefix("+").ifBlank { "33" }
        val normalizedRecipient =
            PhoneNumberValidator.normalize(state.recipient, defaultCountryCode)
        if (normalizedRecipient == null) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = context.getString(
                    R.string.feedback_error_with_message,
                    context.getString(R.string.ovh_invalid_recipient)
                ),
                feedbackType = FeedbackType.ERROR
            )
            return
        }

        val request = OvhSmsRequest(
            senderId = state.senderId.takeIf { it.isNotBlank() },
            appKey = state.ovhAppKey,
            appSecret = state.ovhAppSecret,
            consumerKey = state.ovhConsumerKey,
            serviceName = state.ovhServiceName,
            endpoint = state.ovhEndpoint,
            countryPrefix = state.ovhCountryPrefix,
            recipient = normalizedRecipient,
            message = state.message
        )

        viewModelScope.launch {
            when (val result = sendOvhSmsUseCase(request)) {
                is SendResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        message = "",
                        feedbackMessage = context.getString(R.string.ovh_sms_sent_success),
                        feedbackType = FeedbackType.SUCCESS
                    )
                    delay(4000)
                    clearFeedback()
                }

                is SendResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        feedbackMessage = context.getString(
                            R.string.feedback_error_with_message,
                            result.message
                        ),
                        feedbackType = FeedbackType.ERROR
                    )
                    delay(5000)
                    clearFeedback()
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            feedbackMessage = null,
            feedbackType = FeedbackType.NONE
        )
    }

    // ── Gestion autonome du réseau SIM ────────────────────────────────────
    private fun startSimNetworkMonitoring() {
        if (simNetworkMonitorJob?.isActive == true) return
        simNetworkMonitorJob = viewModelScope.launch {
            simNetworkStatusProvider.observeSimNetworkStatus().collect { isReady ->
                updateSimNetworkStatus(isReady)
            }
        }
    }

    private fun stopSimNetworkMonitoring() {
        simNetworkMonitorJob?.cancel()
        simNetworkMonitorJob = null
    }

    private fun startNetworkMonitoring() {
        if (networkMonitorJob?.isActive == true) return
        networkMonitorJob = viewModelScope.launch {
            networkRepository.observeConnectivityChanges().collect {
                refreshNetworkInfo()
            }
        }
    }

    // ── Utilitaires externalisés (MVVM-compliant) ─────────────────────────
    /**
     * Copie une valeur dans le presse-papiers.
     * Externalise la logique UI pour respecter MVVM.
     */
    fun copyToClipboard(label: String, value: String) {
        clipboardProvider.copyToClipboard(label, value)
    }

    /**
     * Récupère la localisation actuelle de manière asynchrone.
     * Externalise la logique métier pour respecter MVVM.
     */
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(locationData = null)
            val location = withContext(Dispatchers.IO) {
                locationDataProvider.getLastKnownLocation()
            }
            if (location != null) {
                setLocationData(location)
            }
        }
    }

    fun saveAllSettings() {
        saveSettingsJob?.cancel()
        persistCurrentSettingsNow()
    }

    // ── Power / Switch setters ────────────────────────────────────────────
    fun setPowerToken(value: String) {
        _uiState.value = _uiState.value.copy(powerToken = value)
        ApiTokenManager.setPowerToken(value)
        schedulePersistCurrentSettings()
    }

    fun resetPowerIpDiscovery() {
        powerIpDiscoveryJob?.cancel()
        _uiState.value = _uiState.value.copy(
            powerResolvedIp = null,
            isPowerIpDiscoveryLoading = true
        )
        // On force la persistence de powerResolvedIp à null pour vider le cache DB
        schedulePersistCurrentSettings()
        
        powerIpDiscoveryJob = viewModelScope.launch {
            // Un petit délai pour laisser l'UI réagir et éviter des conflits de job
            delay(100)
            runPersistentPowerIpDiscovery()
        }
    }

    fun setPowerBaseUrl(value: String) {
        val ipv4 = extractIpv4Host(value)
        _uiState.value = _uiState.value.copy(
            powerBaseUrl = value,
            powerResolvedIp = ipv4
        )
        schedulePersistCurrentSettings()
        if (ipv4 == null && shouldDiscoverPowerIp(value)) {
            schedulePowerIpDiscovery()
        } else if (ipv4 != null) {
            powerIpDiscoveryJob?.cancel()
            powerIpDiscoveryJob = null
        }
    }

    fun setPowerSwitchNumber(value: String) {
        _uiState.value = _uiState.value.copy(powerSwitchNumber = value)
        schedulePersistCurrentSettings()
    }

    fun setPowerBatteryMin(value: String) {
        _uiState.value = _uiState.value.copy(powerBatteryMin = value)
        schedulePersistCurrentSettings()
    }

    fun setPowerBatteryMax(value: String) {
        _uiState.value = _uiState.value.copy(powerBatteryMax = value)
        schedulePersistCurrentSettings()
    }

    fun triggerPowerCharge() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            val effectiveBaseUrl = snapshot.powerResolvedIp?.let { ip ->
                val uri = runCatching { URI(snapshot.powerBaseUrl) }.getOrNull()
                val scheme = uri?.scheme ?: "http"
                val port = uri?.port ?: -1
                if (port > 0) "$scheme://$ip:$port" else "$scheme://$ip"
            } ?: snapshot.powerBaseUrl

            val toggled = powerRepository.togglePower(effectiveBaseUrl, snapshot.powerSwitchNumber)
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        powerSnackbarMessage = context.getString(
                            R.string.power_charge_error_message,
                            error.message ?: context.getString(R.string.power_charge_error_default)
                        )
                    )
                    return@launch
                }
            val expectedPin = snapshot.powerSwitchNumber.trim().toIntOrNull()
            val isEnabled = toggled.value == 1
            val chargeMessage = if (isEnabled) {
                context.getString(R.string.power_charge_enabled_message)
            } else {
                context.getString(R.string.power_charge_disabled_message)
            }
            _uiState.value = _uiState.value.copy(
                powerStatusState = toggled.state,
                powerStatusValue = toggled.value,
                powerManagerConnected = expectedPin != null && expectedPin == toggled.pin,
                powerLastAction = toggled.action,
                powerSnackbarMessage = chargeMessage
            )
        }
    }

    fun consumePowerSnackbar() {
        _uiState.value = _uiState.value.copy(powerSnackbarMessage = null)
    }

    private fun startPowerAutomation() {
        if (powerAutomationJob?.isActive == true) return
        powerAutomationJob = viewModelScope.launch {
            while (true) {
                runPowerAutomationCycle()
                delay(AUTO_RELAY_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun runPowerAutomationCycle() {
        val snapshot = _uiState.value
        val batteryLevel = batteryLevelProvider.getBatteryLevelPercent()
        if (batteryLevel != null) {
            _uiState.value = _uiState.value.copy(deviceBatteryLevel = batteryLevel)
        }

        val minLevel = parseLevelOrDefault(snapshot.powerBatteryMin, DEFAULT_POWER_BATTERY_MIN)
        val maxLevel = parseLevelOrDefault(snapshot.powerBatteryMax, DEFAULT_POWER_BATTERY_MAX)

        // On utilise l'IP résolue si disponible pour éviter les problèmes de résolution DNS .local
        val effectiveBaseUrl = snapshot.powerResolvedIp?.let { ip ->
            val uri = runCatching { URI(snapshot.powerBaseUrl) }.getOrNull()
            val scheme = uri?.scheme ?: "http"
            val port = uri?.port ?: -1
            if (port > 0) "$scheme://$ip:$port" else "$scheme://$ip"
        } ?: snapshot.powerBaseUrl

        val status = powerRepository.fetchStatus(effectiveBaseUrl, snapshot.powerSwitchNumber)
            .getOrElse {
                _uiState.value = _uiState.value.copy(powerManagerConnected = false)
                return
            }

        val expectedPin = snapshot.powerSwitchNumber.trim().toIntOrNull()
        val connected = expectedPin != null && expectedPin == status.pin

        _uiState.value = _uiState.value.copy(
            powerStatusState = status.state,
            powerStatusValue = status.value,
            powerManagerConnected = connected
        )

        val currentBattery = batteryLevel ?: return

        // Déclenche /api/power/pin si le relais est OFF (status == 0) ET batterie < MIN  → allume la charge
        // Déclenche /api/power/pin si le relais est ON  (status == 1) ET batterie > MAX  → coupe la charge
        val shouldActivatePower   = status.value == 0 && currentBattery < minLevel
        val shouldDeactivatePower = status.value == 1 && currentBattery > maxLevel

        if (!shouldActivatePower && !shouldDeactivatePower) return

        val toggled = powerRepository.togglePower(effectiveBaseUrl, snapshot.powerSwitchNumber)
            .getOrElse {
                return
            }

        _uiState.value = _uiState.value.copy(
            powerStatusState = toggled.state,
            powerStatusValue = toggled.value,
            powerManagerConnected = expectedPin != null && expectedPin == toggled.pin,
            powerLastAction = toggled.action
        )
    }

    override fun onCleared() {
        saveSettingsJob?.cancel()
        restPortEditingUnlockJob?.cancel()
        simNetworkMonitorJob?.cancel()
        networkMonitorJob?.cancel()
        powerAutomationJob?.cancel()
        powerIpDiscoveryJob?.cancel()
        persistCurrentSettingsNow()
        super.onCleared()
    }
}
