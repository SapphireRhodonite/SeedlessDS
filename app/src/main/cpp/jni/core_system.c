
#include <jni.h>
#include <stdlib.h>
#include <string.h>
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
