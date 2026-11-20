# Keep rules for turning on R8 (minifyEnabled true, shrinkResources true in the release buildType, with
# proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro').
# NOT enabled: it has not been tested on a device. Test Shizuku, the remap engine and the widgets after enabling it.

# Shizuku starts this service in its own process from the APK, by class name.
-keep class com.tvsettings.app.ShellBinder { *; }

# remapd starts the injector with app_process by class name: it must keep its name and its main().
-keep class com.clean.stickman.daemon.Injector {
    public static void main(java.lang.String[]);
}

# Components named in the manifest are kept by the Android Gradle plugin on its own. These talk to the system by name:
-keep class com.tvsettings.app.NowPlayingListener { *; }
-keep class com.tvsettings.app.NowPlayingWidget { *; }
-keep class com.tvsettings.app.UsageWidgetProvider { *; }
