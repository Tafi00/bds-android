package vn.futaland.app.features.properties

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.sales.SalesPolicy
import vn.futaland.app.designsystem.FutaColors

/**
 * Sales-policy confirmation shown before submitting a `POST /sales/registrations`
 * request. Used by the listing workspace and the property detail screen so both
 * paths quote the same policy version.
 */
@Composable
fun SalesPolicyConfirmDialog(
    unitCode: String,
    projectName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = FutaColors.BrandOrange,
                modifier = Modifier.size(40.dp)
            )
        },
        title = { Text("Xác nhận đăng ký bán", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Trước khi đăng ký bán căn $unitCode thuộc $projectName, bạn cần đọc và đồng ý với chính sách bán hàng và quy định của FUTA Land.",
                    fontSize = 13.5.sp,
                    color = FutaColors.Slate
                )

                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Nguyên tắc ưu tiên khi có tranh chấp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Trùng khách và thứ tự giữ chỗ được đối soát theo hồ sơ hợp lệ được hệ thống ghi nhận trước, không theo thỏa thuận miệng.",
                            fontSize = 11.5.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { agreed = !agreed }
                ) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = FutaColors.BrandGreen)
                    )
                    Text(
                        text = "Tôi đồng ý với chính sách bán hàng và quy chế phân phối (Phiên bản ${SalesPolicy.VERSION})",
                        fontSize = 12.sp,
                        color = FutaColors.Navy
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = agreed && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Xác nhận đăng ký")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Hủy bỏ", color = FutaColors.Slate)
            }
        }
    )
}
