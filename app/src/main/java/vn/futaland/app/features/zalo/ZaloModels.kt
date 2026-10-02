package vn.futaland.app.features.zalo

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonObject
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.JSONValue
import java.util.Locale

// Models of the Zalo / WhatsApp messaging module (iOS ZaloModels.swift). Every model wraps the raw
// server JSON and derives its fields lazily, so unknown shapes never crash the screens.
// Titles are Vietnamese source strings; `Text` translates them at display time.

// MARK: - Enums

enum class ZaloProvider(val raw: String, val displayName: String, val brandColor: Color) {
    ZALO("zalo", "Zalo", Color(0xFF0068FF)),
    WHATSAPP("whatsapp", "WhatsApp", Color(0xFF26C759));

    companion object {
        fun from(raw: String): ZaloProvider = entries.firstOrNull { it.raw == raw } ?: ZALO
    }
}

enum class ZaloConversationFilter(val raw: String, val title: String) {
    ALL("all", "Tất cả"),
    UNREAD("unread", "Chưa đọc"),
    AWAITING_REPLY("awaiting_reply", "Chờ khách"),
    NEEDS_RESPONSE("needs_response", "Cần trả lời")
}

enum class ZaloSortOrder(val raw: String, val title: String) {
    NEWEST("newest", "Mới nhất"),
    OLDEST("oldest", "Cũ nhất")
}

enum class ZaloCampaignStatus(val raw: String, val title: String, val color: Color) {
    PENDING("pending", "Chờ xử lý", Color(0xFF64748B)),
    RUNNING("running", "Đang chạy", Color(0xFF16A34A)),
    PAUSED_SCHEDULE("paused_schedule", "Tạm dừng (lịch)", Color(0xFFEA580C)),
    PAUSED_DAILY_LIMIT("paused_daily_limit", "Tạm dừng (giới hạn)", Color(0xFFEA580C)),
    PAUSED_MANUAL("paused_manual", "Tạm dừng", Color(0xFFEA580C)),
    COMPLETED("completed", "Hoàn thành", Color(0xFF2563EB)),
    FAILED("failed", "Thất bại", Color(0xFFDC2626));

    /** Sort rank used by the "Trạng thái" ordering of the campaign list. */
    val order: Int
        get() = when (this) {
            RUNNING -> 0
            PAUSED_SCHEDULE -> 1
            PAUSED_DAILY_LIMIT -> 2
            PAUSED_MANUAL -> 3
            PENDING -> 4
            COMPLETED -> 5
            FAILED -> 6
        }

    /** A running campaign must be paused or cancelled before it can be deleted. */
    val canDelete: Boolean get() = this != RUNNING

