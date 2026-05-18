package com.miseservice.smsovh.domain.usecase

import com.miseservice.smsovh.domain.repository.SmsRepository
import com.miseservice.smsovh.model.SmsMessage
import com.miseservice.smsovh.model.SendResult
import javax.inject.Inject

class SendSmsUseCase @Inject constructor(private val repository: SmsRepository) {
    suspend operator fun invoke(sms: SmsMessage): SendResult = repository.sendSms(sms)
}
