# Keep JNI-bound native bridge classes and their native methods
-keepclasseswithmembers class com.ailocal.app.llm.LlamaBridge {
    native <methods>;
}
-keep class com.ailocal.app.llm.** { *; }
-keep class com.ailocal.app.tools.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
