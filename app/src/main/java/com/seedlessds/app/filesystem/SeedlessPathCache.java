package com.seedlessds.app.filesystem;

import android.content.Context;

import androidx.annotation.Keep;



import java.io.File;

@Keep
public class SeedlessPathCache {
    public static final String SYS_PREFIX = "SeedlessDS" + File.separator;
    public static final String USER_PREFIX = "User" + File.separator;

    public static void setAppContext(Context c) {
        PathCache.setAppContext(c);
    }

    public static NativePathHandle open(String path, String mode) {
        return PathCache.open(path, mode);
    }

    public static boolean remove(String path) {
        return PathCache.remove(path);
    }

    public static boolean rename(String from, String to) {
        return PathCache.rename(from, to);
    }
}
