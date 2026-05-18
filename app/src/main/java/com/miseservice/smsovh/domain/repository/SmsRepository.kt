package com.miseservice.smsovh.domain.repository

import com.miseservice.smsovh.model.SmsMessage
import com.miseservice.smsovh.model.SendResult

import com.miseservice.smsovh.model.SendMessageRequest

interface SmsRepository {
    suspend fun sendSms(sms: SmsMessage): SendResult
    suspend fun sendMms(request: SendMessageRequest): SendResult
}

