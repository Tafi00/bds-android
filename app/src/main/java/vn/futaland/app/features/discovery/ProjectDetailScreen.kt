package vn.futaland.app.features.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.compose.LocalPlatformContext
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters

@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var project by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(projectId) {
        loading = true
        try {
            val pRes = APIClient.get().request("/projects/$projectId")
            project = pRes["data"]
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }
    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FutaColors.Navy)
                    }
                    Text(
                        text = project?.get("displayName")?.string?.ifEmpty { project?.get("name")?.string } ?: "Chi tiết dự án",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val projId = project?.get("id")?.string?.ifEmpty { projectId }.orEmpty()
                        val shareUrl = if (projId.isNotEmpty()) "https://bds.futaland.vn/projects/$projId" else "https://bds.futaland.vn/projects"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                            putExtra(Intent.EXTRA_SUBJECT, project?.get("displayName")?.string?.ifEmpty { project?.get("name")?.string } ?: "Chi tiết dự án")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ dự án"))
                    }) {
                        Icon(Icons.Default.Share, null, tint = FutaColors.Navy)
                    }
                }
            }
        },
        bottomBar = {
            FutaStickyActionBar {
                FutaButton(
                    text = "Xem chi tiết trên website",
                    variant = FutaButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        project?.let { p ->
                            val url = PropertyFormatters.projectWebsiteUrl(p)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        if (loading || project == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FutaSkeletonBlock(height = 220.dp, radius = 18.dp)
                FutaSkeletonBlock(height = 24.dp, width = 220.dp)
                FutaSkeletonBlock(height = 80.dp, radius = 14.dp)
            }
        } else {
            val p = project!!
            val title = p["displayName"].string.ifEmpty { p["name"].string }
            val banner = PropertyFormatters.resolveProjectBanner(p)
            val location = p["address"].string.ifEmpty { p["location"].string }.ifEmpty { p["province"].string }
            val developer = p["developer"].string

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(230.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE2E8F0))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(banner)
                            .transformations(ProjectBannerTransformation())
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                FutaCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FutaStatusBadge(title = p["status"].string.ifEmpty { "Đang mở bán" })
                            Text(developer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(location, fontSize = 12.5.sp, color = FutaColors.Slate)
                        }
                    }
                }
            }
        }
    }
}
