# gomobile classes are reached from native code by name; without these rules a release build cannot open a
# tunnel. Consumer rules, so every app using the engine gets them.
-keep class go.** { *; }
-keep class libXray.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
