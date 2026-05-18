package com.miseservice.msmms.data.ble

import com.miseservice.msmms.model.BleCommand
import com.miseservice.msmms.model.BleDeviceState
import com.miseservice.msmms.model.BleRelayState
import kotlinx.coroutines.flow.Flow

interface BleRepository {

    /** Scanne et trouve l'appareil cible par nom */
    suspend fun scanForDevice(timeoutMs: Long = 5_000L): BleDeviceState

    /**
     * Connecte au périphérique BLE après validation du PIN.
     * @return true si PIN valide + connexion réussie
     */
    suspend fun connect(address: String, pin: String): BleDeviceState

    /** Lit l'état courant du relais / IP du serveur web */
    suspend fun readState(): BleRelayState

    /**
     * Envoie une commande BLE.
     * @return BleRelayState après lecture post-envoi
     */
    suspend fun sendCommand(command: BleCommand): BleRelayState

    /** Déconnecte proprement */
    suspend fun disconnect()

    /** Flow de l'état de connexion */
    val connectionState: Flow<BleDeviceState>
}