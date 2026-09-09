package vn.futaland.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import vn.futaland.app.designsystem.FutaButton
import vn.futaland.app.designsystem.FutaButtonVariant
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.FutaEmptyState

sealed interface NativeAccess {
    object PublicAccess : NativeAccess
    object SignedIn : NativeAccess
    data class Permissions(val required: List<String>) : NativeAccess

    fun allows(session: AppSession): Boolean = when (this) {
        is PublicAccess -> true
        is SignedIn -> session.isAuthenticated
        is Permissions -> session.isAuthenticated && required.any { session.hasPermission(it) }
    }
}

@Composable
fun FutaAccessGate(
    access: NativeAccess,
    session: AppSession,
    onRequireLogin: () -> Unit = {},
    content: @Composable () -> Unit
) {
    when {
        access.allows(session) -> content()
        !session.isAuthenticated -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = FutaColors.MintBg,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = FutaColors.BrandGreen
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Không gian của bạn",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Đăng nhập để lưu sản phẩm, theo dõi giao dịch và tiếp tục công việc trên mọi thiết bị.",
                            fontSize = 13.sp,
                            color = FutaColors.Slate,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        FutaButton(
                            text = "Đăng nhập",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = onRequireLogin,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        else -> {
            FutaEmptyState(
                icon = Icons.Default.Lock,
                title = "Không có quyền truy cập",
                message = "Tính năng này không thuộc phân quyền của tài khoản hiện tại."
            )
        }
    }
}
