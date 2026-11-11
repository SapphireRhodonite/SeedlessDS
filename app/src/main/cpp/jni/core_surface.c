#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/data_space.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <errno.h>
#include <linux/sync_file.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include "seedlessds/nds.h"

extern nds_t *seedless_core(void);

typedef struct ASurfaceControl ASurfaceControl;
typedef struct ASurfaceTransaction ASurfaceTransaction;
typedef struct ASurfaceTransactionStats ASurfaceTransactionStats;
typedef void (*surface_transaction_callback_t)(void *, ASurfaceTransactionStats *);
enum { SURFACE_HIDDEN = 0, SURFACE_VISIBLE = 1, SURFACE_OPAQUE = 2 };

#define SURFACE_FUNCTIONS(F) \
    F(ASurfaceControl_createFromWindow) \
    F(ASurfaceControl_release) \
    F(ASurfaceTransaction_create) \
    F(ASurfaceTransaction_delete) \
    F(ASurfaceTransaction_apply) \
    F(ASurfaceTransaction_setBuffer) \
    F(ASurfaceTransaction_setVisibility) \
    F(ASurfaceTransaction_setZOrder) \
    F(ASurfaceTransaction_setBufferTransparency) \
    F(ASurfaceTransaction_setBufferDataSpace) \
    F(ASurfaceTransaction_setBufferTransform) \
    F(ASurfaceTransaction_setEnableBackPressure) \
    F(ASurfaceTransaction_setOnCommit) \
    F(ASurfaceTransaction_setOnComplete) \
    F(ASurfaceTransactionStats_getLatchTime) \
    F(ASurfaceTransactionStats_getPresentFenceFd) \
    F(ASurfaceTransactionStats_getPreviousReleaseFenceFd) \
    F(ASurfaceTransaction_reparent)
#define BUFFER_FUNCTIONS(F) \
    F(AHardwareBuffer_allocate) \
    F(AHardwareBuffer_release)
static ASurfaceControl *(*surface_ASurfaceControl_createFromWindow)(ANativeWindow *, const char *);
static void (*surface_ASurfaceControl_release)(ASurfaceControl *);
static ASurfaceTransaction *(*surface_ASurfaceTransaction_create)(void);
static void (*surface_ASurfaceTransaction_delete)(ASurfaceTransaction *);
static void (*surface_ASurfaceTransaction_apply)(ASurfaceTransaction *);
static void (*surface_ASurfaceTransaction_setBuffer)(ASurfaceTransaction *, ASurfaceControl *, AHardwareBuffer *, int);
static void (*surface_ASurfaceTransaction_setVisibility)(ASurfaceTransaction *, ASurfaceControl *, int8_t);
static void (*surface_ASurfaceTransaction_setZOrder)(ASurfaceTransaction *, ASurfaceControl *, int32_t);
static void (*surface_ASurfaceTransaction_setBufferTransparency)(ASurfaceTransaction *, ASurfaceControl *, int8_t);
static void (*surface_ASurfaceTransaction_setBufferDataSpace)(ASurfaceTransaction *, ASurfaceControl *, enum ADataSpace);
static void (*surface_ASurfaceTransaction_setBufferTransform)(ASurfaceTransaction *, ASurfaceControl *, int32_t);
static void (*surface_ASurfaceTransaction_setEnableBackPressure)(ASurfaceTransaction *, ASurfaceControl *, bool);
static void (*surface_ASurfaceTransaction_setOnCommit)(ASurfaceTransaction *, void *, surface_transaction_callback_t);
static void (*surface_ASurfaceTransaction_setOnComplete)(ASurfaceTransaction *, void *, surface_transaction_callback_t);
static int64_t (*surface_ASurfaceTransactionStats_getLatchTime)(ASurfaceTransactionStats *);
static int (*surface_ASurfaceTransactionStats_getPresentFenceFd)(ASurfaceTransactionStats *);
static int (*surface_ASurfaceTransactionStats_getPreviousReleaseFenceFd)(ASurfaceTransactionStats *, ASurfaceControl *);
static void (*surface_ASurfaceTransaction_reparent)(ASurfaceTransaction *, ASurfaceControl *, ASurfaceControl *);
static int (*surface_AHardwareBuffer_allocate)(const AHardwareBuffer_Desc *, AHardwareBuffer **);
static void (*surface_AHardwareBuffer_release)(AHardwareBuffer *);

static pthread_once_t surface_api_once = PTHREAD_ONCE_INIT;
static int surface_api_ready;

