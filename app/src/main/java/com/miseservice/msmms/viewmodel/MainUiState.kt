package com.miseservice.msmms.viewmodel

import com.miseservice.msmms.model.BleDeviceState
import com.miseservice.msmms.model.BleRelayState

/**
 * État de l’interface principale pour l'envoi de SMS OVH.
 *
 * @property isLoading Indique si les données sont en cours de chargement
 * @property serviceActive Indique si le service foreground est actif
 * @property senderId Identifiant de l'expéditeur
 * @property recipient Destinataire du SMS
 * @property message Contenu du SMS
 * @property hostIp Adresse IP locale de l’appareil
 * @property restPort Port API REST actif
 * @property restPortInput Valeur saisie dans l'UI pour le port
 * @property restPortError Erreur de validation du port
 * @property isIpValid Indique si l'IP est valide
 * @property locationPermissionGranted Permission de localisation accordée
 * @property locationData Coordonnées GPS (latitude, longitude)
 * @property networkType Type de réseau actif
 * @property errorMessage Message d’erreur éventuel
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val selectedTabIndex: Int = 0,
    val serviceToggleTargetActive: Boolean? = null,
    val serviceActive: Boolean = false,
    val senderId: String = "",
    val recipient: String = "",
    val message: String = "",
    val ovhAppKey: String = "",
    val ovhAppSecret: String = "",
    val ovhConsumerKey: String = "",
    val ovhServiceName: String = "",
    val ovhEndpoint: String = "ovh-eu",
    val ovhCountryPrefix: String = "+33",
    val hostIp: String = "127.0.0.1",
    val restPort: Int = 8080,
    val restPortInput: String = "8080",
    val restPortError: String? = null,
    val isIpValid: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val locationData: Pair<Double, Double>? = null,
    val networkType: String = "Non connecté",
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
    val feedbackType: FeedbackType = FeedbackType.NONE,
    val bleMinBattery: Int = 20,
    val bleMaxBattery: Int = 80,
    val bleDeviceState: BleDeviceState = BleDeviceState.Idle,
    val bleRelayState: BleRelayState = BleRelayState.Unknown,
    val bleWifiEnabled: Boolean = false,
    val bleDeviceAddress: String = "",
    val blePin: String = "",
    val bleRestoringConnection: Boolean = false,
    val bleErrorMessage: String? = null,
    val bleRelayLoading: Boolean = false,
    val bleWifiLoading: Boolean = false,
    val switchCommandStatusMessage: String? = null,
    val smsDeniedDialogMode: SmsDeniedDialogMode = SmsDeniedDialogMode.NONE,
    val smsDeniedForSendAction: Boolean = false,
    val batteryOptimizationDialogVisible: Boolean = false,
    val simNetworkAvailable: Boolean = false
) {
    val canSendLocalSms: Boolean
        get() = serviceActive && !isLoading && recipient.isNotBlank() && message.isNotBlank()

    val canSendOvhSms: Boolean
        get() = !isLoading &&
            senderId.isNotBlank() &&
            recipient.isNotBlank() &&
            message.isNotBlank() &&
            ovhAppKey.isNotBlank() &&
            ovhAppSecret.isNotBlank() &&
            ovhConsumerKey.isNotBlank() &&
            ovhServiceName.isNotBlank()
}

enum class FeedbackType {
    NONE, SUCCESS, ERROR
}

enum class SmsDeniedDialogMode {
    NONE, RATIONALE, PERMANENTLY_DENIED
}

