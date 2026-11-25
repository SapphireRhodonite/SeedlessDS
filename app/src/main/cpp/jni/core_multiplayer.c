#include <jni.h>
#include <string.h>
#include "seedlessds/multiplayer.h"

static int read_bytes(JNIEnv *env, jbyteArray input, char *output, jsize capacity)
{
    if (!input) return 0;
    jsize length = (*env)->GetArrayLength(env, input);
    if ((*env)->ExceptionCheck(env) || length >= capacity) return 0;
    (*env)->GetByteArrayRegion(env, input, 0, length, (jbyte *)output);
    if ((*env)->ExceptionCheck(env)) return 0;
    if (memchr(output, 0, (size_t)length)) return 0;
    output[length] = 0;
    return 1;
}

static int read_fixed(JNIEnv *env, jbyteArray input, void *output, jsize size)
{
    if (!input) return 0;
    jsize length = (*env)->GetArrayLength(env, input);
    if ((*env)->ExceptionCheck(env) || length != size) return 0;
    (*env)->GetByteArrayRegion(env, input, 0, size, (jbyte *)output);
    return !(*env)->ExceptionCheck(env);
}

static jint start_multiplayer(JNIEnv *env, jint mode, jbyteArray name, jbyteArray local,
    jbyteArray owner, jint index, jbyteArray mac, jint maximum, jobjectArray diagnostic)
{
    char player[32];
    uint32_t local_address, owner_address;
    uint8_t identity[6];
    if (index <= 0 || !read_bytes(env, name, player, sizeof(player)) ||
        !read_fixed(env, local, &local_address, sizeof(local_address)) ||
        !read_fixed(env, owner, &owner_address, sizeof(owner_address)) ||
        !read_fixed(env, mac, identity, sizeof(identity))) return -1;
    if (!diagnostic)
        return seedless_mp_start_direct(mode, player, local_address, owner_address, (unsigned)index, identity, maximum);
    char output[192];
    const int result = seedless_mp_start_direct_report(mode, player, local_address, owner_address,
        (unsigned)index, identity, maximum, output, sizeof(output));
    if (result != 0 && output[0]) {
        jstring detail = (*env)->NewStringUTF(env, output);
        if ((*env)->ExceptionCheck(env) || !detail) return result;
        (*env)->SetObjectArrayElement(env, diagnostic, 0, detail);
        if ((*env)->ExceptionCheck(env)) return result;
        (*env)->DeleteLocalRef(env, detail);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerStart(JNIEnv *env, jclass cls,
    jint mode, jbyteArray name, jbyteArray local, jbyteArray owner, jint index, jbyteArray mac, jint maximum)
{
    (void)cls;
    return start_multiplayer(env, mode, name, local, owner, index, mac, maximum, NULL);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerStartDiagnostic(JNIEnv *env, jclass cls,
    jint mode, jbyteArray name, jbyteArray local, jbyteArray owner, jint index, jbyteArray mac, jint maximum,
    jobjectArray diagnostic)
{
    (void)cls;
    return start_multiplayer(env, mode, name, local, owner, index, mac, maximum, diagnostic);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerBindInterface(JNIEnv *env, jclass cls, jint fd, jint index)
{
    (void)env;
    (void)cls;
    if (fd < 0 || index <= 0) return -1;
    return seedless_mp_bind_interface(fd, (unsigned)index);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerStop(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return seedless_mp_stop();
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerPause(JNIEnv *env, jclass cls, jboolean paused)
{
    (void)env;
    (void)cls;
    return seedless_mp_pause(paused == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerActive(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return seedless_mp_enabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_multiplayerStatus(JNIEnv *env, jclass cls)
{
    (void)cls;
    char output[16384];
    size_t length = seedless_mp_snapshot(output, sizeof(output));
    if (length >= sizeof(output)) return NULL;
    jbyteArray result = (*env)->NewByteArray(env, (jsize)length);
    if (!result) return NULL;
    (*env)->SetByteArrayRegion(env, result, 0, (jsize)length, (const jbyte *)output);
    if ((*env)->ExceptionCheck(env)) return NULL;
    return result;
}
