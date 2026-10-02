
#include <jni.h>
#include <string.h>
#include <stdint.h>
#include <dlfcn.h>
#include <android/log.h>

#define TAG "SeedlessBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)


JNIEXPORT jint JNICALL
Java_com_seedlessds_app_SeedlessBridge_dumpFramebuffer(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    LOGI("dumpFramebuffer is unavailable");
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_seedlessds_app_SeedlessBridge_nativeHello(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "libseedless_bridge.so loaded");
}

#define RA_OFF_MACHINE  0x34d9930UL
#define RA_OFF_MACHPTR  0x3f1e0b0UL
#define RA_OFF_ARENA    0xFD4E0UL
#define RA_DS_MAINRAM   0x0UL
#define RA_MAINRAM_SIZE 0x400000UL

static uint8_t *g_ds_main_ram = NULL;

typedef void *(*fn_main_ram)(void *);
static uint8_t *ra_resolve_main_ram(void) {
    if (g_ds_main_ram) return g_ds_main_ram;
    {
        void *h = dlopen("librecon_fn.so", RTLD_NOW | RTLD_NOLOAD);
        fn_main_ram f = h ? (fn_main_ram)dlsym(h, "nds_main_ram") : 0;
        if (f) {
            void *ram = f(NULL);
            if (ram) g_ds_main_ram = (uint8_t *)ram + RA_DS_MAINRAM;
        }
    }
    return g_ds_main_ram;
}

