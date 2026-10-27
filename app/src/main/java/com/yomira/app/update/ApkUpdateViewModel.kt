package com.yomira.app.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.core.ui.UpdateDownloadState
import com.yomira.core.ui.UpdateDownloadStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ApkUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(UpdateDownloadState())
    val state = mutableState.asStateFlow()
    private val mutableInstallRequest = MutableStateFlow<File?>(null)
    val installRequest = mutableInstallRequest.asStateFlow()
    private var downloaded: File? = null
    private var downloadedUrl: String? = null

    fun update(url: String) {
        if (state.value.status == UpdateDownloadStatus.Downloading) return
        val existing = downloaded
        if (existing?.isFile == true && downloadedUrl == url) {
            mutableState.value = UpdateDownloadState(UpdateDownloadStatus.ReadyToInstall)
            mutableInstallRequest.value = existing
            return
        }
        mutableState.value = UpdateDownloadState(UpdateDownloadStatus.Downloading)
        viewModelScope.launch {
            try {
                val file = ApkDownloader().download(url, File(getApplication<Application>().cacheDir, "updates")) {
                    mutableState.value = UpdateDownloadState(UpdateDownloadStatus.Downloading, it)
                }
                downloaded = file
                downloadedUrl = url
                mutableState.value = UpdateDownloadState(UpdateDownloadStatus.ReadyToInstall)
                mutableInstallRequest.value = file
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = UpdateDownloadState(UpdateDownloadStatus.Error, failed = true)
            }
        }
    }

    fun consumeInstallRequest() { mutableInstallRequest.value = null }

    fun installationFailed() {
        mutableState.value = UpdateDownloadState(UpdateDownloadStatus.ReadyToInstall, failed = true)
    }
}
