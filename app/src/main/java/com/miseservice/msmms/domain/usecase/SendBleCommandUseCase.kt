// SendBleCommandUseCase.kt
package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.data.ble.BleRepository
import com.miseservice.msmms.model.BleCommand
import com.miseservice.msmms.model.BleRelayState
import javax.inject.Inject

class SendBleCommandUseCase @Inject constructor(
    private val repo: BleRepository
) {
    suspend operator fun invoke(command: BleCommand): BleRelayState =
        repo.sendCommand(command)
}