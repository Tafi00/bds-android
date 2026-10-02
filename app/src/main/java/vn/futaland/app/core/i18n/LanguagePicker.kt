package vn.futaland.app.core.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.FutaColors

/**
 * "Ngôn ngữ" row of the account screen (iOS `AppLanguagePicker`). Picking a language stores it and
 * recreates the activity so every screen re-renders in it; the account tab opens again afterwards.
 */
@Composable
fun LanguagePickerRow() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val current = I18n.language

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showDialog = true },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Language, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text("Ngôn ngữ", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy, modifier = Modifier.weight(1f))
        VerbatimText(current.displayName, fontSize = 14.sp, color = FutaColors.Slate)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Ngôn ngữ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDialog = false
                                    if (option != current) {
                                        I18n.setLanguage(option)
                                        I18n.reopenAccountTab = true
                                        context.findActivity()?.recreate()
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == current,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen),
                            )
                            Spacer(Modifier.width(12.dp))
                            VerbatimText(option.displayName, fontSize = 15.sp, color = FutaColors.Navy)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Đóng", color = FutaColors.Slate) } },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
