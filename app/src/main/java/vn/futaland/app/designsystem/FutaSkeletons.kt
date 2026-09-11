package vn.futaland.app.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.futaSkeletonPulse(): Modifier {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return this.background(Color(0xFFE2E8F0).copy(alpha = alpha))
}

@Composable
fun FutaSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    width: Dp? = null,
    radius: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(radius))
            .futaSkeletonPulse()
    )
}

@Composable
fun FutaSkeletonLines(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { index ->
            val fraction = if (index == count - 1) 0.65f else if (index % 2 == 0) 0.9f else 1.0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .futaSkeletonPulse()
            )
        }
    }
}

@Composable
fun FutaRegistrationCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FutaSkeletonBlock(height = 18.dp, width = 110.dp, radius = 6.dp)
                    FutaSkeletonBlock(height = 12.dp, width = 190.dp, radius = 4.dp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FutaSkeletonBlock(height = 20.dp, width = 95.dp, radius = 10.dp)
                    FutaSkeletonBlock(height = 20.dp, width = 110.dp, radius = 10.dp)
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutaSkeletonBlock(height = 28.dp, width = 28.dp, radius = 14.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FutaSkeletonBlock(height = 12.dp, width = 100.dp, radius = 4.dp)
                        FutaSkeletonBlock(height = 10.dp, width = 75.dp, radius = 3.dp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FutaSkeletonBlock(height = 12.dp, width = 90.dp, radius = 4.dp)
                        FutaSkeletonBlock(height = 10.dp, width = 70.dp, radius = 3.dp)
                    }
                    FutaSkeletonBlock(height = 28.dp, width = 28.dp, radius = 14.dp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FutaSkeletonBlock(height = 16.dp, width = 120.dp, radius = 4.dp)
                FutaSkeletonBlock(height = 12.dp, width = 95.dp, radius = 4.dp)
            }
        }
    }
}

@Composable
fun FutaPropertyCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.5.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Top Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .futaSkeletonPulse()
            )

            // Content
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FutaSkeletonBlock(height = 12.dp, width = 120.dp, radius = 4.dp)
                FutaSkeletonBlock(height = 16.dp, width = 200.dp, radius = 4.dp)

                // Specs row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FutaSkeletonBlock(height = 14.dp, width = 50.dp, radius = 4.dp)
                    FutaSkeletonBlock(height = 14.dp, width = 50.dp, radius = 4.dp)
                    FutaSkeletonBlock(height = 14.dp, width = 50.dp, radius = 4.dp)
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Price and Location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FutaSkeletonBlock(height = 18.dp, width = 90.dp, radius = 4.dp)
                    FutaSkeletonBlock(height = 12.dp, width = 110.dp, radius = 4.dp)
                }
            }
        }
    }
}

@Composable
fun FutaAdminRowSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FutaSkeletonBlock(height = 42.dp, width = 42.dp, radius = 10.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FutaSkeletonBlock(height = 15.dp, width = 160.dp, radius = 4.dp)
                FutaSkeletonBlock(height = 12.dp, width = 110.dp, radius = 4.dp)
            }
            FutaSkeletonBlock(height = 22.dp, width = 64.dp, radius = 11.dp)
        }
    }
}

@Composable
fun FutaDashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FutaSkeletonBlock(height = 22.dp, width = 150.dp, radius = 6.dp)
            FutaSkeletonBlock(height = 13.dp, width = 240.dp, radius = 4.dp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    FutaSkeletonBlock(height = 32.dp, width = 32.dp, radius = 8.dp)
                                    FutaSkeletonBlock(height = 12.dp, width = 60.dp, radius = 4.dp)
                                }
                                FutaSkeletonBlock(height = 22.dp, width = 80.dp, radius = 4.dp)
                                FutaSkeletonBlock(height = 11.dp, width = 90.dp, radius = 4.dp)
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    FutaSkeletonBlock(height = 32.dp, width = 32.dp, radius = 8.dp)
                                    FutaSkeletonBlock(height = 12.dp, width = 60.dp, radius = 4.dp)
                                }
                                FutaSkeletonBlock(height = 22.dp, width = 80.dp, radius = 4.dp)
                                FutaSkeletonBlock(height = 11.dp, width = 90.dp, radius = 4.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
