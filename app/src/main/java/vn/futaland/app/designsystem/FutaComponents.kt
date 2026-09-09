package vn.futaland.app.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ============================================================================
// 1. CUSTOM BUTTONS (Primary, Secondary, Cream, Mint, Outline, Ghost)
// ============================================================================

enum class FutaButtonVariant {
    PRIMARY,        // Green #207446, white text
    SECONDARY,      // Orange #F97316, white text
    CREAM,          // Warm cream #FFF7ED, Orange text
    MINT,           // Mint #ECFDF5, Green text
    OUTLINE,        // White bg, #DFE6ED border, Navy text
    GHOST,          // Transparent bg, Navy/Green text
    DANGER          // Destructive Red #DC2626, white text
}

@Composable
fun FutaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FutaButtonVariant = FutaButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1.0f, label = "scale")

    val (bg, fg, border) = when (variant) {
        FutaButtonVariant.PRIMARY -> Triple(
            if (enabled) FutaColors.BrandGreen else Color(0xFFCBD5E1),
            Color.White,
            null
        )
        FutaButtonVariant.SECONDARY -> Triple(
            if (enabled) FutaColors.BrandOrange else Color(0xFFCBD5E1),
            Color.White,
            null
        )
        FutaButtonVariant.CREAM -> Triple(
            FutaColors.CreamBg,
            FutaColors.BrandOrange,
            BorderStroke(1.dp, FutaColors.PeachBorder.copy(alpha = 0.5f))
        )
        FutaButtonVariant.MINT -> Triple(
            FutaColors.MintBg,
            FutaColors.BrandGreen,
            BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.2f))
        )
        FutaButtonVariant.OUTLINE -> Triple(
            Color.White,
            FutaColors.Navy,
            BorderStroke(1.dp, FutaColors.LightBlueBorder)
        )
        FutaButtonVariant.GHOST -> Triple(
            Color.Transparent,
            FutaColors.BrandGreen,
            null
        )
        FutaButtonVariant.DANGER -> Triple(
            if (enabled) Color(0xFFDC2626) else Color(0xFFCBD5E1),
            Color.White,
            null
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .then(
                if (border != null) Modifier.border(border, shape) else Modifier
            )
            .clip(shape)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = fg,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================================
// 2. CUSTOM TEXT INPUT (Built with BasicTextField, Web border & focus highlight)
// ============================================================================

@Composable
fun FutaInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isFocused -> FutaColors.BrandGreen
        else -> FutaColors.LightBlueBorder
    }

    val bgColor = when {
        !enabled -> Color(0xFFF1F5F9)
        isFocused -> Color.White
        else -> Color(0xFFF9FBFA)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) FutaColors.BrandGreen else FutaColors.Slate,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = FutaColors.Muted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(FutaColors.BrandGreen),
                    textStyle = TextStyle(
                        color = FutaColors.Navy,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }

            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

// ============================================================================
// 3. CUSTOM TEXTAREA (Multiline with clean Web borders)
// ============================================================================

@Composable
fun FutaTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 3,
    maxLines: Int = 6,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = (minLines * 24 + 24).dp, max = (maxLines * 24 + 24).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color(0xFFF9FBFA))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) FutaColors.BrandGreen else FutaColors.LightBlueBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = FutaColors.Muted,
                fontSize = 13.5.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            enabled = enabled,
            cursorBrush = SolidColor(FutaColors.BrandGreen),
            textStyle = TextStyle(
                color = FutaColors.Navy,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        )
    }
}

// ============================================================================
// 4. CUSTOM POPOVER / DROPDOWN MENU (Card-styled floating overlay)
// ============================================================================
@Composable
fun <T> FutaPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    itemTrailingIcon: @Composable ((T) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp)
    ) {
        items.forEachIndexed { index, item ->
            DropdownMenuItem(
                text = { itemContent(item) },
                trailingIcon = itemTrailingIcon?.let { iconBlock ->
                    { iconBlock(item) }
                },
                onClick = {
                    onItemSelected(item)
                    onDismissRequest()
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
            )
            if (index < items.size - 1) {
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
            }
        }
    }
}

// ============================================================================
// 5. CUSTOM BOTTOM SHEET (Custom slide-up modal with drag handle & dimmed backdrop)
// ============================================================================

@Composable
fun FutaBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .clickable(enabled = false) {}
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Color.White)
                        .navigationBarsPadding()
                        .padding(top = 10.dp)
                ) {
                    // Drag Handle Bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFCBD5E1))
                    )

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = FutaColors.Slate,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FutaColors.LightBlueBorder)
                    )

                    // Body
                    Column(
                        modifier = Modifier.padding(16.dp),
                        content = content
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. CUSTOM DIALOG (Clean Web modal without Android system styling)
// ============================================================================

@Composable
fun FutaDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmText: String = "Xác nhận",
    confirmVariant: FutaButtonVariant = FutaButtonVariant.PRIMARY,
    onConfirm: () -> Unit,
    cancelText: String? = "Hủy",
    onCancel: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .shadow(12.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    Spacer(Modifier.height(12.dp))

                    content()

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (cancelText != null) {
                            FutaButton(
                                text = cancelText,
                                variant = FutaButtonVariant.OUTLINE,
                                height = 40.dp,
                                onClick = {
                                    onCancel?.invoke()
                                    onDismiss()
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        FutaButton(
                            text = confirmText,
                            variant = confirmVariant,
                            height = 40.dp,
                            onClick = {
                                onConfirm()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. CUSTOM SEGMENT TABS (Pill style matching Web)
// ============================================================================

@Composable
fun <T> FutaSegmentTabs(
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    titleFor: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val (bg, fg, border) = if (isSelected) {
                Triple(FutaColors.BrandGreen, Color.White, null)
            } else {
                Triple(Color.White, FutaColors.Navy, BorderStroke(1.dp, FutaColors.LightBlueBorder))
            }

            Box(
                modifier = Modifier
                    .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = titleFor(item),
                    color = fg,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
// ============================================================================
// 8. CUSTOM SWITCH TOGGLE (Apple UISwitch aesthetic)
// ============================================================================

@Composable
fun FutaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF0E7643)
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switch_thumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else Color(0xFFE2E8F0),
        animationSpec = tween(durationMillis = 200),
        label = "switch_track"
    )

    Box(
        modifier = modifier
            .width(50.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(elevation = 2.5.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
