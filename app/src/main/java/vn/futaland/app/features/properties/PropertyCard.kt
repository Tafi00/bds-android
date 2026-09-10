package vn.futaland.app.features.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import vn.futaland.app.R
import vn.futaland.app.core.network.JSONValue
import java.util.Locale

/**
 * Standard Apartment / Product Card matching `bds-clone/product-card.tsx` 100%.
 */
@Composable
fun FutaPropertyCard(
    apartment: JSONValue,
    isFavorited: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryImage = PropertyFormatters.resolveImage(apartment)

    val hasTour = apartment["virtualTourUrl"].string.isNotEmpty() ||
        apartment["projectVirtualTourUrl"].string.isNotEmpty() ||
        apartment["virtualTourEmbedUrl"].string.isNotEmpty() ||
        apartment["virtualTourIframe"].string.isNotEmpty() ||
        apartment["tour360Url"].string.isNotEmpty() ||
        apartment["tour360EmbedUrl"].string.isNotEmpty() ||
        apartment["tour360Iframe"].string.isNotEmpty() ||
        apartment["view360Url"].string.isNotEmpty() ||
        apartment["view360EmbedUrl"].string.isNotEmpty() ||
        apartment["matterportUrl"].string.isNotEmpty() ||
        apartment["kuulaUrl"].string.isNotEmpty() ||
        (apartment["virtualTourEnabled"].bool && (apartment["virtualTourUrl"].string.isNotEmpty() || apartment["tour360Url"].string.isNotEmpty()))

    val hasVideo = apartment["videoUrl"].string.isNotEmpty() ||
        apartment["youtubeUrl"].string.isNotEmpty() ||
        apartment["youtubeUrl2"].string.isNotEmpty() ||
        apartment["hasVideo"].bool ||
        apartment["videos"].array.isNotEmpty()

    val rawNote = apartment["note"].string
    val noteProjectMatch = Regex("""(?:^|\n)cardProjectName=([^\n]+)""").find(rawNote)?.groupValues?.get(1)?.trim()
    val projectName = when {
        !noteProjectMatch.isNullOrEmpty() -> noteProjectMatch
        apartment["projectName"].string.trim().isNotEmpty() -> apartment["projectName"].string.trim()
        apartment["zone"].string.trim().isNotEmpty() -> apartment["zone"].string.trim()
        else -> "Dự án FUTA Land"
    }

    val code = when {
        apartment["propertyCode"].string.trim().isNotEmpty() -> apartment["propertyCode"].string.trim()
        apartment["recordId"].string.trim().isNotEmpty() -> apartment["recordId"].string.trim()
        else -> PropertyFormatters.propertyTitle(apartment)
    }

    val sizeStr = apartment["size_m2"].string.trim()
    val sizeDouble = apartment["size_m2"].double.takeIf { it > 0 }
        ?: apartment["areaM2"].double.takeIf { it > 0 }
        ?: apartment["area"].double.takeIf { it > 0 }
    val sizeText = when {
        sizeStr.isNotEmpty() -> if (sizeStr.endsWith("m²")) sizeStr else "$sizeStr m²"
        sizeDouble != null && sizeDouble > 0 -> {
            val formatted = if (sizeDouble % 1.0 == 0.0) "${sizeDouble.toLong()}" else "%.1f".format(sizeDouble).replace(".0", "")
            "$formatted m²"
        }
        else -> ""
    }

    val aptType = apartment["apartmentType"].string.lowercase()
    val beds = apartment["bedrooms"].double
    val bedroomLabel: String? = when {
        aptType.contains("studio") || (beds == 0.0 && aptType.isNotEmpty()) -> "Studio"
        beds > 0 -> "${beds.toInt()} PN"
        else -> null
    }

    val bDir = apartment["balconyDirection"].string.trim()
    val dir = apartment["direction"].string.trim()
    val balconyDirectionText = when {
        bDir.isNotEmpty() -> translateDirection(bDir)
        dir.isNotEmpty() -> translateDirection(dir)
        else -> "-"
    }

    val translatedMainDir = if (dir.isNotEmpty()) translateDirection(dir) else ""
    val mainDirectionText = "Hướng cửa chính: ${translatedMainDir.ifEmpty { "Đang cập nhật" }}"

    val sellPrice = apartment["sellPrice"].double
    val price = apartment["price"].double
    val rawVal = if (sellPrice > 0) sellPrice else price
    val formattedPrice = if (rawVal <= 0) {
        "Liên hệ"
    } else {
        val finalPrice = if (rawVal < 1000) rawVal * 1_000_000 else rawVal
        val formattedNum = "%,d".format(Locale.US, finalPrice.toLong()).replace(',', '.')
        "$formattedNum đ"
    }

    FutaPropertyCardContent(
        projectName = projectName,
        code = code,
        imageUrl = primaryImage,
        sizeText = sizeText,
        bedroomLabel = bedroomLabel,
        balconyDirectionText = balconyDirectionText,
        mainDirectionText = mainDirectionText,
        formattedPrice = formattedPrice,
        hasTour = hasTour,
        hasVideo = hasVideo,
        isFavorited = isFavorited,
        onFavoriteClick = onFavoriteClick,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Backwards compatible overload taking individual string parameters.
 */
@Composable
fun FutaPropertyCard(
    title: String,
    price: String,
    address: String,
    imageUrl: String,
    statusText: String,
    beds: Int,
    baths: Int,
    area: Double,
    direction: String,
    agentName: String,
    agentAvatar: String?,
    isFavorited: Boolean,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onCallClick: () -> Unit,
    onChatClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeText = if (area > 0) {
        val formatted = if (area % 1.0 == 0.0) "${area.toLong()}" else "%.1f".format(area).replace(".0", "")
        "$formatted m²"
    } else ""

    val bedroomLabel = if (beds > 0) "$beds PN" else null
    val balconyDir = translateDirection(direction).ifEmpty { "-" }
    val mainDir = "Hướng cửa chính: ${translateDirection(direction).ifEmpty { "Đang cập nhật" }}"

    FutaPropertyCardContent(
        projectName = address.ifEmpty { "Dự án FUTA Land" },
        code = title,
        imageUrl = imageUrl,
        sizeText = sizeText,
        bedroomLabel = bedroomLabel,
        balconyDirectionText = balconyDir,
        mainDirectionText = mainDir,
        formattedPrice = price,
        hasTour = false,
        hasVideo = false,
        isFavorited = isFavorited,
        onFavoriteClick = onFavoriteClick,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun FutaPropertyCardContent(
    projectName: String,
    code: String,
    imageUrl: String,
    sizeText: String,
    bedroomLabel: String?,
    balconyDirectionText: String,
    mainDirectionText: String,
    formattedPrice: String,
    hasTour: Boolean,
    hasVideo: Boolean,
    isFavorited: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    val cardBorderColor = Color(0xFFE2E8F0)
    val textGray = Color(0xFF74777F)
    val textNavy = Color(0xFF061D3D)
    val brandOrange = Color(0xFFFF8D28)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = cardShape,
                ambientColor = Color(0x0A000000),
                spotColor = Color(0x0F000000)
            )
            .clip(cardShape)
            .background(Color.White)
            .border(1.dp, cardBorderColor, cardShape)
            .clickable(onClick = onClick)
    ) {
        Column {
            // 1. Top Media Image (height 195dp, rounded top corners)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = code,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // View 360 Badge (top-left, only if tour exists)
                if (hasTour) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(brandOrange)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "View 360",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Video Play Button (center, only if video exists)
                if (hasVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = brandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Favorite Heart Button (top-right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(32.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable(onClick = onFavoriteClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = if (isFavorited) R.drawable.futa_ic_heart_icon else R.drawable.ic_listing_heart),
                        contentDescription = "Yêu thích",
                        tint = if (isFavorited) Color(0xFFEF4444) else textNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Content Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
            ) {
                // Project Name
                Text(
                    text = projectName,
                    color = textGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Code / Title
                Text(
                    text = code,
                    color = textNavy,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Specs Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sizeText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.futa_ic_maximize3_icon),
                                contentDescription = null,
                                tint = textGray,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = sizeText,
                                color = textNavy,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (bedroomLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_listing_bedroom),
                                contentDescription = null,
                                tint = textGray,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = bedroomLabel,
                                color = textNavy,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_listing_compass),
                            contentDescription = null,
                            tint = textGray,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = balconyDirectionText,
                            color = textNavy,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Main Direction Line
                Text(
                    text = mainDirectionText,
                    color = textNavy,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Horizontal Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(1.dp)
                        .background(cardBorderColor)
                )

                // Footer: Price & Details Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedPrice,
                        color = brandOrange,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Chi tiết",
                            color = textNavy,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.futa_ic_arrow_right_icon),
                            contentDescription = null,
                            tint = textNavy,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun translateDirection(raw: String): String {
    val norm = raw.trim().lowercase()
        .replace("_", "-")
        .replace("đ", "d")
        .replace("Đ", "d")
        .replace(Regex("""[àáạảãâầấậẩẫăằắặẳẵ]"""), "a")
        .replace(Regex("""[èéẹẻẽêềếệểễ]"""), "e")
        .replace(Regex("""[ìíịỉĩ]"""), "i")
        .replace(Regex("""[òóọỏõôồốộổỗơờớợởỡ]"""), "o")
        .replace(Regex("""[ùúụủũưừứựửữ]"""), "u")
        .replace(Regex("""[ỳýỵỷỹ]"""), "y")
    return when (norm) {
        "dong" -> "Đông"
        "tay" -> "Tây"
        "nam" -> "Nam"
        "bac" -> "Bắc"
        "dong-nam", "dongnam" -> "Đông Nam"
        "dong-bac", "dongbac" -> "Đông Bắc"
        "tay-nam", "taynam" -> "Tây Nam"
        "tay-bac", "taybac" -> "Tây Bắc"
        else -> raw.trim()
    }
}
