-keepclasseswithmembernames class * { native <methods>; }

-keep class com.seedlessds.app.filesystem.SeedlessPathCache {
    public static *** open(java.lang.String, java.lang.String);
    public static *** remove(java.lang.String);
    public static *** rename(java.lang.String, java.lang.String);
    *;
}

-keep class com.seedlessds.app.filesystem.NativePathHandle {
    public *** fileFd;
    public *** fileName;
    public *** filePath;
    <init>(...);
    *;
}

-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
