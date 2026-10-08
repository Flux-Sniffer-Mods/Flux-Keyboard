# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Remove logging in release builds (removes Log.d, Log.v, Log.i calls)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
}

# Keep org.json classes (though they're in Android SDK, better safe than sorry)
-keep class org.json.** { *; }

# Keep BuildConfig for runtime checks if needed
-keep class it.palsoftware.pastiera.BuildConfig { *; }
# Shizuku's shell processes are started by reflection (Shizuku.newProcess is private): without
# this, a minified build strips it and nothing through Shizuku runs (keyboard light, screen size,
# ADB shortcuts, trackpad discovery)
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }

# The shell helper is started by name with app_process
-keep class it.palsoftware.pastiera.adb.shell.ShellServer { public static void main(java.lang.String[]); }
-keep class io.github.muntashirakon.adb.** { *; }
-keep class it.palsoftware.pastiera.gaming.GameInputServer { public static void main(java.lang.String[]); }
