package com.seedlessds.app.filesystem;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Keep;

import com.seedlessds.app.emu.EmuAssets;

import java.io.File;
import java.io.IOException;

@Keep
public class PathCache {
    public static final String SYS_PREFIX = "SeedlessDS" + File.separator;
    public static final String USER_PREFIX = "User" + File.separator;

    private static Context ctx;
    private static volatile File dataRoot;

    public static void setDataRoot(File root) {
        dataRoot = root;
    }

    private static String romKey;
    private static Uri romUri;
    private static String romRealPath;
    private static String romName;

    public static void setAppContext(Context c) {
        ctx = c.getApplicationContext();
    }

    private static File systemDir() {
        File d = dataRoot != null ? dataRoot : new File(ctx.getFilesDir(), EmuAssets.DATA_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static volatile String activeUserSub = "";

    private static File userDir() {
        File base = new File(systemDir(), "user");
        File d = activeUserSub.isEmpty() ? base : new File(base, activeUserSub);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File resolve(String path) {
        if (path.startsWith("/")) return new File(path);
        String p = sanitize(path);
        if (p.startsWith(SYS_PREFIX)) return new File(systemDir(), p.substring(SYS_PREFIX.length()));
        if (p.startsWith(USER_PREFIX)) return new File(userDir(), p.substring(USER_PREFIX.length()));
        throw new RuntimeException("Bad path prefix: " + path);
    }

    public static synchronized void changeRom(String key, Uri uri, String realPath, String name) {
        romKey = key;
        romUri = uri;
        romRealPath = realPath;
        romName = name;
    }

    public static synchronized NativePathHandle open(String path, String mode) {
        if (romKey != null && romKey.equals(path)) {
            if (romUri != null) {
                try {
                    ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(romUri, "r");
                    if (pfd != null) {
                        return new NativePathHandle(romName != null ? romName : path, pfd.detachFd(), romName != null ? romName : path);
                    }
                } catch (Exception e) {
                }
            }
            if (romRealPath != null) return new NativePathHandle(romRealPath, romName != null ? romName : new File(romRealPath).getName());
        }

        if (dataRoot == null && path.startsWith(USER_PREFIX)) {
            NativePathHandle h = com.seedlessds.app.emu.SaveLocation.openHandle(
                ctx, romUri, romRealPath, sanitize(path).substring(USER_PREFIX.length()), mode, activeUserSub, resolve(path));
            if (h != null) return h;
        }
        File f = resolve(path);
        String m = mode.replace("b", "");
        boolean write = m.contains("w") || m.contains("+") || m.contains("a");
        if (write) {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!f.exists()) {
                try { f.createNewFile(); } catch (IOException ignored) { }
            }
        }
        return new NativePathHandle(f);
    }

    public static synchronized boolean remove(String path) {
        if (dataRoot == null && path.startsWith(USER_PREFIX)) {
            Boolean r = com.seedlessds.app.emu.SaveLocation.remove(
                ctx, romUri, romRealPath, sanitize(path).substring(USER_PREFIX.length()), activeUserSub);
            if (r != null) { File i = resolve(path); if (i.exists()) i.delete(); return r; }
        }
        File f = resolve(path);
        return f.exists() && f.delete();
    }

    public static synchronized boolean rename(String from, String to) {
        if (dataRoot == null && from.startsWith(USER_PREFIX) && to.startsWith(USER_PREFIX)) {
            Boolean r = com.seedlessds.app.emu.SaveLocation.rename(
                ctx, romUri, romRealPath, sanitize(from).substring(USER_PREFIX.length()),
                sanitize(to).substring(USER_PREFIX.length()), activeUserSub);
            if (r != null) return r;
        }
        File a = resolve(from);
        if (!a.exists()) return false;
        File b = resolve(to);
        if (b.exists() && !b.delete()) return false;
        File parent = b.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return a.renameTo(b);
    }

    private static String sanitize(String s) {
        String sep = File.separator;
        return s.replaceAll(sep + "\\+", sep).replaceAll("^" + sep, "");
    }
}