    companion object {
        fun from(raw: String): ZaloCampaignStatus = entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

enum class ZaloJobStatus(val raw: String, val title: String, val color: Color) {
    ALL("", "Tất cả", Color(0xFF123355)),
    PENDING("pending", "Chờ gửi", Color(0xFF64748B)),
    PROCESSING("processing", "Đang gửi", Color(0xFF2563EB)),
    SUCCESS("success", "Thành công", Color(0xFF16A34A)),
    FAILED("failed", "Thất bại", Color(0xFFDC2626)),
    SKIPPED("skipped", "Bỏ qua", Color(0xFFEA580C));

    companion object {
        fun from(raw: String): ZaloJobStatus = entries.firstOrNull { it.raw == raw && it != ALL } ?: PENDING
    }
}

enum class ZaloLogLevel(val raw: String, val title: String, val color: Color) {
    ALL("", "Tất cả", Color(0xFF123355)),
    INFO("info", "Thông tin", Color(0xFF2563EB)),
    WARN("warn", "Cảnh báo", Color(0xFFEA580C)),
    ERROR("error", "Lỗi", Color(0xFFDC2626));

    companion object {
        fun from(raw: String): ZaloLogLevel = entries.firstOrNull { it.raw == raw && it != ALL } ?: INFO
    }
}

enum class ZaloQRStatus(val title: String) {
    IDLE("Khởi tạo"),
    WAITING("Chờ quét mã QR"),
    SCANNED("Đã quét! Vui lòng xác nhận trên điện thoại"),
    SUCCESS("Đăng nhập thành công!"),
    EXPIRED("Mã QR đã hết hạn"),
    DECLINED("Đã từ chối đăng nhập"),
    ERROR("Lỗi kết nối QR")
}

// MARK: - Helpers

private fun String.nonEmpty(): String? = ifEmpty { null }

/** Copy of [raw] with [key] replaced, for the local optimistic updates iOS does on `raw.object`. */
internal fun JSONValue.updating(key: String, value: Any?): JSONValue = withUpdates(mapOf(key to value))

private val JSONValue.isObject: Boolean get() = element is JsonObject

private fun JSONValue.has(key: String): Boolean = (element as? JsonObject)?.containsKey(key) == true

// MARK: - Models

data class ZaloAccountModel(val raw: JSONValue) {
    val zaloId: String
        get() = raw["zaloId"].string.nonEmpty() ?: raw["externalAccountId"].string.nonEmpty() ?: raw.id
    val id: String get() = zaloId.ifEmpty { raw.id }
    val displayName: String
        get() = raw["displayName"].string.nonEmpty() ?: tr("Tài khoản {0}", zaloId)
    val avatar: String get() = raw["avatar"].string
    val isOnline: Boolean get() = raw["isOnline"].bool
    val provider: ZaloProvider get() = ZaloProvider.from(raw["provider"].string)
    val campaignOnlyInbound: Boolean get() = raw["campaignOnlyInbound"].bool
}

data class ZaloConversationModel(val raw: JSONValue) {
    val accountId: String get() = raw["accountId"].string
    val threadId: String get() = raw["threadId"].string
    val id: String get() = "$accountId:$threadId"
    val provider: ZaloProvider get() = ZaloProvider.from(raw["provider"].string)
    val unreadCount: Int get() = raw["unreadCount"].int
    val sendError: String get() = raw["sendError"].string

    val userRaw: JSONValue get() = raw["user"]
    /** Nickname set by staff wins over the customer's own display name. */
    val userName: String
        get() = userRaw["nickname"].string.nonEmpty() ?: userRaw["displayName"].string.nonEmpty() ?: tr("Khách hàng")
    val userNickname: String get() = userRaw["nickname"].string
    val userAvatar: String get() = userRaw["avatar"].string
    val userPhone: String get() = userRaw["phoneNumber"].string.nonEmpty() ?: userRaw["phone"].string
    val userId: String get() = userRaw["zaloId"].string.nonEmpty() ?: threadId

    val lastMessageRaw: JSONValue get() = raw["lastMessage"]
    val lastMessageTime: String get() = lastMessageRaw["timestamp"].string
    val lastMessageIsSelf: Boolean get() = lastMessageRaw["isSelf"].bool

    /**
     * Preview line of the last message. Placeholders (`[Hình ảnh]`…) are Vietnamese source strings
     * the caller translates; real text is returned as written.
     */
    val lastMessagePreview: ZaloPreview
        get() {
            val last = lastMessageRaw
            if (last["isUndone"].bool) return ZaloPreview.Placeholder("Tin nhắn đã được thu hồi")
            val content = last["content"]
            content.string.nonEmpty()?.let { return ZaloPreview.Verbatim(it) }
            content["title"].string.nonEmpty()?.let { return ZaloPreview.Verbatim(it) }
            content["description"].string.nonEmpty()?.let { return ZaloPreview.Verbatim(it) }
            val type = last["msgType"].string
            val atts = last["attachments"].array
            if (type.contains("video") || content.has("videoUrl") || atts.any { it["type"].string == "video" }) {
                return ZaloPreview.Placeholder("[Video]")
            }
            if (type.contains("image") || type.contains("photo") || content.has("thumb") || content.has("hdUrl") ||
                atts.any { it["type"].string == "image" }
            ) return ZaloPreview.Placeholder("[Hình ảnh]")
            if (type.contains("audio") || type.contains("voice")) return ZaloPreview.Placeholder("[Ghi âm]")
            if (type.contains("file") || content.has("fileUrl") || atts.any { it["type"].string == "file" }) {
                val name = content["fileName"].string.nonEmpty() ?: atts.firstOrNull()?.get("fileName")?.string.orEmpty()
                return if (name.isEmpty()) ZaloPreview.Placeholder("[Tệp đính kèm]") else ZaloPreview.File(name)
            }
            if (type.contains("sticker")) return ZaloPreview.Placeholder("[Nhãn dán]")
            return ZaloPreview.Placeholder("[Tin nhắn]")
        }
}

sealed interface ZaloPreview {
    data class Verbatim(val text: String) : ZaloPreview
    data class Placeholder(val source: String) : ZaloPreview
    data class File(val name: String) : ZaloPreview
}

data class ZaloMediaImageItem(val fullUrl: String, val thumbUrl: String, val caption: String)
data class ZaloMediaVideoItem(val videoUrl: String, val thumbUrl: String, val title: String, val duration: Int)
data class ZaloMediaFileItem(val fileUrl: String, val fileName: String, val size: Long, val isPdf: Boolean) {
    val formattedSize: String get() = formatByteCount(size)
}
data class ZaloLinkCardItem(val href: String, val title: String, val description: String, val thumbUrl: String)
data class ZaloQuoteModel(val msg: String, val fromD: String)

data class ZaloAttachmentModel(val raw: JSONValue) {
    val type: String get() = raw["type"].string
    val url: String get() = raw["url"].string.nonEmpty() ?: raw["fileUrl"].string
    val thumb: String get() = raw["thumb"].string.nonEmpty() ?: raw["thumbUrl"].string
    val fileName: String get() = raw["fileName"].string.nonEmpty() ?: raw["title"].string
    val size: Long get() = raw["size"].int.toLong().takeIf { it > 0 } ?: raw["totalSize"].int.toLong()
    val mimeType: String get() = raw["mimeType"].string

    val isVideo: Boolean
        get() {
            if (type == "video" || mimeType.startsWith("video/")) return true
            val lower = url.lowercase(Locale.ROOT)
            return listOf(".mp4", ".mov", ".avi", ".webm", ".mkv", ".3gp").any { lower.contains(it) }
        }

    val isAudio: Boolean
        get() {
            if (type == "audio" || type == "voice" || mimeType.startsWith("audio/")) return true
            val lower = url.lowercase(Locale.ROOT)
            return listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg").any { lower.contains(it) }
        }

    val isImage: Boolean
        get() {
            if (isVideo || isAudio) return false
            if (type == "image" || type == "photo" || mimeType.startsWith("image/")) return true
            val lower = (url + thumb).lowercase(Locale.ROOT)
            return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic").any { lower.contains(it) }
        }

    val isPdf: Boolean
        get() = mimeType == "application/pdf" || url.lowercase(Locale.ROOT).contains(".pdf") ||
            fileName.lowercase(Locale.ROOT).endsWith(".pdf")

    val isDocument: Boolean get() = !isImage && !isVideo && !isAudio && (url.isNotEmpty() || fileName.isNotEmpty())
}

/**
 * One chat message. Media extraction mirrors iOS so a message renders the same set of images,
 * videos, voice notes, files and link cards on both platforms.
 */
data class ZaloMessageModel(val raw: JSONValue) {
    val accountId: String get() = raw["accountId"].string
    val threadId: String get() = raw["threadId"].string
    val msgId: String get() = raw["msgId"].string
    val cliMsgId: String get() = raw["cliMsgId"].string
    val fromId: String get() = raw["fromId"].string
    val msgType: String get() = raw["msgType"].string
    val isSelf: Boolean get() = raw["isSelf"].bool
    val isUndone: Boolean get() = raw["isUndone"].bool
    val timestamp: String get() = raw["timestamp"].string

    val id: String
        get() = msgId.nonEmpty() ?: raw["_id"].string.nonEmpty() ?: cliMsgId.nonEmpty()
            ?: "${accountId}_${threadId}_${fromId}_$timestamp"

    private val content: JSONValue get() = raw["content"]
    private val contentIsObject: Boolean get() = content.isObject

    val attachments: List<ZaloAttachmentModel> get() = raw["attachments"].array.map { ZaloAttachmentModel(it) }

    val quote: ZaloQuoteModel?
        get() {
            if (isUndone) return null
            val q = raw["quote"]
            val msg = q["msg"].string
            if (q.isNull || msg.isEmpty()) return null
            return ZaloQuoteModel(msg, q["fromD"].string)
        }

    /** Message text, empty when the message only carries media (placeholders are suppressed). */
    val textContent: String
        get() {
            if (isUndone) return ""
            val str = content.string
            if (str.isNotEmpty()) {
                val hasMedia = mediaImages.isNotEmpty() || mediaVideos.isNotEmpty() ||
                    mediaFiles.isNotEmpty() || mediaAudio.isNotEmpty()
                if (hasMedia && str in setOf("[Hình ảnh]", "[Video]", "[Tệp đính kèm]", "[Tin nhắn]", "[Ghi âm]")) {
                    return ""
                }
                return str
            }
            if (!contentIsObject) return ""
            if (content["action"].string == "rtf") content["title"].string.nonEmpty()?.let { return it }
            if (mediaImages.isEmpty() && mediaVideos.isEmpty() && mediaFiles.isEmpty() && mediaAudio.isEmpty() &&
                linkCard == null
            ) {
                content["title"].string.nonEmpty()?.let { return it }
                content["description"].string.nonEmpty()?.let { return it }
                content["msg"].string.nonEmpty()?.let { return it }
            }
            return ""
        }

    /** Deduplicated images (thumb / normal / hd of one photo render once). */
    val mediaImages: List<ZaloMediaImageItem>
        get() {
            if (isUndone) return emptyList()
            val items = mutableListOf<ZaloMediaImageItem>()
            val seen = mutableSetOf<String>()
            if (contentIsObject) {
                val hd = content["hdUrl"].string
                val normal = content["normalUrl"].string
                val thumb = content["thumb"].string.nonEmpty() ?: content["thumbUrl"].string
                val cUrl = content["url"].string
                val best = hd.nonEmpty() ?: normal.nonEmpty() ?: cUrl.nonEmpty() ?: thumb
                val bestThumb = thumb.nonEmpty() ?: best
                val caption = content["title"].string.nonEmpty() ?: content["description"].string
                val lowerUrl = cUrl.lowercase(Locale.ROOT)
                val isContentVideo = msgType == "chat.video.msg" || content["type"].string == "video" ||
                    (cUrl.isNotEmpty() && (lowerUrl.contains(".mp4") || lowerUrl.contains(".mov")))
                val lowerBest = best.lowercase(Locale.ROOT)
                val looksLikeImage = msgType.contains("photo") || msgType.contains("image") || thumb.isNotEmpty() ||
                    hd.isNotEmpty() || normal.isNotEmpty() ||
                    listOf(".jpg", ".png", ".webp", ".jpeg").any { lowerBest.contains(it) }
                if (best.isNotEmpty() && !isContentVideo && !msgType.contains("audio") && !msgType.contains("voice") &&
                    content["type"].string != "audio" && looksLikeImage
                ) {
                    items += ZaloMediaImageItem(best, bestThumb, caption)
                    seen += best
                    seen += bestThumb
                }
            }
            for (att in attachments) {
                if (!att.isImage || att.url.isEmpty() || att.url in seen) continue
                val t = att.thumb.nonEmpty() ?: att.url
                items += ZaloMediaImageItem(att.url, t, att.fileName)
                seen += att.url
                seen += t
            }
            return items
        }

    val mediaVideos: List<ZaloMediaVideoItem>
        get() {
            if (isUndone) return emptyList()
            val items = mutableListOf<ZaloMediaVideoItem>()
            val seen = mutableSetOf<String>()
            if (contentIsObject) {
                val href = content["href"].string
                val lowerHref = href.lowercase(Locale.ROOT)
                val vUrl = content["videoUrl"].string.nonEmpty()
                    ?: if (href.isNotEmpty() && (lowerHref.contains(".mp4") || lowerHref.contains(".mov") ||
                            lowerHref.contains("/video"))
                    ) href else content["url"].string
                val lower = vUrl.lowercase(Locale.ROOT)
                val isVideoMsg = msgType == "chat.video.msg" || content["type"].string == "video" ||
                    lower.contains(".mp4") || lower.contains(".mov")
                if (isVideoMsg && vUrl.isNotEmpty() && (vUrl.startsWith("http://") || vUrl.startsWith("https://"))) {
                    val thumb = content["thumb"].string.nonEmpty() ?: content["thumbnail"].string
                    val title = content["title"].string.nonEmpty() ?: content["description"].string.nonEmpty() ?: tr("Video")
                    items += ZaloMediaVideoItem(vUrl, thumb, title, content["duration"].int)
                    seen += vUrl
                }
            }
            for (att in attachments) {
                if (!att.isVideo || att.url.isEmpty() || att.url in seen) continue
                items += ZaloMediaVideoItem(att.url, att.thumb, att.fileName.nonEmpty() ?: tr("Video"), 0)
                seen += att.url
            }
            return items
        }

    val mediaAudio: List<ZaloMediaFileItem>
        get() {
            if (isUndone) return emptyList()
            val items = mutableListOf<ZaloMediaFileItem>()
            val seen = mutableSetOf<String>()
            if (msgType.contains("audio") || msgType.contains("voice") || content["type"].string == "audio") {
                val url = content["audioUrl"].string.nonEmpty() ?: content["fileUrl"].string.nonEmpty()
                    ?: content["url"].string.nonEmpty() ?: content["href"].string
                if (url.isNotEmpty()) {
                    items += ZaloMediaFileItem(url, content["title"].string.nonEmpty() ?: tr("Ghi âm"), 0, false)
                    seen += url
                }
            }
            for (att in attachments) {
                if (!att.isAudio || att.url.isEmpty() || !seen.add(att.url)) continue
                items += ZaloMediaFileItem(att.url, att.fileName.nonEmpty() ?: tr("Ghi âm"), att.size, false)
            }
            return items
        }

    val mediaFiles: List<ZaloMediaFileItem>
        get() {
            if (isUndone) return emptyList()
            val items = mutableListOf<ZaloMediaFileItem>()
            val seen = mutableSetOf<String>()
            if (contentIsObject) {
                val fileUrl = content["fileUrl"].string
                val name = content["fileName"].string.nonEmpty() ?: content["title"].string.nonEmpty() ?: tr("Tệp đính kèm")
                val size = content["fileSize"].int.takeIf { it > 0 } ?: content["size"].int
                if (fileUrl.isNotEmpty() && mediaAudio.none { it.fileUrl == fileUrl } &&
                    (fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))
                ) {
                    val isPdf = fileUrl.lowercase(Locale.ROOT).contains(".pdf") ||
                        name.lowercase(Locale.ROOT).endsWith(".pdf")
                    items += ZaloMediaFileItem(fileUrl, name, size.toLong(), isPdf)
                    seen += fileUrl
                }
            }
            for (att in attachments) {
                if (!att.isDocument || att.url.isEmpty() || att.url in seen) continue
                items += ZaloMediaFileItem(att.url, att.fileName.nonEmpty() ?: tr("Tệp đính kèm"), att.size, att.isPdf)
                seen += att.url
            }
            return items
        }

    val linkCard: ZaloLinkCardItem?
        get() {
            if (isUndone || !contentIsObject || mediaVideos.isNotEmpty() || mediaFiles.isNotEmpty() ||
                mediaAudio.isNotEmpty()
            ) return null
            val href = content["href"].string
            if (href.isEmpty() || !href.startsWith("http")) return null
            val lower = href.lowercase(Locale.ROOT)
            if (listOf(".mp4", ".mov", ".jpg", ".png").any { lower.contains(it) }) return null
            return ZaloLinkCardItem(href, content["title"].string, content["description"].string, content["thumb"].string)
        }
}

data class ZaloContactModel(val raw: JSONValue) {
    val zaloId: String get() = raw["zaloId"].string
    val id: String get() = zaloId
    val displayName: String
        get() = raw["nickname"].string.nonEmpty() ?: raw["displayName"].string.nonEmpty() ?: zaloId
    val avatar: String get() = raw["avatar"].string
    val phoneNumber: String get() = raw["phoneNumber"].string
}

data class ZaloCustomLabelModel(val raw: JSONValue) {
    val id: String get() = raw["_id"].string.nonEmpty() ?: raw.id
    val name: String get() = raw["name"].string
    val colorHex: String get() = raw["color"].string
    val color: Color get() = zaloColorFromHex(colorHex)
}

data class ZaloCampaignModel(val raw: JSONValue) {
    val id: String get() = raw["_id"].string.nonEmpty() ?: raw.id
    val name: String get() = raw["name"].string
    val status: ZaloCampaignStatus get() = ZaloCampaignStatus.from(raw["status"].string)
    val totalJobs: Int get() = raw["totalJobs"].int
    val sentCount: Int get() = raw["sentCount"].int
    val failedCount: Int get() = raw["failedCount"].int
    val pendingCount: Int get() = raw["pendingCount"].int
    val dailySentCount: Int get() = raw["dailySentCount"].int
    val createdAt: String get() = raw["createdAt"].string
    val config: JSONValue get() = raw["config"]

