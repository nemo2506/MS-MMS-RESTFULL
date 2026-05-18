// ConnectBleUseCase.kt
package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.data.ble.BleRepository
import com.miseservice.msmms.model.BleDeviceState
import javax.inject.Inject

class ConnectBleUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(address: String, pin: String): BleDeviceState =
        repo.connect(address, pin)
}