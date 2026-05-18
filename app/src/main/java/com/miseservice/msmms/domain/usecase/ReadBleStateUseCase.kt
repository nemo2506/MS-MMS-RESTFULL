// ReadBleStateUseCase.kt
package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.data.ble.BleRepository
import com.miseservice.msmms.model.BleRelayState
import javax.inject.Inject

class ReadBleStateUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(): BleRelayState = repo.readState()
}