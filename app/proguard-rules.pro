# ── SQLCipher ──
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**


# ── Firebase Auth ──
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ── Credential Manager ──
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Kotlinx Serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Hilt ──
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ona.miciclo.data.local.entity.** { *; }
-keep class com.ona.miciclo.core.sync.SupabaseSyncManager$* { *; }
-keep class com.ona.miciclo.core.update.** { *; }

# ── Alibaba MNN Chat ──
-keep class com.alibaba.mnnllm.android.llm.** { *; }
