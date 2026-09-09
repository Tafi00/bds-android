package vn.futaland.app.features.properties

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import vn.futaland.app.designsystem.FutaColors

/**
 * Standard Apartment Card matching `bds-clone/apartment-card.tsx` and iOS `PropertyCardView` 100%.
 */
@Composable
fun FutaPropertyCard(
    apartment: JSONValue,
    isFavorited: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val beds = apartment["bedrooms"].int.takeIf { it > 0 } ?: 2
    val baths = apartment["bathrooms"].int.takeIf { it > 0 } ?: 2
    val area = apartment["areaM2"].double.takeIf { it > 0 }
        ?: apartment["size_m2"].double.takeIf { it > 0 }
        ?: apartment["area"].double.takeIf { it > 0 } ?: 107.5
    val direction = apartment["direction"].string.ifEmpty { "Đông Nam" }
    val status = apartment["status"].string.ifEmpty { "Đang mở bán" }
    val rawAgent = apartment["agent"]["name"].string.trim()
    val agentName = if (rawAgent.isEmpty() || rawAgent.equals("null", ignoreCase = true)) "FUTA Land Advisor" else rawAgent
    val agentAvatar = apartment["agent"]["avatar"].string.takeIf { it.isNotEmpty() && it != "null" }

    FutaPropertyCard(
        title = PropertyFormatters.propertyTitle(apartment),
        price = PropertyFormatters.listingPrice(apartment),
        address = PropertyFormatters.addressText(apartment),
        imageUrl = PropertyFormatters.resolveImage(apartment),
        statusText = status,
        beds = beds,
        baths = baths,
        area = area,
        direction = direction,
        agentName = agentName,
        agentAvatar = agentAvatar,
        isFavorited = isFavorited,
        onFavoriteClick = onFavoriteClick,
        onShareClick = onShareClick,
        onCallClick = onCallClick,
        onChatClick = onChatClick,
        onClick = onClick,
        modifier = modifier
    )
}

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
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = cardShape,
                ambientColor = Color(0x06000000),
                spotColor = Color(0x0A000000)
            )
            .clip(cardShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE8ECEF), cardShape)
            .clickable(onClick = onClick)
    ) {
        Column {
            // 1. Image section - 16:10 aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color(0xFFE2E8F0))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left badges matching Web `apartment-card.tsx`
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gold tone badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFF37022))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusText.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Optional Navy tag
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF064A2B).copy(alpha = 0.95f))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FUTA CHÍNH CHỦ",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // 2. Content section
            Column(modifier = Modifier.padding(16.dp)) {
                // Price & Quick Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = price,
                        color = Color(0xFFE08A11), // Web #E08A11
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Share button (Web vector)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF7F9FC))
                                .border(1.dp, Color(0xFFE8E3DB), CircleShape)
                                .clickable(onClick = onShareClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.sf_card_share),
                                contentDescription = "Chia sẻ",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Favorite button (Web vector)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF7F9FC))
                                .border(1.dp, Color(0xFFE8E3DB), CircleShape)
                                .clickable(onClick = onFavoriteClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isFavorited) R.drawable.sf_card_heart_fill else R.drawable.sf_card_heart),
                                contentDescription = "Yêu thích",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Title - 2 lines max, #064A2B ExtraBold
                Text(
                    text = title,
                    color = Color(0xFF064A2B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                // Location row with web location icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.sf_mappin_circle_green),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = address,
                        color = Color(0xFF596579),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 3. Spec Strip - matching Web apartment-card.tsx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF8F9FA))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpecItem(iconRes = R.drawable.sf_spec_area, label = "${area} m²")
                    Text("•", color = Color(0xFFDFE6ED), fontSize = 12.sp)
                    SpecItem(iconRes = R.drawable.sf_spec_compass, label = direction)
                    Text("•", color = Color(0xFFDFE6ED), fontSize = 12.sp)
                    SpecItem(iconRes = R.drawable.sf_spec_bed, label = "$beds PN")
                    Text("•", color = Color(0xFFDFE6ED), fontSize = 12.sp)
                    SpecItem(iconRes = R.drawable.sf_spec_bath, label = "$baths WC")
                }

                Spacer(Modifier.height(12.dp))

                // Divider #E8E3DB
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE8E3DB))
                )

                Spacer(Modifier.height(12.dp))

                // 4. Footer Agent & Web Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!agentAvatar.isNullOrEmpty()) {
                            AsyncImage(
                                model = agentAvatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFFDFE6ED), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE9EEF5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = agentName.take(1).uppercase(),
                                    color = Color(0xFF064A2B),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = agentName,
                                color = Color(0xFF064A2B),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Môi giới FUTA Land",
                                color = Color(0xFF68759A),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Call button: circular 36x36dp, bg #F37022
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(1.5.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFFF37022))
                                .clickable(onClick = onCallClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.sf_btn_phone),
                                contentDescription = "Gọi",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        // Chat button: circular 36x36dp, bg #123355
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(1.5.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF123355))
                                .clickable(onClick = onChatClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.sf_btn_chat),
                                contentDescription = "Chat",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                }
            }
        }
    }
}
}

@Composable
private fun SpecItem(iconRes: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = Color(0xFF5E5749),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
