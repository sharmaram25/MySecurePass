# Argon2 JNI bindings
-keep class com.lambdapioneer.argon2kt.** { *; }

# zxcvbn loads dictionaries reflectively from resources
-keep class com.nulabinc.zxcvbn.** { *; }
-dontwarn com.nulabinc.zxcvbn.**

# Strip verbose/debug logging from release builds so nothing sensitive can reach logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
