package com.yomira.app.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yomira.core.ui.UpdateDialog
import com.yomira.core.ui.UpdateDialogCopy
import java.io.File

@Composable
fun InAppUpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    copy: UpdateDialogCopy,
    model: ApkUpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val installRequest by model.installRequest.collectAsStateWithLifecycle()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun install(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            model.installationFailed()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val path = pendingPath
        pendingPath = null
        if (path != null && context.packageManager.canRequestPackageInstalls()) install(File(path))
        else model.installationFailed()
    }
    LaunchedEffect(installRequest) {
        val file = installRequest ?: return@LaunchedEffect
        model.consumeInstallRequest()
        if (context.packageManager.canRequestPackageInstalls()) install(file)
        else {
            pendingPath = file.absolutePath
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) {
                pendingPath = null
                model.installationFailed()
            }
        }
    }
    UpdateDialog(
        version = info.version,
        releaseNotes = info.releaseNotes,
        onUpdate = { model.update(info.apkDownloadUrl) },
        onDismiss = onDismiss,
        copy = copy,
        download = state,
    )
}
