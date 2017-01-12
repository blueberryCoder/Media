#include <jni.h>

#include "rtmp.h"
#include "rtmp_sys.h"
#include "amf.h"
#include "log.h"
#include "android/log.h"

RTMP *rtmp = NULL;
char *rtmpUrl = "rtmp://192.168.155.1:1935/live/test";
RTMPPacket *packet = NULL;


JNIEXPORT jstring JNICALL
Java_com_blueberry_hellortmp_MainActivity_stringFromJNI(JNIEnv *env, jobject instance) {
       const char * hello = "Success";


    RTMP_LogSetLevel(RTMP_LOGDEBUG);
    rtmp = RTMP_Alloc(); //申请rtmp空间
    RTMP_Init(rtmp);
    __android_log_print(ANDROID_LOG_DEBUG,"RTMP","%s","rtmp init finished");
    rtmp->Link.timeout = 5;//单位秒
    packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    memset(packet, 0, sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, 1024 * 64);
    RTMPPacket_Reset(packet);

    RTMP_SetupURL(rtmp, rtmpUrl);
    RTMP_EnableWrite(rtmp);



    //连接
    if (!RTMP_Connect(rtmp, NULL)) {
        //clear
        __android_log_print(ANDROID_LOG_DEBUG,"RTMP","%s","error");
        return (*env)->NewStringUTF(env,"error");
    }
    return (*env)->NewStringUTF(env,"hello");
}