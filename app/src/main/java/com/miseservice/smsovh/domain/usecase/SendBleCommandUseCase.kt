// SendBleCommandUseCase.kt
package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.ble.BleRepository
import com.miseservice.smsovh.model.BleCommand
import com.miseservice.smsovh.model.BleRelayState
import javax.inject.Inject

class SendBleCommandUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(command: BleCommand): BleRelayState =
        repo.sendCommand(command)
}