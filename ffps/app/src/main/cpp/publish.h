#include <jni.h>
#include "stdlib.h"


#define BYTE  uint8_t


#ifdef __cplusplus
extern "C"
{
#endif
JNIEXPORT jint JNICALL
        Java_com_blueberry_ffps_Publisher_push(JNIEnv *env, jclass type, jbyteArray data_, jint w,
                                               jint h);

JNIEXPORT jint JNICALL
        Java_com_blueberry_ffps_Publisher_init(JNIEnv *env, jclass type, jstring outUrl_, jint w,
                                               jint h);
JNIEXPORT jint JNICALL
Java_com_blueberry_ffps_Publisher_stop(JNIEnv *env, jclass type) ;

int init(const char *url, int w, int h);
int push(BYTE *data, int w, int h);
int stop();
void callback(void *, int, const char *, va_list);

#ifdef __cplusplus
}
#endif

