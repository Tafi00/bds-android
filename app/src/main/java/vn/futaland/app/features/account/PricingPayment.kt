package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedPrice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

/** Receiving account for package payments (CMS Site Settings → systemConfig.banking). */
data class PricingBankingInfo(
    val bankName: String,
    val bankCode: String,
    val accountNumber: String,
    val accountHolder: String,
    val prefix: String
) {
    val isComplete: Boolean get() = bankCode.isNotEmpty() && accountNumber.isNotEmpty()

    companion object {
        fun from(settings: JSONValue): PricingBankingInfo {
            val sys = settings["systemConfig"].takeIf { !it.isNull } ?: settings
            val b = sys["banking"]
            return PricingBankingInfo(
                bankName = b["bankName"].string,
                bankCode = b["bankCode"].string,
                accountNumber = b["accountNumber"].string,
                accountHolder = b["accountHolder"].string,
                prefix = b["transferSyntaxPrefix"].string.trim().ifEmpty { "FUTAPACK" }
            )
        }
    }
}

/** Transfer content the reconciliation matches on: "<prefix> <orderId>". */
fun pricingTransferReference(order: JSONValue, banking: PricingBankingInfo?): String =
    "${banking?.prefix ?: "FUTAPACK"} ${order.id}".trim()

/**
 * QR + bank transfer instructions for a server-created pricing order. The amount
 * and order id always come from the order returned by `/pricing/checkout` or
 * `/pricing/me`, never from a client-side calculation.
 */
@Composable
fun PricingPaymentDetails(order: JSONValue) {
    val context = LocalContext.current
    var banking by remember { mutableStateOf<PricingBankingInfo?>(null) }
    var bankingLoaded by remember { mutableStateOf(false) }
    var copiedItem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val res = APIClient.get().request("/cms/settings")
            banking = PricingBankingInfo.from(if (res["data"].isNull) res else res["data"])
        } catch (_: Exception) {
            // Without CMS bank data the transfer block stays hidden; never invent an account.
        }
        bankingLoaded = true
    }

    val amount = order["amount"].double
    val reference = pricingTransferReference(order, banking)

    fun copy(title: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(title, value))
        copiedItem = title
        ToastCenter.show(tr("Đã sao chép {0}", tr(title)))
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.QrCodeScanner, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(6.dp))
            Text("Quét mã VietQR để thanh toán", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mở ứng dụng ngân hàng và quét mã để thanh toán tự động đúng số tiền và nội dung.",
                fontSize = 12.sp,
                color = FutaColors.Slate,
                textAlign = TextAlign.Center
            )
        }

        val bank = banking
        if (!bankingLoaded) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FutaColors.BrandGreen, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else if (bank == null || !bank.isComplete) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                Text(
                    "Thông tin chuyển khoản đang được cập nhật. Vui lòng liên hệ hotline FUTA Land.",
                    fontSize = 12.sp,
                    color = FutaColors.Slate,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            val qrUrl = "https://img.vietqr.io/image/${bank.bankCode}-${bank.accountNumber}-compact.png" +
                "?amount=${amount.toLong()}" +
                "&addInfo=${android.net.Uri.encode(reference)}" +
                "&accountName=${android.net.Uri.encode(bank.accountHolder)}"

            FutaCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = qrUrl,
                        contentDescription = tr("Mã VietQR"),
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    )
                    Text("Hỗ trợ quét trên tất cả ứng dụng ngân hàng & ví điện tử", fontSize = 11.sp, color = FutaColors.Slate)
                }
            }

            FutaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hoặc chuyển khoản thủ công", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    if (bank.bankName.isNotEmpty()) {
                        CopyableRow("Ngân hàng", bank.bankName, copiedItem == "Ngân hàng") { copy("Ngân hàng", bank.bankName) }
                    }
                    CopyableRow("Số tài khoản", bank.accountNumber, copiedItem == "Số tài khoản") { copy("Số tài khoản", bank.accountNumber) }
                    if (bank.accountHolder.isNotEmpty()) {
                        CopyableRow("Chủ tài khoản", bank.accountHolder, copiedItem == "Chủ tài khoản") { copy("Chủ tài khoản", bank.accountHolder) }
                    }
                    CopyableRow("Số tiền", LocalizedPrice.full(amount), copiedItem == "Số tiền") { copy("Số tiền", amount.toLong().toString()) }
                    CopyableRow("Nội dung CK", reference, copiedItem == "Nội dung CK", highlight = true) { copy("Nội dung CK", reference) }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FutaColors.BrandOrange.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Info, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(18.dp))
            Text(
                "Vui lòng giữ nguyên nội dung chuyển khoản để hệ thống tự động ghi nhận và kích hoạt gói trong vòng vài phút.",
                fontSize = 12.sp,
                color = FutaColors.Slate
            )
        }
    }
}

@Composable
private fun CopyableRow(title: String, value: String, copied: Boolean, highlight: Boolean = false, onCopy: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.5.sp, color = FutaColors.Slate)
        Spacer(Modifier.width(8.dp))
        VerbatimText(
            value,
            fontSize = 12.5.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) FutaColors.BrandGreen else FutaColors.Navy,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp).background(FutaColors.BrandGreen.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = tr("Sao chép"),
                tint = FutaColors.BrandGreen,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Bottom sheet that reopens the transfer details of an existing order. */
@Composable
fun PricingPaymentSheet(order: JSONValue?, onDismiss: () -> Unit) {
    if (order == null) return
    FutaBottomSheet(visible = true, onDismiss = onDismiss, title = "Thông tin chuyển khoản") {
        PricingPaymentDetails(order)
        Spacer(Modifier.height(12.dp))
    }
}
