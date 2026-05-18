// ScanBleDeviceUseCase.kt
package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.data.ble.BleRepository
import com.miseservice.msmms.model.BleDeviceState
import javax.inject.Inject

class ScanBleDeviceUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(): BleDeviceState = repo.scanForDevice()
}