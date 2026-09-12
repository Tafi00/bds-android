package vn.futaland.app.features.account

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.ToastCenter

enum class AuthStep {
    PHONE,
    PASSWORD,
    OTP,
    CREATE_PASSWORD,
    PROFILE
}

/**
 * Authentication screen matching iOS `AuthenticationView.swift` 100% in design,
 * layout, typography, state transitions, and backend endpoints.
 * (Apple Login button removed on Android per design specifications).
 */
@Composable
fun AuthenticationScreen(
    initialStep: AuthStep = AuthStep.PHONE,
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(initialStep) }

    var rawPhone by remember { mutableStateOf("") }
    var resolvedPhone by remember { mutableStateOf("") }
    var displayIdentifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var signupToken by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }

    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendSeconds by remember { mutableIntStateOf(0) }

    fun startCountdown() {
        resendSeconds = 179
        scope.launch {
            while (resendSeconds > 0) {
                delay(1000)
                resendSeconds--
            }
        }
    }

    // STEP 1: Phone Lookup
    fun handlePhoneSubmit() {
        val cleaned = rawPhone.trim()
        if (cleaned.length < 3) {
            errorMessage = "Tài khoản phải có từ 3 đến 50 ký tự"
            return
        }

        busy = true
        errorMessage = null
        scope.launch {
            try {
                val res = APIClient.get().request(
                    "/auth/lookup-phone",
                    method = "POST",
                    bodyJson = "{\"phone\":\"$cleaned\"}"
                )
                val data = res["data"]
                val phone = data["phone"].string.ifEmpty { cleaned }
                val mode = data["mode"].string

                displayIdentifier = cleaned
                resolvedPhone = phone
                password = ""
                confirmPassword = ""
                otp = ""
                signupToken = ""

                if (mode == "password") {
                    step = AuthStep.PASSWORD
                } else {
                    // New user -> send OTP
                    try {
                        APIClient.get().request(
                            "/auth/send-otp",
                            method = "POST",
                            bodyJson = "{\"phone\":\"$phone\"}"
                        )
                    } catch (_: Exception) {}
                    startCountdown()
                    step = AuthStep.OTP
                }
            } catch (e: Exception) {
                // If lookup fails or server returns direct password requirement
                displayIdentifier = cleaned
                resolvedPhone = cleaned
                step = AuthStep.PASSWORD
            } finally {
                busy = false
            }
        }
    }

    // STEP 2: Password Login
    fun handlePasswordLogin() {
        if (password.isEmpty()) {
            errorMessage = "Vui lòng nhập mật khẩu"
            return
        }

        busy = true
        errorMessage = null
        scope.launch {
            try {
                val body = "{\"phone\":\"$resolvedPhone\",\"password\":\"$password\"}"
                val res = APIClient.get().request("/auth/login", method = "POST", bodyJson = body)
                val data = res["data"]
                val access = data["accessToken"].string
                val refresh = data["refreshToken"].string
                val user = data["user"]

                if (access.isNotEmpty()) {
                    AppSession.shared.login(access, refresh, user)
                    ToastCenter.show("Đăng nhập thành công!")
                    onSuccess()
                } else {
                    errorMessage = "Phản hồi đăng nhập không hợp lệ"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Mật khẩu không chính xác"
            } finally {
                busy = false
            }
        }
    }

    // Switch to OTP login from Password step
    fun switchAndSendOtp() {
        busy = true
        errorMessage = null
        scope.launch {
            try {
                APIClient.get().request(
                    "/auth/send-otp",
                    method = "POST",
                    bodyJson = "{\"phone\":\"$resolvedPhone\"}"
                )
                startCountdown()
                step = AuthStep.OTP
            } catch (e: Exception) {
                errorMessage = e.message ?: "Không thể gửi mã OTP"
            } finally {
                busy = false
            }
        }
    }

    // STEP 3: OTP Verify
    fun handleOtpSubmit() {
        val cleanedOtp = otp.trim()
        if (cleanedOtp.length != 6) {
            errorMessage = "Vui lòng nhập đúng 6 chữ số mã OTP"
            return
        }

        busy = true
        errorMessage = null
        scope.launch {
            try {
                val res = APIClient.get().request(
                    "/auth/verify-otp",
                    method = "POST",
                    bodyJson = "{\"phone\":\"$resolvedPhone\",\"otp\":\"$cleanedOtp\"}"
                )
                signupToken = res["data"]["signupToken"].string
                step = AuthStep.CREATE_PASSWORD
            } catch (e: Exception) {
                errorMessage = e.message ?: "Mã OTP không chính xác hoặc đã hết hạn"
            } finally {
                busy = false
            }
        }
    }

    // Resend OTP
    fun resendOtp() {
        if (resendSeconds > 0) return
        busy = true
        errorMessage = null
        scope.launch {
            try {
                APIClient.get().request(
                    "/auth/send-otp",
                    method = "POST",
                    bodyJson = "{\"phone\":\"$resolvedPhone\"}"
                )
                startCountdown()
                ToastCenter.show("Đã gửi lại mã OTP mới")
            } catch (e: Exception) {
                errorMessage = e.message ?: "Không thể gửi lại mã OTP"
            } finally {
                busy = false
            }
        }
    }

    // STEP 4: Complete Password Signup
    fun handleCreatePassword() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            errorMessage = "Vui lòng nhập họ và tên"
            return
        }
        if (password.length < 8) {
            errorMessage = "Mật khẩu phải có ít nhất 8 ký tự"
            return
        }
        if (password != confirmPassword) {
            errorMessage = "Mật khẩu xác nhận không khớp"
            return
        }

        busy = true
        errorMessage = null
        scope.launch {
            try {
                val body = "{\"phone\":\"$resolvedPhone\",\"name\":\"$trimmedName\",\"password\":\"$password\",\"signupToken\":\"$signupToken\"}"
                val res = APIClient.get().request(
                    "/auth/complete-password-signup",
                    method = "POST",
                    bodyJson = body
                )
                val data = res["data"]
                val access = data["accessToken"].string
                val refresh = data["refreshToken"].string
                val user = data["user"]

                if (access.isNotEmpty()) {
                    AppSession.shared.login(access, refresh, user)
                    ToastCenter.show("Đăng ký tài khoản thành công!")
                    step = AuthStep.PROFILE
                } else {
                    errorMessage = "Không thể hoàn tất đăng ký"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Đăng ký không thành công, vui lòng thử lại"
            } finally {
                busy = false
            }
        }
    }

    // STEP 5: Save Profile (Optional)
    fun handleSaveProfile() {
        busy = true
        scope.launch {
            try {
                val trimmedEmail = email.trim()
                val trimmedReferral = referralCode.trim()
                if (trimmedEmail.isNotEmpty() || trimmedReferral.isNotEmpty()) {
                    val body = mutableListOf<String>()
                    if (trimmedEmail.isNotEmpty()) body.add("\"email\":\"$trimmedEmail\"")
                    if (trimmedReferral.isNotEmpty()) body.add("\"referralCode\":\"$trimmedReferral\"")
                    val json = "{${body.joinToString(",")}}"
                    val res = APIClient.get().request("/auth/me", method = "PUT", bodyJson = json)
                    if (!res["data"].isNull) {
                        AppSession.shared.restore()
                    }
                }
            } catch (_: Exception) {}
            busy = false
            onSuccess()
        }
    }

    // Dynamic Header Title matching iOS headerTitle
    val headerTitle = when (step) {
        AuthStep.PHONE -> "Đăng nhập / Đăng ký"
        AuthStep.PASSWORD -> "Nhập mật khẩu"
        AuthStep.OTP -> "Xác thực số điện thoại"
        AuthStep.CREATE_PASSWORD -> "Tạo mật khẩu mới"
        AuthStep.PROFILE -> "Hoàn tất thông tin"
    }

    // Dynamic Header Subtitle matching iOS headerSubtitle
    val headerSubtitle = when (step) {
        AuthStep.PHONE -> ""
        AuthStep.PASSWORD -> "Nhập mật khẩu tài khoản của bạn để tiếp tục."
        AuthStep.OTP -> "Mã xác thực gồm 6 chữ số đã được gửi tới số điện thoại của bạn."
        AuthStep.CREATE_PASSWORD -> "Mật khẩu cần tối thiểu 8 ký tự để bảo vệ tài khoản."
        AuthStep.PROFILE -> "Cung cấp họ tên và email để nhận thông báo giao dịch."
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = FutaColors.Navy,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // 1. Top Branded Header (Matching Web and iOS official brand header)
            Image(
                painter = painterResource(id = R.drawable.futaland_logo),
                contentDescription = "FUTA Land",
                modifier = Modifier.height(38.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = headerTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy,
                letterSpacing = (-0.5).sp
            )

            if (headerSubtitle.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = headerSubtitle,
                    fontSize = 14.sp,
                    color = FutaColors.Slate,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Error banner if any
            if (!errorMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 2. Active Step Content
            when (step) {
                AuthStep.PHONE -> {
                    // STEP 1: PHONE CONTENT
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SỐ ĐIỆN THOẠI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Slate
                            )

                            // Phone input field matching iOS
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF8F9FA))
                                    .border(1.dp, Color(0xFFE1D9CB), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "+84",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(Color(0xFFCBD5E1))
                                )
                                Spacer(Modifier.width(10.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (rawPhone.isEmpty()) {
                                        Text(
                                            text = "Nhập số điện thoại",
                                            fontSize = 14.sp,
                                            color = FutaColors.Muted
                                        )
                                    }
                                    BasicTextField(
                                        value = rawPhone,
                                        onValueChange = { rawPhone = it },
                                        singleLine = true,
                                        enabled = !busy,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        textStyle = TextStyle(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FutaColors.Navy
                                        ),
                                        cursorBrush = SolidColor(FutaColors.BrandGreen),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (rawPhone.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Xóa",
                                        tint = FutaColors.Slate,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { rawPhone = "" }
                                    )
                                }
                            }
                        }

                        // Button: Tiếp tục + Arrow right
                        val canSubmit = rawPhone.trim().length >= 3
                        Button(
                            onClick = { handlePhoneSubmit() },
                            enabled = canSubmit && !busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSubmit) FutaColors.BrandGreen else FutaColors.BrandGreen.copy(alpha = 0.4f),
                                disabledContainerColor = FutaColors.BrandGreen.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .then(
                                    if (canSubmit) Modifier.shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = FutaColors.BrandGreen.copy(alpha = 0.25f)) else Modifier
                                )
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("Tiếp tục", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Social Login Section (Google only on Android per request)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f).height(1.dp).background(FutaColors.LightBlueBorder))
                                Text(
                                    text = "HOẶC TIẾP TỤC VỚI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Slate,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                Box(modifier = Modifier.weight(1f).height(1.dp).background(FutaColors.LightBlueBorder))
                            }

                            // Full-width Google Login Button (Apple removed)
                            Surface(
                                onClick = {
                                    ToastCenter.show("Đăng nhập Google sẽ sẵn sàng ở bản cập nhật kế tiếp")
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8F9FA),
                                border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google),
                                        contentDescription = "Google",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Đăng nhập với Google",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FutaColors.Navy
                                    )
                                }
                            }
                        }
                    }
                }

                AuthStep.PASSWORD -> {
                    // STEP 2: PASSWORD CONTENT
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        // Account Identifier Badge with "Đổi tài khoản" button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = FutaColors.BrandGreen.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("TÀI KHOẢN ĐĂNG NHẬP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                    Text(
                                        text = displayIdentifier.ifEmpty { resolvedPhone },
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy
                                    )
                                }
                                Text(
                                    text = "Đổi tài khoản",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.clickable { step = AuthStep.PHONE }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MẬT KHẨU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF8F9FA))
                                    .border(1.dp, Color(0xFFE1D9CB), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(10.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (password.isEmpty()) {
                                        Text("Mật khẩu của bạn", fontSize = 14.sp, color = FutaColors.Muted)
                                    }
                                    BasicTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        singleLine = true,
                                        enabled = !busy,
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        textStyle = TextStyle(fontSize = 14.5.sp, color = FutaColors.Navy),
                                        cursorBrush = SolidColor(FutaColors.BrandGreen),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = FutaColors.Slate,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Button: Đăng nhập
                        val canLogin = password.isNotEmpty()
                        Button(
                            onClick = { handlePasswordLogin() },
                            enabled = canLogin && !busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canLogin) FutaColors.BrandGreen else FutaColors.BrandGreen.copy(alpha = 0.4f),
                                disabledContainerColor = FutaColors.BrandGreen.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .then(
                                    if (canLogin) Modifier.shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = FutaColors.BrandGreen.copy(alpha = 0.25f)) else Modifier
                                )
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Đăng nhập", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Switch to OTP link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { switchAndSendOtp() }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Message, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Đăng nhập bằng mã OTP",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = FutaColors.BrandGreen
                            )
                        }
                    }
                }

                AuthStep.OTP -> {
                    // STEP 3: OTP CONTENT
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        // Phone badge with "Đổi" button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8F9FA),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("SỐ ĐIỆN THOẠI NHẬN MÃ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                    Text(resolvedPhone, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                }
                                Text(
                                    text = "Đổi",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.clickable { step = AuthStep.PHONE }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MÃ XÁC THỰC OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF8F9FA))
                                    .border(1.dp, Color(0xFFE1D9CB), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (otp.isEmpty()) {
                                    Text("Nhập mã 6 chữ số", fontSize = 16.sp, color = FutaColors.Muted)
                                }
                                BasicTextField(
                                    value = otp,
                                    onValueChange = { if (it.length <= 6) otp = it },
                                    singleLine = true,
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = FutaColors.Navy,
                                        letterSpacing = 8.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    cursorBrush = SolidColor(FutaColors.BrandGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Button: Xác nhận mã OTP
                        val canVerify = otp.trim().length == 6
                        Button(
                            onClick = { handleOtpSubmit() },
                            enabled = canVerify && !busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canVerify) FutaColors.BrandGreen else FutaColors.BrandGreen.copy(alpha = 0.4f),
                                disabledContainerColor = FutaColors.BrandGreen.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Xác nhận mã OTP", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        // Resend Countdown or Action
                        if (resendSeconds > 0) {
                            Text(
                                text = "Có thể gửi lại mã sau ${resendSeconds}s",
                                fontSize = 12.5.sp,
                                color = FutaColors.Slate,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Gửi lại mã OTP mới",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.BrandGreen,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { resendOtp() }
                            )
                        }
                    }
                }

                AuthStep.CREATE_PASSWORD -> {
                    // STEP 4: CREATE PASSWORD CONTENT
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("HỌ VÀ TÊN (*)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            FutaInputField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = "Nhập họ và tên đầy đủ",
                                enabled = !busy
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MẬT KHẨU MỚI (*)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            FutaPasswordField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "Tối thiểu 8 ký tự",
                                showPassword = showPassword,
                                onToggle = { showPassword = !showPassword },
                                enabled = !busy
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("XÁC NHẬN MẬT KHẨU (*)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            FutaPasswordField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                placeholder = "Nhập lại mật khẩu mới",
                                showPassword = showConfirmPassword,
                                onToggle = { showConfirmPassword = !showConfirmPassword },
                                enabled = !busy
                            )
                        }

                        val canCreate = name.trim().isNotEmpty() && password.length >= 8 && password == confirmPassword
                        Button(
                            onClick = { handleCreatePassword() },
                            enabled = canCreate && !busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canCreate) FutaColors.BrandGreen else FutaColors.BrandGreen.copy(alpha = 0.4f),
                                disabledContainerColor = FutaColors.BrandGreen.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Hoàn tất đăng ký", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }

                AuthStep.PROFILE -> {
                    // STEP 5: PROFILE CONTENT (Optional)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("EMAIL (TÙY CHỌN)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            FutaInputField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "Địa chỉ email",
                                enabled = !busy,
                                keyboardType = KeyboardType.Email
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MÃ GIỚI THIỆU (NẾU CÓ)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            FutaInputField(
                                value = referralCode,
                                onValueChange = { referralCode = it },
                                placeholder = "Nhập mã giới thiệu TVV",
                                enabled = !busy
                            )
                        }

                        Button(
                            onClick = { handleSaveProfile() },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Lưu thông tin", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        Text(
                            text = "Bỏ qua bước này",
                            fontSize = 13.sp,
                            color = FutaColors.Slate,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuccess() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // 3. Terms & Privacy Notice matching iOS termsNotice
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Bằng việc tiếp tục, bạn đồng ý với",
                    fontSize = 11.sp,
                    color = FutaColors.Muted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Điều khoản dịch vụ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bds.futaland.vn/policies"))
                            context.startActivity(intent)
                        }
                    )
                    Text(text = "&", fontSize = 11.sp, color = FutaColors.Muted)
                    Text(
                        text = "Chính sách bảo mật",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bds.futaland.vn/policies"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FutaInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8F9FA))
            .border(1.dp, Color(0xFFE1D9CB), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, fontSize = 14.sp, color = FutaColors.Muted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 14.sp, color = FutaColors.Navy),
            cursorBrush = SolidColor(FutaColors.BrandGreen),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FutaPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8F9FA))
            .border(1.dp, Color(0xFFE1D9CB), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, fontSize = 14.sp, color = FutaColors.Muted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(fontSize = 14.sp, color = FutaColors.Navy),
                    cursorBrush = SolidColor(FutaColors.BrandGreen),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = FutaColors.Slate,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
