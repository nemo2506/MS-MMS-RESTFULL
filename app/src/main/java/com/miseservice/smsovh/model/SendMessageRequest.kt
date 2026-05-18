package com.miseservice.smsovh.model

data class SendMessageRequest(
    val senderId: String?,
    val recipient: String,
    val text: String,
    val base64Jpeg: String?
)
