package com.yomira.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.TextPrimary

/** Shared, accessible action surface for long-press menus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionSheet(title: String, onDismiss: () -> Unit, actions: List<Pair<String, () -> Unit>>) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeepOledBlack, contentColor = TextPrimary) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            actions.forEach { (label, action) ->
                TextButton(
                    onClick = { onDismiss(); action() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonLavender),
                ) { Text(label, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
