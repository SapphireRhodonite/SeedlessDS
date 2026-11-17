
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "seedlessds/nds.h"

#define ICON_PALETTE_WORDS 16
#define ICON_PIXEL_BYTES 1024
#define ICON_TITLE_BYTES 256
#define INFO_STRING_BYTES 0x100

static nds_t *g_nds;

nds_t *seedless_core(void)
{
    return g_nds;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_attachSurface(JNIEnv *env, jclass cls,
                                                    jobject surface, jint versionCode,
                                                    jint apiLevel)
{
    (void)env;
    (void)cls;
    (void)surface;
    g_nds = nds_create(NULL, (uint32_t)apiLevel, (uint32_t)versionCode);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_setOutputPacing(JNIEnv *env, jclass cls,
                                                         jboolean enabled)
{
    (void)env;
    (void)cls;
    nds_set_output_pacing(g_nds, enabled != JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_recordOutputReady(JNIEnv *env, jclass cls, jlong token)
{
    (void)env;
    (void)cls;
    if (token > 0) nds_record_output_ready(g_nds, (uint64_t)token);
}

typedef struct {
    EGLDisplay display;
    EGLSurface surface;
    PFNEGLGETCOMPOSITORTIMINGANDROIDPROC read_timing;
    PFNEGLGETNEXTFRAMEIDANDROIDPROC next_frame;
    PFNEGLGETFRAMETIMESTAMPSANDROIDPROC read_timestamps;
    EGLuint64KHR frame_ids[4];
    EGLuint64KHR frame_source_ids[4];
    uint64_t frame_tokens[4];
    unsigned frame_count;
    unsigned frame_index;
    uint64_t observed_token;
    uint64_t latch_ns;
    uint64_t phase_ns;
    uint64_t rendered_ns;
    uint64_t buffer_released_ns;
    uint64_t recent_token;
    uint64_t recent_latch_ns;
    uint64_t recent_phase_ns;
    uint64_t query_ns;
    uint64_t pending_gpu_interval_ns;
    int supported;
    int latch_supported;
    int buffer_age_supported;
    int enabled;
} output_timing_t;

static __thread output_timing_t output_timing;

static int output_extension_supported(const char *extensions, const char *name)
{
    if (!extensions) return 0;
    const char *match = extensions;
    size_t length = strlen(name);
    while ((match = strstr(match, name))) {
        if ((match == extensions || match[-1] == ' ')
            && (!match[length] || match[length] == ' ')) return 1;
        match += length;
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_recordOutputBufferAge(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    if (!output_timing.supported || !output_timing.buffer_age_supported
        || !output_timing.frame_count) return;
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE
        || display != output_timing.display || surface != output_timing.surface) return;
    unsigned index = (output_timing.frame_index + 3u) % 4u;
    output_timing.frame_source_ids[index] = 0;
    EGLuint64KHR frame_id = 0;
    if (!output_timing.next_frame(display, surface, &frame_id)) {
        eglGetError();
        return;
    }
    if (frame_id != output_timing.frame_ids[index]) return;
    EGLint age = 0, behavior = 0;
    if (!eglQuerySurface(display, surface, EGL_BUFFER_AGE_EXT, &age)
        || !eglQuerySurface(display, surface, EGL_SWAP_BEHAVIOR, &behavior)) {
        eglGetError();
        return;
    }
    if (behavior == EGL_BUFFER_DESTROYED && age > 1 && (unsigned)age < output_timing.frame_count) {
        unsigned source = (index + 4u - (unsigned)age) % 4u;
        output_timing.frame_source_ids[index] = output_timing.frame_ids[source];
    }
}

static void output_timing_disable(void)
{
    if (!output_timing.enabled) return;
    if (eglSurfaceAttrib(output_timing.display, output_timing.surface, EGL_TIMESTAMPS_ANDROID, EGL_FALSE))
        output_timing.enabled = 0;
    else eglGetError();
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_releaseOutputTiming(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    output_timing_disable();
    memset(&output_timing, 0, sizeof output_timing);
}

static uint64_t output_composition_phase(uint64_t latch, uint64_t deadline, uint64_t interval)
{
    if (!latch || !deadline || !interval || interval > UINT64_MAX / 8u) return 0;
    uint64_t remainder = deadline >= latch ? (deadline - latch) % interval
        : (interval - (latch - deadline) % interval) % interval;
    if (remainder <= interval / 2u)
        return latch <= UINT64_MAX - remainder ? latch + remainder : 0;
    uint64_t advance = interval - remainder;
    return latch > advance ? latch - advance : 0;
}

static void output_latch_reference(uint64_t token, uint64_t interval, uint64_t deadline,
                                    uint64_t *observed_token, uint64_t *phase_ns, uint64_t *rendered_token, uint64_t *rendered_ns, uint64_t *buffer_released_ns, uint64_t *completed_gpu_token)
{
    if (!output_timing.latch_supported || !deadline || !interval || interval > UINT64_MAX / 8u) return;
    EGLnsecsANDROID latch = 0, rendered = 0, recent_latch = 0, released = 0, completed_rendered = 0;
    uint64_t source_token = 0, recent_token = 0, completed_token = 0;
    EGLuint64KHR frame_id;
    if (output_timing.next_frame(output_timing.display, output_timing.surface, &frame_id)) {
        unsigned previous = (output_timing.frame_index + 3u) % 4u;
        if (output_timing.frame_count && (frame_id <= output_timing.frame_ids[previous]
            || token <= output_timing.frame_tokens[previous])) {
            output_timing.frame_count = 0;
            output_timing.frame_index = 0;
            output_timing.observed_token = 0;
            output_timing.latch_ns = 0;
            output_timing.phase_ns = 0;
            output_timing.rendered_ns = 0;
            output_timing.buffer_released_ns = 0;
            output_timing.recent_token = 0;
            output_timing.recent_latch_ns = 0;
            output_timing.recent_phase_ns = 0;
        }
        if (output_timing.frame_count == 4u) {
            const EGLint names[2] = {EGL_COMPOSITION_LATCH_TIME_ANDROID, EGL_RENDERING_COMPLETE_TIME_ANDROID};
            EGLnsecsANDROID values[2] = {0, 0};
            source_token = output_timing.frame_tokens[output_timing.frame_index];
            if (output_timing.read_timestamps(output_timing.display, output_timing.surface,
                    output_timing.frame_ids[output_timing.frame_index], 2, names, values)) {
                latch = values[0];
                rendered = values[1];
            } else eglGetError();
            EGLuint64KHR source_id = output_timing.frame_source_ids[output_timing.frame_index];
            if (output_timing.buffer_age_supported && source_id) {
                const EGLint name = EGL_READS_DONE_TIME_ANDROID;
                if (!output_timing.read_timestamps(output_timing.display, output_timing.surface,
                        source_id, 1, &name, &released)) {
                    released = 0;
                    eglGetError();
                }
            }
            const EGLint recent_name = EGL_COMPOSITION_LATCH_TIME_ANDROID;
            unsigned recent_index = (output_timing.frame_index + 2u) % 4u;
            recent_token = output_timing.frame_tokens[recent_index];
            if (!output_timing.read_timestamps(output_timing.display, output_timing.surface,
                    output_timing.frame_ids[recent_index], 1, &recent_name, &recent_latch)) {
                recent_latch = 0;
                eglGetError();
            }
            const EGLint completed_name = EGL_RENDERING_COMPLETE_TIME_ANDROID;
            unsigned completed_index = (output_timing.frame_index + 3u) % 4u;
            completed_token = output_timing.frame_tokens[completed_index];
            if (!output_timing.read_timestamps(output_timing.display, output_timing.surface,
                    output_timing.frame_ids[completed_index], 1, &completed_name, &completed_rendered)) {
                completed_rendered = 0;
                eglGetError();
            }
        } else output_timing.frame_count++;
        output_timing.frame_ids[output_timing.frame_index] = frame_id;
        output_timing.frame_source_ids[output_timing.frame_index] = 0;
        output_timing.frame_tokens[output_timing.frame_index] = token;
        output_timing.frame_index = (output_timing.frame_index + 1u) % 4u;
    } else eglGetError();
    struct timespec time;
    if (clock_gettime(CLOCK_MONOTONIC, &time) != 0) return;
    uint64_t now = (uint64_t)time.tv_sec * 1000000000u + (uint64_t)time.tv_nsec;
    if (now < output_timing.query_ns) {
        output_timing.observed_token = 0;
        output_timing.latch_ns = 0;
        output_timing.phase_ns = 0;
        output_timing.rendered_ns = 0;
        output_timing.buffer_released_ns = 0;
        output_timing.recent_token = 0;
        output_timing.recent_latch_ns = 0;
        output_timing.recent_phase_ns = 0;
    }
    output_timing.query_ns = now;
    if (latch > 0 && (uint64_t)latch <= now && (uint64_t)latch > output_timing.latch_ns
        && source_token > output_timing.observed_token && source_token < token) {
        output_timing.observed_token = source_token;
        output_timing.latch_ns = (uint64_t)latch;
        output_timing.phase_ns = output_composition_phase((uint64_t)latch, deadline, interval);
        output_timing.rendered_ns = rendered > 0 && (uint64_t)rendered <= now ? (uint64_t)rendered : 0;
        output_timing.buffer_released_ns = released > 0 && (uint64_t)released < output_timing.rendered_ns
            ? (uint64_t)released : 0;
    }
    if (recent_latch > 0 && (uint64_t)recent_latch <= now
        && (uint64_t)recent_latch > output_timing.recent_latch_ns
        && recent_token > output_timing.recent_token && recent_token < token) {
        uint64_t recent_phase = output_composition_phase((uint64_t)recent_latch, deadline, interval);
        if (recent_phase && recent_phase <= now && now - recent_phase <= 8u * interval) {
            output_timing.recent_token = recent_token;
            output_timing.recent_latch_ns = (uint64_t)recent_latch;
            output_timing.recent_phase_ns = recent_phase;
        }
    }
    if (!output_timing.latch_ns || !output_timing.phase_ns || now < output_timing.latch_ns
        || now - output_timing.latch_ns > 8u * interval
        || output_timing.observed_token >= token || token - output_timing.observed_token > 8u) {
        output_timing.observed_token = 0;
        output_timing.latch_ns = 0;
        output_timing.phase_ns = 0;
        output_timing.rendered_ns = 0;
        output_timing.buffer_released_ns = 0;
        output_timing.recent_token = 0;
        output_timing.recent_latch_ns = 0;
        output_timing.recent_phase_ns = 0;
        return;
    }
    if (completed_rendered > 0 && (uint64_t)completed_rendered <= now
        && now - (uint64_t)completed_rendered <= 8u * interval
        && completed_token && completed_token < token && token - completed_token == 1u)
        *completed_gpu_token = completed_token;
    else if (completed_rendered <= 0 && completed_token && completed_token < token
        && token - completed_token == 1u)
        output_timing.pending_gpu_interval_ns = interval;
    *observed_token = output_timing.observed_token;
    *phase_ns = output_timing.phase_ns;
    *rendered_token = output_timing.observed_token;
    *rendered_ns = output_timing.rendered_ns;
    *buffer_released_ns = output_timing.buffer_released_ns;
    if (output_timing.recent_token >= output_timing.observed_token
        && output_timing.recent_token < token && token - output_timing.recent_token <= 8u
        && output_timing.recent_phase_ns && now >= output_timing.recent_phase_ns
        && now - output_timing.recent_phase_ns <= 8u * interval
        && now >= output_timing.recent_latch_ns
        && now - output_timing.recent_latch_ns <= 8u * interval) {
        *observed_token = output_timing.recent_token;
        *phase_ns = output_timing.recent_phase_ns;
    }
}

static void output_compositor_timing(uint64_t token, uint64_t *observed_token,
                                     uint64_t *phase_ns, uint64_t *rendered_token, uint64_t *rendered_ns, uint64_t *buffer_released_ns, uint64_t *completed_gpu_token, uint64_t *interval)
{
    output_timing.pending_gpu_interval_ns = 0;
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) return;
    if (display != output_timing.display || surface != output_timing.surface) {
        output_timing_disable();
        memset(&output_timing, 0, sizeof output_timing);
        output_timing.display = display;
        output_timing.surface = surface;
        const char *extensions = eglQueryString(display, EGL_EXTENSIONS);
        if (extensions && strstr(extensions, "EGL_ANDROID_get_frame_timestamps")) {
            PFNEGLGETCOMPOSITORTIMINGSUPPORTEDANDROIDPROC query_support =
                (PFNEGLGETCOMPOSITORTIMINGSUPPORTEDANDROIDPROC)eglGetProcAddress("eglGetCompositorTimingSupportedANDROID");
            PFNEGLGETFRAMETIMESTAMPSUPPORTEDANDROIDPROC timestamp_support =
                (PFNEGLGETFRAMETIMESTAMPSUPPORTEDANDROIDPROC)eglGetProcAddress("eglGetFrameTimestampSupportedANDROID");
            output_timing.read_timing = (PFNEGLGETCOMPOSITORTIMINGANDROIDPROC)eglGetProcAddress("eglGetCompositorTimingANDROID");
            output_timing.next_frame = (PFNEGLGETNEXTFRAMEIDANDROIDPROC)eglGetProcAddress("eglGetNextFrameIdANDROID");
            output_timing.read_timestamps = (PFNEGLGETFRAMETIMESTAMPSANDROIDPROC)eglGetProcAddress("eglGetFrameTimestampsANDROID");
            output_timing.supported = query_support && output_timing.read_timing
                && query_support(display, surface, EGL_COMPOSITE_INTERVAL_ANDROID)
                && query_support(display, surface, EGL_COMPOSITE_DEADLINE_ANDROID);
            output_timing.latch_supported = timestamp_support && output_timing.next_frame
                && output_timing.read_timestamps
                && timestamp_support(display, surface, EGL_COMPOSITION_LATCH_TIME_ANDROID)
                && timestamp_support(display, surface, EGL_RENDERING_COMPLETE_TIME_ANDROID);
            output_timing.supported = output_timing.supported && output_timing.latch_supported;
            output_timing.buffer_age_supported = output_timing.supported
                && (output_extension_supported(extensions, "EGL_EXT_buffer_age")
                    || output_extension_supported(extensions, "EGL_KHR_partial_update"))
                && timestamp_support(display, surface, EGL_READS_DONE_TIME_ANDROID);
            if (output_timing.supported) {
                output_timing.enabled = eglSurfaceAttrib(display, surface, EGL_TIMESTAMPS_ANDROID, EGL_TRUE);
                if (!output_timing.enabled) {
                    output_timing.supported = 0;
                    eglGetError();
                }
            }
        }
    }
    if (!output_timing.supported) return;
    const EGLint names[2] = {EGL_COMPOSITE_INTERVAL_ANDROID, EGL_COMPOSITE_DEADLINE_ANDROID};
    EGLnsecsANDROID values[2] = {0, 0};
    if (!output_timing.read_timing(display, surface, 2, names, values)) {
        output_timing.supported = 0;
        eglGetError();
        output_timing_disable();
        return;
    }
    if (values[0] > 0 && values[1] > 0) {
        output_latch_reference(token, (uint64_t)values[0], (uint64_t)values[1], observed_token, phase_ns, rendered_token, rendered_ns, buffer_released_ns, completed_gpu_token);
        if (*observed_token) *interval = (uint64_t)values[0];
    }
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_completeOutputFrame(JNIEnv *env, jclass cls,
                                                       jlong token, jboolean consumed)
{
    (void)env;
    (void)cls;
    uint64_t observed_token = 0, phase_ns = 0, rendered_token = 0, rendered_ns = 0, buffer_released_ns = 0, completed_gpu_token = 0, interval = 0;
    if (token != 0 && consumed != JNI_FALSE)
        output_compositor_timing((uint64_t)token, &observed_token, &phase_ns, &rendered_token, &rendered_ns, &buffer_released_ns, &completed_gpu_token, &interval);
    nds_complete_output_frame(g_nds, (uint64_t)token, consumed != JNI_FALSE,
                              observed_token, phase_ns, rendered_token, rendered_ns, interval, buffer_released_ns, completed_gpu_token);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_refreshOutputGpu(JNIEnv *env, jclass cls, jlong token)
{
    (void)env;
    (void)cls;
    uint64_t current_token = (uint64_t)token;
    uint64_t interval = output_timing.pending_gpu_interval_ns;
    if (!current_token || !interval || interval > UINT64_MAX / 8u
        || !output_timing.supported || !output_timing.enabled
        || !output_timing.read_timestamps || output_timing.frame_count < 2u) return;
    unsigned current = (output_timing.frame_index + 3u) % 4u;
    unsigned previous = (output_timing.frame_index + 2u) % 4u;
    uint64_t previous_token = output_timing.frame_tokens[previous];
    if (output_timing.frame_tokens[current] != current_token || !previous_token
        || previous_token >= current_token || current_token - previous_token != 1u) return;
    output_timing.pending_gpu_interval_ns = 0;
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE
        || display != output_timing.display || surface != output_timing.surface) return;
    const EGLint name = EGL_RENDERING_COMPLETE_TIME_ANDROID;
    EGLnsecsANDROID rendered = 0;
    if (!output_timing.read_timestamps(display, surface,
            output_timing.frame_ids[previous], 1, &name, &rendered)) {
        eglGetError();
        return;
    }
    struct timespec time;
    if (rendered <= 0 || clock_gettime(CLOCK_MONOTONIC, &time) != 0) return;
    uint64_t now = (uint64_t)time.tv_sec * 1000000000u + (uint64_t)time.tv_nsec;
    if (now < output_timing.query_ns || now - output_timing.query_ns > 8u * interval
        || (uint64_t)rendered > now || now - (uint64_t)rendered > 8u * interval) return;
    nds_complete_output_gpu(g_nds, current_token, previous_token);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_setEarlyInputPacing(JNIEnv *env, jclass cls,
                                                       jboolean enabled)
{
    (void)env;
    (void)cls;
    nds_set_early_input_pacing(g_nds, enabled != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_loadRom(JNIEnv *env, jclass cls, jstring path,
                                              jint stateSlot, jlong configBits,
                                              jint benchmarkFrames, jboolean inCacheDir,
                                              jlong runLimitUs)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    nds_rom_open_t open;
    int ok;

    (void)cls;
    if (utf == NULL)
        return JNI_FALSE;

    open.path = utf;
    open.config_bits = (uint64_t)configBits;
    open.run_limit_us = (uint64_t)runLimitUs;
    open.state_slot = (int)stateSlot;
    open.benchmark_frames = (int)benchmarkFrames;
    open.in_cache_dir = (inCacheDir != JNI_FALSE);

    ok = nds_load_rom(g_nds, &open);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_loadFirmware(JNIEnv *env, jclass cls,
                                                  jlong configBits, jlong runLimitUs)
{
    (void)env;
    (void)cls;
    return nds_load_firmware(g_nds, (uint64_t)configBits, (uint64_t)runLimitUs)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_insertGba(JNIEnv *env, jclass cls, jstring path,
                                                jint stateSlot, jboolean inCacheDir,
                                                jlong runLimitUs)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    int ok;

    (void)cls;
    if (utf == NULL)
        return JNI_FALSE;

    ok = nds_insert_gba(g_nds, utf, (int)stateSlot, inCacheDir != JNI_FALSE,
                        (uint64_t)runLimitUs);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_reset(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    nds_reset(g_nds);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_pause(JNIEnv *env, jclass cls, jint mode)
{
    (void)env;
    (void)cls;
    nds_pause(g_nds, (int)mode);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_quit(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    nds_quit(g_nds);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_release(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    nds_destroy(g_nds);
    g_nds = NULL;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_applyConfig(JNIEnv *env, jclass cls, jlong bits)
{
    (void)env;
    (void)cls;
    nds_apply_config(g_nds, (uint64_t)bits);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_audioVolume(JNIEnv *env, jclass cls, jint volume)
{
    (void)env;
    (void)cls;
    nds_audio_volume(g_nds, (int)volume);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_firmwareUser(JNIEnv *env, jclass cls,
                                                   jstring nickname, jint packed)
{
    const jchar *chars = (*env)->GetStringChars(env, nickname, NULL);
    jsize units;

    (void)cls;
    if (chars == NULL) {
        nds_set_firmware_user(g_nds, NULL, 0, (uint32_t)packed);
        return;
    }
    units = (*env)->GetStringLength(env, nickname);
    nds_set_firmware_user(g_nds, (const uint16_t *)chars, (size_t)units,
                          (uint32_t)packed);
    (*env)->ReleaseStringChars(env, nickname, chars);
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_romHeader(JNIEnv *env, jclass cls, jstring path, jstring name)
{
    uint8_t header[NDS_CARTRIDGE_HEADER_BYTES];
    (void)cls;
    const char *source = (*env)->GetStringUTFChars(env, path, NULL);
    if (source == NULL)
        return NULL;
    const char *file_name = (*env)->GetStringUTFChars(env, name, NULL);
    int ok = 0;
    if (file_name != NULL) {
        ok = nds_rom_header(source, file_name, header);
        (*env)->ReleaseStringUTFChars(env, name, file_name);
    }
    (*env)->ReleaseStringUTFChars(env, path, source);
    if (!ok || (*env)->ExceptionCheck(env))
        return NULL;
    jbyteArray result = (*env)->NewByteArray(env, sizeof header);
    if (result != NULL)
        (*env)->SetByteArrayRegion(env, result, 0, sizeof header, (const jbyte *)header);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_romSection(JNIEnv *env, jclass cls, jstring path, jstring name,
                                                jint offset, jint size)
{
    (void)cls;
    if (!path || !name || offset < 0 || size <= 0 || size > 16 * 1024 * 1024) return NULL;
    const char *source = (*env)->GetStringUTFChars(env, path, NULL);
    if (source == NULL) return NULL;
    const char *file_name = (*env)->GetStringUTFChars(env, name, NULL);
    uint8_t *data = NULL;
    int count = -1;
    if (file_name != NULL) {
        data = malloc((size_t)size);
        if (data != NULL) count = nds_rom_read_section(source, file_name, offset, data, size);
        (*env)->ReleaseStringUTFChars(env, name, file_name);
    }
    (*env)->ReleaseStringUTFChars(env, path, source);
    jbyteArray result = NULL;
    if (count >= 0 && !(*env)->ExceptionCheck(env)) {
        result = (*env)->NewByteArray(env, count);
        if (result != NULL && count > 0) (*env)->SetByteArrayRegion(env, result, 0, count, (const jbyte *)data);
    }
    free(data);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_romIsNds(JNIEnv *env, jclass cls, jstring path)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    int ok;

    (void)cls;
    if (utf == NULL)
        return JNI_FALSE;
    ok = nds_rom_is_nds(utf);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_romType(JNIEnv *env, jclass cls, jstring path)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    int kind;

    (void)cls;
    if (utf == NULL)
        return 0;
    kind = nds_rom_type(utf);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    return (jint)kind;
}

JNIEXPORT jlong JNICALL
Java_com_seedlessds_core_SeedlessCore_romSize(JNIEnv *env, jclass cls, jstring path)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    uint32_t size;

    (void)cls;
    if (utf == NULL)
        return 0;
    size = nds_rom_size(utf);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    return (jlong)size;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_romIcon(JNIEnv *env, jclass cls, jstring path,
                                              jintArray palette, jbyteArray pixels,
                                              jbyteArray title)
{
    const char *utf;
    uint32_t words[ICON_PALETTE_WORDS];
    uint8_t bytes[ICON_PIXEL_BYTES];
    uint8_t name[ICON_TITLE_BYTES];
    int ok;

    (void)cls;
    utf = (*env)->GetStringUTFChars(env, path, NULL);
    if (utf == NULL)
        return JNI_FALSE;

    ok = nds_rom_icon(utf, words, bytes, name);
    (*env)->ReleaseStringUTFChars(env, path, utf);

    (*env)->SetIntArrayRegion(env, palette, 0, ICON_PALETTE_WORDS, (const jint *)words);
    (*env)->SetByteArrayRegion(env, pixels, 0, ICON_PIXEL_BYTES, (const jbyte *)bytes);
    (*env)->SetByteArrayRegion(env, title, 0, ICON_TITLE_BYTES, (const jbyte *)name);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_seedlessds_core_SeedlessCore_infoString(JNIEnv *env, jclass cls)
{
    char buffer[INFO_STRING_BYTES];

    (void)cls;
    nds_info_string(g_nds, buffer, sizeof buffer);
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jstring JNICALL
Java_com_seedlessds_core_SeedlessCore_versionString(JNIEnv *env, jclass cls, jint kind)
{
    (void)cls;
    (void)kind;
    return (*env)->NewStringUTF(env, nds_version_string());
}
