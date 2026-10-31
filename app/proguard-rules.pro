# Regole ProGuard / R8 per OpenTV.
# Media3, OkHttp e Coil includono già le regole consumer necessarie.

# Mantieni i messaggi di errore leggibili nei report di crash.
-keepattributes SourceFile,LineNumberTable

# Media3: evita stripping di classi caricate via riflessione
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
