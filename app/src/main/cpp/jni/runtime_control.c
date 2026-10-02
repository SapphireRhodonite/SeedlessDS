#include <jni.h>
#include "seedlessds/control.h"

JNIEXPORT void JNICALL
Java_com_seedlessds_app_control_NativeControl_begin(JNIEnv *env, jclass cls, jlong clock, jboolean no_skips, jint threads)
{
    (void)env; (void)cls;
    nds_control_begin(clock, no_skips != JNI_FALSE, (unsigned)threads);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_control_NativeControl_end(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    nds_control_end();
}

JNIEXPORT jlongArray JNICALL
Java_com_seedlessds_app_control_NativeControl_status(JNIEnv *env, jclass cls)
{
    (void)cls;
    nds_control_status_t s;
    nds_control_status(&s);
    jlong values[] = { s.frames, s.output_frames, s.output_reads, s.target, s.capture_records,
        s.capture_bytes, s.audio_blocks, s.audio_bytes, s.first_3d, s.first_geometry, s.dispatches,
        s.max_polygons, s.active, s.paused, s.capture_complete, s.audio_complete, s.error,
        s.state_request, s.state_completed, s.epoch, s.state_result, s.state_busy, s.audio_position };
    jsize count = (jsize)(sizeof values / sizeof values[0]);
    jlongArray out = (*env)->NewLongArray(env, count);
    if (out) (*env)->SetLongArrayRegion(env, out, 0, count, values);
    return out;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_control_NativeControl_pause(JNIEnv *env, jclass cls, jboolean paused)
{
    (void)env; (void)cls;
    nds_control_pause(paused != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_control_NativeControl_runTo(JNIEnv *env, jclass cls, jlong frame)
{
    (void)env; (void)cls;
    return frame > 0 && nds_control_run_to((uint64_t)frame) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_control_NativeControl_step(JNIEnv *env, jclass cls, jlong frames)
{
    (void)env; (void)cls;
    return frames > 0 && nds_control_step((uint64_t)frames) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_control_NativeControl_capture(JNIEnv *env, jclass cls, jint fd, jlong first, jlong last, jboolean raw)
{
    (void)env; (void)cls;
    return first >= 0 && last >= first && nds_control_capture(fd, (uint64_t)first, (uint64_t)last, raw != JNI_FALSE)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_control_NativeControl_audio(JNIEnv *env, jclass cls, jint fd, jlong first, jlong last)
{
    (void)env; (void)cls;
    return first >= 0 && last >= first && nds_control_audio(fd, (uint64_t)first, (uint64_t)last) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_control_NativeControl_cancelCapture(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    nds_control_cancel_capture();
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_control_NativeControl_snapshot(JNIEnv *env, jclass cls, jint fd, jboolean raw)
{
    (void)env; (void)cls;
    return nds_control_snapshot(fd, raw != JNI_FALSE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_seedlessds_app_control_NativeControl_state(JNIEnv *env, jclass cls, jint slot, jboolean load)
{
    (void)env; (void)cls;
    return slot >= 0 && slot <= 9 ? (jlong)nds_control_request_state((unsigned)slot, load != JNI_FALSE) : 0;
}
