package vn.futaland.app.core.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class APIError(val statusCode: Int, override val message: String) : Exception(message) {
    val isUnauthorized: Boolean
        get() = statusCode == 401 || message.contains("token", ignoreCase = true) ||
                message.contains("unauthorized", ignoreCase = true) || message.contains("hết hạn", ignoreCase = true)
}

class APIClient private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: APIClient? = null

        fun init(context: Context): APIClient {
            return instance ?: synchronized(this) {
                instance ?: APIClient(context.applicationContext).also { instance = it }
            }
        }

        fun get(): APIClient {
            return checkNotNull(instance) { "APIClient must be initialized first!" }
        }

        val apiBaseUrl = "https://bds.futaland.vn/api"
        val publicWebUrl = "https://bds.futaland.vn"
    }

    val tokenStorage = TokenStorage(context)
    private val refreshMutex = Mutex()

    var onSessionExpired: (() -> Unit)? = null

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    val effectiveToken: String?
        get() = tokenStorage.accessToken?.takeIf { it.isNotEmpty() }
            ?: tokenStorage.guestToken?.takeIf { it.isNotEmpty() }

    suspend fun request(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        query: Map<String, String> = emptyMap()
    ): JSONValue = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(path, query)
        val requestBuilder = Request.Builder().url(fullUrl)

        effectiveToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val requestBody = bodyJson?.toRequestBody("application/json; charset=utf-8".toMediaType())
        requestBuilder.method(method, requestBody)

        var response = okHttpClient.newCall(requestBuilder.build()).execute()

        // Handle 401 and attempt refresh token
        if (response.code == 401 && tokenStorage.refreshToken != null) {
            response.close()
            val refreshed = refreshAccessToken()
            if (refreshed) {
                val retryRequest = Request.Builder().url(fullUrl)
                tokenStorage.accessToken?.let { newToken ->
                    retryRequest.addHeader("Authorization", "Bearer $newToken")
                }
                retryRequest.method(method, requestBody)
                response = okHttpClient.newCall(retryRequest.build()).execute()
            }
        }

        val respBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val code = response.code
            if (code == 401) {
                onSessionExpired?.invoke()
            }
            throw APIError(code, if (respBody.isNotEmpty()) respBody else "Lỗi HTTP $code")
        }

        JSONValue.parse(respBody)
    }

    suspend fun upload(
        data: ByteArray,
        filename: String,
        mimeType: String,
        path: String,
        field: String = "file"
    ): JSONValue = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(path, emptyMap())
        val mediaType = mimeType.toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(field, filename, data.toRequestBody(mediaType))
            .build()

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .post(requestBody)

        effectiveToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        val respBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw APIError(response.code, respBody)
        }
        JSONValue.parse(respBody)
    }

    private suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        val refresh = tokenStorage.refreshToken ?: return false
        return try {
            val refreshUrl = "$apiBaseUrl/auth/refresh"
            val body = "{\"refreshToken\":\"$refresh\"}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(refreshUrl).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONValue.parse(response.body?.string().orEmpty())
                val newAccess = json["data"]["accessToken"].string
                val newRefresh = json["data"]["refreshToken"].string
                if (newAccess.isNotEmpty()) {
                    tokenStorage.accessToken = newAccess
                    if (newRefresh.isNotEmpty()) tokenStorage.refreshToken = newRefresh
                    return true
                }
            }
            if (response.code == 401) {
                tokenStorage.clear()
                onSessionExpired?.invoke()
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): HttpUrl {
        val base = if (path.startsWith("http")) path else "$apiBaseUrl/${path.removePrefix("/")}"
        val urlBuilder = checkNotNull(base.toHttpUrlOrNull()) { "Invalid URL: $base" }.newBuilder()
        query.forEach { (k, v) ->
            urlBuilder.addQueryParameter(k, v)
        }
        return urlBuilder.build()
    }
}
