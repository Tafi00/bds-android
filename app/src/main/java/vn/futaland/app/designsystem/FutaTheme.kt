package vn.futaland.app.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FutaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

val FutaPill = CircleShape

val FutaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.5).sp,
        color = FutaColors.Navy
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp,
        color = FutaColors.Navy
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.2).sp,
        color = FutaColors.Navy
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = FutaColors.Body
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = FutaColors.Body
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = FutaColors.Slate
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = FutaColors.Slate
    )
)

private val LightColorScheme = lightColorScheme(
    primary = FutaColors.BrandGreen,
    onPrimary = FutaColors.CardBg,
    primaryContainer = FutaColors.MintBg,
    onPrimaryContainer = FutaColors.BrandGreenDark,
    secondary = FutaColors.BrandOrange,
    onSecondary = FutaColors.CardBg,
    background = FutaColors.PageBg,
    onBackground = FutaColors.Navy,
    surface = FutaColors.CardBg,
    onSurface = FutaColors.Navy,
    surfaceVariant = FutaColors.PageBg,
    onSurfaceVariant = FutaColors.Slate,
    outline = FutaColors.CardBorder,
    outlineVariant = FutaColors.LightBlueBorder
)

@Composable
fun FutaLandTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        shapes = FutaShapes,
        typography = FutaTypography,
        content = content
    )
}
