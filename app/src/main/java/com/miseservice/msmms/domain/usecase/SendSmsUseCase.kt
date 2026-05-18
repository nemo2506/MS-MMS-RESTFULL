package com.miseservice.msmms.domain.usecase

import com.miseservice.msmms.domain.repository.SmsRepository
import com.miseservice.msmms.model.SmsMessage
import com.miseservice.msmms.model.SendResult
import javax.inject.Inject

class SendSmsUseCase @Inject constructor(private val repository: SmsRepository) {
    suspend operator fun invoke(sms: SmsMessage): SendResult = repository.sendSms(sms)
}
