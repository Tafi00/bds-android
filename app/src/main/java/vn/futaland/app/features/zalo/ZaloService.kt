package vn.futaland.app.features.zalo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.APIError
import vn.futaland.app.core.network.JSONValue
import java.util.concurrent.TimeUnit

/**
 * REST layer of the messaging module (iOS ZaloService.swift). Paths are the backend's
 * `bds-backend/src/modules/zalo/src/routes/` (one file per resource), mounted under `/api/zalo`.
 */
object ZaloService {

    private val client get() = APIClient.get()

    private fun strings(values: Collection<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

    /** Lists come back bare, or wrapped in `key` / `data` depending on the route. */
    private fun extractArray(json: JSONValue, key: String? = null): List<JSONValue> {
        if (json.element is JsonArray) return json.array
        if (key != null && json[key].element is JsonArray) return json[key].array
        if (json["data"].element is JsonArray) return json["data"].array
        return emptyList()
    }

    private fun providerQuery(provider: ZaloProvider) = mapOf("provider" to provider.raw)

    // MARK: - Accounts

    suspend fun fetchAccounts(provider: ZaloProvider? = null): List<ZaloAccountModel> {
        val query = provider?.let { providerQuery(it) } ?: emptyMap()
        return extractArray(client.request("/zalo/accounts", query = query)).map { ZaloAccountModel(it) }
    }

    suspend fun updateAccountSettings(accountId: String, campaignOnlyInbound: Boolean, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject { put("campaignOnlyInbound", campaignOnlyInbound) }.toString()
        return client.request("/zalo/accounts/${enc(accountId)}", "PATCH", body, providerQuery(provider))
    }

    suspend fun reconnectAccount(accountId: String, provider: ZaloProvider) {
        client.request("/zalo/accounts/${enc(accountId)}/reconnect", "POST", EMPTY_BODY, providerQuery(provider))
    }

    suspend fun removeAccount(accountId: String, provider: ZaloProvider) {
        client.request("/zalo/accounts/${enc(accountId)}", "DELETE", query = providerQuery(provider))
    }

    // MARK: - QR login

    /** Returns the login id; the QR image then arrives over the websocket or the status poll. */
    suspend fun startQRLogin(provider: ZaloProvider): String {
        val res = client.request("/zalo/accounts/login/qr/${provider.raw}", "POST", EMPTY_BODY)
        return res["loginId"].string
    }

    suspend fun fetchQRLoginStatus(loginId: String): JSONValue =
        client.request("/zalo/accounts/login/qr/${enc(loginId)}/status")

    suspend fun cancelQRLogin(loginId: String) {
        runCatching { client.request("/zalo/accounts/login/qr/${enc(loginId)}", "DELETE") }
    }

    // MARK: - Conversations

    data class ConversationsResult(val conversations: List<ZaloConversationModel>, val total: Int, val hasMore: Boolean)

    suspend fun fetchConversations(
        limit: Int = 50,
        offset: Int = 0,
        unreadOnly: Boolean = false,
        accountId: String? = null,
        provider: ZaloProvider? = null,
        search: String? = null,
        replyStatus: ZaloConversationFilter? = null,
        sort: ZaloSortOrder? = null,
        labelIds: Collection<String>? = null
    ): ConversationsResult {
        val query = mutableMapOf("limit" to "$limit", "offset" to "$offset")
        if (unreadOnly) query["unreadOnly"] = "true"
        if (!accountId.isNullOrEmpty()) query["accountId"] = accountId
        if (provider != null) query["provider"] = provider.raw
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { query["search"] = it }
        if (replyStatus == ZaloConversationFilter.AWAITING_REPLY || replyStatus == ZaloConversationFilter.NEEDS_RESPONSE) {
            query["replyStatus"] = replyStatus.raw
        }
        if (sort != null) query["sort"] = sort.raw
        if (!labelIds.isNullOrEmpty()) query["labelIds"] = labelIds.joinToString(",")
        val res = client.request("/zalo/messages/conversations", query = query)
        return ConversationsResult(
            conversations = extractArray(res, "conversations").map { ZaloConversationModel(it) },
            total = res["total"].int,
            hasMore = res["hasMore"].bool
        )
    }

    // MARK: - Messages

    suspend fun fetchMessages(accountId: String, threadId: String, provider: ZaloProvider, before: String? = null): List<ZaloMessageModel> {
        val query = providerQuery(provider).toMutableMap()
        if (!before.isNullOrEmpty()) query["before"] = before
        val res = client.request("/zalo/messages/${enc(accountId)}/${enc(threadId)}", query = query)
        return extractArray(res).map { ZaloMessageModel(it) }
    }

    suspend fun sendMessage(accountId: String, threadId: String, content: String, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject { put("content", content) }.toString()
        return client.request("/zalo/messages/${enc(accountId)}/${enc(threadId)}", "POST", body, providerQuery(provider))
    }

    private val uploadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /**
     * `POST /messages/:accountId/:threadId/send-with-file` (multipart `content`, `provider`, `file`).
     * APIClient.upload only sends one field, so this builds the form itself like iOS does.
     */
    suspend fun sendFileMessage(
        accountId: String,
        threadId: String,
        data: ByteArray,
        filename: String,
        mimeType: String,
        content: String,
        provider: ZaloProvider
    ): JSONValue = withContext(Dispatchers.IO) {
        val url = "${APIClient.apiBaseUrl}/zalo/messages/${enc(accountId)}/${enc(threadId)}/send-with-file"
            .toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("provider", provider.raw)?.build()
            ?: throw APIError(0, tr("URL không hợp lệ"))
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content", content)
            .addFormDataPart("provider", provider.raw)
            .addFormDataPart("file", filename.replace("\"", ""), data.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()
        val request = Request.Builder().url(url).post(form)
        client.effectiveToken?.let { request.header("Authorization", "Bearer $it") }
        uploadClient.newCall(request.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val json = JSONValue.parse(text)
                val message = json["error"].string.ifEmpty { json["message"].string }
                    .ifEmpty { json["error"]["message"].string }
                    .ifEmpty { tr("Gửi tệp thất bại (HTTP {0})", response.code) }
                throw APIError(response.code, message)
            }
            if (text.isBlank()) JSONValue.parse("{\"success\":true}") else JSONValue.parse(text)
        }
    }

    suspend fun uploadCampaignImage(jpeg: ByteArray, filename: String = "campaign.jpg"): String {
        val res = client.upload(jpeg, filename, "image/jpeg", "/zalo/campaigns/upload-image", field = "image")
        val url = res["url"].string.ifEmpty { res["data"]["url"].string }
        if (url.isEmpty()) throw APIError(0, tr("Máy chủ không trả về URL ảnh"))
        return url
    }

    suspend fun undoMessage(accountId: String, threadId: String, msgId: String, cliMsgId: String, provider: ZaloProvider) {
        val body = buildJsonObject {
            put("msgId", msgId)
            put("cliMsgId", cliMsgId.ifEmpty { msgId })
        }.toString()
        client.request("/zalo/messages/${enc(accountId)}/${enc(threadId)}/undo", "POST", body, providerQuery(provider))
    }

    suspend fun markAsRead(accountId: String, threadId: String, provider: ZaloProvider) {
        client.request("/zalo/messages/${enc(accountId)}/${enc(threadId)}/read", "POST", EMPTY_BODY, providerQuery(provider))
    }

    suspend fun markAsUnread(accountId: String, threadId: String, provider: ZaloProvider) {
        client.request("/zalo/messages/${enc(accountId)}/${enc(threadId)}/unread", "POST", EMPTY_BODY, providerQuery(provider))
    }

    /** AI reply suggestions; failures (quota, AI off) simply mean no suggestions, as on iOS. */
    suspend fun fetchMessageSuggestions(accountId: String, threadId: String, provider: ZaloProvider): List<String> =
        runCatching {
            val res = client.request(
                "/zalo/messages/${enc(accountId)}/${enc(threadId)}/suggestions", query = providerQuery(provider)
            )
            extractArray(res, "suggestions").map { it.string }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    // MARK: - AI suggestion settings

    suspend fun fetchSuggestionConfig(): JSONValue = client.request("/zalo/suggestions/config")

    suspend fun updateSuggestionConfig(styleDescription: String, sampleMessages: List<String>): JSONValue {
        val body = buildJsonObject {
            put("styleDescription", styleDescription)
            put("sampleMessages", strings(sampleMessages))
        }.toString()
        return client.request("/zalo/suggestions/config", "PUT", body)
    }

    suspend fun resetSuggestionConfig(): JSONValue = client.request("/zalo/suggestions/config/reset", "POST", EMPTY_BODY)

    // MARK: - Contacts & users

    suspend fun fetchCachedContacts(accountId: String, provider: ZaloProvider): List<ZaloContactModel> {
        val res = client.request("/zalo/users/contacts/${enc(accountId)}/cached", query = providerQuery(provider))
        return extractArray(res).map { ZaloContactModel(it) }
    }

    suspend fun syncContacts(accountId: String, provider: ZaloProvider): JSONValue =
        client.request("/zalo/users/contacts/${enc(accountId)}/sync", "POST", EMPTY_BODY, providerQuery(provider))

    suspend fun findUserByPhone(accountId: String, phoneNumber: String, provider: ZaloProvider): JSONValue =
        client.request("/zalo/users/find-by-phone/${enc(accountId)}/${enc(phoneNumber)}", query = providerQuery(provider))

    suspend fun updateNickname(userId: String, nickname: String, accountId: String, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject {
            put("nickname", nickname)
            put("accountId", accountId)
        }.toString()
        return client.request("/zalo/users/${enc(userId)}/nickname", "PATCH", body, providerQuery(provider))
    }

    suspend fun updateUserPhone(userId: String, phoneNumber: String, accountId: String, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject {
            put("phoneNumber", phoneNumber)
            put("accountId", accountId)
        }.toString()
        return client.request("/zalo/users/${enc(userId)}/phone", "PATCH", body, providerQuery(provider))
    }

    suspend fun fetchCustomerData(zaloId: String, accountId: String, provider: ZaloProvider): JSONValue =
        client.request(
            "/zalo/users/${enc(zaloId)}/customer-data",
            query = mapOf("accountId" to accountId, "provider" to provider.raw)
        )

    suspend fun updateCustomerNeeds(zaloId: String, needs: String, accountId: String, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject {
            put("customerNeeds", needs)
            put("accountId", accountId)
        }.toString()
        return client.request("/zalo/users/${enc(zaloId)}/needs", "PUT", body, providerQuery(provider))
    }

    suspend fun updateCustomerTags(zaloId: String, tags: List<String>, accountId: String, provider: ZaloProvider): JSONValue {
        val body = buildJsonObject {
            put("tags", strings(tags))
            put("accountId", accountId)
        }.toString()
        return client.request("/zalo/users/${enc(zaloId)}/tags", "PUT", body, providerQuery(provider))
    }

    // MARK: - Labels

    suspend fun fetchLabels(): List<ZaloCustomLabelModel> =
        extractArray(client.request("/zalo/labels")).map { ZaloCustomLabelModel(it) }

    suspend fun createLabel(name: String, color: String): ZaloCustomLabelModel {
        val body = buildJsonObject {
            put("name", name)
            put("color", color)
        }.toString()
        return ZaloCustomLabelModel(client.request("/zalo/labels", "POST", body))
    }

    suspend fun updateLabel(id: String, name: String, color: String): ZaloCustomLabelModel {
        val body = buildJsonObject {
            put("name", name)
            put("color", color)
        }.toString()
        return ZaloCustomLabelModel(client.request("/zalo/labels/${enc(id)}", "PUT", body))
    }

    suspend fun deleteLabel(id: String) {
        client.request("/zalo/labels/${enc(id)}", "DELETE")
    }

    private fun conversationQuery(provider: ZaloProvider, accountId: String) =
        mapOf("provider" to provider.raw, "accountId" to accountId)

    suspend fun assignLabel(provider: ZaloProvider, accountId: String, threadId: String, labelId: String) {
        client.request(
            "/zalo/labels/conversations/${enc(threadId)}/${enc(labelId)}", "POST", EMPTY_BODY,
            conversationQuery(provider, accountId)
        )
    }

    suspend fun removeLabel(provider: ZaloProvider, accountId: String, threadId: String, labelId: String) {
        client.request(
            "/zalo/labels/conversations/${enc(threadId)}/${enc(labelId)}", "DELETE",
            query = conversationQuery(provider, accountId)
        )
    }

    suspend fun fetchConversationLabels(provider: ZaloProvider, accountId: String, threadId: String): List<ZaloCustomLabelModel> {
        val res = client.request("/zalo/labels/conversations/${enc(threadId)}", query = conversationQuery(provider, accountId))
        return extractArray(res).map { ZaloCustomLabelModel(it) }
    }

    // MARK: - Campaigns

    suspend fun fetchCampaigns(): List<ZaloCampaignModel> =
        extractArray(client.request("/zalo/campaigns")).map { ZaloCampaignModel(it) }

    suspend fun getCampaign(id: String): ZaloCampaignModel = ZaloCampaignModel(client.request("/zalo/campaigns/${enc(id)}"))

    suspend fun createCampaign(configJson: String): JSONValue = client.request("/zalo/campaigns", "POST", configJson)

    suspend fun deleteCampaign(id: String) {
        client.request("/zalo/campaigns/${enc(id)}", "DELETE")
    }

    suspend fun startCampaign(id: String) {
        client.request("/zalo/campaigns/${enc(id)}/start", "POST", EMPTY_BODY)
    }

    suspend fun pauseCampaign(id: String) {
        client.request("/zalo/campaigns/${enc(id)}/pause", "POST", EMPTY_BODY)
    }

    suspend fun resumeCampaign(id: String) {
        client.request("/zalo/campaigns/${enc(id)}/resume", "POST", EMPTY_BODY)
    }

    suspend fun cancelCampaign(id: String) {
        client.request("/zalo/campaigns/${enc(id)}/cancel", "POST", EMPTY_BODY)
    }

    suspend fun rerunCampaign(id: String): JSONValue = client.request("/zalo/campaigns/${enc(id)}/rerun", "POST", EMPTY_BODY)

    suspend fun getCampaignJobs(id: String, status: ZaloJobStatus, limit: Int = 100, offset: Int = 0): Pair<List<ZaloCampaignJobModel>, Int> {
        val query = mutableMapOf("limit" to "$limit", "offset" to "$offset")
        if (status.raw.isNotEmpty()) query["status"] = status.raw
        val res = client.request("/zalo/campaigns/${enc(id)}/jobs", query = query)
        return extractArray(res, "jobs").map { ZaloCampaignJobModel(it) } to res["total"].int
    }

    suspend fun getCampaignLogs(id: String, level: ZaloLogLevel, limit: Int = 100, offset: Int = 0): Pair<List<ZaloCampaignLogModel>, Int> {
        val query = mutableMapOf("limit" to "$limit", "offset" to "$offset")
        if (level.raw.isNotEmpty()) query["level"] = level.raw
        val res = client.request("/zalo/campaigns/${enc(id)}/logs", query = query)
        return extractArray(res, "logs").map { ZaloCampaignLogModel(it) } to res["total"].int
    }

    suspend fun getMyGroups(accountId: String? = null): List<ZaloGroupInfoModel> {
        val query = accountId?.let { mapOf("accountId" to it) } ?: emptyMap()
        return extractArray(client.request("/zalo/campaigns/my-groups", query = query)).map { ZaloGroupInfoModel(it) }
    }

    suspend fun scanGroupLink(groupLink: String, accountId: String? = null): ZaloGroupInfoModel {
        val body = buildJsonObject {
            put("groupLink", groupLink)
            if (accountId != null) put("accountId", accountId)
        }.toString()
        return ZaloGroupInfoModel(client.request("/zalo/campaigns/scan-group-link", "POST", body))
    }

    suspend fun fetchCrmBootstrap(): JSONValue = client.request("/zalo/campaigns/crm/bootstrap")

    suspend fun createCrmRecipientSnapshot(filters: JsonElement): JSONValue =
        client.request("/zalo/campaigns/crm/recipient-snapshots", "POST", filters.toString())

    // MARK: - Helpers

    /** POSTs without a payload still send `{}` so the JSON body parser on the server is happy. */
    private const val EMPTY_BODY = "{}"

    /** Path segment encoding (phone numbers like `+84…`, WhatsApp JIDs with `@`). */
    private fun enc(segment: String): String = android.net.Uri.encode(segment)
}