static void surface_load_api(void)
{
    void *android = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
    void *native = dlopen("libnativewindow.so", RTLD_NOW | RTLD_LOCAL);
    if (!android || !native) return;
    int ready = 1;
#define LOAD_SURFACE(name) surface_##name = dlsym(android, #name); ready &= surface_##name != NULL;
    SURFACE_FUNCTIONS(LOAD_SURFACE)
#undef LOAD_SURFACE
#define LOAD_BUFFER(name) surface_##name = dlsym(native, #name); ready &= surface_##name != NULL;
    BUFFER_FUNCTIONS(LOAD_BUFFER)
#undef LOAD_BUFFER
    surface_api_ready = ready;
}

enum { SURFACE_SLOT_COUNT = 3, SURFACE_HISTORY_COUNT = 16 };
enum { SURFACE_FREE, SURFACE_WRITING, SURFACE_SUBMITTED };

typedef struct {
    AHardwareBuffer *buffer;
    EGLImageKHR image;
    GLuint texture;
    GLuint framebuffer;
    int state;
    int release_fd;
    uint64_t generation;
} surface_slot_t;

typedef struct {
    uint64_t sequence;
    uint64_t token;
    uint64_t latch_ns;
    uint64_t rendered_ns;
    uint64_t release_ns;
    int gpu_fd;
    int present_fd;
    int release_fd;
    int pending;
} surface_frame_t;

typedef struct {
    pthread_t owner;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    ASurfaceControl *control;
    EGLDisplay display;
    PFNEGLCREATEIMAGEKHRPROC create_image;
    PFNEGLDESTROYIMAGEKHRPROC destroy_image;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC target_image;
    PFNEGLCREATESYNCKHRPROC create_sync;
    PFNEGLDESTROYSYNCKHRPROC destroy_sync;
    PFNEGLWAITSYNCKHRPROC wait_sync;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dup_fence;
    surface_slot_t slots[SURFACE_SLOT_COUNT];
    surface_frame_t history[SURFACE_HISTORY_COUNT];
    uint64_t interval_ns;
    uint64_t sequence;
    unsigned callbacks;
    int next_slot;
    int writing_slot;
    int previous_slot;
    int closing;
    int failed;
} surface_output_t;

typedef struct {
    surface_output_t *output;
    uint64_t sequence;
    int previous_slot;
    uint64_t previous_generation;
    unsigned remaining;
    int allocated;
} surface_callback_t;

static int surface_has_extension(const char *list, const char *name)
{
    if (!list) return 0;
    size_t length = strlen(name);
    for (const char *p = list; (p = strstr(p, name)); ++p)
        if ((p == list || p[-1] == ' ') && (!p[length] || p[length] == ' ')) return 1;
    return 0;
}

static uint64_t surface_fence_time(int fd)
{
    if (fd < 0) return 0;
    struct sync_file_info info = {0};
    if (ioctl(fd, SYNC_IOC_FILE_INFO, &info) || info.status != 1
        || !info.num_fences || info.num_fences > 16) return 0;
    struct sync_fence_info fences[16] = {0};
    info.sync_fence_info = (uint64_t)(uintptr_t)fences;
    if (ioctl(fd, SYNC_IOC_FILE_INFO, &info) || info.status != 1) return 0;
    uint64_t latest = 0;
    for (unsigned i = 0; i < info.num_fences; ++i) {
        if (fences[i].status != 1 || !fences[i].timestamp_ns) return 0;
        if (fences[i].timestamp_ns > latest) latest = fences[i].timestamp_ns;
    }
    return latest;
}

static int surface_wait_fence(int fd, uint64_t *timestamp)
{
    *timestamp = 0;
    if (fd < 0) return 1;
    struct pollfd item = {.fd = fd, .events = POLLIN};
    int result;
    do result = poll(&item, 1, -1); while (result < 0 && errno == EINTR);
    int valid = result == 1 && (item.revents & POLLIN) && !(item.revents & (POLLERR | POLLNVAL));
    if (valid) *timestamp = surface_fence_time(fd);
    close(fd);
    return valid;
}

static int surface_order_release(surface_output_t *output, int released)
{
    if (released < 0) return 1;
    int imported = dup(released);
    if (imported < 0) return 0;
    const EGLint attributes[] = {EGL_SYNC_NATIVE_FENCE_FD_ANDROID, imported, EGL_NONE};
    EGLSyncKHR fence = output->create_sync(output->display, EGL_SYNC_NATIVE_FENCE_ANDROID, attributes);
    if (fence == EGL_NO_SYNC_KHR) { close(imported); return 0; }
    EGLint result = output->wait_sync(output->display, fence, 0);
    output->destroy_sync(output->display, fence);
    return result == EGL_TRUE;
}

