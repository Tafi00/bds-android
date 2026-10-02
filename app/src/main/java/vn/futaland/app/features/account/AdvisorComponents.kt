package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.DocumentUpload
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import java.net.URLEncoder

// Shared building blocks for the advisor (tư vấn viên) screens: header, grouped sections,
// labeled rows, document uploads and bank-transfer details (iOS AdvisorViews.swift).

@Composable
fun AdvisorTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
            }
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
    }
}

/** Grouped section like an iOS inset-grouped `Section`: optional header, card body, optional footer. */
@Composable
fun AdvisorSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!header.isNullOrEmpty()) {
            Text(
                text = header,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        FutaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        if (!footer.isNullOrEmpty()) {
            Text(
                text = footer,
                fontSize = 11.5.sp,
                color = FutaColors.Slate,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Label on the left, value on the right (iOS `LabeledContent`). Values are shown as written. */
@Composable
fun AdvisorLabeledRow(label: String, value: String, valueColor: Color = FutaColors.Navy, bold: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
        VerbatimText(
            text = value.ifEmpty { "-" },
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
fun AdvisorLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) { FutaSkeletonBlock(height = 96.dp, radius = 16.dp) }
    }
}

@Composable
fun AdvisorErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FutaEmptyState(
        title = "Không tải được dữ liệu",
        message = message,
        icon = Icons.Default.ErrorOutline,
        modifier = modifier,
        actionButton = {
            FutaButton(text = "Thử lại", onClick = onRetry, variant = FutaButtonVariant.OUTLINE, icon = Icons.Default.Refresh)
        }
    )
}

/** Small colored capsule used for statuses. */
@Composable
fun AdvisorBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

fun copyToClipboard(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("FutaLand", text))
    ToastCenter.show(tr(toast))
}

/** "yyyy-MM-dd…" → "yyyy-MM-dd", the same short date the iOS advisor screens show. */
fun shortDate(iso: String): String = if (iso.length >= 10) iso.take(10) else iso

/**
 * Image document row with preview, pick (photo library), optional camera capture, replace and
 * remove. The upload happens here; [onUploaded] receives the absolute URL.
 */
@Composable
fun DocumentUploadRow(
    title: String,
    url: String,
    enabled: Boolean = true,
    allowCamera: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onUploadingChange: (Boolean) -> Unit = {},
    uploadName: String = "document",
    successMessage: String = "Tải lên tài liệu thành công",
    onUploaded: suspend (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    fun upload(uri: Uri) {
        scope.launch {
            uploading = true
            onUploadingChange(true)
            try {
                val uploaded = DocumentUpload.uploadImage(context, uri, uploadName)
                onUploaded(uploaded)
                ToastCenter.show(tr(successMessage))
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi tải lên: {0}", e.message.orEmpty()), isError = true)
            } finally {
                uploading = false
                onUploadingChange(false)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) upload(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null) upload(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.5.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            when {
                uploading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FutaColors.BrandGreen)
                url.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Đã tải", fontSize = 11.5.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (url.isNotEmpty()) {
            AsyncImage(
                model = DocumentUpload.absoluteUrl(url),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val actionsEnabled = enabled && !uploading
            UploadAction(
                icon = Icons.Default.AddPhotoAlternate,
                label = if (url.isEmpty()) "Chọn ảnh tài liệu" else "Thay đổi ảnh",
                enabled = actionsEnabled
            ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (allowCamera) {
                UploadAction(icon = Icons.Default.PhotoCamera, label = "Chụp ảnh", enabled = actionsEnabled) {
                    val uri = DocumentUpload.newCaptureUri(context)
                    captureUri = uri
                    camera.launch(uri)
                }
            }
            if (onRemove != null && url.isNotEmpty()) {
                UploadAction(icon = Icons.Default.Delete, label = "Xóa", enabled = actionsEnabled, color = Color(0xFFDC2626), onClick = onRemove)
            }
        }
    }
}

@Composable
private fun UploadAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    color: Color = FutaColors.BrandGreen,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (enabled) color else FutaColors.Slate.copy(alpha = 0.5f)
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.5.sp, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** Receiving bank account from CMS Site Settings (`systemConfig.banking`). */
data class PackageBankAccount(val name: String, val code: String, val number: String, val holder: String) {
    val isConfigured: Boolean get() = name.isNotEmpty() && number.isNotEmpty()

    fun qrUrl(reference: String, amount: Double): String? {
        if (code.isEmpty() || number.isEmpty()) return null
        val params = mutableListOf<String>()
        if (amount > 0) params.add("amount=${amount.toLong()}")
        if (reference.isNotEmpty()) params.add("addInfo=${URLEncoder.encode(reference, "UTF-8")}")
        if (holder.isNotEmpty()) params.add("accountName=${URLEncoder.encode(holder, "UTF-8")}")
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "https://img.vietqr.io/image/$code-$number-compact2.png$query"
    }

    companion object {
        fun from(banking: JSONValue) = PackageBankAccount(
            name = banking["bankName"].string.trim(),
            code = banking["bankCode"].string.trim(),
            number = banking["accountNumber"].string.trim(),
            holder = banking["accountHolder"].string.trim()
        )
    }
}

/** QR code plus account details for a package transfer (iOS `PackageTransferSections`). */
@Composable
fun PackageTransferSection(account: PackageBankAccount, amount: Double, reference: String, showsHint: Boolean = true) {
    val context = LocalContext.current
    if (!account.isConfigured) {
        AdvisorSection {
            Text(
                "Chưa có thông tin tài khoản nhận thanh toán. Vui lòng liên hệ bộ phận hỗ trợ để được hướng dẫn chuyển khoản.",
                fontSize = 13.sp,
                color = FutaColors.Slate
            )
        }
        return
    }
    account.qrUrl(reference, amount)?.let { qr ->
        AdvisorSection {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = qr,
                    contentDescription = tr("Mã VietQR"),
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quét mã VietQR để chuyển khoản đúng số tiền và nội dung",
                    fontSize = 12.sp,
                    color = FutaColors.Slate,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    AdvisorSection(
        footer = if (showsHint) "Mã QR đã điền sẵn nội dung chuyển khoản theo ID khách hàng của bạn. Vui lòng kiểm tra số tiền trước khi xác nhận giao dịch." else null
    ) {
        AdvisorLabeledRow("Ngân hàng", account.name)
        if (account.holder.isNotEmpty()) AdvisorLabeledRow("Chủ tài khoản", account.holder)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Số tài khoản", fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
            VerbatimText(account.number, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            IconButton(onClick = { copyToClipboard(context, account.number, "Đã sao chép số tài khoản") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, tr("Sao chép số tài khoản"), tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
            }
        }
        if (amount > 0) AdvisorLabeledRow("Số tiền thanh toán", LocalizedPrice.full(amount))
        if (reference.isNotEmpty()) {
            Text("Nội dung chuyển khoản", fontSize = 13.sp, color = FutaColors.Slate)
            VerbatimText(reference, fontSize = 13.5.sp, color = FutaColors.Navy, style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
fun PackageCopyReferenceButton(reference: String) {
    val context = LocalContext.current
    FutaButton(
        text = "Sao chép cú pháp chuyển khoản",
        variant = FutaButtonVariant.OUTLINE,
        icon = Icons.Default.ContentCopy,
        onClick = { copyToClipboard(context, reference, "Đã sao chép cú pháp chuyển khoản") },
        modifier = Modifier.fillMaxWidth()
    )
}
