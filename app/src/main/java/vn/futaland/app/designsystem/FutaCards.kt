package vn.futaland.app.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Web-aligned Card: pure white background with sandy-beige (#E9E2D5)
 * or light blue (#DFE6ED) border, 16dp rounded corners, and crisp non-blurred surface.
 */
@Composable
fun FutaCard(
    modifier: Modifier = Modifier,
    borderColor: Color = FutaColors.CardBorder,
    borderWidth: Dp = 1.dp,
    containerColor: Color = FutaColors.CardBg,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val cardModifier = modifier.futaDropShadow(
        shape = shape,
        color = Color(0x08061D3D),
        blur = 8.dp,
        offsetY = 2.dp
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box {
                content()
            }
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box {
                content()
            }
        }
    }
}

/**
 * Inner secondary panel matching Web admin panel containers.
 */
@Composable
fun FutaPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = FutaColors.PageBg,
        border = BorderStroke(1.dp, FutaColors.LightBlueBorder)
    ) {
        content()
    }
}
