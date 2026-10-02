
#include <jni.h>
#include "seedlessds/nds.h"

nds_t *seedless_core(void);

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_fxLoad(JNIEnv *env, jclass cls, jstring recipePath,
                                             jint vboVertexOffset, jint vboTexcoordOffset)
{
    const char *utf = (*env)->GetStringUTFChars(env, recipePath, NULL);
    jint result;

    (void)cls;
    result = (jint)nds_fx_load(seedless_core(), utf, (uint32_t)vboVertexOffset,
                               (uint32_t)vboTexcoordOffset);
    if (utf != NULL)
        (*env)->ReleaseStringUTFChars(env, recipePath, utf);
    return result;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_fxSetup(JNIEnv *env, jclass cls, jint sourceWidth,
                                              jint sourceHeight, jint x, jint y,
                                              jint viewWidth, jint viewHeight)
{
    (void)env;
    (void)cls;
    nds_fx_setup(seedless_core(), (int)sourceWidth, (int)sourceHeight, (int)x, (int)y,
                 (int)viewWidth, (int)viewHeight);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_fxRender(JNIEnv *env, jclass cls, jint textureTop,
                                               jint textureBottom, jint vertexTop,
                                               jint vertexBottom, jint vertexIntermediate,
                                               jint topWidth, jint topHeight,
                                               jint bottomWidth, jint bottomHeight,
                                               jboolean flag)
{
    nds_fx_frame_t frame;

    (void)env;
    (void)cls;

    frame.texture_top = (uint32_t)textureTop;
    frame.texture_bottom = (uint32_t)textureBottom;
    frame.vertex_top = (uint32_t)vertexTop;
    frame.vertex_bottom = (uint32_t)vertexBottom;
    frame.vertex_intermediate = (uint32_t)vertexIntermediate;
    frame.top_width = (uint32_t)topWidth;
    frame.top_height = (uint32_t)topHeight;
    frame.bottom_width = (uint32_t)bottomWidth;
    frame.bottom_height = (uint32_t)bottomHeight;
    frame.flag = (flag != JNI_FALSE);

    nds_fx_render(seedless_core(), &frame);
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_extfxLoad(JNIEnv *env, jclass cls,
                                                jstring recipePath, jint vboVertexOffset,
                                                jint vboTexcoordOffset)
{
    const char *utf = (*env)->GetStringUTFChars(env, recipePath, NULL);
    jint result;

    (void)cls;
    result = (jint)nds_extfx_load(seedless_core(), utf, (uint32_t)vboVertexOffset,
                                  (uint32_t)vboTexcoordOffset);
    if (utf != NULL)
        (*env)->ReleaseStringUTFChars(env, recipePath, utf);
    return result;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_extfxSetup(JNIEnv *env, jclass cls,
                                                 jint sourceWidth, jint sourceHeight,
                                                 jint x, jint y, jint viewWidth,
                                                 jint viewHeight)
{
    (void)env;
    (void)cls;
    nds_extfx_setup(seedless_core(), (int)sourceWidth, (int)sourceHeight, (int)x, (int)y,
                    (int)viewWidth, (int)viewHeight);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_extfxRender(JNIEnv *env, jclass cls, jint texture,
                                                  jint index, jint a, jint b, jint c,
                                                  jint d)
{
    (void)env;
    (void)cls;
    nds_extfx_render(seedless_core(), (uint32_t)texture, (uint32_t)index, (uint32_t)a,
                     (uint32_t)b, (uint32_t)c, (uint32_t)d);
}
