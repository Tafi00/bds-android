package vn.futaland.app.features.properties

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.ToastCenter

/**
 * "Hồ sơ pháp lý" panel (iOS PropertyDetailView.legalDocumentsSection / web LegalDocumentsPanel).
 * Lists every document attached to the property; when the property has none, falls back to the
 * project's documents from `GET /projects/{projectId}`. Documents open in the browser.
 */
@Composable
fun PropertyLegalDocumentsCard(property: JSONValue) {
    val context = LocalContext.current
    val propertyDocs = property["legalDocuments"].array
    val projectId = property["projectId"].string
    var projectDocs by remember(projectId) { mutableStateOf<List<JSONValue>>(emptyList()) }

    LaunchedEffect(projectId, propertyDocs.isEmpty()) {
        if (propertyDocs.isNotEmpty() || projectId.isEmpty()) return@LaunchedEffect
        projectDocs = try {
            val res = APIClient.get().request("/projects/${Uri.encode(projectId)}")
            val project = if (res["data"].isNull) res else res["data"]
            project["legalDocuments"].array
        } catch (_: Exception) {
            emptyList()
        }
    }

    val docs = propertyDocs.ifEmpty { projectDocs }.filter { it["url"].string.isNotEmpty() || it["name"].string.isNotEmpty() }
    val legalStatus = property["legalStatus"].string.ifEmpty { property["ownershipType"].string }

    FutaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, null, tint = FutaColors.PanelTitle, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "HỒ SƠ PHÁP LÝ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.PanelTitle,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (docs.isNotEmpty()) {
                    Surface(shape = CircleShape, color = FutaColors.MintBg) {
                        Text(
                            tr("{0} tài liệu", docs.size),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (legalStatus.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FutaColors.MintBg.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .border(1.dp, FutaColors.BrandGreen.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tình trạng pháp lý", fontSize = 11.sp, color = FutaColors.Slate)
                        Text(legalStatus, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    }
                    Text(
                        "Xác minh",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen,
                        modifier = Modifier.background(FutaColors.MintBg, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            if (docs.isEmpty()) {
                // Honest empty state, no mock documents.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PendingActions, null, tint = FutaColors.Slate, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Chưa có hồ sơ pháp lý đính kèm", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        Text("Hồ sơ pháp lý đang được cập nhật bởi chủ đầu tư", fontSize = 11.sp, color = FutaColors.Slate)
                    }
                }
            } else {
                docs.forEachIndexed { idx, doc ->
                    val name = doc["name"].string.ifEmpty { tr("Tài liệu pháp lý {0}", idx + 1) }
                    val url = doc["url"].string
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD7DCE2)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val resolved = resolveDocumentUrl(url)
                            if (resolved == null) {
                                ToastCenter.show(tr("Tài liệu pháp lý đang được cập nhật bản scan số."))
                            } else {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved)))
                                } catch (_: Exception) {
                                    ToastCenter.show(tr("Không thể mở tài liệu: {0}", name), isError = true)
                                }
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DocumentTypeBadge(name = name, mimeType = doc["mimeType"].string)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                VerbatimText(
                                    name.translated("project"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FutaColors.Navy,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                VerbatimText(formatDocFileSize(doc["size"].double.toLong(), name), fontSize = 11.sp, color = FutaColors.Slate)
                            }
                            Spacer(Modifier.width(4.dp))
                            Row(
                                modifier = Modifier.background(FutaColors.MintBg, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Visibility, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Xem", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTypeBadge(name: String, mimeType: String) {
    val lower = name.lowercase()
    val isImage = listOf(".png", ".jpg", ".jpeg", ".webp").any { lower.endsWith(it) } || mimeType.contains("image")
    val isPdf = lower.endsWith(".pdf") || mimeType == "application/pdf" || (!isImage && mimeType.isEmpty())
    val (bg, fg, icon, label) = when {
        isImage -> Quad(Color(0xFF2563EB).copy(alpha = 0.12f), Color(0xFF2563EB), Icons.Default.Image, "ẢNH")
        isPdf -> Quad(FutaColors.RedPdfBg, FutaColors.RedPdf, Icons.Default.Description, "PDF")
        else -> Quad(Color.Gray.copy(alpha = 0.15f), Color.Gray, Icons.Default.InsertDriveFile, "FILE")
    }
    Box(modifier = Modifier.size(44.dp).background(bg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(
                label,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.background(fg, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp)
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** Same output as iOS formatDocFileSize: "1.2 MB", "340 KB" or the file extension. */
private fun formatDocFileSize(size: Long, name: String): String {
    if (size <= 0) {
        val ext = name.substringAfterLast('.', "").uppercase()
        return if (ext.isEmpty() || ext.length > 5) tr("TÀI LIỆU") else ext
    }
    if (size >= 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    return "${Math.ceil(size / 1024.0).toLong()} KB"
}

/** Absolute URLs pass through; "/uploads/…" paths resolve against the public web origin. */
internal fun resolveDocumentUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val origin = APIClient.apiBaseUrl.removeSuffix("/api")
    return if (trimmed.startsWith("/")) "$origin$trimmed" else "$origin/$trimmed"
}
