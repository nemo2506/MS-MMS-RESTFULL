package com.miseservice.msmms.domain.repository

import com.miseservice.msmms.model.SmsMessage
import com.miseservice.msmms.model.SendResult

import com.miseservice.msmms.model.SendMessageRequest

interface SmsRepository {
    suspend fun sendSms(sms: SmsMessage): SendResult
    suspend fun sendMms(request: SendMessageRequest): SendResult
}

