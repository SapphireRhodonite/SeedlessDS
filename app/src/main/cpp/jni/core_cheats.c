
#include <jni.h>
#include <string.h>
#include "seedlessds/nds.h"

nds_t *seedless_core(void);

static jbyteArray bytes_of(JNIEnv *env, const char *text)
{
    jbyteArray out;
    jsize length;

    if (text == NULL)
        return NULL;
    length = (jsize)strlen(text);
    out = (*env)->NewByteArray(env, length + 1);
    if (out == NULL)
        return NULL;
    (*env)->SetByteArrayRegion(env, out, 0, length, (const jbyte *)text);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatCount(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_cheat_count(seedless_core());
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatEnabled(JNIEnv *env, jclass cls, jint index)
{
    (void)env;
    (void)cls;
    return nds_cheat_enabled(seedless_core(), (int)index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatSetEnabled(JNIEnv *env, jclass cls, jint index,
                                                      jboolean enabled)
{
    (void)env;
    (void)cls;
    nds_cheat_set_enabled(seedless_core(), (int)index, enabled != JNI_FALSE);
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatName(JNIEnv *env, jclass cls, jint index)
{
    (void)cls;
    return bytes_of(env, nds_cheat_name(seedless_core(), (int)index));
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatNote(JNIEnv *env, jclass cls, jint index)
{
    (void)cls;
    return bytes_of(env, nds_cheat_note(seedless_core(), (int)index));
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderCount(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_cheat_folder_count(seedless_core());
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderId(JNIEnv *env, jclass cls, jint index)
{
    (void)env;
    (void)cls;
    return (jint)nds_cheat_folder_id(seedless_core(), (int)index);
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderName(JNIEnv *env, jclass cls, jint index)
{
    (void)cls;
    return bytes_of(env, nds_cheat_folder_name(seedless_core(), (int)index));
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderNote(JNIEnv *env, jclass cls, jint index)
{
    (void)cls;
    return bytes_of(env, nds_cheat_folder_note(seedless_core(), (int)index));
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderExpanded(JNIEnv *env, jclass cls,
                                                          jint index)
{
    (void)env;
    (void)cls;
    return nds_cheat_folder_expanded(seedless_core(), (int)index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatSetFolderExpanded(JNIEnv *env, jclass cls,
                                                             jint index,
                                                             jboolean expanded)
{
    (void)env;
    (void)cls;
    nds_cheat_set_folder_expanded(seedless_core(), (int)index, expanded != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFolderMultiSelect(JNIEnv *env, jclass cls,
                                                             jint index)
{
    (void)env;
    (void)cls;
    return nds_cheat_folder_multi_select(seedless_core(), (int)index) ? JNI_TRUE
                                                                     : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatCustomCount(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_cheat_custom_count(seedless_core());
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatCustomName(JNIEnv *env, jclass cls, jint index)
{
    (void)cls;
    return bytes_of(env, nds_cheat_custom_name(seedless_core(), (int)index));
}

JNIEXPORT jintArray JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatCustomData(JNIEnv *env, jclass cls, jint index)
{
    uint32_t words = 0;
    const int32_t *codes = nds_cheat_custom_data(seedless_core(), (int)index, &words);
    jintArray out;

    (void)cls;
    if (codes == NULL)
        return NULL;
    out = (*env)->NewIntArray(env, (jsize)words);
    if (out == NULL)
        return NULL;
    (*env)->SetIntArrayRegion(env, out, 0, (jsize)words, (const jint *)codes);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatCustomEnabled(JNIEnv *env, jclass cls,
                                                         jint index)
{
    (void)env;
    (void)cls;
    return nds_cheat_custom_enabled(seedless_core(), (int)index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatSetCustomEnabled(JNIEnv *env, jclass cls,
                                                            jint index, jboolean enabled)
{
    (void)env;
    (void)cls;
    nds_cheat_set_custom_enabled(seedless_core(), (int)index, enabled != JNI_FALSE);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatAddCustom(JNIEnv *env, jclass cls, jstring name,
                                                     jintArray codes, jint words,
                                                     jboolean enabled)
{
    const char *utf = (*env)->GetStringUTFChars(env, name, NULL);
    void *data;
    int result;

    (void)cls;
    if (utf == NULL)
        return NDS_CHEAT_ADD_FAILED;

    data = (*env)->GetPrimitiveArrayCritical(env, codes, NULL);
    if (data == NULL) {
        (*env)->ReleaseStringUTFChars(env, name, utf);
        return NDS_CHEAT_ADD_FAILED;
    }
    result = nds_cheat_add_custom(seedless_core(), utf, data, (uint32_t)words,
                                  enabled != JNI_FALSE);
    (*env)->ReleasePrimitiveArrayCritical(env, codes, data, 0);
    (*env)->ReleaseStringUTFChars(env, name, utf);
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatFindCustom(JNIEnv *env, jclass cls,
                                                      jintArray codes, jint words)
{
    void *data = (*env)->GetPrimitiveArrayCritical(env, codes, NULL);
    int result;

    (void)cls;
    if (data == NULL)
        return 0;
    result = nds_cheat_find_custom(seedless_core(), data, (uint32_t)words);
    (*env)->ReleasePrimitiveArrayCritical(env, codes, data, 0);
    return (jint)result;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatRemoveCustom(JNIEnv *env, jclass cls,
                                                        jint index)
{
    (void)env;
    (void)cls;
    nds_cheat_remove_custom(seedless_core(), (int)index);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_cheatUpdate(JNIEnv *env, jclass cls, jboolean enabled)
{
    (void)env;
    (void)cls;
    nds_cheat_update(seedless_core(), enabled != JNI_FALSE);
}
