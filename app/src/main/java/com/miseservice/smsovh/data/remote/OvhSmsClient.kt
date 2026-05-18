package com.miseservice.smsovh.data.remote

import com.miseservice.smsovh.model.OvhSmsRequest
import com.miseservice.smsovh.model.SendResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Singleton
class OvhSmsClient @Inject constructor() {

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private val RETRIABLE_CODES = setOf(429, 500, 502, 503, 504)
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun send(request: OvhSmsRequest): SendResult = withContext(Dispatchers.IO) {
        sendWithRetry(request, attempt = 1)
    }

    suspend fun getCredits(request: OvhSmsRequest): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = buildBaseUrl(request.endpoint)
            val url = "$baseUrl/sms/${request.serviceName}"
            val response = executeRequest(buildGet(request, url))
            JSONObject(response).optDouble("creditsLeft", 0.0)
        }
    }

    private suspend fun sendWithRetry(request: OvhSmsRequest, attempt: Int): SendResult {
        val baseUrl = buildBaseUrl(request.endpoint)
        val url = "$baseUrl/sms/${request.serviceName}/jobs"

        val body = JSONObject().apply {
            put("from", request.senderId ?: "")
            put("to", JSONArray().put(request.recipient))
            put("message", request.message)
            put("noStopClause", request.noStopClause)
            put("priority", request.priority)
            put("validityPeriod", request.validityPeriod)
            put("coding", request.coding)
            put("charset", request.charset)
        }

        return try {
            val rawResponse = executeRequest(buildPost(request, url, body.toString()))
            val parsed = JSONObject(rawResponse)
            if (parsed.has("ids") || parsed.has("totalCreditsRemoved")) {
                SendResult.Success
            } else {
                SendResult.Error(500, "Reponse OVH inattendue")
            }
        } catch (e: OvhApiException) {
            if (e.httpCode in RETRIABLE_CODES && attempt < MAX_RETRIES) {
                delay(RETRY_DELAY_MS * attempt)
                sendWithRetry(request, attempt + 1)
            } else {
                SendResult.Error(e.httpCode, e.ovhMessage)
            }
        } catch (e: IOException) {
            if (attempt < MAX_RETRIES) {
                delay(RETRY_DELAY_MS * attempt)
                sendWithRetry(request, attempt + 1)
            } else {
                SendResult.Error(-1, "Reseau: ${e.message}")
            }
        } catch (e: Exception) {
            SendResult.Error(500, "Erreur OVH: ${e.message}")
        }
    }

    private fun buildPost(request: OvhSmsRequest, url: String, body: String): Request {
        val ts = System.currentTimeMillis() / 1000
        val sig = ovhSignature(request, "POST", url, body, ts)
        return Request.Builder()
            .url(url)
            .addHeader("X-Ovh-Application", request.appKey)
            .addHeader("X-Ovh-Consumer", request.consumerKey)
            .addHeader("X-Ovh-Timestamp", ts.toString())
            .addHeader("X-Ovh-Signature", "$" + sig)
            .post(body.toRequestBody(JSON_TYPE))
            .build()
    }

    private fun buildGet(request: OvhSmsRequest, url: String): Request {
        val ts = System.currentTimeMillis() / 1000
        val sig = ovhSignature(request, "GET", url, "", ts)
        return Request.Builder()
            .url(url)
            .addHeader("X-Ovh-Application", request.appKey)
            .addHeader("X-Ovh-Consumer", request.consumerKey)
            .addHeader("X-Ovh-Timestamp", ts.toString())
            .addHeader("X-Ovh-Signature", "$" + sig)
            .get()
            .build()
    }

    private fun ovhSignature(request: OvhSmsRequest, method: String, url: String, body: String, ts: Long): String {
        val data = "${request.appSecret}+${request.consumerKey}+$method+$url+$body+$ts"
        val md = MessageDigest.getInstance("SHA-1")
        return "1" + md.digest(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun buildBaseUrl(endpointValue: String): String {
        val host = when (endpointValue.trim().lowercase()) {
            "ovh-ca", "ca", "ca.api.ovh.com" -> "ca.api.ovh.com"
            "ovh-us", "us", "us.api.ovhcloud.com", "us.api.ovh.com" -> "us.api.ovhcloud.com"
            "ovh-eu", "eu", "eu.api.ovh.com" -> "eu.api.ovh.com"
            else -> endpointValue.trim().ifBlank { "eu.api.ovh.com" }
        }
        return "https://$host/1.0"
    }

    private fun executeRequest(request: Request): String {
        http.newCall(request).execute().use { response ->
            val rawBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMessage = runCatching {
                    JSONObject(rawBody).optString("message", rawBody)
                }.getOrDefault(rawBody)
                throw OvhApiException(response.code, errMessage)
            }
            return rawBody
        }
    }
}

private class OvhApiException(
    val httpCode: Int,
    val ovhMessage: String
) : Exception("OVH API $httpCode: $ovhMessage")

