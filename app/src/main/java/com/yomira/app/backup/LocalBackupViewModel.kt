package com.yomira.app.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BackupMessage { CREATED, RESTORED, FAILED }

@HiltViewModel
class LocalBackupViewModel @Inject constructor(private val service: LocalBackupService) : ViewModel() {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    internal val message = MutableStateFlow<BackupMessage?>(null)
    fun create(uri: Uri?) = run(uri, BackupMessage.CREATED, service::create)
    fun restore(uri: Uri?) = run(uri, BackupMessage.RESTORED, service::restore)
    fun launchFailed() { message.value = BackupMessage.FAILED }
    private fun run(uri: Uri?, success: BackupMessage, action: suspend (Uri) -> Unit) {
        if (uri == null || busy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { action(uri); message.value = success
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message.value = BackupMessage.FAILED
            } finally { mutableBusy.value = false }
        }
    }
}
