package com.yomira.app.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yomira.app.R

@Composable
fun LocalBackupActions(model: LocalBackupViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), model::create)
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::restore)
    LaunchedEffect(message) {
        message?.let {
            val resource = when (it) {
                BackupMessage.CREATED -> R.string.backup_created
                BackupMessage.RESTORED -> R.string.backup_restored
                BackupMessage.FAILED -> R.string.backup_failed
            }
            Toast.makeText(context, resource, Toast.LENGTH_LONG).show()
            model.message.value = null
        }
    }
    Column {
        Text(stringResource(R.string.backup_scope))
        OutlinedButton(enabled = enabled && !busy, onClick = {
            try { create.launch(LocalBackupCodec.filename()) } catch (_: Exception) { model.launchFailed() }
        }) { Text(stringResource(R.string.backup_create)) }
        OutlinedButton(enabled = enabled && !busy, onClick = {
            try { restore.launch(arrayOf("*/*")) } catch (_: Exception) { model.launchFailed() }
        }) { Text(stringResource(R.string.backup_restore)) }
    }
}
