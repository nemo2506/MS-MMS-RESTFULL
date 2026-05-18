package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.data.remote.OvhSmsClient
import com.miseservice.smsovh.model.OvhSmsRequest
import com.miseservice.smsovh.model.SendResult
import javax.inject.Inject

class SendOvhSmsUseCase @Inject constructor(
    private val ovhSmsClient: OvhSmsClient
) {
    suspend operator fun invoke(request: OvhSmsRequest): SendResult {
        if (request.senderId.isNullOrBlank()) {
            return SendResult.Error(400, "Sender ID manquant")
        }
        if (request.recipient.isBlank()) {
            return SendResult.Error(400, "Recipient manquant")
        }
        if (request.message.isBlank()) {
            return SendResult.Error(400, "Message manquant")
        }
        if (
            request.appKey.isBlank() ||
            request.appSecret.isBlank() ||
            request.consumerKey.isBlank() ||
            request.serviceName.isBlank()
        ) {
            return SendResult.Error(400, "Configuration OVH incomplete")
        }
        return ovhSmsClient.send(request)
    }
}

