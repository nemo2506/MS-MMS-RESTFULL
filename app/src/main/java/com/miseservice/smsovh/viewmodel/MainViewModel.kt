package com.miseservice.smsovh.viewmodel

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.Context
import android.util.Log
import com.miseservice.smsovh.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miseservice.smsovh.domain.usecase.SendSmsUseCase
import com.miseservice.smsovh.domain.usecase.SendOvhSmsUseCase
import com.miseservice.smsovh.domain.usecase.GetSettingsUseCase
import com.miseservice.smsovh.domain.usecase.UpdateRestPortUseCase
import com.miseservice.smsovh.domain.usecase.ScanBleDeviceUseCase
import com.miseservice.smsovh.domain.usecase.ConnectBleUseCase
import com.miseservice.smsovh.domain.usecase.SendBleCommandUseCase
import com.miseservice.smsovh.domain.usecase.ReadBleStateUseCase
import com.miseservice.smsovh.model.*
import com.miseservice.smsovh.model.BleCommand
import com.miseservice.smsovh.data.repository.SettingsRepository
import com.miseservice.smsovh.service.ServiceControlManager
import com.miseservice.smsovh.util.ApiTokenManager
import com.miseservice.smsovh.util.PhoneNumberValidator
import com.miseservice.smsovh.util.RestServerEventManager
import com.miseservice.smsovh.util.RestServerEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendSmsUseCase: SendSmsUseCase,
    private val sendOvhSmsUseCase: SendOvhSmsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateRestPortUseCase: UpdateRestPortUseCase,
    private val scanBleDeviceUseCase: ScanBleDeviceUseCase,
    private val connectBleUseCase: ConnectBleUseCase,
    private val sendBleCommandUseCase: SendBleCommandUseCase,
    private val readBleStateUseCase: ReadBleStateUseCase,
    private val settingsRepository: SettingsRepository,
    private val restServerEventManager: RestServerEventManager,
    private val serviceControlManager: ServiceControlManager,
    private val serviceToggleProcessor: ServiceToggleProcessor
) : ViewModel() {
    private var saveSettingsJob: Job? = null
    private var restPortEditingUnlockJob: Job? = null
    private var autoRelayMonitorJob: Job? = null
    private var lastAppliedServiceActive: Boolean? = null
    @Volatile
    private var isRestPortEditing: Boolean = false
    private var pendingObservedSettings: com.miseservice.smsovh.data.local.AppSettingsEntity? = null

    private companion object {
        const val SETTINGS_SAVE_DEBOUNCE_MS = 500L
        const val DEFAULT_REST_PORT = 8080
        const val REST_PORT_EDIT_GRACE_MS = 5000L
        const val AUTO_RELAY_CHECK_INTERVAL_MS = 60_000L
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

    private fun applyObservedSettings(currentSettings: com.miseservice.smsovh.data.local.AppSettingsEntity) {
        val host = currentSettings.hostIp ?: _uiState.value.hostIp
        val restPort = currentSettings.restPort.takeIf { isPortValid(it) } ?: DEFAULT_REST_PORT
        runCatching {
            applyServiceActiveState(currentSettings.serviceActive)
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                feedbackMessage = context.getString(R.string.service_unavailable_message, error.message.orEmpty()),
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
            isIpValid = isHostIpUsable(host),
            blePin = currentSettings.blePin.orEmpty(),
            bleMinBattery = currentSettings.bleMinBattery,
            bleMaxBattery = currentSettings.bleMaxBattery
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
        syncAutoRelayMonitorWithServiceState()
    }

    private fun syncAutoRelayMonitorWithServiceState() {
        if (_uiState.value.serviceActive) {
            startAutoRelayMonitor()
        } else {
            stopAutoRelayMonitor()
        }
    }

    private fun startAutoRelayMonitor() {
        if (autoRelayMonitorJob?.isActive == true) return
        autoRelayMonitorJob = viewModelScope.launch {
            while (true) {
                evaluateBatteryRuleAndExecute()
                delay(AUTO_RELAY_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopAutoRelayMonitor() {
        autoRelayMonitorJob?.cancel()
        autoRelayMonitorJob = null
    }

    private fun evaluateBatteryRuleAndExecute() {
        val state = _uiState.value
        if (!state.serviceActive) return
        if (state.bleDeviceState != BleDeviceState.Connected) return
        if (state.bleRelayLoading) return

        val batteryPercent = getCurrentBatteryPercent() ?: return
        Log.d("POWER_RULE", "battery=$batteryPercent min=${state.bleMinBattery} max=${state.bleMaxBattery} relay=${state.bleRelayState}")

        if (state.bleRelayState != BleRelayState.On && batteryPercent < state.bleMinBattery) {
            sendRelayOnCommand()
            return
        }
        if (state.bleRelayState == BleRelayState.On && batteryPercent > state.bleMaxBattery) {
            sendRelayOffCommand()
        }
    }

    private fun isHostIpUsable(ip: String): Boolean {
        return ip.isNotBlank() && ip != "127.0.0.1"
    }

    private fun isPortValid(port: Int): Boolean = port in 1..65535

    private fun parsePortOrNull(portText: String): Int? {
        val normalized = portText.trim()
        if (normalized.isBlank()) return null
        val port = normalized.toIntOrNull() ?: return null
        return port.takeIf { isPortValid(it) }
    }

    private fun persistCurrentSettingsNow() {
        val state = _uiState.value
        val currentToken = _token.value
        viewModelScope.launch {
            val entity = com.miseservice.smsovh.data.local.AppSettingsEntity(
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
                blePin = state.blePin,
                bleMinBattery = state.bleMinBattery,
                bleMaxBattery = state.bleMaxBattery
            )
            settingsRepository.saveSettings(entity)
        }
    }

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

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    init {
        viewModelScope.launch {
            val secureToken = ApiTokenManager.getToken(context)
            var settings = getSettingsUseCase()
            if (settings == null) {
                settings = com.miseservice.smsovh.data.local.AppSettingsEntity(
                    senderId = null,
                    recipient = null,
                    message = null,
                    ovhAppKey = null,
                    ovhAppSecret = null,
                    ovhConsumerKey = null,
                    ovhServiceName = null,
                    ovhEndpoint = "ovh-eu",
                    ovhCountryPrefix = "+33",
                    serviceActive = false,
                    hostIp = null,
                    restPort = DEFAULT_REST_PORT,
                    token = secureToken,
                    blePin = null,
                    bleMinBattery = 20,
                    bleMaxBattery = 80
                )
                settingsRepository.saveSettings(settings)
            } else {
                val storedToken = settings.token.orEmpty()
                if (storedToken != secureToken) {
                    settingsRepository.updateToken(secureToken)
                }
            }

            settingsRepository.observeSettings().collect { observed ->
                val currentSettings = observed ?: return@collect
                _token.value = currentSettings.token ?: secureToken
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
    }

    fun resetToken() {
        viewModelScope.launch {
            val newToken = java.util.UUID.randomUUID().toString().replace("-", "")
            ApiTokenManager.setToken(context, newToken)
            settingsRepository.updateToken(newToken)
            _token.value = newToken
            persistCurrentSettingsNow()
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
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
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
        val trimmed = portText.filter { it.isDigit() }.take(5)
        val parsed = parsePortOrNull(trimmed)
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
        val port = parsePortOrNull(_uiState.value.restPortInput)
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
                syncAutoRelayMonitorWithServiceState()
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
            isIpValid = isHostIpUsable(hostIp)
        )
        schedulePersistCurrentSettings()
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationPermissionGranted = granted)
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
                        feedbackMessage = context.getString(R.string.feedback_error_with_message, result.message),
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
        val normalizedRecipient = PhoneNumberValidator.normalize(state.recipient, defaultCountryCode)
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
                        feedbackMessage = context.getString(R.string.feedback_error_with_message, result.message),
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

    /**
     * Met a jour le seuil bas (0..100) pour l'automatisation BLE et persiste l'etat UI.
     *
     * Note: la logique de declenchement immediate ci-dessous depend du niveau batterie
     * recu du module et doit etre appelee avec une telemetrie batterie fiable.
     */
     fun setBleBatteryMin(percent: Int) {
         val clamped = percent.coerceIn(0, 100)
         _uiState.value = _uiState.value.copy(bleMinBattery = clamped)
         schedulePersistCurrentSettings()
         evaluateBatteryRuleAndExecute()
      }

    /**
     * Met a jour le seuil haut (0..100) pour l'automatisation BLE et persiste l'etat UI.
     *
     * Note: la logique de declenchement immediate ci-dessous depend du niveau batterie
     * recu du module et doit etre appelee avec une telemetrie batterie fiable.
     */
      fun setBleBatteryMax(percent: Int) {
         val clamped = percent.coerceIn(0, 100)
         _uiState.value = _uiState.value.copy(bleMaxBattery = clamped)
         schedulePersistCurrentSettings()
         evaluateBatteryRuleAndExecute()
     }

    private fun getCurrentBatteryPercent(): Int? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    private fun resolveBleErrorMessage(state: BleDeviceState): String? = when (state) {
        BleDeviceState.InvalidPin -> context.getString(R.string.bluetooth_error_invalid_pin)
        BleDeviceState.NotFound -> context.getString(R.string.bluetooth_error_device_not_found)
        BleDeviceState.Timeout -> context.getString(R.string.bluetooth_error_timeout)
        BleDeviceState.ServiceNotFound -> context.getString(R.string.bluetooth_error_service_not_found)
        BleDeviceState.CharacteristicNotFound -> context.getString(R.string.bluetooth_error_characteristic_not_found)
        is BleDeviceState.Error -> context.getString(R.string.bluetooth_error_generic, state.message)
        else -> null
    }

    private fun resolveBleStatusLabel(state: BleDeviceState): String = when (state) {
        BleDeviceState.Connected -> context.getString(R.string.bluetooth_status_connected)
        BleDeviceState.Connecting -> context.getString(R.string.bluetooth_status_connecting)
        else -> context.getString(R.string.bluetooth_status_disconnected)
    }

    // ── Commandes Bluetooth ESP32 ──────────────────────────────────────────────

    fun scanBleDevice() {
        viewModelScope.launch {
            val result = scanBleDeviceUseCase()
            when (result) {
                is BleDeviceState.Found -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleDeviceAddress = result.address,
                        bleErrorMessage = null
                    )
                }
                is BleDeviceState.NotFound -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                is BleDeviceState.Error -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                BleDeviceState.Scanning -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = null
                    )
                }
                BleDeviceState.Connecting -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = null
                    )
                }
                BleDeviceState.Connected -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = null
                    )
                }
                BleDeviceState.InvalidPin -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                BleDeviceState.Timeout -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                BleDeviceState.ServiceNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                BleDeviceState.CharacteristicNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = resolveBleErrorMessage(result)
                    )
                }
                BleDeviceState.Disconnected -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = null
                    )
                }
                BleDeviceState.Idle -> {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = result,
                        bleErrorMessage = null
                    )
                }
            }
        }
    }

    fun connectBleDevice(pin: String = BleRuntimeConfig.pin) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                bleDeviceState = BleDeviceState.Connecting,
                bleErrorMessage = null,
                switchCommandStatusMessage = context.getString(R.string.bluetooth_command_sent)
            )

            val address = _uiState.value.bleDeviceAddress
            if (address.isBlank()) {
                val scanned = scanBleDeviceUseCase()
                if (scanned is BleDeviceState.Found) {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceAddress = scanned.address,
                        bleErrorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        bleDeviceState = scanned,
                        bleErrorMessage = resolveBleErrorMessage(scanned),
                        switchCommandStatusMessage = context.getString(R.string.bluetooth_command_failed)
                    )
                    return@launch
                }
            }

            val result = connectBleUseCase(_uiState.value.bleDeviceAddress, pin)
            val connected = result == BleDeviceState.Connected
            val remoteState = if (connected) readBleStateUseCase() else null
            _uiState.value = _uiState.value.copy(
                bleDeviceState = result,
                blePin = if (connected) pin else _uiState.value.blePin,
                bleErrorMessage = if (connected) null else resolveBleErrorMessage(result),
                bleRelayState = when (remoteState) {
                    BleRelayState.On, BleRelayState.Off -> remoteState
                    else -> _uiState.value.bleRelayState
                },
                bleWifiEnabled = when (remoteState) {
                    is BleRelayState.WebServer -> true
                    else -> if (connected) _uiState.value.bleWifiEnabled else false
                },
                switchCommandStatusMessage = if (connected) {
                    context.getString(R.string.bluetooth_command_confirmed)
                } else {
                    context.getString(R.string.bluetooth_command_failed)
                }
            )
            if (connected) {
                schedulePersistCurrentSettings()
            }
        }
    }

    fun sendBleCommand(command: BleCommand) {
        val isRelayCommand = command == BleCommand.RelayOn || command == BleCommand.RelayOff
        val isWifiCommand = command == BleCommand.WifiOn || command == BleCommand.WifiOff

        // Activer uniquement le loader concerné pour garder les commandes indépendantes.
        val loadingState = _uiState.value
        _uiState.value = loadingState.copy(
            bleRelayLoading = if (isRelayCommand) true else loadingState.bleRelayLoading,
            bleWifiLoading = if (isWifiCommand) true else loadingState.bleWifiLoading,
            switchCommandStatusMessage = when {
                isRelayCommand -> context.getString(R.string.relay_command_sent)
                isWifiCommand -> context.getString(R.string.wifi_command_sent)
                else -> loadingState.switchCommandStatusMessage
            }
        )
        viewModelScope.launch {
            runCatching {
                sendBleCommandUseCase(command)
            }.onSuccess { result ->
                val current = _uiState.value
                val wifiEnabled = when (command) {
                    BleCommand.WifiOn -> true
                    BleCommand.WifiOff -> false
                    else -> when (result) {
                        is BleRelayState.WebServer -> true
                        else -> current.bleWifiEnabled
                    }
                }

                val newRelayState = when (command) {
                    BleCommand.RelayOn -> when (result) {
                        BleRelayState.On, BleRelayState.Off -> result
                        else -> BleRelayState.On
                    }

                    BleCommand.RelayOff -> when (result) {
                        BleRelayState.On, BleRelayState.Off -> result
                        else -> BleRelayState.Off
                    }

                    BleCommand.WifiOn, BleCommand.WifiOff -> current.bleRelayState
                }

                _uiState.value = current.copy(
                    bleRelayState = newRelayState,
                    bleWifiEnabled = wifiEnabled,
                    bleRelayLoading = if (isRelayCommand) false else current.bleRelayLoading,
                    bleWifiLoading = if (isWifiCommand) false else current.bleWifiLoading,
                    switchCommandStatusMessage = when {
                        isRelayCommand -> context.getString(R.string.relay_command_confirmed)
                        isWifiCommand -> context.getString(R.string.wifi_command_confirmed)
                        else -> current.switchCommandStatusMessage
                    }
                )
            }.onFailure {
                val current = _uiState.value
                _uiState.value = current.copy(
                    bleRelayLoading = if (isRelayCommand) false else current.bleRelayLoading,
                    bleWifiLoading = if (isWifiCommand) false else current.bleWifiLoading,
                    switchCommandStatusMessage = when {
                        isRelayCommand -> context.getString(R.string.relay_command_failed)
                        isWifiCommand -> context.getString(R.string.wifi_command_failed)
                        else -> current.switchCommandStatusMessage
                    }
                )
            }
        }
    }

    fun consumeSwitchCommandStatusMessage() {
        _uiState.value = _uiState.value.copy(switchCommandStatusMessage = null)
    }

    fun readBleState() {
        viewModelScope.launch {
            val result = readBleStateUseCase()
            val current = _uiState.value
            _uiState.value = current.copy(
                bleRelayState = when (result) {
                    BleRelayState.On, BleRelayState.Off -> result
                    else -> current.bleRelayState
                },
                bleWifiEnabled = when (result) {
                    is BleRelayState.WebServer -> true
                    else -> current.bleWifiEnabled
                }
            )
        }
    }

    fun disconnectBle() {
        _uiState.value = _uiState.value.copy(
            bleDeviceState = BleDeviceState.Disconnected,
            bleRelayState = BleRelayState.Unknown,
            bleWifiEnabled = false,
            bleErrorMessage = null,
            switchCommandStatusMessage = context.getString(R.string.bluetooth_command_confirmed)
        )
    }

    fun sendRelayOnCommand() {
        sendBleCommand(BleCommand.RelayOn)
    }

    fun sendRelayOffCommand() {
        sendBleCommand(BleCommand.RelayOff)
    }

    fun sendWifiOnCommand() {
        sendBleCommand(BleCommand.WifiOn)
    }

    fun sendWifiOffCommand() {
        sendBleCommand(BleCommand.WifiOff)
    }

    fun saveAllSettings() {
        saveSettingsJob?.cancel()
        persistCurrentSettingsNow()
    }

    override fun onCleared() {
        saveSettingsJob?.cancel()
        restPortEditingUnlockJob?.cancel()
        stopAutoRelayMonitor()
        persistCurrentSettingsNow()
        super.onCleared()
    }
}
