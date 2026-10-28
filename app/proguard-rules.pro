-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.stride.**$$serializer { *; }
-keepclassmembers class app.stride.** { *** Companion; }
-keepclasseswithmembers class app.stride.** { kotlinx.serialization.KSerializer serializer(...); }

# Jackson (used by the Music module's yt-dlp library) works by reflection.
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * { @com.fasterxml.jackson.annotation.* <fields>; @com.fasterxml.jackson.annotation.* <methods>; }
-keepattributes Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class kotlin.Metadata { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

# Music (youtubedl-android): yt-dlp's JSON is read with Jackson, the library loads its parts by name.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
