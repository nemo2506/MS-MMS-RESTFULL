package com.miseservice.smsovh.viewmodel

import com.miseservice.smsovh.service.ServiceControlManager
import javax.inject.Inject

class ServiceToggleProcessor @Inject constructor(
    private val serviceControlManager: ServiceControlManager
) {
    fun toggle(
        active: Boolean,
        currentState: MainUiState,
        currentHostIp: String,
        feedbackMessage: String
    ): MainUiState {
        if (active) {
            serviceControlManager.start()
        } else {
            serviceControlManager.stop()
        }

        return currentState.copy(
            serviceActive = active,
            isIpValid = currentHostIp.isNotBlank() && currentHostIp != "127.0.0.1",
            feedbackMessage = feedbackMessage,
            feedbackType = FeedbackType.SUCCESS
        )
    }
}
