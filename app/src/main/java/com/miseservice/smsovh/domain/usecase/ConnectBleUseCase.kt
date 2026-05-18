// ConnectBleUseCase.kt
package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.ble.BleRepository
import com.miseservice.smsovh.model.BleDeviceState
import javax.inject.Inject

class ConnectBleUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(address: String, pin: String): BleDeviceState =
        repo.connect(address, pin)
}