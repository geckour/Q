# Spotify App Remote
# The SDK instantiates its internal classes by name through reflection.
-keep class com.spotify.android.appremote.** { *; }
-keep class com.spotify.protocol.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.**
