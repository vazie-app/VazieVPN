# R8 rules for the Vazie VPN release build.

# Room loads `<Database>_Impl` by name; WorkManager's database arrives through Glance.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# AndroidX Startup loads initializers by the names in the manifest.
-keep class * implements androidx.startup.Initializer { <init>(); }

# libXray calls back into Kotlin through gomobile proxies R8 cannot see; losing `DialerController` silently
# loops the engine's socket through its own tunnel.
-keep class libXray.** { *; }
-keep class go.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlinx.serialization: `serializer()` finds generated `$$serializer` classes by name (typed routes).
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Readable stack traces: keep line numbers; `mapping.txt` is archived by the release workflow.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
