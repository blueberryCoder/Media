#include <jni.h>
#include "stdio.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"

#include "android/log.h"

#define LOGI(format, ...) __android_log_print(ANDROID_LOG_INFO, TAG, format, ##__VA_ARGS__)

#define TAG "FFmpeg"


/**
 * 获得FFmpeg 的config信息
 */
JNIEXPORT jstring JNICALL
Java_com_blueberry_helloffmpeg_FFmpegUtil_configInfo(JNIEnv *env, jclass type) {

    char info[1000] = {0};
    sprintf(info, "%s\n", avcodec_configuration());
    LOGI("%s", info);
    return (*env)->NewStringUTF(env, info);
}

/**
 * 获得FFmpeg的封装格式信息
 */
JNIEXPORT jstring JNICALL
Java_com_blueberry_helloffmpeg_FFmpegUtil_formatInfo(JNIEnv *env, jclass type) {

    char info[40000] = {0};
    av_register_all();
    AVInputFormat *if_temp = av_iformat_next(NULL);
    AVOutputFormat *of_temp = av_oformat_next(NULL);

    // Input
    while (if_temp != NULL) {
        sprintf(info, "%s[IN] [%10s]\n", info, if_temp->name);
        if_temp = if_temp->next;
    }
    //Outpuf
    while (of_temp != NULL) {
        sprintf(info, "%s[OUT] [%10s]\n", info, of_temp->name);
        of_temp = of_temp->next;
    }
    LOGI("%s", info);

    return (*env)->NewStringUTF(env, info);
}

/**
 * 获得FFmpeg的滤镜配置信息
 */
JNIEXPORT jstring JNICALL
Java_com_blueberry_helloffmpeg_FFmpegUtil_filterInfo(JNIEnv *env, jclass type) {
    char info[10000] = {0};
    avfilter_register_all();
    AVFilter *f_temp = (AVFilter *) avfilter_next(NULL);
    while (f_temp != NULL) {
        LOGI("%s", f_temp->name);
        sprintf(info, "%s[Filter] [%10s]\n", info, f_temp->name);
        f_temp = f_temp->next;
    }
    return (*env)->NewStringUTF(env, info);
}

struct URLProtocol;

/**
 * 获得FFmpeg的支持的协议
 */
JNIEXPORT jstring JNICALL
Java_com_blueberry_helloffmpeg_FFmpegUtil_protocolInfo(JNIEnv *env, jclass type) {

    char info[40000] = {0};
    av_register_all();
    struct URLProtocol *pup = NULL;

    //input
    struct URLProtocol **p_temp = &pup;
    avio_enum_protocols((void **) p_temp, 0);

    while ((*p_temp) != NULL) {
        sprintf(info, "%s[In][%10s]\n", info, avio_enum_protocols((void **) p_temp, 0));
    }

    pup = NULL;

    avio_enum_protocols((void **) p_temp, 1);
    while ((*p_temp) != NULL) {
        sprintf(info, "%s[Out][%10s]\n", info, avio_enum_protocols((void **) p_temp, 0));
    }


    return (*env)->NewStringUTF(env, info);
}

/**
 * 获得FFmpeg所支持的编码类型
 */
JNIEXPORT jstring JNICALL
Java_com_blueberry_helloffmpeg_FFmpegUtil_avCodecInfo(JNIEnv *env, jclass type) {
    char info[100000] = {0};
    av_register_all();
    AVCodec *c_temp = av_codec_next(NULL);

    while (c_temp != NULL) {
        if (c_temp->decode != NULL) {
            sprintf(info, "%s[Dec]", info);
        } else {
            sprintf(info, "%s[Enc]", info);
        }

        switch (c_temp->type) {
            case AVMEDIA_TYPE_VIDEO:
                sprintf(info, "%s[Video]", info);
                break;
            case AVMEDIA_TYPE_AUDIO:
                sprintf(info, "%s[Audio]", info);
                break;
            default:
                sprintf(info, "%s[Other]", info);
                break;
        }
        sprintf(info,"%s[%10s]\n",info,c_temp->name);
        c_temp = c_temp->next;
    }

    return (*env)->NewStringUTF(env, info);
}