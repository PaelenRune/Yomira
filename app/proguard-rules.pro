# =============================================================================
# Yomira release (R8 / minify + resource shrinking) keep rules
#
# The release build enables isMinifyEnabled + isShrinkResources, so every library
# that resolves classes, members or annotations reflectively at runtime needs an
# explicit contract here. Room, Hilt/Dagger, kotlinx.serialization, WorkManager,
# DataStore, Compose and Coroutines all do.
#
# Rules are ordered by library. Keeping attributes first matters: the
# polymorphic/serializer lookups below rely on annotations and signatures that
# R8 would otherwise strip.
# =============================================================================

# -----------------------------------------------------------------------------
# Global attributes & Kotlin metadata
# -----------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes InnerClasses,EnclosingMethod,Signature,Exceptions
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin reflect/metadata is what keeps `@Serializable` companions and Kotlin
# default-argument bridges discoverable after obfuscation.
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Enum valuesOf/valueOf are resolved by name across Compose, Room and navigation.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}

# Parcelable CREATOR fields are read reflectively by the framework.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# -----------------------------------------------------------------------------
# Room (core:database) — @Entity, @Dao, @Database, @TypeConverters, _Impl
# -----------------------------------------------------------------------------
# Room builds the schema from annotations on the entity classes at runtime.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keep @androidx.room.Embedded class * { *; }
-keep @androidx.room.Relation class * { *; }
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
    public static ** create(android.content.Context, java.lang.String, androidx.room.RoomDatabase$Callback);
}
# Generated `MyDatabase_Impl` is looked up by name via reflection.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class **_Impl { *; }
# Room reads @Query/@Insert/@Transaction through reflection on DAO methods.
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
# DAO interfaces are implemented by generated `*_Impl` classes; their methods
# must keep their names so the generated binding still overrides correctly.
-keep @androidx.room.Dao interface * { *; }
-keep class androidx.room.Room { *; }
-keep class androidx.room.Room$* { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# Dagger Hilt — @HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @HiltWorker
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { <init>(...); }
-keep @androidx.hilt.work.HiltWorker class * { <init>(...); }

# Hilt/Dagger generated code is instantiated reflectively.
# `@HiltAndroidApp`/`@AndroidEntryPoint` classes are resolved through the
# generated component interfaces (DaggerHilt...), so both the `extends` and
# `implements` shapes must survive R8 full mode.
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep interface * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManagerHolder { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.android.internal.** { *; }
-keep class androidx.hilt.work.** { *; }
-keep class androidx.hilt.navigation.** { *; }
-keep class androidx.hilt.lifecycle.** { *; }

# Every @Inject constructor and @Provides method is bound by name at runtime.
-keepclasseswithmembers,includedescriptorclasses class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keepclasseswithmembers class * {
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
    @dagger.multibindings.IntoSet <methods>;
    @dagger.multibindings.IntoMap <methods>;
    @dagger.multibindings.ElementsIntoSet <methods>;
}
-keep @dagger.Module class * { *; }
-keep @dagger.Provides class * { *; }
-keep @dagger.Component class * { *; }
-keep @javax.inject.Singleton class * { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# -----------------------------------------------------------------------------
# kotlinx.serialization — @Serializable, generated $$serializer, polymorphic
# -----------------------------------------------------------------------------
# Official kotlinx.serialization contract (serializers are looked up by name).
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
# Every generated serializer implements KSerializer and is instantiated through
# its Companion; both are looked up by the runtime, never referenced statically.
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yomira.**$$serializer { *; }
-keepclassmembers class com.yomira.** {
    *** Companion;
}
-keepclasseswithmembers class com.yomira.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Domain/extension payloads are decoded polymorphically by class name, so the
# serial name must survive alongside the serializer.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** $serializer;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
-keep class kotlinx.serialization.modules.** { *; }
-keep class kotlinx.serialization.descriptors.** { *; }
-keep class kotlinx.serialization.internal.** { *; }
-keep class kotlinx.serialization.builtins.** { *; }
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# -----------------------------------------------------------------------------
# WorkManager — ListenableWorker / CoroutineWorker (SyncWorker)
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
    <init>(android.content.Context, androidx.work.WorkerParameters, ...);
}
-keep class * implements androidx.work.WorkerFactory { *; }
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
-keep class androidx.work.impl.WorkManagerImpl { *; }
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.** { *; }
-keepclassmembers class * {
    @androidx.work.* <methods>;
}
# Workers are instantiated from the persisted WorkSpec by class name.
-keepnames class * extends androidx.work.ListenableWorker
-dontwarn androidx.work.**

# -----------------------------------------------------------------------------
# DataStore (Preferences)
# -----------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-keep class * extends androidx.datastore.preferences.core.Preferences { *; }
-keep class androidx.datastore.core.** { *; }
-keepclassmembers class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.**

# -----------------------------------------------------------------------------
# Jetpack Compose
# -----------------------------------------------------------------------------
# Compose emits reflective lookups for @Composable lambdas and the
# ComposableSingletons holder during R8 full mode.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.ui.tooling.** { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# Kotlin Coroutines
# -----------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    public static ** INSTANCE;
}
-keep class kotlin.coroutines.Continuation { *; }
# suspend bodies store locals in ContinuationImpl subclasses; R8 must keep the
# fields the generated state machine reads/writes.
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.ContinuationImpl {
    <fields>;
}
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.SuspendLambda {
    <fields>;
}
-dontwarn kotlinx.coroutines.**
# Coroutine debug probes are optional and absent from the release classpath.
-dontwarn kotlinx.coroutines.debug.**

# -----------------------------------------------------------------------------
# AndroidX / platform components resolved from the manifest
# -----------------------------------------------------------------------------
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }
-keep class androidx.startup.** { *; }
-keep class androidx.appcompat.app.AppLocalesMetadataHolderService { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# -----------------------------------------------------------------------------
# Networking / HTML parsing (OkHttp 4, Jsoup)
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.squareup.okhttp3.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
# OkHttp probes for its bundled public-suffix list by name.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# JVM-only / optional dependencies that never ship on Android.
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
