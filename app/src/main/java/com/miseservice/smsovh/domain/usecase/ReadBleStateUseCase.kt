// ReadBleStateUseCase.kt
package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.ble.BleRepository
import com.miseservice.smsovh.model.BleRelayState
import javax.inject.Inject

class ReadBleStateUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(): BleRelayState = repo.readState()
}