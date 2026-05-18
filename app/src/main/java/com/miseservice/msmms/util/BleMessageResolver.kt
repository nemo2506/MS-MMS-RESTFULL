package com.miseservice.msmms.util

import android.content.Context
import com.miseservice.msmms.R
import com.miseservice.msmms.model.BleDeviceState

/**
 * Utilitaire pour résoudre les messages et labels Bluetooth.
 * Centralise la logique de messages d'erreur et de statut pour MVVM.
 */
class BleMessageResolver(private val context: Context) {

    /**
     * Résout le message d'erreur approprié pour un état d'appareil Bluetooth.
     */
    fun getErrorMessage(state: BleDeviceState): String? = when (state) {
        BleDeviceState.InvalidPin -> context.getString(R.string.bluetooth_error_invalid_pin)
        BleDeviceState.NotFound -> context.getString(R.string.bluetooth_error_device_not_found)
        BleDeviceState.Timeout -> context.getString(R.string.bluetooth_error_timeout)
        BleDeviceState.ServiceNotFound -> context.getString(R.string.bluetooth_error_service_not_found)
        BleDeviceState.CharacteristicNotFound -> context.getString(R.string.bluetooth_error_characteristic_not_found)
        is BleDeviceState.Error -> context.getString(R.string.bluetooth_error_generic, state.message)
        else -> null
    }

    /**
     * Résout le label de statut pour un état d'appareil Bluetooth.
     */
    fun getStatusLabel(state: BleDeviceState): String = when (state) {
        BleDeviceState.Connected -> context.getString(R.string.bluetooth_status_connected)
        BleDeviceState.Connecting -> context.getString(R.string.bluetooth_status_connecting)
        else -> context.getString(R.string.bluetooth_status_disconnected)
    }

    /**
     * Crée une description lisible du statut de découverte/connexion BLE pour l'UI.
     */
    fun getConnectionStatusMessage(state: BleDeviceState): String = when (state) {
        BleDeviceState.Idle -> context.getString(R.string.ble_status_idle)
        BleDeviceState.Scanning -> context.getString(R.string.ble_status_scanning)
        is BleDeviceState.Found -> context.getString(R.string.ble_status_found, state.name)
        BleDeviceState.NotFound -> context.getString(R.string.ble_status_not_found)
        BleDeviceState.Connecting -> context.getString(R.string.ble_status_connecting)
        BleDeviceState.Connected -> context.getString(R.string.ble_status_connected)
        BleDeviceState.InvalidPin -> context.getString(R.string.ble_status_invalid_pin)
        BleDeviceState.Timeout -> context.getString(R.string.ble_status_timeout)
        BleDeviceState.ServiceNotFound -> context.getString(R.string.ble_status_service_not_found)
        BleDeviceState.CharacteristicNotFound -> context.getString(R.string.ble_status_characteristic_not_found)
        is BleDeviceState.Error -> context.getString(R.string.ble_status_error, state.message)
        BleDeviceState.Disconnected -> context.getString(R.string.ble_status_disconnected)
    }
}

