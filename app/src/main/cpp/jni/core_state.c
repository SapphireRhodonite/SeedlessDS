
#include <jni.h>
#include "seedlessds/nds.h"

#define SCRIPT_AXES 4

nds_t *seedless_core(void);

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSave(JNIEnv *env, jclass cls, jint slot,
                                                jboolean withScreens)
{
    (void)env;
    (void)cls;
    (void)withScreens;
    return nds_state_save(seedless_core(), (int)slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateLoad(JNIEnv *env, jclass cls, jint slot)
{
    (void)env;
    (void)cls;
    return nds_state_load(seedless_core(), (int)slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSaving(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_state_saving(seedless_core()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSlot(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_state_slot(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_autosaveInterval(JNIEnv *env, jclass cls,
                                                       jint seconds)
{
    (void)env;
    (void)cls;
    nds_autosave_interval(seedless_core(), (unsigned)seconds);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_accelerometer(JNIEnv *env, jclass cls, jfloat x,
                                                    jfloat y, jfloat z)
{
    (void)env;
    (void)cls;
    nds_accel(seedless_core(), (float)x, (float)y, (float)z);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_gyroscope(JNIEnv *env, jclass cls, jfloat rotation)
{
    (void)env;
    (void)cls;
    nds_gyro(seedless_core(), (float)rotation, 0.0f, 0.0f);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_rumble(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_rumble_state(seedless_core()) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_whitenoise(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env;
    (void)cls;
    nds_whitenoise(seedless_core(), on != JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_hinge(JNIEnv *env, jclass cls, jboolean closed)
{
    (void)env;
    (void)cls;
    nds_hinge(seedless_core(), closed != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptActive(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_script_active(seedless_core()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptOverrides(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_script_overrides(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptAxes(JNIEnv *env, jclass cls, jfloat leftX,
                                                 jfloat leftY, jfloat rightX,
                                                 jfloat rightY)
{
    float axes[SCRIPT_AXES];

    (void)env;
    (void)cls;
    axes[0] = (float)leftX;
    axes[1] = (float)leftY;
    axes[2] = (float)rightX;
    axes[3] = (float)rightY;
    nds_script_axis(seedless_core(), axes, SCRIPT_AXES);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptRotation(JNIEnv *env, jclass cls,
                                                     jint rotation)
{
    (void)env;
    (void)cls;
    nds_script_rotation(seedless_core(), (float)rotation);
}