static void surface_committed(void *context, ASurfaceTransactionStats *stats)
{
    surface_callback_t *callback = context;
    surface_output_t *output = callback->output;
    int64_t latch = surface_ASurfaceTransactionStats_getLatchTime(stats);
    pthread_mutex_lock(&output->mutex);
    surface_frame_t *frame = &output->history[callback->sequence % SURFACE_HISTORY_COUNT];
    if (frame->sequence == callback->sequence && frame->pending) {
        if (latch > 0) frame->latch_ns = (uint64_t)latch;
    } else output->failed = 1;
    callback->remaining--;
    frame->pending = (int)callback->remaining;
    output->callbacks--;
    int dispose = !callback->remaining && callback->allocated;
    pthread_cond_broadcast(&output->condition);
    pthread_mutex_unlock(&output->mutex);
    if (dispose) free(callback);
}

static void surface_completed(void *context, ASurfaceTransactionStats *stats)
{
    surface_callback_t *callback = context;
    surface_output_t *output = callback->output;
    int released = callback->previous_slot >= 0
        ? surface_ASurfaceTransactionStats_getPreviousReleaseFenceFd(stats, output->control) : -1;
    int presented = callback->sequence ? surface_ASurfaceTransactionStats_getPresentFenceFd(stats) : -1;
    int64_t latch = callback->sequence ? surface_ASurfaceTransactionStats_getLatchTime(stats) : 0;
    pthread_mutex_lock(&output->mutex);
    if (callback->previous_slot >= 0) {
        surface_slot_t *slot = &output->slots[callback->previous_slot];
        if (slot->state == SURFACE_SUBMITTED && slot->generation == callback->previous_generation
            && slot->release_fd == -1) {
            slot->release_fd = released;
            released = -1;
            slot->state = SURFACE_FREE;
        } else output->failed = 1;
    }
    if (callback->sequence) {
        surface_frame_t *frame = &output->history[callback->sequence % SURFACE_HISTORY_COUNT];
        if (frame->sequence == callback->sequence && frame->pending) {
            frame->present_fd = presented;
            presented = -1;
            if (latch > 0) frame->latch_ns = (uint64_t)latch;
        } else output->failed = 1;
    }
    callback->remaining--;
    if (callback->sequence)
        output->history[callback->sequence % SURFACE_HISTORY_COUNT].pending = (int)callback->remaining;
    output->callbacks--;
    int dispose = !callback->remaining && callback->allocated;
    pthread_cond_broadcast(&output->condition);
    pthread_mutex_unlock(&output->mutex);
    if (released >= 0) close(released);
    if (presented >= 0) close(presented);
    if (dispose) free(callback);
}

static surface_output_t *surface_handle(jlong handle)
{
    surface_output_t *output = (surface_output_t *)(uintptr_t)handle;
    return output && pthread_equal(output->owner, pthread_self()) ? output : NULL;
}

static void surface_delete(surface_output_t *output)
{
    for (unsigned i = 0; i < SURFACE_SLOT_COUNT; ++i) {
        surface_slot_t *slot = &output->slots[i];
        uint64_t timestamp;
        surface_wait_fence(slot->release_fd, &timestamp);
        if (slot->framebuffer) glDeleteFramebuffers(1, &slot->framebuffer);
        if (slot->texture) glDeleteTextures(1, &slot->texture);
        if (slot->image != EGL_NO_IMAGE_KHR) output->destroy_image(output->display, slot->image);
        if (slot->buffer) surface_AHardwareBuffer_release(slot->buffer);
    }
    for (unsigned i = 0; i < SURFACE_HISTORY_COUNT; ++i) {
        if (output->history[i].gpu_fd >= 0) close(output->history[i].gpu_fd);
        if (output->history[i].present_fd >= 0) close(output->history[i].present_fd);
        if (output->history[i].release_fd >= 0) close(output->history[i].release_fd);
    }
    if (output->control) surface_ASurfaceControl_release(output->control);
    pthread_cond_destroy(&output->condition);
    pthread_mutex_destroy(&output->mutex);
    free(output);
}

