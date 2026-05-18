package com.miseservice.smsovh.model

data class OvhSmsRequest(
    val senderId: String?,
    val appKey: String,
    val appSecret: String,
    val consumerKey: String,
    val serviceName: String,
    val endpoint: String,
    val countryPrefix: String,
    val recipient: String,
    val message: String,
    val noStopClause: Boolean = false,
    val priority: String = "high",
    val validityPeriod: Int = 2880,
    val coding: String = "7bit",
    val charset: String = "UTF-8"
)

