#include <jni.h>
#include <string>
extern "C"
{
#include "libavcodec/avcodec.h"
}


JNIEXPORT jint JNICALL
Java_com_blueberry_ffps_Publisher_push(JNIEnv *env, jclass type, jbyteArray data_, jint w, jint h) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    // TODO

    env->ReleaseByteArrayElements(data_, data, 0);
}


extern "C"
jstring
Java_com_blueberry_ffps_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    avcodec_register_all();

    const char * hello = avcodec_configuration();

    return env->NewStringUTF(hello);
}
