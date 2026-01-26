# Keep ALL Compose classes (critical for runtime)
-keep class androidx.compose.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, Signature, Exception, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

# Keep SnakeYAML classes
-keep class org.yaml.snakeyaml.** { *; }
-keepclassmembers class org.yaml.snakeyaml.** { *; }
-dontwarn java.beans.**

# Keep all app classes
-keep class app.s4h.souschef.** { *; }
-keepclassmembers class app.s4h.souschef.** { *; }

# Keep Okio
-dontwarn okio.**
-keep class okio.** { *; }

# Keep kotlinx.datetime
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.serialization.**

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep lifecycle
-keep class androidx.lifecycle.** { *; }

# Keep markdown renderer
-keep class com.mikepenz.markdown.** { *; }

# Suppress warnings for missing classes
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
