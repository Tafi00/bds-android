package vn.futaland.app.features.properties

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import vn.futaland.app.core.auth.FavoritesStore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPropertiesScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val favoriteIds by FavoritesStore.ids.collectAsState()

    // iOS SavedPropertiesView: ids from the shared favorites store, then the
    // property DTOs via /apartments?recordIds=… in the saved order.
    fun loadFavorites() {
        scope.launch {
            loading = true
            error = null
            try {
                FavoritesStore.load(force = true)
                val ids = FavoritesStore.ids.value.toList()
                if (ids.isEmpty()) {
                    items = emptyList()
                } else {
                    val chunk = ids.take(200)
                    val propertiesRes = APIClient.get().request(
                        "/apartments",
                        query = mapOf(
                            "recordIds" to chunk.joinToString(","),
                            "limit" to maxOf(24, chunk.size).toString()
                        )
                    )
                    val loaded = propertiesRes["data"].array
                    items = chunk.mapNotNull { id -> loaded.firstOrNull { it["recordId"].string.ifEmpty { it.id } == id || it.id == id } }
                }
            } catch (e: Exception) {
                items = emptyList()
                error = e.message
            } finally {
                loading = false
                refreshing = false
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Căn yêu thích",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                }

            }
        }
        // Unfavorited items disappear immediately (heart tapped here or on the detail page).
        val visibleItems = items.filter { favoriteIds.contains(it["recordId"].string.ifEmpty { it.id }) || favoriteIds.contains(it.id) }
        if (loading && items.isEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    FutaPropertyCardSkeleton()
                }
            }
        } else if (error != null && items.isEmpty()) {
            FutaEmptyState(
                title = "Không thể tải dữ liệu",
                message = error ?: "",
                actionButton = { FutaButton(text = "Thử lại", onClick = { loadFavorites() }, variant = FutaButtonVariant.OUTLINE) }
            )
        } else if (visibleItems.isEmpty()) {
            FutaEmptyState(
                icon = Icons.Default.FavoriteBorder,
                title = "Chưa có tin yêu thích",
                message = "Nhấn vào biểu tượng trái tim ở các tin đăng để lưu lại tại đây."
            )
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; loadFavorites() },
                modifier = Modifier.fillMaxSize()
            ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(visibleItems, key = { idx, property -> (property.id.ifEmpty { "fav" }) + "-$idx" }) { _, property ->
                    FutaPropertyCard(
                        property = property,
                        onFavoriteClick = {
                            scope.launch { FavoritesStore.toggle(property["recordId"].string.ifEmpty { property.id }) }
                        },
                        onShareClick = {
                            val shareUrl = PropertyFormatters.shareUrl(property)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                                putExtra(Intent.EXTRA_SUBJECT, PropertyFormatters.propertyTitle(property))
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ sản phẩm"))
                        },
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0903715757"))
                            context.startActivity(intent)
                        },
                        onChatClick = { onNavigate(FutaDestinations.INBOX) },
                        onClick = { onNavigate(FutaDestinations.propertyDetail(property.id)) }
                    )
                }
            }
            }
        }
    }
}
