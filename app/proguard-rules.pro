# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.creatorpulse.**$$serializer { *; }
-keepclassmembers class dev.creatorpulse.** { *** Companion; }
-keepclasseswithmembers class dev.creatorpulse.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Tink (pulled in by security-crypto) references errorprone annotations that are compile-only
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
