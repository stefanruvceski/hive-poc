-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.stride.**$$serializer { *; }
-keepclassmembers class app.stride.** { *** Companion; }
-keepclasseswithmembers class app.stride.** { kotlinx.serialization.KSerializer serializer(...); }

# Jackson (used only inside the Music module's yt-dlp library: its update check reads JSON trees, getInfo maps JSON onto
# the library's mapper classes). Unused Jackson code may be shrunk away; names stay, and the mapped classes keep their
# fields and accessors, which Jackson finds by reflection.
-keepnames class com.fasterxml.jackson.** { *; }
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepclassmembers class * { @com.fasterxml.jackson.annotation.* <fields>; @com.fasterxml.jackson.annotation.* <methods>; }
-keepattributes Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

# Music (youtubedl-android): yt-dlp's JSON is read with Jackson, the library loads its parts by name.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
# commons-compress unpacks the bundled Python / ffmpeg zips; only the archive code it reaches is kept (service files are
# honoured by R8), names stay for its error messages.
-keepnames class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
