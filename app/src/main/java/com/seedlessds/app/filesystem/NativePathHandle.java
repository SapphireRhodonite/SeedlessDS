package com.seedlessds.app.filesystem;

import androidx.annotation.Keep;

import java.io.File;

@Keep
public class NativePathHandle {
    public final int fileFd;
    public final String fileName;
    public final String filePath;

    public NativePathHandle(File file) {
        this(file.getAbsolutePath(), file.getName());
    }

    public NativePathHandle(String path) {
        this(path, path.substring(path.lastIndexOf(File.separatorChar) + 1));
    }

    public NativePathHandle(String path, int fd, String name) {
        this.filePath = path;
        this.fileFd = fd;
        this.fileName = name;
    }

    public NativePathHandle(String path, String name) {
        this(path, -1, name);
    }
}
