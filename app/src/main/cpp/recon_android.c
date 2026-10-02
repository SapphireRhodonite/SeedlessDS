#include <android/log.h>
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define TAG "reconDS"

void recon_set_scale(unsigned n, int native_scale);
unsigned recon_page_scale(void);
void recon_drs_set(unsigned enabled, unsigned floor);
__attribute__((weak)) void recon_crash_log_fd(int fd);

static int g_fd = -1;
extern unsigned recon_present_flips;

static void log_line(const char *level, const char *txt) {
    __android_log_print(level[0] == 'E' ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO,
                        TAG, "%s", txt);
    time_t t = time(NULL);
    struct tm tm;
    localtime_r(&t, &tm);
    if (g_fd >= 0) {
        char line[1200];
        int n = snprintf(line, sizeof(line), "%02d:%02d:%02d %s core: %s\n", tm.tm_hour, tm.tm_min, tm.tm_sec, level, txt);
        if (n >= (int)sizeof(line)) n = (int)sizeof(line) - 1;
        if (n > 0) { ssize_t w = write(g_fd, line, (size_t)n); (void)w; }
    }
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_presentFlips(JNIEnv *env, jclass c) {
    (void)env; (void)c;
    return (jint)recon_present_flips;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_logFd(JNIEnv *env, jclass c, jint fd) {
    (void)env; (void)c;
    if (g_fd >= 0) close(g_fd);
    g_fd = fd >= 0 ? dup(fd) : -1;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_crashLogFd(JNIEnv *env, jclass c, jint fd) {
    (void)env; (void)c;
    if (!recon_crash_log_fd) return;
    recon_crash_log_fd(fd >= 0 ? dup(fd) : -1);
}

static void log_linef(const char *level, const char *fmt, ...) {
    char b[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(b, sizeof(b), fmt, ap);
    va_end(ap);
    log_line(level, b);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_setInternalResolution(JNIEnv *env, jclass c, jint n, jboolean native_scale) {
    (void)env; (void)c;
    recon_set_scale((unsigned)n, native_scale ? 1 : 0);
    log_linef("I", "internal scale set to x%d (native delivery %d)", (int)n, native_scale ? 1 : 0);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_pageScale(JNIEnv *env, jclass c) {
    (void)env; (void)c;
    return (jint)recon_page_scale();
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_setDynamicResolution(JNIEnv *env, jclass c, jboolean enabled, jint floor) {
    (void)env; (void)c;
    recon_drs_set(enabled ? 1u : 0u, (unsigned)floor);
    log_linef("I", "dynamic 3D resolution %s (floor x%d)", enabled ? "on" : "off", (int)floor);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_logRomEvent(JNIEnv *env, jclass c, jstring ev, jstring name) {
    (void)c;
    const char *e = ev ? (*env)->GetStringUTFChars(env, ev, NULL) : "?";
    const char *n = name ? (*env)->GetStringUTFChars(env, name, NULL) : "?";
    log_linef("I", "=== ROM %s: %s ===", e, n);
    if (ev)  (*env)->ReleaseStringUTFChars(env, ev, e);
    if (name) (*env)->ReleaseStringUTFChars(env, name, n);
}
