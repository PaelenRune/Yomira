package com.yomira.app.runtime

import android.app.Application
import android.content.Context
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

/**
 * Registers the host services third-party extension code expects to find in the
 * global Injekt scope. Keiyoushi sources built against extensions-lib resolve
 * `Injekt.get<Context>()` / `Injekt.get<Application>()` at class-init or
 * preference-screen time; without these registrations such code fails fast
 * inside the host process.
 *
 * The scope itself is initialized by the injekt-core library (a
 * DefaultRegistrar-backed InjektScope in its static initializer); this object
 * only adds singletons, idempotently and best-effort.
 */
object InjektHost {

    @Volatile
    private var registered = false

    fun initialize(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            runCatching {
                Injekt.addSingleton(fullType<Context>(), context)
                (context as? Application)?.let { application ->
                    Injekt.addSingleton(fullType<Application>(), application)
                }
            }
            registered = true
        }
    }
}
