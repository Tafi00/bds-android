package vn.futaland.app.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard Form Row (Label left minWidth 105dp, content right).
 */
@Composable
fun FutaFormRow(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.widthIn(min = 105.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FutaColors.Navy
            )
            if (required) {
                Text(
                    text = "*",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        content()
    }
}

/**
 * Stacked Form Field (Label on top, Input below).
 */
@Composable
fun FutaFormSectionField(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate
            )
            if (required) {
                Text(
                    text = "*",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/**
 * Styled Text Input matching Web input borders and focus state.
 */
@Composable
fun FutaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        placeholder = {
            Text(text = placeholder, color = FutaColors.Muted, fontSize = 13.5.sp)
        },
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color(0xFFF9FBFA),
            disabledContainerColor = Color(0xFFF1F5F9),
            focusedBorderColor = FutaColors.BrandGreen,
            unfocusedBorderColor = FutaColors.LightBlueBorder,
            focusedTextColor = FutaColors.Navy,
            unfocusedTextColor = FutaColors.Navy
        )
    )
}

/**
 * Standard Select field trigger with chevron.
 */
@Composable
fun FutaSelectField(
    title: String? = null,
    displayValue: String,
    placeholder: String = "Chọn...",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isPlaceholder = displayValue.isEmpty() || displayValue == placeholder
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = FutaColors.Navy,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF7F9FC),
            border = BorderStroke(1.dp, FutaColors.LightBlueBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isPlaceholder) placeholder else displayValue,
                    fontSize = 13.5.sp,
                    color = if (isPlaceholder) FutaColors.Muted else FutaColors.Navy
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = FutaColors.BrandGreen
                )
            }
        }
    }
}

/**
 * Web-style Sticky Action Bar for bottom of forms and detail pages.
 */
@Composable
fun FutaStickyActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .futaDropShadow(
                shape = RectangleShape,
                color = Color(0x0C061D3D),
                blur = 12.dp,
                offsetY = (-3).dp
            ),
        color = Color.White,
        border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Empty / Unavailable State (matching iOS ContentUnavailableView).
 */
@Composable
fun FutaEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = FutaColors.PageBg,
            border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = FutaColors.Slate
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = FutaColors.Navy,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = FutaColors.Slate,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        if (actionButton != null) {
            Spacer(Modifier.height(16.dp))
            actionButton()
        }
    }
}
