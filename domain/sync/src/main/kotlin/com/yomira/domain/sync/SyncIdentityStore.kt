package com.yomira.domain.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installation-scoped, randomly generated device identity.
 *
 * The value is not a hardware identifier and carries no user information. It only
 * distinguishes payloads when two devices write at nearly the same time.
 */
@Singleton
class SyncIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val deviceId: String by lazy {
        preferences.getString(KEY_DEVICE_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit().putString(KEY_DEVICE_ID, generated).apply()
            }
    }

    fun deviceId(): String = deviceId

    private companion object {
        const val PREFERENCES_NAME = "yomira_sync"
        const val KEY_DEVICE_ID = "device_id"
    }
}