    /** Processed share (sent + failed) used by the detail progress bar. */
    val progress: Float
        get() = if (totalJobs <= 0) 0f else ((sentCount + failedCount).toFloat() / totalJobs).coerceIn(0f, 1f)

    /** Sent share used by the list cards. */
    val sentProgress: Float
        get() = if (totalJobs <= 0) 0f else (sentCount.toFloat() / totalJobs).coerceIn(0f, 1f)
}

data class ZaloCampaignJobModel(val raw: JSONValue) {
    val id: String get() = raw["_id"].string.nonEmpty() ?: raw.id
    val phone: String get() = raw["phone"].string.nonEmpty() ?: raw["zaloId"].string
    val recipientName: String get() = raw["recipientName"].string
    val status: ZaloJobStatus get() = ZaloJobStatus.from(raw["status"].string)
    val error: String get() = raw["error"].string
    val sentAt: String get() = raw["sentAt"].string
    val renderedMessage: String get() = raw["renderedMessage"].string
}

data class ZaloCampaignLogModel(val raw: JSONValue) {
    val id: String
        get() = raw["_id"].string.nonEmpty() ?: raw.id.nonEmpty()
            ?: listOf("campaignId", "jobId", "timestamp", "eventCode", "phone", "message")
                .joinToString("_") { raw[it].string }
    val campaignId: String get() = raw["campaignId"].string
    val level: ZaloLogLevel get() = ZaloLogLevel.from(raw["level"].string)
    val message: String get() = raw["message"].string
    val phone: String get() = raw["phone"].string.nonEmpty() ?: raw["zaloId"].string
    val recipientName: String get() = raw["recipientName"].string
    val timestamp: String get() = raw["timestamp"].string
}

data class ZaloGroupInfoModel(val raw: JSONValue) {
    val groupId: String get() = raw["groupId"].string
    val id: String get() = groupId
    val name: String get() = raw["name"].string
    val avatar: String get() = raw["avatar"].string
    val totalMember: Int get() = raw["totalMember"].int
}

// MARK: - Shared formatting

/** `#RRGGBB` → Color, blue when the value is malformed (iOS fallback). */
fun zaloColorFromHex(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    val value = if (clean.length == 6) clean.toLongOrNull(16) else null
    return if (value == null) Color(0xFF3B82F6) else Color(0xFF000000 or value)
}

/** File size like iOS ByteCountFormatter (.file style, decimal units). */
fun formatByteCount(bytes: Long): String {
    if (bytes <= 0) return ""
    if (bytes < 1000) return "$bytes bytes"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1000.0
    var index = 0
    while (value >= 1000 && index < units.lastIndex) {
        value /= 1000
        index++
    }
    val text = if (value >= 100 || index == 0) String.format(Locale.ROOT, "%.0f", value)
    else String.format(Locale.ROOT, "%.1f", value)
    return "$text ${units[index]}"
}
