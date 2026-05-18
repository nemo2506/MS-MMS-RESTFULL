package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.domain.repository.SmsRepository
import com.miseservice.smsovh.model.SendMessageRequest
import com.miseservice.smsovh.model.SmsMessage
import com.miseservice.smsovh.model.SendResult
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(private val repository: SmsRepository) {
    suspend operator fun invoke(request: SendMessageRequest): SendResult {
        return if (request.base64Jpeg.isNullOrBlank()) {
            repository.sendSms(
                SmsMessage(
                    from = request.senderId ?: "",
                    to = request.recipient,
                    message = request.text
                )
            )
        } else {
            repository.sendMms(request)
        }
    }
}
