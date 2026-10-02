
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "seedlessds/nds.h"

#define SCREEN_PIXELS (256 * 192)
#define SCREEN_RGBA_BYTES (SCREEN_PIXELS * 4)
#define SCREEN_RGB16_BYTES (SCREEN_PIXELS * 2)
#define PIXEL_OPAQUE 0xff000000u
#define EMBOSS_ROWS 190
#define EMBOSS_COLUMNS 0xfe
#define EMBOSS_ROW_BYTES 0x100
#define EMBOSS_DEST_ROW 0x400
#define EMBOSS_DEST_FIRST 0x404
#define LUMA_RED 0x4c8bu
#define LUMA_GREEN 0x9645u
#define LUMA_BLUE 0x1cedu

nds_t *seedless_core(void);

static uint32_t from_rgb555(uint16_t pixel)
{
    uint32_t v = pixel;

    return ((v << 5) & 0xfc00u) | (v << 19) | ((v >> 8) & 0xf8u) | PIXEL_OPAQUE;
}

static uint8_t luma(uint16_t pixel)
{
    uint32_t red = ((uint32_t)pixel << 3) & 0xf8u;
    uint32_t green = ((uint32_t)pixel >> 3) & 0xfcu;
    uint32_t blue = ((uint32_t)pixel >> 8) & 0xf8u;
    uint32_t value = (red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE) >> 16;

    return (uint8_t)(value > 0xffu ? 0xffu : value);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_input(JNIEnv *env, jclass cls, jint packedPad,
                                            jint packedTouch, jint mask)
{
    nds_input_t input;

    (void)env;
    (void)cls;

    input.keys = (uint32_t)packedPad & 0x7fffffffu;
    input.keys_mask = (uint32_t)mask;
    input.touch_down = ((uint32_t)packedPad >> 31) != 0;
    input.touch_x = (int)((int32_t)packedTouch >> 16);
    input.touch_y = (int)((int32_t)packedTouch & 0xffff);
    nds_input_set(seedless_core(), &input);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_uploadScreens(JNIEnv *env, jclass cls, jint first,
                                                    jint second, jboolean skipUpload)
{
    (void)env;
    (void)cls;
    (void)nds_render_frame(seedless_core(), (uint32_t)first, (uint32_t)second,
                           skipUpload != JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_drawScreen(JNIEnv *env, jclass cls, jint texture,
                                                 jint index)
{
    (void)env;
    (void)cls;
    nds_draw_screen(seedless_core(), (uint32_t)texture, (uint32_t)index);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_drawScreenExt(JNIEnv *env, jclass cls, jint texture,
                                                    jint index)
{
    (void)env;
    (void)cls;
    nds_draw_screen_ext(seedless_core(), (uint32_t)texture, (uint32_t)index);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_frameWord(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_frame_word(seedless_core());
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_hudWord(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_perf_word(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_screensRgba(JNIEnv *env, jclass cls, jintArray top,
                                                  jintArray bottom)
{
    void *dst_top;
    void *dst_bottom;

    (void)cls;
    dst_top = (*env)->GetPrimitiveArrayCritical(env, top, NULL);
    dst_bottom = (*env)->GetPrimitiveArrayCritical(env, bottom, NULL);
    if (dst_top != NULL && dst_bottom != NULL)
        (void)nds_screens_rgba(seedless_core(), dst_top, dst_bottom);
    if (dst_bottom != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, bottom, dst_bottom, 0);
    if (dst_top != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, top, dst_top, 0);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_screenshot(JNIEnv *env, jclass cls, jintArray out)
{
    void *dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);

    (void)cls;
    if (dst == NULL)
        return;
    (void)nds_screenshot(seedless_core(), dst, (size_t)SCREEN_RGBA_BYTES * 2);
    (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_clearScreens(JNIEnv *env, jclass cls,
                                                   jint textureTop, jint textureBottom)
{
    (void)env;
    (void)cls;
    nds_clear_screens(seedless_core(), (uint32_t)textureTop, (uint32_t)textureBottom);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_signalScreen(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    nds_signal_screen(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_waitScreen(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    nds_wait_screen(seedless_core());
}

static void snapshot_to_rgba(JNIEnv *env, jintArray top, jintArray bottom,
                             const uint8_t *src_top, const uint8_t *src_bottom, int ok)
{
    uint8_t *dst_top = (*env)->GetPrimitiveArrayCritical(env, top, NULL);
    uint8_t *dst_bottom = (*env)->GetPrimitiveArrayCritical(env, bottom, NULL);

    if (ok && dst_top != NULL && dst_bottom != NULL) {
        uint32_t i;

        for (i = 0; i < SCREEN_PIXELS; i++) {
            uint16_t a;
            uint16_t b;

            memcpy(&a, src_top + (size_t)i * 2, sizeof a);
            memcpy(&b, src_bottom + (size_t)i * 2, sizeof b);
            {
                uint32_t va = from_rgb555(a);
                uint32_t vb = from_rgb555(b);

                memcpy(dst_top + (size_t)i * 4, &va, sizeof va);
                memcpy(dst_bottom + (size_t)i * 4, &vb, sizeof vb);
            }
        }
    }
    if (dst_bottom != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, bottom, dst_bottom, 0);
    if (dst_top != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, top, dst_top, 0);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_snapshotSlot(JNIEnv *env, jclass cls, jint slot,
                                                   jintArray top, jintArray bottom)
{
    uint8_t *capture_top = malloc(SCREEN_RGB16_BYTES);
    uint8_t *capture_bottom = malloc(SCREEN_RGB16_BYTES);

    (void)cls;
    if (capture_top != NULL && capture_bottom != NULL) {
        int r = nds_snapshot_slot(seedless_core(), (int)slot, capture_top, capture_bottom);

        snapshot_to_rgba(env, top, bottom, capture_top, capture_bottom,
                         r == 0 || r == -2);
    }
    free(capture_bottom);
    free(capture_top);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_snapshotFile(JNIEnv *env, jclass cls, jstring path,
                                                   jintArray top, jintArray bottom)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    uint8_t *capture_top;
    uint8_t *capture_bottom;

    (void)cls;
    if (utf == NULL)
        return;

    capture_top = malloc(SCREEN_RGB16_BYTES);
    capture_bottom = malloc(SCREEN_RGB16_BYTES);
    if (capture_top != NULL && capture_bottom != NULL) {
        int r = nds_snapshot_file(seedless_core(), utf, capture_top, capture_bottom);

        snapshot_to_rgba(env, top, bottom, capture_top, capture_bottom,
                         r == 0 || r == -2);
    }
    free(capture_bottom);
    free(capture_top);
    (*env)->ReleaseStringUTFChars(env, path, utf);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_snapshotTopEmboss(JNIEnv *env, jclass cls,
                                                        jstring path, jintArray out)
{
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    uint8_t *capture_top;
    uint8_t *capture_bottom;
    uint8_t *grey;

    (void)cls;
    if (utf == NULL)
        return;

    capture_top = malloc(SCREEN_RGB16_BYTES);
    capture_bottom = malloc(SCREEN_RGB16_BYTES);
    grey = malloc(SCREEN_PIXELS);

    if (capture_top != NULL && capture_bottom != NULL && grey != NULL) {
        int r = nds_snapshot_file(seedless_core(), utf, capture_top, capture_bottom);

        if (r == 0 || r == -2) {
            uint8_t *dst;
            uint32_t i;

            for (i = 0; i < SCREEN_PIXELS; i++) {
                uint16_t pixel;

                memcpy(&pixel, capture_top + (size_t)i * 2, sizeof pixel);
                grey[i] = luma(pixel);
            }

            dst = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
            if (dst != NULL) {
                unsigned row;

                for (row = 0; row < EMBOSS_ROWS; row++) {
                    const uint8_t *src = grey + (size_t)row * EMBOSS_ROW_BYTES;
                    uint8_t *line = dst + EMBOSS_DEST_FIRST + (size_t)row * EMBOSS_DEST_ROW;
                    unsigned i2;

                    for (i2 = 0; i2 < EMBOSS_COLUMNS; i2++) {
                        int32_t d = (int32_t)(src[i2] * 2u - src[i2 + 0x101]
                                              - src[i2 + 0x202] + 0x80);
                        int32_t half = d >> 1;
                        uint32_t v = (uint32_t)half & ~((uint32_t)(d >> 31));
                        uint32_t packed;

                        if ((int32_t)v >= 0xff)
                            v = 0xff;
                        packed = v | (v << 8) | (v << 16) | PIXEL_OPAQUE;
                        memcpy(line + (size_t)i2 * 4, &packed, sizeof packed);
                    }
                }
                (*env)->ReleasePrimitiveArrayCritical(env, out, dst, 0);
            }
        }
    }
    free(grey);
    free(capture_bottom);
    free(capture_top);
    (*env)->ReleaseStringUTFChars(env, path, utf);
}
