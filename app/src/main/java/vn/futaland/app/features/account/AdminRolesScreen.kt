package vn.futaland.app.features.account

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
fun AdminRolesScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var selectedRoleCode by remember { mutableStateOf("admin") }
    var selectedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Dialog states
    var isCreatingRole by remember { mutableStateOf(false) }
    var newRoleCode by remember { mutableStateOf("") }
    var newRoleName by remember { mutableStateOf("") }
    var newRoleDesc by remember { mutableStateOf("") }

    var isEditingRole by remember { mutableStateOf(false) }
    var editRoleName by remember { mutableStateOf("") }
    var editRoleDesc by remember { mutableStateOf("") }

    // Expanded groups map
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    fun selectRole(role: JSONValue) {
        val code = role["code"].string.ifEmpty { role["id"].string }
        selectedRoleCode = code
        selectedPermissions = role["permissions"].array.map { it.string }.toSet()
    }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val rRoles = APIClient.get().request("/users/roles")
                val rCatalog = APIClient.get().request("/users/permissions/catalog")
                roles = rRoles["data"].array.ifEmpty { rRoles["data"]["records"].array }
                catalog = rCatalog["data"].array.ifEmpty { rCatalog["data"]["records"].array }

                if (roles.isNotEmpty()) {
                    val current = roles.firstOrNull {
                        (it["code"].string.ifEmpty { it["id"].string }) == selectedRoleCode
                    } ?: roles.first()
                    selectRole(current)
                }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun savePermissions() {
        if (selectedRoleCode.isEmpty()) return
        scope.launch {
            isSaving = true
            try {
                val permsJson = selectedPermissions.sorted().joinToString(",") { "\"$it\"" }
                val body = "{\"permissions\":[$permsJson]}"
                APIClient.get().request("/users/role-permissions/$selectedRoleCode", method = "PUT", bodyJson = body)
                ToastCenter.show("Đã lưu phân quyền vai trò $selectedRoleCode thành công!")
                loadData()
            } catch (e: Exception) {
                ToastCenter.show("Lỗi: ${e.message}", isError = true)
            } finally {
                isSaving = false
            }
        }
    }

    val currentRole = roles.firstOrNull {
        (it["code"].string.ifEmpty { it["id"].string }) == selectedRoleCode
    }
    val selectedRoleName = currentRole?.get("name")?.string?.ifEmpty { selectedRoleCode } ?: selectedRoleCode

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FutaHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )
                    Text(
                        text = "Vai trò & Phân quyền",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    FutaHeaderIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Lưu phân quyền",
                        tint = FutaColors.BrandGreen,
                        onClick = { savePermissions() }
                    )
                }
            }
        }
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(4) { FutaSkeletonBlock(height = 90.dp, radius = 16.dp) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Section 1: Inset Grouped Roles Container (Matching iOS Section exactly)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Danh sách vai trò",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FutaColors.Slate
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 2x2 Grid Layout for roles (No horizontal scroll needed!)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val chunkedRoles = roles.chunked(2)
                                    chunkedRoles.forEach { rowRoles ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowRoles.forEach { r ->
                                                val code = r["code"].string.ifEmpty { r["id"].string }
                                                val isSelected = code == selectedRoleCode
                                                val name = r["name"].string.ifEmpty { code }
                                                val permsCount = r["permissions"].array.size

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { selectRole(r) }
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.White else FutaColors.Navy,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "$permsCount quyền",
                                                            fontSize = 11.sp,
                                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else FutaColors.Slate
                                                        )
                                                    }
                                                }
                                            }
                                            if (rowRoles.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                // Bottom action row: Tạo vai trò mới + Sửa thông tin
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.clickable { isCreatingRole = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Tạo vai trò mới",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FutaColors.BrandGreen
                                        )
                                    }

                                    if (currentRole != null) {
                                        Text(
                                            text = "Sửa thông tin",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FutaColors.BrandGreen,
                                            modifier = Modifier.clickable {
                                                editRoleName = currentRole["name"].string.ifEmpty { selectedRoleCode }
                                                editRoleDesc = currentRole["description"].string
                                                isEditingRole = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Inset Grouped Permission Categories (Exact iOS List Style)
                item {
                    Text(
                        text = "Ma trận quyền cho: $selectedRoleName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FutaColors.Slate
                    )
                }

                // Inset Grouped Container for all categories
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            catalog.forEachIndexed { groupIdx, group ->
                                val groupName = group["name"].string.ifEmpty { "Nhóm quyền" }
                                val groupPerms = group["permissions"].array
                                val activeCount = groupPerms.count { p ->
                                    val key = p["key"].string.ifEmpty { p.string }
                                    selectedPermissions.contains(key)
                                }
                                val isExpanded = expandedGroups.getOrDefault(groupName, false)

                                Column {
                                    // Row item matching iOS DisclosureGroup label
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedGroups[groupName] = !isExpanded }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = groupName,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$activeCount/${groupPerms.size}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = FutaColors.Slate
                                            )
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Sub items when expanded
                                    if (isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF8FAFC))
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            groupPerms.forEachIndexed { pIdx, p ->
                                                val key = p["key"].string.ifEmpty { p.string }
                                                val label = p["label"].string.ifEmpty { key }
                                                val isChecked = selectedPermissions.contains(key)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val updated = selectedPermissions.toMutableSet()
                                                            if (isChecked) updated.remove(key) else updated.add(key)
                                                            selectedPermissions = updated
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 13.sp,
                                                        color = FutaColors.Navy,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    FutaSwitch(
                                                        checked = isChecked,
                                                        onCheckedChange = { on ->
                                                            val updated = selectedPermissions.toMutableSet()
                                                            if (on) updated.add(key) else updated.remove(key)
                                                            selectedPermissions = updated
                                                        },
                                                        activeColor = FutaColors.BrandGreen
                                                    )
                                                }
                                                if (pIdx < groupPerms.size - 1) {
                                                    HorizontalDivider(color = Color(0xFFE2E8F0).copy(alpha = 0.6f))
                                                }
                                            }
                                        }
                                    }

                                    if (groupIdx < catalog.size - 1) {
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    // Dialog Create Role
    if (isCreatingRole) {
        FutaDialog(
            visible = true,
            title = "Tạo vai trò mới",
            confirmText = "Tạo vai trò",
            onConfirm = {
                scope.launch {
                    try {
                        val body = """
                            {
                                "code": "${newRoleCode.trim().lowercase()}",
                                "name": "${newRoleName.trim()}",
                                "description": "${newRoleDesc.trim()}",
                                "permissions": []
                            }
                        """.trimIndent()
                        APIClient.get().request("/users/roles", method = "POST", bodyJson = body)
                        ToastCenter.show("Đã tạo vai trò mới thành công!")
                        isCreatingRole = false
                        newRoleCode = ""
                        newRoleName = ""
                        newRoleDesc = ""
                        loadData()
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { isCreatingRole = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tên vai trò", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = newRoleName, onValueChange = { newRoleName = it }, placeholder = "VD: Trưởng phòng kinh doanh")
                Text("Mã định danh (code)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = newRoleCode, onValueChange = { newRoleCode = it }, placeholder = "VD: sale_manager")
                Text("Mô tả vai trò", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = newRoleDesc, onValueChange = { newRoleDesc = it }, placeholder = "Mô tả nhiệm vụ...")
            }
        }
    }

    // Dialog Edit Role Metadata
    if (isEditingRole && currentRole != null) {
        FutaDialog(
            visible = true,
            title = "Sửa thông tin vai trò",
            confirmText = "Lưu thay đổi",
            onConfirm = {
                scope.launch {
                    try {
                        val permsJson = selectedPermissions.sorted().joinToString(",") { "\"$it\"" }
                        val body = """
                            {
                                "name": "${editRoleName.trim()}",
                                "description": "${editRoleDesc.trim()}",
                                "permissions": [$permsJson]
                            }
                        """.trimIndent()
                        APIClient.get().request("/users/roles/$selectedRoleCode", method = "PUT", bodyJson = body)
                        ToastCenter.show("Đã cập nhật thông tin vai trò thành công!")
                        isEditingRole = false
                        loadData()
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { isEditingRole = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tên vai trò", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = editRoleName, onValueChange = { editRoleName = it }, placeholder = "Tên vai trò")
                Text("Mô tả vai trò", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = editRoleDesc, onValueChange = { editRoleDesc = it }, placeholder = "Mô tả...")
            }
        }
    }
}