uint32_t ra_read_memory(uint32_t addr, uint8_t *buf, uint32_t n) {
    uint8_t *ram = ra_resolve_main_ram();
    if (!ram || addr >= RA_MAINRAM_SIZE) return 0;
    if (addr + n > RA_MAINRAM_SIZE) n = (uint32_t) (RA_MAINRAM_SIZE - addr);
    memcpy(buf, ram + addr, n);
    return n;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_SeedlessBridge_resetMemory(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    g_ds_main_ram = NULL;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_app_SeedlessBridge_readMemory(JNIEnv *env, jclass clazz,
                                                jlong ds_address, jbyteArray out, jint len) {
    (void) clazz;
    uint8_t *ram = ra_resolve_main_ram();
    if (!ram) { LOGI("readMemory: main RAM not mapped yet (no game loaded?)"); return 0; }
    uint32_t addr = (uint32_t) ds_address;
    if (addr >= RA_MAINRAM_SIZE) return 0;
    if ((uint32_t) len > RA_MAINRAM_SIZE - addr) len = (jint) (RA_MAINRAM_SIZE - addr);
    (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte *) (ram + addr));
    return len;
}


#include <android/hardware_buffer.h>
#include <math.h>
typedef int (*fn_hb_alloc)(const AHardwareBuffer_Desc *, AHardwareBuffer **);
typedef void (*fn_hb_desc)(const AHardwareBuffer *, AHardwareBuffer_Desc *);
typedef int (*fn_hb_lock)(AHardwareBuffer *, uint64_t, int32_t, const void *, void **);
typedef int (*fn_hb_unlock)(AHardwareBuffer *, int32_t *);
typedef void (*fn_hb_release)(AHardwareBuffer *);
static fn_hb_alloc hb_alloc; static fn_hb_desc hb_desc; static fn_hb_lock hb_lock; static fn_hb_unlock hb_unlock; static fn_hb_release hb_release;
static int load_nativewindow(void) {
    if (hb_alloc) return 1;
    void *h = dlopen("libnativewindow.so", RTLD_NOW);
    if (!h) return 0;
    hb_alloc = (fn_hb_alloc)dlsym(h, "AHardwareBuffer_allocate"); hb_desc = (fn_hb_desc)dlsym(h, "AHardwareBuffer_describe");
    hb_lock = (fn_hb_lock)dlsym(h, "AHardwareBuffer_lock"); hb_unlock = (fn_hb_unlock)dlsym(h, "AHardwareBuffer_unlock");
    hb_release = (fn_hb_release)dlsym(h, "AHardwareBuffer_release");
    return hb_alloc && hb_desc && hb_lock && hb_unlock && hb_release;
}
static AHardwareBuffer *g_pages_hb;
static void *g_pages_mem;
static int g_pages_hw_on;
static void *reserve_pages_hw(unsigned long size) {
    if (!load_nativewindow()) { __android_log_print(5, "reconDS", "PAGES-HW: no libnativewindow"); return 0; }
    unsigned n = (unsigned)lround(sqrt((double)size / 0xC0000));
    if (size != 4UL * 0x30000UL * n * n || n == 0) { __android_log_print(5, "reconDS", "PAGES-HW: size %lu does not match", size); return 0; }
    AHardwareBuffer_Desc d = { .width = 256 * n, .height = 192 * n * 4, .layers = 1, .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
        .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE };
    AHardwareBuffer *hb = 0;
    if (hb_alloc(&d, &hb) != 0 || !hb) { __android_log_print(5, "reconDS", "PAGES-HW: allocate failed"); return 0; }
    AHardwareBuffer_Desc real; hb_desc(hb, &real);
    if (real.stride != 256 * n) { __android_log_print(5, "reconDS", "PAGES-HW: stride %u != %u, discarded", real.stride, 256 * n); hb_release(hb); return 0; }
    void *mem = 0;
    if (hb_lock(hb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, 0, &mem) != 0 || !mem) {
        __android_log_print(5, "reconDS", "PAGES-HW: lock failed"); hb_release(hb); return 0; }
    if (g_pages_hb) { hb_unlock(g_pages_hb, 0); hb_release(g_pages_hb); }
    g_pages_hb = hb; g_pages_mem = mem;
    __android_log_print(4, "reconDS", "PAGES-HW: %ux%u RGBA8888 stride %u at %p (%lu bytes), locked", real.width, real.height, real.stride, mem, size);
    return mem;
}
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
typedef EGLClientBuffer (*fn_eglGetNativeClientBufferANDROID)(const AHardwareBuffer *);
typedef EGLImageKHR (*fn_eglCreateImageKHR)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint *);
typedef void (*fn_glEGLImageTargetTexture2DOES)(GLenum, GLeglImageOES);
JNIEXPORT jint JNICALL
Java_com_seedlessds_app_SeedlessBridge_texturePages(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (!g_pages_hb) { __android_log_print(5, "reconDS", "PAGES-HW texture: no buffer"); return 0; }
    void *legl = dlopen("libEGL.so", RTLD_NOW); void *lgl = dlopen("libGLESv2.so", RTLD_NOW);
    if (!legl || !lgl) return 0;
    void *(*gpa)(const char *) = (void *(*)(const char *))dlsym(legl, "eglGetProcAddress");
    EGLDisplay (*gcd)(void) = (EGLDisplay (*)(void))dlsym(legl, "eglGetCurrentDisplay");
    void (*genTex)(GLsizei, GLuint *) = (void (*)(GLsizei, GLuint *))dlsym(lgl, "glGenTextures");
    void (*bindTex)(GLenum, GLuint) = (void (*)(GLenum, GLuint))dlsym(lgl, "glBindTexture");
    void (*texParam)(GLenum, GLenum, GLint) = (void (*)(GLenum, GLenum, GLint))dlsym(lgl, "glTexParameteri");
    GLenum (*getErr)(void) = (GLenum (*)(void))dlsym(lgl, "glGetError");
    if (!gpa || !gcd || !genTex || !bindTex || !texParam) return 0;
    fn_eglGetNativeClientBufferANDROID gncb = (fn_eglGetNativeClientBufferANDROID)gpa("eglGetNativeClientBufferANDROID");
    fn_eglCreateImageKHR cik = (fn_eglCreateImageKHR)gpa("eglCreateImageKHR");
    fn_glEGLImageTargetTexture2DOES tt = (fn_glEGLImageTargetTexture2DOES)gpa("glEGLImageTargetTexture2DOES");
    if (!gncb || !cik || !tt) { __android_log_print(5, "reconDS", "PAGES-HW texture: missing EGL extensions"); return 0; }
    EGLClientBuffer cb = gncb(g_pages_hb);
    const EGLint attrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
    EGLImageKHR img = cik(gcd(), EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, cb, attrs);
    if (!img || img == EGL_NO_IMAGE_KHR) { __android_log_print(5, "reconDS", "PAGES-HW texture: eglCreateImageKHR failed"); return 0; }
    GLuint tex = 0; genTex(1, &tex); bindTex(GL_TEXTURE_2D, tex);
    tt(GL_TEXTURE_2D, (GLeglImageOES)img);
    texParam(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); texParam(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    texParam(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); texParam(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    GLenum e = getErr ? getErr() : 0;
    __android_log_print(4, "reconDS", "PAGES-HW texture %u over the AHardwareBuffer (EGLImage %p, glError 0x%x)", tex, img, e);
    return (jint)tex;
}
JNIEXPORT jint JNICALL
Java_com_seedlessds_app_SeedlessBridge_pageList(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    static unsigned (*f)(void);
    if (!f) { void *h = dlopen("librecon_fn.so", RTLD_NOW | RTLD_NOLOAD); if (h) f = (unsigned (*)(void))dlsym(h, "recon_page_list"); }
    return f ? (jint)f() : -1;
}
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_SeedlessBridge_niceEmu(JNIEnv *env, jclass clazz, jint nice) {
    void *h = dlopen("librecon_fn.so", RTLD_NOW | RTLD_NOLOAD);
    void (*f)(int) = h ? (void (*)(int))dlsym(h, "recon_present_set_nice_emu") : 0;
    (void)env; (void)clazz;
    if (f) f((int)nice);
    return f ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jobject JNICALL
Java_com_seedlessds_app_SeedlessBridge_pageBuffer(JNIEnv *env, jclass clazz) {
    (void)clazz;
    if (!g_pages_mem || !g_pages_hb) return NULL;
    AHardwareBuffer_Desc d; hb_desc(g_pages_hb, &d);
    return (*env)->NewDirectByteBuffer(env, g_pages_mem, (jlong)d.stride * d.height * 4);
}
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_SeedlessBridge_pagesActive(JNIEnv *env, jclass clazz) { (void)env; (void)clazz; return g_pages_mem ? JNI_TRUE : JNI_FALSE; }
JNIEXPORT void JNICALL
Java_com_seedlessds_app_SeedlessBridge_fboDest(JNIEnv *env, jclass clazz, jint fbo) {
    static void (*f)(unsigned);
    (void)env; (void)clazz;
    if (!f) { void *h = dlopen("librecon_fn.so", RTLD_NOW | RTLD_NOLOAD); if (h) f = (void (*)(unsigned))dlsym(h, "recon_present_set_fbo_dest"); }
    if (f) f((unsigned)fbo);
}
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_SeedlessBridge_presentInstant(JNIEnv *env, jclass clazz, jobject tx, jlong ns) {
    static void *(*from_java)(JNIEnv *, jobject);
    static void (*set_time)(void *, int64_t);
    static int looked_up;
    (void)clazz;
    if (!looked_up) {
        looked_up = 1;
        void *h = dlopen("libandroid.so", RTLD_NOW);
        if (h) {
            from_java = (void *(*)(JNIEnv *, jobject))dlsym(h, "ASurfaceTransaction_fromJava");
            set_time = (void (*)(void *, int64_t))dlsym(h, "ASurfaceTransaction_setDesiredPresentTime");
        }
        __android_log_print(4, "reconDS", "ATOMIC present time: fromJava=%p setDesiredPresentTime=%p", (void *)from_java, (void *)set_time);
    }
    if (!from_java || !set_time) return JNI_FALSE;
    void *t = from_java(env, tx);
    if (!t) return JNI_FALSE;
    set_time(t, (int64_t)ns);
    return JNI_TRUE;
}
void *sds_reserve_pages(unsigned long size) { return g_pages_hw_on ? reserve_pages_hw(size) : 0; }
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_SeedlessBridge_hwPages(JNIEnv *env, jclass clazz, jboolean on) {
    (void)env; (void)clazz;
    g_pages_hw_on = on ? 1 : 0;
    __android_log_print(4, "reconDS", "PAGES-HW: %s", on ? "on (the app owns the allocation)" : "off (posix_memalign in the core)");
    return JNI_TRUE;
}

typedef void (*fn_present_mode)(unsigned);
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_SeedlessBridge_presentMode(JNIEnv *env, jclass clazz, jint mode) {
    void *h = dlopen("librecon_fn.so", RTLD_NOW | RTLD_NOLOAD);
    fn_present_mode pm = h ? (fn_present_mode)dlsym(h, "recon_present_set_mode") : 0;
    (void)env; (void)clazz;
    if (!pm) {
        __android_log_print(5, "reconDS", "PRESENT mode=%d: recon_present_set_mode not found (handle=%p)", (int)mode, h);
        return JNI_FALSE;
    }
    pm((unsigned)mode);
    __android_log_print(4, "reconDS", "PRESENT mode=%d set in the core", (int)mode);
    return JNI_TRUE;
}
