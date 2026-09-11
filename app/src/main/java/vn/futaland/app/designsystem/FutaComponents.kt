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
import android.graphics.BlurMaskFilter
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.material3.Surface
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
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
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
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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
    headerTrailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
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
                        .heightIn(max = 700.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            headerTrailing?.invoke()
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
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FutaColors.LightBlueBorder)
                    )

                    // Body: wrapped in verticalScroll so content can scroll when keyboard opens
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(bottom = if (footer != null) 8.dp else 16.dp)
                            .verticalScroll(rememberScrollState()),
                        content = content
                    )

                    if (footer != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FutaColors.LightBlueBorder)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            footer()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modifier extension to dismiss keyboard when tapping outside of inputs.
 */
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    this.pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus()
        })
    }
}

/**
 * iOS-grade feathered soft shadow with custom tint, blur, and directional Y-offset.
 * Replaces harsh Android Material ambient/spot gray rings with soft, elegant diffuse drop shadows.
 */
fun Modifier.futaDropShadow(
    shape: Shape,
    color: Color = Color(0x12061D3D),
    blur: androidx.compose.ui.unit.Dp = 14.dp,
    offsetY: androidx.compose.ui.unit.Dp = 5.dp,
    offsetX: androidx.compose.ui.unit.Dp = 0.dp,
    spread: androidx.compose.ui.unit.Dp = 0.dp
): Modifier = this.drawBehind {
    if (color.alpha <= 0f) return@drawBehind

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = color.toArgb()
        if (blur > 0.dp) {
            frameworkPaint.maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val left = offsetX.toPx() - spread.toPx()
        val top = offsetY.toPx() - spread.toPx()
        val right = size.width + offsetX.toPx() + spread.toPx()
        val bottom = size.height + offsetY.toPx() + spread.toPx()

        val outline = shape.createOutline(
            size = Size(right - left, bottom - top),
            layoutDirection = layoutDirection,
            density = this
        )

        canvas.save()
        canvas.translate(left, top)
        when (outline) {
            is Outline.Rectangle -> canvas.drawRect(outline.rect, paint)
            is Outline.Rounded -> canvas.drawRoundRect(
                outline.roundRect.left,
                outline.roundRect.top,
                outline.roundRect.right,
                outline.roundRect.bottom,
                outline.roundRect.topLeftCornerRadius.x,
                outline.roundRect.topLeftCornerRadius.y,
                paint
            )
            is Outline.Generic -> canvas.drawPath(outline.path, paint)
        }
        canvas.restore()
    }
}

/**
 * GPU-accelerated soft elevation with navy/slate tint instead of harsh black rings.
 */
fun Modifier.futaSoftElevation(
    elevation: androidx.compose.ui.unit.Dp = 4.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color(0xFF061D3D)
): Modifier = this.graphicsLayer {
    this.shadowElevation = elevation.toPx()
    this.shape = shape
    this.clip = false
    this.ambientShadowColor = tint.copy(alpha = 0.04f)
    this.spotShadowColor = tint.copy(alpha = 0.09f)
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
                    .imePadding()
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
                        .heightIn(max = 580.dp)
                        .futaDropShadow(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x1F061D3D),
                            blur = 24.dp,
                            offsetY = 8.dp
                        )
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

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        content()
                    }

                    Spacer(Modifier.height(16.dp))

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
                .futaDropShadow(
                    shape = CircleShape,
                    color = Color(0x18000000),
                    blur = 4.dp,
                    offsetY = 1.5.dp
                )
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ============================================================================
// 9. UNIFIED HEADER BUTTONS (Single & Group Button Styles matching iOS)
// ============================================================================

@Composable
fun FutaHeaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FutaColors.Navy,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .size(size)
            .futaDropShadow(
                shape = CircleShape,
                color = Color(0x0C061D3D),
                blur = 8.dp,
                offsetY = 2.dp
            )
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FutaHeaderActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.futaDropShadow(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0C061D3D),
            blur = 8.dp,
            offsetY = 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
