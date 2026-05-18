package com.miseservice.smsovh.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Suppress("unused")
class ApiCircuitTest {

    companion object {
        const val BASE_URL = "http://localhost:8080"
        const val BEARER_TOKEN = "YOUR_TOKEN_HERE"
        const val TEST_PHONE = "+33612345678"
        const val TEST_MESSAGE = "Test API Circuit"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private inline fun executeBooleanRequest(
        request: Request,
        crossinline block: (okhttp3.Response) -> Boolean
    ): Boolean {
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                block(response)
            }
        }.getOrDefault(false)
    }

    fun testHealthCheck(): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/health")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .get()
            .build()

        return executeBooleanRequest(request) { response ->
            response.isSuccessful
        }
    }

    fun testAuthenticationMissingToken(): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/health")
            .get()
            .build()

        return executeBooleanRequest(request) { response -> response.code == 401 }
    }

    fun testAuthenticationInvalidToken(): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/health")
            .addHeader("Authorization", "Bearer INVALID_TOKEN_12345")
            .get()
            .build()

        return executeBooleanRequest(request) { response -> response.code == 401 }
    }

    fun testSendSmsBasic(): Boolean {
        val json = JSONObject()
            .put("senderId", "TestApp")
            .put("recipient", TEST_PHONE)
            .put("text", TEST_MESSAGE)

        val request = Request.Builder()
            .url("$BASE_URL/api/send-sms")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response ->
            if (!response.isSuccessful) return@executeBooleanRequest false
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            responseJson.optBoolean("success", false) &&
                responseJson.optString("type", "") == "SMS"
        }
    }

    fun testSendSmsWithValidation(): Boolean {
        val json = JSONObject()
            .put("senderId", "TestApp")
            .put("recipient", "abc123")
            .put("text", TEST_MESSAGE)

        val request = Request.Builder()
            .url("$BASE_URL/api/send-sms")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response -> response.code == 400 }
    }

    fun testSendMmsWithImage(): Boolean {
        val base64Image = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAA" +
            "EBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8VAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k"

        val json = JSONObject()
            .put("senderId", "TestApp")
            .put("recipient", TEST_PHONE)
            .put("text", "Test MMS")
            .put("base64Jpeg", base64Image)

        val request = Request.Builder()
            .url("$BASE_URL/api/send-mms")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response ->
            if (!response.isSuccessful) return@executeBooleanRequest false
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            responseJson.optBoolean("success", false) &&
                responseJson.optString("type", "") == "MMS"
        }
    }

    fun testSendMessageAutoRoute(): Boolean {
        val json = JSONObject()
            .put("senderId", "TestApp")
            .put("recipient", TEST_PHONE)
            .put("text", TEST_MESSAGE)

        val request = Request.Builder()
            .url("$BASE_URL/api/send-message")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response ->
            if (!response.isSuccessful) return@executeBooleanRequest false
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            responseJson.optBoolean("success", false) &&
                responseJson.optString("type", "") == "SMS"
        }
    }

    fun testSendLog(): Boolean {
        val json = JSONObject().put("message", "Test log message from API circuit test")

        val request = Request.Builder()
            .url("$BASE_URL/api/logs")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response -> response.isSuccessful }
    }

    fun testNotFoundError(): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/invalid-endpoint")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response -> response.code == 404 }
    }

    fun testBadRequestError(): Boolean {
        val request = Request.Builder()
            .url("$BASE_URL/api/send-sms")
            .addHeader("Authorization", "Bearer $BEARER_TOKEN")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        return executeBooleanRequest(request) { response -> response.code == 400 }
    }

    fun runAllTests(): Map<String, Boolean> {
        return linkedMapOf(
            "1. Health Check" to testHealthCheck(),
            "2. Auth - Missing Token" to testAuthenticationMissingToken(),
            "3. Auth - Invalid Token" to testAuthenticationInvalidToken(),
            "4. Send SMS Basic" to testSendSmsBasic(),
            "5. Send SMS Validation" to testSendSmsWithValidation(),
            "6. Send MMS With Image" to testSendMmsWithImage(),
            "7. Send Message Auto-Route" to testSendMessageAutoRoute(),
            "8. Send Log" to testSendLog(),
            "9. 404 Not Found" to testNotFoundError(),
            "10. 400 Bad Request" to testBadRequestError()
        )
    }
}

