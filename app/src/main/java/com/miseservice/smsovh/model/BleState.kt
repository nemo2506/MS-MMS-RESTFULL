package com.miseservice.smsovh.model

import com.miseservice.smsovh.BuildConfig

object BleRuntimeConfig {
    val deviceName: String = BuildConfig.BLE_DEVICE_NAME
    val serviceUuid: String = BuildConfig.BLE_SERVICE_UUID
    val characteristicUuid: String = BuildConfig.BLE_CHARACTERISTIC_UUID
    val pin: String = BuildConfig.BLE_PIN
    val writeType: Int = BuildConfig.BLE_WRITE_TYPE
}

sealed class BleDeviceState {
    object Idle : BleDeviceState()
    object Scanning : BleDeviceState()
    data class Found(val address: String, val name: String) : BleDeviceState()
    object NotFound : BleDeviceState()
    object Connecting : BleDeviceState()
    object Connected : BleDeviceState()
    object InvalidPin : BleDeviceState()
    object Timeout : BleDeviceState()
    object ServiceNotFound : BleDeviceState()
    object CharacteristicNotFound : BleDeviceState()
    data class Error(val message: String) : BleDeviceState()
    object Disconnected : BleDeviceState()
}

sealed class BleRelayState {
    object Unknown : BleRelayState()
    object On : BleRelayState()
    object Off : BleRelayState()
    data class WebServer(val ip: String) : BleRelayState()
    data class Raw(val message: String) : BleRelayState()
}

sealed class BleCommand(val raw: String) {
    object RelayOn  : BleCommand("1")
    object RelayOff : BleCommand("0")
    object WifiOn   : BleCommand("W")
    object WifiOff  : BleCommand("X")
}