JNIEXPORT jlong JNICALL
Java_com_seedlessds_core_SeedlessCore_createSurfaceOutput(JNIEnv *env, jclass cls,
                                                        jobject surface, jint width,
                                                        jint height, jlong interval)
{
    (void)cls;
    if (!surface || width <= 0 || height <= 0 || interval <= 0) return 0;
    pthread_once(&surface_api_once, surface_load_api);
    if (!surface_api_ready) return 0;
    EGLDisplay display = eglGetCurrentDisplay();
    const char *extensions = display != EGL_NO_DISPLAY ? eglQueryString(display, EGL_EXTENSIONS) : NULL;
    if (!surface_has_extension(extensions, "EGL_ANDROID_get_native_client_buffer")
        || !surface_has_extension(extensions, "EGL_ANDROID_image_native_buffer")
        || !surface_has_extension(extensions, "EGL_ANDROID_native_fence_sync")
        || !surface_has_extension(extensions, "EGL_KHR_wait_sync")
        || !surface_has_extension((const char *)glGetString(GL_EXTENSIONS), "GL_OES_EGL_image")
        || !surface_has_extension((const char *)glGetString(GL_EXTENSIONS), "GL_OES_EGL_sync")) return 0;
    surface_output_t *output = calloc(1, sizeof *output);
    if (!output) return 0;
    output->display = display;
    output->owner = pthread_self();
    output->interval_ns = (uint64_t)interval;
    output->writing_slot = -1;
    output->previous_slot = -1;
    for (unsigned i = 0; i < SURFACE_SLOT_COUNT; ++i) output->slots[i].release_fd = -1;
    for (unsigned i = 0; i < SURFACE_HISTORY_COUNT; ++i) {
        output->history[i].gpu_fd = -1;
        output->history[i].present_fd = -1;
        output->history[i].release_fd = -1;
    }
    if (pthread_mutex_init(&output->mutex, NULL)) { free(output); return 0; }
    if (pthread_cond_init(&output->condition, NULL)) {
        pthread_mutex_destroy(&output->mutex);
        free(output);
        return 0;
    }
    PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC get_buffer =
        (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)eglGetProcAddress("eglGetNativeClientBufferANDROID");
    output->create_image = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    output->destroy_image = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    output->target_image = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    output->create_sync = (PFNEGLCREATESYNCKHRPROC)eglGetProcAddress("eglCreateSyncKHR");
    output->destroy_sync = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
    output->wait_sync = (PFNEGLWAITSYNCKHRPROC)eglGetProcAddress("eglWaitSyncKHR");
    output->dup_fence = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC)eglGetProcAddress("eglDupNativeFenceFDANDROID");
    if (!get_buffer || !output->create_image || !output->destroy_image || !output->target_image
        || !output->create_sync || !output->destroy_sync || !output->wait_sync || !output->dup_fence) goto fail;
    AHardwareBuffer_Desc descriptor = {.width = (uint32_t)width, .height = (uint32_t)height,
        .layers = 1, .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
        .usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
            | AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY};
    for (unsigned i = 0; i < SURFACE_SLOT_COUNT; ++i) {
        surface_slot_t *slot = &output->slots[i];
        if (surface_AHardwareBuffer_allocate(&descriptor, &slot->buffer) || !slot->buffer) goto fail;
        const EGLint attributes[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
        slot->image = output->create_image(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
            get_buffer(slot->buffer), attributes);
        if (slot->image == EGL_NO_IMAGE_KHR) goto fail;
        glGenTextures(1, &slot->texture);
        glBindTexture(GL_TEXTURE_2D, slot->texture);
        output->target_image(GL_TEXTURE_2D, slot->image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &slot->framebuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, slot->framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, slot->texture, 0);
        if (!slot->texture || !slot->framebuffer
            || glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE
            || glGetError() != GL_NO_ERROR) goto fail;
    }
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if ((*env)->ExceptionCheck(env)) {
        if (window) ANativeWindow_release(window);
        goto fail;
    }
    if (!window) goto fail;
    output->control = surface_ASurfaceControl_createFromWindow(window, "SeedlessExternalOutput");
    ANativeWindow_release(window);
    if (!output->control) goto fail;
    ASurfaceTransaction *transaction = surface_ASurfaceTransaction_create();
    surface_ASurfaceTransaction_setEnableBackPressure(transaction, output->control, true);
    surface_ASurfaceTransaction_setVisibility(transaction, output->control, SURFACE_VISIBLE);
    surface_ASurfaceTransaction_setZOrder(transaction, output->control, 1);
    surface_ASurfaceTransaction_setBufferTransparency(transaction, output->control, SURFACE_OPAQUE);
    surface_ASurfaceTransaction_setBufferDataSpace(transaction, output->control, ADATASPACE_SRGB);
    surface_ASurfaceTransaction_setBufferTransform(transaction, output->control, ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL);
    surface_ASurfaceTransaction_apply(transaction);
    surface_ASurfaceTransaction_delete(transaction);
    __android_log_print(ANDROID_LOG_INFO, "reconDS", "External buffers: %dx%d, slots=%d", width, height, SURFACE_SLOT_COUNT);
    return (jlong)(uintptr_t)output;
fail:
    surface_delete(output);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_acquireSurfaceOutput(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    surface_output_t *output = surface_handle(handle);
    if (!output) return 0;
    pthread_mutex_lock(&output->mutex);
    if (output->writing_slot >= 0 || output->closing || output->failed) {
        pthread_mutex_unlock(&output->mutex);
        return 0;
    }
    int index = -1;
    while (index < 0 && !output->closing && !output->failed) {
        for (int offset = 0; offset < SURFACE_SLOT_COUNT; ++offset) {
            int i = (output->next_slot + offset) % SURFACE_SLOT_COUNT;
            if (output->slots[i].state == SURFACE_FREE) { index = i; break; }
        }
        if (index < 0) pthread_cond_wait(&output->condition, &output->mutex);
    }
    if (index < 0) { pthread_mutex_unlock(&output->mutex); return 0; }
    surface_slot_t *slot = &output->slots[index];
    slot->state = SURFACE_WRITING;
    output->writing_slot = index;
    output->next_slot = (index + 1) % SURFACE_SLOT_COUNT;
    int released = slot->release_fd;
    pthread_mutex_unlock(&output->mutex);
    if (!surface_order_release(output, released)) {
        pthread_mutex_lock(&output->mutex);
        output->failed = 1;
        pthread_mutex_unlock(&output->mutex);
        return 0;
    }
    return (jint)slot->framebuffer;
}

static void surface_read_timing(surface_output_t *output, uint64_t token,
                                uint64_t *observed, uint64_t *latch,
                                uint64_t *rendered_token, uint64_t *rendered,
                                uint64_t *released, uint64_t *completed)
{
    for (unsigned i = 0; i < SURFACE_HISTORY_COUNT; ++i) {
        surface_frame_t *frame = &output->history[i];
        if (!frame->token || frame->token >= token) continue;
        if (!frame->rendered_ns && frame->gpu_fd >= 0)
            frame->rendered_ns = surface_fence_time(frame->gpu_fd);
        if (frame->rendered_ns && !frame->release_ns && frame->release_fd >= 0)
            frame->release_ns = surface_fence_time(frame->release_fd);
        if (frame->latch_ns && frame->token > *observed) {
            *observed = frame->token;
            *latch = frame->latch_ns;
        }
        if (frame->rendered_ns && frame->token > *rendered_token) {
            *rendered_token = frame->token;
            *rendered = frame->rendered_ns;
            *released = frame->release_ns;
        }
        if (frame->rendered_ns && token - frame->token == 1) *completed = frame->token;
    }
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_completeSurfaceOutputFrame(JNIEnv *env, jclass cls,
                                                               jlong handle, jlong token,
                                                               jboolean consumed)
{
    (void)env;
    (void)cls;
    surface_output_t *output = surface_handle(handle);
    if (!output || token <= 0) return;
    uint64_t observed = 0, latch = 0, rendered_token = 0, rendered = 0, released = 0, completed = 0;
    pthread_mutex_lock(&output->mutex);
    if (consumed) surface_read_timing(output, (uint64_t)token, &observed, &latch,
        &rendered_token, &rendered, &released, &completed);
    pthread_mutex_unlock(&output->mutex);
    nds_complete_output_frame(seedless_core(), (uint64_t)token, consumed != JNI_FALSE,
        observed, latch, rendered_token, rendered, output->interval_ns, released, completed);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_refreshSurfaceOutputGpu(JNIEnv *env, jclass cls,
                                                            jlong handle, jlong token)
{
    (void)env;
    (void)cls;
    surface_output_t *output = surface_handle(handle);
    if (!output || token <= 0) return;
    uint64_t observed = 0, latch = 0, rendered_token = 0, rendered = 0, released = 0, completed = 0;
    pthread_mutex_lock(&output->mutex);
    surface_read_timing(output, (uint64_t)token, &observed, &latch, &rendered_token, &rendered, &released, &completed);
    pthread_mutex_unlock(&output->mutex);
    if (completed) nds_complete_output_gpu(seedless_core(), (uint64_t)token, completed);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_presentSurfaceOutput(JNIEnv *env, jclass cls,
                                                         jlong handle, jlong token)
{
    (void)env;
    (void)cls;
    surface_output_t *output = surface_handle(handle);
    if (!output) return JNI_FALSE;
    surface_callback_t *callback = calloc(1, sizeof *callback);
    if (!callback) return JNI_FALSE;
    EGLSyncKHR fence = output->create_sync(output->display, EGL_SYNC_NATIVE_FENCE_ANDROID, NULL);
    if (fence == EGL_NO_SYNC_KHR) { free(callback); return JNI_FALSE; }
    glFlush();
    int ready = output->dup_fence(output->display, fence);
    output->destroy_sync(output->display, fence);
    if (ready < 0) { free(callback); return JNI_FALSE; }
    int timestamp_fd = dup(ready);
    if (timestamp_fd < 0) { close(ready); free(callback); return JNI_FALSE; }
    pthread_mutex_lock(&output->mutex);
    uint64_t sequence = output->sequence + 1;
    surface_frame_t *frame = &output->history[sequence % SURFACE_HISTORY_COUNT];
    if (!sequence || output->writing_slot < 0 || output->closing || output->failed || frame->pending) {
        pthread_mutex_unlock(&output->mutex);
        close(timestamp_fd);
        close(ready);
        free(callback);
        return JNI_FALSE;
    }
    if (frame->gpu_fd >= 0) close(frame->gpu_fd);
    if (frame->present_fd >= 0) close(frame->present_fd);
    if (frame->release_fd >= 0) close(frame->release_fd);
    surface_slot_t *slot = &output->slots[output->writing_slot];
    *frame = (surface_frame_t){.sequence = sequence, .token = token > 0 ? (uint64_t)token : 0,
        .gpu_fd = timestamp_fd, .present_fd = -1, .pending = 2, .release_fd = slot->release_fd};
    slot->release_fd = -1;
    callback->output = output;
    callback->sequence = sequence;
    callback->previous_slot = output->previous_slot;
    callback->previous_generation = output->previous_slot >= 0
        ? output->slots[output->previous_slot].generation : 0;
    callback->remaining = 2;
    callback->allocated = 1;
    slot->generation = sequence;
    slot->state = SURFACE_SUBMITTED;
    output->previous_slot = output->writing_slot;
    output->writing_slot = -1;
    output->sequence = sequence;
    output->callbacks += 2;
    pthread_mutex_unlock(&output->mutex);
    ASurfaceTransaction *transaction = surface_ASurfaceTransaction_create();
    surface_ASurfaceTransaction_setBuffer(transaction, output->control, slot->buffer, ready);
    surface_ASurfaceTransaction_setOnCommit(transaction, callback, surface_committed);
    surface_ASurfaceTransaction_setOnComplete(transaction, callback, surface_completed);
    surface_ASurfaceTransaction_apply(transaction);
    surface_ASurfaceTransaction_delete(transaction);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_closeSurfaceOutput(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    surface_output_t *output = surface_handle(handle);
    if (!output) return;
    glFinish();
    surface_callback_t callback = {.output = output, .previous_slot = output->previous_slot,
        .previous_generation = output->previous_slot >= 0 ? output->slots[output->previous_slot].generation : 0,
        .remaining = 1};
    pthread_mutex_lock(&output->mutex);
    output->closing = 1;
    output->callbacks++;
    pthread_mutex_unlock(&output->mutex);
    ASurfaceTransaction *transaction = surface_ASurfaceTransaction_create();
    surface_ASurfaceTransaction_setVisibility(transaction, output->control, SURFACE_HIDDEN);
    surface_ASurfaceTransaction_reparent(transaction, output->control, NULL);
    surface_ASurfaceTransaction_setOnComplete(transaction, &callback, surface_completed);
    surface_ASurfaceTransaction_apply(transaction);
    surface_ASurfaceTransaction_delete(transaction);
    pthread_mutex_lock(&output->mutex);
    while (output->callbacks) pthread_cond_wait(&output->condition, &output->mutex);
    pthread_mutex_unlock(&output->mutex);
    surface_delete(output);
}

#undef SURFACE_FUNCTIONS
#undef BUFFER_FUNCTIONS
