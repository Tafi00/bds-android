package vn.futaland.app.features.properties

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlin.math.max
import vn.futaland.app.core.network.JSONValue

/**
 * Which slot a photo is being decoded for.
 *
 * Coil's default memory cache key only contains the data (the URL) unless the request carries
 * transformations, so a 68dp thumbnail and a full-screen viewer share one cache entry: the
 * smaller bitmap wins whichever request finishes first and every later load is upscaled from
 * it, which is exactly why only the first gallery photo used to look sharp. Each slot now gets
 * its own memory cache key plus an explicit target size, so decoded bitmaps never cross slots.
 */
enum class PropertyImageSlot(internal val cachePrefix: String) {
    THUMBNAIL("pthumb"),
    HERO("phero"),
    FULLSCREEN("pfull"),
}

/**
 * Ordered photo list for the detail gallery: primary image, banner, then every uploaded
 * photo. Hoisted out of the detail screen so the in-page gallery and the full-screen viewer
 * always read exactly the same list.
 */
fun propertyGalleryImages(property: JSONValue): List<String> {
    fun clean(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "https://bds.futaland.vn$path"
    }

    val rawImages = mutableListOf<String>()
    clean(property["image"].string).takeIf { it.isNotEmpty() }?.let(rawImages::add)
    clean(property["bannerImage"].string).takeIf { it.isNotEmpty() }?.let(rawImages::add)
    property["images"].array.forEach { item ->
        val original = clean(
            item["original"].string.ifEmpty {
                item["url"].string.ifEmpty { item.string }
            }
        )
        if (original.isNotEmpty()) rawImages.add(original)
    }

    if (rawImages.isNotEmpty()) return rawImages.distinct()
    val fallback = PropertyFormatters.resolveImage(property)
    return if (fallback.isNotEmpty()) listOf(fallback) else emptyList()
}

/** Upper bound for one decoded bitmap; keeps a 48MP upload from allocating a huge bitmap. */
private const val MAX_DECODE_PIXELS = 4096

fun propertyImageRequest(
    context: Context,
    url: String,
    slot: PropertyImageSlot,
    width: Dp = 0.dp,
    height: Dp = 0.dp,
): ImageRequest {
    val density = context.resources.displayMetrics.density
    val builder = ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey("${slot.cachePrefix}:$url")

    return when (slot) {
        PropertyImageSlot.FULLSCREEN -> {
            val metrics = context.resources.displayMetrics
            val bound = (max(metrics.widthPixels, metrics.heightPixels) * 3)
                .coerceAtMost(MAX_DECODE_PIXELS)
                .coerceAtLeast(1)
            builder
                .size(Size(bound, bound))
                // INEXACT keeps the decoder from stretching the bitmap to an exact box it does
                // not know the aspect ratio of; it only downsamples, so zooming in stays sharp.
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .build()
        }
        else -> {
            val widthPx = (width.value * density).toInt().coerceIn(1, MAX_DECODE_PIXELS)
            val heightPx = (height.value * density).toInt().coerceIn(1, MAX_DECODE_PIXELS)
            builder
                .size(Size(widthPx, heightPx))
                .scale(Scale.FILL)
                .precision(Precision.EXACT)
                .build()
        }
    }
}

/** Remembers an [ImageRequest] keyed by the URL, slot and frame so scrolling does not rebuild it. */
@Composable
fun rememberPropertyImageRequest(
    url: String,
    slot: PropertyImageSlot,
    width: Dp,
    height: Dp,
): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(url, slot, width, height) {
        propertyImageRequest(context, url, slot, width, height)
    }
}

/**
 * Full-screen, swipeable, pinch-to-zoom photo viewer.
 *
 * Paging stays enabled only while the photo sits at 1×: the zoom/pan pointer handler consumes
 * drags merely when a pinch is in flight or the photo is already magnified, so a single-finger
 * swipe still moves between photos.
 */
@Composable
fun PropertyPhotoViewerDialog(
    images: List<String>,
    title: String,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChange: (Int) -> Unit = {},
) {
    if (images.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        val startPage = initialIndex.coerceIn(0, images.lastIndex)
        val pagerState = rememberPagerState(initialPage = startPage, pageCount = { images.size })

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val zoomed = scale > 1.02f

        LaunchedEffect(pagerState.currentPage) {
            onIndexChange(pagerState.currentPage)
            scale = 1f
            offset = Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
            ) { page ->
                val url = images[page]
                AsyncImage(
                    model = rememberPropertyImageRequest(
                        url = url,
                        slot = PropertyImageSlot.FULLSCREEN,
                        width = 0.dp,
                        height = 0.dp,
                    ),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(url) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    // Only steal the gesture for a pinch or for panning an
                                    // already-magnified photo; otherwise the pager keeps it.
                                    if (pressed > 1 || scale > 1.02f) {
                                        val nextScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                        scale = nextScale
                                        offset = if (nextScale > 1.02f) {
                                            clampPan(offset + event.calculatePan(), nextScale, containerSize)
                                        } else {
                                            Offset.Zero
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(url) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.02f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                )
            }

            // Top bar: close, title, position counter.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .size(34.dp)
                        .noRippleClickable(onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (images.size > 1) {
                            "Ảnh ${pagerState.currentPage + 1}/${images.size} · chụm 2 ngón để phóng to"
                        } else {
                            "Chụm 2 ngón để phóng to"
                        },
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (zoomed) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .size(34.dp)
                            .noRippleClickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = "Thu nhỏ ảnh",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    images.indices.forEach { idx ->
                        val active = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                                .background(
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.32f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Keeps a magnified photo inside its own bounds instead of letting it drift off screen. */
private fun clampPan(proposed: Offset, scale: Float, container: IntSize): Offset {
    if (scale <= 1.02f || container.width == 0 || container.height == 0) return Offset.Zero
    val maxX = container.width * (scale - 1f) / 2f
    val maxY = container.height * (scale - 1f) / 2f
    return Offset(
        x = proposed.x.coerceIn(-maxX, maxX),
        y = proposed.y.coerceIn(-maxY, maxY)
    )
}

/** Clickable without the Material ripple, which looks wrong over a full-screen dark viewer. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
