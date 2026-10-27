package com.yomira.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.yomira.core.ui.ImageMemoryPolicy
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yomira.app.sync.AutomaticSyncController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YomiraApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var automaticSync: AutomaticSyncController

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // minSdk 26: Coil may decode straight into hardware-backed buffers when safe.
        // Coil still falls back to software for transformations and unsupported devices.
        .allowHardware(true)
        .bitmapConfig(Bitmap.Config.HARDWARE)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizeBytes(ImageMemoryPolicy.artworkBytes(Runtime.getRuntime().maxMemory()))
                .build()
        }
        .crossfade(false)
        .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        automaticSync.start(this)
    }
}
