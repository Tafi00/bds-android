package vn.futaland.app.core.network

import vn.futaland.app.core.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Upload of identity / payment documents through `POST /upload/document` (field `document`),
 * same as iOS: images are re-encoded as JPEG so the bytes always match the declared MIME type
 * (the backend checks the real format) and stay well under the size limit.
 */
object DocumentUpload {
    private const val MAX_SIDE = 2000

    /**
     * The backend validates document links as absolute URLs, but local-storage uploads come
     * back as "/media/..."; resolve those against the public origin (iOS `resolveDocumentURL`).
     */
    fun absoluteUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) return trimmed
        return APIClient.publicWebUrl + (if (trimmed.startsWith("/")) trimmed else "/$trimmed")
    }

    /** Uploads the picked image and returns its absolute URL. */
    suspend fun uploadImage(context: Context, uri: Uri, name: String): String {
        val jpeg = withContext(Dispatchers.IO) { encodeJpeg(context, uri) }
            ?: throw APIError(0, tr("Không đọc được ảnh đã chọn"))
        return uploadJpeg(jpeg, name)
    }

    suspend fun uploadJpeg(jpeg: ByteArray, name: String): String {
        val res = APIClient.get().upload(
            data = jpeg,
            filename = "$name.jpg",
            mimeType = "image/jpeg",
            path = "/upload/document",
            field = "document"
        )
        val url = absoluteUrl(res["data"]["url"].string.ifEmpty { res["url"].string })
        if (url.isEmpty()) throw APIError(0, tr("Không nhận được đường dẫn tệp"))
        return url
    }

    /** Fresh cache file (exposed through the app FileProvider) for a camera capture. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun encodeJpeg(context: Context, uri: Uri): ByteArray? {
        val bitmap = decode(context, uri) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies the EXIF orientation of camera photos.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()
}
