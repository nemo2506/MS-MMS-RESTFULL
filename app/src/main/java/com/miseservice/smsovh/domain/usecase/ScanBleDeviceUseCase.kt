// ScanBleDeviceUseCase.kt
package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.ble.BleRepository
import com.miseservice.smsovh.model.BleDeviceState
import javax.inject.Inject

class ScanBleDeviceUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(): BleDeviceState = repo.scanForDevice()
}