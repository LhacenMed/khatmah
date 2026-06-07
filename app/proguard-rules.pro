# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# WorkManager – Room generates WorkDatabase_Impl at compile time; R8 must not
# strip or rename it because WorkManager looks it up by exact class name.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Keep Worker subclass constructors so WorkManager can instantiate them.
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Suppress notes about missing WorkManager internals (safe to ignore).
-dontnote androidx.work.**

# Keep AppTab and AppPage subclass names to prevent route collision in release mode.
# The core/nav/AppDest.kt autoRoute() function relies on class simpleName to generate unique route keys.
# Without this, R8 minifies multiple tabs to the same name (e.g. "s8"), causing LazyLayout key crashes.
-keepnames class * extends com.lhacenmed.khatmah.core.nav.AppTab
-keepnames class * extends com.lhacenmed.khatmah.core.nav.AppPage