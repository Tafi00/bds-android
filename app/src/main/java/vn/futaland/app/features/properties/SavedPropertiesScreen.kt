package vn.futaland.app.features.properties

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun SavedPropertiesScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadFavorites() {
        scope.launch {
            loading = true
            try {
                val favRes = APIClient.get().request("/favorites")
                val ids = favRes["data"].array.map { it.string }.filter { it.isNotEmpty() }
                if (ids.isEmpty()) {
                    items = emptyList()
                } else {
                    val aptsRes = APIClient.get().request(
                        "/apartments",
                        query = mapOf(
                            "recordIds" to ids.take(50).joinToString(","),
                            "limit" to "50"
                        )
                    )
                    items = aptsRes["data"].array
                }
            } catch (_: Exception) {
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFavorites()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FutaColors.PageBg)
    ) {
        // Top Header
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Căn yêu thích",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy
                )
            }
        }

        if (loading) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    FutaSkeletonBlock(height = 240.dp, radius = 18.dp)
                }
            }
        } else if (items.isEmpty()) {
            FutaEmptyState(
                icon = Icons.Default.FavoriteBorder,
                title = "Chưa có tin yêu thích",
                message = "Nhấn vào biểu tượng trái tim ở các tin đăng để lưu lại tại đây."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(items, key = { idx, apt -> (apt.id.ifEmpty { "fav" }) + "-$idx" }) { _, apt ->
                    FutaPropertyCard(
                        apartment = apt,
                        isFavorited = true,
                        onFavoriteClick = {
                            scope.launch {
                                try {
                                    APIClient.get().request("/favorites/${apt.id}", method = "POST")
                                    loadFavorites()
                                } catch (_: Exception) {}
                            }
                        },
                        onShareClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Xem bất động sản: ${PropertyFormatters.propertyTitle(apt)}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        },
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852"))
                            context.startActivity(intent)
                        },
                        onChatClick = { onNavigate(FutaDestinations.INBOX) },
                        onClick = { onNavigate(FutaDestinations.propertyDetail(apt.id)) }
                    )
                }
            }
        }
    }
}
